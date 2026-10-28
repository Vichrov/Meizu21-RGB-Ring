package com.vichrov.meizu21rgbring;

interface IRingService {
    String diagnostics();
    String setPureColor(int argb, int brightness);
    String setRainbow(int brightness);
    String startSoftwareRainbow(int brightness);
    String off();
}
