package com.vichrov.meizu21rgbring;

/** Runs in Shizuku/Sui's shell/root process. */
public final class RingUserService extends IRingService.Stub {
    private RingController controller;
    private final AwBinderController awController = new AwBinderController();
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
    public String sysfsDiagnostics() {
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.sysfsDiagnostics();
    }

    @Override
    public String setSingleLed(int index, int argb, int brightness) {
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.setSingleLed(index, argb, brightness);
    }

    @Override
    public String setAllLeds(int argb, int brightness) {
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.setAllLeds(argb, brightness);
    }

    @Override
    public String startThreeColorSpin(int brightness) {
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.startThreeColorSpin(brightness);
    }

    @Override
    public String clearLedFrame() {
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.clearLedFrame();
    }

    @Override
    public String awDiagnostics() {
        return awController.diagnostics();
    }

    @Override
    public String startAwEffect(int effect, int argb, int strengthPercent, int amplitude, int progress) {
        return awController.startEffect(effect, argb, strengthPercent, amplitude, progress);
    }

    @Override
    public String stopAwEffect() {
        return awController.stop();
    }

    @Override
    public String off() {
        String aw = awController.stop();
        if (controller == null) return "INIT ERROR: " + initError + "\n" + aw;
        return controller.off() + "\n" + aw;
    }
}
