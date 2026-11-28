# Canonical API Contract — Passerelle (`v1`)

This document is authoritative for the integration between the **Rust API** container (`backend/`, served at the root `https://s.domaine.fr` or under a configurable prefix `API_BASE_PATH`, e.g. `https://partage.exemple.fr/passerelle-api`) and the **KMP Web PWA & Android** clients (`client/`).

All dates use the **ISO-8601 UTC** format (`"2026-11-27T09:30:00Z"`). All JSON fields use **`camelCase`**.

---

## 1. PWA Runtime Configuration (`GET ./config.json` on Nginx)

```json
{
  "apiBaseUrl": "https://partage.exemple.fr/passerelle-api"
}
```
- On **Web (`wasmJs`)**: the app fetches `./config.json` (relative to its hosting subdirectory, e.g. `/passerelle/config.json` or `/config.json`) at startup (with a fallback to `http://localhost:8080` in local dev) to initialize the API server URL.
- On **Android**: the user enters the server URL (`https://partage.exemple.fr/passerelle-api` or `https://s.domaine.fr`) on the login screen (persisted locally).

---

## 2. Normalized JSON Error Format

Any API error (`4xx` / `5xx`) returns:
```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Identifiants administrateur invalides."
}
```
Standard codes: `INVALID_CREDENTIALS`, `UNAUTHORIZED`, `TOKEN_EXPIRED`, `FORBIDDEN_PATH`, `NOT_FOUND`, `SLUG_ALREADY_EXISTS`, `RESERVED_SLUG`, `PAYLOAD_TOO_LARGE`, `SHARE_EXPIRED`, `SHARE_REVOKED`, `QUOTA_EXCEEDED`, `INVALID_PASSWORD`, `RATE_LIMITED`, `INTERNAL_ERROR`.

> Note: `message` is the literal human-readable string returned by the server (currently in French); only `code` is part of the machine-readable contract.

---

## 3. Health & Authentication

> **Configurable prefix (`API_BASE_PATH`)**: when `API_BASE_PATH` is set (e.g. `/passerelle-api`), all the routes below (`/api/v1/*`, `/{slug}`, `/{slug}/unlock`, `/d/{slug}`, `/health`) are mounted under this prefix. `GET /health` also remains accessible at the root `/` for local Docker probes.

### `GET /health` (Public)
`200 OK` response:
```json
{
  "status": "ok",
  "version": "0.1.0"
}
```

