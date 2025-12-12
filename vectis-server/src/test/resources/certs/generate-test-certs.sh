#!/bin/bash
# Generate self-signed certificates for TLS and mTLS testing
# Run this script from the certs directory

set -e

CERTS_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$CERTS_DIR"

# Configuration
CA_DAYS=3650
CERT_DAYS=365
KEY_SIZE=2048
PASSWORD="changeit"

echo "=== Generating Test Certificates for PeSIT Server ==="
echo "Output directory: $CERTS_DIR"
echo ""

# ========== CA Certificate ==========
echo "1. Generating CA certificate..."
openssl genrsa -out ca-key.pem $KEY_SIZE 2>/dev/null

openssl req -new -x509 -days $CA_DAYS -key ca-key.pem -out ca-cert.pem \
    -subj "/C=FR/ST=IDF/L=Paris/O=PeSIT Test CA/OU=Test/CN=PeSIT Test CA"

# Create CA truststore (PKCS12) using keytool (openssl doesn't work well for truststores)
rm -f ca-truststore.p12
keytool -importcert -file ca-cert.pem -alias ca -keystore ca-truststore.p12 \
    -storetype PKCS12 -storepass $PASSWORD -noprompt

echo "   CA certificate: ca-cert.pem"
echo "   CA truststore: ca-truststore.p12"
echo ""

# ========== Server Certificate ==========
echo "2. Generating Server certificate..."
openssl genrsa -out server-key.pem $KEY_SIZE 2>/dev/null

# Create server CSR
openssl req -new -key server-key.pem -out server.csr \
    -subj "/C=FR/ST=IDF/L=Paris/O=PeSIT Server/OU=Test/CN=localhost"

# Create server certificate extensions
cat > server-ext.cnf << EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names

[alt_names]
DNS.1 = localhost
DNS.2 = pesit-server
DNS.3 = *.pesit-server.svc.cluster.local
IP.1 = 127.0.0.1
IP.2 = ::1
EOF

# Sign server certificate with CA
openssl x509 -req -in server.csr -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out server-cert.pem -days $CERT_DAYS \
    -extfile server-ext.cnf 2>/dev/null

# Create server keystore (PKCS12)
openssl pkcs12 -export -in server-cert.pem -inkey server-key.pem \
    -certfile ca-cert.pem -out server-keystore.p12 \
    -password pass:$PASSWORD -name "server"

echo "   Server certificate: server-cert.pem"
echo "   Server keystore: server-keystore.p12"
echo ""

# ========== Client Certificate (for mTLS) ==========
echo "3. Generating Client certificate (for mTLS)..."
openssl genrsa -out client-key.pem $KEY_SIZE 2>/dev/null

# Create client CSR
openssl req -new -key client-key.pem -out client.csr \
    -subj "/C=FR/ST=IDF/L=Paris/O=PeSIT Client/OU=Test/CN=test-client"

# Create client certificate extensions
cat > client-ext.cnf << EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
extendedKeyUsage = clientAuth
EOF

# Sign client certificate with CA
openssl x509 -req -in client.csr -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out client-cert.pem -days $CERT_DAYS \
    -extfile client-ext.cnf 2>/dev/null

# Create client keystore (PKCS12)
openssl pkcs12 -export -in client-cert.pem -inkey client-key.pem \
    -certfile ca-cert.pem -out client-keystore.p12 \
    -password pass:$PASSWORD -name "client"

echo "   Client certificate: client-cert.pem"
echo "   Client keystore: client-keystore.p12"
echo ""

# ========== Partner Certificate (for partner-specific mTLS) ==========
echo "4. Generating Partner certificate..."
openssl genrsa -out partner-key.pem $KEY_SIZE 2>/dev/null

openssl req -new -key partner-key.pem -out partner.csr \
    -subj "/C=FR/ST=IDF/L=Paris/O=Partner Corp/OU=MFT/CN=PARTNER_A"

