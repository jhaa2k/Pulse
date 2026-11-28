#!/usr/bin/env bash
# Retry only identifiable dependency-resolution errors; never retry or suppress test/lint failures.
set -euo pipefail
log=$(mktemp)
trap 'rm -f "$log"' EXIT
for attempt in 1 2 3; do
  extra=()
  if [ "$attempt" -gt 1 ]; then extra+=(--refresh-dependencies); fi
  if ./gradlew "$@" "${extra[@]}" --stacktrace 2>&1 | tee "$log"; then
    exit 0
  fi
  if ! grep -Eiq 'could not resolve plugin artifact|could not resolve all (files|dependencies)|could not (get|download|resolve) (resource|artifact)|read timed out|connection reset|temporary failure in name resolution' "$log"; then
    exit 1
  fi
  if grep -Eiq 'there were failing tests|lint found [1-9][0-9]* errors|^e: file:|compilation error' "$log"; then
    exit 1
  fi
  if [ "$attempt" -eq 3 ]; then exit 1; fi
  echo "Dependency resolution failed; retrying the same Gradle tasks (attempt $((attempt+1))/3)."
  sleep $((attempt*10))
done
