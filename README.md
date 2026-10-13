# ms-ordenes

Microservicio de **órdenes de compra** del caso EventPass (Spring Boot, Java 21, PostgreSQL y Liquibase).

Un comprador (`USER`) crea una orden y ms-ordenes **descuenta el aforo en ms-eventos** y **pide los tickets a ms-tickets**.
Los endpoints están protegidos con el **JWT que emite ms-auth** (mismo secreto en todos los servicios) y según el rol: `USER` (comprador) o `STAFF`.

> **Diseño vs. implementación.** En el diagrama de arquitectura la emisión de tickets es asíncrona (SQS + Lambda).
> En esta entrega se simula de forma síncrona con `POST /comprar`. `POST /` deja la orden en `PENDIENTE_EMISION`
> y `messaging/LogOrdenPublisher` solo registra en el log (punto de extensión para publicar en SQS).

## Cómo ejecutarlo

### Opción A: todo junto con Docker Compose (recomendada)

Requisitos: **Docker Desktop** (con el motor corriendo), **Git** y una terminal **bash** (Git Bash en Windows).

```bash
git clone https://github.com/electivojava2026/compose
cd compose
./setup.sh                  # clona ms-auth, ms-eventos, ms-ordenes, ms-tickets y demo-frontend
docker compose up --build   # construye y levanta todo (la primera vez tarda unos minutos)
```

| Servicio | URL | Notas |
|----------|-----|-------|
| ms-auth | http://localhost:8081 | emite los JWT |
| ms-eventos | http://localhost:8082 | eventos y aforo |
| **ms-ordenes** | **http://localhost:8083** | este servicio |
| ms-tickets | http://localhost:8084 | emisión de tickets |
| frontend | http://localhost:5173 | demo |
| PostgreSQL | localhost:5432 | una BD por servicio (`db_auth`, `db_eventos`, `db_ordenes`, `db_tickets`), usuario/clave `eventpass` |

Para detener todo: `docker compose down` (agregar `-v` borra los datos y recrea las BD con sus datos semilla).

### Opción B: solo ms-ordenes en tu equipo

Requisitos: JDK 21, Docker Desktop y Git. Para el login y `POST /comprar` también deben estar corriendo ms-auth, ms-eventos y ms-tickets (lo más simple es la opción A).

1. Levantar la base de datos (PostgreSQL 16 en el puerto 5435, BD `ordenes_db`):
   ```bash
   docker compose up -d
   ```
2. Ejecutar los tests (usan H2 en memoria, no necesitan la BD):
   ```bash
   ./mvnw test            # Windows: .\mvnw.cmd test
   ```
3. Compilar y empaquetar (genera `target/ms-ordenes-0.0.1-SNAPSHOT.jar`):
   ```bash
   ./mvnw clean package   # Windows: .\mvnw.cmd clean package
   ```
4. Ejecutar la aplicación en el puerto 8083 (el puerto no está fijado en `application.properties`, por eso se indica con `SERVER_PORT`):
   ```bash
   SERVER_PORT=8083 java -jar target/ms-ordenes-0.0.1-SNAPSHOT.jar          # bash
   $env:SERVER_PORT=8083; java -jar target\ms-ordenes-0.0.1-SNAPSHOT.jar    # PowerShell
   ```
   Las URLs de los otros servicios se pueden cambiar con `EVENTOS_URL` (por defecto `http://localhost:8082`) y `TICKETS_URL` (por defecto `http://localhost:8084`).

## Usuarios de prueba (datos semilla de ms-auth)

| Email | Clave | Rol |
|-------|-------|-----|
| admin@eventpass.com | admin123 | STAFF |
| staff@eventpass.com | staff123 | STAFF |
| user@eventpass.com | user123 | USER |

Login: `POST http://localhost:8081/api/v1/auth/login` con `{ "email": "...", "password": "..." }`. Devuelve el `token` para usar como `Authorization: Bearer <token>`.

