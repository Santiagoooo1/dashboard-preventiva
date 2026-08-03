# Guía de instalación y arranque

## Requisitos

- **Java 21** o superior (el `pom.xml` fija `java.version=21`).
- **Maven Wrapper incluido** (`backend/mvnw.cmd`): no hace falta instalar Maven aparte.
- **Node.js/npm** (para el frontend; cualquier versión reciente de Node 20+ funciona con Vite 8).
- **Docker Desktop** en marcha (para levantar PostgreSQL).
- No hace falta instalar PostgreSQL aparte: se levanta vía `docker-compose.yml` en la raíz del
  repo.

## Backend

```
cd /d C:\Users\santi\dashboard-preventiva
docker compose up -d
docker exec -it preventiva_postgres pg_isready -U preventiva_user -d preventiva_db
cd backend
mvnw.cmd spring-boot:run
```

- `docker compose up -d` levanta el contenedor `preventiva_postgres` (Postgres 16) en el puerto
  `5433` del host (mapeado al `5432` interno).
- `pg_isready` confirma que la base de datos ya acepta conexiones antes de arrancar el backend; si
  no responde, espera unos segundos y repite el comando.
- Al primer arranque, `DataSeeder` crea de forma idempotente roles, un hospital de demostración, un
  usuario admin (`admin@preventiva.local` / `admin1234`) y una configuración de ejemplo para el
  antiguo flujo de importación por departamento. **El flujo actual de "Crear dashboard" no depende
  de nada de esto**: detecta las columnas directamente del archivo que subas, sin plantilla previa.
- El backend queda escuchando en `http://localhost:8080`.

## Frontend

```
cd /d C:\Users\santi\dashboard-preventiva\frontend
npm install
npm run dev
```

`npm install` solo hace falta la primera vez (o cuando cambie `package-lock.json`).

## URLs

- **Frontend**: `http://localhost:5173` (o el puerto que indique Vite en la consola si el 5173 ya
  está ocupado).
- **Backend**: `http://localhost:8080/api/health` — debe responder `200`.

El frontend en desarrollo usa un proxy de Vite (`vite.config.ts`) que reenvía `/api/*` a
`http://localhost:8080`, así que no hace falta configurar CORS ni URLs absolutas para probar en
local.

## Comandos de verificación rápida

```
cd backend
mvnw.cmd clean test       # tests de backend
mvnw.cmd clean compile    # compilación

cd ../frontend
npm run build             # build de producción (tsc + vite build)
npm run lint               # oxlint
```

## Problemas frecuentes

**Docker no responde / `docker ps` se queda colgado.**
Docker Desktop puede tardar en arrancar tras reiniciar Windows, o quedarse en un estado donde el
proceso está vivo pero el daemon no responde. Espera un minuto y reintenta `docker ps`. Si sigue sin
responder, reinicia Docker Desktop manualmente antes de asumir que es un problema de la aplicación.

**Puerto ocupado (8080 o 5173).**
- Backend: cambia `server.port` en `backend/src/main/resources/application.properties`, o localiza
  y cierra el proceso que ya usa el 8080 (`Get-NetTCPConnection -LocalPort 8080` en PowerShell).
- Frontend: Vite detecta el puerto ocupado automáticamente y usa el siguiente libre (5174, 5175...);
  revisa la consola de `npm run dev` para ver cuál eligió.

**El backend no conecta con PostgreSQL.**
Comprueba que el contenedor está arriba (`docker ps` debe listar `preventiva_postgres`) y que
`pg_isready` responde. La cadena de conexión está fija en `application.properties`
(`jdbc:postgresql://localhost:5433/preventiva_db`); si cambiaste el puerto en
`docker-compose.yml`, debes actualizarla ahí también.

**El frontend no encuentra el backend (errores "No se pudo conectar con el backend").**
Confirma que el backend está arrancado y responde en `/api/health` antes de usar la app. El proxy
de Vite asume que el backend está en `localhost:8080`; si lo cambiaste de puerto, actualiza
`vite.config.ts`.

**La base de datos tiene datasets de pruebas antiguos.**
Es habitual acumular datasets `BORRADOR` de pruebas interrumpidas. Se pueden descartar desde
"Datasets" → "Pruebas y borradores" → "Descartar borrador" (no borra nada hasta confirmar). Para
datasets ya `ACTIVO`, usa "Archivar" desde el listado principal — no elimina físicamente los datos,
solo los oculta de los listados normales.
