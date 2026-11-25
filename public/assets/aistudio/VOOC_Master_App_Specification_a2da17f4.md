# VOOC — Master App Development Specification
Version 2.0 | Android | Development blueprint

## 1. Product vision
Build an independent native Android music visualization and video creation studio inspired by the workflow and reactive behavior of Avee Player, with modern professional editing controls. Do not copy proprietary source code or assets. Support music playback, real-time visualization, layered scene editing, reusable presets and offline video export. Prioritize responsiveness, predictable behavior, low memory consumption and reliable installation over feature count. No claim of zero latency or exact compatibility without measurement.

## 2. Repository and delivery rules
Repository: https://github.com/jhaa2k/Vooc
Active branch: engine/rebuild-v2. Preserve main and redesign/full-studio. Work in coherent, testable milestones. Audit existing code before replacing it. Keep legacy functionality available until migration is validated. Do not mark a property implemented merely because a control exists. Each property must connect to runtime, persistence, import/export and tests where applicable. Deliver signed debug APK artifacts through GitHub Actions; never claim a successful build without its result.

## 3. Technology stack
- Kotlin, Android SDK (minimum API 26; current project compile/target 35), JDK 17, Gradle.
- Jetpack Compose and Material 3, MVVM, clean architecture, Hilt, coroutines and Flow.
- Media3 ExoPlayer and MediaSession for playback; PCM analysis using reusable buffers and FFT. Optional C++17/JNI for measured bottlenecks.
- Dedicated EGL/OpenGL ES rendering thread, GLSL shaders; GLES2 baseline, GLES3 optimized path when supported.
- MediaCodec, MediaMuxer and Media3 Transformer where suitable for video export.
- Room project metadata, DataStore preferences, Storage Access Framework for imports, MediaStore for published videos, foreground services and WorkManager as appropriate.

## 4. UI/UX design system
Default monochrome: black background, charcoal panels, white primary text, gray secondary text. Poppins variable font, uppercase compact labels, sharp rectangular controls, consistent spacing, no unnecessary rounded cards. Accessible contrast, scalable text and touch targets. Thin slider tracks with larger handles; numeric editing, double-tap slider reset, default value indicator. No horizontal overflow or clipped labels. Optional theme presets, dark/light variants and custom accent.

Editor: permanent top preview in landscape aspect ratios, bottom half controls. Portrait preview fills available screen with bottom translucent overlay; use a supported blur or dim fallback without expensive frame readback. Keep preview visible during navigation. Scroll only the property region, keep transport and navigation stable. Preserve expanded properties until explicitly closed. Provide full-screen preview, aspect ratio switch, zoom/pan, safe-area guides, undo/redo, autosave and clear error messages.

Navigation: Projects, Audio, Editor, Templates, Export, Settings. Editor tabs: Layers, Properties, Assets, Presets. Layer list supports add, delete, duplicate, reorder, rename, visibility, lock, grouping and composition routing. Show selected layer and property hierarchy. Confirm destructive deletion, not ordinary edits.

## 5. Playback and library
Import local audio through SAF; persist permission where available. Display title, artist, album, artwork, duration and position. Support play/pause, previous/next, seek, restart, shuffle, repeat off/one/all, queue editing and volume. Handle audio focus, noisy output, interruptions, playback errors and lifecycle. MediaSession notification controls and foreground playback service must remain synchronized with editor state. Avoid duplicate players. Handle unsupported codecs gracefully. Audio is analyzed only from the app's own playback, not microphone or unrelated apps.

## 6. Audio engine architecture
Decoder -> pass-through PCM tap -> bounded SPSC ring -> analysis worker -> timestamped spectrum snapshots -> provider mapping -> render thread -> display or encoder surface.

