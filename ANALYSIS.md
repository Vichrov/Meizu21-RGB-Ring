# Reverse engineering notes — Meizu 21 RGB Ring

## Meizu Tools 12.0.15

Source APK analysed: `com.meizu.flyme.toolbox` (Meizu Tools 12.0.15).

### RGB ring architecture

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

Scaling each RGB channel does dim the ring, but below a device-dependent threshold the hue degrades toward a dim white. Therefore RGB scaling is only an approximation, not true low-level current control.

## AW20072 / raw 16-pixel experiment

Open Meizu 21-series hardware code describes an AW20072 backend with 16 logical RGB pixels and a 96-byte frame, where colour and brightness are encoded separately. This suggested that independent segment control is physically possible.

However, hardware testing on stock Flyme with Shizuku UID 2000 showed that `/sys/class/leds` cannot be listed and `/sys/class/leds/aw20072_led/frame` is not accessible from the shell domain. Direct frame access therefore requires stronger privileges / a different SELinux domain and is not the preferred non-root path.

## Reconstructed aw_light Binder experiment

A vendor `aw_light` Binder API reconstructed from open Meizu/Lineage hardware code was tested in v0.4. Stock Flyme returned `DeadObjectException` on the small transactions. This strongly indicates that the reconstructed vendor Binder ABI does not match the stock Flyme implementation. v0.5 no longer relies on that Binder contract.

## Stock Flyme Settings 12.1.0 findings

The uploaded stock `Settings` APK contains the actual Meizu light-settings implementation. The important result is that normal previews use the same Android/Flyme `LightsManager` path that already works through Shizuku, not the reconstructed `aw_light` Binder.

`LightPreviewManager`:

- finds light type `10` (`LIGHT_TYPE_BREATHE`)
- opens a Lights session at priority `10009`
- creates `LightState.Builder()`
- calls `setEffectId(effectId)`
- calls `setColor(color)`
- submits the request through `SystemLightsSession.requestLights(...)`

No `setBrightness()` call is used by this Settings preview path.

Additional stock effect IDs observed in Settings:

- effect `65`: in-call light path
- effect `66`: notification light path

Previously confirmed from Tools:

- effect `32`: rainbow
- effect `47`: pure colour

v0.5 therefore includes a runtime stock-effect explorer that reflects all visible `EFFECT_ID_*`, `SESSION_PRIORITY_*`, and relevant hidden Lights methods from the actual phone framework, then lets the known working Lights service submit arbitrary effect IDs.

## Stock music-light preview path

The music-light settings are controlled by:

- `Settings.System["flyme_music_light_on"]`
- disabled packages list: `Settings.System["flyme_music_light_off_apps"]`

Most importantly, the Settings UI does not directly submit a light effect when previewing music. It plays audio with the vendor preview tag:

```text
light_preview_music
```

The recovered preview playback attributes are:

- stream type: `STREAM_MUSIC` (`3`)
- content type: `CONTENT_TYPE_MUSIC` (`2`)
- usage: `USAGE_MEDIA` (`1`)
- AudioAttributes flags: `64`
- AudioAttributes tag: `light_preview_music`

This indicates that a system-side Flyme component observes tagged/eligible music playback and drives the multi-segment ring itself. v0.5 reproduces this playback path with a short pulsing test tone, allowing the genuine stock music-light pipeline to be tested without guessing a vendor Binder ABI.

## Next framework targets

The uploaded archive contains product/system_ext/vendor framework directories and optimized artifacts for Flyme runtime, but not the two most useful original JARs. The next files to inspect are:

- `/system/framework/framework.jar`
- `/apex/com.flyme.runtime/javalib/service-flyme.jar`

These should help recover the full vendor `LightState` effect catalogue and the system-side music-light observer/renderer.
