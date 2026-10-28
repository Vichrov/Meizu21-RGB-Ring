# Meizu 21 RGB Ring

Experimental controller for the RGB/breathing-light ring around the camera on **Meizu 21 / Flyme 12**.

This project was reconstructed by analysing the stock **Meizu Tools 12.0.15** APK (`com.meizu.flyme.toolbox`). The stock flashlight UI talks to Android's Lights service with Meizu/Flyme framework extensions.

## Hardware-verified findings

Testing on a real Meizu 21 confirmed:

- ADB-shell Shizuku (UID 2000) has `android.permission.CONTROL_DEVICE_LIGHTS` on the tested Flyme build.
- The ring is exposed as light **id 10 / type 10** (`LIGHT_TYPE_BREATHE`).
- `SESSION_PRIORITY_FLASHLIGHT = 10010`.
- `EFFECT_ID_PURE = 47` works for arbitrary RGB colours.
- `EFFECT_ID_RAINBOW = 32` works and produces the native smooth rainbow animation.
- Closing the Lights session turns the ring off.
- Meizu's `LightState.Builder.setBrightness(int)` is accepted, but values from 0 to 255 do **not** change visible ring brightness on the tested device.

Because the vendor brightness field is ignored, v0.2 tests brightness by scaling the actual RGB channel values before sending `EFFECT_ID_PURE`.

## Features

- Connect through Shizuku/Sui
- Enumerate and diagnose the Android Lights service
- Arbitrary RGB selection
- RGB-scaled software brightness (0–255)
- Native `EFFECT_ID_PURE`
- Native smooth `EFFECT_ID_RAINBOW` at driver-controlled brightness
- Software rainbow at adjustable RGB-scaled brightness
- Ring OFF by closing the light session

## Requirements

- Meizu 21 with Flyme exposing the camera RGB ring through the vendor Lights extensions
- Android 12+
- Shizuku (ADB shell works on the tested device) or Sui

## Build

Every push is built by GitHub Actions and uploaded as the **Meizu21-RGB-Ring-debug** artifact.

Local build:

```bash
gradle :app:assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## v0.2 brightness test

1. Connect Shizuku.
2. Pick a colour.
3. Set **Brightness (RGB scaling)** to values such as 255, 128, 64, 16 and 0.
4. Press **Apply selected color (adjustable brightness)** and check whether physical brightness changes while hue stays similar.
5. **Native smooth rainbow (fixed brightness)** uses Meizu's native `EFFECT_ID_RAINBOW` unchanged.
6. **Software rainbow (adjustable brightness)** repeatedly sends `EFFECT_ID_PURE` colours at 25 FPS and applies the RGB brightness scaling.

See [`ANALYSIS.md`](ANALYSIS.md) for reverse-engineering and hardware-test notes.

## Status

**v0.2-brightness-test** — PURE, native RAINBOW, OFF and ADB-shell Shizuku access are hardware verified. RGB-channel scaling is the current brightness experiment.

This project is not affiliated with Meizu.
