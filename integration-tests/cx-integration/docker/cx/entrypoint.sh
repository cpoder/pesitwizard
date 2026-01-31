#!/bin/bash
# Connect:Express container entrypoint

set -e

# Fix permissions on mounted volumes (running as root initially)
mkdir -p /tmp/cx-send /tmp/cx-received
chown -R cxuser:cxuser /tmp/cx-send /tmp/cx-received

# Run the main script as cxuser
exec su -s /bin/bash -c '/cx-main.sh' cxuser
