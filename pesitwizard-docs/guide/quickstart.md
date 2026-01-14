# Démarrage rapide

Ce guide vous permettra d'effectuer votre premier transfert PeSIT Wizard en moins de 15 minutes.

## Prérequis

- Docker et Docker Compose
- Accès à un serveur PeSIT Wizard (votre banque ou notre serveur de test)

## 1. Lancer le client PeSIT Wizard

```bash
# Télécharger et lancer le client
docker run -d \
  --name pesitwizard-client \
  -p 9081:9081 \
  -e SPRING_DATASOURCE_URL=jdbc:h2:mem:pesitwizard \
  ghcr.io/pesitwizard/pesitwizard-client:latest
```

L'interface web est accessible sur http://localhost:9081

## 2. Configurer un serveur cible

Dans l'interface web, allez dans **Serveurs** > **Ajouter** :

| Champ | Valeur |
|-------|--------|
| Nom | Ma Banque |
| Hôte | pesitwizard.mabanque.fr |
| Port | 5000 |
| Identifiant serveur | BANK_SERVER |
| Identifiant client | MON_ENTREPRISE |
| Mot de passe | (fourni par la banque) |

## 3. Envoyer un fichier

1. Allez dans **Transferts** > **Envoyer**
2. Sélectionnez le serveur configuré
3. Choisissez le fichier à envoyer
4. Renseignez le nom distant (ex: `VIREMENT_20250110.XML`)
5. Cliquez sur **Envoyer**

## 4. Recevoir un fichier

1. Allez dans **Transferts** > **Recevoir**
2. Sélectionnez le serveur
3. Renseignez le nom du fichier distant (ex: `RELEVE_20250110.XML`)
4. Cliquez sur **Recevoir**

## Environnement de test

Pour tester sans accès bancaire, vous pouvez lancer notre serveur de test :

```bash
# Lancer un serveur PeSIT Wizard de test
docker run -d \
  --name pesitwizard-server \
  -p 5000:5000 \
  -p 8080:8080 \
  ghcr.io/pesitwizard/pesitwizard-server:latest
```

Puis configurez le client avec :
- Hôte : `localhost`
- Port : `5000`
- Identifiant serveur : `PESIT_SERVER`
- Identifiant client : `TEST_CLIENT`

## Prochaines étapes

- [Configuration avancée du client](/guide/client/configuration)
- [Intégration avec votre ERP](/guide/client/erp-integration)
- [Déploiement en production](/guide/server/installation)
