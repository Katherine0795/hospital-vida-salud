# Hospital Vida Salud — Evaluación T2

Solución académica de microservicios para pacientes, citas, atenciones y pagos ficticios, con notificaciones y auditoría por RabbitMQ. Incluye código fuente, persistencia SQL/R2DBC, pruebas, Docker Compose, colección Postman y workflows de CI/CD.

## Requisitos

- Java **21**, Maven Wrapper incluido (Maven 3.9.9), conexión para descargar dependencias.
- Docker Engine/Desktop con plugin Compose; Docker Desktop en modo **Linux containers** en Windows.
- Python 3 para generar configuración local y ejecutar la prueba de extremo a extremo.
- Puertos locales 8080, 8761, 5432, 5672 y 15672 disponibles. Se recomienda al menos 4 GB de RAM para los contenedores.
- Spring Boot 3.5.16 y Spring Cloud 2025.0.3 fijados en el POM. `common` solo contiene contratos e infraestructura: no comparte entidades ni tablas de los dominios.

## Inicio desde cero en Windows PowerShell

Desde la carpeta que contiene `pom.xml`:

```powershell
java -version
.\mvnw.cmd -B -ntp verify
python scripts/create_env.py
docker compose up -d --build
python scripts/smoke.py --docker-checks
```

Si Python se invoca con `py`, sustituir `python` por `py`. La primera compilación y descarga de imágenes puede tardar varios minutos. La prueba espera el registro de servicios y termina con un resumen de comprobaciones.

En Linux/macOS:

```bash
chmod +x mvnw infra/init-databases.sh deploy/deploy-pacientes.sh
./mvnw -B -ntp verify
python3 scripts/create_env.py
docker compose up -d --build
python3 scripts/smoke.py --docker-checks
```

El script genera passwords aleatorios en `.env` y no sobrescribe un archivo existente. `.env` está excluido de Git. No cambiar esas passwords si los volúmenes de PostgreSQL/RabbitMQ ya están inicializados sin actualizar también los usuarios reales.

- Gateway: http://localhost:8080
- Eureka: http://localhost:8761
- RabbitMQ Management: http://localhost:15672 (usuario `RABBIT_USER` y password `RABBIT_PASSWORD` del archivo local `.env`).
- Logs: `docker compose logs -f citas notificaciones auditoria`.
- Apagar conservando datos: `docker compose down`.
- Volver a iniciar: `docker compose up -d`. No utilizar `down --volumes` si se quieren conservar los datos.

El usuario administrativo de PostgreSQL es `hospital_admin`. El contenedor crea seis bases y seis usuarios separados; cada servicio utiliza el usuario de su dominio. El schema de cada servicio está en `MODULO/src/main/resources/schema.sql` y se inicializa mediante Spring SQL con R2DBC. El montaje de inicialización solo crea usuarios y bases al arrancar un volumen nuevo. No hay claves foráneas entre bases; sí hay una relación local entre historial y cita.

## Arquitectura y decisiones

| Módulo | Puerto interno | Datos y responsabilidad |
|---|---:|---|
| discovery | 8761 | Registro Eureka; no transporta el tráfico de negocio |
| gateway | 8080 | Rutas `lb://nombre` para los seis dominios |
| pacientes | 8081 | Pacientes ficticios y documento único |
| citas | 8082 | Agenda, estados, historial local y outbox |
| atenciones | 8083 | Atención administrativa y cierre reintentable de cita |
| pagos | 8084 | Pagos simulados y operación única |
| notificaciones | 8085 | Avisos simulados persistidos a partir de eventos |
| auditoria | 8086 | Eventos persistidos con estado anterior/nuevo y origen |

Los puertos de negocio no se publican en el host. Los clientes usan Gateway. Todas las operaciones HTTP/SQL usan WebFlux y Spring Data R2DBC `DatabaseClient`, con repositorios reactivos propios anotados `@Repository`. No se utiliza JDBC ni JPA. `Mono` se usa para un recurso y `Flux` para listados (máximo 100 resultados en esta demostración). La adaptación de RabbitTemplate bloqueante se ejecuta únicamente en `boundedElastic` dentro del publicador outbox; ese adaptador no se presenta como completamente reactivo.

