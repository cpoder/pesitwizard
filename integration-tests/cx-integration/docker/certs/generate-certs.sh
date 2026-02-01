#!/bin/bash
# Generate test certificates for PeSIT Wizard TLS testing
# Creates CA, server, and client certificates

set -e

CERT_DIR="$(dirname "$0")"
cd "$CERT_DIR"

# Configuration
CA_SUBJECT="/C=FR/ST=IDF/L=Paris/O=PeSIT Wizard/OU=Test CA/CN=PeSIT Wizard Test CA"
SERVER_SUBJECT="/C=FR/ST=IDF/L=Paris/O=PeSIT Wizard/OU=Server/CN=pw-server"
CLIENT_SUBJECT="/C=FR/ST=IDF/L=Paris/O=PeSIT Wizard/OU=Client/CN=pw-client"
CX_SERVER_SUBJECT="/C=FR/ST=IDF/L=Paris/O=Connect Express/OU=Server/CN=cx-server"
CX_CLIENT_SUBJECT="/C=FR/ST=IDF/L=Paris/O=Connect Express/OU=Client/CN=cx-client"
VALIDITY_DAYS=365
KEY_SIZE=2048
PASSWORD="changeit"

echo "=== Generating PeSIT Wizard Test Certificates ==="
echo ""

# 1. Generate CA key and certificate
echo "1. Generating CA certificate..."
openssl genrsa -out ca-key.pem $KEY_SIZE 2>/dev/null
openssl req -new -x509 -days $VALIDITY_DAYS -key ca-key.pem -out ca-cert.pem \
    -subj "$CA_SUBJECT" 2>/dev/null
echo "   CA certificate: ca-cert.pem"

# 2. Generate PW Server certificate
echo "2. Generating PW Server certificate..."
openssl genrsa -out pw-server-key.pem $KEY_SIZE 2>/dev/null
openssl req -new -key pw-server-key.pem -out pw-server.csr \
    -subj "$SERVER_SUBJECT" 2>/dev/null

# Create extensions file for SAN
cat > pw-server-ext.cnf << EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names

[alt_names]
DNS.1 = pw-server
DNS.2 = localhost
IP.1 = 127.0.0.1
EOF

openssl x509 -req -days $VALIDITY_DAYS -in pw-server.csr -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out pw-server-cert.pem -extfile pw-server-ext.cnf 2>/dev/null

# Create PKCS12 keystore for Java
openssl pkcs12 -export -in pw-server-cert.pem -inkey pw-server-key.pem \
    -certfile ca-cert.pem -out pw-server-keystore.p12 -name pw-server \
    -password pass:$PASSWORD 2>/dev/null
echo "   Server keystore: pw-server-keystore.p12"

# 3. Generate PW Client certificate
echo "3. Generating PW Client certificate..."
openssl genrsa -out pw-client-key.pem $KEY_SIZE 2>/dev/null
openssl req -new -key pw-client-key.pem -out pw-client.csr \
    -subj "$CLIENT_SUBJECT" 2>/dev/null

cat > pw-client-ext.cnf << EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = clientAuth
subjectAltName = @alt_names

[alt_names]
DNS.1 = pw-client
DNS.2 = localhost
IP.1 = 127.0.0.1
EOF

openssl x509 -req -days $VALIDITY_DAYS -in pw-client.csr -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out pw-client-cert.pem -extfile pw-client-ext.cnf 2>/dev/null

openssl pkcs12 -export -in pw-client-cert.pem -inkey pw-client-key.pem \
    -certfile ca-cert.pem -out pw-client-keystore.p12 -name pw-client \
    -password pass:$PASSWORD 2>/dev/null
echo "   Client keystore: pw-client-keystore.p12"

# 4. Generate CX Server certificate (for CX to accept TLS connections)
echo "4. Generating CX Server certificate..."
openssl genrsa -out cx-server-key.pem $KEY_SIZE 2>/dev/null
openssl req -new -key cx-server-key.pem -out cx-server.csr \
    -subj "$CX_SERVER_SUBJECT" 2>/dev/null

cat > cx-server-ext.cnf << EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names

[alt_names]
DNS.1 = cx-server
DNS.2 = localhost
IP.1 = 127.0.0.1
EOF

openssl x509 -req -days $VALIDITY_DAYS -in cx-server.csr -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out cx-server-cert.pem -extfile cx-server-ext.cnf 2>/dev/null

openssl pkcs12 -export -in cx-server-cert.pem -inkey cx-server-key.pem \
    -certfile ca-cert.pem -out cx-server-keystore.p12 -name cx-server \
    -password pass:$PASSWORD 2>/dev/null
echo "   CX server keystore: cx-server-keystore.p12"

# 5. Generate CX Client certificate (for CX to initiate TLS connections to PW)
echo "5. Generating CX Client certificate..."
openssl genrsa -out cx-client-key.pem $KEY_SIZE 2>/dev/null
openssl req -new -key cx-client-key.pem -out cx-client.csr \
    -subj "$CX_CLIENT_SUBJECT" 2>/dev/null

cat > cx-client-ext.cnf << EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = clientAuth
subjectAltName = @alt_names

[alt_names]
DNS.1 = cx-client
DNS.2 = cx-server
DNS.3 = localhost
IP.1 = 127.0.0.1
EOF

openssl x509 -req -days $VALIDITY_DAYS -in cx-client.csr -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out cx-client-cert.pem -extfile cx-client-ext.cnf 2>/dev/null

openssl pkcs12 -export -in cx-client-cert.pem -inkey cx-client-key.pem \
    -certfile ca-cert.pem -out cx-client-keystore.p12 -name cx-client \
    -password pass:$PASSWORD 2>/dev/null
echo "   CX client keystore: cx-client-keystore.p12"

# 7. Create CA truststore
echo "6. Creating CA truststore..."
keytool -importcert -alias pesit-ca -file ca-cert.pem \
    -keystore ca-truststore.p12 -storetype PKCS12 \
    -storepass $PASSWORD -noprompt 2>/dev/null
echo "   Truststore: ca-truststore.p12"

# 8. Cleanup CSR and extension files
rm -f *.csr *.cnf *.srl

echo ""
echo "=== Certificate Generation Complete ==="
echo ""
echo "Files created:"
echo "  CA:         ca-cert.pem, ca-key.pem"
echo "  PW Server:  pw-server-keystore.p12, pw-server-cert.pem, pw-server-key.pem"
echo "  PW Client:  pw-client-keystore.p12, pw-client-cert.pem, pw-client-key.pem"
echo "  CX Server:  cx-server-keystore.p12, cx-server-cert.pem, cx-server-key.pem"
echo "  CX Client:  cx-client-keystore.p12, cx-client-cert.pem, cx-client-key.pem"
echo "  Truststore: ca-truststore.p12"
echo ""
echo "Password for all keystores: $PASSWORD"
echo ""
echo "To verify certificates:"
echo "  openssl x509 -in ca-cert.pem -text -noout"
echo "  keytool -list -keystore pw-server-keystore.p12 -storepass $PASSWORD"
