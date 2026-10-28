package com.vichrov.meizu21rgbring;

/** Runs in Shizuku/Sui's shell/root process. */
public final class RingUserService extends IRingService.Stub {
    private RingController controller;
    private final AwBinderController awController = new AwBinderController();
    private final StockEffectsController stockController = new StockEffectsController();
    private final MusicPreviewController musicPreview = new MusicPreviewController();
    private final ExperimentalStateController experimentalController = new ExperimentalStateController();
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
        musicPreview.stop();
        stockController.stop();
        experimentalController.stop();
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.setPureColor(argb, brightness);
    }

    @Override
    public String setRainbow(int brightness) {
        musicPreview.stop();
        stockController.stop();
        experimentalController.stop();
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.setRainbow(brightness);
    }

    @Override
    public String startSoftwareRainbow(int brightness) {
        musicPreview.stop();
        stockController.stop();
        experimentalController.stop();
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
        musicPreview.stop();
        stockController.stop();
        experimentalController.stop();
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.setSingleLed(index, argb, brightness);
    }

    @Override
    public String setAllLeds(int argb, int brightness) {
        musicPreview.stop();
        stockController.stop();
        experimentalController.stop();
        if (controller == null) return "INIT ERROR: " + initError;
        return controller.setAllLeds(argb, brightness);
    }

    @Override
    public String startThreeColorSpin(int brightness) {
        musicPreview.stop();
        stockController.stop();
        experimentalController.stop();
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
        return "v0.7: stock Flyme mechanisms + automatic music capture / brightnessArray tests.\n\n"
                + stockController.diagnostics() + "\n\n" + musicPreview.diagnostics();
    }

    @Override
    public String startAwEffect(int effect, int argb, int strengthPercent, int amplitude, int progress) {
        awController.stop();
        experimentalController.stop();
        if (controller != null) controller.off();
        stockController.stop();

        if (effect == 38) {
            return "STOCK MUSIC PREVIEW: " + musicPreview.start();
        }

        musicPreview.stop();
        return "STOCK LIGHTS PATH: "
                + stockController.setEffect(effect, argb, StockEffectsController.SETTINGS_PREVIEW_PRIORITY);
    }

    @Override
    public String stopAwEffect() {
        String a = musicPreview.stop();
        String b = stockController.stop();
        String c = experimentalController.stop();
        return a + "\n" + b + "\n" + c;
    }

    @Override
    public String stockDiagnostics() {
        return stockController.diagnostics() + "\n\n" + musicPreview.diagnostics();
    }

    @Override
    public String startStockEffect(int effect, int argb, int priority) {
        awController.stop();
        musicPreview.stop();
        experimentalController.stop();
        if (controller != null) controller.off();
        return stockController.setEffect(effect, argb, priority);
    }

    @Override
    public String stopStockEffect() {
        return stockController.stop();
    }

    @Override
    public String runAutomaticMusicCapture() {
        awController.stop();
        stockController.stop();
        experimentalController.stop();
        if (controller != null) controller.off();
        String start = musicPreview.start();
        try { Thread.sleep(350); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        String capture = experimentalController.captureState(35, 40);
        String stop = musicPreview.stop();
        return start + "\n\n" + capture + "\n" + stop;
    }

    @Override
    public String testBrightnessArray(int pattern, int argb) {
        awController.stop();
        musicPreview.stop();
        stockController.stop();
        if (controller != null) controller.off();
        experimentalController.stop();
        return experimentalController.setBrightnessPattern(pattern, argb);
    }

    @Override
    public String stopBrightnessArrayTest() {
        return experimentalController.stop();
    }

    @Override
    public String off() {
        String aw = awController.stop();
        String stock = stockController.stop();
        String music = musicPreview.stop();
        String experimental = experimentalController.stop();
        if (controller == null) return "INIT ERROR: " + initError + "\n" + aw + "\n" + stock + "\n" + music + "\n" + experimental;
        return controller.off() + "\n" + aw + "\n" + stock + "\n" + music + "\n" + experimental;
    }
}
