#!/bin/bash
# Configure Connect:Express SSL/TLS parameters
# This script sets up certificates and SSL parameter tables for secure PeSIT connections
#
# IMPORTANT: This script must be run BEFORE the CX monitor starts!
# The certmgr.sh tool requires the monitor to be stopped.
#
# Usage: ./setup-ssl.sh
# Environment: Expects TOM_DIR to be set
#
# Certificate deployment:
#   1. CA certificate -> imported to CX trust chain (PWCA)
#   2. CX Server cert -> used when CX accepts TLS connections (CXSRVCERT)
#   3. CX Client cert -> used when CX initiates TLS connections (CXCLICERT)
#
# SSLPARM configuration:
#   - SSLPARM1: Server mode (for incoming TLS from PW Client)
#   - SSLPARM2: Client mode (for outgoing TLS to PW Server)

set -e

# Source CX profile
. $TOM_DIR/profile

CERTMGR="$TOM_DIR/config/certmgr.sh"
TOMPRM="$TOM_DIR/config/tom_prm"
SSL_DIR="$TOM_DIR/config/ssl"
PASSWORD="changeit"

echo "==========================================="
echo "  Connect:Express SSL Configuration"
echo "==========================================="
echo ""

# Check if certmgr.sh exists
if [ ! -x "$CERTMGR" ]; then
    echo "ERROR: certmgr.sh not found at $CERTMGR"
    echo "SSL configuration cannot proceed."
    exit 1
fi

# Check if CX monitor is running (certmgr.sh requires it to be stopped)
if pgrep -f "tom_mon" > /dev/null 2>&1; then
    echo "WARNING: CX monitor appears to be running."
    echo "certmgr.sh requires the monitor to be stopped."
    echo "Will try to continue with tom_prm only..."
    CERTMGR_AVAILABLE=false
else
    CERTMGR_AVAILABLE=true
fi

# Check if certificates exist
CERT_DIR="/app/certs"
if [ ! -d "$CERT_DIR" ]; then
    echo "WARNING: Certificate directory $CERT_DIR not found"
    echo "SSL configuration will be skipped"
    exit 0
fi

# Check for required certificate files
# IMPORTANT: CX has filename length limits - use short names matching CX conventions
CA_CERT="$CERT_DIR/ca-cert.pem"
SERVER_CERT="$CERT_DIR/CXSRV.pem"
SERVER_KEY="$CERT_DIR/CXSRVKEY.pem"
CLIENT_CERT="$CERT_DIR/CXCLI.pem"
CLIENT_KEY="$CERT_DIR/CXCLIKEY.pem"

if [ ! -f "$CA_CERT" ]; then
    echo "WARNING: CA certificate not found at $CA_CERT"
    echo "SSL configuration will be skipped"
    exit 0
fi

echo "Certificates found:"
echo "  CA cert: $CA_CERT"
[ -f "$SERVER_CERT" ] && echo "  Server cert: $SERVER_CERT"
[ -f "$CLIENT_CERT" ] && echo "  Client cert: $CLIENT_CERT"
echo ""

# -------------------------------------------------------------------
# Step 1: Import CA Certificate
# -------------------------------------------------------------------
echo "Step 1: Importing CA certificate (PWCA)..."

if [ "$CERTMGR_AVAILABLE" = true ]; then
    # Use certmgr.sh to create CA certificate definition
    # For CA certs (no private key), only cert file is needed
    $CERTMGR -y create PWCA "$CA_CERT" 2>/dev/null && \
        echo "   CA certificate imported successfully" || \
        echo "   CA certificate already exists or import failed"
    # Create symlink to ensure PWCA.pem exists (certmgr uses original filename)
    # CX looks for CERTNAM (PWCA.pem) but certmgr copies with original basename
    CA_BASENAME=$(basename "$CA_CERT")
    if [ "$CA_BASENAME" != "PWCA.pem" ]; then
        ln -sf "$CA_BASENAME" "$SSL_DIR/lcert/PWCA.pem" 2>/dev/null || true
    fi
else
    # Manual import: copy cert to lcert directory and create hash link
    echo "   Copying CA certificate manually..."
    cp "$CA_CERT" "$SSL_DIR/lcert/PWCA.pem"

    # Create hash link for OpenSSL verification
    HASH=$($SSL_DIR/openssl/bin/openssl x509 -hash -noout -in "$SSL_DIR/lcert/PWCA.pem")
    cd "$SSL_DIR/lcert"
    ln -sf PWCA.pem "${HASH}.0" 2>/dev/null || true
    cd - > /dev/null
    echo "   CA certificate copied to $SSL_DIR/lcert/PWCA.pem"
fi

# -------------------------------------------------------------------
# Step 2: Import CX Server Certificate (for incoming TLS connections)
# -------------------------------------------------------------------
# Note: CX has name limits: NAME max 8 chars, CERTNAM max 12 chars
# Using CXSRV as name (5 chars), CXSRV.pem as filename (8 chars)
echo ""
echo "Step 2: Importing CX Server certificate (CXSRV)..."

if [ -f "$SERVER_CERT" ] && [ -f "$SERVER_KEY" ]; then
    if [ "$CERTMGR_AVAILABLE" = true ]; then
        # Import server certificate with private key
        $CERTMGR -y -p "$PASSWORD" create CXSRV "$SERVER_CERT" "$SERVER_KEY" 2>/dev/null && \
            echo "   Server certificate imported successfully" || \
            echo "   Server certificate already exists or import failed"
    else
        # Manual import
        cp "$SERVER_CERT" "$SSL_DIR/lcert/CXSRV.pem"
        cp "$SERVER_KEY" "$SSL_DIR/priv/CXSRVKEY.pem"
        chmod 600 "$SSL_DIR/priv/CXSRVKEY.pem"

        HASH=$($SSL_DIR/openssl/bin/openssl x509 -hash -noout -in "$SSL_DIR/lcert/CXSRV.pem")
        cd "$SSL_DIR/lcert"
        ln -sf CXSRV.pem "${HASH}.0" 2>/dev/null || true
        cd - > /dev/null
        echo "   Server certificate copied manually"
    fi