PCM tap must not block playback. Handle supported PCM encodings and channel layouts, preserve original bytes, downmix for analysis, sanitize nonfinite values. Track sample rate, channel count, frame index, stream generation and media timestamp. Reset ring, FFT overlap, envelopes and beat state on seek, track transition or format change using a race-safe generation protocol. Avoid stale backlog: explicitly detect drops, discard obsolete data and re-anchor timestamps. Decode-ahead is not playback time; select analysis frames against playback clock and measured output latency. Pause should decay or freeze according to project settings. Resume must not replay stale frames.

FFT: reusable window, scratch, twiddle and output buffers; Hann window with coherent gain correction; configurable FFT size 256/512/1024/2048/4096/8192; hop size independent of frame rate. Native and Kotlin fallback must match amplitude conventions. Handle silence, DC, clipping, Nyquist, sample-rate changes and mono/stereo cancellation. Do not apply unintended multiple smoothing stages. Measure CPU cost before moving work to native code.

## 7. Audio Provider — property contract
Multiple independent providers may consume shared raw analysis while maintaining independent mapping and envelopes. Provider indices and IDs must resolve consistently across compositions.

| Section | Properties | Required behavior |
|---|---|---|
| General | Tag, Enabled, Sample Provider (Spectrum, Spectrum2, Waveform) | Select analysis type; preserve provider identity |
| Spectrum | Sample Out Count, Mirror Samples, Repeat Samples, Start And End Gap | Resample output, mirror/repeat without invalid indices; gaps preserve intended geometry |
| Filtering | Smooth, Pre Smooth, Filter Radius, Filter Strength, AWeight, Output Multiplier | Explicit spatial versus temporal filtering; avoid accidental double smoothing; calibrated gain |
| Spectrum Hz | Lower Hz, Higher Hz, Hz Linear Factor, Hz Shift | Clamp to Nyquist; interpolate linear/log distribution; define shift and edge handling |
| FFT | FFT Size, Hop Size, Window, Channel Mode | Reconfigure safely; share raw analysis where compatible |
| Response | Attack, Release, Peak Hold, Peak Decay | Time-based response, independent of display FPS; zero attack immediate |
| Beat | Beat Smooth, Beat Range Bar First/Last, Beat Range Value Lower/Higher, Beat Sensitivity | Compute normalized beat/energy over selected range; handle reversed and empty ranges |

Spectrum2 may use RMS band aggregation; waveform preserves time-domain samples. Frequency mapping must aggregate bins or interpolate appropriately instead of merely duplicating a single low-frequency bin. A-weighting must use actual Hz. Define units and defaults in schema; imported values must retain original semantics where known.

## 8. Scene and composition architecture
Project contains canvas settings, audio reference, ordered compositions, layers, providers, assets, keyframes and schema version. Final Composition is the output root; sub-compositions render to reusable textures. Prevent cycles, self-reference and invalid targets. Render dependency graph topologically; preserve layer order within each composition. A composition reference must resolve to the correct texture, not album art or a placeholder. Use stable IDs independent of display order. Provide preview isolation and composition thumbnails. Final Composition exposes output-relevant controls rather than redundant generic transforms.

## 9. Common layer properties
Every applicable visual layer: Tag, Visible, Locked, Blend Mode, Opacity, Position X/Y, Anchor X/Y, Alignment, Pivot, Scale X/Y, Uniform Scale, Rotation, Measure Pos, Measure Scale, Measure Rot, optional measured opacity/color. Define normalized screen coordinates, local geometry, anchor and pivot conventions. Transform order must be documented and identical in preview/export. Scale particles as a complete layer when changing layer transform, not just each sprite locally. Handle negative scale, aspect ratio, rotation and off-screen positions. Never silently ignore unsupported controls.

Blend modes: Normal/Alpha, PreMulAlpha, Add, AddAlpha, Screen, Multiply, Overwrite and supported additional modes. Define equations, premultiplied versus straight alpha and color space. Use shader-based blending where fixed-function blending cannot reproduce the equation. Test translucent source/destination and nested composition cases.

