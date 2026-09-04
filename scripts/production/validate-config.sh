#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

ENV_FILE="$PRODUCTION_ROOT/.env.example"

while [ "$#" -gt 0 ]; do
	case "$1" in
		--env-file) ENV_FILE="${2:?missing value for --env-file}"; shift 2 ;;
		*) die "unknown argument: $1" ;;
	esac
done

require_command docker
require_command node

log "validating production Compose syntax without printing secrets"
docker compose --env-file "$ENV_FILE" --file "$COMPOSE_FILE" config --quiet

log "validating production shell syntax"
for script in "$SCRIPT_DIR"/*.sh; do
	bash -n "$script"
done

log "validating CloudFront Function JavaScript syntax"
node --check "$PRODUCTION_ROOT/cloudfront/spa-rewrite.js"

log "static infrastructure configuration validation passed"