openssl x509 -req -in partner.csr -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out partner-cert.pem -days $CERT_DAYS \
    -extfile client-ext.cnf 2>/dev/null

openssl pkcs12 -export -in partner-cert.pem -inkey partner-key.pem \
    -certfile ca-cert.pem -out partner-keystore.p12 \
    -password pass:$PASSWORD -name "partner"

echo "   Partner certificate: partner-cert.pem"
echo "   Partner keystore: partner-keystore.p12"
echo ""

# ========== Untrusted Certificate (for negative testing) ==========
echo "5. Generating Untrusted certificate (self-signed, not from CA)..."
openssl genrsa -out untrusted-key.pem $KEY_SIZE 2>/dev/null

openssl req -new -x509 -days $CERT_DAYS -key untrusted-key.pem -out untrusted-cert.pem \
    -subj "/C=FR/ST=IDF/L=Paris/O=Untrusted/OU=Test/CN=untrusted-client"

openssl pkcs12 -export -in untrusted-cert.pem -inkey untrusted-key.pem \
    -out untrusted-keystore.p12 -password pass:$PASSWORD -name "untrusted"

echo "   Untrusted certificate: untrusted-cert.pem"
echo "   Untrusted keystore: untrusted-keystore.p12"
echo ""

# ========== Expired Certificate (for negative testing) ==========
echo "6. Generating Expired certificate..."
openssl genrsa -out expired-key.pem $KEY_SIZE 2>/dev/null

openssl req -new -key expired-key.pem -out expired.csr \
    -subj "/C=FR/ST=IDF/L=Paris/O=Expired/OU=Test/CN=expired-client"

# Create certificate that expired yesterday
openssl x509 -req -in expired.csr -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out expired-cert.pem -days -1 \
    -extfile client-ext.cnf 2>/dev/null || true

# If the above fails (some OpenSSL versions don't support negative days), create with 1 day
if [ ! -f expired-cert.pem ]; then
    openssl x509 -req -in expired.csr -CA ca-cert.pem -CAkey ca-key.pem \
        -CAcreateserial -out expired-cert.pem -days 1 \
        -extfile client-ext.cnf 2>/dev/null
    echo "   Note: Created certificate with 1 day validity (adjust system time for testing)"
fi

openssl pkcs12 -export -in expired-cert.pem -inkey expired-key.pem \
    -certfile ca-cert.pem -out expired-keystore.p12 \
    -password pass:$PASSWORD -name "expired" 2>/dev/null || true

echo "   Expired certificate: expired-cert.pem"
echo ""

# ========== Cleanup temporary files ==========
echo "7. Cleaning up temporary files..."
rm -f *.csr *.cnf *.srl

# ========== Summary ==========
echo ""
echo "=== Certificate Generation Complete ==="
echo ""
echo "Files generated:"
echo "  CA:"
echo "    - ca-cert.pem (CA certificate)"
echo "    - ca-key.pem (CA private key)"
echo "    - ca-truststore.p12 (Truststore with CA cert, password: $PASSWORD)"
echo ""
echo "  Server:"
echo "    - server-cert.pem (Server certificate)"
echo "    - server-key.pem (Server private key)"
echo "    - server-keystore.p12 (Keystore, password: $PASSWORD)"
echo ""
echo "  Client (mTLS):"
echo "    - client-cert.pem (Client certificate)"
echo "    - client-key.pem (Client private key)"
echo "    - client-keystore.p12 (Keystore, password: $PASSWORD)"
echo ""
echo "  Partner:"
echo "    - partner-cert.pem (Partner certificate)"
echo "    - partner-key.pem (Partner private key)"
echo "    - partner-keystore.p12 (Keystore, password: $PASSWORD)"
echo ""
echo "  Untrusted (for negative testing):"
echo "    - untrusted-cert.pem (Self-signed, not trusted)"
echo "    - untrusted-keystore.p12 (Keystore, password: $PASSWORD)"
echo ""
echo "All keystores/truststores use password: $PASSWORD"
