# Phase 6 renderer and audio audit

Premultiplied-alpha shader contract; image uniform reset between image, text and effects; shader location cache; custom font picker and bounded file storage; spectrum smoothing and delay length reset; waveform spatial-filter input and FFT energy fix; disabled provider reset; time-scaled peak decay and pre-smoothing; particle texture batch with mirrored copies, velocity orientation and trail approximation; fade-in size and 2D depth. Shape buffer capacity and zero stroke corrected. Tests cover waveform, disabled provider and smoothing.

No physical GPU or audio playback test yet. True overlapping crossfade, gapless verification, remaining effect properties and fully accelerated video decoder are not completed. Blend visual parity needs device comparison.