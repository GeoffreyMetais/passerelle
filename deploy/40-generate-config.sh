#!/bin/sh
set -eu

OUTPUT_PATH="${OUTPUT_PATH:-/usr/share/nginx/html/config.json}"
RAW_URL="${API_BASE_URL:-http://localhost:8080}"

# Trim leading/trailing whitespace
TRIMMED_URL="$(printf '%s' "$RAW_URL" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"

# Fall back to default if empty after trimming
if [ -z "$TRIMMED_URL" ]; then
  TRIMMED_URL="http://localhost:8080"
fi

# Strip trailing slashes
CLEAN_URL="$(printf '%s' "$TRIMMED_URL" | sed -e 's:/*$::')"
if [ -z "$CLEAN_URL" ]; then
  CLEAN_URL="http://localhost:8080"
fi

# Escape backslashes, double quotes, tabs, carriage returns, and newlines for valid JSON
ESCAPED_URL="$(printf '%s' "$CLEAN_URL" | awk '
BEGIN { ORS = "" }
{
  if (NR > 1) printf "\\n"
  gsub(/\\/, "\\\\")
  gsub(/"/, "\\\"")
  gsub(/\t/, "\\t")
  gsub(/\r/, "\\r")
  printf "%s", $0
}')"

mkdir -p "$(dirname "$OUTPUT_PATH")"
printf '{\n  "apiBaseUrl": "%s"\n}\n' "$ESCAPED_URL" > "$OUTPUT_PATH"
