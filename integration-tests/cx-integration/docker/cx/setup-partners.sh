#!/bin/bash
# Configure Connect:Express partners and files for PeSIT Wizard integration tests

set -e

# Source CX profile
. $TOM_DIR/profile

# SSL/TLS configuration (set via environment variables)
# CX_SSL_ENABLED=true to enable TLS
# CX_SSL_SSLPARM=SSLPARM1 for server mode, SSLPARM2 for client mode
# For SSL partners: LINK=S (instead of T for TCP), SSLPARM=<sslparm_name>
SSL_ENABLED="${CX_SSL_ENABLED:-false}"
SSL_SSLPARM_SERVER="${CX_SSL_SSLPARM_SERVER:-SSLPARM1}"
SSL_SSLPARM_CLIENT="${CX_SSL_SSLPARM_CLIENT:-SSLPARM2}"

if [ "$SSL_ENABLED" = "true" ]; then
    echo "SSL/TLS mode enabled"
    echo "  Server SSLPARM: $SSL_SSLPARM_SERVER"
    echo "  Client SSLPARM: $SSL_SSLPARM_CLIENT"

    # Run SSL setup if not already done
    if [ -x /setup-ssl.sh ]; then
        /setup-ssl.sh
    fi
fi

# Add MAXSES to SYSIN if not already present
# MAXSES controls max simultaneous PeSIT sessions (default is very low)
if ! grep -q "^MAXSES=" $TOM_DIR/config/sysin; then
    echo "Adding MAXSES=020 to SYSIN file..."
    # Add MAXSES after STRFRN line
    sed -i '/^STRFRN=/a MAXSES=020                          3 MAX PESIT SESSIONS' $TOM_DIR/config/sysin
fi

# Resolve PW server IP address
PW_SERVER_IP=$(getent hosts pw-server | awk '{print $1}' | head -1)
if [ -z "$PW_SERVER_IP" ]; then
    echo "ERROR: Could not resolve pw-server hostname"
    exit 1
fi
echo "Resolved pw-server to IP: $PW_SERVER_IP"

echo "Configuring PWSERVER partner (for CX to connect OUT to PW server)..."
if [ "$SSL_ENABLED" = "true" ]; then
    # SSL partner: Keep LINK=T (TCP), add SSLPARM to enable SSL on the connection
    # The SSLPARM parameter references the SSL parameter table which contains cipher/cert config
    $TOM_DIR/config/tom_prm PARTNER \
        "NAME=PWSERVER,PASSWD=,STATE=E,TYPE=O,PROT=3,SESSION=1,MAXSES=10,MAXSESIN=05,MAXSESOUT=05,LINK=T,HOST=,TCPADDR=$PW_SERVER_IP,TCPPORT=05001,DPCSID=PWSRV01,DPCPSW=,NRETRY=3,INTSESST=30,INTTRANST=60,SSLPARM=$SSL_SSLPARM_CLIENT,MODE=REPLACE"
    echo "   SSL partner configured (SSLPARM=$SSL_SSLPARM_CLIENT)"
else
    $TOM_DIR/config/tom_prm PARTNER \
        "NAME=PWSERVER,PASSWD=,STATE=E,TYPE=O,PROT=3,SESSION=1,MAXSES=10,MAXSESIN=05,MAXSESOUT=05,LINK=T,HOST=,TCPADDR=$PW_SERVER_IP,TCPPORT=05001,DPCSID=PWSRV01,DPCPSW=,NRETRY=3,INTSESST=30,INTTRANST=60,MODE=REPLACE"
fi

echo "Configuring PWSRV01 partner (for PW client to connect IN to CX)..."
# Partner NAME must match PI_03 (demandeur) sent by the client in CONNECT message
if [ "$SSL_ENABLED" = "true" ]; then
    # SSL partner: Keep LINK=T (TCP), add SSLPARM for incoming SSL connections
    $TOM_DIR/config/tom_prm PARTNER \
        "NAME=PWSRV01,PASSWD=,STATE=E,TYPE=O,PROT=3,SESSION=1,MAXSES=10,MAXSESIN=05,MAXSESOUT=05,LINK=T,HOST=,NRETRY=3,INTSESST=30,INTTRANST=60,SSLPARM=$SSL_SSLPARM_SERVER,MODE=REPLACE"
    echo "   SSL partner configured (SSLPARM=$SSL_SSLPARM_SERVER)"
else
    $TOM_DIR/config/tom_prm PARTNER \
        "NAME=PWSRV01,PASSWD=,STATE=E,TYPE=O,PROT=3,SESSION=1,MAXSES=10,MAXSESIN=05,MAXSESOUT=05,LINK=T,HOST=,NRETRY=3,INTSESST=30,INTTRANST=60,MODE=REPLACE"
fi

echo "Configuring PWSEND file (for sending to PeSIT Wizard)..."
$TOM_DIR/config/tom_prm FILE \
    'NAME=PWSEND,STATE=E,DIRECTION=T,RPART=PWSERVER,TPART=$$ALL$$,FORMAT=BU,LREC=04096,PRIORITY=2,DEFTYPE=D,FICPARAMS=N,SPACE=N,ALLOCATION=0,DSN=/tmp/cx-send,FA=N,MODE=REPLACE'

echo "Configuring PWRECV file (for receiving from PeSIT Wizard client)..."
# DIRECTION=R for receiving files
# TPART=PWSRV01 authorizes PWSRV01 as transmitter (the client sending to CX)
# RPART=$$ALL$$ allows any receiver
# DSN uses &REQNUMB keyword to generate unique filename for each received file
# FORMAT=** accepts any record format from the sender
$TOM_DIR/config/tom_prm FILE \
    'NAME=PWRECV,STATE=E,DIRECTION=R,RPART=$$ALL$$,TPART=PWSRV01,FORMAT=**,LREC=04096,PRIORITY=2,DEFTYPE=D,FICPARAMS=N,SPACE=N,ALLOCATION=0,DSN=/tmp/cx-received/&REQNUMB,FA=N,MODE=REPLACE'

# Create test directories
mkdir -p /tmp/cx-send /tmp/cx-received

echo "Connect:Express configuration complete."
