#!/usr/bin/env bash
set -Eeuo pipefail

oracle_schema="${ORACLE_SCHEMA:-STOCKIT}"
oracle_directory_object="${ORACLE_DIRECTORY_OBJECT:-STOCKIT_BACKUP_DIR}"
oracle_backup_dir="${ORACLE_BACKUP_DIR:-/opt/oracle/backup/stockit}"
backup_s3_uri="${BACKUP_S3_URI:?BACKUP_S3_URI is required}"
metric_file="${BACKUP_METRIC_FILE:-}"
timestamp="$(date '+%Y%m%d_%H%M%S')"
base_name="stockit_${timestamp}"
dump_name="${base_name}.dmp"
log_name="${base_name}.log"
archive_name="${dump_name}.gz"
lock_file="${BACKUP_LOCK_FILE:-$oracle_backup_dir/.backup.lock}"

if ! [[ "$oracle_schema" =~ ^[A-Za-z][A-Za-z0-9_$#]*(,[A-Za-z][A-Za-z0-9_$#]*)*$ ]]; then
    echo "ORACLE_SCHEMA contains an invalid schema name." >&2
    exit 64
fi

if ! [[ "$oracle_directory_object" =~ ^[A-Za-z][A-Za-z0-9_$#]*$ ]]; then
    echo "ORACLE_DIRECTORY_OBJECT is invalid." >&2
    exit 64
fi

write_metric() {
    local success="$1"
    [[ -z "$metric_file" ]] && return 0
    mkdir -p "$(dirname "$metric_file")"
    {
        echo "# HELP stockit_oracle_backup_success Whether the last Oracle backup succeeded."
        echo "# TYPE stockit_oracle_backup_success gauge"
        echo "stockit_oracle_backup_success $success"
        echo "# HELP stockit_oracle_backup_last_attempt_timestamp_seconds Last Oracle backup attempt time."
        echo "# TYPE stockit_oracle_backup_last_attempt_timestamp_seconds gauge"
        echo "stockit_oracle_backup_last_attempt_timestamp_seconds $(date +%s)"
    } > "${metric_file}.tmp"
    mv "${metric_file}.tmp" "$metric_file"
}

on_error() {
    write_metric 0 || true
    echo "Oracle backup failed." >&2
}
trap on_error ERR

for command_name in expdp aws gzip sha256sum flock; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "Required command is missing: $command_name" >&2
        exit 1
    fi
done

mkdir -p "$oracle_backup_dir"
exec 9>"$lock_file"
if ! flock -n 9; then
    echo "Another Oracle backup is already running." >&2
    exit 1
fi

parameter_file="$(mktemp "$oracle_backup_dir/stockit-expdp.XXXXXX.par")"
trap 'rm -f "$parameter_file"' EXIT
cat > "$parameter_file" <<EOF
schemas=$oracle_schema
directory=$oracle_directory_object
dumpfile=$dump_name
logfile=$log_name
reuse_dumpfiles=no
compression=metadata_only
EOF
chmod 600 "$parameter_file"

echo "Starting Oracle Data Pump export for schema: $oracle_schema"
expdp "/ as sysdba" parfile="$parameter_file"

gzip -9 "$oracle_backup_dir/$dump_name"
(
    cd "$oracle_backup_dir"
    sha256sum "$archive_name" > "${archive_name}.sha256"
)

destination="${backup_s3_uri%/}/$(date '+%Y/%m/%d')"
aws s3 cp "$oracle_backup_dir/$archive_name" "$destination/$archive_name" --only-show-errors
aws s3 cp "$oracle_backup_dir/${archive_name}.sha256" "$destination/${archive_name}.sha256" --only-show-errors
aws s3 cp "$oracle_backup_dir/$log_name" "$destination/$log_name" --only-show-errors

rm -f \
    "$oracle_backup_dir/$archive_name" \
    "$oracle_backup_dir/${archive_name}.sha256" \
    "$oracle_backup_dir/$log_name"

write_metric 1
trap - ERR
echo "Oracle backup uploaded successfully: $destination/$archive_name"
