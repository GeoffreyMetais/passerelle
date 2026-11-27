# Product Requirements Document (PRD) — Passerelle

**Projet :** Passerelle (Transfert & Partage Sécurisé Souverain)  
**Version :** 1.0.0  
**Statut :** Validé / En phase de conception & prototypage  
**Date :** Novembre 2026  
**Auteurs :** Équipe Produit & Architecture Sécurité Passerelle  

---

## 1. Vue d'ensemble & Vision Produit

### 1.1 Contexte & Problématique
Le transfert de fichiers sensibles en entreprise ou entre particuliers repose aujourd'hui quasi-exclusivement sur des solutions SaaS centralisées ou des serveurs cloud tiers (WeTransfer, Google Drive, OneDrive, SwissTransfer, etc.). Ces intermédiaires posent des risques majeurs :
- Rupture du secret et de la confidentialité par transit et stockage non souverains sur des infrastructures tierces.
- Exposition aux lois extraterritoriales (ex. Cloud Act).
- Clés de chiffrement souvent détenues ou déchiffrables par le prestataire de service.
- Manque de contrôle en temps réel sur la durée de vie, la révocation et l'auditabilité des liens générés.

### 1.2 La Vision Passerelle
**Passerelle** est une solution moderne, auto-hébergée (self-hosted) et zéro-connaissance (*Zero-Knowledge*), permettant d'exposer, de partager et de distribuer des fichiers et dossiers volumineux directement depuis sa propre instance locale (NAS, serveur privé, conteneur Docker, machine de travail), avec un chiffrement matériel de bout en bout (E2EE) et sans dépendre d'un cloud intermédiaire.

---

## 2. Objectifs Stratégiques & Mesures de Succès (KPIs)

| Objectif | Métrique / KPI cible |
| :--- | :--- |
| **Souveraineté & Zéro-Trace** | 100% du déchiffrement réalisé côté client (navigateur / WebAssembly). Aucune clé en clair reçue par le serveur. |
| **Simplicité d'usage (UX)** | Moins de 3 clics et moins de 5 secondes pour créer un lien chiffré prêt à partager avec QR code. |
| **Performance de transfert** | Débit maximal en connexion directe locale ou P2P (> 50 Mo/s en LAN / direct tunnel) avec vérification de hachage SHA-256 instantanée. |
| **Adoption multi-device** | Expérience fluide et réactive de la vue destinataire sur desktop et mobile sans obligation de créer un compte. |

---

## 3. Personas Utilisateurs

### Persona 1 : L'Hôte / L'Administrateur ("Alexandre", Ingénieur DevOps & RSSI)
- **Besoins :** Partager des archives lourdes (`.tar.gz`, `.iso`, builds Linux, documentations confidentielles) avec des collaborateurs ou des tiers sans passer par un service SaaS grand public.
- **Attentes :** Contrôle précis des quotas de téléchargements, révocation immédiate en 1 clic, expiration configurable, protection par mot de passe et journal d'audit local transparent.

### Persona 2 : Le Destinataire ("Camille", Partenaire / Client)
- **Besoins :** Récupérer un fichier sécurisé rapidement, sur ordinateur ou smartphone, sans friction et sans créer de compte.
- **Attentes :** Interface rassurante, indicateurs de sécurité clairs (Zero-Knowledge, E2E), flash d'un QR code pour transfert direct sur mobile, barre de téléchargement transparente avec étapes cryptographiques.

---

## 4. Parcours Utilisateur & Arborescence (Information Architecture)

```
[INSTANCE LOCALE PASSERELLE]
│
├── 1. Explorateur de Fichiers (Vue Liste / Vue Grille)
│   ├── Navigation arborescente (Dossiers, Fichiers, Métadonnées)
│   ├── Actions contextuelles (Partager, Télécharger, Supprimer)
│   └── Barre d'état système (Stockage NVMe/SSD, Nœud actif, Liens en cours)
│
├── 2. Volet de Partage & Création de Liens (Side Drawer / Mobile Bottom Sheet)
│   ├── Paramètres d'expiration (1h, 24h, 7 jours, 30 jours, Permanent)
│   ├── Quota de téléchargement (Unique, 5, 10, Illimité)
│   ├── Sécurité (Mot de passe / clé AES-256, Alias personnalisé)
│   └── Génération instantanée → URL courte sécurisée + QR Code dynamique
│
├── 3. Gestionnaire des Liens Partagés (Dashboard Liens)
│   ├── Suivi des liens actifs et expirés
│   ├── Télémétrie : compteur de téléchargements, horodatage, statut
│   └── Actions : Édition rapide des règles, affichage QR Code, révocation immédiate
│
└── 4. Interface Publique Destinataire (Sans authentification requise)
    ├── Écran d'accueil sécurisé (Métadonnées du fichier, statut E2E, empreinte SHA-256)
    ├── Saisie du mot de passe / clé de déchiffrement local
    ├── Aperçu visuel sécurisé du document
    └── Écran de progression (Pipeline cryptographique : ECDH, Chunks WASM, AES-GCM, SHA-256)
```

---

## 5. Spécifications Fonctionnelles Détaillées

