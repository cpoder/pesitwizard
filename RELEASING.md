# Releasing to Maven Central

## Prerequisites

### 1. Sonatype OSSRH Account
Create account at https://issues.sonatype.org and claim `com.pesitwizard` namespace.

### 2. GPG Key
```bash
gpg --gen-key
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
gpg --armor --export-secret-keys YOUR_KEY_ID
```

### 3. GitHub Secrets
- `OSSRH_USERNAME`: Sonatype username
- `OSSRH_TOKEN`: Sonatype password
- `GPG_PRIVATE_KEY`: Armored GPG private key
- `GPG_PASSPHRASE`: GPG passphrase

## Release Process

```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow deploys to Maven Central automatically.
