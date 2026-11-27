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
env -u API_BASE_URL OUTPUT_PATH="${OUT_DEFAULT}" "${TARGET_SCRIPT}"
assert_json_api_url "${OUT_DEFAULT}" "http://localhost:8080"

# 2. Trailing slashes stripped
OUT_SLASH="${TMP_DIR}/slash.json"
API_BASE_URL="https://s.domaine.fr///" OUTPUT_PATH="${OUT_SLASH}" "${TARGET_SCRIPT}"
assert_json_api_url "${OUT_SLASH}" "https://s.domaine.fr"

# 3. Special JSON characters escaped properly (quotes, backslashes, control chars, whitespace trimmed)
OUT_SPECIAL="${TMP_DIR}/special.json"
API_BASE_URL='  https://s.domaine.fr/path?a="1"&b=\test  ' OUTPUT_PATH="${OUT_SPECIAL}" "${TARGET_SCRIPT}"
assert_json_api_url "${OUT_SPECIAL}" 'https://s.domaine.fr/path?a="1"&b=\test'

# 4. Empty API_BASE_URL falls back to default
OUT_EMPTY="${TMP_DIR}/empty.json"
API_BASE_URL="" OUTPUT_PATH="${OUT_EMPTY}" "${TARGET_SCRIPT}"
assert_json_api_url "${OUT_EMPTY}" "http://localhost:8080"

echo "PASS: all 40-generate-config.sh tests succeeded"
