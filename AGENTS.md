# AGENTS.md — Passerelle Engineering Guidelines

This document defines the mandatory architecture, development, and quality rules for all agents and developers working on **Passerelle** (Rust Backend + Kotlin Multiplatform Android & Web PWA Client + Docker Compose monorepo).

---

## 1. Core Engineering Principles

1. **English Only**:
   - Use English only for all code comments and documentation.
2. **Think Before Coding**:
   - State assumptions explicitly before implementing. If requirements are unclear or ambiguous, stop and ask.
3. **Simplicity First**:
   - Write the minimum code that solves the problem. Avoid speculative abstractions or unnecessary complexity.
4. **Surgical Changes**:
   - Every changed line must trace directly to the user's request. Do not touch unrelated code.
5. **Goal-Driven Execution**:
   - Turn every task into a concrete, runnable check (test, build, linter) and loop until it passes.

---

## 2. Strict Clean Architecture & Single Responsibility (Mandatory)

The project MUST strictly follow **Clean Architecture** principles with a unidirectional dependency rule pointing inward (`Domain` ← `Application / UseCases` ← `Infrastructure / Data & Presentation`) and a strong emphasis on the **Single Responsibility Principle (SRP)**—every module, class, function, and handler should have one clear, well-scoped responsibility.

### 2.1. Rust Backend (`backend/`)
- **`domain/` (Pure Rust, zero I/O, HTTP, or SQL dependencies)**:
  - Contains domain entities (`ShareLink`, `FileEntry`, `AdminSession`), self-validating Value Objects (`Slug`, `RelativeFilePath`), typed domain errors (`thiserror`), and abstract **traits (ports)** (`ShareRepository`, `SessionRepository`, `FileSystemPort`, `PasswordHasher`, `TokenService`).
  - No references to `axum`, `sqlx`, or the real filesystem are allowed in `domain/`.
- **`application/` (Use Cases / Business Orchestration)**:
  - Implements Use Cases (`AuthUseCases`, `FileUseCases`, `ShareUseCases`) depending **only** on `domain/`.
  - 100% testable in memory using fake implementations of domain ports.
- **`infrastructure/` (Technical Adapters)**:
  - `persistence/`: SQLite (`sqlx`) implementations of domain repositories.
  - `fs/`: read-only secured access (`PathGuard` with `canonicalize` + `starts_with`), zero-temp-storage ZIP64 streaming (`async_zip` in `Stored` mode), and WebP thumbnail generation/disk caching.
  - `security/`: `argon2` and `jsonwebtoken`.
  - `http/`: `axum` handlers and routers, strict CORS, rate-limiting, lightweight Material 3 HTML/CSS rendering (`askama`) for the public `/{slug}` page, and `Range` / ZIP64 streaming on `/d/{slug}`.

### 2.2. Kotlin Multiplatform Client (`client/` — Android & Web PWA `wasmJs`)
- **`:core:domain` (Pure Kotlin `commonMain`, no Android/Web/Ktor dependencies)**:
  - Immutable domain models, typed `AppError` hierarchy, `Resource<T>` wrapper, repository interfaces, and focused single-responsibility Use Cases (`operator fun invoke(...)`).
- **`:core:data` (Data Layer Implementation)**:
  - `internal` `@Serializable` DTOs, `Ktor` HTTP client with Bearer/Refresh authentication, explicit `DTO ↔ Domain` mappers, and `internal` implementations of `:core:domain` interfaces. Never expose a DTO outside `:core:data`.
- **`:core:platform` (Multiplatform `expect` / `actual` Abstractions)**:
  - Token storage, `/config.json` reading on Web vs. configurable URL on Android, clipboard, and native Android Share Sheet (`Intent.ACTION_SEND`).
- **`:core:designsystem` (Material Design 3 Adaptive)**:
  - `PasserelleTheme` (Android 12+ Dynamic Color & Web M3 light/dark tonal palette), 8dp spacing tokens, typography, and reusable domain-agnostic components.