### 5.1 Explorateur de Fichiers (Desktop & Mobile)
- **Vues commutables :** Vue Liste tabulaire détaillée (taille, date de modification, badge de statut) et Vue Grille avec prévisualisation des cartes de fichiers.
- **Filtres et recherche :** Champ de recherche instantané (`Ctrl+K` / `⌘K`), filtres par type (`Tous`, `Dossiers`, `Fichiers & Médias`), tri alphabétique ou par date.
- **Télémétrie système :** Indicateur d'espace disque disponible (`128 Go libres sur 256 Go`) et statut de l'instance (`:RO Lecture Seule` ou actif).

### 5.2 Moteur de Partage & Génération de Liens
- **Création rapide en 1 clic :** Génération avec profils par défaut (7 jours, accès illimité avec mot de passe optionnel).
- **Règles d'accès fines :**
  - Expiration temporelle automatique avec compte à rebours.
  - Quota strict de téléchargements (ex: destruction après 1 téléchargement unique).
  - Protection renforcée par passphrase / clé client locale.
  - Alias d'URL personnalisable (ex. `/s/vacances-ete`, `/s/projet-carmen`).
- **Diffusion multicanal :**
  - Copie instantanée de l'URL publique (`http://localhost:8080/s/...`).
  - QR Code vectoriel haute résolution avec glyphe Passerelle pour scan mobile direct.
  - Raccourcis de partage natifs (e-mail, partage OS).

### 5.3 Suivi & Cycle de Vie des Liens (Dashboard)
- Filtrage par état : `Tous`, `Actifs`, `Expirés / Révoqués`.
- Édition en cours de vie : modification de la date d'expiration ou du mot de passe sans casser le lien URL.
- Révocation destructrice en 1 clic avec invalidation immédiate de la route sur le serveur hôte.

### 5.4 Expérience de Téléchargement Destinataire (Desktop & Mobile)
- **Shell minimaliste & sans friction :** Zéro menu d'administration, zéro inscription, focus 100% sur le document à recevoir.
- **Déchiffrement côté navigateur :** Saisie du mot de passe déchiffrant la clé en mémoire vive sans transmission vers le serveur.
- **Aperçu documentaire :** Prévisualisation des documents PDF/images dans un conteneur sécurisé vectoriel WebAssembly.
- **Visualiseur de flux cryptographique pas-à-pas :**
  1. *Échange de clés ECDH & Handshake* (Curve25519)
  2. *Réception des fragments chiffrés* (Chunks WebAssembly)
  3. *Déchiffrement local AES-256-GCM* (Web Workers en RAM)
  4. *Contrôle d'intégrité SHA-256* (Vérification du hash d'origine)
  5. *Assemblage & Sauvegarde locale* (File API navigateur)

---

## 6. Spécifications Techniques & Architecture

### 6.1 Pile Technologique Recommandée
- **Frontend :** HTML5, Tailwind CSS / Passerelle Core Design System, TypeScript, WebAssembly (WASM) pour la cryptographie performante, Web Workers pour le calcul non bloquant.
- **Backend / Serveur Hôte :** Go ou Rust (moteur léger et binaire unique autonome), API REST & WebSockets pour les transferts directs chunkés.
- **Sécurité & Cryptographie :**
  - Chiffrement symétrique : **AES-256-GCM**.
  - Échange asymétrique de clés éphémères : **ECDH (Curve25519)**.
  - Hachage d'intégrité : **SHA-256**.
  - Transport : **TLS 1.3** avec fallback P2P local (WebRTC DataChannel).

### 6.2 Principes de Sécurité Non Négociables
- **Zero-Knowledge Architecture :** L'hôte stocke les blocs chiffrés ; le déchiffrement n'a lieu que dans la sandbox mémoire du navigateur du destinataire.
- **Garantie Zéro-Trace :** Aucun fichier temporaire déchiffré n'est écrit sur le disque du serveur hôte lors d'une transmission.
- **Auditabilité :** Journalisation locale immuable de chaque événement de téléchargement (horodatage, adresse IP locale/distante, succès/échec de vérification SHA-256).

---

## 7. Système de Design & Principes d'UI/UX

- **Design System :** *Passerelle Core* (palette `#1d4ed8` bleu primaire cobalt, surfaces claires `#f8f9ff` à conteneurs bas `#eff4ff`, typographie `Inter`).
- **Thèmes :** Support natif Clair (Light) et Sombre (Dark / OLED).
- **Accessibilité & Ergonomie :**
  - Cibles tactiles mobiles supérieures à 48px.
  - Contrastes WCAG AAA sur les métadonnées et puces d'état.
  - Composants natifs réactifs : Bottom Sheets sur mobile, Right Drawers sur Desktop.

---

## 8. Feuille de Route & Jalons (Roadmap)

| Phase | Livrables clés | Statut |
| :--- | :--- | :--- |
| **Phase 1 (Actuelle)** | Conception UI/UX complète (Desktop & Mobile) : Explorateur, Partage, Liens, Page Destinataire, Progress Bar. | **Terminé** (Écrans validés) |
| **Phase 2** | Spécification de l'API REST/WebSocket & maquettage des flux de journaux d'audit. | **En cours** |
| **Phase 3** | Développement du moteur backend léger (Go/Rust) et intégration WebAssembly cryptographique. | T1 2027 |
| **Phase 4** | Transfert P2P via WebRTC en réseau local et package Docker/TrueNAS/Synology en 1 clic. | T2 2027 |
