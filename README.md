# AprilTag Detector for Android

Real-time [AprilTag](https://april.eecs.umich.edu/software/apriltag) detection on Android. Point your camera at AprilTags and the app draws each detected tag's outline and decoded ID over the live preview.

This is a modernized fork maintained by **Binnacle-Tech**, continuing the original [`johnjwang/apriltag-android`](https://github.com/johnjwang/apriltag-android) demo app from the University of Michigan APRIL Robotics Lab. The original targeted deprecated APIs (Camera1, the support library, `PreferenceActivity`) and no longer builds on current tooling. This fork brings it onto a current AndroidX + CameraX stack while keeping the native AprilTag detector unchanged.

## Features

- Detects multiple tag families: `tag36h11` (default), `tag36h10`, `tag25h9`, `tag25h7`, `tag16h5`
- Live camera preview with detections drawn as an overlay (outline + tag ID)
- Fully on-device detection via the native AprilTag C library — no network needed
- Tunable detector settings (decimation, blur, threads, hamming error)
- Optional on-screen diagnostics (camera/detection FPS and latency)

## Requirements

- A recent Android Studio
- JDK 17 (required by Android Gradle Plugin 8.x)
- Android NDK and CMake (install via SDK Manager → SDK Tools) to build the native library
- A device or emulator running Android 5.0 (API 21) or higher

Toolchain in use:

- Gradle 8.7, Android Gradle Plugin 8.5.2
- `compileSdk` / `targetSdk` 34, `minSdk` 21, Java 17
- CameraX 1.4.1
- Native libraries are 16 KB page-size aligned, for Android 15+ / 16 KB-page devices

## Build & run

1. Open the project root in Android Studio and let Gradle sync.
2. Confirm the NDK and CMake are installed (SDK Manager → SDK Tools).
3. Choose a device or emulator and Run. Grant the camera permission when prompted.

To test in an emulator, add a tag image to the virtual scene by following Google's [ARCore emulator instructions](https://developers.google.com/ar/develop/java/emulator#move_the_virtual_camera).

## How it works

The app is a thin Java/AndroidX shell around the native AprilTag C detector.

`CameraController` configures CameraX with a `Preview` use case (rendered in a `PreviewView`) and an `ImageAnalysis` use case, both bound to the same 4:3 aspect ratio so their fields of view match. Each analysis frame arrives as `YUV_420_888`; the luma (Y) plane is copied into a packed grayscale buffer and passed to the native detector (`ApriltagNative.apriltag_detect_yuv`) over JNI, since only the Y channel is needed for detection.

`DetectionThread` runs detection on a background thread and draws each result — a filled quad, colored border, and the tag ID — onto a transparent `TextureView` overlay stacked above the preview. Detection coordinates are mapped from image space to view space using the frame's reported rotation and a fit-center transform, so the overlay lines up with what the preview shows. The native library `libapriltag.so` is compiled from the bundled AprilTag C sources under `src/main/apriltag/` via CMake and the NDK.

## Settings

Open the gear icon in the toolbar. Changes take effect when you return to the camera view; the refresh icon resets everything to defaults.

| Setting | What it does | Default |
| --- | --- | --- |
| Tag family | Which AprilTag family to detect. | `tag36h11` |
| Image decimation | Downsamples the frame before detection. Higher is faster but reduces range and accuracy. | 8 |
| Gaussian blur (sigma) | Blurs the frame before detection; can help with noisy input. `0` disables it. | 0.0 |
| Number of threads | Detector worker threads. | CPU core count |
| Max Hamming error | Bit errors tolerated when decoding a tag. Higher detects more tags but increases false positives. | 0 |
| Enable Diagnostics | Shows camera/detection FPS and latency on screen. | Off |

## Attribution & licenses

- **App:** originally [`johnjwang/apriltag-android`](https://github.com/johnjwang/apriltag-android), an AprilTag detector demo for Android from the University of Michigan APRIL Robotics Lab. This fork modernizes the build system and camera pipeline.
- **AprilTag library:** the C sources under `src/main/apriltag/` are from [`AprilRobotics/apriltag`](https://github.com/AprilRobotics/apriltag), Copyright (C) 2013–2016 The Regents of the University of Michigan, developed in the APRIL Robotics Lab under the direction of Edwin Olson. Distributed under the **BSD 2-Clause** license; the original copyright and license notices are retained in the source headers.
- **Fonts:** Space Grotesk, Inter, and IBM Plex Mono (bundled in `res/font/`), © their respective authors, licensed under the **SIL Open Font License 1.1**. Full notices are in `licenses/`.

Please keep these notices intact in any redistribution.
