package com.vichrov.meizu21rgbring;

import android.os.Binder;
import android.os.IBinder;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Experimental Flyme LightState controller reconstructed from stock framework/services.jar.
 *
 * Stock music paths use exactly 20 Integer brightness/phase values:
 *  - effect 38: MUSIC, setEffectId(38) + setBrightnessArray(values)
 *  - effect 50: IN_CALL_MUSIC, setEffectId(50) + setColor(color) + setBrightnessArray(values)
 *  - effect 64: IN_CALL_MUSIC_PREVIEW, setEffectId(64) + setColor(color) + setBrightnessArray(values)
 *
 * No setBrightness() or setPercent() is used for these frames.
 */
final class ExperimentalStateController {
    private static final int PRIORITY = 10009;
    private static final int MUSIC_EFFECT = 38;
    private static final int IN_CALL_MUSIC_EFFECT = 50;
    private static final int INCALL_MUSIC_PREVIEW_EFFECT = 64;
    private static final int PHASE_COUNT = 20;
    private static final int PHASE_LEVEL = 60;

    private final IBinder token = new Binder();
    private Object service;
    private Method getLights;
    private Method getLightState;
    private Method openSession;
    private Method closeSession;
    private Method setLightStates;
    private Class<?> lightStateClass;
    private int breatheType;
    private int ringLightId = -1;
    private boolean sessionOpen;

    private volatile boolean stopAnimation;
    private Thread animationThread;

    ExperimentalStateController() {
        try { initialize(); } catch (Throwable ignored) {}
    }

    synchronized String captureState(int samples, int intervalMs) {
        StringBuilder out = new StringBuilder("LightState capture\n");
        try {
            if (service == null) initialize();
            samples = Math.max(1, Math.min(80, samples));
            intervalMs = Math.max(10, Math.min(200, intervalMs));
            String last = null;
            int changes = 0;
            for (int i = 0; i < samples; i++) {
                Object state = invoke(getLightState, service, ringLightId);
                String now = describeState(state);
                if (!now.equals(last)) {
                    changes++;
                    out.append('[').append(i).append("] ").append(now).append('\n');
                    last = now;
                }
                if (i + 1 < samples) Thread.sleep(intervalMs);
            }
            out.append("state changes observed = ").append(changes).append('\n');
        } catch (Throwable t) {
            out.append("CAPTURE ERROR: ").append(rootMessage(t));
        }
        return out.toString();
    }

    /**
     * Pattern ids used by the experimental UI:
     *   0..3   -> effect 38 static patterns
     *   10..13 -> effect 64 static patterns with caller color
     *   20     -> effect 38 moving one-hot
     *   30     -> effect 64 moving one-hot + caller color
     *   40     -> effect 50 one-hot index 0
     *   41     -> effect 50 one-hot index 5
     *   42     -> effect 50 one-hot index 10
     *   43     -> effect 50 one-hot index 15
     *   44     -> effect 50 one-hot index 19
     *   50     -> effect 50 moving one-hot + caller color
     *   51     -> effect 50 alternating 60/0 + caller color
     *   52     -> effect 50 all 20 = 60 + caller color
     */
    synchronized String setBrightnessPattern(int pattern, int argb) {
        try {
            if (service == null) initialize();
            stopAnimationLocked();
            ensureSession();

            if (pattern == 20) return startSweepLocked(MUSIC_EFFECT, argb);
            if (pattern == 30) return startSweepLocked(INCALL_MUSIC_PREVIEW_EFFECT, argb);
            if (pattern == 50) return startSweepLocked(IN_CALL_MUSIC_EFFECT, argb);

            if (pattern >= 40 && pattern <= 44) {
                int[] positions = {0, 5, 10, 15, 19};
                int index = positions[pattern - 40];
                ArrayList<Integer> values = oneHot(index);
                sendState(IN_CALL_MUSIC_EFFECT, argb, values);
                return sentText(IN_CALL_MUSIC_EFFECT, argb, values,
                        "one-hot index=" + index);
            }

            if (pattern == 51) {
                ArrayList<Integer> values = buildPattern(1);
                sendState(IN_CALL_MUSIC_EFFECT, argb, values);
                return sentText(IN_CALL_MUSIC_EFFECT, argb, values, "alternating 60/0");
            }

            if (pattern == 52) {
                ArrayList<Integer> values = buildPattern(3);
                sendState(IN_CALL_MUSIC_EFFECT, argb, values);
                return sentText(IN_CALL_MUSIC_EFFECT, argb, values, "all 20 = 60");
            }

            int effect = pattern >= 10 ? INCALL_MUSIC_PREVIEW_EFFECT : MUSIC_EFFECT;
            int basePattern = pattern >= 10 ? pattern - 10 : pattern;
            ArrayList<Integer> values = buildPattern(basePattern);
            if (values == null) return "20-PHASE ERROR: unknown pattern " + pattern;

            sendState(effect, argb, values);
            return sentText(effect, argb, values, "static pattern=" + basePattern);
        } catch (Throwable t) {
            return "20-PHASE ERROR: " + rootMessage(t);
        }
    }

