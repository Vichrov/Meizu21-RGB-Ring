package com.vichrov.meizu21rgbring;

import android.graphics.Color;
import android.os.Binder;
import android.os.IBinder;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Meizu 21 RGB ring experiments.
 *
 * High-level mode uses Flyme's Android Lights extensions through Shizuku.
 * Low-level experimental mode targets the AW20072 16-pixel frame sysfs interface
 * reconstructed from the Meizu 21 Pro hardware implementation.
 */
final class RingController {
    private static final String LIGHTS_SERVICE = "lights";

    // AW20072 driver interface used by Meizu 21-series ring implementations.
    private static final String SYSFS_BASE = "/sys/class/leds/aw20072_led";
    private static final String SYSFS_FRAME = SYSFS_BASE + "/frame";
    private static final String SYSFS_EFFECT = SYSFS_BASE + "/effect";
    private static final int LED_COUNT = 16;
    private static final int FRAME_BYTES = 96; // 16 LEDs * RGB * (color + brightness)
    private static final int MAX_HW_BRIGHTNESS = 60;

    private final IBinder sessionToken = new Binder();
    private final ScheduledExecutorService animationExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "meizu21-ring-animation");
        t.setDaemon(true);
        return t;
    });

    private Object lightsService;
    private Method getLights;
    private Method openSession;
    private Method closeSession;
    private Method setLightStates;

    private Class<?> lightStateClass;
    private int breatheType;
    private int sessionPriority;
    private int effectPure;
    private int effectRainbow;
    private int ringLightId = -1;
    private boolean sessionOpen;

    private ScheduledFuture<?> softwareRainbowTask;
    private ScheduledFuture<?> segmentAnimationTask;
    private int animationGeneration;
    private String lastAnimationError;

    RingController() throws Exception {
        initialize();
    }

    synchronized String diagnostics() {
        StringBuilder out = new StringBuilder();
        out.append("Lights service: ").append(lightsService != null ? lightsService.getClass().getName() : "null").append('\n');
        out.append("LIGHT_TYPE_BREATHE = ").append(breatheType).append('\n');
        out.append("SESSION_PRIORITY_FLASHLIGHT = ").append(sessionPriority).append('\n');
        out.append("EFFECT_ID_PURE = ").append(effectPure).append('\n');
        out.append("EFFECT_ID_RAINBOW = ").append(effectRainbow).append('\n');
        out.append("Selected ring light id = ").append(ringLightId).append('\n');
        out.append("Session open = ").append(sessionOpen).append('\n');
        out.append("Software rainbow = ").append(softwareRainbowTask != null && !softwareRainbowTask.isCancelled()).append('\n');
        out.append("16-LED animation = ").append(segmentAnimationTask != null && !segmentAnimationTask.isCancelled()).append('\n');
        if (lastAnimationError != null) out.append("Animation error = ").append(lastAnimationError).append('\n');
        out.append("Brightness mode = RGB scaling for high-level PURE\n");
        out.append("AW20072 expected LEDs = 16, frame bytes = 96\n");
        out.append("AW20072 frame path = ").append(SYSFS_FRAME).append('\n');
        out.append("\nLights:\n");
        try {
            List<?> lights = (List<?>) invoke(getLights, lightsService);
            if (lights == null || lights.isEmpty()) {
                out.append("  <empty>\n");
            } else {
                for (Object light : lights) {
                    out.append("  id=").append(callInt(light, "getId"));
                    out.append(" type=").append(callInt(light, "getType"));
                    Object name = callOptional(light, "getName");
                    if (name != null) out.append(" name=").append(name);
                    out.append('\n');
                }
            }
        } catch (Throwable t) {
            out.append("  ERROR: ").append(rootMessage(t)).append('\n');
        }
        return out.toString();
    }

    synchronized String setPureColor(int argb, int brightness) {
        try {
            stopAllAnimationsLocked();
            brightness = clamp(brightness, 0, 255);
            int hwColor = scaleRgb(argb, brightness);
            apply(effectPure, 255, hwColor, true);
            return String.format("PURE OK: light=%d requested=#%06X scaled=#%06X softwareBrightness=%d",
                    ringLightId, argb & 0x00FFFFFF, hwColor & 0x00FFFFFF, brightness);
        } catch (Throwable t) {
            return "PURE ERROR: " + rootMessage(t);
        }
    }

    synchronized String setRainbow(int ignoredBrightness) {
        try {
            stopAllAnimationsLocked();
            apply(effectRainbow, 255, -1, false);
            return "NATIVE RAINBOW OK: light=" + ringLightId + " (driver-controlled fixed brightness)";
        } catch (Throwable t) {
            return "NATIVE RAINBOW ERROR: " + rootMessage(t);
        }
    }

    synchronized String startSoftwareRainbow(int brightness) {
        try {
            stopAllAnimationsLocked();
            brightness = clamp(brightness, 0, 255);
            final int requestedBrightness = brightness;
            final int generation = ++animationGeneration;
            lastAnimationError = null;

            softwareRainbowTask = animationExecutor.scheduleAtFixedRate(new Runnable() {
                private float hue = 0f;

                @Override
                public void run() {
                    synchronized (RingController.this) {
                        if (generation != animationGeneration) return;
                        try {
                            int fullColor = Color.HSVToColor(new float[]{hue, 1f, 1f});
                            int hwColor = scaleRgb(fullColor, requestedBrightness);
                            apply(effectPure, 255, hwColor, true);
                            hue += 5f;
                            if (hue >= 360f) hue -= 360f;
                        } catch (Throwable t) {
                            lastAnimationError = rootMessage(t);
                            stopSoftwareRainbowLocked();
                        }
                    }
                }
            }, 0, 40, TimeUnit.MILLISECONDS);

            return "SOFTWARE RAINBOW STARTED: brightness=" + brightness + " (RGB-scaled, 25 fps)";
        } catch (Throwable t) {
            return "SOFTWARE RAINBOW ERROR: " + rootMessage(t);
        }
    }

    synchronized String sysfsDiagnostics() {
        StringBuilder out = new StringBuilder("AW20072 / 16-LED diagnostics\n");
        File base = new File(SYSFS_BASE);
        File frame = new File(SYSFS_FRAME);
        File effect = new File(SYSFS_EFFECT);
        out.append("base: exists=").append(base.exists())
                .append(" dir=").append(base.isDirectory())
                .append(" read=").append(base.canRead())
                .append(" write=").append(base.canWrite()).append('\n');
        out.append("frame: exists=").append(frame.exists())
                .append(" read=").append(frame.canRead())
                .append(" write=").append(frame.canWrite()).append('\n');
        out.append("effect: exists=").append(effect.exists())
                .append(" read=").append(effect.canRead())
                .append(" write=").append(effect.canWrite()).append('\n');

        File leds = new File("/sys/class/leds");
        String[] names = leds.list();
        if (names != null) {
            Arrays.sort(names);
            out.append("/sys/class/leds entries:");
            for (String name : names) {
                if (name.toLowerCase().contains("aw") || name.toLowerCase().contains("rgb")
                        || name.toLowerCase().contains("led")) {
                    out.append("\n  ").append(name);
                }
            }
            out.append('\n');
        } else {
            out.append("Cannot list /sys/class/leds\n");
        }
        out.append("Frame format expected: 16 logical LEDs, 96 binary bytes, RGB channels + per-channel brightness 0..60.\n");
        out.append("No frame was written by this diagnostic.");
        return out.toString();
    }

    synchronized String setSingleLed(int index, int argb, int brightness) {
        try {
            index = clamp(index, 0, LED_COUNT - 1);
            brightness = clamp(brightness, 0, MAX_HW_BRIGHTNESS);
            prepareLowLevelModeLocked();
            byte[] frame = new byte[FRAME_BYTES];
            putPixel(frame, index, argb, brightness);
            writeFrame(frame);
            return String.format("16-LED OK: LED=%d color=#%06X brightness=%d/60",
                    index, argb & 0x00FFFFFF, brightness);
        } catch (Throwable t) {
            return "16-LED ERROR: " + rootMessage(t);
        }
    }

    synchronized String setAllLeds(int argb, int brightness) {
        try {
            brightness = clamp(brightness, 0, MAX_HW_BRIGHTNESS);
            prepareLowLevelModeLocked();
            byte[] frame = new byte[FRAME_BYTES];
            for (int i = 0; i < LED_COUNT; i++) putPixel(frame, i, argb, brightness);
            writeFrame(frame);
            return String.format("16-LED ALL OK: color=#%06X brightness=%d/60",
                    argb & 0x00FFFFFF, brightness);
        } catch (Throwable t) {
            return "16-LED ALL ERROR: " + rootMessage(t);
        }
    }

    synchronized String startThreeColorSpin(int brightness) {
        try {
            stopAllAnimationsLocked();
            closeLightsSessionLocked();
            brightness = clamp(brightness, 0, MAX_HW_BRIGHTNESS);
            final int hwBrightness = brightness;
            final int generation = ++animationGeneration;
            lastAnimationError = null;

            segmentAnimationTask = animationExecutor.scheduleAtFixedRate(new Runnable() {
                private int phase;
                private final int[] colors = {Color.RED, Color.GREEN, Color.BLUE};

                @Override
                public void run() {
                    synchronized (RingController.this) {
                        if (generation != animationGeneration) return;
                        try {
                            byte[] frame = new byte[FRAME_BYTES];
                            for (int i = 0; i < LED_COUNT; i++) {
                                putPixel(frame, i, colors[(i + phase) % colors.length], hwBrightness);
                            }
                            writeFrame(frame);
                            phase = (phase + 1) % colors.length;
                        } catch (Throwable t) {
                            lastAnimationError = rootMessage(t);
                            stopSegmentAnimationLocked();
                        }
                    }
                }
            }, 0, 80, TimeUnit.MILLISECONDS);
            return "3-COLOR 16-LED SPIN STARTED: brightness=" + brightness + "/60";
        } catch (Throwable t) {
            return "3-COLOR SPIN ERROR: " + rootMessage(t);
        }
    }

    synchronized String clearLedFrame() {
        try {
            stopAllAnimationsLocked();
            closeLightsSessionLocked();
            writeFrame(new byte[FRAME_BYTES]);
            tryWriteEffectZero();
            return "16-LED FRAME CLEARED";
        } catch (Throwable t) {
            return "CLEAR FRAME ERROR: " + rootMessage(t);
        }
    }

    synchronized String off() {
        String lowLevel = null;
        try {
            stopAllAnimationsLocked();
            closeLightsSessionLocked();
            if (new File(SYSFS_FRAME).exists()) {
                try {
                    writeFrame(new byte[FRAME_BYTES]);
                    tryWriteEffectZero();
                    lowLevel = " + AW20072 frame cleared";
                } catch (Throwable t) {
                    lowLevel = " (AW20072 clear skipped: " + rootMessage(t) + ")";
                }
            }
            return "OFF OK (animations stopped, lights session closed)" + (lowLevel == null ? "" : lowLevel);
        } catch (Throwable t) {
            sessionOpen = false;
            return "OFF ERROR: " + rootMessage(t);
        }
    }

    private void prepareLowLevelModeLocked() throws Exception {
        stopAllAnimationsLocked();
        closeLightsSessionLocked();
        File frame = new File(SYSFS_FRAME);
        if (!frame.exists()) throw new IllegalStateException(SYSFS_FRAME + " does not exist");
    }

    private void closeLightsSessionLocked() throws Exception {
        if (sessionOpen) {
            invoke(closeSession, lightsService, sessionToken);
            sessionOpen = false;
        }
    }

    private void stopAllAnimationsLocked() {
        animationGeneration++;
        stopSoftwareRainbowLocked();
        stopSegmentAnimationLocked();
    }

    private void stopSoftwareRainbowLocked() {
        if (softwareRainbowTask != null) {
            softwareRainbowTask.cancel(false);
            softwareRainbowTask = null;
        }
    }

    private void stopSegmentAnimationLocked() {
        if (segmentAnimationTask != null) {
            segmentAnimationTask.cancel(false);
            segmentAnimationTask = null;
        }
    }

    /**
     * Stock AW20072 frame encoder reconstructed from the Meizu 21-series implementation.
     * The 96-byte hardware order is the logical 96-byte stream rotated by six bytes:
     * frameMap[n] = (n + 6) mod 96.
     */
    private static void putPixel(byte[] wire, int led, int argb, int brightness) {
        if (wire == null || wire.length != FRAME_BYTES) throw new IllegalArgumentException("frame size");
        if (led < 0 || led >= LED_COUNT) throw new IllegalArgumentException("LED index " + led);
        brightness = clamp(brightness, 0, MAX_HW_BRIGHTNESS);
        int[] channels = {
                (argb >> 16) & 0xFF,
                (argb >> 8) & 0xFF,
                argb & 0xFF
        };
        for (int c = 0; c < 3; c++) {
            int logicalColor = 6 * led + 2 * c;
            int logicalBrightness = logicalColor + 1;
            int colorPos = (logicalColor + 6) % FRAME_BYTES;
            int brightnessPos = (logicalBrightness + 6) % FRAME_BYTES;
            int value = channels[c];
            wire[colorPos] = (byte) (value == 0 ? 0 : Math.max(1, value >> 2));
            wire[brightnessPos] = (byte) brightness;
        }
    }

    private static void writeFrame(byte[] frame) throws Exception {
        if (frame == null || frame.length != FRAME_BYTES) throw new IllegalArgumentException("Expected 96-byte frame");
        try (FileOutputStream out = new FileOutputStream(SYSFS_FRAME, false)) {
            out.write(frame);
            out.flush();
        }
    }

    private static void tryWriteEffectZero() {
        try (FileOutputStream out = new FileOutputStream(SYSFS_EFFECT, false)) {
            out.write("0\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            out.flush();
        } catch (Throwable ignored) {
        }
    }

    private void initialize() throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Method getService = serviceManager.getDeclaredMethod("getService", String.class);
        getService.setAccessible(true);
        IBinder binder = (IBinder) getService.invoke(null, LIGHTS_SERVICE);
        if (binder == null) throw new IllegalStateException("ServiceManager.getService(\"lights\") returned null");

        Class<?> stub = Class.forName("android.hardware.lights.ILightsManager$Stub");
        Method asInterface = stub.getDeclaredMethod("asInterface", IBinder.class);
        asInterface.setAccessible(true);
        lightsService = asInterface.invoke(null, binder);
        if (lightsService == null) throw new IllegalStateException("ILightsManager.Stub.asInterface returned null");

        Class<?> iface = Class.forName("android.hardware.lights.ILightsManager");
        getLights = findMethod(iface, "getLights", 0);
        openSession = findMethod(iface, "openSession", 2);
        closeSession = findMethod(iface, "closeSession", 1);
        setLightStates = findMethod(iface, "setLightStates", 3);

        Class<?> lightClass = Class.forName("android.hardware.lights.Light");
        Class<?> lightsManagerClass = Class.forName("android.hardware.lights.LightsManager");
        lightStateClass = Class.forName("android.hardware.lights.LightState");

        breatheType = staticIntAny("LIGHT_TYPE_BREATHE", lightClass, lightsManagerClass);
        sessionPriority = staticIntAny("SESSION_PRIORITY_FLASHLIGHT", lightClass, lightsManagerClass);
        effectPure = staticIntAny("EFFECT_ID_PURE", lightStateClass, lightClass);
        effectRainbow = staticIntAny("EFFECT_ID_RAINBOW", lightStateClass, lightClass);

        selectRingLight();
    }

    private void selectRingLight() throws Exception {
        List<?> lights = (List<?>) invoke(getLights, lightsService);
        if (lights == null) throw new IllegalStateException("getLights() returned null");
        for (Object light : lights) {
            int type = callInt(light, "getType");
            if (type == breatheType) {
                ringLightId = callInt(light, "getId");
                return;
            }
        }
        throw new IllegalStateException("No light with LIGHT_TYPE_BREATHE=" + breatheType + " found");
    }

    private void ensureSession() throws Exception {
        if (!sessionOpen) {
            invoke(openSession, lightsService, sessionToken, sessionPriority);
            sessionOpen = true;
        }
    }

    private void apply(int effectId, int brightness, int color, boolean useColor) throws Exception {
        if (ringLightId < 0) selectRingLight();
        ensureSession();

        Class<?> builderClass = Class.forName("android.hardware.lights.LightState$Builder");
        Object builder = builderClass.getDeclaredConstructor().newInstance();

        if (useColor) {
            Method setColor = findMethod(builderClass, "setColor", 1);
            invoke(setColor, builder, color);
        }

        Method setEffectId = findMethod(builderClass, "setEffectId", 1);
        Method setBrightness = findMethod(builderClass, "setBrightness", 1);
        Method build = findMethod(builderClass, "build", 0);
        invoke(setEffectId, builder, effectId);
        invoke(setBrightness, builder, brightness);
        Object state = invoke(build, builder);

        Object states = Array.newInstance(lightStateClass, 1);
        Array.set(states, 0, state);
        invoke(setLightStates, lightsService, sessionToken, new int[]{ringLightId}, states);
    }

    private static int scaleRgb(int argb, int brightness) {
        brightness = clamp(brightness, 0, 255);
        int r = (((argb >> 16) & 0xFF) * brightness + 127) / 255;
        int g = (((argb >> 8) & 0xFF) * brightness + 127) / 255;
        int b = ((argb & 0xFF) * brightness + 127) / 255;
        return Color.argb(255, r, g, b);
    }

    private static Method findMethod(Class<?> cls, String name, int paramCount) throws NoSuchMethodException {
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                m.setAccessible(true);
                return m;
            }
        }
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new NoSuchMethodException(cls.getName() + "." + name + "/" + paramCount);
    }

    private static int staticInt(Class<?> cls, String fieldName) throws Exception {
        Field f;
        try {
            f = cls.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            f = cls.getField(fieldName);
        }
        f.setAccessible(true);
        return f.getInt(null);
    }

    private static int staticIntAny(String fieldName, Class<?>... classes) throws Exception {
        Throwable last = null;
        for (Class<?> cls : classes) {
            try {
                return staticInt(cls, fieldName);
            } catch (Throwable t) {
                last = t;
            }
        }
        throw new NoSuchFieldException(fieldName + " (last error: " + String.valueOf(last) + ")");
    }

    private static int callInt(Object target, String method) throws Exception {
        Method m = findMethod(target.getClass(), method, 0);
        return ((Number) invoke(m, target)).intValue();
    }

    private static Object callOptional(Object target, String method) {
        try {
            return invoke(findMethod(target.getClass(), method, 0), target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invoke(Method method, Object target, Object... args) throws Exception {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw e;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static String rootMessage(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null && x.getCause() != x) x = x.getCause();
        return x.getClass().getSimpleName() + ": " + String.valueOf(x.getMessage());
    }
}
