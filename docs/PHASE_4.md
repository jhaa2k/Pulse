# Native Studio v3 – phase 4

Inspector controls for spectrum count, spacing, rounding, measure thresholds, independent accordions, provider bindings and opacity reactivity. Five JSON templates wired into template pickers. Added mini-player, MediaStore track/album/artist/folder browsing, sleep timer and artwork palette action. Export improvements: dedicated EGL thread, codec selection, disk-spooled PCM, trim at frame boundaries, pending encoded packets until both tracks are ready, timeouts, cancellation, even dimensions, failed MediaStore cleanup. AAC bitrate controls.

Limitations: physical-device validation pending, 4K/60 depends on encoder/GPU, no new video-background renderer, genre/playlist browsing, crossfade or EQ. Android 7–9 uses SAF output. Sleep timer belongs to current ViewModel lifetime.
