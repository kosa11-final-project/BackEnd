#!/usr/bin/env bash
set -Eeuo pipefail

stockfit_home="${STOCKFIT_HOME:-/opt/stockit}"
runtime_env_file="${RUNTIME_ENV_FILE:-$stockfit_home/runtime/deployment.env}"
scripts_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
lock_file="${DEPLOY_LOCK_FILE:-/var/lock/stockfit-deploy.lock}"
drain_seconds="${DRAIN_SECONDS:-30}"

if [[ ! -f "$runtime_env_file" ]]; then
    echo "Required file is missing: $runtime_env_file" >&2
    exit 1
fi

set -a
# shellcheck disable=SC1090
source "$runtime_env_file"
set +a

stockfit_home="${STOCKFIT_HOME:-/opt/stockit}"
compose_file="${COMPOSE_FILE:-$stockfit_home/infra/compose/compose.prod.yml}"
app_env_file="${APP_ENV_FILE:-$stockfit_home/config/app.env}"
active_slot_file="${ACTIVE_SLOT_FILE:-$stockfit_home/runtime/active-slot}"

if [[ ! -f "$active_slot_file" ]]; then
    echo "Cannot rollback before the first successful deployment." >&2
    exit 1
fi

current_slot="$(tr -d '[:space:]' < "$active_slot_file")"
case "$current_slot" in
    blue) rollback_slot="green" ;;
    green) rollback_slot="blue" ;;
    *)
        echo "Invalid active slot state: $current_slot" >&2
        exit 1
        ;;
esac

rollback_key="BACKEND_${rollback_slot^^}_IMAGE_TAG"
rollback_tag="${!rollback_key:-}"
if [[ -z "$rollback_tag" || "$rollback_tag" == "bootstrap" ]]; then
    echo "No previously deployed image is recorded for $rollback_slot." >&2
    exit 1
fi

exec 9>"$lock_file"
if ! flock -n 9; then
    echo "Another Stockit deployment operation is already running." >&2
    exit 1
fi

compose() {
    docker compose \
        --env-file "$runtime_env_file" \
        --env-file "$app_env_file" \
        -f "$compose_file" "$@"
}

echo "Starting rollback slot: $rollback_slot"
compose up -d redis rabbitmq
compose up -d --no-deps "backend-$rollback_slot"

if ! bash "$scripts_dir/health-check.sh" "$rollback_slot"; then
    compose stop "backend-$rollback_slot" || true
    echo "Rollback target is unhealthy; traffic remains on $current_slot." >&2
    exit 1
fi

NGINX_UPSTREAM_DIR="${NGINX_UPSTREAM_DIR:-/etc/nginx/stockit}" \
bash "$scripts_dir/switch-upstream.sh" "$rollback_slot"
printf '%s\n' "$rollback_slot" > "$active_slot_file"
echo "Waiting ${drain_seconds}s before stopping $current_slot."
sleep "$drain_seconds"
compose stop "backend-$current_slot" || true

echo "Rollback completed. Active slot: $rollback_slot"
