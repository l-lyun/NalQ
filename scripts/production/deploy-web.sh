#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

ENV_FILE="$DEFAULT_WEB_ENV_FILE"
APPLY=false
CONFIRMATION=""

while [ "$#" -gt 0 ]; do
	case "$1" in
		--env-file) ENV_FILE="${2:?missing value for --env-file}"; shift 2 ;;
		--apply) APPLY=true; shift ;;
		--confirm) CONFIRMATION="${2:?missing value for --confirm}"; shift 2 ;;
		*) die "unknown argument: $1" ;;
	esac
done

load_env_file_unexported "$ENV_FILE"
release="$(git -C "$REPOSITORY_ROOT" rev-parse HEAD)"

if [ "$APPLY" != true ]; then
	log "DRY RUN: would build release $release and publish web/dist to s3://$WEB_S3_BUCKET"
	log "index.html is uploaded last; rollback copies a retained release prefix"
	log "rerun with --apply --confirm DEPLOY_WEB"
	exit 0
fi

require_confirmation "$CONFIRMATION" DEPLOY_WEB
"$SCRIPT_DIR/validate-web-env.sh" --env-file "$ENV_FILE"
require_command node
require_command pnpm
require_command aws

web_aws() {
	AWS_REGION="$AWS_REGION" aws "$@"
}

[ -z "$(git -C "$REPOSITORY_ROOT" status --porcelain)" ] || die "web deployment requires a clean working tree"

log "verifying and building production web artifact"
pnpm_command="$(command -v pnpm)"
node_runtime_directory="$(dirname "$(node -p 'process.execPath')")"
env -i \
	PATH="$node_runtime_directory:$PATH" \
	TMPDIR="${TMPDIR:-/tmp}" \
	VITE_API_BASE_URL="$VITE_API_BASE_URL" \
	VITE_QUIZ_RUNTIME_MODE=api \
	VITE_APP_VERSION="$release" \
	"$pnpm_command" -C "$REPOSITORY_ROOT/web" verify

release_uri="s3://$WEB_S3_BUCKET/releases/$release"
web_aws s3 cp "$REPOSITORY_ROOT/web/dist/" "$release_uri/" \
	--recursive --only-show-errors --cache-control 'no-cache'
web_aws s3 sync "$REPOSITORY_ROOT/web/dist/assets/" "s3://$WEB_S3_BUCKET/assets/" \
	--only-show-errors --cache-control 'public,max-age=31536000,immutable'
web_aws s3 sync "$REPOSITORY_ROOT/web/dist/" "s3://$WEB_S3_BUCKET/" \
	--exclude 'assets/*' --exclude 'index.html' --only-show-errors --cache-control 'public,max-age=300'
web_aws s3 cp "$REPOSITORY_ROOT/web/dist/index.html" "s3://$WEB_S3_BUCKET/index.html" \
	--only-show-errors --cache-control 'no-cache,no-store,must-revalidate' --content-type 'text/html; charset=utf-8'
printf '%s\n' "$release" | web_aws s3 cp - "s3://$WEB_S3_BUCKET/releases/current" \
	--only-show-errors --cache-control 'no-cache'
web_aws cloudfront create-invalidation \
	--distribution-id "$CLOUDFRONT_DISTRIBUTION_ID" \
	--paths '/index.html' >/dev/null

log "web release published: $release"
