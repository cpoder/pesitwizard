#!/bin/bash
# Configure Connect:Express partners and files for PeSIT Wizard integration tests

set -e

# Source CX profile
. $TOM_DIR/profile

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
$TOM_DIR/config/tom_prm PARTNER \
    "NAME=PWSERVER,PASSWD=,STATE=E,TYPE=O,PROT=3,SESSION=1,MAXSES=10,MAXSESIN=05,MAXSESOUT=05,LINK=T,HOST=,TCPADDR=$PW_SERVER_IP,TCPPORT=05001,DPCSID=PWSRV01,DPCPSW=,NRETRY=3,INTSESST=30,INTTRANST=60,MODE=REPLACE"

echo "Configuring PWSRV01 partner (for PW client to connect IN to CX)..."
# Partner NAME must match PI_03 (demandeur) sent by the client in CONNECT message
$TOM_DIR/config/tom_prm PARTNER \
    "NAME=PWSRV01,PASSWD=,STATE=E,TYPE=O,PROT=3,SESSION=1,MAXSES=10,MAXSESIN=05,MAXSESOUT=05,LINK=T,HOST=,NRETRY=3,INTSESST=30,INTTRANST=60,MODE=REPLACE"

echo "Configuring PWSEND file (for sending to PeSIT Wizard)..."
$TOM_DIR/config/tom_prm FILE \
    'NAME=PWSEND,STATE=E,DIRECTION=T,RPART=PWSERVER,TPART=$$ALL$$,FORMAT=BF,LREC=04096,PRIORITY=2,DEFTYPE=D,FICPARAMS=N,SPACE=N,ALLOCATION=0,DSN=/tmp/cx-send,FA=N,MODE=REPLACE'

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
