#!/usr/bin/env bash
set -euo pipefail
repo_root=$(cd "$(dirname "$0")/../.." && pwd)
env_file=${LMS_STAGING_ENV_FILE:-$repo_root/deploy/.env.staging}
compose=(docker compose --project-name lms-staging --env-file "$env_file" -f "$repo_root/deploy/compose.yml" -f "$repo_root/deploy/compose.staging.yml")
case "${1:-help}" in
 prepare) node "$repo_root/deploy/scripts/prepare-staging.mjs" "$env_file" ;;
 config) node "$repo_root/deploy/scripts/check-config.mjs" "$env_file"; "${compose[@]}" config --quiet ;;
 up) node "$repo_root/deploy/scripts/check-config.mjs" "$env_file"; "${compose[@]}" config --quiet; "${compose[@]}" up -d --build ;;
 status) "${compose[@]}" ps ;;
 check) node "$repo_root/deploy/scripts/check-staging.mjs" ;;
 down) "${compose[@]}" down ;;
 *) echo 'Kullanım: bash deploy/scripts/staging.sh prepare|config|up|status|check|down'; exit 1 ;;
esac