Las consultas entre Citas/Pacientes, Pagos/Pacientes y Atenciones/Citas usan WebClient con balanceo/descubrimiento y timeout de dos segundos. Citas verifica que la referencia de atención exista antes de marcar ATENDIDA; esa referencia se consulta por el API de Atenciones. Referencia inexistente devuelve 404; dependencia caída 503; timeout 504; referencia o estado incompatible 409.

### Estados y reglas

- Paciente: ACTIVO/INACTIVO. Documento único `(tipo_documento, numero_documento)`. DNI y OTRO son tipos académicos; no usar datos reales. La fecha de nacimiento debe ser pasada; correo y campos obligatorios se validan.
- Agenda: instantes futuros y bloques de 30 minutos en America/Lima. Dos citas no CANCELADAS/AUSENTES del mismo profesional o paciente no pueden ocupar el mismo bloque. PostgreSQL aplica índices únicos parciales, incluso ante solicitudes concurrentes.
- Reprogramación: solo PENDIENTE/CONFIRMADA; nueva fecha futura y profesional, con restricciones de agenda y evento CitaReprogramada. El historial local conserva la agenda anterior y nueva.
- Cita: PENDIENTE → CONFIRMADA/CANCELADA; CONFIRMADA → ATENDIDA/CANCELADA/AUSENTE. Ningún estado final se reabre. Versionado optimista evita sobrescribir un cambio concurrente.
- Atención: PENDIENTE_CIERRE → REGISTRADA. Una por cita; solo se crea inicialmente sobre una cita confirmada. La creación y el cierre cruzan bases: no se promete una transacción distribuida. Repetir POST de la misma cita reintenta el cierre con el mismo ID, sin duplicar. Si otro proceso cancela la cita durante el cierre, el registro queda PENDIENTE_CIERRE y requiere revisión administrativa; esta limitación se documenta.
- Pago: monto positivo, máximo dos decimales, medio EFECTIVO/TARJETA/TRANSFERENCIA y operación única. Todos los pagos son ficticios; no se integra un proveedor de cobros.
- 400: entrada inválida; 404: recurso ausente; 409: duplicado/conflicto; 503/504: dependencia no disponible/timeout. Los mensajes no exponen SQL ni passwords.

### Eventos de Citas

Exchange durable topic `hospital.events`, routing keys `cita.registrada.v1`, `cita.confirmada.v1`, `cita.cancelada.v1`, `cita.atendida.v1`, `cita.ausente.v1`, `cita.reprogramada.v1`. Colas durables `notificaciones.cita.v1` y `auditoria.cita.v1` enlazadas a `cita.*.v1`. Exchange de errores `hospital.errors`, con colas `notificaciones.errores.v1` y `auditoria.errores.v1`.

La creación o cambio de estado guarda cita, historial y outbox en una transacción R2DBC. Un publicador programado drena hasta 50 eventos cada dos segundos, espera confirmación del broker y solo después marca `sent_at`. Si el broker falla, el evento continúa pendiente. La topología de ambas colas se declara desde la configuración compartida antes de publicar; así se conservan eventos aunque los consumidores aún no hayan arrancado.

Cada evento contiene `eventId`, `eventType`, `version=1`, `occurredAt`, `citaId`, `pacienteId`, `estadoAnterior`, `estadoNuevo` y `origen`. Los consumidores devuelven `Mono<Void>`; el contenedor AMQP confirma después de completar la persistencia. `event_id` es clave primaria: redelivery no duplica filas. Los errores de acceso a datos se reintentan dos veces y los mensajes rechazados van a la cola de errores. La entrega es al menos una vez, con consistencia eventual. No hay correos ni mensajes reales.

## API y Postman

Importar `postman/Hospital_Vida_Salud.postman_collection.json` en Postman. Contiene 23 solicitudes con comprobaciones HTTP y captura de IDs. Al ejecutar la solicitud 01, genera identificadores ficticios y una fecha futura alineada a un bloque. Ejecutar en orden. Las consultas 09/10 pueden necesitar repetirse tras unos segundos por consistencia eventual; la prueba Python sí espera el evento confirmado.

