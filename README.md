# Meizu 21 RGB Ring

Experimental controller for the RGB/breathing-light ring around the camera on **Meizu 21 / Flyme 12**.

This project was reconstructed by analysing the stock **Meizu Tools 12.0.15** APK (`com.meizu.flyme.toolbox`). The stock flashlight UI does not drive the ring with a custom animation loop; it talks to Android's Lights service with Meizu/Flyme framework extensions.

## What was found

The stock app:

- looks for a light with `LIGHT_TYPE_BREATHE`;
- opens a high-priority lights session using `SESSION_PRIORITY_FLASHLIGHT`;
- uses `LightState.EFFECT_ID_PURE` for a fixed RGB colour;
- uses `LightState.EFFECT_ID_RAINBOW` for the native smooth rainbow effect;
- uses Meizu's extended `LightState.Builder.setBrightness(int)`;
- turns the ring off by closing the lights session.

See [`ANALYSIS.md`](ANALYSIS.md) for reverse-engineering notes.

## Why Shizuku/Sui is used

Direct access to `ILightsManager` is protected by `android.permission.CONTROL_DEVICE_LIGHTS`, which is a privileged/signature permission. A normal sideloaded APK cannot obtain it.

This test app therefore runs the ring-control code in a **Shizuku UserService**. Depending on the Flyme build, ADB-shell Shizuku may or may not have sufficient permission. **Sui/root has the best chance of working.**

The app includes diagnostics so the actual permission state and available light devices can be checked on the phone.

## Test features

- Connect through Shizuku/Sui
- Enumerate Lights service devices
- Detect `LIGHT_TYPE_BREATHE`
- Arbitrary RGB selection
- Brightness 0–255
- Native `EFFECT_ID_PURE`
- Native smooth `EFFECT_ID_RAINBOW`
- Ring OFF by closing the light session
- On-device diagnostics

## Requirements

- Meizu 21 with a Flyme build exposing the RGB camera ring through the vendor Lights extensions
- Android 12+
- Shizuku or Sui

## Build

The repository contains a GitHub Actions workflow. Every push builds a debug APK and uploads it as the **Meizu21-RGB-Ring-debug** workflow artifact.

Local build requires JDK 17, Android SDK 35 and Gradle 8.9 (or a compatible version):

```bash
gradle :app:assembleDebug
```

The output is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## First test procedure

1. Install/start Shizuku (or preferably Sui if the device is rooted).
2. Install the generated debug APK.
3. Open **Meizu 21 RGB Ring**.
4. Press **Grant / connect Shizuku**.
5. Press **Run diagnostics**.
6. Confirm that a light with the Meizu `LIGHT_TYPE_BREATHE` type is detected.
7. Try a low/medium brightness fixed colour first.
8. Try **Native smooth rainbow**.
9. Use **Turn ring off** when finished.

## Status

**v0.1-test** — first hardware test build. The exact Flyme framework behaviour still needs verification on a real Meizu 21.

This project is not affiliated with Meizu.
