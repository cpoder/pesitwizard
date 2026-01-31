# PeSIT Wizard - Roadmap vers la Production

## État Actuel: 80% (Beta Avancé)

Dernière mise à jour: 2026-01-31

---

## Phase 1: Validation TLS/SSL (Priorité: HAUTE)

**Objectif**: Sécuriser les communications avec Connect:Express

| ID | Tâche | Effort | Status |
|----|-------|--------|--------|
| 1.1 | Configurer SSL parameter tables (SSLPARM1/SSLPARM2) dans CX | 2h | ⬜ |
| 1.2 | Créer certificats de test (CA, server, client) compatibles PW/CX | 1h | ⬜ |
| 1.3 | Modifier `cx-setup-partner` pour supporter `nature='S'` | 1h | ⬜ |
| 1.4 | Tester TLS unidirectionnel (server auth only) PW -> CX | 2h | ⬜ |
| 1.5 | Tester TLS unidirectionnel CX -> PW | 2h | ⬜ |
| 1.6 | Tester mTLS (mutual TLS) bidirectionnel | 2h | ⬜ |
| 1.7 | Ajouter tests TLS au Docker integration suite | 2h | ⬜ |
| 1.8 | Documenter procédure de configuration TLS | 1h | ⬜ |

**Critère de succès**: Tests Docker TLS passent, documentation complète

---

## Phase 2: Tests de Performance (Priorité: HAUTE)

**Objectif**: Établir les limites et garantir la stabilité sous charge

| ID | Tâche | Effort | Status |
|----|-------|--------|--------|
| 2.1 | Créer script de benchmark avec JMeter ou Gatling | 4h | ⬜ |
| 2.2 | Test: 10 transferts concurrents de 100MB | 2h | ⬜ |
| 2.3 | Test: 100 transferts concurrents de 1MB | 2h | ⬜ |
| 2.4 | Test: 1 transfert de 10GB (fichier volumineux) | 2h | ⬜ |
| 2.5 | Test: transferts pendant 24h (stabilité long terme) | 1h setup | ⬜ |
| 2.6 | Mesurer: latence, throughput, CPU, mémoire | 2h | ⬜ |
| 2.7 | Identifier et corriger les goulots d'étranglement | Variable | ⬜ |
| 2.8 | Documenter les benchmarks et limites recommandées | 2h | ⬜ |

**Critère de succès**: Benchmarks documentés, pas de memory leak sur 24h

---

## Phase 3: Tests de Résilience (Priorité: HAUTE)

**Objectif**: Garantir la robustesse face aux pannes

| ID | Tâche | Effort | Status |
|----|-------|--------|--------|
| 3.1 | Test: kill -9 pendant transfert, vérifier restart | 2h | ⬜ |
| 3.2 | Test: coupure réseau (iptables drop) pendant transfert | 2h | ⬜ |
| 3.3 | Test: timeout serveur distant (connexion lente) | 1h | ⬜ |
| 3.4 | Test: disque plein côté réception | 1h | ⬜ |
| 3.5 | Test: certificat expiré (TLS) | 1h | ⬜ |
| 3.6 | Test: rollback après échec partiel | 2h | ⬜ |
| 3.7 | Implémenter retry automatique avec backoff exponentiel | 4h | ⬜ |
| 3.8 | Ajouter circuit breaker pour serveurs défaillants | 4h | ⬜ |

**Critère de succès**: Tous les scénarios de panne gérés gracieusement

---

## Phase 4: Sécurité (Priorité: HAUTE)

**Objectif**: Audit et renforcement de la sécurité

| ID | Tâche | Effort | Status |
|----|-------|--------|--------|
| 4.1 | Audit OWASP: injection, XSS, CSRF sur API REST | 4h | ⬜ |
| 4.2 | Vérifier chiffrement des secrets (passwords, keystores) | 2h | ⬜ |
| 4.3 | Implémenter rate limiting sur API REST | 2h | ⬜ |
| 4.4 | Ajouter validation stricte des entrées (filenames, paths) | 2h | ⬜ |
| 4.5 | Scan de dépendances (OWASP Dependency Check) | 1h | ⬜ |
| 4.6 | Configurer Content Security Policy pour l'UI | 1h | ⬜ |
| 4.7 | Documenter la politique de sécurité | 2h | ⬜ |

**Critère de succès**: Aucune vulnérabilité critique/haute

---

## Phase 5: Haute Disponibilité (Priorité: MOYENNE)

**Objectif**: Clustering et failover en production

