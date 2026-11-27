# Contrat d'API Canonique — Passerelle (`v1`)

Ce document fait foi pour l'intégration entre le conteneur **Rust API** (`backend/`, servi sur `https://s.domaine.fr`) et les clients **KMP PWA Web & Android** (`client/`).

Toutes les dates sont au format **ISO-8601 UTC** (`"2026-11-27T09:30:00Z"`). Tous les champs JSON utilisent le **`camelCase`**.

---

## 1. Configuration Runtime de la PWA (`GET /config.json` sur Nginx `app.domaine.fr`)

```json
{
  "apiBaseUrl": "http://localhost:8080"
}
```
- Sur **Web (`wasmJs`)** : l'application récupère `/config.json` au démarrage (avec repli sur `http://localhost:8080` en dev local) pour initialiser l'URL du serveur API.
- Sur **Android** : l'utilisateur saisit l'URL du serveur (`https://s.domaine.fr`) sur l'écran de connexion (persistée localement).

---

## 2. Format Normalisé des Erreurs JSON

Toute erreur d'API (`4xx` / `5xx`) renvoie :
```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Identifiants administrateur invalides."
}
```
Codes standards : `INVALID_CREDENTIALS`, `UNAUTHORIZED`, `TOKEN_EXPIRED`, `FORBIDDEN_PATH`, `NOT_FOUND`, `SLUG_ALREADY_EXISTS`, `RESERVED_SLUG`, `PAYLOAD_TOO_LARGE`, `SHARE_EXPIRED`, `SHARE_REVOKED`, `QUOTA_EXCEEDED`, `INVALID_PASSWORD`, `RATE_LIMITED`, `INTERNAL_ERROR`.

---

## 3. Santé & Authentification

### `GET /health` (Public)
Réponse `200 OK` :
```json
{
  "status": "ok",
  "version": "0.1.0"
}
```

### `POST /api/v1/auth/login` (Public, Rate-limited)
Requête :
```json
{
  "username": "admin",
  "password": "secret-password",
  "deviceName": "PWA Web"
}
```
Réponse `200 OK` :
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "rt_...",
  "expiresInSeconds": 900,
  "publicBaseUrl": "https://s.domaine.fr"
}
```

### `POST /api/v1/auth/refresh` (Public)
Requête :
```json
{
  "refreshToken": "rt_..."
}
```
Réponse `200 OK` : même structure que `/api/v1/auth/login`.

### `DELETE /api/v1/auth/logout` (Admin — Header `Authorization: Bearer <accessToken>`)
Réponse `204 No Content`.

---

## 4. Explorateur de Fichiers Administrateur (`Authorization: Bearer <accessToken>`)

### `GET /api/v1/files`
Query params :
- `path` (optionnel, défaut `""` = racine `/data`, ex: `"Photos/Vacances"`)
- `sortBy` (optionnel : `"name"` | `"size"` | `"modifiedAt"`, défaut `"name"`)
- `sortOrder` (optionnel : `"asc"` | `"desc"`, défaut `"asc"` ; les dossiers sont toujours groupés avant les fichiers)
- `showHidden` (optionnel : `true` | `false`, défaut `false`)

Réponse `200 OK` :
```json
{
  "currentPath": "Photos/Vacances",
  "parentPath": "Photos",
  "breadcrumbs": [
    { "name": "Racine", "path": "" },
    { "name": "Photos", "path": "Photos" },
    { "name": "Vacances", "path": "Photos/Vacances" }
  ],
  "entries": [
    {
      "name": "Japon",
      "path": "Photos/Vacances/Japon",
      "isDirectory": true,
      "sizeBytes": null,
      "modifiedAt": "2026-10-12T14:20:00Z",
      "mimeType": null,
      "hasThumbnail": false,
      "activeShareCount": 1
    },
    {
      "name": "plage.jpg",
      "path": "Photos/Vacances/plage.jpg",
      "isDirectory": false,
      "sizeBytes": 3450120,
      "modifiedAt": "2026-10-14T08:15:30Z",
      "mimeType": "image/jpeg",
      "hasThumbnail": true,
      "activeShareCount": 0
    }
  ]
}
```
*(Note : à la racine `currentPath = ""`, `parentPath = null` et `breadcrumbs = [{ "name": "Racine", "path": "" }]`.)*

### `GET /api/v1/files/search`
Query params :
- `q` (obligatoire, chaîne recherchée insensible à la casse, min 1 caractère)
- `path` (optionnel, sous-dossier de départ, défaut `""`)
- `showHidden` (optionnel, défaut `false`)

Réponse `200 OK` :
```json
{
  "query": "plage",
  "results": [
    {
      "name": "plage.jpg",
      "path": "Photos/Vacances/plage.jpg",
      "isDirectory": false,
      "sizeBytes": 3450120,
      "modifiedAt": "2026-10-14T08:15:30Z",
      "mimeType": "image/jpeg",
      "hasThumbnail": true,
      "activeShareCount": 0
    }
  ]
}
```

### `GET /api/v1/files/thumbnail?path=...`
- Retourne `200 OK` avec `Content-Type: image/webp` (miniature max 256x256) + `Cache-Control: private, max-age=86400` + `ETag`.
- Accepte l'authentification par header `Authorization: Bearer <token>` (supporté par Coil 3 via intercepteur Ktor).

### `GET /api/v1/files/download?path=...`
- Téléchargement direct du fichier par l'administrateur (`Content-Disposition: attachment` ou `inline` si `?inline=true`).

---

## 5. Gestion des Liens de Partage (`Authorization: Bearer <accessToken>`)

### Modèle JSON `ShareDto`
```json
{
  "id": "01936d8a-7c10-7000-8000-000000000001",
  "slug": "vacances-ete",
  "publicUrl": "https://s.domaine.fr/vacances-ete",
  "directDownloadUrl": "https://s.domaine.fr/d/vacances-ete",
  "targetName": "Vacances",
  "relativePath": "Photos/Vacances",
  "isDirectory": true,
  "hasPassword": true,
  "expiresAt": "2026-12-04T09:30:00Z",
  "maxDownloads": 10,
  "downloadCount": 2,
  "isRevoked": false,
  "isActive": true,
  "createdAt": "2026-11-27T09:30:00Z",
  "lastAccessedAt": "2026-11-27T10:00:00Z"
}
```
*(Où `isActive` vaut `true` ssi `!isRevoked && !expired && (maxDownloads == null || downloadCount < maxDownloads)`.)*

### `POST /api/v1/shares`
Requête :
```json
{
  "relativePath": "Photos/Vacances",
  "customSlug": "vacances-ete",
  "password": null,
  "expiresInSeconds": 604800,
  "maxDownloads": 10
}
```
- `customSlug` : `null` ou chaîne `[a-zA-Z0-9_-]{3,64}` hors mots réservés (`api`, `d`, `health`, `assets`, `robots.txt`, `favicon.ico`). Si `null`, génère un slug aléatoire court de 8 caractères alphanumériques.
- `expiresInSeconds` : `null` (permanent) ou entier positif (`> 0`).
- `maxDownloads` : `null` (illimité) ou entier positif (`> 0`).
- `password` : `null` ou chaîne non vide (hashée Argon2id).

Réponse `201 Created` : `ShareDto`.

### `GET /api/v1/shares`
Query params optionnels :
- `path` : filtre exact sur `relativePath` (utile dans le panneau d'inspection d'un fichier/dossier !)
- `activeOnly` : `true` | `false` (défaut `false`)

Réponse `200 OK` :
```json
{
  "shares": [ /* ShareDto[] */ ]
}
```

### `PATCH /api/v1/shares/{id}`
Requête (champs optionnels pour mise à jour partielle) :
```json
{
  "isRevoked": true,
  "expiresAt": "2026-12-31T23:59:59Z",
  "clearExpiration": false,
  "maxDownloads": 25,
  "clearMaxDownloads": false,
  "newPassword": null,
  "clearPassword": false
}
```
Réponse `200 OK` : `ShareDto`.

### `DELETE /api/v1/shares/{id}`
Réponse `204 No Content`.

### `GET /api/v1/shares/{id}/qr`
Réponse `200 OK` avec `Content-Type: image/svg+xml` (QR code vectoriel encodant `publicUrl`).

---

## 6. Routes Publiques Destinataires (`https://s.domaine.fr`)

