# ms-ordenes

Microservicio de **órdenes de compra** del caso EventPass (Spring Boot + PostgreSQL en Docker).
Registra la compra de inmediato como `PENDIENTE_EMISION`; la emisión del ticket y el descuento de aforo
se hacen después, de forma asíncrona (SQS + Lambda → ms-eventos / ms-tickets).
Los endpoints están protegidos con el **JWT que emite `ms-auth`** (mismo secreto) y según el rol: `USER` (comprador) o `STAFF`.

## Requisitos
- JDK 21
- Docker Desktop (con el motor corriendo)
- Git
- Para probar con roles en Postman: `ms-auth` corriendo en `http://localhost:8081` (de ahí salen los tokens)

## Cómo ejecutarlo

1. Levantar la base de datos (contenedor PostgreSQL, puerto 5435)
   ```bash
   docker compose up -d
   ```
2. Ejecutar los tests (usan H2 en memoria, no necesitan la BD)
   ```bash
   ./mvnw test            # Windows: .\mvnw.cmd test
   ```
3. Compilar y empaquetar
   ```bash
   ./mvnw clean package   # Windows: .\mvnw.cmd clean package
   ```
4. Ejecutar la aplicación (puerto 8083)
   ```bash
   java -jar target/ms-ordenes-0.0.1-SNAPSHOT.jar
   ```

## Endpoints (base: `http://localhost:8083/api/v1/ordenes`)

Todos requieren el header `Authorization: Bearer <token>`.
Sin token → **401**. Con token pero sin el rol necesario → **403**.

| Método | Ruta | Descripción | Rol | Respuesta |
|--------|------|-------------|-----|-----------|
| POST | `/` | Crear orden (queda `PENDIENTE_EMISION`) | USER | 201 / 400 |
| GET | `/mis-ordenes` | Órdenes del comprador autenticado | USER | 200 |
| GET | `/{id}` | Obtener una orden (el comprador solo ve las suyas) | USER, STAFF | 200 / 404 |
| GET | `/?estado=...` | Listar todas, filtro opcional por estado | STAFF | 200 |
| PATCH | `/{id}/estado` | Marcar `EMITIDA` o `RECHAZADA` (lo usará el proceso de emisión) | STAFF | 200 / 400 / 404 / **409 ya resuelta** |

Body para crear: `{ "eventoId": 1, "tipoEntradaId": 1, "cantidad": 2 }`
Body para cambiar estado: `{ "estado": "RECHAZADA", "motivo": "Sin aforo" }`

Estados: `PENDIENTE_EMISION` → `EMITIDA` | `RECHAZADA` (una orden resuelta no vuelve a cambiar).

Colección de Postman lista para importar: `postman/ms-ordenes.postman_collection.json`
(hace login en ms-auth y guarda los tokens `STAFF` y `USER` automáticamente).

## Pendiente (siguientes pasos)
- `messaging/LogOrdenPublisher`: reemplazar el log por el envío real a la cola SQS.
- Lambda de emisión: reservar aforo en ms-eventos y luego llamar `PATCH /{id}/estado`.

## Base de datos
- Imagen: `postgres:16` (ver `compose.yaml`) · BD `ordenes_db` · usuario/clave `eventpass`
- Ver los datos desde el contenedor:
  ```bash
  docker exec -it eventpass-ordenes-db psql -U eventpass -d ordenes_db -c "select * from ordenes;"
  ```
- Apagar: `docker compose down` (agregar `-v` para borrar los datos)

## Estructura
```
controller/  -> endpoints REST
service/     -> lógica de negocio y cambios de estado de la orden
repository/  -> acceso a datos (Spring Data JPA)
model/       -> entidad Orden y enum EstadoOrden
dto/         -> objetos de entrada/salida de la API
messaging/   -> publicación de la orden a la cola de emisión (hoy solo log)
exception/   -> manejo de errores (400 / 404 / 409)
security/    -> validación del JWT de ms-auth y reglas por rol
```
