# Phase 83 — release candidate packaging

Phase 82 Android CI passed (46101351891). The separate emulator run (46101351933) reported a failed instrumentation step; final logs were not yet available when inspected. This is an unresolved release gate, not a passing device test.

Keep application ID com.vexo.studio, version 3.0.0 / code 3, minSdk 24, targetSdk 35 and existing unminified release behavior. Add opt-in Gradle production signing without debug-key fallback. GitHub Actions builds unsigned APK/AAB automatically when release configuration changes, or builds signed candidates by manual dispatch with the signed input enabled and four repository secrets. Signed packaging verifies APK/AAB signatures and all packages include SHA-256 checksums and commit identity. No secrets are written to the repository. No tag, public release, Play Store upload or distribution performed.

## Signing setup

Use your existing production keystore if updating a published app. Do not create a replacement key for an existing signing identity. In repository Settings → Secrets and variables → Actions, set:

- VEXO_RELEASE_KEYSTORE_BASE64 — base64 of the private keystore
- VEXO_RELEASE_STORE_PASSWORD
- VEXO_RELEASE_KEY_ALIAS
- VEXO_RELEASE_KEY_PASSWORD

Run Android release packaging on engine/native-studio-v3 with signed enabled. Retrieve the candidate artifact after signature verification. Keep the original keystore backed up privately. Unsigned artifacts are for build validation, not installation.

## Release gates

See RELEASE_READINESS.md. Physical device tests, exact Avee visual comparison, long exports, memory measurements, release signing credentials and privacy/licensing review remain unresolved. R8 stays disabled intentionally. A candidate build is not a production release.
