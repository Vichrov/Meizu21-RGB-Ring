package com.vichrov.meizu21rgbring;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import dalvik.system.PathClassLoader;

/** Direct client for stock vendor.meizu.hardware.aw_light@1.0::IAwLight. */
final class DirectHidlController {
    private static final String SERVICES_JAR = "/system/framework/services.jar";
    private static final String IFACE = "vendor.meizu.hardware.aw_light.V1_0.IAwLight";
    private static final int PHASE_COUNT = 20;
    private static final int LEVEL = 60;

    private Object remote;
    private Method init, start, stop, switchEffect, colorPhase, musicPhase;
    private volatile boolean stopAnimation;
    private Thread animationThread;

    synchronized String diagnostics() {
        StringBuilder out = new StringBuilder("Direct HIDL diagnostics — stock getService(true)\n");
        try {
            connect();
            out.append("services.jar = ").append(SERVICES_JAR).append('\n');
            out.append("interface = ").append(IFACE).append('\n');
            out.append("remote class = ").append(remote.getClass().getName()).append('\n');
            try {
                Method descriptor = findMethod(remote.getClass(), "interfaceDescriptor", 0);
                out.append("descriptor = ").append(invoke(descriptor, remote)).append('\n');
            } catch (Throwable t) {
                out.append("descriptor = <").append(rootMessage(t)).append(">\n");
            }
            out.append("aw_light_init() = ").append(invokeBool(init)).append('\n');
            out.append("CONNECT OK");
        } catch (Throwable t) {
            out.append("CONNECT ERROR: ").append(rootMessage(t));
        }
        return out.toString();
    }

    synchronized String runTest(int testId, int argb) {
        try {
            connect();
            stopAnimationLocked();
            int r = (argb >> 16) & 0xff;
            int g = (argb >> 8) & 0xff;
            int b = argb & 0xff;

            switch (testId) {
                case 1:
                    return runStatic("PURE 47 + selected color", 47, r, g, b, null, false);
                case 2:
                    return runStatic("MUSIC 38: color + phase BEFORE start", 38, r, g, b, oneHot(0), false);
                case 3:
                    return runStatic("MUSIC 38: start BEFORE color + phase", 38, r, g, b, oneHot(0), true);
                case 4:
                    return startSweep(38, r, g, b, "MUSIC 38 direct sweep; force color each frame");
                case 5:
                    return runStatic("PURE 47 + one-hot music phase", 47, r, g, b, oneHot(0), false);
                case 6:
                    return startSweep(47, r, g, b, "PURE 47 + moving music phase");
                case 7:
                    return runNoSwitch(r, g, b);
                case 8:
                    return runStatic("MUSIC 38 + all 20 = 60", 38, r, g, b, filled(LEVEL), false);
                default:
                    return "DIRECT HIDL ERROR: unknown test " + testId;
            }
        } catch (Throwable t) {
            return "DIRECT HIDL ERROR: " + rootMessage(t);
        }
    }

    synchronized String stopAll() {
        try {
            stopAnimationLocked();
            connect();
            return "DIRECT HIDL STOP: aw_light_stop() = " + invokeBool(stop);
        } catch (Throwable t) {
            return "DIRECT HIDL STOP ERROR: " + rootMessage(t);
        }
    }

    private String runStatic(String label, int effect, int r, int g, int b,
                             ArrayList<Integer> phase, boolean startBeforeData) throws Exception {
        StringBuilder out = new StringBuilder(label).append('\n');
        out.append("stop = ").append(invokeBool(stop)).append('\n');
        out.append("init = ").append(invokeBool(init)).append('\n');
        out.append("switchEffect(").append(effect).append(") = ")
                .append(invokeBool(switchEffect, effect)).append('\n');
        if (startBeforeData) out.append("start = ").append(invokeBool(start)).append('\n');
        out.append("colorPhase(").append(r).append(',').append(g).append(',').append(b).append(") = ")
                .append(invokeBool(colorPhase, r, g, b)).append('\n');
        if (phase != null) {
            out.append("musicPhase(size=").append(phase.size()).append(") = ")
                    .append(invokeBool(musicPhase, phase)).append('\n');
        }
        if (!startBeforeData) out.append("start = ").append(invokeBool(start)).append('\n');
        return out.toString();
    }

