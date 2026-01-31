# Connect:Express Docker image for integration testing
FROM ubuntu:22.04

# Install required packages
RUN apt-get update && apt-get install -y \
    ed \
    libc6 \
    bash \
    dnsutils \
    net-tools \
    procps \
    netcat-openbsd \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Set bash as default shell
SHELL ["/bin/bash", "-c"]

# Create CX user and directories
RUN useradd -m -s /bin/bash cxuser

# Copy CX installation files
COPY --chown=cxuser:cxuser CX/ /opt/cx-install/

WORKDIR /opt/cx-install

# Install CX silently as cxuser
# -s: silent mode
# -d: installation directory (max 4 chars in last path component)
# -e: edition (snp = Standard Non-Production)
USER cxuser
RUN ./install.sh -s -d /home/cxuser/cx -e snp

# Switch back to root for copying scripts
USER root

# Copy configuration scripts
COPY entrypoint.sh /entrypoint.sh
COPY --chown=cxuser:cxuser cx-main.sh /cx-main.sh
COPY --chown=cxuser:cxuser setup-partners.sh /setup-partners.sh
COPY --chown=cxuser:cxuser run-transfer.sh /run-transfer.sh
RUN chmod +x /entrypoint.sh /cx-main.sh /setup-partners.sh /run-transfer.sh

# Keep root user for entrypoint (it will switch to cxuser after fixing permissions)
WORKDIR /home/cxuser

# Set environment
ENV TOM_DIR=/home/cxuser/cx
ENV PATH="${TOM_DIR}/strf:${TOM_DIR}/itom:${PATH}"
ENV LD_LIBRARY_PATH="${TOM_DIR}/lib:${LD_LIBRARY_PATH}"

# Expose PeSIT port
EXPOSE 5000

# Health check - test if TCP port 5000 is listening (more reliable than pgrep)
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=3 \
    CMD bash -c "netstat -tuln | grep -q ':5000 ' || exit 1"

ENTRYPOINT ["/bin/bash", "/entrypoint.sh"]