else
    echo "   WARNING: Server certificate or key not found, skipping"
fi

# -------------------------------------------------------------------
# Step 3: Import CX Client Certificate (for outgoing TLS connections)
# -------------------------------------------------------------------
# Using CXCLI as name (5 chars), CXCLI.pem as filename (8 chars)
echo ""
echo "Step 3: Importing CX Client certificate (CXCLI)..."

if [ -f "$CLIENT_CERT" ] && [ -f "$CLIENT_KEY" ]; then
    if [ "$CERTMGR_AVAILABLE" = true ]; then
        # Import client certificate with private key
        $CERTMGR -y -p "$PASSWORD" create CXCLI "$CLIENT_CERT" "$CLIENT_KEY" 2>/dev/null && \
            echo "   Client certificate imported successfully" || \
            echo "   Client certificate already exists or import failed"
    else
        # Manual import
        cp "$CLIENT_CERT" "$SSL_DIR/lcert/CXCLI.pem"
        cp "$CLIENT_KEY" "$SSL_DIR/priv/CXCLIKEY.pem"
        chmod 600 "$SSL_DIR/priv/CXCLIKEY.pem"

        HASH=$($SSL_DIR/openssl/bin/openssl x509 -hash -noout -in "$SSL_DIR/lcert/CXCLI.pem")
        cd "$SSL_DIR/lcert"
        ln -sf CXCLI.pem "${HASH}.0" 2>/dev/null || true
        cd - > /dev/null
        echo "   Client certificate copied manually"
    fi
else
    echo "   WARNING: Client certificate or key not found, skipping"
fi

# -------------------------------------------------------------------
# Step 4: Configure SSLPARM1 (Server mode - accept incoming TLS)
# -------------------------------------------------------------------
echo ""
echo "Step 4: Configuring SSLPARM1 (Server mode on port 5001)..."

# SSLPARM format from tom_prm:
#   SSLMODE = S (server), C (client), A (any)
#   VERIFY_OPT = 0 (no verify), 1 (verify peer), 2 (require peer cert)
#   CERT_ID = certificate name from CERT table
#   CA_LIST = comma-separated list of CA cert names
#   PORT = creates a dedicated SSL listener on this port (required for server mode)

cat > /tmp/sslparm1.prm << EOF
SSLPARM
    NAME = SSLPARM1,
    ENABLED = E,
    SSLMODE = S,
    VERIFY_OPT = 0,
    CERT_ID = CXSRV,
    CIPHER_LIST = ,
    TLSV1 = Y,
    SSLV3 = Y,
    SSLV2 = N,
    DH_PARAM = '',
    CA_LIST = 'PWCA',
    NETWORK = T,
    ADDRESS = ,
    PORT = 05001,
    TCPIP_HEADER = N,
    MODE = REPLACE
EOF

$TOMPRM input=/tmp/sslparm1.prm 2>/dev/null && \
    echo "   SSLPARM1 configured successfully" || \
    echo "   WARNING: SSLPARM1 configuration may have failed"

# -------------------------------------------------------------------
# Step 5: Configure SSLPARM2 (Client mode - initiate outgoing TLS)
# -------------------------------------------------------------------
echo ""
echo "Step 5: Configuring SSLPARM2 (Client mode)..."

cat > /tmp/sslparm2.prm << EOF
SSLPARM
    NAME = SSLPARM2,
    ENABLED = E,
    SSLMODE = C,
    VERIFY_OPT = 1,
    CERT_ID = CXCLI,
    CIPHER_LIST = ,
    TLSV1 = Y,
    SSLV3 = N,
    SSLV2 = N,
    DH_PARAM = '',
    CA_LIST = 'PWCA',
    NETWORK = ,
    ADDRESS = ,
    PORT = ,
    TCPIP_HEADER = N,
    MODE = REPLACE
EOF

$TOMPRM input=/tmp/sslparm2.prm 2>/dev/null && \
    echo "   SSLPARM2 configured successfully" || \
    echo "   WARNING: SSLPARM2 configuration may have failed"

# Cleanup
rm -f /tmp/sslparm1.prm /tmp/sslparm2.prm

# -------------------------------------------------------------------
# Summary
# -------------------------------------------------------------------
echo ""
echo "==========================================="
echo "  SSL Configuration Complete"
echo "==========================================="
echo ""
echo "Certificates imported:"
echo "  PWCA  - PeSIT Wizard Test CA (for trust chain)"
echo "  CXSRV - CX Server certificate (for incoming TLS)"
echo "  CXCLI - CX Client certificate (for outgoing TLS)"
echo ""
echo "SSLPARM tables configured:"
echo "  SSLPARM1 - Server mode (SSLMODE=S, CERT=CXSRV, PORT=5001)"
echo "  SSLPARM2 - Client mode (SSLMODE=C, CERT=CXCLI)"
echo ""
echo "Listeners:"
echo "  Port 5000 - Plain TCP (standard PeSIT)"
echo "  Port 5001 - TLS/SSL (secure PeSIT via SSLPARM1)"
echo ""
echo "Partner configuration:"
echo "  For incoming TLS: SSLPARM=SSLPARM1"
echo "  For outgoing TLS: SSLPARM=SSLPARM2"
