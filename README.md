# Passerelle

**Passerelle** is a self-hosted secure file and folder sharing solution, built around a **strict read-only** (`:ro`) architecture, a high-performance **Rust** backend, and a **Kotlin Multiplatform** client (native Android app + **Progressive Web App** `wasmJs` Material Design 3 Adaptive).

---

## Architecture: Single Domain in Subdirectories (or 2 Domains / Root)

Passerelle strictly separates the admin interface (PWA) from the API server and the public links, and can be hosted **either** on a **single domain in two configurable subdirectories** (e.g. `https://partage.exemple.fr/passerelle` and `https://partage.exemple.fr/passerelle-api`) or at the `/` root of two dedicated subdomains:

```text
                    ┌─────────────────────────────┐
                    │    TLS Reverse Proxy (Host) │
                    │     Caddy / Traefik / Nginx │
                    └──────────────┬──────────────┘
                           │                           │
       https://partage.exemple.fr/passerelle   https://partage.exemple.fr/passerelle-api
       (or https://app.domaine.fr/)            (or https://s.domaine.fr/)
                           │                           │
                           ▼                           ▼
             ┌─────────────────────────┐   ┌─────────────────────────┐
             │      passerelle-web     │   │      passerelle-api     │
             │   Nginx 1.27 Alpine PWA │   │    Backend Rust (Axum)  │
             │                         │   │                         │
             │ • Kotlin/Wasm bundle    │   │ • Admin API /api/v1/*   │
             │ • dynamic ./config.json │   │ • Public pages /{slug}  │
             │ • Service Worker & icons│   │ • Range & ZIP64 stream  │
             └─────────────────────────┘   └────────────┬────────────┘
                                                        │          │
                                          read-only     │          │ SQLite WAL
                                          (:ro)          │          │ + WebP thumbnails
                                                        ▼          ▼
                                                     /data    /var/lib/passerelle
```

- **`/passerelle` (`passerelle-web`, configurable via `WEB_BASE_PATH`)**: hosts the installable Kotlin/Wasm PWA with relative paths (`./manifest.webmanifest`, `./sw.js`, `./config.json`). At container startup, `40-generate-config.sh` generates `config.json` (from `API_BASE_URL`) and the Nginx configuration adapted to `WEB_BASE_PATH`.
- **`/passerelle-api` (`passerelle-api`, configurable via `API_BASE_PATH`)**: exposes the secured REST API (`POST /api/v1/auth/login`, `/api/v1/files/*`, `/api/v1/shares/*`), the lightweight public pages `GET /{slug}` and the direct downloads `GET /d/{slug}` under the configured prefix. The local Docker health probe `GET /health` always remains available at the root.
- **Root mode (`/`)**: simply leave `WEB_BASE_PATH=""` and `API_BASE_PATH=""` to serve both services at the root (e.g. `https://app.domaine.fr` and `https://s.domaine.fr`).

---

## Key Features

- **Read-Only Security (`:ro`)**: the `/data` directory is mounted strictly read-only. No file is ever created, renamed, or deleted on your NAS/storage, and every path is canonically validated (`PathGuard` with `canonicalize` + `starts_with`) to prevent any *path traversal*.
- **On-the-fly ZIP64 streaming with no temporary storage**: folder shares are streamed directly over HTTP (`async_zip` in `Stored` mode) at constant memory usage, while skipping hidden NAS files and respecting the configurable `MAX_ZIP_SIZE_BYTES` limit.
- **Download resume (`Range` / `206 Partial Content`)**: individual files support byte-range requests without double-counting quota on resumes (`bytes=N-` with `N > 0`).
- **Full access control on links**:
  - Custom or short random slug (8 characters) with reserved-route protection (`api`, `d`, `health`, `assets`, `robots.txt`, `favicon.ico`).
  - UTC expiration date and maximum download quota decremented atomically in SQLite.
  - Optional **Argon2id**-hashed password, unlockable via HTML form (`POST /{slug}/unlock` with `HttpOnly; SameSite=Lax` cookie scoped to the subdirectory), `?password=...`, or `Authorization: Basic` (`curl`/`wget` compatible).
  - Instant revocation and vector **SVG QR Code** generation (`GET /api/v1/shares/{id}/qr`).
- **Ultra-light public pages**: Material 3 HTML/CSS rendered server-side via Askama (`< 20 KB`), automatic light/dark theme, and OpenGraph tags (`og:title`, `og:description`, `og:type`).
- **Kotlin Multiplatform Material 3 Adaptive client**:
  - **Web PWA (`wasmJs`)** installable (`manifest.webmanifest`, `192x192`, `512x512` & `maskable` icons, Service Worker with App Shell cache scoped to the subdirectory and excluding `config.json`, `/api/*` and `/d/*`).
  - **Native Android** with Dynamic Color (Android 12+), Edge-to-Edge, and the system Share Sheet (`Intent.ACTION_SEND`).
  - Adaptive layout (`NavigationSuiteScaffold`, master-detail pane on large screens).

---

## Quick Deployment (Docker Compose)

### 1. Prepare the configuration

```bash
cp deploy/.env.example deploy/.env
```

Edit `deploy/.env` with your values. Example on a single domain (`partage.exemple.fr`):

```dotenv
API_PORT=8080
WEB_PORT=3000
HOST_DATA_DIR=/absolute/path/to/your/files

APP_SCHEME=https
APP_DOMAIN=partage.exemple.fr
WEB_BASE_PATH=/passerelle
API_BASE_PATH=/passerelle-api
PUBLIC_BASE_URL=${APP_SCHEME}://${APP_DOMAIN}${API_BASE_PATH}
ALLOWED_ORIGINS=${APP_SCHEME}://${APP_DOMAIN}

ADMIN_USERNAME=admin
ADMIN_PASSWORD=a-very-strong-password
JWT_SECRET=generate-with-openssl-rand-hex-32
```

