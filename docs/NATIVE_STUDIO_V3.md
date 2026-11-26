# Native Studio v3 — phase 1–3

Based on the native stability branch, not React. Main and existing branches are preserved. Native Kotlin/Compose, OpenGL ES 2.0, Media3 PCM analysis, optional per-session Android Visualizer, versioned scene model, and five JSON templates. See app/build.gradle.kts and manifest for the exact pinned dependencies and permissions. SDK 24 is the intended minimum; CI and actual API 24 device compatibility must be verified.

## Architecture
- model: serializable scene, layer and provider contracts
- data: NativeVexCodec, VexArchive, project and settings persistence, template repository
- playback: Media3 controller, MediaSession, PCM tap, MediaStore query
- engine/audio: ring buffer, radix-2 FFT, provider mapping and optional Visualizer adapter
- engine/scene: measures, keyframes, particles, artwork palette
- engine/gl: GLSL programs, FBO composition, spectrum geometry and shared renderer
- ui: existing editor (phase 4 overhaul intentionally deferred)
- export: existing MediaCodec/Muxer implementation (phase 4 overhaul intentionally deferred)

## Scope
The five JSON templates can be loaded through BuiltInTemplateRepository or the existing native JSON codec. Their picker wiring is deferred. Visualizer requires explicit runtime microphone permission; PCM is the main analyzer, so permissions are optional for normal playback and deterministic export. Palette extraction is on-device algorithmic quantization, not generative AI. Media library query provides track/album/artist/folder metadata only; genre, playlist, crossfade, EQ controls, sleep timer and video background are not completed. Granular inspector wiring for new properties and new UI/MP4 work intentionally wait for Next. Existing UI and export are not replaced in this phase. JSON schema is a structural schema, Kotlin codec enforces additional limits and layer-type consistency.
