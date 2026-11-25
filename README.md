# Vexo — Native Android Music Visualizer & Video Studio

A compact, modern native Android audio visualizer and layered video creation studio built with Kotlin, Jetpack Compose, Media3 ExoPlayer, OpenGL ES 2.0 shaders, and deterministic MediaCodec MP4 export.

## Core Modules

- **Audio Analysis Engine (`engine/audio`)**:
  - `PcmRingBuffer`: Lock-free bounded SPSC PCM tap with stream-generation reset protocol and drop tracking.
  - `FftProcessor`: Reusable Radix-2 Cooley-Tukey FFT with Hann/Hamming/Rectangular windows, coherent gain correction, and DC removal (256–8192).
  - `AudioProviderEngine`: Spectrum, RMS Spectrum2, Waveform, Nyquist-clamped Hz log/linear interpolation, IEC A-weighting, spatial/temporal filtering, mirror/repeat/gaps, FPS-independent attack/release/peak-hold, and beat energy envelopes.
- **Scene Graph & Effects (`engine/scene`, `engine/gl`)**:
  - Topological composition sorting with cycle detection (`Final Composition` + sub-compositions).
  - OpenGL ES 2.0 renderer supporting `Bars` (Line, Circle, Polygon, Sharp/Round Bars, Mirror, Reaction Delay), `Image` (SAF, Album Art, Internal textures, Color Key, Shape Mask), `Text` (`{Title}`, `{Artist}`, `{Album}`, `{Time}`), `Shape`, pooled deterministic `Particles`, `BlurEffect`, `RgbSplitEffect`, `MotionBlurEffect`, and `MirrorEffect`.
- **Playback & MediaSession (`playback`)**:
  - Shared ExoPlayer + MediaSessionService notification controls, SAF audio import, metadata/artwork extraction, queue, shuffle, repeat off/one/all, and volume.
- **Preset Compatibility & Persistence (`data`)**:
  - `AveeJsonCodec`: Import/export Avee JSON & `.viz` archives safely with unknown field preservation.
  - `ProjectRepository` & `SettingsRepository`: Atomic project autosave/recovery, 50-step Undo/Redo, and DataStore preferences.
- **Offline MP4 Video Export (`export`)**:
  - Shared GL renderer on an EGL14 `MediaCodec` H.264 input surface + AAC audio muxing via `MediaMuxer`, MP4 verification, and atomic `MediaStore` publication via foreground `ExportService`.

## Build & Test

```bash
./gradlew testDebugUnitTest assembleDebug
```