| ID | Tâche | Effort | Status |
|----|-------|--------|--------|
| 5.1 | Tester cluster 2 nodes avec load balancer | 4h | ⬜ |
| 5.2 | Tester failover: kill node primaire pendant transfert | 2h | ⬜ |
| 5.3 | Tester reprise de transfert après failover | 2h | ⬜ |
| 5.4 | Valider cohérence BDD avec PostgreSQL en cluster | 2h | ⬜ |
| 5.5 | Documenter architecture HA recommandée | 2h | ⬜ |

**Critère de succès**: Failover transparent, pas de perte de données

---

## Phase 6: Observabilité Production (Priorité: MOYENNE)

**Objectif**: Monitoring et alerting pour opérations

| ID | Tâche | Effort | Status |
|----|-------|--------|--------|
| 6.1 | Dashboard Grafana: transferts, erreurs, latence | 4h | ⬜ |
| 6.2 | Alertes: transfert échoué, queue pleine, certificat expire | 2h | ⬜ |
| 6.3 | Métriques business: volume/jour, taux de succès, partenaires | 2h | ⬜ |
| 6.4 | Intégration avec système d'alerting (PagerDuty, Slack) | 2h | ⬜ |
| 6.5 | Logs structurés (JSON) pour ELK/Splunk | 2h | ⬜ |
| 6.6 | Tracing distribué (Jaeger/Zipkin) pour debug | 4h | ⬜ |

**Critère de succès**: Visibilité complète des opérations

---

## Phase 7: Documentation (Priorité: MOYENNE)

**Objectif**: Compléter la documentation existante (pesitwizard-docs)

**Documentation existante** (✅ déjà fait):
- Guide Client: installation, configuration, usage, vidéo démo, screenshots
- Guide Serveur: installation, configuration, connecteurs, secrets, observabilité
- Sécurité: TLS/mTLS complet (550 lignes), CA privée, workflows certificats
- API Reference: authentification, client API, server API
- Déploiement: Docker, Kubernetes, Helm

| ID | Tâche | Effort | Status |
|----|-------|--------|--------|
| 7.1 | Guide de dépannage: erreurs PeSIT, diagnostics réseau | 3h | ⬜ |
| 7.2 | Runbook opérationnel: backup, restore, maintenance | 3h | ⬜ |
| 7.3 | Guide Connect:Express: interopérabilité, configuration | 2h | ⬜ |
| 7.4 | Guide performance: tuning, benchmarks, limites | 2h | ⬜ |

**Critère de succès**: Ops peut résoudre les problèmes courants sans escalade

---

## Phase 8: Conformité et Audit (Priorité: BASSE)

**Objectif**: Traçabilité pour audits bancaires

| ID | Tâche | Effort | Status |
|----|-------|--------|--------|
| 8.1 | Log d'audit immutable (qui, quoi, quand) | 4h | ⬜ |
| 8.2 | Rétention des logs configurable | 2h | ⬜ |
| 8.3 | Export des logs pour audit externe | 2h | ⬜ |
| 8.4 | Rapport de conformité automatisé | 4h | ⬜ |

**Critère de succès**: Piste d'audit complète pour régulateurs

---

## Résumé par Priorité

| Priorité | Phases | Effort Total | Impact |
|----------|--------|--------------|--------|
| **HAUTE** | 1, 2, 3, 4 | ~60h | Bloquant pour production |
| **MOYENNE** | 5, 6, 7 | ~38h | Nécessaire pour opérations |
| **BASSE** | 8 | ~12h | Nice-to-have |

**Total estimé**: ~110h de travail (3 semaines à temps plein)

> Note: La documentation existante (pesitwizard-docs) couvre déjà ~80% des besoins.
> Seuls les guides dépannage, runbook ops et CX interop restent à faire.

---

## Checklist Go/No-Go Production

- [ ] TLS validé avec partenaire réel (Phase 1)
- [ ] Benchmarks documentés et acceptables (Phase 2)
- [ ] Tests de résilience passent (Phase 3)
- [ ] Audit sécurité sans critique (Phase 4)
- [x] Documentation utilisateur complète (pesitwizard-docs ✅)
- [ ] Monitoring et alerting en place (Phase 6)
- [ ] Runbook opérationnel validé (Phase 7)
- [ ] Test avec volume réel pendant 1 semaine (Phase 2)

---

## Historique

| Date | Version | Changements |
|------|---------|-------------|
| 2026-01-31 | 1.0 | Création initiale après validation CX integration |
