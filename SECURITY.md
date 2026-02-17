# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.2.x   | :white_check_mark: |
| < 1.2   | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability in PeSIT Wizard, please report it responsibly:

1. **Do NOT** open a public GitHub issue for security vulnerabilities
2. Email security findings to: security@pesitwizard.com
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

We will acknowledge receipt within 48 hours and provide a detailed response within 7 business days.

## Security Measures

- All secrets are encrypted at rest (AES-256-GCM or HashiCorp Vault)
- TLS 1.3 enforced for PeSIT protocol connections
- CSRF protection on all state-changing endpoints
- Rate limiting on all API endpoints
- Input validation and output encoding throughout
- Container images scanned with Trivy on every build
- Dependencies monitored via Dependabot and OWASP Dependency Check
