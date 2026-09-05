#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

temporary_directory="$(mktemp -d)"
trap 'rm -rf "$temporary_directory"' EXIT

expect_failure_containing() {
	local expected="$1"
	shift
	local output status
	set +e
	output="$("$@" 2>&1)"
	status=$?
	set -e
	[ "$status" -ne 0 ] || {
		printf 'expected command to fail: %s\n' "$*" >&2
		exit 1
	}
	case "$output" in
		*"$expected"*) ;;
		*) printf 'expected failure containing %s, got: %s\n' "$expected" "$output" >&2; exit 1 ;;
	esac
}

write_valid_server_env() {
	local target="$1"
	printf '%s\n' \
		'AWS_REGION=ap-northeast-2' \
		'SERVICE_DOMAIN=nalq.test' \
		'WEB_DOMAIN=app.nalq.test' \
		'API_DOMAIN=api.nalq.test' \
		'WEB_ORIGIN=https://app.nalq.test' \
		'DB_BACKUP_S3_URI=s3://nalq-test-backup/mysql' \
		'SERVER_IMAGE=registry.invalid/openmd/server@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' \
		'SERVER_HOST_PORT=8080' \
		'MYSQL_DATABASE=openmd' \
		'MYSQL_USER=openmd' \
		'MYSQL_PASSWORD=test-only' \
		'MYSQL_ROOT_PASSWORD=test-only-root' \
		'OPENMD_CORS_ALLOWED_ORIGINS=https://app.nalq.test' \
		'OPENMD_AUTH_BROWSER_ALLOWED_ORIGINS=https://app.nalq.test' \
		'OPENMD_AUTH_ACCESS_TOKEN_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=' \
		'OPENMD_AUTH_EMAIL_CODE_HMAC_SECRET=QkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkI=' \
		'OPENMD_MAIL_FROM=no-reply@nalq.test' \
		'SPRING_MAIL_HOST=smtp.nalq.test' \
		'SPRING_MAIL_USERNAME=test-only' \
		'SPRING_MAIL_PASSWORD=test-only' \
		'OPENMD_NOTION_ENABLED=false' \
		'OPENMD_QUIZ_GENERATION_ENABLED=false' \
		'SPRING_AI_OPENAI_BASE_URL=https://us.api.openai.com' \
		'OPENAI_API_KEY=no-key-configured' >"$target"
}

write_valid_web_env() {
	local target="$1"
	printf '%s\n' \
		'AWS_REGION=ap-northeast-2' \
		'SERVICE_DOMAIN=nalq.test' \
		'WEB_DOMAIN=app.nalq.test' \
		'API_DOMAIN=api.nalq.test' \
		'VITE_API_BASE_URL=https://api.nalq.test' \
		'WEB_S3_BUCKET=nalq-test-web' \
		'CLOUDFRONT_DISTRIBUTION_ID=TESTDISTRIBUTION' >"$target"
}

server_env="$temporary_directory/server.env"
write_valid_server_env "$server_env"
"$SCRIPT_DIR/validate-env.sh" --env-file "$server_env" >/dev/null
"$SCRIPT_DIR/deploy-server.sh" --env-file "$server_env" >/dev/null

server_cross_site_env="$temporary_directory/server-cross-site.env"
cp "$server_env" "$server_cross_site_env"
printf '%s\n' 'API_DOMAIN=api.other.test' >>"$server_cross_site_env"
expect_failure_containing 'API_DOMAIN must equal api.SERVICE_DOMAIN' \
	"$SCRIPT_DIR/validate-env.sh" --env-file "$server_cross_site_env"

bad_port_env="$temporary_directory/bad-port.env"
cp "$server_env" "$bad_port_env"
printf '%s\n' 'SERVER_HOST_PORT=18080' >>"$bad_port_env"
expect_failure_containing 'SERVER_HOST_PORT must be exactly 8080' \
	"$SCRIPT_DIR/validate-env.sh" --env-file "$bad_port_env"

global_openai_env="$temporary_directory/global-openai.env"
cp "$server_env" "$global_openai_env"
printf '%s\n' 'SPRING_AI_OPENAI_BASE_URL=https://api.openai.com' >>"$global_openai_env"
expect_failure_containing 'SPRING_AI_OPENAI_BASE_URL must be https://us.api.openai.com' \
	"$SCRIPT_DIR/validate-env.sh" --env-file "$global_openai_env"

notion_missing_env="$temporary_directory/notion-missing.env"
cp "$server_env" "$notion_missing_env"
printf '%s\n' \
	'OPENMD_NOTION_ENABLED=true' \
	'OPENMD_NOTION_CLIENT_ID=test-client' \
	'OPENMD_NOTION_CLIENT_SECRET=test-secret' \
	'OPENMD_NOTION_CALLBACK_URI=https://api.nalq.test/api/v1/integrations/notion/callback' \
	'OPENMD_NOTION_TOKEN_KEYS=v1:QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=' \
	'OPENMD_NOTION_WRITE_KEY_VERSION=v1' >>"$notion_missing_env"
