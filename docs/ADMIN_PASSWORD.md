# Changing the Admin Password After Deployment

The admin password is **not stored in the database**, and there is **no endpoint** to change it on the fly: the authentication routes are limited to `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, and `DELETE /api/v1/auth/logout` (`backend/src/infrastructure/http/mod.rs:21-23`).

At container startup, the `ADMIN_PASSWORD` environment variable is read (`backend/src/infrastructure/config.rs:76`), hashed with **Argon2id** in memory only (`backend/src/lib.rs:48`), then verified at each login (`backend/src/application/auth_use_cases.rs:64`).

---

## 1. Change procedure

1. Edit `deploy/.env` with the new value:

   ```dotenv
   ADMIN_USERNAME=admin
   ADMIN_PASSWORD=a-new-very-strong-password
   ```

   Generate a strong password if needed: `openssl rand -base64 32`.

2. **Recreate** the API container so it reloads the environment — a plain `docker compose restart` is not enough, environment variables are frozen at container creation:

   ```bash
   docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --force-recreate passerelle-api
   ```

3. Log back into the PWA with the new password, then check the service health:

   ```bash
   curl -fsS http://127.0.0.1:8080/passerelle-api/health
   ```

> The same procedure applies to `ADMIN_USERNAME` (also read from the environment at startup).

---

## 2. Invalidating existing sessions

Changing `ADMIN_PASSWORD` does **not** revoke already-open sessions:

- the access token expires after **15 minutes** (`ACCESS_TOKEN_TTL_SECONDS = 900`, `backend/src/application/auth_use_cases.rs:9`);
- the refresh token lasts **30 days** and is persisted in SQLite (`REFRESH_TOKEN_TTL_DAYS = 30`, `backend/src/application/auth_use_cases.rs:10`).

If the password has been compromised, also change `JWT_SECRET` in `deploy/.env` and recreate the container: every JWT signed with the old secret becomes immediately invalid.

---

## 3. Best practices

- `deploy/.env` is Git-ignored (`.gitignore`): never commit a secret. As an alternative, use [Docker secrets](https://docs.docker.com/engine/swarm/secrets/) instead of the `.env` file.
- Restrict access to `deploy/.env` on the host (`chmod 600 deploy/.env`).

---

## 4. Do not confuse with a share password

Each public link can have its own password, changeable **on the fly**, without a restart, via the admin API or the PWA:

```http
PATCH /api/v1/shares/{id}
Authorization: Bearer <access-token>
Content-Type: application/json

{ "newPassword": "new-password" }
```

- `{ "newPassword": "..." }`: sets or replaces the link password (Argon2id-hashed);
- `{ "clearPassword": true }`: removes the password protection.

See the details in [docs/API_CONTRACT.md](API_CONTRACT.md) (section `PATCH /api/v1/shares/{id}`).
