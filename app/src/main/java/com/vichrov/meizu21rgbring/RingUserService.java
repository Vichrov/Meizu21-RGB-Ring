package com.vichrov.meizu21rgbring;

/** Runs in Shizuku/Sui's shell/root process. */
public final class RingUserService extends IRingService.Stub {
    private RingController controller;
    private final AwBinderController awController = new AwBinderController();
    private final StockEffectsController stockController = new StockEffectsController();
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
        stockController.stop();
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.setPureColor(argb, brightness);
    }

    @Override
    public String setRainbow(int brightness) {
        stockController.stop();
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.setRainbow(brightness);
    }

    @Override
    public String startSoftwareRainbow(int brightness) {
        stockController.stop();
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
        stockController.stop();
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.setSingleLed(index, argb, brightness);
    }

    @Override
    public String setAllLeds(int argb, int brightness) {
        stockController.stop();
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.setAllLeds(argb, brightness);
    }

    @Override
    public String startThreeColorSpin(int brightness) {
        stockController.stop();
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.startThreeColorSpin(brightness);
    }

    @Override
    public String clearLedFrame() {
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.clearLedFrame();
    }

    /*
     * v0.4 exposed these as aw_light Binder tests. Stock Flyme on the tested Meizu 21
     * rejects that reconstructed Binder ABI. Keep the UI/AIDL compatible, but route the
     * buttons through the exact LightsManager path recovered from Settings 12.1.0.
     */
    @Override
    public String awDiagnostics() {
        return "v0.5: aw Binder disabled; using stock Settings Lights API instead.\n\n"
                + stockController.diagnostics();
    }

    @Override
    public String startAwEffect(int effect, int argb, int strengthPercent, int amplitude, int progress) {
        awController.stop();
        if (controller != null) controller.off();
        return "v0.5 STOCK PATH: "
                + stockController.setEffect(effect, argb, StockEffectsController.SETTINGS_PREVIEW_PRIORITY);
    }

    @Override
    public String stopAwEffect() {
        return stockController.stop();
    }

    @Override
    public String stockDiagnostics() {
        return stockController.diagnostics();
    }

    @Override
    public String startStockEffect(int effect, int argb, int priority) {
        awController.stop();
        if (controller != null) controller.off();
        return stockController.setEffect(effect, argb, priority);
    }

    @Override
    public String stopStockEffect() {
        return stockController.stop();
    }

    @Override
    public String off() {
        String aw = awController.stop();
        String stock = stockController.stop();
        if (controller == null) return "INIT ERROR: " + initError + "\n" + aw + "\n" + stock;
        return controller.off() + "\n" + aw + "\n" + stock;
    }
}
