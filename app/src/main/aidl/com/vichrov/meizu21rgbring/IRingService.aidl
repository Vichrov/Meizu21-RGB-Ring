package com.vichrov.meizu21rgbring;

interface IRingService {
    String diagnostics();
    String setPureColor(int argb, int brightness);
    String setRainbow(int brightness);
    String startSoftwareRainbow(int brightness);

    String sysfsDiagnostics();
    String setSingleLed(int index, int argb, int brightness);
    String setAllLeds(int argb, int brightness);
    String startThreeColorSpin(int brightness);
    String clearLedFrame();

    String awDiagnostics();
    String startAwEffect(int effect, int argb, int strengthPercent, int amplitude, int progress);
    String stopAwEffect();

    String stockDiagnostics();
    String startStockEffect(int effect, int argb, int priority);
    String stopStockEffect();

    String runAutomaticMusicCapture();
    String testBrightnessArray(int pattern, int argb);
    String stopBrightnessArrayTest();

    String off();
}
