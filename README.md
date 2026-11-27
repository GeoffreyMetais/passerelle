# Passerelle

**Passerelle** est une solution auto-hébergée de partage sécurisé de fichiers et dossiers, conçue autour d'une architecture **lecture seule stricte** (`:ro`), d'un backend **Rust** haute performance et d'un client **Kotlin Multiplatform** (Application Android native + **Progressive Web App** `wasmJs` Material Design 3 Adaptive).

---

## Architecture à 2 Domaines

Passerelle sépare strictement l'interface d'administration (PWA) du serveur d'API et des liens publics courts afin de garder des URLs de partage courtes et d'isoler les surfaces exposées :

```text
                    ┌──────────────────────────────────────────┐
                    │       Reverse-Proxy TLS (Hôte)           │
                    │     Caddy / Traefik / Nginx              │
                    └──────┬───────────────────────────┬───────┘
                           │                           │
            https://app.domaine.fr              https://s.domaine.fr
                           │                           │
                           ▼                           ▼
             ┌───────────────────────────┐   ┌────────────────────────────┐
             │      passerelle-web       │   │       passerelle-api       │
             │   Nginx 1.27 Alpine PWA   │   │   Backend Rust (Axum)      │
             │                           │   │                            │
             │ • Bundle Kotlin/Wasm      │   │ • API Admin /api/v1/*      │
             │ • /config.json dynamique  │   │ • Pages publiques /{slug}  │
             │ • Service Worker & icons  │   │ • Streaming Range & ZIP64  │
             └───────────────────────────┘   └──────────┬──────────┬──────┘
                                                        │          │
                                          lecture seule │          │ SQLite WAL
                                                  (:ro) │          │ + miniatures WebP
                                                        ▼          ▼
                                                     /data    /var/lib/passerelle
```

- **`app.domaine.fr` (`passerelle-web`)** : héberge la PWA installable Kotlin/Wasm. Au démarrage, l'application lit `/config.json` (généré dynamiquement au lancement du conteneur à partir de `API_BASE_URL`) pour découvrir l'URL du backend.
- **`s.domaine.fr` (`passerelle-api`)** : expose l'API REST sécurisée (`POST /api/v1/auth/login`, `/api/v1/files/*`, `/api/v1/shares/*`), les pages publiques légères `GET /{slug}` ainsi que les téléchargements directs `GET /d/{slug}`.

---

## Fonctionnalités Clés

- **Sécurité Lecture Seule (`:ro`)** : le répertoire `/data` est monté en lecture seule stricte. Aucun fichier n'est jamais créé, renommé ou supprimé sur votre NAS/stockage, et chaque chemin est validé canoniquement (`PathGuard` avec `canonicalize` + `starts_with`) pour empêcher tout *path traversal*.
- **Streaming ZIP64 à la volée sans stockage temporaire** : les partages de dossiers sont diffusés directement en flux HTTP (`async_zip` en mode `Stored`) à mémoire constante, tout en ignorant les fichiers cachés NAS et en respectant la limite configurable `MAX_ZIP_SIZE_BYTES`.
- **Reprise de téléchargement (`Range` / `206 Partial Content`)** : les fichiers individuels supportent les requêtes par plage d'octets sans double décompte de quota lors des reprises (`bytes=N-` avec `N > 0`).
- **Contrôles d'accès complets sur les liens** :
  - Slug personnalisé ou aléatoire court (8 caractères) avec protection des routes réservées (`api`, `d`, `health`, `assets`, `robots.txt`, `favicon.ico`).
  - Date d'expiration UTC et quota maximal de téléchargements décrémenté atomiquement en SQLite.
  - Mot de passe optionnel hashé **Argon2id**, déverrouillable via formulaire HTML (`POST /{slug}/unlock` avec cookie `HttpOnly; SameSite=Lax`), `?password=...` ou `Authorization: Basic` (compatible `curl`/`wget`).
  - Révocation instantanée et génération de **QR Code SVG** vectoriel (`GET /api/v1/shares/{id}/qr`).
- **Pages publiques ultra-légères** : HTML/CSS Material 3 rendu côté serveur via Askama (`< 20 Ko`), thème clair/sombre automatique et balises OpenGraph (`og:title`, `og:description`, `og:type`).
- **Client Kotlin Multiplatform Material 3 Adaptive** :
  - **PWA Web (`wasmJs`)** installable (`manifest.webmanifest`, icônes `192x192`, `512x512` & `maskable`, Service Worker avec cache App Shell excluant `/config.json`, `/api/*` et `/d/*`).
  - **Android** natif avec Dynamic Color (Android 12+), Edge-to-Edge et Share Sheet système (`Intent.ACTION_SEND`).
  - Mise en page adaptative (`NavigationSuiteScaffold`, panneau maître-détail sur grands écrans).

---

## Déploiement Rapide (Docker Compose)

### 1. Préparer la configuration

```bash
cp deploy/.env.example deploy/.env
```

Éditez `deploy/.env` avec vos valeurs :

```dotenv
API_PORT=8080
WEB_PORT=3000
HOST_DATA_DIR=/chemin/absolu/vers/vos/fichiers
PUBLIC_BASE_URL=https://s.domaine.fr
ALLOWED_ORIGINS=https://app.domaine.fr,https://s.domaine.fr
ADMIN_USERNAME=admin
ADMIN_PASSWORD=un-mot-de-passe-tres-robuste
JWT_SECRET=generez-avec-openssl-rand-hex-32
```

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
curl -fsS http://127.0.0.1:3000/healthz
curl -fsS http://127.0.0.1:3000/config.json
```

---

## Configuration du Reverse-Proxy (TLS)

### Option A — Caddy (`Caddyfile`)

```caddyfile
app.domaine.fr {
    reverse_proxy 127.0.0.1:3000
}

s.domaine.fr {
    reverse_proxy 127.0.0.1:8080
}
```

### Option B — Nginx (Hôte)

```nginx
server {
    listen 443 ssl http2;
    server_name app.domaine.fr;

    # Certificats TLS gérés par Certbot / ACME
    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

server {
    listen 443 ssl http2;
    server_name s.domaine.fr;

    # Désactiver le buffering pour le streaming ZIP64 et gros fichiers
    proxy_buffering off;
    proxy_request_buffering off;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### Option C — Traefik (Labels Docker)

Ajoutez les labels Traefik sur `passerelle-web` (`Host(\`app.domaine.fr\`)`, port `80`) et `passerelle-api` (`Host(\`s.domaine.fr\`)`, port `8080`) avec votre `certresolver` TLS.

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
