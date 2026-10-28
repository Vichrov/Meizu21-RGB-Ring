package com.vichrov.meizu21rgbring;

import android.os.IBinder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import vendor.meizu.hardware.aw_light.IAwLight;
import vendor.meizu.hardware.aw_light.IAwLightClient;
import vendor.meizu.hardware.aw_light.LightRequest;
import vendor.meizu.hardware.aw_light.LightState;

/** Experimental direct client for Meizu's aw_light Binder extension. */
final class AwBinderController {
    private static final String HAL_SERVICE = "android.hardware.light.ILights/default";
    private static final int REQUEST_ID = 31;

    private IBinder halBinder;
    private IBinder extensionBinder;
    private IAwLight remote;
    private String lastCallback = "<none>";

    private final IAwLightClient client = new IAwLightClient.Stub() {
        @Override
        public void onStateChanged(LightState state) {
            lastCallback = formatState(state);
        }
    };

    synchronized String diagnostics() {
        StringBuilder out = new StringBuilder("Meizu aw_light Binder diagnostics\n");
        try {
            connect();
            out.append("HAL service = ").append(HAL_SERVICE).append('\n');
            out.append("HAL binder = ").append(halBinder != null).append('\n');
            out.append("Extension binder = ").append(extensionBinder != null).append('\n');
            if (extensionBinder != null) {
                try {
                    out.append("Extension descriptor = ")
                            .append(extensionBinder.getInterfaceDescriptor()).append('\n');
                } catch (Throwable t) {
                    out.append("Extension descriptor ERROR = ").append(RingController.rootMessage(t)).append('\n');
                }
            }
            out.append("IAwLight proxy = ").append(remote != null).append('\n');
            if (remote != null) {
                try {
                    out.append("Current state = ").append(formatState(remote.getState())).append('\n');
                } catch (Throwable t) {
                    out.append("getState ERROR = ").append(RingController.rootMessage(t)).append('\n');
                }
            }
            out.append("Last callback = ").append(lastCallback);
        } catch (Throwable t) {
            out.append("CONNECT ERROR = ").append(RingController.rootMessage(t));
        }
        return out.toString();
    }

    synchronized String startEffect(int effect, int argb, int strengthPercent, int amplitude, int progress) {
        try {
            connect();
            LightRequest request = new LightRequest();
            request.effect = effect;
            request.color = argb & 0x00FFFFFF;
            request.strength = clamp(strengthPercent, 0, 100) / 100.0f;
            request.progress = clamp(progress, 0, 100);
            request.amplitude = clamp(amplitude, 0, 60);
            request.letter = 0;
            request.priority = 75;
            request.timeoutMs = 0;
            request.resume = true;

            remote.request(client, REQUEST_ID, request);
            LightState state = remote.getState();
            return "AW REQUEST OK: effect=" + effect
                    + " color=#" + String.format("%06X", request.color)
                    + " strength=" + strengthPercent + "%"
                    + " amplitude=" + request.amplitude
                    + " progress=" + request.progress
                    + "\nState: " + formatState(state);
        } catch (Throwable t) {
            return "AW REQUEST ERROR: " + RingController.rootMessage(t);
        }
    }

    synchronized String stop() {
        try {
            if (remote == null) connect();
            try {
                remote.cancel(client, REQUEST_ID);
            } catch (Throwable ignored) {
            }
            try {
                remote.release(client);
            } catch (Throwable ignored) {
            }
            LightState state = null;
            try {
                state = remote.getState();
            } catch (Throwable ignored) {
            }
            return "AW STOP OK" + (state == null ? "" : "\nState: " + formatState(state));
        } catch (Throwable t) {
            return "AW STOP ERROR: " + RingController.rootMessage(t);
        }
    }

    private void connect() throws Exception {
        if (remote != null && extensionBinder != null && extensionBinder.pingBinder()) return;

        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Method checkService = findMethod(serviceManager, "checkService", 1);
        halBinder = (IBinder) invoke(checkService, null, HAL_SERVICE);
        if (halBinder == null) {
            // Some builds expose only getService(); try it as a fallback.
            Method getService = findMethod(serviceManager, "getService", 1);
            halBinder = (IBinder) invoke(getService, null, HAL_SERVICE);
        }
        if (halBinder == null) throw new IllegalStateException("HAL service not found: " + HAL_SERVICE);

        Method getExtension = findMethod(halBinder.getClass(), "getExtension", 0);
        extensionBinder = (IBinder) invoke(getExtension, halBinder);
        if (extensionBinder == null) throw new IllegalStateException("Lights HAL Binder extension is null");

        remote = IAwLight.Stub.asInterface(extensionBinder);
        if (remote == null) throw new IllegalStateException("IAwLight.Stub.asInterface returned null");
    }

    private static String formatState(LightState state) {
        if (state == null) return "null";
        return "owner=" + state.owner
                + " requestId=" + state.requestId
                + " effect=" + state.effect
                + " active=" + state.active
                + " animated=" + state.animated
                + " error=" + state.error;
    }

    private static Method findMethod(Class<?> cls, String name, int parameterCount) throws NoSuchMethodException {
        Class<?> current = cls;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        for (Method method : cls.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(cls.getName() + "." + name + "/" + parameterCount);
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
}
