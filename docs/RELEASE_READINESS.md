# Release readiness — remaining work after Phase 71

The branch has a passing Phase 70 JVM test/lint/APK workflow (46033778998); the Phase 71 commit requires CI confirmation. APK compilation is not evidence of device rendering or export accuracy. Do not label the app production-ready until release gates have evidence.

## P0 — release blockers / verification gates
- Run the existing Android instrumented MP4 export test and extend it across 480p/720p/1080p/1440p/4K, 24/25/30/50/60fps on representative hardware; unsupported combinations must report a clear error. Validate audio sync, trim, orientation, codec fallback, cancel, file publication and repeated exports.
- GPU screenshot comparisons for the eight supplied Avee scenes against original reference renders at fixed timestamps and identical assets/audio. Validate spectrum geometry, reaction timing, transform/anchor, blend modes, masks, glow, motion blur, mirror, RGB split and particles. Original Visualizer 36 archive still needed; creator download gate was not bypassed.
- Exercise low-RAM and midrange devices, background/foreground, rotation, process death, URI revocation, cache pressure, audio switching, and long exports; record peak memory, dropped frames, stalls and crash traces. No measured performance claims until this is run.
- Confirm preset switching preserves current music/aspect ratio, undo/redo and autosave recovery. Validate all imported assets including fonts, video and masks. Add end-to-end UI tests.

## P1 — known feature/parity gaps
- Implement/verify shape-dependent spectrum width, reaction delays and polygon normals; tune audio-provider response against reference traces, rather than arbitrary gain increases.
- Image animation speed, animated image playback, generated album artwork, original font substitution mapping and nested image shape controls; validate composition masking and crop/alignment.
- Exact Avee shake/beat/time semantics and motion timing; original internal assets are approximations. Verify particles and force fields.
- Expose independent glow X/Y editor controls and distinct RGB effect/content blending; check all existing inspector properties bind to renderer and persist.
- Verify 60+ presets are structurally and visually distinct; produce contact sheet and audio-reactivity comparisons, not recolors.
- Export EGL initialization fallback across encoder candidates, GPU memory preflight, peak allocations, long export recovery, and high-resolution text/texture fidelity. Automatic bitrate is resolution/FPS-based, not complexity-based.

## P2 — release polish and distribution
- Test splash screen, 41% preview, accordion behavior, font/color wheel, horizontal editor controls, accessibility, localization, tablet/phone layouts and playback notification controls.
- Improve import diagnostics into an actionable report; recovery guidance for broken references and unsupported properties; preserve source archives and native roundtrip.
- Release signing, versioning, R8/release build validation, permissions/privacy review, dependency/security checks, crash reporting (opt-in), licensing review of presets/fonts/assets, distribution and changelog.

## Exit criteria
A clean CI build plus device test matrix, original-vs-imported screenshot evidence, successful long exports, no unresolved P0 crashes/data loss, and a verified release-signed APK/AAB. P1 gaps either resolved or explicitly documented; no claim of exact Avee parity without visual evidence.