expect_failure_containing 'OPENMD_NOTION_ALLOWED_RETURN_URIS is required' \
	"$SCRIPT_DIR/validate-env.sh" --env-file "$notion_missing_env"

notion_valid_env="$temporary_directory/notion-valid.env"
cp "$notion_missing_env" "$notion_valid_env"
printf '%s\n' \
	'OPENMD_NOTION_ALLOWED_RETURN_URIS=https://app.nalq.test/learning/import/notion,https://app.nalq.test/notion/failure' \
	'OPENMD_NOTION_FAILURE_RETURN_URI=https://app.nalq.test/notion/failure' >>"$notion_valid_env"
"$SCRIPT_DIR/validate-env.sh" --env-file "$notion_valid_env" >/dev/null

notion_short_key_env="$temporary_directory/notion-short-key.env"
cp "$notion_valid_env" "$notion_short_key_env"
printf '%s\n' 'OPENMD_NOTION_TOKEN_KEYS=v1:QUFB' >>"$notion_short_key_env"
expect_failure_containing 'must be version:base64' \
	"$SCRIPT_DIR/validate-env.sh" --env-file "$notion_short_key_env"

notion_duplicate_key_env="$temporary_directory/notion-duplicate-key.env"
cp "$notion_valid_env" "$notion_duplicate_key_env"
printf '%s\n' 'OPENMD_NOTION_TOKEN_KEYS=v1:QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=,v1:QkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkI=' >>"$notion_duplicate_key_env"
expect_failure_containing 'contains a duplicate version' \
	"$SCRIPT_DIR/validate-env.sh" --env-file "$notion_duplicate_key_env"

notion_missing_write_version_env="$temporary_directory/notion-missing-write-version.env"
cp "$notion_valid_env" "$notion_missing_write_version_env"
printf '%s\n' 'OPENMD_NOTION_WRITE_KEY_VERSION=v2' >>"$notion_missing_write_version_env"
expect_failure_containing 'must exist in OPENMD_NOTION_TOKEN_KEYS' \
	"$SCRIPT_DIR/validate-env.sh" --env-file "$notion_missing_write_version_env"

notion_invalid_write_version_env="$temporary_directory/notion-invalid-write-version.env"
cp "$notion_valid_env" "$notion_invalid_write_version_env"
printf '%s\n' 'OPENMD_NOTION_WRITE_KEY_VERSION=v1:invalid' >>"$notion_invalid_write_version_env"
expect_failure_containing 'has an invalid format' \
	"$SCRIPT_DIR/validate-env.sh" --env-file "$notion_invalid_write_version_env"

notion_bad_allowlist_env="$temporary_directory/notion-bad-allowlist.env"
cp "$notion_valid_env" "$notion_bad_allowlist_env"
printf '%s\n' 'OPENMD_NOTION_FAILURE_RETURN_URI=https://app.nalq.test/notion/not-allowed' >>"$notion_bad_allowlist_env"
expect_failure_containing 'must be an exact member' \
	"$SCRIPT_DIR/validate-env.sh" --env-file "$notion_bad_allowlist_env"

notion_http_env="$temporary_directory/notion-http.env"
cp "$notion_valid_env" "$notion_http_env"
printf '%s\n' \
	'OPENMD_NOTION_ALLOWED_RETURN_URIS=http://app.nalq.test/notion/failure' \
	'OPENMD_NOTION_FAILURE_RETURN_URI=http://app.nalq.test/notion/failure' >>"$notion_http_env"
expect_failure_containing 'must be an absolute https URI' \
	"$SCRIPT_DIR/validate-env.sh" --env-file "$notion_http_env"

web_env="$temporary_directory/web.env"
write_valid_web_env "$web_env"
"$SCRIPT_DIR/validate-web-env.sh" --env-file "$web_env" >/dev/null
"$SCRIPT_DIR/deploy-web.sh" --env-file "$web_env" >/dev/null
"$SCRIPT_DIR/rollback-web.sh" --env-file "$web_env" --release 0123456789abcdef0123456789abcdef01234567 >/dev/null

web_cross_site_env="$temporary_directory/web-cross-site.env"
cp "$web_env" "$web_cross_site_env"
printf '%s\n' 'WEB_DOMAIN=app.other.test' >>"$web_cross_site_env"
expect_failure_containing 'WEB_DOMAIN must equal app.SERVICE_DOMAIN' \
	"$SCRIPT_DIR/validate-web-env.sh" --env-file "$web_cross_site_env"

web_secret_env="$temporary_directory/web-secret.env"
cp "$web_env" "$web_secret_env"
printf '%s\n' 'MYSQL_PASSWORD=must-not-reach-web-build' >>"$web_secret_env"
expect_failure_containing 'must not contain server configuration or secrets' \
	"$SCRIPT_DIR/validate-web-env.sh" --env-file "$web_secret_env"