    private String sentText(int effect, int argb, ArrayList<Integer> values, String description) {
        return "20-PHASE SENT: effect=" + effect
                + " " + description
                + " count=" + values.size()
                + " color=" + String.format("#%08X", argb)
                + " values=" + values
                + "\nExact Flyme-style frame: effect + 20 Integer phase values; no setBrightness/setPercent.";
    }

    private ArrayList<Integer> oneHot(int index) {
        ArrayList<Integer> values = new ArrayList<>(PHASE_COUNT);
        for (int i = 0; i < PHASE_COUNT; i++) values.add(i == index ? PHASE_LEVEL : 0);
        return values;
    }

    private ArrayList<Integer> buildPattern(int pattern) {
        ArrayList<Integer> values = new ArrayList<>(PHASE_COUNT);
        for (int i = 0; i < PHASE_COUNT; i++) {
            int v;
            switch (pattern) {
                case 0: v = (i == 0) ? PHASE_LEVEL : 0; break;
                case 1: v = (i % 2 == 0) ? PHASE_LEVEL : 0; break;
                case 2: v = Math.round(i * (PHASE_LEVEL / (float) (PHASE_COUNT - 1))); break;
                case 3: v = PHASE_LEVEL; break;
                default: return null;
            }
            values.add(v);
        }
        return values;
    }

    private String startSweepLocked(int effect, int argb) {
        stopAnimation = false;
        animationThread = new Thread(() -> {
            int index = 0;
            try {
                while (!stopAnimation) {
                    ArrayList<Integer> values = oneHot(index);
                    synchronized (ExperimentalStateController.this) {
                        if (stopAnimation) break;
                        sendState(effect, argb, values);
                    }
                    index = (index + 1) % PHASE_COUNT;
                    Thread.sleep(120);
                }
            } catch (Throwable ignored) {
            }
        }, "flyme-20-phase-sweep");
        animationThread.setDaemon(true);
        animationThread.start();
        return "20-PHASE SWEEP STARTED: effect=" + effect
                + " count=20 one-hot level=" + PHASE_LEVEL
                + " color=" + String.format("#%08X", argb)
                + " step=120ms full-cycle=2.4s";
    }

    private void sendState(int effect, int argb, ArrayList<Integer> values) throws Exception {
        Class<?> builderClass = Class.forName("android.hardware.lights.LightState$Builder");
        Object builder = builderClass.getDeclaredConstructor().newInstance();
        invoke(findMethod(builderClass, "setEffectId", 1), builder, effect);
        if (effect == IN_CALL_MUSIC_EFFECT || effect == INCALL_MUSIC_PREVIEW_EFFECT) {
            invoke(findMethod(builderClass, "setColor", 1), builder, argb);
        }
        invoke(findMethod(builderClass, "setBrightnessArray", 1), builder, values);
        Object state = invoke(findMethod(builderClass, "build", 0), builder);

        Object states = Array.newInstance(lightStateClass, 1);
        Array.set(states, 0, state);
        invoke(setLightStates, service, token, new int[]{ringLightId}, states);
    }

    synchronized String stop() {
        try {
            stopAnimationLocked();
            if (sessionOpen && service != null) invoke(closeSession, service, token);
            sessionOpen = false;
            return "EXPERIMENTAL SESSION STOP OK";
        } catch (Throwable t) {
            sessionOpen = false;
            return "EXPERIMENTAL SESSION STOP ERROR: " + rootMessage(t);
        }
    }

