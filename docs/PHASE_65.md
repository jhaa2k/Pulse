# Phase 65 — independent legacy glow axes and validation repair

Phase 64 CI compiled and ran 188 tests; one failed because the fixture selected the first exported element (a default audio provider) instead of the Image element. Select by objType, retain all color and config assertions. Do not skip tests.

Legacy blur layer scales are f2 pairs. Previously importer averaged them and renderer used that average on both axes. Store normalized horizontal ratio for each of three glow layers, recover the pair in native/legacy persistence and render independent axes. Existing scalar editor continues proportional scale; no independent axis UI yet. Native uniform layers default to ratio 1. Degenerate axes skip drawing and render scale still capped at 4 per axis. Three CPU tests added. No new motion blur, mask or mirror algorithm in this phase, no device verification. Main unchanged.
