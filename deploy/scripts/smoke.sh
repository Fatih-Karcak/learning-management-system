#!/usr/bin/env bash
set -euo pipefail
base=${1:?HTTPS uygulama adresini belirtin}
[[ "$base" == https://* ]] || { echo 'HTTPS adresi gerekli'; exit 1; }
curl --fail --silent --show-error "$base/" >/dev/null
curl --fail --silent --show-error "$base/api/v1/courses?size=1" | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["success"] and "content" in d["data"]'
for path in /api/v1/internal/users/test /actuator/prometheus; do
 status=$(curl --silent --output /dev/null --write-out '%{http_code}' "$base$path")
 [[ "$status" == 404 ]] || { echo "Özel endpoint dışarıya açık: $path"; exit 1; }
done
echo 'HTTPS, katalog ve özel endpoint koruması geçti.'
