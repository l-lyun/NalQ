#!/usr/bin/env bash

set -euo pipefail

PRODUCTION_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$PRODUCTION_SCRIPT_DIR/../.." && pwd)"
PRODUCTION_ROOT="$REPOSITORY_ROOT/infra/production"
COMPOSE_FILE="$PRODUCTION_ROOT/compose.yml"
DEFAULT_ENV_FILE="/opt/nalq/production.env"

die() {
	printf 'ERROR: %s\n' "$*" >&2
	exit 1
}

log() {
	printf '==> %s\n' "$*"
}

require_command() {
	command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

require_file() {
	[ -f "$1" ] || die "required file not found: $1"
}

load_env_file() {
	local env_file="$1"
	require_file "$env_file"
	set -a
	# shellcheck disable=SC1090
	. "$env_file"
	set +a
}

require_confirmation() {
	local actual="$1"
	local expected="$2"
	[ "$actual" = "$expected" ] || die "apply requires --confirm $expected"
}

require_immutable_image() {
	local image="$1"
	[[ "$image" =~ @sha256:[0-9a-f]{64}$ ]] || die "server image must be pinned by sha256 digest"
}

compose() {
	docker compose --env-file "$ENV_FILE" --file "$COMPOSE_FILE" "$@"
}

sha256_file() {
	if command -v sha256sum >/dev/null 2>&1; then
		sha256sum "$1"
	else
		shasum -a 256 "$1"
	fi
}
