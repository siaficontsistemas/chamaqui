#!/usr/bin/env bash

set -euo pipefail

RELEASE_BUNDLE_PATH="${1:?Informe o caminho do bundle de release.}"
DEPLOY_ROOT="${DEPLOY_ROOT:-/home/ec2-user/deploy/chamaqui}"
BACKEND_JAR_PATH="${BACKEND_JAR_PATH:-/home/ec2-user/app.jar}"
BAILEYS_APP_DIR="${BAILEYS_APP_DIR:-/home/ec2-user/baileys-service}"
PM2_BACKEND_APP_NAME="${PM2_BACKEND_APP_NAME:-chamaqui-backend}"
PM2_BAILEYS_APP_NAME="${PM2_BAILEYS_APP_NAME:-chamaqui-baileys}"
RESTART_BAILEYS="${RESTART_BAILEYS:-true}"

TIMESTAMP="$(date +%Y%m%d%H%M%S)"

mkdir -p "${DEPLOY_ROOT}" "$(dirname "${BACKEND_JAR_PATH}")" "${BAILEYS_APP_DIR}"
WORK_DIR="$(mktemp -d "${DEPLOY_ROOT%/}/release-${TIMESTAMP}-XXXX")"

cleanup() {
  rm -rf "${WORK_DIR:-}"
}

trap cleanup EXIT

tar -xzf "${RELEASE_BUNDLE_PATH}" -C "${WORK_DIR}"

install -m 0644 "${WORK_DIR}/backend/app.jar" "${BACKEND_JAR_PATH}"

if [ -f "${WORK_DIR}/baileys-service/package.json" ]; then
  install -m 0644 "${WORK_DIR}/baileys-service/package.json" "${BAILEYS_APP_DIR}/package.json"

  if [ -f "${WORK_DIR}/baileys-service/package-lock.json" ]; then
    install -m 0644 "${WORK_DIR}/baileys-service/package-lock.json" "${BAILEYS_APP_DIR}/package-lock.json"
  fi

  rm -rf "${BAILEYS_APP_DIR}/src"
  cp -a "${WORK_DIR}/baileys-service/src" "${BAILEYS_APP_DIR}/src"

  (
    cd "${BAILEYS_APP_DIR}"
    npm ci --omit=dev
  )
fi

pm2 restart "${PM2_BACKEND_APP_NAME}" --update-env

if [ "${RESTART_BAILEYS}" = "true" ]; then
  pm2 restart "${PM2_BAILEYS_APP_NAME}" --update-env
fi

pm2 save

rm -f "${RELEASE_BUNDLE_PATH}"
