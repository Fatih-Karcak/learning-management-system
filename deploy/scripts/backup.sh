#!/usr/bin/env bash
set -euo pipefail
umask 077
repo_root=$(cd "$(dirname "$0")/../.." && pwd)
compose_file=${LMS_COMPOSE_FILE:-$repo_root/deploy/compose.yml}
env_file=${LMS_ENV_FILE:-$repo_root/deploy/.env}
compose=(docker compose --env-file "$env_file" -f "$compose_file")
backup_dir=${1:?Yedek dizinini belirtin}
mkdir -p "$backup_dir"
backup_dir=$(cd "$backup_dir" && pwd)
stamp=$(date -u +%Y%m%dT%H%M%SZ)
for db in lms_users lms_courses lms_enrollments lms_payments lms_content lms_notifications; do
 "${compose[@]}" exec -T postgres pg_dump -U lms_user -Fc "$db" > "$backup_dir/$stamp-$db.dump.part"
 mv "$backup_dir/$stamp-$db.dump.part" "$backup_dir/$stamp-$db.dump"
done
(cd "$backup_dir" && sha256sum "$stamp"-*.dump > "$stamp.sha256")
# Back up S3 separately using versioning/replication to an independent account or provider.
# Dumps contain personal data. Encrypt and copy off the VPS; apply your documented retention period.
echo "Altı veritabanı yedeği tamamlandı."
