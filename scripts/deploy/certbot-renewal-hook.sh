#!/usr/bin/env bash

set -euo pipefail

# Certbot calls deploy hooks only after a certificate was successfully renewed.
# Validate the Nginx configuration before reloading it so a bad config does not
# interrupt the currently running API proxy.
nginx -t
systemctl reload nginx