> [!TIP]
> For a root `/` deployment on two distinct domains (`https://app.domaine.fr` and `https://s.domaine.fr`), configure:
> ```dotenv
> WEB_BASE_PATH=
> API_BASE_PATH=
> PUBLIC_BASE_URL=https://s.domaine.fr
> ALLOWED_ORIGINS=https://app.domaine.fr,https://s.domaine.fr
> ```

> [!IMPORTANT]
> `JWT_SECRET` must contain at least **16 characters** (for example use `openssl rand -hex 32`), and `PUBLIC_BASE_URL` / `ALLOWED_ORIGINS` must not have a trailing slash.

> [!TIP]
> To change the administrator password once the backend is deployed, see [docs/ADMIN_PASSWORD.md](docs/ADMIN_PASSWORD.md).

### 2. Build and start the containers

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

Check the health of both services:

```bash
docker compose -f deploy/docker-compose.yml ps
curl -fsS http://127.0.0.1:8080/health
curl -fsS http://127.0.0.1:8080/passerelle-api/health
curl -fsS http://127.0.0.1:3000/healthz
curl -fsS http://127.0.0.1:3000/passerelle/config.json
```

---

## Reverse-Proxy Configuration (TLS)

The snippets below route `https://partage.exemple.fr/passerelle` to `127.0.0.1:${WEB_PORT}` (`3000`) and `https://partage.exemple.fr/passerelle-api` to `127.0.0.1:${API_PORT}` (`8080`) without path rewriting (both services natively handle their respective subdirectory).

### Option A — Caddy (`Caddyfile`)

```caddyfile
partage.exemple.fr {
    handle /passerelle {
        redir /passerelle/ permanent
    }
    handle /passerelle/* {
        reverse_proxy 127.0.0.1:3000
    }
    handle /passerelle-api* {
        reverse_proxy 127.0.0.1:8080 {
            flush_interval -1
        }
    }
}
```

### Option B — Nginx (Host)

```nginx
server {
    listen 443 ssl http2;
    server_name partage.exemple.fr;

    # TLS certificates managed by Certbot / ACME
    location = /passerelle {
        return 301 /passerelle/;
    }

    location /passerelle/ {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /passerelle-api/ {
        # Disable buffering for ZIP64 streaming and large files
        proxy_buffering off;
        proxy_request_buffering off;

        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### Option C — Traefik (Docker Labels)

Add the Traefik labels on a single HTTPS router (`Host(\`partage.exemple.fr\`)`):
- On `passerelle-web` (`port 80`): `traefik.http.routers.passerelle-web.rule=Host(\`partage.exemple.fr\`) && PathPrefix(\`/passerelle\`)`
- On `passerelle-api` (`port 8080`): `traefik.http.routers.passerelle-api.rule=Host(\`partage.exemple.fr\`) && PathPrefix(\`/passerelle-api\`)`

*(In root mode with two distinct domains, use `Host(\`app.domaine.fr\`)` on `passerelle-web` and `Host(\`s.domaine.fr\`)` on `passerelle-api`.)*

---

## Local Development

### Rust Backend (`backend/`)

Prerequisites: Rust `>= 1.85` (edition 2024).

```bash
cd backend
cargo fmt --all -- --check
cargo clippy --all-targets -- -D warnings
cargo test

# Run the server locally
mkdir -p /tmp/passerelle-data /tmp/passerelle-state
BIND_ADDR=127.0.0.1:8080 \
DATA_DIR=/tmp/passerelle-data \
STATE_DIR=/tmp/passerelle-state \
ADMIN_USERNAME=admin \
ADMIN_PASSWORD=dev-password-123 \
JWT_SECRET=dev-jwt-secret-minimum-16-chars \
PUBLIC_BASE_URL=http://localhost:8080 \
ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8080 \
cargo run
```

### KMP Android & PWA Client (`client/`)

Prerequisites: JDK 21 and Android SDK (`platforms;android-35`).

```bash
cd client
./gradlew testDebugUnitTest wasmJsBrowserTest --no-daemon
./gradlew :composeApp:assembleDebug --no-daemon
./gradlew :composeApp:wasmJsBrowserDistribution --no-daemon
```

The production PWA bundle is generated in `client/composeApp/build/dist/wasmJs/productionExecutable/`.

### Tests for the Nginx entrypoint script (`deploy/`)

```bash
./deploy/tests/test_generate_config.sh
```

---

## Architecture & Engineering

Passerelle strictly applies the **Clean Architecture** and **TDD (Red → Green → Refactor)** defined in [AGENTS.md](AGENTS.md) as well as the canonical contract described in [docs/API_CONTRACT.md](docs/API_CONTRACT.md):

- **Backend (`backend/`)**:
  - `src/domain/`: entities, Value Objects (`Slug`, `RelativeFilePath`), typed `thiserror` errors, and abstract ports (zero I/O).
  - `src/application/`: use cases (`AuthUseCases`, `FileUseCases`, `ShareUseCases`) testable in memory.
  - `src/infrastructure/`: SQLite (`sqlx`), read-only disk guard (`PathGuard`), ZIP64 streaming (`async_zip`), WebP thumbnails, security (`argon2`, `jsonwebtoken`), `axum` router & `askama` templates.
- **KMP Client (`client/`)**:
  - `:core:domain` ← `:core:data` / `:core:platform` / `:core:designsystem` ← `:feature:auth`, `:feature:files`, `:feature:shares` ← `:composeApp`.
  - Strict unidirectional MVI flow (`StateFlow<UiState>` + `SharedFlow<UiEvent>`).
