#!/usr/bin/env bash
set -euo pipefail
for module in pacientes citas atenciones pagos notificaciones auditoria; do
  variable="${module^^}_DB_PASSWORD"
  password="${!variable:?Falta password de base de datos}"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres --set=role="$module" --set=database="$module" --set=password="$password" <<'SQL'
CREATE ROLE :"role" LOGIN PASSWORD :'password';
CREATE DATABASE :"database" OWNER :"role";
REVOKE CONNECT ON DATABASE :"database" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"database" TO :"role";
SQL
done
