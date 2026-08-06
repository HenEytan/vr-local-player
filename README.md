# VR Local Player (Android)

A minimal Android app that plays a local video file and displays it in
Google Cardboard style side by side (SBS) stereo for VR headsets, by
duplicating the decoded frame into both the left and right eye viewports.
It can also play the same file flat/normal (single view) via a toggle,
with a live control panel for zoom, position, eye gap, and eye alignment.

## How it works

- `MainActivity` lets you pick a local video file (`ACTION_GET_CONTENT`,
  `video/*`) and choose whether to duplicate it into stereo SBS or play flat.
- `VrPlayerActivity` hosts a `StereoGLSurfaceView` (custom `GLSurfaceView`)
  plus a slide up control panel (tap the gear icon, top right) for live
  tuning of:
  - **Zoom** (50%, 200%)
  - **Horizontal / Vertical position** (pan the video within each eye)
  - **Space between videos** (the gap between the left/right eye views)
  - **Eye alignment (IPD)** (shifts each eye's image toward or away from
    center, for matching your own interpupillary distance)
  - **VR Duplicate Mode** on/off, switchable without leaving the player
  All values persist across sessions (`VrSettings`, backed by
  SharedPreferences), with a one tap Reset to defaults.
- `StereoRenderer` creates an external OES texture + `SurfaceTexture` and
  hands a `Surface` to ExoPlayer (Media3), so the decoder writes frames
  straight into the GL texture. Each draw call renders that texture through
  a per eye model matrix (zoom/pan/IPD are just a translate plus scale in
  NDC space applied before drawing):
  - **Duplicate SBS mode**: once into the left half of the screen, once
    into the right half, the classic Cardboard/Google VR stereo layout.
  - **Flat mode**: once, full screen.

No native code, no NDK. Pure Kotlin + Media3 ExoPlayer + GLES 2.0.

## Build it locally (optional)

An APK is also built automatically by GitHub Actions on every push, see
`.github/workflows/build-apk.yml`, published to the repo's Releases page.

To build locally instead:

1. Install Android Studio (Hedgehog or newer).
2. `File > Open` this folder. Let it sync Gradle.
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)`, or run:
   ```
   ./gradlew assembleDebug
   ```
4. The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Notes / next steps

- `minSdk` is 26 (Android 8.0), `targetSdk`/`compileSdk` 34.
- The stereo split currently has no barrel/pincushion lens distortion
  correction, it's a plain SBS duplicate, which many simple Cardboard
  viewers don't need, but if your headset expects distortion corrected
  output you'll want to add that to the fragment shader per eye.
- If a video is already authored in side by side format, turn off
  "VR Duplicate Mode" in the control panel before playing it, since
  duplicating an already SBS video would show two half width frames
  instead of one full stereo pair.
- Supported formats depend on the device's decoders via ExoPlayer/Media3
  (mp4/H.264/H.265, mkv, webm, etc).
