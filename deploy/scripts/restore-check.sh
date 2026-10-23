#!/usr/bin/env bash
set -euo pipefail
repo_root=$(cd "$(dirname "$0")/../.." && pwd)
dump=${1:?Kontrol edilecek dump dosyasını belirtin}
[[ -s "$dump" ]] || { echo 'Yedek dosyası bulunamadı'; exit 1; }
# Uses a disposable DB only. Never restores over a live application database.
restore_db="lms_restore_check_$(date +%s)_$$"
compose_file=${LMS_COMPOSE_FILE:-$repo_root/deploy/compose.yml}
env_file=${LMS_ENV_FILE:-$repo_root/deploy/.env}
compose=(docker compose --env-file "$env_file" -f "$compose_file")
cleanup(){ "${compose[@]}" exec -T postgres dropdb -U lms_user --if-exists "$restore_db" >/dev/null; }
trap cleanup EXIT
"${compose[@]}" exec -T postgres createdb -U lms_user "$restore_db"
"${compose[@]}" exec -T postgres pg_restore --exit-on-error --no-owner -U lms_user -d "$restore_db" < "$dump"
"${compose[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U lms_user -d "$restore_db" <<'SQL'
DO $check$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='flyway_schema_history')
 THEN RAISE EXCEPTION 'Migration history is missing'; END IF;
END $check$;
SQL

echo 'Yedek geçici veritabanına hatasız geri yüklendi.'
