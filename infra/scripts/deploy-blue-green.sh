#!/usr/bin/env bash
set -Eeuo pipefail

image_tag="${1:-}"
stockfit_home="${STOCKFIT_HOME:-/opt/stockit}"
runtime_env_file="${RUNTIME_ENV_FILE:-$stockfit_home/runtime/deployment.env}"
scripts_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
drain_seconds="${DRAIN_SECONDS:-30}"
lock_file="${DEPLOY_LOCK_FILE:-/var/lock/stockfit-deploy.lock}"

if [[ -z "$image_tag" || ! "$image_tag" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$ ]]; then
    echo "Usage: $0 <valid-ecr-image-tag>" >&2
    exit 64
fi

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

for required_file in "$compose_file" "$app_env_file"; do
    if [[ ! -f "$required_file" ]]; then
        echo "Required file is missing: $required_file" >&2
        exit 1
    fi
done

mkdir -p "$(dirname "$active_slot_file")"
exec 9>"$lock_file"
if ! flock -n 9; then
    echo "Another Stockit deployment is already running." >&2
    exit 1
fi

compose() {
    docker compose \
        --env-file "$runtime_env_file" \
        --env-file "$app_env_file" \
        -f "$compose_file" "$@"
}

set_env_value() {
    local key="$1"
    local value="$2"
    local temporary_file
    temporary_file="$(mktemp "${runtime_env_file}.XXXXXX")"
    awk -v key="$key" -v value="$value" '
        BEGIN { updated = 0 }
        index($0, key "=") == 1 { print key "=" value; updated = 1; next }
        { print }
        END { if (!updated) print key "=" value }
    ' "$runtime_env_file" > "$temporary_file"
    chmod --reference="$runtime_env_file" "$temporary_file"
    mv "$temporary_file" "$runtime_env_file"
}

current_slot=""
if [[ -f "$active_slot_file" ]]; then
    current_slot="$(tr -d '[:space:]' < "$active_slot_file")"
fi

case "$current_slot" in
    blue) target_slot="green" ;;
    green) target_slot="blue" ;;
    "") target_slot="blue" ;;
    *)
        echo "Invalid active slot state: $current_slot" >&2
        exit 1
        ;;
esac

target_service="backend-$target_slot"
target_key="BACKEND_${target_slot^^}_IMAGE_TAG"

echo "Deploying image tag '$image_tag' to $target_slot."
set_env_value "$target_key" "$image_tag"

compose pull "$target_service"
compose up -d redis rabbitmq
compose up -d --no-deps --force-recreate "$target_service"

if ! bash "$scripts_dir/health-check.sh" "$target_slot"; then
    compose stop "$target_service" || true
    echo "Deployment failed before traffic switch; $current_slot remains active." >&2
    exit 1
fi

NGINX_UPSTREAM_DIR="${NGINX_UPSTREAM_DIR:-/etc/nginx/stockit}" \
    bash "$scripts_dir/switch-upstream.sh" "$target_slot"
printf '%s\n' "$target_slot" > "$active_slot_file"

if [[ -n "$current_slot" ]]; then
    echo "Waiting ${drain_seconds}s before stopping $current_slot."
    sleep "$drain_seconds"
    compose stop "backend-$current_slot"
fi

echo "Deployment completed. Active slot: $target_slot, image tag: $image_tag"
