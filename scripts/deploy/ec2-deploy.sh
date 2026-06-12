#!/usr/bin/env bash

set -euo pipefail

RELEASE_BUNDLE_PATH="${1:?Informe o caminho do bundle de release.}"
DEPLOY_ROOT="${DEPLOY_ROOT:-/home/ec2-user/deploy/chamaqui}"
BACKEND_JAR_PATH="${BACKEND_JAR_PATH:-/home/ec2-user/app.jar}"
FRONTEND_WEB_ROOT="${FRONTEND_WEB_ROOT:-/usr/share/nginx/html}"
BAILEYS_APP_DIR="${BAILEYS_APP_DIR:-/home/ec2-user/baileys-service}"
PM2_BACKEND_APP_NAME="${PM2_BACKEND_APP_NAME:-chamaqui-backend}"
PM2_BAILEYS_APP_NAME="${PM2_BAILEYS_APP_NAME:-chamaqui-baileys}"
RESTART_BAILEYS="${RESTART_BAILEYS:-true}"
NGINX_RELOAD_CMD="${NGINX_RELOAD_CMD:-sudo systemctl reload nginx}"

TIMESTAMP="$(date +%Y%m%d%H%M%S)"
BACKUP_DIR="${DEPLOY_ROOT%/}/backups/${TIMESTAMP}"

mkdir -p "${DEPLOY_ROOT}" "${BACKUP_DIR}" "$(dirname "${BACKEND_JAR_PATH}")" "${FRONTEND_WEB_ROOT}" "${BAILEYS_APP_DIR}"
WORK_DIR="$(mktemp -d "${DEPLOY_ROOT%/}/release-${TIMESTAMP}-XXXX")"

cleanup() {
  rm -rf "${WORK_DIR:-}"
}

sync_directory() {
  local source_dir="$1"
  local target_dir="$2"
  local use_sudo="false"

  if [ ! -w "${target_dir}" ]; then
    use_sudo="true"
  fi

  if [ "${use_sudo}" = "true" ]; then
    sudo mkdir -p "${target_dir}"
  else
    mkdir -p "${target_dir}"
  fi

  if command -v rsync >/dev/null 2>&1; then
    if [ "${use_sudo}" = "true" ]; then
      sudo rsync -a --delete "${source_dir}/" "${target_dir}/"
    else
      rsync -a --delete "${source_dir}/" "${target_dir}/"
    fi
    return
  fi

  if [ "${use_sudo}" = "true" ]; then
    sudo find "${target_dir}" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
    sudo cp -a "${source_dir}/." "${target_dir}/"
  else
    find "${target_dir}" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
    cp -a "${source_dir}/." "${target_dir}/"
  fi
}

trap cleanup EXIT

tar -xzf "${RELEASE_BUNDLE_PATH}" -C "${WORK_DIR}"

if [ -f "${BACKEND_JAR_PATH}" ]; then
  cp "${BACKEND_JAR_PATH}" "${BACKUP_DIR}/app.jar"
fi

install -m 0644 "${WORK_DIR}/backend/app.jar" "${BACKEND_JAR_PATH}"

if [ -d "${WORK_DIR}/frontend/dist" ]; then
  sync_directory "${WORK_DIR}/frontend/dist" "${FRONTEND_WEB_ROOT}"
fi

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

if [ -n "${NGINX_RELOAD_CMD}" ]; then
  bash -lc "${NGINX_RELOAD_CMD}"
fi

rm -f "${RELEASE_BUNDLE_PATH}"
