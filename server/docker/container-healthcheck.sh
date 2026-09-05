#!/usr/bin/env sh

set -eu

# The existing application SecurityFilterChain protects every non-auth endpoint.
# The unsupported GET must reach the existing route and return 405. This proves
# that the embedded HTTP server and mapping are responding without changing
# authentication policy. MySQL and Redis readiness are checked independently.
status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
	--max-time 3 http://127.0.0.1:8080/api/v1/auth/web/sessions/refresh || true)"

case "$status" in
	405) exit 0 ;;
	*) exit 1 ;;
esac
