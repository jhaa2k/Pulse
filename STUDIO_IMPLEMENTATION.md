# VOOC React Android editor

This update adds an independent scene editor based on supplied Avee JSON property descriptors and VIZ scene structure. No APK binary, decompiled proprietary code, fonts or artwork are copied. The original React source remains available in src/App.tsx; the new entry point opens the scene editor.

- Nested property controls preserve raw keys, type descriptors, order, groups and unknown fields.
- Composition/layer creation, deletion, duplication, reorder, visibility, selection, undo/redo and local autosave.
- Audio provider spectrum/waveform, configurable ranges, filtering, smoothing, sample repeats/mirror, beat envelope.
- Canvas renderer for bars, image, text, shape, particles, blur, RGB split, temporal motion blur and mirror, plus composition references and cycle guard.
- ZIP VIZ import, JSON/VEX import/export; local image picker, album-art override and playback.
- Real-time recording via MediaRecorder, with audio and native share when supported.
- Preview aspect ratios and selected composition preview.

Limitations: Canvas approximations are not exact Avee shader behavior. Imported nested fields remain editable but not all are mapped to runtime (e.g. proprietary letter paths, mask luminance modes, album-art hints, perspective/force-field variants, motion source/relative mode, all blur tint layers, exact A-weighting). No deterministic offline MP4 encoder, queue/shuffle or background media service. Device compatibility and both supplied preset render fidelity require physical testing. Unsupported properties remain preserved on export.