| Operación | Endpoint | Éxito |
|---|---|---:|
| Registrar / listar paciente | POST / GET `/api/pacientes` | 201 / 200 |
| Consultar / actualizar paciente | GET / PUT `/api/pacientes/{id}` | 200 |
| Programar / listar cita | POST / GET `/api/citas` | 201 / 200 |
| Consultar cita | GET `/api/citas/{id}` | 200 |
| Cambiar estado | PATCH `/api/citas/{id}/estado` | 200 |
| Reprogramar agenda | PATCH `/api/citas/{id}/agenda` | 200 |
| Registrar / consultar atención | POST `/api/atenciones`, GET `/api/atenciones/{id}` | 201 / 200 |
| Registrar / consultar pago | POST `/api/pagos`, GET `/api/pagos/{id}` | 201 / 200 |
| Consultar avisos | GET `/api/notificaciones?citaId={id}` | 200 |
| Consultar trazabilidad | GET `/api/auditoria?recursoId={citaId}` | 200 |

Ejemplo de paciente:

```json
{
  "tipoDocumento": "OTRO",
  "numeroDocumento": "DEMO-001",
  "nombres": "Paciente",
  "apellidos": "Demostracion",
  "fechaNacimiento": "1990-01-01",
  "telefono": "000000000",
  "correo": "demo@example.test",
  "direccion": "Direccion ficticia"
}
```

Los otros cuerpos se encuentran en Postman y `scripts/smoke.py`. Para actualizar un paciente, PUT recibe los mismos campos, con `estado` opcional ACTIVO/INACTIVO. PATCH estado recibe `{"estado":"CONFIRMADA"}`; ATENDIDA debe cerrarse desde Atenciones, que proporciona el ID real de la atención.

## Pruebas

`./mvnw verify` compila todos los módulos y ejecuta pruebas JUnit, StepVerifier y WebTestClient. Las pruebas de repositorio utilizan el controlador R2DBC H2 real **solo para pruebas**. En Citas se omiten los índices parciales específicos de PostgreSQL en esa prueba H2; la protección concurrente de agenda se comprueba con PostgreSQL en la prueba de extremo a extremo. Mockito utiliza mocks por subclase y no necesita autoacoplar un agente al JDK.

`python3 scripts/smoke.py --docker-checks` exige Docker Compose y la arquitectura levantada. Verifica registro/consulta/duplicado de pacientes, agenda, estados, atención, pago, eventos, persistencia PostgreSQL, republicación idempotente y recuperación de outbox tras detener/reiniciar RabbitMQ. También detiene pacientes temporalmente para verificar el error de dependencia y lo reinicia. Ejecutar sobre un ambiente académico de demostración, no sobre otro entorno compartido. El resultado se guarda en `evidencias/smoke-result.json`.

Los resultados realmente obtenidos en la preparación se describen en `evidencias/VALIDACION_LOCAL.md`. Las evidencias de GitHub, Docker y despliegue se deben obtener ejecutando los workflows en el repositorio y máquina propios.

## CI con GitHub Actions

`.github/workflows/ci.yml` ejecuta:

1. Push a cualquier rama o PR hacia main.
2. Checkout, JDK 21, caché Maven y `./mvnw verify`.
3. Credenciales temporales, build Docker de todos los servicios y arranque Compose.
4. Prueba completa con PostgreSQL/RabbitMQ reales, guardando resultados y logs.
5. Si fue push aprobado a main, conserva la imagen de pacientes etiquetada por SHA para CD.
6. Apaga los contenedores temporales de CI. Ese runner temporal no constituye el despliegue persistente solicitado.

Crear el repositorio y subirlo desde la raíz:

```bash
git init -b main
git add .
git commit -m "Implement hospital microservices and CI CD"
git remote add origin URL_DE_TU_REPOSITORIO
git push -u origin main
```

