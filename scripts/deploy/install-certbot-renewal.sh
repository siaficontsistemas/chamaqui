#!/usr/bin/env bash

set -euo pipefail

HOOK_SOURCE="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/certbot-renewal-hook.sh}"
HOOK_TARGET="/etc/letsencrypt/renewal-hooks/deploy/chamaqui-nginx-reload.sh"

if [ "$(id -u)" -ne 0 ]; then
  echo "Execute este script com sudo." >&2
  exit 1
fi

if ! command -v certbot >/dev/null 2>&1; then
  echo "Certbot não está instalado nesta máquina." >&2
  exit 1
fi

if ! command -v systemctl >/dev/null 2>&1; then
  echo "systemctl não está disponível; configure o agendador do Certbot manualmente." >&2
  exit 1
fi

install -d -m 0755 "$(dirname "${HOOK_TARGET}")"
install -m 0755 "${HOOK_SOURCE}" "${HOOK_TARGET}"

if systemctl cat certbot.timer >/dev/null 2>&1; then
  systemctl enable --now certbot.timer
else
  echo "A unidade certbot.timer não foi encontrada; verifique a instalação do pacote Certbot." >&2
  exit 1
fi

echo "Agendador do Certbot:"
systemctl is-enabled certbot.timer
systemctl is-active certbot.timer
systemctl list-timers certbot.timer --no-pager

echo "Validando a renovação sem alterar o certificado atual..."
certbot renew --dry-run

echo "Renovação automática configurada."
