# Reverse engineering notes — Meizu Tools 12.0.15

Source APK analysed: `com.meizu.flyme.toolbox` (Meizu Tools 12.0.15).

## RGB ring architecture

The flashlight section contains code for a Meizu breathing/RGB light. The relevant control path resolves the Android Lights framework and vendor extensions rather than writing RGB values to sysfs directly.

Observed concepts/identifiers:

- `LIGHT_TYPE_BREATHE`
- `SESSION_PRIORITY_FLASHLIGHT`
- `EFFECT_ID_PURE`
- `EFFECT_ID_RAINBOW`
- `LightState.Builder.setBrightness(int)`
- `LightState.Builder.setEffectId(int)`
- `LightsManager` / `ILightsManager`

The stock UI selects a light whose type matches `LIGHT_TYPE_BREATHE`, opens a flashlight-priority light session, and then submits a `LightState` to that light.

## Fixed colour

For a fixed colour, the app selects `EFFECT_ID_PURE` and supplies a colour plus brightness. The Meizu implementation carries brightness separately while the colour is still represented as an ARGB-style integer.

Stock preset colours seen in the APK include values such as:

- `#FFFF5757`
- `#FFFF833E`
- `#FFFFF066`
- `#FFF051E0`
- `#FF59E742`
- `#FF50F0F2`
- `#FF26CBFF`
- `#FFC13DFF`

The test app exposes arbitrary RGB instead of limiting the user to these presets.

## Rainbow

The smooth colour-cycling mode is a native/vendor effect: `EFFECT_ID_RAINBOW`.

That is important because the application does **not** need to approximate the animation by repeatedly changing RGB values on the Java side. Flyme/the light driver performs the effect itself.

## Turning the light off

The stock control path releases/closes the lights session. The test application follows the same idea and closes its `ILightsManager` session when OFF is requested.

## Permission problem

The Lights Binder interface is protected by `android.permission.CONTROL_DEVICE_LIGHTS`. A normal third-party APK cannot normally obtain this privileged/signature permission.

For that reason the first test implementation uses a Shizuku UserService and invokes the Lights Binder interface from the Shizuku/Sui process.

Two cases need to be tested on a real Meizu 21:

1. **ADB-shell Shizuku** — may work if Flyme grants the shell UID access to the Lights service.
2. **Sui/root** — expected to have the highest chance of success if shell is denied.

## Test goal

The first APK is deliberately diagnostic. Before adding automation, notification effects, music visualisation, or integrations, it should establish:

- whether the vendor constants are visible to an external process;
- whether Shizuku shell/root can open a lights session;
- the actual `LIGHT_TYPE_BREATHE` value on the tested Flyme build;
- the ring light ID returned by `getLights()`;
- whether both PURE and RAINBOW effects can be submitted successfully.
