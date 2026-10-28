package com.vichrov.meizu21rgbring;

import android.os.Binder;
import android.os.IBinder;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Stock Flyme Lights API explorer reconstructed from Meizu Settings 12.1.0.
 *
 * Settings uses LIGHT_TYPE_BREATHE=10, opens a Lights session with priority 10009,
 * then submits LightState.Builder().setEffectId(effect).setColor(color).
 */
final class StockEffectsController {
    static final int SETTINGS_PREVIEW_PRIORITY = 10009;

    private final IBinder token = new Binder();
    private Object service;
    private Method getLights;
    private Method openSession;
    private Method closeSession;
    private Method setLightStates;
    private Class<?> lightStateClass;
    private int breatheType;
    private int ringLightId = -1;
    private boolean sessionOpen;
    private int openPriority = Integer.MIN_VALUE;

    StockEffectsController() {
        try {
            initialize();
        } catch (Throwable ignored) {
        }
    }

    synchronized String diagnostics() {
        StringBuilder out = new StringBuilder("Stock Flyme Lights API diagnostics\n");
        try {
            if (service == null) initialize();
            out.append("Settings preview priority = ").append(SETTINGS_PREVIEW_PRIORITY).append('\n');
            out.append("LIGHT_TYPE_BREATHE = ").append(breatheType).append('\n');
            out.append("Selected ring light id = ").append(ringLightId).append('\n');
            out.append("Session open = ").append(sessionOpen).append('\n');
            out.append("\nKnown from uploaded stock APKs:\n");
            out.append("  EFFECT 32 = rainbow (Tools)\n");
            out.append("  EFFECT 47 = pure color (Tools)\n");
            out.append("  EFFECT 65 = in-call light (Settings)\n");
            out.append("  EFFECT 66 = notification light (Settings)\n");
            out.append("  music preview AudioAttributes tag = light_preview_music\n");

            Class<?> lightState = Class.forName("android.hardware.lights.LightState");
            Class<?> light = Class.forName("android.hardware.lights.Light");
            Class<?> manager = Class.forName("android.hardware.lights.LightsManager");
            Class<?> builder = Class.forName("android.hardware.lights.LightState$Builder");
            Class<?> iface = Class.forName("android.hardware.lights.ILightsManager");

            appendIntConstants(out, "LightState EFFECT_ID_*", lightState, "EFFECT_ID_");
            appendIntConstants(out, "Light LIGHT_TYPE_*", light, "LIGHT_TYPE_");
            appendIntConstants(out, "Light SESSION_PRIORITY_*", light, "SESSION_PRIORITY_");
            appendIntConstants(out, "LightsManager SESSION_PRIORITY_*", manager, "SESSION_PRIORITY_");
            appendMethods(out, "LightState.Builder methods", builder);
            appendMethods(out, "LightsManager methods", manager);
            appendMethods(out, "ILightsManager methods", iface);
        } catch (Throwable t) {
            out.append("ERROR: ").append(rootMessage(t)).append('\n');
        }
        return out.toString();
    }

    synchronized String setEffect(int effectId, int argb, int priority) {
        try {
            if (service == null) initialize();
            if (effectId < 0 || effectId > 255) return "STOCK EFFECT ERROR: effect must be 0..255";
            priority = Math.max(0, Math.min(20000, priority));
            ensureSession(priority);

            Class<?> builderClass = Class.forName("android.hardware.lights.LightState$Builder");
            Object builder = builderClass.getDeclaredConstructor().newInstance();
            Method setEffectId = findMethod(builderClass, "setEffectId", 1);
            Method setColor = findMethod(builderClass, "setColor", 1);
            Method build = findMethod(builderClass, "build", 0);
            invoke(setEffectId, builder, effectId);
            invoke(setColor, builder, argb);
            Object state = invoke(build, builder);

            Object states = Array.newInstance(lightStateClass, 1);
            Array.set(states, 0, state);
            invoke(setLightStates, service, token, new int[]{ringLightId}, states);
            return String.format("STOCK EFFECT OK: id=%d color=#%08X priority=%d", effectId, argb, priority);
        } catch (Throwable t) {
            return "STOCK EFFECT ERROR: " + rootMessage(t);
        }
    }

