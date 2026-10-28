package vendor.meizu.hardware.aw_light;

import vendor.meizu.hardware.aw_light.LightState;

oneway interface IAwLightClient {
    void onStateChanged(in LightState state);
}
