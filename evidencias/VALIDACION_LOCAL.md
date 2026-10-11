# Validación ejecutada durante la preparación

- Java 21.0.6, Maven Wrapper 3.9.9; Spring Boot 3.5.16 / Cloud 2025.0.3.
- Comando real: Maven Wrapper con configuración de proxy/certificados exclusiva del entorno de preparación, `clean verify`. Esa configuración temporal no pertenece al proyecto ni se requiere en una máquina con conexión normal.
- Resultado: **BUILD SUCCESS**, todos los módulos compilados y empaquetados; **47 pruebas**, 0 fallos, 0 errores y 0 omitidas.
- Prueba HTTP de la aplicación Pacientes arrancada: registro 201, consulta 200 y duplicado 409, con acceso R2DBC H2 real.
- Repositorios R2DBC: persistencia y restricciones; transacción de Citas/outbox e historial; idempotencia de consumidores. En H2 se omiten los índices parciales propios de PostgreSQL.
- Verificación adicional de reprogramación, historial, outbox y control de versión con SQL/R2DBC: aprobada; registro `reprogramacion-r2dbc.log`.
- Prueba separada **LiveBrokerIT** contra RabbitMQ 3.12.1 real: 1 prueba, 0 fallos/errores. Se publicó con confirmación y se recibió en dos colas independientes. La prueba utiliza colas temporales; no representa el flujo completo de servicios desplegados.
- Workflows CI/CD validados con actionlint 1.7.7. Scripts shell, YAML, JSON y Python revisados sintácticamente.

## Registros incluidos

`maven-verify.log`, `unit-test-summary.json`, reportes XML en `surefire/`, `rabbitmq-live.log` y `reprogramacion-r2dbc.log`. Son resultados obtenidos, no capturas inventadas de GitHub.

## Pendientes del ambiente propio

Este entorno no dispone de Docker Engine ni de una instancia PostgreSQL ejecutable para levantar la arquitectura completa. No se ha ejecutado aquí `scripts/smoke.py --docker-checks` ni se ha probado el flujo completo con PostgreSQL, Eureka y Gateway en contenedores.

El workflow incorpora esa prueba de extremo a extremo con PostgreSQL y RabbitMQ reales. Debe ejecutarse en el repositorio propio y corregirse cualquier diferencia ambiental que aparezca. Faltan evidencias de GitHub CI rojo/verde, Eureka, Gateway, persistencia PostgreSQL, consumidores y despliegue CD en la máquina destino. No se creó un repositorio remoto ni se desplegó en la cuenta del estudiante.
