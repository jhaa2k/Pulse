# Phase 71 — framebuffer scratch lifecycle

Phase 70 CI passed. Use exact scratch membership to release obsolete blur pyramid and second-axis buffers, avoid prefix collisions, release main/border scratch independently when no active layers require them, and bypass motion history allocation when gain is zero (preserve tint, blend and clean layer order). Four JVM plan tests added. Existing filter kernels and full-resolution composition slots unchanged. No GPU benchmark or instrumented device run yet. See RELEASE_READINESS.md for remaining gates. Main unchanged.
