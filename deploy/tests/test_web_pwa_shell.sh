#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

INDEX_HTML="${REPO_ROOT}/client/composeApp/src/wasmJsMain/resources/index.html"
WEB_PLATFORM_KT="${REPO_ROOT}/client/core/platform/src/wasmJsMain/kotlin/fr/passerelle/client/core/platform/WebPlatform.kt"
SW_JS="${REPO_ROOT}/client/composeApp/src/wasmJsMain/resources/sw.js"

# 1. Regression: index.html must NOT declare a static <canvas id="ComposeTarget">
#    ComposeViewport(document.body) appends its own <canvas> to <body>; a 100%-height
#    static <canvas id="ComposeTarget"> pushes the real Compose canvas below the viewport
#    (clipped by body { overflow: hidden; }), resulting in a blank screen after splash.
if grep -qE '<canvas[^>]*id="ComposeTarget"' "${INDEX_HTML}"; then
    echo "FAIL: ${INDEX_HTML} contains a static <canvas id=\"ComposeTarget\"> that pushes ComposeViewport's canvas off-screen" >&2
    exit 1
fi

# 2. Regression: WebPlatform.kt must NOT call .await<String>() on a JS Promise<JsString>.
#    In Kotlin/Wasm, awaiting a JS string Promise as Kotlin String throws
#    `RuntimeError: illegal cast` at runtime; it must use .await<JsString>().toString().
if grep -qE '\.await<String>\(\)' "${WEB_PLATFORM_KT}"; then
    echo "FAIL: ${WEB_PLATFORM_KT} calls .await<String>() which throws RuntimeError: illegal cast in Kotlin/Wasm" >&2
    exit 1
fi

if ! grep -qE '\.await<JsString>\(\)\.toString\(\)' "${WEB_PLATFORM_KT}"; then
    echo "FAIL: ${WEB_PLATFORM_KT} must decode Response.text() via .await<JsString>().toString()" >&2
    exit 1
fi

# 3. Regression: Service Worker cache version must be bumped past v1 so clients
#    that cached the broken index.html evict the old cache on activation.
if grep -q "CACHE_NAME = 'passerelle-pwa-v1'" "${SW_JS}"; then
    echo "FAIL: ${SW_JS} still uses passerelle-pwa-v1; bump CACHE_NAME to invalidate stale shells" >&2
    exit 1
fi

echo "PASS: all web PWA shell non-regression checks succeeded"
