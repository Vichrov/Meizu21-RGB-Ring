package com.vichrov.meizu21rgbring;

import android.os.Binder;
import android.os.IBinder;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Full no-root Flyme LightState path. Everything here is sent through system_server's
 * ILightsManager, so the app never opens the SELinux-protected AW20072 device directly.
 */
final class NoRootStateController {
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
    private int priority = Integer.MIN_VALUE;

    NoRootStateController() {
        try { initialize(); } catch (Throwable ignored) {}
    }

    synchronized String apply(int effectId, int argb, int brightness, int percent,
                              int playerId, int semaphoreCode, int amplitude, int requestedPriority) {
        try {
            if (service == null) initialize();
            effectId = clamp(effectId, 0, 255);
            brightness = clamp(brightness, 0, 255);
            percent = clamp(percent, 0, 100);
            playerId = clamp(playerId, 0, 255);
            semaphoreCode = clamp(semaphoreCode, 0, 65535);
            amplitude = clamp(amplitude, 0, 60);
            requestedPriority = clamp(requestedPriority, 0, 20000);
            ensureSession(requestedPriority);

            Class<?> builderClass = Class.forName("android.hardware.lights.LightState$Builder");
            Object builder = builderClass.getDeclaredConstructor().newInstance();
            invoke(findMethod(builderClass, "setEffectId", 1), builder, effectId);
            invoke(findMethod(builderClass, "setColor", 1), builder, argb);
            invoke(findMethod(builderClass, "setBrightness", 1), builder, brightness);
            invoke(findMethod(builderClass, "setPercent", 1), builder, percent);
            invoke(findMethod(builderClass, "setPlayerId", 1), builder, playerId);
            invoke(findMethod(builderClass, "setSemaphoreLetter", 1), builder, (char) semaphoreCode);

            ArrayList<Integer> levels = new ArrayList<>(20);
            for (int i = 0; i < 20; i++) levels.add(amplitude);
            invoke(findMethod(builderClass, "setBrightnessArray", 1), builder, levels);

            Object state = invoke(findMethod(builderClass, "build", 0), builder);
            Object states = Array.newInstance(lightStateClass, 1);
            Array.set(states, 0, state);
            invoke(setLightStates, service, token, new int[]{ringLightId}, states);

            return String.format(
                    "LightState OK · effect=%d color=#%08X brightness=%d percent=%d playerId=%d semaphore=%d amplitude=%d priority=%d",
                    effectId, argb, brightness, percent, playerId, semaphoreCode, amplitude, requestedPriority);
        } catch (Throwable t) {
            return "LightState ERROR: " + rootMessage(t);
        }
    }

    synchronized String currentState() {
        try {
            if (service == null) initialize();
            Object state = invoke(getLightState, service, ringLightId);
            if (state == null) return "Current LightState: <null>";
            StringBuilder s = new StringBuilder("Current LightState");
            appendNumber(s, state, "effect", "getEffectId");
            appendHex(s, state, "color", "getColor");
            appendNumber(s, state, "brightness", "getBrightness");
            appendNumber(s, state, "percent", "getPercent");
            appendNumber(s, state, "playerId", "getPlayerId");
            try {
                Object c = invoke(findMethod(state.getClass(), "getSemaphoreLetter", 0), state);
                if (c instanceof Character) s.append("\nsemaphore = ").append((int) ((Character)c).charValue());
            } catch (Throwable ignored) {}
            try {
                Object a = invoke(findMethod(state.getClass(), "getBrightnessArray", 0), state);
                if (a instanceof List) s.append("\nbrightnessArray = ").append(a);
            } catch (Throwable ignored) {}
            return s.toString();
        } catch (Throwable t) {
            return "Current LightState ERROR: " + rootMessage(t);
        }
    }

    synchronized String stop() {
        try {
            if (service != null && sessionOpen) invoke(closeSession, service, token);
            sessionOpen = false;
            priority = Integer.MIN_VALUE;
            return "LightState session stopped";
        } catch (Throwable t) {
            sessionOpen = false;
            priority = Integer.MIN_VALUE;
            return "LightState stop ERROR: " + rootMessage(t);
        }
    }

    private void initialize() throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        Method getService = sm.getDeclaredMethod("getService", String.class);
        getService.setAccessible(true);
        IBinder binder = (IBinder) getService.invoke(null, "lights");
        if (binder == null) throw new IllegalStateException("lights binder is null");

        Class<?> stub = Class.forName("android.hardware.lights.ILightsManager$Stub");
        Method asInterface = stub.getDeclaredMethod("asInterface", IBinder.class);
        asInterface.setAccessible(true);
        service = asInterface.invoke(null, binder);
        if (service == null) throw new IllegalStateException("ILightsManager is null");

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
        if (lights == null) throw new IllegalStateException("getLights returned null");
        for (Object light : lights) {
            int type = ((Number) invoke(findMethod(light.getClass(), "getType", 0), light)).intValue();
            if (type == breatheType) {
                ringLightId = ((Number) invoke(findMethod(light.getClass(), "getId", 0), light)).intValue();
                return;
            }
        }
        throw new IllegalStateException("No LIGHT_TYPE_BREATHE light");
    }

    private void ensureSession(int requestedPriority) throws Exception {
        if (sessionOpen && priority == requestedPriority) return;
        if (sessionOpen) invoke(closeSession, service, token);
        invoke(openSession, service, token, requestedPriority);
        sessionOpen = true;
        priority = requestedPriority;
    }

    private static void appendNumber(StringBuilder out, Object state, String label, String method) {
        try { out.append('\n').append(label).append(" = ").append(invoke(findMethod(state.getClass(), method, 0), state)); }
        catch (Throwable ignored) {}
    }

    private static void appendHex(StringBuilder out, Object state, String label, String method) {
        try {
            Object value = invoke(findMethod(state.getClass(), method, 0), state);
            if (value instanceof Number) out.append('\n').append(label).append(" = ").append(String.format("#%08X", ((Number)value).intValue()));
        } catch (Throwable ignored) {}
    }

    private static int staticInt(Class<?> cls, String name) throws Exception {
        java.lang.reflect.Field f;
        try { f = cls.getDeclaredField(name); }
        catch (NoSuchFieldException e) { f = cls.getField(name); }
        f.setAccessible(true);
        return f.getInt(null);
    }

    private static Method findMethod(Class<?> cls, String name, int count) throws NoSuchMethodException {
        Class<?> c = cls;
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == count) {
                    m.setAccessible(true);
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == count) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new NoSuchMethodException(cls.getName() + "." + name + "/" + count);
    }

    private static Object invoke(Method method, Object target, Object... args) throws Exception {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof Exception) throw (Exception)c;
            if (c instanceof Error) throw (Error)c;
            throw e;
        }
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private static String rootMessage(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null && x.getCause() != x) x = x.getCause();
        return x.getClass().getSimpleName() + ": " + String.valueOf(x.getMessage());
    }
}
