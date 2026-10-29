package com.vichrov.meizu21rgbring;

/** Dedicated Shizuku UserService for the v1.2 effect-38 forced-color experiment. */
public final class Color38UserService extends IColor38Service.Stub {
    private final ForceColor38Controller controller = new ForceColor38Controller();

    @Override public String runTest(int testId, int argb) {
        return controller.runTest(testId, argb);
    }

    @Override public String stop() {
        return controller.stop();
    }
}
