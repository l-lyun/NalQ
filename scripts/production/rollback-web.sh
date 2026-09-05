#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

ENV_FILE="$DEFAULT_WEB_ENV_FILE"
RELEASE=""
APPLY=false
CONFIRMATION=""

while [ "$#" -gt 0 ]; do
	case "$1" in
		--env-file) ENV_FILE="${2:?missing value for --env-file}"; shift 2 ;;
		--release) RELEASE="${2:?missing value for --release}"; shift 2 ;;
		--apply) APPLY=true; shift ;;
		--confirm) CONFIRMATION="${2:?missing value for --confirm}"; shift 2 ;;
		*) die "unknown argument: $1" ;;
	esac
done

[[ "$RELEASE" =~ ^[0-9a-f]{40}$ ]] || die "--release must be a full Git commit SHA"
load_env_file_unexported "$ENV_FILE"

if [ "$APPLY" != true ]; then
	log "DRY RUN: would restore web release $RELEASE from the private S3 release prefix"
	log "rerun with --apply --confirm ROLLBACK_WEB"
	exit 0
fi

require_confirmation "$CONFIRMATION" ROLLBACK_WEB
"$SCRIPT_DIR/validate-web-env.sh" --env-file "$ENV_FILE"
require_command aws

web_aws() {
	AWS_REGION="$AWS_REGION" aws "$@"
}

release_uri="s3://$WEB_S3_BUCKET/releases/$RELEASE"
web_aws s3 ls "$release_uri/index.html" >/dev/null
web_aws s3 cp "$release_uri/assets/" "s3://$WEB_S3_BUCKET/assets/" \
	--recursive --only-show-errors --cache-control 'public,max-age=31536000,immutable' --metadata-directive REPLACE
web_aws s3 cp "$release_uri/" "s3://$WEB_S3_BUCKET/" \
	--recursive --exclude 'assets/*' --exclude 'index.html' --only-show-errors --cache-control 'public,max-age=300' --metadata-directive REPLACE
web_aws s3 cp "$release_uri/index.html" "s3://$WEB_S3_BUCKET/index.html" \
	--only-show-errors --cache-control 'no-cache,no-store,must-revalidate' --content-type 'text/html; charset=utf-8' --metadata-directive REPLACE
printf '%s\n' "$RELEASE" | web_aws s3 cp - "s3://$WEB_S3_BUCKET/releases/current" \
	--only-show-errors --cache-control 'no-cache'
web_aws cloudfront create-invalidation \
	--distribution-id "$CLOUDFRONT_DISTRIBUTION_ID" \
	--paths '/index.html' >/dev/null

log "web rollback published: $RELEASE"
