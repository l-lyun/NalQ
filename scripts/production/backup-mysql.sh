#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

ENV_FILE="$DEFAULT_ENV_FILE"
APPLY=false

while [ "$#" -gt 0 ]; do
	case "$1" in
		--env-file) ENV_FILE="${2:?missing value for --env-file}"; shift 2 ;;
		--apply) APPLY=true; shift ;;
		*) die "unknown argument: $1" ;;
	esac
done

load_env_file "$ENV_FILE"
[ -n "${DB_BACKUP_S3_URI:-}" ] || die "DB_BACKUP_S3_URI is required"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
object_uri="${DB_BACKUP_S3_URI%/}/openmd-${timestamp}.sql.gz"

if [ "$APPLY" != true ]; then
	log "DRY RUN: would create a consistent MySQL dump and upload it to $object_uri"
	log "rerun with --apply after reviewing the destination and IAM role"
	exit 0
fi

require_command docker
require_command aws
require_command gzip

temporary_directory="$(mktemp -d)"
trap 'rm -rf "$temporary_directory"' EXIT
dump_file="$temporary_directory/openmd-${timestamp}.sql.gz"
checksum_file="$dump_file.sha256"

log "creating compressed logical backup"
compose exec -T mysql sh -c \
	'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --user=root --single-transaction --quick --skip-lock-tables --routines --events --triggers --set-gtid-purged=OFF "$MYSQL_DATABASE"' \
	| gzip -9 >"$dump_file"

sha256_file "$dump_file" >"$checksum_file"
aws s3 cp "$dump_file" "$object_uri" --only-show-errors --sse AES256
aws s3 cp "$checksum_file" "$object_uri.sha256" --only-show-errors --sse AES256
aws s3 ls "$object_uri" >/dev/null
aws s3 ls "$object_uri.sha256" >/dev/null

log "backup uploaded with checksum: $object_uri"