## Endpoints (base: `http://localhost:8083/api/v1/ordenes`)

Todos requieren el header `Authorization: Bearer <token>`.
Sin token → **401**. Con token pero sin el rol necesario → **403**.

| Método | Ruta | Descripción | Rol | Respuesta |
|--------|------|-------------|-----|-----------|
| POST | `/` | Crear orden (queda `PENDIENTE_EMISION`) | USER | 201 / 400 |
| POST | `/comprar` | Compra completa: crea la orden, descuenta aforo en ms-eventos y emite un ticket por entrada en ms-tickets | USER | 201 / 400 |
| GET | `/mis-ordenes` | Órdenes del comprador autenticado, cada una con sus tickets | USER | 200 |
| GET | `/{id}` | Obtener una orden (el comprador solo ve las suyas) | USER, STAFF | 200 / 404 |
| GET | `/?estado=...` | Listar todas, filtro opcional por estado | STAFF | 200 |
| PATCH | `/{id}/estado` | Marcar `EMITIDA` o `RECHAZADA` | STAFF | 200 / 400 / 404 / **409 ya resuelta** |

Body para crear o comprar: `{ "eventoId": 1, "tipoEntradaId": 1, "cantidad": 2 }`
Body para cambiar estado: `{ "estado": "RECHAZADA", "motivo": "Sin aforo" }`

**Flujo de `POST /comprar`:**
1. Registra la orden como `PENDIENTE_EMISION`.
2. Llama a ms-eventos para reservar el aforo (con un token de cuenta de servicio, rol `SERVICE`).
3. Pide a ms-tickets un ticket por cada entrada (reenvía el token del comprador).
4. Marca la orden `EMITIDA` y responde **201** con `{ "orden": {...}, "tickets": [...] }`.

Si no hay aforo (ms-eventos responde 409) o un servicio falla, la orden queda `RECHAZADA` con su motivo (por ejemplo `Sin aforo`) y la respuesta sigue siendo **201**: la orden se registró, lo que cambia es su estado.

Estados: `PENDIENTE_EMISION` → `EMITIDA` | `RECHAZADA` (una orden resuelta no vuelve a cambiar).

## Pruebas con Postman

Colección lista para importar: `postman/ms-ordenes.postman_collection.json`.

- Variables: `baseUrl = http://localhost:8083` y `authUrl = http://localhost:8081` (puertos de la opción A).
- Ejecutar primero **0a** y **0b** (login): guardan los tokens `tokenStaff` y `tokenUser` automáticamente.
- Incluye los casos de éxito (200/201) y los de error (400, 401, 403, 404, 409), más `1b` (compra exitosa con tickets) y `1c` (compra sin aforo, orden `RECHAZADA`).

## Base de datos

- Esquema creado y poblado con **Liquibase** (`src/main/resources/db/changelog/db.changelog-master.yaml`): tabla `ordenes` y 3 órdenes de ejemplo.
- Con Docker Compose (opción A), desde la carpeta `compose`:
  ```bash
  docker compose exec postgres psql -U eventpass -d db_ordenes -c "select * from ordenes;"
  ```
- Con la opción B:
  ```bash
  docker exec -it eventpass-ordenes-db psql -U eventpass -d ordenes_db -c "select * from ordenes;"
  ```
- Apagar la BD de la opción B: `docker compose down` (con `-v` borra los datos).

## Estructura

```
controller/  -> endpoints REST
service/     -> lógica de negocio, compra y cambios de estado de la orden
repository/  -> acceso a datos (Spring Data JPA)
model/       -> entidad Orden y enum EstadoOrden
dto/         -> objetos de entrada/salida de la API
client/      -> llamadas a ms-eventos (reservar aforo) y ms-tickets (emitir y listar tickets)
messaging/   -> publicación de la orden a la cola de emisión (hoy solo log)
exception/   -> manejo de errores (400 / 404 / 409)
security/    -> validación del JWT de ms-auth y reglas por rol
```
