# AGENTS.md — Directives d'Ingénierie du Projet Passerelle

Ce document définit les règles d'architecture, de développement et de qualité obligatoires pour tous les agents et développeurs intervenant sur **Passerelle** (monorepo Backend Rust + Client KMP Android & PWA Web + Docker Compose).

---

## 1. Clean Architecture Stricte (Obligatoire)

Le projet DOIT respecter rigoureusement les principes de la **Clean Architecture** avec une règle de dépendance unidirectionnelle stricte orientée vers l'intérieur (`Domain` ← `Application / UseCases` ← `Infrastructure / Data & Presentation`).

### 1.1. Backend Rust (`backend/`)
- **`domain/` (Pur Rust, zéro dépendance I/O, HTTP ou SQL)** :
  - Contient les entités métier (`ShareLink`, `FileEntry`, `AdminSession`), les Value Objects auto-validants (`Slug`, `RelativeFilePath`), les erreurs métier typées (`thiserror`) et les **traits (ports)** abstraits (`ShareRepository`, `SessionRepository`, `FileSystemPort`, `PasswordHasher`, `TokenService`).
  - Aucune référence à `axum`, `sqlx` ou au système de fichiers réel n'est autorisée dans `domain/`.
- **`application/` (Cas d'usage / Orchestration métier)** :
  - Implémente les Use Cases (`AuthUseCases`, `FileUseCases`, `ShareUseCases`) en ne dépendant **que** de `domain/`.
  - Testable à 100 % en mémoire via des implémentations factices (Fakes) des ports du domaine.
- **`infrastructure/` (Adaptateurs techniques)** :
  - `persistence/` : implémentations SQLite (`sqlx`) des repositories du domaine.
  - `fs/` : accès lecture seule sécurisé (`PathGuard` avec `canonicalize` + `starts_with`), streaming ZIP64 sans stockage temporaire (`async_zip` en mode `Stored`), génération et cache disque des miniatures WebP.
  - `security/` : `argon2` et `jsonwebtoken`.
  - `http/` : handlers et routeurs `axum`, CORS strict, rate-limiting, rendu HTML/CSS Material 3 léger (`askama`) pour la page publique `/{slug}` et streaming `Range` / ZIP64 sur `/d/{slug}`.

### 1.2. Client Kotlin Multiplatform (`client/` — Android & PWA Web `wasmJs`)
- **`:core:domain` (Pur Kotlin `commonMain`, aucune dépendance Android/Web/Ktor)** :
  - Modèles métier immuables, hiérarchie d'erreurs typées `AppError`, wrapper `Resource<T>`, interfaces de repositories et Use Cases (`operator fun invoke(...)`).
- **`:core:data` (Implémentation de la couche Data)** :
  - DTOs `@Serializable` marqués `internal`, client HTTP `Ktor` avec authentification Bearer/Refresh, mappers explicites `DTO ↔ Domain`, implémentations `internal` des interfaces de `:core:domain`. Ne jamais exposer un DTO hors de `:core:data`.
- **`:core:platform` (Abstractions multiplateformes `expect` / `actual`)** :
  - Stockage de jetons, lecture de `/config.json` sur Web vs URL configurable sur Android, presse-papier, Share Sheet natif Android (`Intent.ACTION_SEND`).
- **`:core:designsystem` (Material Design 3 Adaptive)** :
  - Thème `PasserelleTheme` (Dynamic Color Android 12+ & palette tonale M3 Web clair/sombre), tokens d'espacement 8dp, typographie et composants réutilisables découplés du métier.
- **`:feature:auth`, `:feature:files`, `:feature:shares` (Couche Presentation MVI / UDF)** :
  - Flux de données unidirectionnel strict : `StateFlow<UiState>` (`data class` immuable) + `SharedFlow<UiEvent>` (`replay = 0`) pour les effets ponctuels + `ViewModel` JetBrains.
  - Les écrans Composables reçoivent des données primitives/modèles et des callbacks ; aucun Composable enfant ne dépend directement d'un `ViewModel`.
- **`:composeApp` (Point d'entrée Android & Progressive Web App `wasmJs`)** :
  - Assemblage DI `Koin`, navigation racine adaptative (`NavigationSuiteScaffold`), configuration Edge-to-Edge Android et ressources **PWA** (`manifest.webmanifest`, `sw.js`, icônes installables, splash screen M3).

---

## 2. Développement Piloté par les Tests — Mode TDD Obligatoire

Pour **toute nouvelle fonctionnalité, évolution ou correction de bug**, il est **obligatoire** de procéder en mode **TDD (Test-Driven Development)** selon le cycle strict **Red → Green → Refactor** :

1. **RED (Écrire le test d'abord)** :
   - Pour une **évolution / fonctionnalité** : écrire d'abord le(s) test(s) unitaire(s) ou d'intégration exprimant le comportement attendu et vérifier qu'ils échouent.
   - Pour une **correction de bug** : écrire impérativement un test de non-régression qui reproduit exactement le bug et échoue avant toute modification du code de production.
2. **GREEN (Implémentation minimale)** :
   - Écrire uniquement le code de production nécessaire pour faire passer les tests au vert sans casser la suite existante.
3. **REFACTOR (Amélioration structurelle)** :
   - Nettoyer la duplication, renforcer le typage, vérifier le respect de la Clean Architecture et passer les linters (`cargo clippy -- -D warnings`, `cargo fmt --check`, `./gradlew check`) en maintenant 100 % des tests au vert.

### Règles de test spécifiques
- **Pas de mocks fragiles côté KMP** : privilégier des **Fakes** implémentant les interfaces de `:core:domain` dans `commonTest` avec `kotlinx-coroutines-test` (`runTest`).
- **Tests Backend Rust** : tester unitairement les invariants de `domain/` et les Use Cases de `application/`, et couvrir les endpoints `axum` (sécurité anti path-traversal, streaming ZIP64, expiration, quotas, mots de passe) par des tests d'intégration dans `backend/tests/`.

---

## 3. Exigences Produit & Garde-fous Critiques

1. **Progressive Web App (PWA)** :
   - La cible Web `wasmJs` doit toujours rester une **PWA installable** valide (`manifest.webmanifest` complet, icônes `192x192`, `512x512` et `maskable`, Service Worker `sw.js` mettant en cache l'App Shell Wasm/JS tout en excluant strictement `/config.json` et `/api/*`).
2. **Sécurité Lecture Seule (`:ro`)** :
   - Le backend ne modifie, ne renomme ni ne supprime **jamais** aucun fichier dans `/data`. Tout chemin fourni par un client doit être validé canoniquement sous la racine `/data`.
3. **Zéro `unwrap()` / `expect()` en production Rust** :
   - Propager proprement les erreurs typées jusqu'à la couche HTTP qui les traduit en réponses JSON normalisées (sans fuite de chemins internes serveur).
4. **Conformité Material 3 & Accessibilité** :
   - Aucune couleur hexadécimale codée en dur dans les écrans Compose (utiliser `MaterialTheme.colorScheme`).
   - Support complet des écrans compacts (mobile Android) et étendus (PWA Desktop/Tablette : rail latéral, panneau maître-détail, focus clavier et curseurs de survol).

---

## 4. Skills de Référence à Consulter

Avant d'intervenir sur une brique technique, consulter via `lookup_skill` les skills dédiés installés dans l'environnement :
- **Backend Rust** : `rust-architect`
- **Architecture & Modules KMP** : `kmp-compose-multiplatform`, `kotlin-project-modularization`, `kotlin-navigation-compose-multiplatform`, `kotlin-ui-adaptive-resources`
- **UI Compose & Material 3 Adaptive** : `material-3`, `adaptive`, `compose-component-design`, `compose-state-and-effects`, `compose-focus-navigation`, `edge-to-edge`, `jetpack-compose-performance`

---

## 5. Contrat d'API Partagé

Toute modification d'endpoint ou de payload JSON entre `backend/` et `client/` doit maintenir à jour et respecter [docs/API_CONTRACT.md](file:///home/antigravity/GeoffreyMetais/passerelle/docs/API_CONTRACT.md).