## 10. Audio-reactive measures
Reusable Measure What selector for position, scale, rotation, color and effect properties where meaningful. Options from prior project requirements: Nothing, Beat, TotalTime, TotalTimeBackward, TotalTimeWhenPlaying, TotalTimeAndBeat, TrackPosition, BeatRandomShake, BeatCamShakeRotMore, BeatCamShakeRotLess, ConstantShake. Proposed extensions: Bass, Mid, Treble, RMS, Peak, FrequencyBand, BeatPulse, BeatCounter, Sine, Noise, Keyframe. Each measure has A/B amount or axis parameters where applicable, provider selection, smoothing and deterministic seed. Define output units and ranges per target. Evaluate once per frame using consistent time; export must reproduce the same motion at the same timestamp. Do not treat proposed extensions as confirmed Avee options.

## 11. Bars / Spectrum layer
General: provider index, visibility, transform, blend, opacity. Path: horizontal line, vertical line, circle, polygon, sided polygon, letter/custom path where supported. Path controls: radius, sides, angle, spacing, direction, closed/open, alignment. Segment controls: bar/line/round bar/rectangle/triangle/point and optional secondary segment; colors A/B, gradient, width, height scale, minimum/maximum height, fixed height, mirror, flip, center alignment, alternating direction. Reaction Delay and Reaction Accumulated use bounded history with explicit units. Support peak markers and waveform mode as extensions. Preserve imported signed height scales and min/max behavior. Avoid clipping every amplitude to 1 before applying intended gain unless schema requires it. Attack should be fast; release should be adjustable without extra hidden smoothing. Batch geometry, reuse buffers, avoid per-bar draw calls. Test bar count changes, circular closure, mirror symmetry and silence.

## 12. Image layer
Image source: imported image, album art, generated color, composition texture, built-in asset, GIF/animated image where supported. Import must visibly replace selected source. Album art override must update all layers referencing shared artwork, not unrelated explicit images. Properties: tint/color, opacity, fit/fill/stretch, keep aspect ratio, crop, alignment, UV flip, saturation, color key, mask source, mask mode, blur, border, shape, rotation, measured transform. Color key controls: key color, tolerance, softness, spill suppression where supported. Mask controls: alpha/luminance/invert and target reference. Cache decoded images by source identity and processing settings; do not decode per frame. Respect EXIF orientation, transparency, maximum texture size and memory budget. Animated images use timestamp-based frame selection.

## 13. Text and logo layers
Text: content, metadata placeholders such as {Artist}, {Title}, {Album}, {Time}, font family, font weight/style, size, color, alignment, tracking, line spacing, stroke, shadow, background, wrapping and transform. Font bold/italic must actually change glyph rendering. Use cached glyph atlas or text texture; invalidate only on relevant changes. Support imported fonts through safe file access. AppLogo: optional project branding, image/text source, visibility and animation. Never impose a watermark on user exports unless explicitly selected.

## 14. Shapes and particles
Shapes: rectangle, ellipse, polygon, star, line and custom path; fill/stroke, stroke width, gradient, size, corner controls where appropriate, transform and measured properties.

Particles: Count, Spawn Area, Spawn Interval, Lifetime, Speed, Direction/Vector Angle, Spread, Gravity, Drag, Size Start/End, Color Start/End, Fade In/Out, Rotation, Velocity Angle, Texture, Blend Mode, Perspective Depth, Perspective FOV, Z, Near Camera Fade, beat-driven emission, random seed. Use pooled arrays and fixed-step simulation with bounded catch-up. Spawn positions and velocities must respect coordinate space. Distinguish emitter transform, layer transform and individual sprite scale. Support deterministic reset and export. Batch sprites; avoid object allocation per particle per frame. Count limits should adapt to device capability without silently changing saved project values.

## 15. Effects
Blur Effect: target composition/image, radius, passes, quality/downsample, tint, Blend Mode Content, Show Unblurred Content, Show Unblurred Content Under, measured blur amount. Use separable blur with reusable ping-pong FBOs. Bloom/glow: threshold, intensity, radius, color, additive composite. Zoom blur: center, strength, samples, beat modulation. Directional blur: angle, amount, samples. Depth-of-field: focus region and blur amount where depth/mask data exists.

