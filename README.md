# Passerelle

**Passerelle** est une solution auto-hébergée de partage sécurisé de fichiers et dossiers, conçue autour d'une architecture **lecture seule stricte** (`:ro`), d'un backend **Rust** haute performance et d'un client **Kotlin Multiplatform** (Application Android native + **Progressive Web App** `wasmJs` Material Design 3 Adaptive).

---

## Architecture : Domaine Unique en Sous-Dossiers (ou 2 Domaines / Racine)

Passerelle sépare strictement l'interface d'administration (PWA) du serveur d'API et des liens publics, et peut être hébergé **au choix** sur un **domaine unique dans deux sous-dossiers configurables** (ex : `https://partage.exemple.fr/passerelle` et `https://partage.exemple.fr/passerelle-api`) ou à la racine `/` de deux sous-domaines dédiés :

```text
                    ┌──────────────────────────────────────────┐
                    │       Reverse-Proxy TLS (Hôte)           │
                    │     Caddy / Traefik / Nginx              │
                    └──────┬───────────────────────────┬───────┘
                           │                           │
       https://partage.exemple.fr/passerelle   https://partage.exemple.fr/passerelle-api
       (ou https://app.domaine.fr/)           (ou https://s.domaine.fr/)
                           │                           │
                           ▼                           ▼
             ┌───────────────────────────┐   ┌────────────────────────────┐
             │      passerelle-web       │   │       passerelle-api       │
             │   Nginx 1.27 Alpine PWA   │   │   Backend Rust (Axum)      │
             │                           │   │                            │
             │ • Bundle Kotlin/Wasm      │   │ • API Admin /api/v1/*      │
             │ • ./config.json dynamique │   │ • Pages publiques /{slug}  │
             │ • Service Worker & icons  │   │ • Streaming Range & ZIP64  │
             └───────────────────────────┘   └──────────┬──────────┬──────┘
                                                        │          │
                                          lecture seule │          │ SQLite WAL
                                                  (:ro) │          │ + miniatures WebP
                                                        ▼          ▼
                                                     /data    /var/lib/passerelle
```

- **`/passerelle` (`passerelle-web`, configurable via `WEB_BASE_PATH`)** : héberge la PWA installable Kotlin/Wasm avec des chemins relatifs (`./manifest.webmanifest`, `./sw.js`, `./config.json`). Au démarrage du conteneur, `40-generate-config.sh` génère `config.json` (à partir de `API_BASE_URL`) et la configuration Nginx adaptée à `WEB_BASE_PATH`.
- **`/passerelle-api` (`passerelle-api`, configurable via `API_BASE_PATH`)** : expose l'API REST sécurisée (`POST /api/v1/auth/login`, `/api/v1/files/*`, `/api/v1/shares/*`), les pages publiques légères `GET /{slug}` ainsi que les téléchargements directs `GET /d/{slug}` sous le préfixe configuré. La sonde Docker locale `GET /health` reste toujours disponible à la racine.
- **Mode racine (`/`)** : laissez simplement `WEB_BASE_PATH=""` et `API_BASE_PATH=""` pour servir les deux services à la racine (ex. `https://app.domaine.fr` et `https://s.domaine.fr`).

---

## Fonctionnalités Clés

- **Sécurité Lecture Seule (`:ro`)** : le répertoire `/data` est monté en lecture seule stricte. Aucun fichier n'est jamais créé, renommé ou supprimé sur votre NAS/stockage, et chaque chemin est validé canoniquement (`PathGuard` avec `canonicalize` + `starts_with`) pour empêcher tout *path traversal*.
- **Streaming ZIP64 à la volée sans stockage temporaire** : les partages de dossiers sont diffusés directement en flux HTTP (`async_zip` en mode `Stored`) à mémoire constante, tout en ignorant les fichiers cachés NAS et en respectant la limite configurable `MAX_ZIP_SIZE_BYTES`.
- **Reprise de téléchargement (`Range` / `206 Partial Content`)** : les fichiers individuels supportent les requêtes par plage d'octets sans double décompte de quota lors des reprises (`bytes=N-` avec `N > 0`).
- **Contrôles d'accès complets sur les liens** :
  - Slug personnalisé ou aléatoire court (8 caractères) avec protection des routes réservées (`api`, `d`, `health`, `assets`, `robots.txt`, `favicon.ico`).
  - Date d'expiration UTC et quota maximal de téléchargements décrémenté atomiquement en SQLite.
  - Mot de passe optionnel hashé **Argon2id**, déverrouillable via formulaire HTML (`POST /{slug}/unlock` avec cookie `HttpOnly; SameSite=Lax` scopé au sous-dossier), `?password=...` ou `Authorization: Basic` (compatible `curl`/`wget`).
  - Révocation instantanée et génération de **QR Code SVG** vectoriel (`GET /api/v1/shares/{id}/qr`).
- **Pages publiques ultra-légères** : HTML/CSS Material 3 rendu côté serveur via Askama (`< 20 Ko`), thème clair/sombre automatique et balises OpenGraph (`og:title`, `og:description`, `og:type`).
- **Client Kotlin Multiplatform Material 3 Adaptive** :
  - **PWA Web (`wasmJs`)** installable (`manifest.webmanifest`, icônes `192x192`, `512x512` & `maskable`, Service Worker avec cache App Shell scopé au sous-dossier et excluant `config.json`, `/api/*` et `/d/*`).
  - **Android** natif avec Dynamic Color (Android 12+), Edge-to-Edge et Share Sheet système (`Intent.ACTION_SEND`).
  - Mise en page adaptative (`NavigationSuiteScaffold`, panneau maître-détail sur grands écrans).

---