    private String runNoSwitch(int r, int g, int b) throws Exception {
        ArrayList<Integer> phase = oneHot(0);
        StringBuilder out = new StringBuilder("NO switchEffect: init -> color -> musicPhase -> start\n");
        out.append("stop = ").append(invokeBool(stop)).append('\n');
        out.append("init = ").append(invokeBool(init)).append('\n');
        out.append("colorPhase = ").append(invokeBool(colorPhase, r, g, b)).append('\n');
        out.append("musicPhase = ").append(invokeBool(musicPhase, phase)).append('\n');
        out.append("start = ").append(invokeBool(start)).append('\n');
        return out.toString();
    }

    private String startSweep(int effect, int r, int g, int b, String label) throws Exception {
        boolean st = invokeBool(stop);
        boolean in = invokeBool(init);
        boolean sw = invokeBool(switchEffect, effect);
        boolean co = invokeBool(colorPhase, r, g, b);
        boolean sa = invokeBool(start);

        stopAnimation = false;
        animationThread = new Thread(() -> {
            int index = 0;
            try {
                while (!stopAnimation) {
                    ArrayList<Integer> phase = oneHot(index);
                    synchronized (DirectHidlController.this) {
                        if (stopAnimation) break;
                        invokeBool(colorPhase, r, g, b);
                        invokeBool(musicPhase, phase);
                    }
                    index = (index + 1) % PHASE_COUNT;
                    Thread.sleep(120);
                }
            } catch (Throwable ignored) {
            }
        }, "direct-aw-hidl-sweep");
        animationThread.setDaemon(true);
        animationThread.start();

        return label + "\nstop=" + st + " init=" + in + " switch=" + sw
                + " color=" + co + " start=" + sa
                + "\none-hot 20 phases, level=60, step=120 ms";
    }

    /**
     * Stock LightsService$IAwLightWrapper.connectToHal() calls IAwLight.getService(true)
     * directly. v1.3 mistakenly tried false first; on Flyme that throws
     * NoSuchElementException and prevented the correct true call from ever running.
     */
    private void connect() throws Exception {
        if (remote != null) return;

        PathClassLoader loader = new PathClassLoader(SERVICES_JAR, ClassLoader.getSystemClassLoader());
        Class<?> ifaceClass = Class.forName(IFACE, true, loader);
        Method getService = null;
        for (Method m : ifaceClass.getDeclaredMethods()) {
            if (!m.getName().equals("getService")) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 1 && p[0] == boolean.class) {
                getService = m;
                break;
            }
        }
        if (getService == null) throw new NoSuchMethodException(IFACE + ".getService(boolean)");
        getService.setAccessible(true);

        // Exact stock behavior recovered from services.jar: getService(true).
        remote = invoke(getService, null, true);
        if (remote == null) throw new IllegalStateException("IAwLight.getService(true) returned null");

        Class<?> rc = remote.getClass();
        init = findMethod(rc, "aw_light_init", 0);
        start = findMethod(rc, "aw_light_start", 0);
        stop = findMethod(rc, "aw_light_stop", 0);
        switchEffect = findMethod(rc, "aw_light_switchEffect", 1);
        colorPhase = findMethod(rc, "aw_color_set_phase", 3);
        musicPhase = findMethod(rc, "aw_music_phase", 1);
    }

    private static ArrayList<Integer> oneHot(int index) {
        ArrayList<Integer> values = new ArrayList<>(PHASE_COUNT);
        for (int i = 0; i < PHASE_COUNT; i++) values.add(i == index ? LEVEL : 0);
        return values;
    }

    private static ArrayList<Integer> filled(int value) {
        ArrayList<Integer> values = new ArrayList<>(PHASE_COUNT);
        for (int i = 0; i < PHASE_COUNT; i++) values.add(value);
        return values;
    }

    private void stopAnimationLocked() {
        stopAnimation = true;
        Thread t = animationThread;
        animationThread = null;
        if (t != null) t.interrupt();
    }

    private boolean invokeBool(Method m, Object... args) throws Exception {
        Object value = invoke(m, remote, args);
        if (!(value instanceof Boolean)) throw new IllegalStateException(m.getName() + " did not return boolean");
        return (Boolean) value;
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
        for (Class<?> i : cls.getInterfaces()) {
            for (Method m : i.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == count) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        throw new NoSuchMethodException(cls.getName() + "." + name + "/" + count);
    }

    private static Object invoke(Method method, Object target, Object... args) throws Exception {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
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
