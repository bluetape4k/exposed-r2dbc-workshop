#!/usr/bin/env bash

set -euo pipefail

max_attempts="${GRADLE_RATE_LIMIT_MAX_ATTEMPTS:-3}"
retry_delay_seconds="${GRADLE_RATE_LIMIT_RETRY_DELAY_SECONDS:-60}"

if [[ ! "$max_attempts" =~ ^[1-9][0-9]*$ ]]; then
  echo "GRADLE_RATE_LIMIT_MAX_ATTEMPTS must be a positive integer" >&2
  exit 2
fi

if [[ ! "$retry_delay_seconds" =~ ^[0-9]+$ ]]; then
  echo "GRADLE_RATE_LIMIT_RETRY_DELAY_SECONDS must be a non-negative integer" >&2
  exit 2
fi

log_file="$(mktemp "${RUNNER_TEMP:-/tmp}/gradle-rate-limit.XXXXXX")"
trap 'rm -f "$log_file"' EXIT

for ((attempt = 1; attempt <= max_attempts; attempt++)); do
  : > "$log_file"

  set +e
  ./gradlew "$@" 2>&1 | tee "$log_file"
  status=${PIPESTATUS[0]}
  set -e

  if (( status == 0 )); then
    exit 0
  fi

  if (( attempt == max_attempts )) || ! grep -Eqi \
    'Received status code 429 from server|429[[:space:]]+Too Many Requests|Too Many Requests' \
    "$log_file"; then
    exit "$status"
  fi

  delay=$((retry_delay_seconds * (1 << (attempt - 1))))
  echo "Gradle dependency rate limit detected; retrying in ${delay}s (attempt $((attempt + 1))/${max_attempts})." >&2
  sleep "$delay"
done
