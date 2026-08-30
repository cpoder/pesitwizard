# Certificates & PKI

The node has a built-in certificate store and PKI, managed in the **Certificates** tab or under
`/api/v1/certificates`. TLS on listeners and outbound connections references a keystore / truststore
by name.

![Certificates](/screenshots/certificates.png)

## Store

- **Keystores** — a TLS identity (certificate + private key).
- **Truststores** — CA bundles used to validate the peer.

Every certificate can be inspected (subject, issuer, SAN, validity, fingerprint).

## TLS

Enable TLS on a listener with `sslEnabled` and a keystore, and (for mTLS) a truststore and
`sslClientAuth`. `tcpipHeader` toggles the Connect:Express transport-length header. Outbound TLS is
configured per remote server.

## Local CA

Generate a certificate authority and issue partner / server certificates from it:

```bash
curl -s "${H[@]}" "$A/api/v1/certificates/ca" -X POST \
  -d '{"commonName":"PeSIT Wizard CA","organization":"Acme","validityDays":3650}'

curl -s "${H[@]}" "$A/api/v1/certificates/issue" -X POST \
  -d '{"name":"pesit-node","commonName":"pesit.example.com","sans":["pesit.example.com"],"ttlDays":825,"kind":"server"}'
```

## Native HashiCorp Vault PKI

Instead of the local CA, the node can issue and sign through **Vault's PKI secrets engine** (token or
AppRole auth), configured per node under `/api/v1/certificates/vault`.

## Rotation, revocation, OCSP

- **Rotation** — `POST /api/v1/certificates/keystores/{name}/rotate` re-issues a managed keystore in
  place; a leader-driven task auto-rotates keystores within `PESIT_CERT_ROTATION_DAYS` of expiry.
- **Revocation** — `POST /api/v1/certificates/revoked` revokes a serial; `GET /api/v1/certificates/crl`
  returns a CRL signed by the local CA.
- **OCSP** — an online responder (RFC 6960) at **`/ocsp`** (POST and GET, unauthenticated) answers the
  revocation status of certificates issued by the local CA, signed by the CA key:

  ```console
  $ openssl ocsp -issuer ca.pem -cert leaf.pem -url http://node:8080/ocsp -CAfile ca.pem
  Response verify OK
  leaf.pem: good
  ```