    private void stopAnimationLocked() {
        stopAnimation = true;
        Thread t = animationThread;
        animationThread = null;
        if (t != null) t.interrupt();
    }

    private void initialize() throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Method getService = serviceManager.getDeclaredMethod("getService", String.class);
        getService.setAccessible(true);
        IBinder binder = (IBinder) getService.invoke(null, "lights");
        if (binder == null) throw new IllegalStateException("lights binder is null");

        Class<?> stub = Class.forName("android.hardware.lights.ILightsManager$Stub");
        Method asInterface = stub.getDeclaredMethod("asInterface", IBinder.class);
        asInterface.setAccessible(true);
        service = asInterface.invoke(null, binder);

        Class<?> iface = Class.forName("android.hardware.lights.ILightsManager");
        getLights = findMethod(iface, "getLights", 0);
        getLightState = findMethod(iface, "getLightState", 1);
        openSession = findMethod(iface, "openSession", 2);
        closeSession = findMethod(iface, "closeSession", 1);
        setLightStates = findMethod(iface, "setLightStates", 3);
        lightStateClass = Class.forName("android.hardware.lights.LightState");

        Class<?> lightClass = Class.forName("android.hardware.lights.Light");
        breatheType = staticInt(lightClass, "LIGHT_TYPE_BREATHE");
        selectRing();
    }

    private void selectRing() throws Exception {
        List<?> lights = (List<?>) invoke(getLights, service);
        for (Object light : lights) {
            int type = ((Number) invoke(findMethod(light.getClass(), "getType", 0), light)).intValue();
            if (type == breatheType) {
                ringLightId = ((Number) invoke(findMethod(light.getClass(), "getId", 0), light)).intValue();
                return;
            }
        }
        throw new IllegalStateException("No breathe light");
    }

    private void ensureSession() throws Exception {
        if (sessionOpen) return;
        invoke(openSession, service, token, PRIORITY);
        sessionOpen = true;
    }

    private static String describeState(Object state) {
        if (state == null) return "<null>";
        StringBuilder s = new StringBuilder();
        appendNumber(s, state, "effect", "getEffectId");
        appendNumber(s, state, "brightness", "getBrightness");
        appendNumber(s, state, "percent", "getPercent");
        appendNumber(s, state, "playerId", "getPlayerId");
        try {
            Object c = invoke(findMethod(state.getClass(), "getColor", 0), state);
            if (c instanceof Number) s.append(" color=").append(String.format("#%08X", ((Number) c).intValue()));
        } catch (Throwable ignored) {}
        try {
            Object a = invoke(findMethod(state.getClass(), "getBrightnessArray", 0), state);
            if (a instanceof List) {
                List<?> list = (List<?>) a;
                s.append(" brightnessArray(size=").append(list.size()).append(")=").append(list);
            } else s.append(" brightnessArray=null");
        } catch (Throwable ignored) {}
        return s.toString().trim();
    }

    private static void appendNumber(StringBuilder s, Object state, String label, String getter) {
        try {
            Object v = invoke(findMethod(state.getClass(), getter, 0), state);
            if (v instanceof Number) s.append(label).append('=').append(v).append(' ');
        } catch (Throwable ignored) {}
    }

    private static Method findMethod(Class<?> cls, String name, int count) throws NoSuchMethodException {
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == count) {
                m.setAccessible(true);
                return m;
            }
        }
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == count) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new NoSuchMethodException(cls.getName() + "." + name + "/" + count);
    }

    private static int staticInt(Class<?> cls, String name) throws Exception {
        java.lang.reflect.Field f;
        try { f = cls.getDeclaredField(name); } catch (NoSuchFieldException e) { f = cls.getField(name); }
        f.setAccessible(true);
        return f.getInt(null);
    }

    private static Object invoke(Method method, Object target, Object... args) throws Exception {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof Exception) throw (Exception) c;
            if (c instanceof Error) throw (Error) c;
            throw e;
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null && x.getCause() != x) x = x.getCause();
        return x.getClass().getSimpleName() + ": " + String.valueOf(x.getMessage());
    }
}
