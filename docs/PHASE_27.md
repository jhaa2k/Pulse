# Phase 27 — Native MP4 export stabilization

- Integer frame timestamps and sample clocks; decoded PCM duration drives output frame count, bounded trim-duration mismatch check and previous-sync seek.
- HEVC preference retries a reported H.264 encoder if HEVC configure/surface/start fails. Resolution, FPS and bitrate are not silently lowered. Runtime codec failures still report an error.
- Bounded AAC lead during render, simultaneous final drain, cancellation checks, separate resource stop/release, unique temporary files and EGL timestamp validation.
- Audio decoding progress, monotonic render progress and throttled notification updates; notification errors no longer fail export.
- Pure timing unit tests and a device instrumentation test generating a trimmed WAV-driven 720p MP4, inspecting audio/video tracks and decoding a frame.

Run the device test with a connected Android device or emulator:
```
./gradlew connectedDebugAndroidTest
```
CI compiles the instrumentation test APK but does not execute a device test. Export is deterministic offline frame rendering with live progress, not a guaranteed wall-clock 1x capture. Speed depends on device codec/GPU, resolution, effects and available memory/storage. No physical-device verification yet. Main unchanged.
