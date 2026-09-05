#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

ENV_FILE="$DEFAULT_ENV_FILE"
IMAGE=""
APPLY=false
CONFIRMATION=""
SKIP_BACKUP=false
EMPTY_DATABASE_CONFIRMATION=""
STATE_DIRECTORY="${NALQ_STATE_DIRECTORY:-/var/lib/nalq}"

while [ "$#" -gt 0 ]; do
	case "$1" in
		--env-file) ENV_FILE="${2:?missing value for --env-file}"; shift 2 ;;
		--image) IMAGE="${2:?missing value for --image}"; shift 2 ;;
		--apply) APPLY=true; shift ;;
		--confirm) CONFIRMATION="${2:?missing value for --confirm}"; shift 2 ;;
		--skip-backup) SKIP_BACKUP=true; shift ;;
		--confirm-empty-db) EMPTY_DATABASE_CONFIRMATION="${2:?missing value for --confirm-empty-db}"; shift 2 ;;
		*) die "unknown argument: $1" ;;
	esac
done

load_env_file "$ENV_FILE"
IMAGE="${IMAGE:-${SERVER_IMAGE:-}}"
require_immutable_image "$IMAGE"
require_restore_complete "$STATE_DIRECTORY"

if [ "$APPLY" != true ]; then
	log "DRY RUN: would deploy immutable server image $IMAGE"
	log "steps: validate env, start/wait MySQL+Redis, backup existing DB, pull image, replace/wait server, HTTPS smoke"
	log "rerun with --apply --confirm DEPLOY_SERVER"
	exit 0
fi

require_confirmation "$CONFIRMATION" DEPLOY_SERVER
export SERVER_IMAGE="$IMAGE"
"$SCRIPT_DIR/validate-env.sh" --env-file "$ENV_FILE"
require_command docker
require_command curl

log "starting and waiting for stateful dependencies"
compose up --detach --wait mysql redis

if [ "$SKIP_BACKUP" != true ]; then
	"$SCRIPT_DIR/backup-mysql.sh" --env-file "$ENV_FILE" --apply
else
	require_confirmation "$EMPTY_DATABASE_CONFIRMATION" FIRST_EMPTY_DATABASE
	log "WARNING: backup explicitly skipped after operator confirmed a verified empty first database"
fi

require_restore_complete "$STATE_DIRECTORY"
running_container_id="$(compose ps --status running --quiet server 2>/dev/null | head -1)"
running_image=""
if [ -n "$running_container_id" ]; then
	running_image="$(docker inspect --format '{{.Config.Image}}' "$running_container_id")"
fi
record_rollback_candidate "$running_image" "$IMAGE" "$STATE_DIRECTORY"

log "pulling and starting server image"
compose pull server
compose up --detach --no-deps --wait server

status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
	--max-time 10 "https://${API_DOMAIN}/api/v1/auth/web/sessions/refresh" || true)"
case "$status" in
	405) ;;
	*) die "external API smoke failed with HTTP status ${status:-none}" ;;
esac

atomic_write_file "$IMAGE" "$STATE_DIRECTORY/current-server-image"

log "server deployment passed container and HTTPS smoke checks"
