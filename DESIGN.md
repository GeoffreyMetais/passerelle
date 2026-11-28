# Product Requirements Document (PRD) — Passerelle

**Project:** Passerelle (Sovereign Secure Transfer & Sharing)  
**Version:** 1.0.0  
**Status:** Approved / In design & prototyping phase  
**Date:** November 2026  
**Authors:** Passerelle Product & Security Architecture Team  

---

## 1. Overview & Product Vision

### 1.1 Context & Problem Statement
Transferring sensitive files within companies or between individuals today relies almost exclusively on centralized SaaS solutions or third-party cloud servers (WeTransfer, Google Drive, OneDrive, SwissTransfer, etc.). These intermediaries pose major risks:
- Breach of secrecy and confidentiality through non-sovereign transit and storage on third-party infrastructures.
- Exposure to extraterritorial laws (e.g. Cloud Act).
- Encryption keys often held or decryptable by the service provider.
- Lack of real-time control over the lifespan, revocation, and auditability of generated links.

### 1.2 The Passerelle Vision
**Passerelle** is a modern, self-hosted, zero-knowledge solution for exposing, sharing, and distributing large files and folders directly from your own local instance (NAS, private server, Docker container, workstation), with end-to-end encryption (E2EE) and without depending on any third-party cloud.

---

## 2. Strategic Goals & Success Metrics (KPIs)

| Goal | Metric / Target KPI |
| :--- | :--- |
| **Sovereignty & Zero-Trace** | 100% of decryption performed client-side (browser / WebAssembly). No plaintext key ever received by the server. |
| **Ease of use (UX)** | Fewer than 3 clicks and under 5 seconds to create a share-ready encrypted link with QR code. |
| **Transfer performance** | Maximum throughput on a direct local connection or P2P (> 50 MB/s on LAN / direct tunnel) with instant SHA-256 hash verification. |
| **Multi-device adoption** | Smooth, responsive recipient view on desktop and mobile with no account creation required. |

---

## 3. User Personas

### Persona 1: The Host / Administrator ("Alexandre", DevOps Engineer & CISO)
- **Needs:** Share heavy archives (`.tar.gz`, `.iso`, Linux builds, confidential documentation) with collaborators or third parties without going through a mainstream SaaS service.
- **Expectations:** Precise control over download quotas, 1-click immediate revocation, configurable expiration, password protection, and a transparent local audit log.

### Persona 2: The Recipient ("Camille", Partner / Customer)
- **Needs:** Quickly retrieve a secure file on computer or smartphone, frictionless and without creating an account.
- **Expectations:** Reassuring interface, clear security indicators (Zero-Knowledge, E2E), QR code flash for direct mobile transfer, transparent download bar with cryptographic steps.

---

## 4. User Journeys & Information Architecture

```
[LOCAL PASSERELLE INSTANCE]
│
├─ 1. File Explorer (List View / Grid View)
│   ├─ Tree navigation (Folders, Files, Metadata)
│   ├─ Contextual actions (Share, Download, Delete)
│   └─ System status bar (NVMe/SSD storage, active node, active links)
│
├─ 2. Share Drawer & Link Creation (Side Drawer / Mobile Bottom Sheet)
│   ├─ Expiration settings (1h, 24h, 7 days, 30 days, Permanent)
│   ├─ Download quota (Single, 5, 10, Unlimited)
│   ├─ Security (Password / AES-256 key, custom alias)
│   └─ Instant generation → secure short URL + dynamic QR Code
│
├─ 3. Shared Links Manager (Links Dashboard)
│   ├─ Tracking of active and expired links
│   ├─ Telemetry: download counter, timestamp, status
│   └─ Actions: quick rule editing, QR Code display, immediate revocation
│
└─ 4. Public Recipient Interface (No authentication required)
    ├─ Secure landing screen (file metadata, E2E status, SHA-256 fingerprint)
    ├─ Password / local decryption key entry
    ├─ Secure visual preview of the document
    └─ Progress screen (cryptographic pipeline: ECDH, WASM chunks, AES-GCM, SHA-256)
```

---

## 5. Detailed Functional Specifications