## Déploiement Rapide (Docker Compose)

### 1. Préparer la configuration

```bash
cp deploy/.env.example deploy/.env
```

Éditez `deploy/.env` avec vos valeurs. Exemple sur un domaine unique (`partage.exemple.fr`) :

```dotenv
API_PORT=8080
WEB_PORT=3000
HOST_DATA_DIR=/chemin/absolu/vers/vos/fichiers

APP_SCHEME=https
APP_DOMAIN=partage.exemple.fr
WEB_BASE_PATH=/passerelle
API_BASE_PATH=/passerelle-api
PUBLIC_BASE_URL=${APP_SCHEME}://${APP_DOMAIN}${API_BASE_PATH}
ALLOWED_ORIGINS=${APP_SCHEME}://${APP_DOMAIN}

ADMIN_USERNAME=admin
ADMIN_PASSWORD=un-mot-de-passe-tres-robuste
JWT_SECRET=generez-avec-openssl-rand-hex-32
```

> [!TIP]
> Pour un déploiement à la racine `/` sur deux domaines distincts (`https://app.domaine.fr` et `https://s.domaine.fr`), configurez :
> ```dotenv
> WEB_BASE_PATH=
> API_BASE_PATH=
> PUBLIC_BASE_URL=https://s.domaine.fr
> ALLOWED_ORIGINS=https://app.domaine.fr,https://s.domaine.fr
> ```

> [!IMPORTANT]
> `JWT_SECRET` doit contenir au minimum **16 caractères** (utilisez par exemple `openssl rand -hex 32`), et `PUBLIC_BASE_URL` / `ALLOWED_ORIGINS` ne doivent pas comporter de slash final.

### 2. Construire et démarrer les conteneurs

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

Vérifier l'état de santé des deux services :

```bash
docker compose -f deploy/docker-compose.yml ps
curl -fsS http://127.0.0.1:8080/health
curl -fsS http://127.0.0.1:8080/passerelle-api/health
curl -fsS http://127.0.0.1:3000/healthz
curl -fsS http://127.0.0.1:3000/passerelle/config.json
```

---

## Configuration du Reverse-Proxy (TLS)

Les extraits ci-dessous routent `https://partage.exemple.fr/passerelle` vers `127.0.0.1:${WEB_PORT}` (`3000`) et `https://partage.exemple.fr/passerelle-api` vers `127.0.0.1:${API_PORT}` (`8080`) sans réécriture de chemin (les deux services gèrent nativement leur sous-dossier respectif).

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

### Option B — Nginx (Hôte)

```nginx
server {
    listen 443 ssl http2;
    server_name partage.exemple.fr;

    # Certificats TLS gérés par Certbot / ACME
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
        # Désactiver le buffering pour le streaming ZIP64 et gros fichiers
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

### Option C — Traefik (Labels Docker)

Ajoutez les labels Traefik sur un même routeur HTTPS (`Host(\`partage.exemple.fr\`)`) :
- Sur `passerelle-web` (`port 80`) : `traefik.http.routers.passerelle-web.rule=Host(\`partage.exemple.fr\`) && PathPrefix(\`/passerelle\`)`
- Sur `passerelle-api` (`port 8080`) : `traefik.http.routers.passerelle-api.rule=Host(\`partage.exemple.fr\`) && PathPrefix(\`/passerelle-api\`)`

*(En mode racine avec deux domaines distincts, utilisez `Host(\`app.domaine.fr\`)` sur `passerelle-web` et `Host(\`s.domaine.fr\`)` sur `passerelle-api`.)*

---

## Développement Local

### Backend Rust (`backend/`)

Prérequis : Rust `>= 1.85` (édition 2024).

```bash
cd backend
cargo fmt --all -- --check
cargo clippy --all-targets -- -D warnings
cargo test

# Lancer le serveur en local
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

### Client KMP Android & PWA (`client/`)

Prérequis : JDK 21 et Android SDK (`platforms;android-35`).

```bash
cd client
./gradlew testDebugUnitTest wasmJsBrowserTest --no-daemon
./gradlew :composeApp:assembleDebug --no-daemon
./gradlew :composeApp:wasmJsBrowserDistribution --no-daemon
```

Le bundle PWA de production est généré dans `client/composeApp/build/dist/wasmJs/productionExecutable/`.

### Tests du script d'entrypoint Nginx (`deploy/`)

```bash
./deploy/tests/test_generate_config.sh
```

---

## Architecture & Ingénierie

Passerelle applique strictement la **Clean Architecture** et le **TDD (Red → Green → Refactor)** définis dans [AGENTS.md](AGENTS.md) ainsi que le contrat canonique décrit dans [docs/API_CONTRACT.md](docs/API_CONTRACT.md) :

- **Backend (`backend/`)** :
  - `src/domain/` : entités, Value Objects (`Slug`, `RelativeFilePath`), erreurs typées `thiserror` et ports abstraits (zéro I/O).
  - `src/application/` : cas d'usage (`AuthUseCases`, `FileUseCases`, `ShareUseCases`) testables en mémoire.
  - `src/infrastructure/` : SQLite (`sqlx`), garde-fou disque lecture seule (`PathGuard`), streaming ZIP64 (`async_zip`), miniatures WebP, sécurité (`argon2`, `jsonwebtoken`), routeur `axum` & templates `askama`.
- **Client KMP (`client/`)** :
  - `:core:domain` ← `:core:data` / `:core:platform` / `:core:designsystem` ← `:feature:auth`, `:feature:files`, `:feature:shares` ← `:composeApp`.
  - Flux unidirectionnel strict MVI (`StateFlow<UiState>` + `SharedFlow<UiEvent>`).
