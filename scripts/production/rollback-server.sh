#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

ENV_FILE="$DEFAULT_ENV_FILE"
IMAGE=""
APPLY=false
CONFIRMATION=""
STATE_DIRECTORY="${NALQ_STATE_DIRECTORY:-/var/lib/nalq}"

while [ "$#" -gt 0 ]; do
	case "$1" in
		--env-file) ENV_FILE="${2:?missing value for --env-file}"; shift 2 ;;
		--image) IMAGE="${2:?missing value for --image}"; shift 2 ;;
		--apply) APPLY=true; shift ;;
		--confirm) CONFIRMATION="${2:?missing value for --confirm}"; shift 2 ;;
		*) die "unknown argument: $1" ;;
	esac
done

if [ -z "$IMAGE" ] && [ -f "$STATE_DIRECTORY/previous-server-image" ]; then
	IMAGE="$(head -1 "$STATE_DIRECTORY/previous-server-image")"
fi
[ -n "$IMAGE" ] || die "no previous image recorded; pass --image"
require_immutable_image "$IMAGE"

if [ "$APPLY" != true ]; then
	log "DRY RUN: would back up the database and roll back the server to $IMAGE"
	log "schema compatibility must be confirmed manually; Flyway is never rolled back"
	log "rerun with --apply --confirm ROLLBACK_SERVER"
	exit 0
fi

require_confirmation "$CONFIRMATION" ROLLBACK_SERVER
log "operator confirmed that the target image is compatible with the current Flyway schema"
"$SCRIPT_DIR/deploy-server.sh" \
	--env-file "$ENV_FILE" \
	--image "$IMAGE" \
	--apply \
	--confirm DEPLOY_SERVER
