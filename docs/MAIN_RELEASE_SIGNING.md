# Main branch release signing

The native studio development branch was merged to main. Release packaging now triggers on relevant main pushes and requires all four production signing secrets for automatic main releases. A manually requested unsigned validation build is still allowed. Development branches may produce unsigned validation packages.

Create and back up your own signing key privately. Never commit a keystore, passwords, Base64 keystore, or upload them as public workflow artifacts. The old key-generation workflow is replaced by a secret-presence check to prevent accidental signing-key disclosure. It never prints secret values.

Repository Settings > Secrets and variables > Actions:
- VEXO_RELEASE_KEYSTORE_BASE64
- VEXO_RELEASE_STORE_PASSWORD
- VEXO_RELEASE_KEY_ALIAS
- VEXO_RELEASE_KEY_PASSWORD

Use the original key for previously production-signed applications. Run Android release packaging on main with signed enabled after configuring the four secrets. APK and AAB signatures are verified before upload. No automatic public release or Play Store publication.