### `POST /api/v1/auth/login` (Public, Rate-limited)
Request:
```json
{
  "username": "admin",
  "password": "secret-password",
  "deviceName": "PWA Web"
}
```
`200 OK` response:
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "rt_...",
  "expiresInSeconds": 900,
  "publicBaseUrl": "https://s.domaine.fr"
}
```

### `POST /api/v1/auth/refresh` (Public)
Request:
```json
{
  "refreshToken": "rt_..."
}
```
`200 OK` response: same structure as `/api/v1/auth/login`.

### `DELETE /api/v1/auth/logout` (Admin — Header `Authorization: Bearer <accessToken>`)
`204 No Content` response.

---

## 4. Admin File Explorer (`Authorization: Bearer <accessToken>`)

### `GET /api/v1/files`
Query params:
- `path` (optional, default `""` = `/data` root, e.g. `"Photos/Holidays"`)
- `sortBy` (optional: `"name"` | `"size"` | `"modifiedAt"`, default `"name"`)
- `sortOrder` (optional: `"asc"` | `"desc"`, default `"asc"`; directories are always grouped before files)
- `showHidden` (optional: `true` | `false`, default `false`)

`200 OK` response:
```json
{
  "currentPath": "Photos/Holidays",
  "parentPath": "Photos",
  "breadcrumbs": [
    { "name": "Racine", "path": "" },
    { "name": "Photos", "path": "Photos" },
    { "name": "Holidays", "path": "Photos/Holidays" }
  ],
  "entries": [
    {
      "name": "Japan",
      "path": "Photos/Holidays/Japan",
      "isDirectory": true,
      "sizeBytes": null,
      "modifiedAt": "2026-10-12T14:20:00Z",
      "mimeType": null,
      "hasThumbnail": false,
      "activeShareCount": 1
    },
    {
      "name": "beach.jpg",
      "path": "Photos/Holidays/beach.jpg",
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
*(Note: at the root, `currentPath = ""`, `parentPath = null` and `breadcrumbs = [{ "name": "Racine", "path": "" }]` — the root breadcrumb name `"Racine"` is a literal server-side label.)*

### `GET /api/v1/files/search`
Query params:
- `q` (required, case-insensitive search string, min 1 character)
- `path` (optional, starting subdirectory, default `""`)
- `showHidden` (optional, default `false`)

`200 OK` response:
```json
{
  "query": "beach",
  "results": [
    {
      "name": "beach.jpg",
      "path": "Photos/Holidays/beach.jpg",
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
- Returns `200 OK` with `Content-Type: image/webp` (thumbnail max 256x256) + `Cache-Control: private, max-age=86400` + `ETag`.
- Accepts `Authorization: Bearer <token>` header authentication (supported by Coil 3 via a Ktor interceptor).

### `GET /api/v1/files/download?path=...`
- Direct file download for the administrator (`Content-Disposition: attachment`, or `inline` if `?inline=true`).

---

## 5. Share Link Management (`Authorization: Bearer <accessToken>`)

### `ShareDto` JSON Model
```json
{
  "id": "01936d8a-7c10-7000-8000-000000000001",
  "slug": "summer-holidays",
  "publicUrl": "https://s.domaine.fr/summer-holidays",
  "directDownloadUrl": "https://s.domaine.fr/d/summer-holidays",
  "targetName": "Holidays",
  "relativePath": "Photos/Holidays",
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
*(Where `isActive` is `true` iff `!isRevoked && !expired && (maxDownloads == null || downloadCount < maxDownloads)`.)*

### `POST /api/v1/shares`
Request:
```json
{
  "relativePath": "Photos/Holidays",
  "customSlug": "summer-holidays",
  "password": null,
  "expiresInSeconds": 604800,
  "maxDownloads": 10
}
```
- `customSlug`: `null` or a string `[a-zA-Z0-9_-]{3,64}` excluding reserved words (`api`, `d`, `health`, `assets`, `robots.txt`, `favicon.ico`). If `null`, generates a short random slug of 8 alphanumeric characters.
- `expiresInSeconds`: `null` (permanent) or a positive integer (`> 0`).
- `maxDownloads`: `null` (unlimited) or a positive integer (`> 0`).
- `password`: `null` or a non-empty string (Argon2id-hashed).

`201 Created` response: `ShareDto`.

### `GET /api/v1/shares`
Optional query params:
- `path`: exact filter on `relativePath` (useful in the inspection panel of a file/folder!)
- `activeOnly`: `true` | `false` (default `false`)

`200 OK` response:
```json
{
  "shares": [ /* ShareDto[] */ ]
}
```

### `PATCH /api/v1/shares/{id}`
Request (optional fields for partial update):
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
`200 OK` response: `ShareDto`.

### `DELETE /api/v1/shares/{id}`
`204 No Content` response.

### `GET /api/v1/shares/{id}/qr`
`200 OK` response with `Content-Type: image/svg+xml` (vector QR code encoding `publicUrl`).

---

## 6. Public Recipient Routes (`https://s.domaine.fr`)

1. **`GET /{slug}`**:
   - Renders lightweight Material 3 HTML/CSS (`< 20 KB`, automatic light/dark theme via `@media (prefers-color-scheme: dark)`), OpenGraph tags (`og:title`, `og:description`, `og:type`).
   - If the link is expired, revoked, or its quota is reached: displays a clear status page (`410 Gone`).
   - If the link is password-protected and not unlocked: displays the password form (`POST /{slug}/unlock`).
   - Otherwise: displays the file/folder name (with a `.zip` suffix for a folder), the size (for a file, or the computed total size of the folder), any expiration date, and the **Download** button pointing to `/d/{slug}`.
2. **`POST /{slug}/unlock`** (`application/x-www-form-urlencoded` `password=...`, Rate-limited):
   - Verifies the Argon2id hash; if valid, sets a signed `HttpOnly; SameSite=Lax` cookie valid for 1 hour for this `slug` and redirects (`303 See Other`) to `/{slug}`.
3. **`GET /d/{slug}`**:
   - Allowed if there is no password, OR a valid unlock cookie, OR a valid `?password=...` / `Authorization: Basic` (for `curl`/`wget`).
   - Increments `download_count` and updates `last_accessed_at` atomically in SQLite (`UPDATE share_links SET download_count = download_count + 1 ... WHERE id = ? AND is_revoked = 0 AND (max_downloads IS NULL OR download_count < max_downloads)`).
   - **If file**: serves the file with `Range` support (`206 Partial Content`; note: do not re-increment the quota on continuation `Range` requests `bytes=N-` with `N > 0`, so as not to penalize a download manager or media player!).
   - **If folder**: generates a continuous stream (`Content-Type: application/zip`, `Content-Disposition: attachment; filename="<name>.zip"`) of a **ZIP64** archive in `Stored` mode (constant memory) containing the folder's relative tree (skipping hidden NAS files).
