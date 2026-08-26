#!/usr/bin/env bash

set -euo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

run_web() {
	printf '==> Verifying web\n'
	pnpm -C "$REPOSITORY_ROOT/web" verify
}

run_server_fast() {
	printf '==> Running server fast tests\n'
	"$REPOSITORY_ROOT/server/gradlew" -p "$REPOSITORY_ROOT/server" fastTest
}

run_server_integration() {
	printf '==> Running server integration tests\n'
	"$REPOSITORY_ROOT/server/gradlew" -p "$REPOSITORY_ROOT/server" integrationTest
}

usage() {
	cat <<'EOF'
Usage: ./scripts/verify.sh <command>

Commands:
  fast                Run web static verification and server tests without Testcontainers
  all                 Run web static verification and all server tests
  web                 Run web typecheck, lint, and build
  server-fast         Run server tests without Testcontainers
  server-integration  Run only Testcontainers integration tests
EOF
}

case "${1:-}" in
	fast)
		run_web
		run_server_fast
		;;
	all)
		run_web
		run_server_fast
		run_server_integration
		;;
	web)
		run_web
		;;
	server-fast)
		run_server_fast
		;;
	server-integration)
		run_server_integration
		;;
	*)
		usage >&2
		exit 2
		;;
esac
