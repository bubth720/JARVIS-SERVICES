#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Keep inherited settings; only translate the managed HTTP proxy for JVM tools.
if [[ -n "${HTTPS_PROXY:-}" ]]; then
  read -r jarvis_proxy_host jarvis_proxy_port < <(python3 - <<'PYCODE'
import os, urllib.parse
u=urllib.parse.urlparse(os.environ['HTTPS_PROXY'])
if not u.hostname or u.username or u.password: raise SystemExit('Use JVM proxy settings configured by your environment')
print(u.hostname, u.port or 80)
PYCODE
)
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhttps.proxyHost=$jarvis_proxy_host -Dhttps.proxyPort=$jarvis_proxy_port -Dhttp.proxyHost=$jarvis_proxy_host -Dhttp.proxyPort=$jarvis_proxy_port"
  if [[ -r /etc/ssl/certs/java/cacerts && "${JAVA_TOOL_OPTIONS}" != *javax.net.ssl.trustStore=* ]]; then
    export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
  fi
fi
exec ./gradlew "$@"