Motion Blur Effect: Blur Amount Multiplier, Motion Source (EffectTransform or supported source), Relative Motion Mode, Blend Mode Content, Color, Target Image, Show Unblurred Content, Show Unblurred Content Under. Track previous/current transforms, reset history on seek or discontinuity, avoid stale trails. Define sample count and shutter interval; use identical time conventions in export.

Mirror Effect: target image/composition, horizontal/vertical/both, center, flip, repeat/reflection count, tint, blend. Validate coordinates and alpha. RGB Split Effect: channel offsets, direction, amount, target, blend, measured strength. Camera shake: deterministic seeded translation/rotation, frequency, amplitude, beat mapping. Additional effects may include vignette, color grading, distortion and chromatic aberration. All effects must obey composition routing and layer order.

## 16. GPU rendering and performance
One long-lived EGL context per render pipeline, with lifecycle-safe recreation after context loss. Use reusable FBO/texture pool, shader cache, vertex buffers, texture atlas and batched draws. No per-frame bitmap readback in normal preview/export. Avoid Compose recomposition at audio sample or render rate. Pass immutable configuration snapshots to GL thread. Use actual frame delta, cap simulation catch-up, release GPU resources on correct thread. Validate GL capabilities and framebuffer completeness. Support adaptive preview resolution and effect quality, but preserve final export settings. Pause rendering when invisible or inactive unless needed. Instrument CPU/GPU frame times, dropped frames, PCM drops, memory and thermal behavior. Target 60 FPS on capable devices and stable 30 FPS fallback; not guaranteed on every device.

## 17. Project and VIZ compatibility
Support native versioned project format with stable IDs, asset references, metadata, compositions, layer properties, provider configuration, keyframes and export preferences. Autosave atomically; recover last valid project after crash. Import/export reusable templates and asset bundles. VIZ importer must detect actual container/JSON structure, validate types, resolve composition/provider indices, map known typed properties and preserve unknown fields. Never execute embedded code. Protect against zip traversal, decompression bombs, oversized images and malformed values. Report unsupported elements explicitly. Round-trip tests must preserve known and unknown data. Exact Avee behavior requires authorized reference presets and visual/audio comparisons; do not claim full parity based on file parsing alone.

## 18. Video export
Use shared scene evaluator and renderer with EGL encoder surface, MediaCodec video encoder and MediaMuxer. Export offline at deterministic timestamps, with audio analysis aligned to output presentation time. Audio may be remuxed if compatible or transcoded to AAC. Respect trim, duration, start offset, orientation, aspect ratio, resolution, FPS and bitrate. Presets: 16:9, 9:16, 1:1, 4:5 and custom; 720p/1080p/1440p/4K subject to device encoder capability; 24/25/30/60 FPS where supported. H.264 MP4 baseline; HEVC optional when supported. Show real progress, estimated remaining time, cancel and errors. Use temporary output and publish only after successful finalization. Save through MediaStore or user-selected SAF location; verify output exists, is playable, has audio/video tracks and correct duration. Clean partial files after cancellation. Foreground export notification with progress and cancel. Do not show success before actual file publication.

## 19. Editing and project workflow
Create project -> import audio -> choose aspect ratio -> select template or blank scene -> add compositions/layers -> adjust properties -> preview -> save -> export. Support undo/redo transactions, duplicate project, rename, asset relink, template preview and project recovery. Keyframes: time, value, interpolation (hold/linear/ease), timeline scrubbing and deterministic evaluation. Use bounded history and avoid recording every slider movement as separate undo entry. Provide accessible help for every property: meaning, units, default, range, interaction and example.

