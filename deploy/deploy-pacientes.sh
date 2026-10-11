#!/usr/bin/env bash
set -euo pipefail
: "${IMAGE_TAG:?Falta IMAGE_TAG}"
: "${DEMO_GATEWAY_URL:?Falta DEMO_GATEWAY_URL}"
: "${DEMO_PATIENT_ID:?Falta DEMO_PATIENT_ID}"
[[ "$IMAGE_TAG" =~ ^[0-9a-f]{40}$ ]] || { echo 'SHA invalido'; exit 1; }
[[ "$DEMO_PATIENT_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || { echo 'ID de prueba invalido'; exit 1; }
# Ambiente persistente previamente preparado; .env no se copia ni guarda en Git.
test -f /opt/hospital-demo/.env
docker network inspect hospital >/dev/null
docker load -i approved-image/pacientes-image.tar
compose=(docker compose --env-file /opt/hospital-demo/.env -f compose.yml)
"${compose[@]}" up -d --no-deps pacientes
container_id=$("${compose[@]}" ps -q pacientes)
test -n "$container_id"
[[ $(docker inspect --format '{{.Config.Image}}' "$container_id") == "hospital-pacientes:${IMAGE_TAG}" ]]
container_ip=$(docker inspect --format '{{(index .NetworkSettings.Networks "hospital").IPAddress}}' "$container_id")
test -n "$container_ip"
ready=false
for attempt in {1..24}; do
  if curl --fail --silent --show-error --max-time 5 "http://${container_ip}:8081/actuator/health" > health.json; then
    if python3 -c 'import json; assert json.load(open("health.json"))["status"] == "UP"'; then ready=true; break; fi
  fi
  sleep 5
done
[[ "$ready" == true ]] || { echo 'El contenedor no alcanzo estado UP'; exit 1; }
ready=false
for attempt in {1..24}; do
  if curl --fail --silent --show-error --max-time 5 "${DEMO_GATEWAY_URL%/}/api/pacientes/${DEMO_PATIENT_ID}" > patient.json; then
    if python3 - <<'PY'
import json,os
assert json.load(open('patient.json'))['id']==os.environ['DEMO_PATIENT_ID']
print('Endpoint verificado por Gateway')
PY
    then ready=true; break; fi
  fi
  sleep 5
done
[[ "$ready" == true ]] || { echo 'Fallo la comprobacion por Gateway'; exit 1; }
rm -f health.json patient.json
printf 'Despliegue verificado para commit %s\n' "$IMAGE_TAG"