### 5.1 File Explorer (Desktop & Mobile)
- **Switchable views:** detailed tabular List view (size, modification date, status badge) and Grid view with file card previews.
- **Filters and search:** instant search field (`Ctrl+K` / `⌘K`), type filters (`All`, `Folders`, `Files & Media`), alphabetical or date sorting.
- **System telemetry:** available disk space indicator (`128 GB free of 256 GB`) and instance status (`:RO Read-Only` or active).

### 5.2 Share Engine & Link Generation
- **1-click quick creation:** generation with default profiles (7 days, unlimited access with optional password).
- **Fine-grained access rules:**
  - Automatic time-based expiration with countdown.
  - Strict download quota (e.g. destruction after a single download).
  - Strengthened protection via client-side passphrase / key.
  - Customizable URL alias (e.g. `/s/summer-holidays`, `/s/carmen-project`).
- **Multi-channel distribution:**
  - Instant copy of the public URL (`http://localhost:8080/s/...`).
  - High-resolution vector QR Code with a Passerelle glyph for direct mobile scanning.
  - Native share shortcuts (email, OS sharing).

### 5.3 Link Tracking & Lifecycle (Dashboard)
- Filter by state: `All`, `Active`, `Expired / Revoked`.
- In-flight editing: change the expiration date or the password without breaking the URL link.
- 1-click destructive revocation with immediate route invalidation on the host server.

### 5.4 Recipient Download Experience (Desktop & Mobile)
- **Minimalist, frictionless shell:** zero admin menu, zero sign-up, 100% focus on the document to receive.
- **Browser-side decryption:** entry of the password that decrypts the key in RAM, never transmitted to the server.
- **Document preview:** PDF/image preview in a secure WebAssembly vector container.
- **Step-by-step cryptographic stream visualizer:**
  1. *ECDH key exchange & handshake* (Curve25519)
  2. *Encrypted chunk reception* (WebAssembly chunks)
  3. *Local AES-256-GCM decryption* (Web Workers in RAM)
  4. *SHA-256 integrity check* (original hash verification)
  5. *Assembly & local save* (browser File API)

---

## 6. Technical Specifications & Architecture

### 6.1 Recommended Tech Stack
- **Frontend:** HTML5, Tailwind CSS / Passerelle Core Design System, TypeScript, WebAssembly (WASM) for high-performance cryptography, Web Workers for non-blocking computation.
- **Backend / Host Server:** Go or Rust (lightweight engine and single self-contained binary), REST API & WebSockets for direct chunked transfers.
- **Security & Cryptography:**
  - Symmetric encryption: **AES-256-GCM**.
  - Ephemeral asymmetric key exchange: **ECDH (Curve25519)**.
  - Integrity hashing: **SHA-256**.
  - Transport: **TLS 1.3** with local P2P fallback (WebRTC DataChannel).

### 6.2 Non-Negotiable Security Principles
- **Zero-Knowledge Architecture:** the host stores encrypted blocks; decryption only happens in the recipient browser's memory sandbox.
- **Zero-Trace Guarantee:** no decrypted temporary file is ever written to the host server's disk during a transfer.
- **Auditability:** immutable local log of every download event (timestamp, local/remote IP address, SHA-256 verification success/failure).

---

## 7. Design System & UI/UX Principles

- **Design System:** *Passerelle Core* (cobalt primary blue palette `#1d4ed8`, light surfaces `#f8f9ff` down to low containers `#eff4ff`, `Inter` typography).
- **Themes:** native Light and Dark (OLED) support.
- **Accessibility & Ergonomics:**
  - Mobile touch targets above 48px.
  - WCAG AAA contrasts on metadata and status chips.
  - Reactive native components: Bottom Sheets on mobile, Right Drawers on Desktop.

---

## 8. Roadmap & Milestones

| Phase | Key deliverables | Status |
| :--- | :--- | :--- |
| **Phase 1 (Current)** | Complete UI/UX design (Desktop & Mobile): Explorer, Sharing, Links, Recipient Page, Progress Bar. | **Done** (screens approved) |
| **Phase 2** | REST/WebSocket API specification & audit log flow mockups. | **In progress** |
| **Phase 3** | Development of the lightweight backend engine (Go/Rust) and WebAssembly cryptographic integration. | Q1 2027 |
| **Phase 4** | P2P transfer via WebRTC on the local network and 1-click Docker/TrueNAS/Synology package. | Q2 2027 |