## 20. Proposed initial property defaults
These are design baselines, not verified Avee defaults. V2 bars: count 96 (1–512), gain 4 (0–32), release 75 ms (0–2000), width 0.72 (0.05–1). FFT 1024, hop 256. At 48 kHz the window is 21.33 ms and hop 5.33 ms; neither is total latency. Prior project requirements include alternative imported values; retain them per preset rather than overwriting them with defaults. Store property descriptors centrally: key, label, type, units, default, range, step, options, visibility dependencies, migration, runtime binding and reset behavior.

## 21. Verification and acceptance criteria
- Unit tests: ring wraparound/overflow/concurrency, FFT sine/impulse/silence, native/fallback parity, sample-rate mapping, beat envelopes, seek generation, transforms, blend equations, composition cycle detection, preset round-trip, deterministic particles and keyframes.
- Instrumentation: startup, file picker, audio playback, notification controls, pause/resume/seek, GPU context loss, image import, effect output, export cancellation and saved file validation. Test Android API 30 and 35 plus real low/mid/high-end devices.
- Performance: record p50/p95/p99 frame time, FPS, audio-to-visual latency, dropped PCM, allocations, memory and thermal throttling for simple bars and complex scenes. Compare with reference presets using same audio and device. Report actual measurements.
- CI: compile, lint, unit tests, assembleDebug, verify APK package/signing/ABI, upload APK; emulator tests must fail CI when tests fail. Never mask a failing build or test. Verify actual launcher and installation.
- Property audit: for each control test default, minimum, maximum, reset, persistence, import, live preview and export. Check unsupported values and malformed files. No inert sliders or fake success states.

## 22. Current V2 status (based on conversation history; not a fresh repository audit)
Branch created from legacy studio. Commits reported: PCM ring f8182e4; ring tests 4400dee; FFT analyzer 6d90795; worker dfc14e2; GLES bars 19e5f7b; Media3 PCM tap 0d526d1; Compose activity d477a45; launcher 445e846; CI branch triggers de7ed27 and 656836e; configurable bars 23e9ea1; live controls ebeceee. Basic preview and audio picker are coded. Label double-tap reset exists, not confirmed slider-track reset. Current prototype uses fixed bin mapping, simple gain and release, and latest FFT snapshot; proper playback-clock alignment, seek reset, sample-rate mapping and stale-buffer handling remain incomplete. CI was observed running earlier; latest success and device performance are not verified. Full scene editor, effects, VIZ parity, background playback and shared export pipeline remain migration/implementation work in V2. Legacy branch may contain implementations that require audit before reuse.

## 23. Implementation sequence
1. Verify V2 build and launcher; fix compilation and startup errors.
2. Implement stream generations, timestamps, sample-rate-aware mapping, pause/seek handling and audio-clock synchronization; validate synthetic signals and device response.
3. Establish project schema, property registry, scene graph and shared renderer contracts.
4. Complete professional editor layout, layer list, transport, custom sliders, typography and persistence.
5. Implement providers, bars/paths, image/text/shapes, composition routing and blend modes with tests.
6. Implement particles, blur, motion blur, mirror, RGB split and measured effects; benchmark complex scenes.
7. Implement VIZ import/export compatibility and reference fixture tests.
8. Implement shared deterministic video export, MediaStore publication and notification controls.
9. Run full regression, real-device performance tests, compatibility audit and release packaging.

## 24. Instructions for AI coding agents
Treat this document as a requirements blueprint, not proof that every listed feature already exists. Inspect repository and tests first. Work only on engine/rebuild-v2 unless explicitly authorized otherwise. Resolve ambiguities by documenting decisions; preserve imported semantics and user data. Implement end-to-end vertical slices, not disconnected files or placeholder controls. Prefer measurable performance improvements over repeated speculative micro-optimizations. Build and test after coherent changes. Keep a property coverage matrix and a verified/pending/failed status ledger. Report commit IDs, actual test results, known limitations and APK artifact links. Continue through milestones within available execution limits; never claim background work or completion without evidence.
