package com.vichrov.meizu21rgbring;

import android.os.Binder;
import android.os.IBinder;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Experimental Flyme LightState probes recovered from the user's stock framework.jar. */
final class ExperimentalStateController {
    private static final int PRIORITY = 10009;
    private static final int MUSIC_EFFECT = 38;

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

    ExperimentalStateController() {
        try { initialize(); } catch (Throwable ignored) {}
    }

    synchronized String captureState(int samples, int intervalMs) {
        StringBuilder out = new StringBuilder("Automatic music LightState capture\n");
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

    synchronized String setBrightnessPattern(int pattern, int argb) {
        try {
            if (service == null) initialize();
            ensureSession();

            ArrayList<Integer> values = new ArrayList<>();
            final int max = 60;
            for (int i = 0; i < 16; i++) {
                int v;
                switch (pattern) {
                    case 0: v = (i == 0) ? max : 0; break;                 // one hot
                    case 1: v = (i % 2 == 0) ? max : 0; break;            // alternating
                    case 2: v = Math.round(i * (max / 15.0f)); break;     // ramp
                    case 3: v = max; break;                               // all
                    default: return "BRIGHTNESS ARRAY ERROR: unknown pattern " + pattern;
                }
                values.add(v);
            }

            Class<?> builderClass = Class.forName("android.hardware.lights.LightState$Builder");
            Object builder = builderClass.getDeclaredConstructor().newInstance();
            invoke(findMethod(builderClass, "setEffectId", 1), builder, MUSIC_EFFECT);
            invoke(findMethod(builderClass, "setColor", 1), builder, argb);
            invoke(findMethod(builderClass, "setBrightnessArray", 1), builder, values);
            callOptional(builderClass, builder, "setBrightness", 255);
            callOptional(builderClass, builder, "setPercent", 100);
            Object state = invoke(findMethod(builderClass, "build", 0), builder);

            Object states = Array.newInstance(lightStateClass, 1);
            Array.set(states, 0, state);
            invoke(setLightStates, service, token, new int[]{ringLightId}, states);

            String readback = describeState(invoke(getLightState, service, ringLightId));
            return "BRIGHTNESS ARRAY SENT: effect=38 pattern=" + pattern + " values=" + values
                    + "\nReadback: " + readback;
        } catch (Throwable t) {
            return "BRIGHTNESS ARRAY ERROR: " + rootMessage(t);
        }
    }

    synchronized String stop() {
        try {
            if (sessionOpen && service != null) invoke(closeSession, service, token);
            sessionOpen = false;
            return "EXPERIMENTAL SESSION STOP OK";
        } catch (Throwable t) {
            sessionOpen = false;
            return "EXPERIMENTAL SESSION STOP ERROR: " + rootMessage(t);
        }
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

    private static void callOptional(Class<?> cls, Object target, String name, Object arg) {
        try { invoke(findMethod(cls, name, 1), target, arg); } catch (Throwable ignored) {}
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
