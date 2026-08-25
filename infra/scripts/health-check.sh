#!/usr/bin/env bash
set -Eeuo pipefail

target="${1:-}"
attempts="${HEALTH_CHECK_ATTEMPTS:-30}"
interval_seconds="${HEALTH_CHECK_INTERVAL_SECONDS:-5}"

case "$target" in
    blue) health_url="http://127.0.0.1:8081/actuator/health" ;;
    green) health_url="http://127.0.0.1:8082/actuator/health" ;;
    http://*|https://*) health_url="$target" ;;
    *)
        echo "Usage: $0 <blue|green|health-url>" >&2
        exit 64
        ;;
esac

if ! [[ "$attempts" =~ ^[1-9][0-9]*$ && "$interval_seconds" =~ ^[1-9][0-9]*$ ]]; then
    echo "Health check attempts and interval must be positive integers." >&2
    exit 64
fi

for ((attempt = 1; attempt <= attempts; attempt++)); do
    response="$(curl --silent --show-error --max-time 5 "$health_url" 2>/dev/null || true)"
    if grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<<"$response"; then
        echo "Health check succeeded: $health_url"
        exit 0
    fi

    echo "Health check $attempt/$attempts failed: $health_url" >&2
    sleep "$interval_seconds"
done

echo "Health check exhausted all retries: $health_url" >&2
exit 1
