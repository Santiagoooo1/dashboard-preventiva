# Arquitectura técnica

Resumen para quien vaya a mantener o ampliar el proyecto. Monorepo con dos apps que solo se
comunican por HTTP: `backend/` (Spring Boot) y `frontend/` (React + Vite).

## Backend

- **Spring Boot 3.5 / Java 21**, Spring Data JPA, `spring-boot-starter-web`,
  `spring-boot-starter-validation`. Apache POI (`poi-ooxml`) para leer Excel.
- **PostgreSQL** vía Docker Compose (`preventiva_postgres`, puerto host `5433`). Sin migraciones:
  `spring.jpa.hibernate.ddl-auto=update` genera el esquema desde las entidades.
- **Seguridad**: `spring-boot-starter-security` está en el classpath, pero `SecurityConfig`
  permite todas las peticiones (`permitAll()`). No hay login ni JWT.

### Dos pipelines de datos, uno activo

El repositorio contiene **dos modelos de importación distintos**, resultado de la evolución del
proyecto:

1. **Pipeline heredado, específico de Cirugía/ILQ** (`Hospital` → `Servicio` → `Cirugia` +
   tablas hijas `Profilaxis`/`InfeccionQuirurgica`/`Microbiologia`/`SeguimientoPostoperatorio`/
   `MedidasPreventivas`, con `PlantillaExcel`/`MapeoColumnaExcel` fijas por departamento). Sigue
   compilando y tiene sus propios controladores (`ImportacionExcelController`,
   `PlantillaExcelController`, `ServicioController`, `DashboardController`), pero **el frontend
   actual no llama a ninguno de estos endpoints**: es código vivo mas no usado por la UI de hoy.
2. **Pipeline genérico y configurable**, el que usa realmente todo el flujo actual (`/crear-dashboard`
   y `/datasets`): `DatasetClinico` (el "dashboard" que se crea) tiene `CampoClinico` (columnas
   configurables, cualquier nombre/tipo), `PlantillaImportacion` + `MapeoCampoImportacion` (qué
   columna del archivo va a qué campo), y los registros importados quedan en
   `RegistroClinicoGenerico` vía `ImportacionGenerica`. `MetricaClinica` y `PanelClinico` (+
   `PanelMetrica`) definen los indicadores y su agrupación en dashboards.

Toda la lógica descrita en el resto de este documento y en la guía de usuario se refiere al
**pipeline genérico** (2), que es el que importa para entender el producto tal como se usa hoy.

### Copia de trabajo (corrección sin tocar el original)

`ImportacionTrabajo` + `FilaImportacionTrabajo` son el corazón de la corrección asistida: al leer un
archivo se crea una copia interna, fila a fila, con sus errores ya detectados. Las correcciones
(`corregirCelda`, `excluirFila`, `rellenarColumna`, `normalizarColumna`, deshacer, restaurar
original) se aplican sobre esa copia, nunca sobre el archivo subido. Cuando la copia queda
"importable", `importarDesdeTrabajo` vuelca los datos corregidos a `RegistroClinicoGenerico` a
través de una `ImportacionGenerica`.

### Trazabilidad

`EventoImportacionTrabajo` registra, en orden cronológico, cada acción sobre una copia de trabajo
(creación, corrección de celda, exclusión, relleno/normalización en bloque, restauración,
importación, activación del dataset). Se consulta vía `TrazabilidadImportacionTrabajoService`, tanto
por `importacionTrabajoId` como, tras importar, por `importacionGenericaId`.

### Tests

Suite de tests de integración (`backend/src/test/java/...`) contra la base de datos real de
desarrollo (sin H2 ni Testcontainers), con `@Transactional` para rollback automático por test y
perfil `test` (`application-test.properties`) para silenciar el log SQL. Cubren: copia de trabajo y
sus correcciones, trazabilidad, y reanudación/reconstrucción de borradores.

## Frontend

- **React 19 + Vite 8**, TypeScript, React Router 7. Sin librería de estado global: cada página usa
  `useApiResource` (hook propio) para pedir datos y gestionar loading/error.
- **Sin tests de frontend** todavía (ver `docs/estado-mvp.md`).

### Rutas principales (`src/routes/AppRoutes.tsx`)

- `/` — inicio.
- `/crear-dashboard` (y `/crear-dashboard/borrador/:datasetId` para reanudar) — asistente guiado.
- `/datasets`, `/datasets/:id`, `/datasets/:id/campos|plantillas|metricas|paneles` — zona avanzada.
- `/paneles/:panelId/dashboard` — dashboard (inicial o configurado a mano).
- `/importaciones-trabajo/:id/trazabilidad`, `/trazabilidad/importacion-generica/:id`.

### Asistente guiado (`components/importacion-guiada/`)

`GuidedImportWizard` orquesta los pasos (subir → columnas → configuración → corrección de filas →
resultado). La detección/sugerencia de columnas es puramente heurística y vive en el frontend
(`utils/importacionGuiada/sugerenciasColumnas.ts`, `camposClave.ts`): analiza el nombre de cada
columna para proponer tipo de dato y rol clínico antes de crear nada en el backend.
`utils/importacionGuiada/orquestador.ts` encadena las llamadas reales (crear dataset → campos →
plantilla → mapeos → validar → importar).

### Dashboard inicial

`utils/dashboardInicial/reglasMetricas.ts` decide, a partir de la metadata real de campos del
dataset, qué métricas proponer (total de registros, edad media, indicadores booleanos clínicos,
distribuciones por sexo/procedimiento/diagnóstico...). `orquestadorDashboardInicial.ts` las crea y
las agrupa en un panel `dashboard_inicial`. El hook `useDashboardInicial` centraliza esa lógica
(comprobación de idempotencia incluida) para los distintos puntos de entrada: el resultado de
importación (`DashboardInicialCta`) y el detalle de dataset / listados vacíos de métricas y paneles
(`DashboardInicialCard`).

### Páginas avanzadas

CRUD manual de datasets, campos, plantillas de importación, métricas y paneles, para quien necesite
ir más allá de lo que genera el dashboard inicial.

### API client (`src/api/`)

Un archivo por recurso (`datasetApi.ts`, `metricasApi.ts`, `panelesApi.ts`,
`importacionesTrabajoApi.ts`...), todos sobre `apiClient.ts` (fetch con manejo de errores
uniforme). `VITE_API_BASE_URL=/api`, reenviado por el proxy de Vite a `http://localhost:8080` en
desarrollo.

## Flujo de datos

```
CSV/Excel
  → detección de columnas (heurística, frontend)
  → dataset borrador + campos + plantilla + mapeos (backend, pipeline genérico)
  → copia interna (ImportacionTrabajo + FilaImportacionTrabajo)
  → correcciones (celda, exclusión, relleno, normalización)
  → importación (ImportacionGenerica)
  → registros clínicos (RegistroClinicoGenerico)
  → métricas (MetricaClinica) / paneles (PanelClinico + PanelMetrica)
  → dashboard (ejecución en tiempo real sobre los registros)
  → trazabilidad (EventoImportacionTrabajo, en paralelo desde la copia interna)
```