    synchronized String stop() {
        try {
            if (service != null && sessionOpen) invoke(closeSession, service, token);
            sessionOpen = false;
            openPriority = Integer.MIN_VALUE;
            return "STOCK EFFECT STOP OK";
        } catch (Throwable t) {
            sessionOpen = false;
            openPriority = Integer.MIN_VALUE;
            return "STOCK EFFECT STOP ERROR: " + rootMessage(t);
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
        if (service == null) throw new IllegalStateException("ILightsManager is null");

        Class<?> iface = Class.forName("android.hardware.lights.ILightsManager");
        getLights = findMethod(iface, "getLights", 0);
        openSession = findMethod(iface, "openSession", 2);
        closeSession = findMethod(iface, "closeSession", 1);
        setLightStates = findMethod(iface, "setLightStates", 3);
        lightStateClass = Class.forName("android.hardware.lights.LightState");

        Class<?> lightClass = Class.forName("android.hardware.lights.Light");
        breatheType = staticInt(lightClass, "LIGHT_TYPE_BREATHE");
        selectRingLight();
    }

    private void selectRingLight() throws Exception {
        List<?> lights = (List<?>) invoke(getLights, service);
        if (lights == null) throw new IllegalStateException("getLights returned null");
        for (Object light : lights) {
            Method getType = findMethod(light.getClass(), "getType", 0);
            int type = ((Number) invoke(getType, light)).intValue();
            if (type == breatheType) {
                Method getId = findMethod(light.getClass(), "getId", 0);
                ringLightId = ((Number) invoke(getId, light)).intValue();
                return;
            }
        }
        throw new IllegalStateException("No LIGHT_TYPE_BREATHE light");
    }

    private void ensureSession(int priority) throws Exception {
        if (sessionOpen && openPriority == priority) return;
        if (sessionOpen) {
            invoke(closeSession, service, token);
            sessionOpen = false;
        }
        invoke(openSession, service, token, priority);
        sessionOpen = true;
        openPriority = priority;
    }

    private static void appendIntConstants(StringBuilder out, String title, Class<?> cls, String prefix) {
        out.append("\n").append(title).append(":\n");
        List<Field> fields = new ArrayList<>();
        Collections.addAll(fields, cls.getDeclaredFields());
        fields.sort(Comparator.comparing(Field::getName));
        int count = 0;
        for (Field f : fields) {
            if (!f.getName().startsWith(prefix)) continue;
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != int.class) continue;
            try {
                f.setAccessible(true);
                out.append("  ").append(f.getName()).append(" = ").append(f.getInt(null)).append('\n');
                count++;
            } catch (Throwable t) {
                out.append("  ").append(f.getName()).append(" = <").append(t.getClass().getSimpleName()).append(">\n");
            }
        }
        if (count == 0) out.append("  <none visible>\n");
    }

    private static void appendMethods(StringBuilder out, String title, Class<?> cls) {
        out.append("\n").append(title).append(":\n");
        List<String> names = new ArrayList<>();
        for (Method m : cls.getDeclaredMethods()) {
            StringBuilder s = new StringBuilder("  ").append(m.getName()).append('(');
            Class<?>[] p = m.getParameterTypes();
            for (int i = 0; i < p.length; i++) {
                if (i > 0) s.append(',');
                s.append(p[i].getSimpleName());
            }
            s.append(") -> ").append(m.getReturnType().getSimpleName());
            names.add(s.toString());
        }
        Collections.sort(names);
        for (String s : names) out.append(s).append('\n');
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
        Field f;
        try { f = cls.getDeclaredField(name); }
        catch (NoSuchFieldException e) { f = cls.getField(name); }
        f.setAccessible(true);
        return f.getInt(null);
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
