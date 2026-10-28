package com.vichrov.meizu21rgbring;

import android.os.Binder;
import android.os.IBinder;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Clean-room implementation of the mechanism observed in Meizu Tools 12.0.15.
 * Runs inside a Shizuku UserService, where non-SDK reflection is not blocked.
 */
final class RingController {
    private static final String LIGHTS_SERVICE = "lights";

    private final IBinder sessionToken = new Binder();
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
            brightness = clamp(brightness, 0, 255);
            int hwColor = (brightness << 24) | (argb & 0x00FFFFFF);
            apply(effectPure, brightness, hwColor, true);
            return String.format("PURE OK: light=%d color=#%08X brightness=%d", ringLightId, hwColor, brightness);
        } catch (Throwable t) {
            return "PURE ERROR: " + rootMessage(t);
        }
    }

    synchronized String setRainbow(int brightness) {
        try {
            brightness = clamp(brightness, 0, 255);
            apply(effectRainbow, brightness, -1, false);
            return "RAINBOW OK: light=" + ringLightId + " brightness=" + brightness;
        } catch (Throwable t) {
            return "RAINBOW ERROR: " + rootMessage(t);
        }
    }

    synchronized String off() {
        try {
            if (sessionOpen) {
                invoke(closeSession, lightsService, sessionToken);
                sessionOpen = false;
            }
            return "OFF OK (lights session closed)";
        } catch (Throwable t) {
            sessionOpen = false;
            return "OFF ERROR: " + rootMessage(t);
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

        // Meizu 21 / Flyme 12 exposes these as vendor framework extensions.
        // The stock APK resolves them reflectively too. Search a few logical owners
        // so minor Flyme framework reshuffles do not make diagnostics useless.
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
