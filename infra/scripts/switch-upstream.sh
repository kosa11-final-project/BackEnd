#!/usr/bin/env bash
set -Eeuo pipefail

slot="${1:-}"
nginx_upstream_dir="${NGINX_UPSTREAM_DIR:-/etc/nginx/stockit}"

if [[ "$slot" != "blue" && "$slot" != "green" ]]; then
    echo "Usage: $0 <blue|green>" >&2
    exit 64
fi

source_file="$nginx_upstream_dir/upstream-$slot.conf"
target_link="$nginx_upstream_dir/upstream.conf"
previous_target="$(readlink "$target_link" 2>/dev/null || true)"

if [[ ! -f "$source_file" ]]; then
    echo "Missing Nginx upstream file: $source_file" >&2
    exit 1
fi

ln -sfn "$source_file" "$target_link"

if ! nginx -t; then
    if [[ -n "$previous_target" ]]; then
        ln -sfn "$previous_target" "$target_link"
    else
        rm -f "$target_link"
    fi
    echo "Nginx validation failed; the previous upstream was restored." >&2
    exit 1
fi

systemctl reload nginx
echo "Nginx now routes traffic to $slot."
