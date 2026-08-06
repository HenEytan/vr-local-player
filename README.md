# VR Local Player (Android)

A minimal Android app that plays a local video file and displays it in
Google Cardboard style side by side (SBS) stereo for VR headsets, by
duplicating the decoded frame into both the left and right eye viewports.
It can also play the same file flat/normal (single view) via a toggle.

## How it works

- `MainActivity` lets you pick a local video file (`ACTION_GET_CONTENT`,
  `video/*`) and choose whether to duplicate it into stereo SBS or play flat.
- `VrPlayerActivity` hosts a `StereoGLSurfaceView` (custom `GLSurfaceView`).
- `StereoRenderer` creates an external OES texture + `SurfaceTexture` and
  hands a `Surface` to ExoPlayer (Media3), so the decoder writes frames
  straight into the GL texture. Each draw call renders that texture:
  - **Duplicate SBS mode**: once into the left half of the screen, once
    into the right half — the classic Cardboard/Google VR stereo layout.
  - **Flat mode**: once, full screen.

No native code, no NDK. Pure Kotlin + Media3 ExoPlayer + GLES 2.0.

## Build it

This repo has no compiled APK in it (building one needs the Android SDK,
which isn't available in the environment that generated this code). To
build:

1. Install [Android Studio](https://developer.android.com/studio) (Hedgehog
   or newer).
2. `File > Open` this folder. Let it sync Gradle (it will download the
   Gradle 8.7 distribution and Android SDK components automatically).
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)`, or run:
   ```
   ./gradlew assembleDebug
   ```
4. The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.
5. Install on a phone that goes into a Cardboard/Google VR headset:
   ```
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## Notes / next steps

- `minSdk` is 26 (Android 8.0), `targetSdk`/`compileSdk` 34.
- The stereo split currently has no barrel/pincushion lens distortion
  correction — it's a plain SBS duplicate, which many simple Cardboard
  viewers don't need, but if your headset expects distortion-corrected
  output you'll want to add that to the fragment shader per eye.
- If a video is *already* authored in side by side format, turn off
  "Play in VR (Duplicate SBS)" before playing it through this app's
  duplicate mode, since duplicating an already-SBS video would show two
  half-width frames instead of one full stereo pair. A dedicated
  "already SBS, just play as-is split screen" mode is a natural next
  feature to add (split the incoming texture instead of duplicating it).
- Supported formats depend on the device's decoders via ExoPlayer/Media3
  (mp4/H.264/H.265, mkv, webm, etc. — no exotic container/codec support
  is added here beyond what Media3 ships with by default).