grep -Fq 'validate-web-env.sh' "$SCRIPT_DIR/deploy-web.sh"
if grep -Eq '/validate-env\.sh|load_env_file ' "$SCRIPT_DIR/deploy-web.sh"; then
	printf 'deploy-web must not load or validate the server environment\n' >&2
	exit 1
fi
grep -Fq 'env -i' "$SCRIPT_DIR/deploy-web.sh"
grep -Fq 'VITE_HOME_VISITS_API_ENABLED=true' "$SCRIPT_DIR/deploy-web.sh"

restore_state="$temporary_directory/restore-state"
mkdir -p "$restore_state"
printf '%s\n' 'status=deletion-journal-reapply-required' >"$restore_state/restore-incomplete"
expect_failure_containing 'restore is incomplete' \
	env NALQ_STATE_DIRECTORY="$restore_state" "$SCRIPT_DIR/deploy-server.sh" --env-file "$server_env"

first_image='registry.invalid/openmd/server@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
second_image='registry.invalid/openmd/server@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
third_image='registry.invalid/openmd/server@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc'
fourth_image='registry.invalid/openmd/server@sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd'
rollback_state="$temporary_directory/rollback-state"
mkdir -p "$rollback_state"

# First deploy has no running image and therefore needs no verified marker.
record_rollback_candidate "" "$second_image" "$rollback_state"
[ ! -e "$rollback_state/previous-server-image" ]

# A running container without a trustworthy success marker must stop safely.
expect_failure_containing 'no last-verified image marker' \
	record_rollback_candidate "$first_image" "$second_image" "$rollback_state"
printf '%s\n%s\n' "$first_image" "$second_image" >"$rollback_state/current-server-image"
expect_failure_containing 'last-verified image marker is malformed' \
	record_rollback_candidate "$first_image" "$second_image" "$rollback_state"
printf '%s\n' 'not-an-image-digest' >"$rollback_state/current-server-image"
expect_failure_containing 'last-verified image marker is malformed' \
	record_rollback_candidate "$first_image" "$second_image" "$rollback_state"

# A was smoke-verified; a normal A -> B deployment records A.
atomic_write_file "$first_image" "$rollback_state/current-server-image"
record_rollback_candidate "$first_image" "$second_image" "$rollback_state" >/dev/null
[ "$(head -1 "$rollback_state/previous-server-image")" = "$first_image" ]

# B failed smoke, so B -> C retry must preserve A instead of promoting B.
record_rollback_candidate "$second_image" "$third_image" "$rollback_state" >/dev/null
[ "$(head -1 "$rollback_state/previous-server-image")" = "$first_image" ]

# C succeeding changes only the verified marker; A remains its rollback target.
atomic_write_file "$third_image" "$rollback_state/current-server-image"
[ "$(head -1 "$rollback_state/previous-server-image")" = "$first_image" ]

# Redeploying the same verified image leaves the prior rollback target intact.
record_rollback_candidate "$third_image" "$third_image" "$rollback_state" >/dev/null
[ "$(head -1 "$rollback_state/previous-server-image")" = "$first_image" ]

# A later normal C -> D deployment records verified C.
record_rollback_candidate "$third_image" "$fourth_image" "$rollback_state" >/dev/null
[ "$(head -1 "$rollback_state/previous-server-image")" = "$third_image" ]

record_line="$(grep -nF 'record_rollback_candidate "$running_image"' "$SCRIPT_DIR/deploy-server.sh" | cut -d: -f1)"
pull_line="$(grep -nF 'compose pull server' "$SCRIPT_DIR/deploy-server.sh" | cut -d: -f1)"
[ -n "$record_line" ] && [ -n "$pull_line" ] && [ "$record_line" -lt "$pull_line" ] || {
	printf 'rollback image must be atomically recorded before replacement\n' >&2
	exit 1
}

grep -Fq 'server deployment remains blocked' "$SCRIPT_DIR/restore-mysql.sh"
grep -Fq 'exit 2' "$SCRIPT_DIR/restore-mysql.sh"
grep -Fq 'SPRING_AI_OPENAI_BASE_URL: "${SPRING_AI_OPENAI_BASE_URL:?set SPRING_AI_OPENAI_BASE_URL}"' \
	"$REPOSITORY_ROOT/infra/production/compose.yml"
grep -Fq 'proxy_pass http://127.0.0.1:8080;' "$REPOSITORY_ROOT/infra/production/nginx/nalq-api.conf.template"
grep -Fq 'existing/default VPC' "$REPOSITORY_ROOT/docs/plans/plan-production-deployment-infrastructure.md"
if grep -Fq 'NalQ 전용 VPC 하나' "$REPOSITORY_ROOT/docs/plans/plan-production-deployment-infrastructure.md"; then
	printf 'plan must not require a dedicated VPC\n' >&2
	exit 1
fi

printf 'production infrastructure safety checks passed\n'
