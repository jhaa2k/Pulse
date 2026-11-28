# Phase 30 — custom spectrum path editor and CI resilience

- Tap-to-place path points (2–32; closed paths require 3), undo, clear, Apply/Cancel, switch back to preset paths.
- Arc-length interpolation distributes bars evenly along user-defined polylines. Independent X/Y stretch, custom path angle, shared geometry used in preview and export. Defaults preserve old native projects.
- Custom path geometry and stretch persist in native VEX. VIZ export does not promise support for these native-only fields.
- CI retries recognizable dependency-resolution failures up to three attempts, without bypassing failed tests or lint. No dependency versions changed.
- Unit tests cover path spacing, closed-path geometry, finite vertices and native round-trip. Device execution remains unverified.
- Existing transforms are unchanged pending verified Avee fixtures; asset management is a separate follow-up.
