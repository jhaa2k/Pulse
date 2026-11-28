#!/usr/bin/env bash
set -euo pipefail
: "${KEYSTORE_BASE64:?Missing release keystore secret}"
: "${VEXO_RELEASE_STORE_PASSWORD:?Missing store password}"
: "${VEXO_RELEASE_KEY_ALIAS:?Missing key alias}"
: "${VEXO_RELEASE_KEY_PASSWORD:?Missing key password}"
umask 077
export VEXO_RELEASE_KEYSTORE="$(mktemp "$RUNNER_TEMP/vexo-release.XXXXXX")"
csr="$(mktemp "$RUNNER_TEMP/vexo-check.XXXXXX")"
trap 'rm -f "$VEXO_RELEASE_KEYSTORE" "$csr"' EXIT
if ! printf '%s' "$KEYSTORE_BASE64" | tr -d '[:space:]' | base64 --decode > "$VEXO_RELEASE_KEYSTORE"; then
  echo "::error::Invalid Base64 keystore. Paste only the content of key_base64.txt."
  exit 1
fi
unset KEYSTORE_BASE64
# The supplied credentials file uses NAME=value. Accept that form as well as raw values.
normalize_credential() {
  local name="$1" value="$2"
  if [[ "$value" == "$name="* ]]; then value="${value#"$name="}"; fi
  printf '%s' "$value" | tr -d '\r\n'
}
store_ok() {
  keytool -list -keystore "$VEXO_RELEASE_KEYSTORE" -storepass:env VEXO_RELEASE_STORE_PASSWORD >/dev/null 2>&1
}
if ! store_ok; then
  export VEXO_RELEASE_STORE_PASSWORD="$(normalize_credential VEXO_RELEASE_STORE_PASSWORD "$VEXO_RELEASE_STORE_PASSWORD")"
  printf '::add-mask::%s\n' "$VEXO_RELEASE_STORE_PASSWORD"
  if ! store_ok; then
    echo "::error::Keystore could not be opened. Verify VEXO_RELEASE_STORE_PASSWORD and VEXO_RELEASE_KEYSTORE_BASE64 belong to the same key."
    exit 1
  fi
fi
export VEXO_RELEASE_KEY_ALIAS="$(normalize_credential VEXO_RELEASE_KEY_ALIAS "$VEXO_RELEASE_KEY_ALIAS")"
if ! keytool -list -alias "$VEXO_RELEASE_KEY_ALIAS" -keystore "$VEXO_RELEASE_KEYSTORE" -storepass:env VEXO_RELEASE_STORE_PASSWORD >/dev/null 2>&1; then
  echo "::error::Key alias not found. Check VEXO_RELEASE_KEY_ALIAS."
  exit 1
fi
key_ok() {
  keytool -certreq -alias "$VEXO_RELEASE_KEY_ALIAS" -keystore "$VEXO_RELEASE_KEYSTORE" -storepass:env VEXO_RELEASE_STORE_PASSWORD -keypass:env VEXO_RELEASE_KEY_PASSWORD -file "$csr" >/dev/null 2>&1
}
if ! key_ok; then
  export VEXO_RELEASE_KEY_PASSWORD="$(normalize_credential VEXO_RELEASE_KEY_PASSWORD "$VEXO_RELEASE_KEY_PASSWORD")"
  printf '::add-mask::%s\n' "$VEXO_RELEASE_KEY_PASSWORD"
  if ! key_ok; then
    echo "::error::Private key could not be unlocked. Check VEXO_RELEASE_KEY_PASSWORD."
    exit 1
  fi
fi
echo "Signing preflight passed: store, alias and private key verified."
bash .github/scripts/gradle-ci.sh testDebugUnitTest lintRelease assembleRelease bundleRelease