Reemplazar `URL_DE_TU_REPOSITORIO` por la URL real. Configurar GitHub para exigir el check `build-test` antes de integrar en main. No incluir `.env`, targets, archivos temporales ni credenciales. El código incluye workflow y fuentes; el repositorio remoto no se crea sin acceso a tu cuenta.

### Fallo de CI controlado

```bash
git switch -c demo-ci-fallo
```

En `pagos/src/test/java/pe/vidasalud/pagos/PagoServiceTest.java`, cambiar temporalmente el valor válido `50.00` de `positiveIsAllowed` por `0.00`, conservando la expectativa `doesNotThrowAnyException()`. La prueba debe fallar. Hacer commit/push, abrir PR hacia main y capturar el check rojo. Restaurar `50.00`, hacer otro commit/push y capturar verde. No integrar la versión con la prueba rota.

## CD para pacientes y su endpoint

`.github/workflows/cd-pacientes.yml` escucha el éxito de Hospital CI y acepta solo un push aprobado de main del propio repositorio. Descarga la imagen de la ejecución exacta y la carga en un runner Linux x64 persistente con etiqueta `hospital-demo`. No se despliega código de PR de terceros. La máquina debe disponer de Docker, Compose, curl y Python, y alcanzar el Gateway.

Preparación una sola vez en esa máquina:

1. Ejecutar todo el proyecto con Compose para crear el ambiente persistente. Mantener una sola instancia de pacientes para esta demostración.
2. Conservar la misma configuración `.env` en `/opt/hospital-demo/.env` con permisos restringidos; no cambiar passwords respecto a los volúmenes existentes.
3. Registrar el runner desde GitHub `Settings > Actions > Runners` y ponerlo a ejecutar con etiqueta `hospital-demo`. Usar un repositorio privado o restringido a colaboradores de confianza.
4. Crear environment `hospital-demo`, limitarlo a main y definir variables `DEMO_GATEWAY_URL` (por ejemplo `http://localhost:8080`) y `DEMO_PATIENT_ID` (ID de un paciente ficticio registrado en ese ambiente).
5. Hacer push válido a main. El script actualiza únicamente pacientes y verifica la etiqueta SHA, Actuator UP y `GET /api/pacientes/{id}` por Gateway, esperando HTTP 200 y el ID esperado.

Se despliega un servicio que contiene el endpoint, no una ruta aislada. La etapa CD falla si no se cumple la verificación. No hay rollback automático; conservar una imagen aprobada anterior y ejecutar Compose con su `IMAGE_TAG` para restaurarla, si el schema sigue siendo compatible. El workflow serializa los despliegues con concurrency y omite ejecuciones de un SHA que ya no es el HEAD de main para evitar publicar una versión anterior por terminar más tarde.

## Evidencias y entrega

- Capturar Eureka con los servicios registrados y Gateway respondiendo.
- Capturar altas, consultas, duplicados, agenda y estados; además SQL persistido.
- Capturar exchange/colas RabbitMQ y filas de Notificaciones/Auditoría.
- Capturar pruebas locales y logs/reportes de CI, con fallo controlado y corrección.
- Capturar CD exitoso y consulta del paciente después del despliegue.
- Entregar informe PDF, repositorio accesible y ZIP del código. El Word es editable y se incluye aparte.

Los datos son ficticios y los servicios carecen de autenticación/autorización en este alcance académico. No son una plataforma clínica ni una aplicación lista para producción. No se implementan laboratorio, camas, farmacia ni procesamiento de cobros reales, conforme al alcance seleccionado y justificado.

## Prueba opcional de broker real aislada

`common/src/test/java/pe/vidasalud/common/LiveBrokerIT.java` no se ejecuta en la suite normal. Con un broker de demostración accesible y las variables `LIVE_RABBIT_HOST`, `LIVE_RABBIT_USER`, `LIVE_RABBIT_PASSWORD`, ejecutar `./mvnw -pl common test -Dtest=LiveBrokerIT`. Crea dos colas temporales propias, publica un evento con confirmación del broker, verifica su recepción en ambas y elimina únicamente esas colas. No utiliza credenciales incluidas en el código. Esta prueba se ejecutó en la preparación y su registro está en evidencias/rabbitmq-live.log.
