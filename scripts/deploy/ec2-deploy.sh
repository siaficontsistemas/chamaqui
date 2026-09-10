#!/usr/bin/env bash

set -euo pipefail

RELEASE_BUNDLE_PATH="${1:?Informe o caminho do bundle de release.}"
DEPLOY_ROOT="${DEPLOY_ROOT:-/home/ec2-user/deploy/chamaqui}"
BACKEND_JAR_PATH="${BACKEND_JAR_PATH:-/home/ec2-user/app.jar}"
BAILEYS_APP_DIR="${BAILEYS_APP_DIR:-/home/ec2-user/baileys-service}"
PM2_BACKEND_APP_NAME="${PM2_BACKEND_APP_NAME:-chamaqui-backend}"
PM2_BAILEYS_APP_NAME="${PM2_BAILEYS_APP_NAME:-chamaqui-baileys}"
RESTART_BAILEYS="${RESTART_BAILEYS:-true}"
BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-/home/ec2-user/chamaqui-backend.env}"
DATASOURCE_ENV_FILE="${DATASOURCE_ENV_FILE:-}"

if [ -f "${BACKEND_ENV_FILE}" ]; then
  set -a
  # shellcheck disable=SC1090
  source "${BACKEND_ENV_FILE}"
  set +a
fi

# Apply only the datasource values supplied by the release workflow after
# loading the existing file, preserving all other EC2 configuration (S3,
# mail, WhatsApp, push notifications, and application secrets).
if [ -n "${DATASOURCE_ENV_FILE}" ] && [ -f "${DATASOURCE_ENV_FILE}" ]; then
  set -a
  # shellcheck disable=SC1090
  source "${DATASOURCE_ENV_FILE}"
  set +a
else
  echo "Arquivo temporário de datasource não encontrado." >&2
  exit 1
fi

EXPECTED_DATASOURCE_URL="jdbc:postgresql://chamaqui-postgres-new.cy9g64kikfr9.us-east-1.rds.amazonaws.com:5432/helpdesk?sslmode=require"
if [ "${SPRING_DATASOURCE_URL:-}" != "${EXPECTED_DATASOURCE_URL}" ]; then
  echo "SPRING_DATASOURCE_URL inválida para produção." >&2
  exit 1
fi
if [ "${SPRING_DATASOURCE_USERNAME:-}" != "helpdesk" ]; then
  echo "SPRING_DATASOURCE_USERNAME inválido para produção." >&2
  exit 1
fi
if [ -z "${SPRING_DATASOURCE_PASSWORD:-}" ]; then
  echo "SPRING_DATASOURCE_PASSWORD não definida." >&2
  exit 1
fi

TIMESTAMP="$(date +%Y%m%d%H%M%S)"

mkdir -p "${DEPLOY_ROOT}" "$(dirname "${BACKEND_JAR_PATH}")" "${BAILEYS_APP_DIR}"
WORK_DIR="$(mktemp -d "${DEPLOY_ROOT%/}/release-${TIMESTAMP}-XXXX")"

cleanup() {
  rm -rf "${WORK_DIR:-}"
  case "${DATASOURCE_ENV_FILE}" in
    /tmp/chamaqui-datasource-*.env) rm -f "${DATASOURCE_ENV_FILE}" ;;
  esac
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

BACKEND_PM2_ID="$(pm2 jlist | node -e '
let input = "";
process.stdin.on("data", chunk => { input += chunk; });
process.stdin.on("end", () => {
  const apps = JSON.parse(input);
  const app = apps.find(item => item.name === process.env.PM2_BACKEND_APP_NAME);
  if (!app) process.exit(1);
  process.stdout.write(String(app.pm_id));
});
')"

# Validate the environment loaded by PM2 without printing any secret value.
pm2 env "${BACKEND_PM2_ID}" | awk '
  /SPRING_DATASOURCE_URL/ && /chamaqui-postgres-new\.cy9g64kikfr9\.us-east-1\.rds\.amazonaws\.com/ { url = 1 }
  /SPRING_DATASOURCE_USERNAME/ && /helpdesk/ { username = 1 }
  /SPRING_DATASOURCE_PASSWORD/ && $0 !~ /(^|[=:])[[:space:]]*$/ { password = 1 }
  END { exit !(url && username && password) }
'

if [ "${RESTART_BAILEYS}" = "true" ]; then
  pm2 restart "${PM2_BAILEYS_APP_NAME}" --update-env
fi

pm2 save

rm -f "${RELEASE_BUNDLE_PATH}"
