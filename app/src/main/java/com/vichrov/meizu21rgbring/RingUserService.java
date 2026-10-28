package com.vichrov.meizu21rgbring;

/** Runs in Shizuku/Sui's shell/root process. */
public final class RingUserService extends IRingService.Stub {
    private RingController controller;
    private String initError;

    public RingUserService() {
        try {
            controller = new RingController();
        } catch (Throwable t) {
            initError = RingController.rootMessage(t);
        }
    }

    @Override
    public String diagnostics() {
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.diagnostics();
    }

    @Override
    public String setPureColor(int argb, int brightness) {
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.setPureColor(argb, brightness);
    }

    @Override
    public String setRainbow(int brightness) {
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.setRainbow(brightness);
    }

    @Override
    public String startSoftwareRainbow(int brightness) {
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.startSoftwareRainbow(brightness);
    }

    @Override
    public String off() {
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.off();
    }
}
