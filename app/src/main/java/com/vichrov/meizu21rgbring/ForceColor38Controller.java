package com.vichrov.meizu21rgbring;

import android.os.Binder;
import android.os.IBinder;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * v1.2 experiment: EFFECT_ID_MUSIC=38 with a forced global RGB color and the same
 * 20-value phase array that produced the moving highlight in v1.0.
 *
 * services.jar shows LightsService always forwards LightState.getColor() to
 * aw_color_set_phase(), even for effect 38. Earlier tests simply left color=0.
 */
final class ForceColor38Controller {
    private static final int EFFECT = 38;
    private static final int PRIORITY = 10009;
    private static final int COUNT = 20;
    private static final int LEVEL = 60;

    private final IBinder token = new Binder();
    private Object service;
    private Method getLights, openSession, closeSession, setLightStates;
    private Class<?> lightStateClass;
    private int ringLightId = -1;
    private int breatheType;
    private boolean sessionOpen;

    private volatile boolean stopAnimation;
    private Thread animationThread;

    ForceColor38Controller() {
        try { initialize(); } catch (Throwable ignored) {}
    }

    synchronized String runTest(int testId, int argb) {
        try {
            if (service == null) initialize();
            stopAnimationLocked();
            ensureSession();

            switch (testId) {
                case 1: {
                    ArrayList<Integer> v = oneHot(0);
                    send(argb, v, true);
                    return sent("forced color one-hot phase 0", argb, v, true);
                }
                case 2: {
                    ArrayList<Integer> v = oneHot(5);
                    send(argb, v, true);
                    return sent("forced color one-hot phase 5", argb, v, true);
                }
                case 3:
                    return startSweep(argb, true);
                case 4: {
                    ArrayList<Integer> v = all(LEVEL);
                    send(argb, v, true);
                    return sent("forced color all 20 = 60", argb, v, true);
                }
                case 5: {
                    ArrayList<Integer> v = alternating();
                    send(argb, v, true);
                    return sent("forced color alternating 60/0", argb, v, true);
                }
                case 6:
                    return startColorFlip();
                case 7:
                    return startSweep(argb, false); // stock-like reference: no setColor()
                default:
                    return "COLOR38 ERROR: unknown test " + testId;
            }
        } catch (Throwable t) {
            return "COLOR38 ERROR: " + rootMessage(t);
        }
    }

    private String startSweep(int argb, boolean forceColor) {
        stopAnimation = false;
        animationThread = new Thread(() -> {
            int index = 0;
            try {
                while (!stopAnimation) {
                    ArrayList<Integer> v = oneHot(index);
                    synchronized (ForceColor38Controller.this) {
                        if (stopAnimation) break;
                        send(argb, v, forceColor);
                    }
                    index = (index + 1) % COUNT;
                    Thread.sleep(120);
                }
            } catch (Throwable ignored) {}
        }, "effect38-color-sweep");
        animationThread.setDaemon(true);
        animationThread.start();
        return "EFFECT 38 SWEEP STARTED: forceColor=" + forceColor
                + " color=" + String.format("#%08X", argb)
                + " one-hot 20 phases, 120ms/step, 2.4s/cycle";
    }

    private String startColorFlip() {
        stopAnimation = false;
        animationThread = new Thread(() -> {
            boolean red = true;
            ArrayList<Integer> v = oneHot(0);
            try {
                while (!stopAnimation) {
                    int c = red ? 0xFFFF0000 : 0xFF0000FF;
                    synchronized (ForceColor38Controller.this) {
                        if (stopAnimation) break;
                        send(c, v, true);
                    }
                    red = !red;
                    Thread.sleep(700);
                }
            } catch (Throwable ignored) {}
        }, "effect38-color-flip");
        animationThread.setDaemon(true);
        animationThread.start();
        return "EFFECT 38 COLOR FLIP STARTED: fixed phase 0, RED/BLUE every 700ms";
    }

    private void send(int argb, ArrayList<Integer> values, boolean forceColor) throws Exception {
        Class<?> builderClass = Class.forName("android.hardware.lights.LightState$Builder");
        Object builder = builderClass.getDeclaredConstructor().newInstance();
        invoke(findMethod(builderClass, "setEffectId", 1), builder, EFFECT);
        if (forceColor) invoke(findMethod(builderClass, "setColor", 1), builder, argb);
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
            return "COLOR38 STOP OK";
        } catch (Throwable t) {
            sessionOpen = false;
            return "COLOR38 STOP ERROR: " + rootMessage(t);
        }
    }

    private String sent(String desc, int argb, ArrayList<Integer> values, boolean forceColor) {
        return "EFFECT 38 SENT: " + desc
                + " forceColor=" + forceColor
                + " color=" + String.format("#%08X", argb)
                + " values=" + values;
    }

    private ArrayList<Integer> oneHot(int index) {
        ArrayList<Integer> v = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) v.add(i == index ? LEVEL : 0);
        return v;
    }

    private ArrayList<Integer> alternating() {
        ArrayList<Integer> v = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) v.add((i & 1) == 0 ? LEVEL : 0);
        return v;
    }

    private ArrayList<Integer> all(int value) {
        ArrayList<Integer> v = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) v.add(value);
        return v;
    }

    private void stopAnimationLocked() {
        stopAnimation = true;
        Thread t = animationThread;
        animationThread = null;
        if (t != null) t.interrupt();
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
        openSession = findMethod(iface, "openSession", 2);
        closeSession = findMethod(iface, "closeSession", 1);
        setLightStates = findMethod(iface, "setLightStates", 3);
        lightStateClass = Class.forName("android.hardware.lights.LightState");

        Class<?> light = Class.forName("android.hardware.lights.Light");
        breatheType = staticInt(light, "LIGHT_TYPE_BREATHE");
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
        throw new IllegalStateException("No LIGHT_TYPE_BREATHE light");
    }

    private void ensureSession() throws Exception {
        if (sessionOpen) return;
        invoke(openSession, service, token, PRIORITY);
        sessionOpen = true;
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
        try { f = cls.getDeclaredField(name); }
        catch (NoSuchFieldException e) { f = cls.getField(name); }
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
