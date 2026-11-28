# Changer le mot de passe administrateur après déploiement

Le mot de passe administrateur **n'est pas stocké en base de données** et il n'existe **aucun endpoint** pour le modifier à chaud : les routes d'authentification se limitent à `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh` et `DELETE /api/v1/auth/logout` (`backend/src/infrastructure/http/mod.rs:21-23`).

Au démarrage du conteneur, la variable d'environnement `ADMIN_PASSWORD` est lue (`backend/src/infrastructure/config.rs:76`), hashée en **Argon2id** uniquement en mémoire (`backend/src/lib.rs:48`), puis vérifiée à chaque login (`backend/src/application/auth_use_cases.rs:64`).

---

## 1. Procédure de changement

1. Éditez `deploy/.env` avec la nouvelle valeur :

   ```dotenv
   ADMIN_USERNAME=admin
   ADMIN_PASSWORD=nouveau-mot-de-passe-tres-robuste
   ```

   Générez un mot de passe robuste si besoin : `openssl rand -base64 32`.

2. **Recréez** le conteneur API pour qu'il relise l'environnement — un simple `docker compose restart` ne suffit pas, les variables d'environnement sont figées à la création du conteneur :

   ```bash
   docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --force-recreate passerelle-api
   ```

3. Reconnectez-vous sur la PWA avec le nouveau mot de passe, puis vérifiez la santé du service :

   ```bash
   curl -fsS http://127.0.0.1:8080/passerelle-api/health
   ```

> La même procédure s'applique à `ADMIN_USERNAME` (également lu depuis l'environnement au démarrage).

---

## 2. Invalider les sessions existantes

Changer `ADMIN_PASSWORD` **n'invalide pas** les sessions déjà ouvertes :

- l'access token expire au bout de **15 minutes** (`ACCESS_TOKEN_TTL_SECONDS = 900`, `backend/src/application/auth_use_cases.rs:9`) ;
- le refresh token dure **30 jours** et est persisté en SQLite (`REFRESH_TOKEN_TTL_DAYS = 30`, `backend/src/application/auth_use_cases.rs:10`).

En cas de compromission du mot de passe, changez **aussi** `JWT_SECRET` dans `deploy/.env` et recréez le conteneur : tous les JWT signés avec l'ancien secret deviennent immédiatement invalides.

---

## 3. Bonnes pratiques

- `deploy/.env` est ignoré par Git (`.gitignore`) : ne commitez jamais de secret. En alternative, utilisez des [Docker secrets](https://docs.docker.com/engine/swarm/secrets/) plutôt que le fichier `.env`.
- Limitez l'accès à `deploy/.env` sur l'hôte (`chmod 600 deploy/.env`).

---

## 4. Ne pas confondre avec le mot de passe d'un partage

Chaque lien public peut avoir son propre mot de passe, modifiable **à chaud**, sans redémarrage, via l'API admin ou la PWA :

```http
PATCH /api/v1/shares/{id}
Authorization: Bearer <access-token>
Content-Type: application/json

{ "newPassword": "nouveau-mot-de-passe" }
```

- `{ "newPassword": "..." }` : définit ou remplace le mot de passe du lien (hashé Argon2id) ;
- `{ "clearPassword": true }` : supprime la protection par mot de passe.

Voir le détail dans [docs/API_CONTRACT.md](API_CONTRACT.md) (section `PATCH /api/v1/shares/{id}`).
