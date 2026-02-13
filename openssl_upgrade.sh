#!/usr/bin/env bash
# upgrade_custom_openssl_to_3.0.19.sh
# Automates upgrade of custom OpenSSL (used by CrowdStrike Falcon) to 3.0.19
# Run with: sudo bash upgrade_custom_openssl_to_3.0.19.sh

set -euo pipefail

# ──────────────────────────────────────────────────────────────────────────────
# Configuration
# ──────────────────────────────────────────────────────────────────────────────

OPENSSL_VERSION="3.0.19"
DOWNLOAD_URL="https://github.com/openssl/openssl/releases/download/openssl-${OPENSSL_VERSION}/openssl-${OPENSSL_VERSION}.tar.gz"
PREFIX="/usr/local/ssl"
SRC_DIR="/usr/local/src"
BACKUP_FILE="/root/openssl_backup_3.0.16_$(date +%Y-%m-%d).tar.gz"

# Optional: SHA256 from official release page (update if needed)
EXPECTED_SHA256="UPDATE_THIS_WITH_REAL_SHA256_FROM_RELEASE_PAGE"  # e.g. from https://github.com/openssl/openssl/releases/tag/openssl-3.0.19

# ──────────────────────────────────────────────────────────────────────────────
# Helper functions
# ──────────────────────────────────────────────────────────────────────────────

log() { echo "[INFO]  $*" >&2; }
error() { echo "[ERROR] $*" >&2; exit 1; }

check_command() {
    command -v "$1" >/dev/null 2>&1 || error "$1 is required but not installed."
}

# ──────────────────────────────────────────────────────────────────────────────
# Main
# ──────────────────────────────────────────────────────────────────────────────

log "Starting custom OpenSSL upgrade to ${OPENSSL_VERSION}"

# 1. Check prerequisites
check_command wget
check_command tar
check_command make
check_command gcc  # part of build-essential

if ! systemctl is-active falcon-sensor >/dev/null 2>&1; then
    log "Warning: falcon-sensor service not detected or not active."
fi

# 2. Backup current install
log "Creating backup: ${BACKUP_FILE}"
sudo tar -czf "${BACKUP_FILE}" "${PREFIX}" || error "Backup failed"

log "Backup created successfully"

# 3. Download source
cd "${SRC_DIR}" || error "Cannot cd to ${SRC_DIR}"

log "Downloading ${DOWNLOAD_URL}"
wget -O "openssl-${OPENSSL_VERSION}.tar.gz" "${DOWNLOAD_URL}" || error "Download failed"

# Optional: Basic integrity check (uncomment and fill EXPECTED_SHA256)
# computed_sha256=$(sha256sum "openssl-${OPENSSL_VERSION}.tar.gz" | awk '{print $1}')
# if [[ "${computed_sha256}" != "${EXPECTED_SHA256}" ]]; then
#     error "SHA256 mismatch! Expected: ${EXPECTED_SHA256} Got: ${computed_sha256}"
# fi
# log "SHA256 verified"

# 4. Extract & enter dir
tar -xzf "openssl-${OPENSSL_VERSION}.tar.gz" || error "Extraction failed"
cd "openssl-${OPENSSL_VERSION}" || error "Cannot cd into source dir"

# 5. Clean previous build attempts
make clean >/dev/null 2>&1 || true

# 6. Configure
log "Configuring with prefix=${PREFIX}, shared, zlib, rpath"
./config \
    --prefix="${PREFIX}" \
    --openssldir="${PREFIX}" \
    shared zlib \
    -Wl,-rpath,"${PREFIX}/lib64" || error "Configure failed"

# 7. Build
log "Building (this may take a few minutes)..."
make -j$(nproc) || error "Build failed"

# 8. Install
log "Installing (overwrites old files)..."
sudo make install || error "Install failed"

# 9. Update linker cache
log "Running ldconfig..."
sudo ldconfig

# 10. Verify
log "Verifying new version:"
NEW_VERSION=$("${PREFIX}/bin/openssl" version -a)
echo "${NEW_VERSION}"

if echo "${NEW_VERSION}" | grep -q "${OPENSSL_VERSION}"; then
    log "Upgrade successful - detected OpenSSL ${OPENSSL_VERSION}"
else
    error "Version check failed - did not detect ${OPENSSL_VERSION}"
fi

# 11. Restart dependent service
log "Restarting falcon-sensor to load new libraries..."
sudo systemctl restart falcon-sensor || log "Warning: restart failed or service not found"

sleep 5
sudo systemctl status falcon-sensor --no-pager | head -n 15 || true

# 12. Optional cleanup (uncomment if you want to remove old source)
# log "Cleaning up old source directories..."
# cd "${SRC_DIR}"
# rm -rf "openssl-3.0."*  # removes 3.0.16 and 3.0.19 dirs/tars — be careful!

log "Upgrade process completed."
log "Next steps:"
log "  - Re-run your vulnerability scan (Tenable plugin 266297 should clear)"
log "  - Monitor CrowdStrike console for sensor health"
log "  - If issues: restore from backup with 'sudo tar -xzf ${BACKUP_FILE} -C /'"
log "  - To make openssl easier: add 'export PATH=\"${PREFIX}/bin:\$PATH\"' to /etc/profile.d/"

exit 0