1. **`GET /{slug}`** :
   - Rendu HTML/CSS Material 3 léger (`< 20 Ko`, thème clair/sombre auto via `@media (prefers-color-scheme: dark)`), balises OpenGraph (`og:title`, `og:description`, `og:type`).
   - Si le lien est expiré, révoqué ou quota atteint : affiche une page d'état claire (`410 Gone`).
   - Si le lien est protégé par mot de passe et non déverrouillé : affiche le formulaire de mot de passe (`POST /{slug}/unlock`).
   - Sinon : affiche le nom du fichier/dossier (avec mention `.zip` si dossier), la taille (pour un fichier ou taille totale calculée du dossier), l'échéance éventuelle et le bouton **Télécharger** pointant vers `/d/{slug}`.
2. **`POST /{slug}/unlock`** (`application/x-www-form-urlencoded` `password=...`, Rate-limited) :
   - Vérifie le hash Argon2id ; si valide, pose un cookie `HttpOnly; SameSite=Lax` signé valable 1 heure pour ce `slug` et redirige (`303 See Other`) vers `/{slug}`.
3. **`GET /d/{slug}`** :
   - Autorisé si pas de mot de passe, OU cookie de déverrouillage valide, OU `?password=...` / `Authorization: Basic` valide (pour `curl`/`wget`).
   - Incrémente `download_count` et met à jour `last_accessed_at` de manière atomique en SQLite (`UPDATE share_links SET download_count = download_count + 1 ... WHERE id = ? AND is_revoked = 0 AND (max_downloads IS NULL OR download_count < max_downloads)`).
   - **Si fichier** : sert le fichier avec support `Range` (`206 Partial Content` ; note : ne pas ré-incrémenter le quota sur les requêtes `Range` de continuation `bytes=N-` avec `N > 0` afin de ne pas pénaliser un gestionnaire de téléchargement ou lecteur média !).
   - **Si dossier** : génère en streaming continu (`Content-Type: application/zip`, `Content-Disposition: attachment; filename="<nom>.zip"`) une archive **ZIP64** en mode `Stored` (mémoire constante) contenant l'arborescence relative du dossier (en ignorant les fichiers cachés NAS).
