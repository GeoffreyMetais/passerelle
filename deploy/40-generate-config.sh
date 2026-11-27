#!/bin/sh
set -eu

OUTPUT_PATH="${OUTPUT_PATH:-/usr/share/nginx/html/config.json}"
NGINX_CONF_OUT="${NGINX_CONF_OUT:-/etc/nginx/conf.d/default.conf}"
RAW_URL="${API_BASE_URL:-http://localhost:8080}"
RAW_WEB_BASE_PATH="${WEB_BASE_PATH:-}"

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

# Normalize WEB_BASE_PATH: "" or "/" -> "", "passerelle" / "/passerelle/" -> "/passerelle"
TRIMMED_WEB_PATH="$(printf '%s' "$RAW_WEB_BASE_PATH" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e 's:^/*::' -e 's:/*$::')"
if [ -n "$TRIMMED_WEB_PATH" ]; then
  CLEAN_WEB_PATH="/$TRIMMED_WEB_PATH"
else
  CLEAN_WEB_PATH=""
fi

mkdir -p "$(dirname "$NGINX_CONF_OUT")"

if [ -z "$CLEAN_WEB_PATH" ]; then
  cat > "$NGINX_CONF_OUT" <<'EOF'
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    include /etc/nginx/mime.types;

    # Ensure WebAssembly and Web App Manifest MIME types are explicitly handled
    # without risking duplicate-extension warnings in /etc/nginx/mime.types.
    location ~* \.wasm$ {
        default_type application/wasm;
        add_header Cache-Control "public, max-age=31536000, immutable" always;
        try_files $uri =404;
    }

    location = /manifest.webmanifest {
        default_type application/manifest+json;
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        try_files $uri =404;
    }

    location ~* \.webmanifest$ {
        default_type application/manifest+json;
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        try_files $uri =404;
    }

    # Gzip compression for Wasm, JS, JSON, manifests, SVG, and CSS
    gzip on;
    gzip_vary on;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_min_length 256;
    gzip_types
        application/wasm
        application/javascript
        application/json
        application/manifest+json
        image/svg+xml
        text/css
        text/plain;

    # Security headers
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    # Health check endpoint
    location = /healthz {
        access_log off;
        default_type text/plain;
        return 200 "ok\n";
    }

    # Never cache runtime config, service worker, or SPA shell entrypoint
    location = /index.html {
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        try_files $uri =404;
    }

    location = /sw.js {
        default_type application/javascript;
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        try_files $uri =404;
    }

    location = /config.json {
        default_type application/json;
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        try_files $uri =404;
    }

    # Static PWA icons and Compose resources
    location /icons/ {
        add_header Cache-Control "public, max-age=604800" always;
        try_files $uri =404;
    }

    location /composeResources/ {
        add_header Cache-Control "public, max-age=604800" always;
        try_files $uri =404;
    }

    # SPA fallback routing
    location / {
        try_files $uri $uri/ /index.html;
    }
}
EOF
else
  cat > "$NGINX_CONF_OUT" <<EOF
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    include /etc/nginx/mime.types;

    # Gzip compression for Wasm, JS, JSON, manifests, SVG, and CSS
    gzip on;
    gzip_vary on;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_min_length 256;
    gzip_types
        application/wasm
        application/javascript
        application/json
        application/manifest+json
        image/svg+xml
        text/css
        text/plain;

    # Security headers
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    # Health check endpoint (always accessible at root)
    location = /healthz {
        access_log off;
        default_type text/plain;
        return 200 "ok\n";
    }

    # Redirect /prefix -> /prefix/ so relative ./config.json, ./sw.js, ./composeApp.js resolve properly
    location = ${CLEAN_WEB_PATH} {
        return 301 ${CLEAN_WEB_PATH}/;
    }

    # Never cache runtime config, service worker, manifest, or SPA shell entrypoint under subpath
    location = ${CLEAN_WEB_PATH}/config.json {
        default_type application/json;
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        alias /usr/share/nginx/html/config.json;
    }

    location = ${CLEAN_WEB_PATH}/sw.js {
        default_type application/javascript;
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        alias /usr/share/nginx/html/sw.js;
    }

    location = ${CLEAN_WEB_PATH}/index.html {
        default_type text/html;
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        alias /usr/share/nginx/html/index.html;
    }

    location = ${CLEAN_WEB_PATH}/manifest.webmanifest {
        default_type application/manifest+json;
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        alias /usr/share/nginx/html/manifest.webmanifest;
    }

    # Immutable cache for compiled Wasm binaries under subpath
    location ~ ^${CLEAN_WEB_PATH}/(.+\.wasm)$ {
        default_type application/wasm;
        add_header Cache-Control "public, max-age=31536000, immutable" always;
        alias /usr/share/nginx/html/\$1;
    }

    # Subpath static assets and SPA fallback
    location ${CLEAN_WEB_PATH}/ {
        alias /usr/share/nginx/html/;
        try_files \$uri \$uri/ ${CLEAN_WEB_PATH}/index.html;
    }

    # Fallback if an upstream reverse-proxy already stripped ${CLEAN_WEB_PATH}
    location ~* \.wasm$ {
        default_type application/wasm;
        add_header Cache-Control "public, max-age=31536000, immutable" always;
        try_files \$uri =404;
    }

    location = /manifest.webmanifest {
        default_type application/manifest+json;
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        try_files \$uri =404;
    }

    location = /index.html {
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        try_files \$uri =404;
    }

    location = /sw.js {
        default_type application/javascript;
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        try_files \$uri =404;
    }

    location = /config.json {
        default_type application/json;
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        try_files \$uri =404;
    }

    location /icons/ {
        add_header Cache-Control "public, max-age=604800" always;
        try_files \$uri =404;
    }

    location /composeResources/ {
        add_header Cache-Control "public, max-age=604800" always;
        try_files \$uri =404;
    }

    location / {
        try_files \$uri \$uri/ /index.html;
    }
}
EOF
fi
