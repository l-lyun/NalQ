#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

ENV_FILE="$DEFAULT_ENV_FILE"
SOURCE_URI=""
APPLY=false
CONFIRMATION=""

while [ "$#" -gt 0 ]; do
	case "$1" in
		--env-file) ENV_FILE="${2:?missing value for --env-file}"; shift 2 ;;
		--source) SOURCE_URI="${2:?missing value for --source}"; shift 2 ;;
		--apply) APPLY=true; shift ;;
		--confirm) CONFIRMATION="${2:?missing value for --confirm}"; shift 2 ;;
		*) die "unknown argument: $1" ;;
	esac
done

[ -n "$SOURCE_URI" ] || die "--source s3://.../backup.sql.gz is required"
load_env_file "$ENV_FILE"
case "$SOURCE_URI" in
	"${DB_BACKUP_S3_URI%/}/"*.sql.gz) ;;
	*) die "source must be a .sql.gz object below DB_BACKUP_S3_URI" ;;
esac

if [ "$APPLY" != true ]; then
	log "DRY RUN: would restore $SOURCE_URI only into an empty $MYSQL_DATABASE database"
	log "rerun with --apply --confirm RESTORE_EMPTY_DATABASE"
	exit 0
fi

require_confirmation "$CONFIRMATION" RESTORE_EMPTY_DATABASE
require_command docker
require_command aws
require_command gzip

table_count="$(compose exec -T mysql sh -c \
	'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --batch --skip-column-names --user=root --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE();" "$MYSQL_DATABASE"')"
[ "$table_count" = "0" ] || die "restore refused: target database is not empty"

temporary_directory="$(mktemp -d)"
trap 'rm -rf "$temporary_directory"' EXIT
dump_file="$temporary_directory/restore.sql.gz"
checksum_file="$dump_file.sha256"

aws s3 cp "$SOURCE_URI" "$dump_file" --only-show-errors
aws s3 cp "$SOURCE_URI.sha256" "$checksum_file" --only-show-errors
expected_hash="$(awk '{print $1}' "$checksum_file")"
actual_hash="$(sha256_file "$dump_file" | awk '{print $1}')"
[ "$actual_hash" = "$expected_hash" ] || die "backup checksum mismatch"

log "restoring verified backup into empty database"
gzip -dc "$dump_file" | compose exec -T mysql sh -c \
	'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root "$MYSQL_DATABASE"'
log "restore completed; run server startup and application smoke tests next"