- **`:feature:auth`, `:feature:files`, `:feature:shares` (MVI / UDF Presentation Layer)**:
  - Strict unidirectional data flow: `StateFlow<UiState>` (immutable `data class`) + `SharedFlow<UiEvent>` (`replay = 0`) for one-off effects + JetBrains `ViewModel`.
  - Composable screens receive primitive/model data and callbacks; no child Composable depends directly on a `ViewModel`.
- **`:composeApp` (Android & Progressive Web App `wasmJs` Entry Point)**:
  - `Koin` DI wiring, adaptive root navigation (`NavigationSuiteScaffold`), Android Edge-to-Edge setup, and **PWA** resources (`manifest.webmanifest`, `sw.js`, installable icons, M3 splash screen).

---

## 3. Testing Strategy (TDD & Meaningful Tests)

- **Minimum tests, focus on a few meaningful tests**:
  - Avoid redundant or trivial tests. Write concise, high-value tests that verify core invariants, security boundaries, and critical behaviors.
- **TDD Cycle (Red → Green → Refactor)**:
  1. **RED**: Write a failing test expressing the expected behavior (or reproducing a bug before fixing it).
  2. **GREEN**: Write the minimum production code needed to make the test pass.
  3. **REFACTOR**: Clean up duplication, enforce SRP and Clean Architecture boundaries, and run linters/checks (`cargo clippy -- -D warnings`, `cargo fmt --check`, `./gradlew check`) while keeping all tests green.

### Specific Test Rules
- **No fragile mocks in KMP**: Prefer **Fakes** implementing `:core:domain` interfaces in `commonTest` with `kotlinx-coroutines-test` (`runTest`).
- **Rust Backend Tests**: Unit test `domain/` invariants and `application/` Use Cases, and cover `axum` endpoints (anti-path-traversal security, ZIP64 streaming, expiration, quotas, passwords) with integration tests in `backend/tests/`.

---

## 4. Product Requirements & Critical Guardrails

1. **Progressive Web App (PWA)**:
   - The `wasmJs` web target must always remain a valid installable **PWA** (complete `manifest.webmanifest`, `192x192`, `512x512`, and `maskable` icons, Service Worker `sw.js` caching the Wasm/JS App Shell while strictly excluding `/config.json` and `/api/*`).
2. **Read-Only Security (`:ro`)**:
   - The backend must **never** modify, rename, or delete any file in `/data`. Every client-provided path must be canonically validated under the `/data` root.
3. **Zero `unwrap()` / `expect()` in Production Rust**:
   - Propagate typed errors cleanly to the HTTP layer, which translates them into normalized JSON responses (without leaking internal server paths).
4. **Material 3 Compliance & Accessibility**:
   - No hardcoded hex colors in Compose screens (use `MaterialTheme.colorScheme`).
   - Full support for compact screens (Android mobile) and expanded screens (Desktop/Tablet PWA: navigation rail, master-detail pane, keyboard focus, and hover cursors).

---

## 5. Reference Skills

Before working on a technical area, consult the dedicated skills installed in the environment via `lookup_skill`:
- **Rust Backend**: `rust-architect`
- **KMP Architecture & Modules**: `kmp-compose-multiplatform`, `kotlin-project-modularization`, `kotlin-navigation-compose-multiplatform`, `kotlin-ui-adaptive-resources`
- **Compose UI & Material 3 Adaptive**: `material-3`, `adaptive`, `compose-component-design`, `compose-state-and-effects`, `compose-focus-navigation`, `edge-to-edge`, `jetpack-compose-performance`

---

## 6. Shared API Contract

Any endpoint or JSON payload change between `backend/` and `client/` must update and adhere to [docs/API_CONTRACT.md](file:///home/antigravity/GeoffreyMetais/passerelle/docs/API_CONTRACT.md).
