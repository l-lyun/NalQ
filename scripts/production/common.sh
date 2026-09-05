#!/usr/bin/env bash

set -euo pipefail

PRODUCTION_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$PRODUCTION_SCRIPT_DIR/../.." && pwd)"
PRODUCTION_ROOT="$REPOSITORY_ROOT/infra/production"
COMPOSE_FILE="$PRODUCTION_ROOT/compose.yml"
DEFAULT_ENV_FILE="/opt/nalq/production.env"
DEFAULT_WEB_ENV_FILE="/opt/nalq/web-deploy.env"

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

load_env_file_unexported() {
	local env_file="$1"
	require_file "$env_file"
	set +a
	# shellcheck disable=SC1090
	. "$env_file"
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

atomic_write_file() {
	local content="$1"
	local target="$2"
	local target_directory temporary_file
	target_directory="$(dirname "$target")"
	install -d -m 0700 "$target_directory"
	temporary_file="$(mktemp "$target_directory/.nalq-state.XXXXXX")"
	if ! printf '%s\n' "$content" >"$temporary_file"; then
		rm -f "$temporary_file"
		die "failed to write temporary state file"
	fi
	chmod 0600 "$temporary_file"
	mv -f "$temporary_file" "$target"
}

require_restore_complete() {
	local state_directory="$1"
	local marker="$state_directory/restore-incomplete"
	[ ! -e "$marker" ] || die "restore is incomplete: deletion journal reapplication is required before server startup ($marker)"
}

record_rollback_candidate() {
	local current_image="$1"
	local target_image="$2"
	local state_directory="$3"
	local verified_marker verified_image marker_line_count
	[ -n "$current_image" ] || return 0
	require_immutable_image "$current_image"
	verified_marker="$state_directory/current-server-image"
	[ -f "$verified_marker" ] || die "running server has no last-verified image marker: $verified_marker"
	marker_line_count="$(wc -l <"$verified_marker" | tr -d ' ')"
	[ "$marker_line_count" = "1" ] || die "last-verified image marker is malformed: $verified_marker"
	verified_image="$(head -1 "$verified_marker")"
	if ! [[ "$verified_image" =~ @sha256:[0-9a-f]{64}$ ]]; then
		die "last-verified image marker is malformed: $verified_marker"
	fi
	if [ "$current_image" != "$verified_image" ]; then
		log "running image is not the last smoke-verified image; preserving the existing rollback target"
		return 0
	fi
	[ "$verified_image" != "$target_image" ] || return 0
	atomic_write_file "$verified_image" "$state_directory/previous-server-image"
	log "recorded current running image as rollback target before replacement"
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
