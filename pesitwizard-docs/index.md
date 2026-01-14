---
layout: home

hero:
  name: PeSIT Wizard
  text: Transferts bancaires PeSIT
  tagline: Solution open source pour automatiser vos échanges de fichiers avec les banques.
  actions:
    - theme: brand
      text: Démarrer
      link: /guide/quickstart
    - theme: alt
      text: GitHub
      link: https://github.com/pesitwizard/pesitwizard

features:
  - icon: 📡
    title: Protocole PeSIT
    details: Implémentation complète du protocole PeSIT D et E pour les échanges bancaires.
  - icon: ⚡
    title: Simple à déployer
    details: Configuration simple, documentation complète. Pas besoin d'expert PeSIT.
  - icon: 🔌
    title: API REST
    details: Intégrez facilement vos ERP et logiciels comptables via notre API REST.
  - icon: 🐳
    title: Docker Ready
    details: Images Docker disponibles pour un déploiement rapide.
  - icon: 🔒
    title: Sécurisé
    details: TLS 1.3, authentification par certificat, chiffrement de bout en bout.
  - icon: 📖
    title: Open Source
    details: Licence Apache 2.0. Code source disponible sur GitHub.
---

## Pourquoi PeSIT Wizard ?

Le protocole **PeSIT** (Protocole d'Échange pour un Système Interbancaire de Télécompensation) est le standard utilisé par les banques françaises pour les échanges de fichiers sécurisés.

**PeSIT Wizard** est une implémentation open source moderne du protocole PeSIT :
- **Gratuit** : licence Apache 2.0
- **Simple** : documentation complète, API REST
- **Moderne** : Java 21, Spring Boot, Docker

## Cas d'usage

### Automatisation des virements
Envoyez automatiquement vos fichiers de virements SEPA à votre banque depuis votre ERP.

### Récupération des relevés
Récupérez automatiquement vos relevés de compte chaque matin pour les intégrer dans votre comptabilité.

### Centralisation multi-banques
Gérez tous vos échanges avec plusieurs banques depuis une seule interface.

## Composants

| Module | Description |
|--------|-------------|
| **pesitwizard-server** | Serveur PeSIT complet |
| **pesitwizard-client** | Client Java pour envoyer/recevoir des fichiers |
| **pesitwizard-client-ui** | Interface graphique pour le client |
| **pesitwizard-pesit** | Bibliothèque d'implémentation du protocole |

---

<div style="text-align: center; margin-top: 2rem;">
  <a href="/guide/quickstart" style="display: inline-block; padding: 12px 24px; background: #3451b2; color: white; border-radius: 8px; text-decoration: none; font-weight: 500; margin-right: 1rem;">
    Documentation
  </a>
  <a href="https://github.com/pesitwizard/pesitwizard" style="display: inline-block; padding: 12px 24px; background: #24292e; color: white; border-radius: 8px; text-decoration: none; font-weight: 500;">
    GitHub
  </a>
</div>
