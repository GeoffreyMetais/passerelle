#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
TARGET_SCRIPT="${DEPLOY_DIR}/40-generate-config.sh"

TMP_DIR="$(mktemp -d)"
cleanup() {
    rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

assert_json_api_url() {
    local json_file="$1"
    local expected="$2"
    python3 - "${json_file}" "${expected}" <<'PY'
import json
import sys

path, expected = sys.argv[1], sys.argv[2]
with open(path, "r", encoding="utf-8") as f:
    data = json.load(f)

assert isinstance(data, dict), f"JSON root must be an object, got {type(data)}"
assert list(data.keys()) == ["apiBaseUrl"], f"Unexpected keys: {list(data.keys())}"
actual = data["apiBaseUrl"]
assert actual == expected, f"Expected apiBaseUrl={expected!r}, got {actual!r}"
PY
}

if [[ ! -x "${TARGET_SCRIPT}" ]]; then
    echo "FAIL: ${TARGET_SCRIPT} does not exist or is not executable" >&2
    exit 1
fi

# 1. Default API_BASE_URL when unset
OUT_DEFAULT="${TMP_DIR}/default.json"
env -u API_BASE_URL OUTPUT_PATH="${OUT_DEFAULT}" NGINX_CONF_OUT="${TMP_DIR}/default.conf" "${TARGET_SCRIPT}"
assert_json_api_url "${OUT_DEFAULT}" "http://localhost:8080"

# 2. Trailing slashes stripped
OUT_SLASH="${TMP_DIR}/slash.json"
API_BASE_URL="https://s.domaine.fr///" OUTPUT_PATH="${OUT_SLASH}" NGINX_CONF_OUT="${TMP_DIR}/slash.conf" "${TARGET_SCRIPT}"
assert_json_api_url "${OUT_SLASH}" "https://s.domaine.fr"

# 3. Special JSON characters escaped properly (quotes, backslashes, control chars, whitespace trimmed)
OUT_SPECIAL="${TMP_DIR}/special.json"
API_BASE_URL='  https://s.domaine.fr/path?a="1"&b=\test  ' OUTPUT_PATH="${OUT_SPECIAL}" NGINX_CONF_OUT="${TMP_DIR}/special.conf" "${TARGET_SCRIPT}"
assert_json_api_url "${OUT_SPECIAL}" 'https://s.domaine.fr/path?a="1"&b=\test'

# 4. Empty API_BASE_URL falls back to default
OUT_EMPTY="${TMP_DIR}/empty.json"
API_BASE_URL="" OUTPUT_PATH="${OUT_EMPTY}" NGINX_CONF_OUT="${TMP_DIR}/empty.conf" "${TARGET_SCRIPT}"
assert_json_api_url "${OUT_EMPTY}" "http://localhost:8080"

# 5. Root mode nginx config (WEB_BASE_PATH="" or "/")
NGINX_ROOT="${TMP_DIR}/nginx-root.conf"
OUT_ROOT="${TMP_DIR}/root.json"
API_BASE_URL="https://s.domaine.fr" WEB_BASE_PATH="" OUTPUT_PATH="${OUT_ROOT}" NGINX_CONF_OUT="${NGINX_ROOT}" "${TARGET_SCRIPT}"
assert_json_api_url "${OUT_ROOT}" "https://s.domaine.fr"
if [[ ! -f "${NGINX_ROOT}" ]]; then
    echo "FAIL: expected ${NGINX_ROOT} to be generated" >&2
    exit 1
fi
grep -q "location = /healthz" "${NGINX_ROOT}"
grep -q "location = /config.json" "${NGINX_ROOT}"
grep -q "location / {" "${NGINX_ROOT}"
if grep -q "/passerelle" "${NGINX_ROOT}"; then
    echo "FAIL: root nginx config must not contain /passerelle" >&2
    exit 1
fi

# 6. Subpath mode nginx config (WEB_BASE_PATH="/passerelle" or "passerelle/")
NGINX_SUB="${TMP_DIR}/nginx-sub.conf"
OUT_SUB="${TMP_DIR}/sub.json"
API_BASE_URL="https://partage.exemple.fr/passerelle-api/" WEB_BASE_PATH="/passerelle/" OUTPUT_PATH="${OUT_SUB}" NGINX_CONF_OUT="${NGINX_SUB}" "${TARGET_SCRIPT}"
assert_json_api_url "${OUT_SUB}" "https://partage.exemple.fr/passerelle-api"
if [[ ! -f "${NGINX_SUB}" ]]; then
    echo "FAIL: expected ${NGINX_SUB} to be generated" >&2
    exit 1
fi
grep -q "location = /healthz" "${NGINX_SUB}"
grep -q "location = /passerelle {" "${NGINX_SUB}"
grep -q "return 301 /passerelle/;" "${NGINX_SUB}"
grep -q "location = /passerelle/config.json" "${NGINX_SUB}"
grep -q "location = /passerelle/sw.js" "${NGINX_SUB}"
grep -q "location = /passerelle/index.html" "${NGINX_SUB}"
grep -q "location = /passerelle/manifest.webmanifest" "${NGINX_SUB}"
grep -q "location /passerelle/ {" "${NGINX_SUB}"
grep -Fq 'location ~ ^/passerelle/(.+\.wasm)$ {' "${NGINX_SUB}"
grep -q "alias /usr/share/nginx/html/;" "${NGINX_SUB}"

echo "PASS: all 40-generate-config.sh tests succeeded"
