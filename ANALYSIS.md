# Reverse engineering notes — Meizu Tools 12.0.15

Source APK analysed: `com.meizu.flyme.toolbox` (Meizu Tools 12.0.15).

## RGB ring architecture

The flashlight section controls the camera ring through Android's Lights framework plus Meizu/Flyme extensions rather than direct sysfs writes.

Relevant identifiers:

- `LIGHT_TYPE_BREATHE`
- `SESSION_PRIORITY_FLASHLIGHT`
- `EFFECT_ID_PURE`
- `EFFECT_ID_RAINBOW`
- `LightState.Builder.setBrightness(int)`
- `LightState.Builder.setEffectId(int)`
- `LightsManager` / `ILightsManager`

The app selects the breathe light, opens a flashlight-priority session and submits a `LightState` for that light.

## Hardware verification on Meizu 21

The first test APK was run on a real Meizu 21 and returned:

```text
Shizuku UID = 2000 (ADB shell)
CONTROL_DEVICE_LIGHTS = GRANTED
LIGHT_TYPE_BREATHE = 10
SESSION_PRIORITY_FLASHLIGHT = 10010
EFFECT_ID_PURE = 47
EFFECT_ID_RAINBOW = 32
Selected ring light id = 10
```

The following operations were physically verified:

- PURE arbitrary colour: works
- native RAINBOW: works
- OFF by closing the session: works
- ADB-shell Shizuku: sufficient; root/Sui is not required on the tested build

## Brightness behavior

Although the Flyme framework exposes `LightState.Builder.setBrightness(int)`, hardware testing showed no visible difference between brightness `0` and `255` for the camera ring. Changing the ARGB alpha byte also did not provide usable brightness control.

This means the vendor brightness property is accepted by the framework but ignored (or not mapped to LED current) for this specific light/driver path.

v0.2 therefore tests a different mechanism: multiply each RGB channel by the requested brightness before submitting `EFFECT_ID_PURE`.

For example, at roughly 25% brightness:

```text
#FF0000 -> #400000
#00FF00 -> #004000
#8080FF -> #202040
```

If the driver maps RGB channel magnitude to PWM/current, this should provide real brightness control while preserving hue approximately.

## Rainbow

The native smooth colour-cycling mode is `EFFECT_ID_RAINBOW = 32`. It is driver-controlled and does not require Java-side animation, but its brightness appears fixed because the ring ignores the vendor brightness value.

v0.2 therefore keeps the native effect and additionally provides a software rainbow. The software version cycles HSV colours through `EFFECT_ID_PURE` at 25 FPS and applies RGB-channel scaling, allowing brightness testing without modifying the native effect.

## Turning the light off

Closing the Lights session turns the ring off and is hardware verified.

## Permission result

`ILightsManager` requires `android.permission.CONTROL_DEVICE_LIGHTS`. On the tested Flyme build the Shizuku ADB-shell process (UID 2000) has this permission, so the ring can be controlled without root.

## Current status

The core control path is confirmed. The remaining brightness question is whether the LED driver honours RGB channel magnitude as intensity or internally normalizes non-zero colours to a fixed output level.
