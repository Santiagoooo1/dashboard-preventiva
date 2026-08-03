# CLAUDE.md

Responde en español.

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Dashboard Preventiva is a surgical-site-infection (ILQ) prevention monitoring system for a hospital.
The repo is a monorepo with two top-level apps that currently only communicate over HTTP:

- `backend/` — Spring Boot 3.5 (Java 21) REST API. This is where essentially all the code lives today.
- `frontend/` — empty placeholder directory; no frontend has been scaffolded yet.
- `docker-compose.yml` — runs only the Postgres database used by the backend.
- `docs/Diagrama.png` — architecture/ER diagram.

Core domain: hospitals import Excel/CSV exports of surgical records per `Servicio` (surgical
department — e.g. Trauma, Neurocirugía, Cesáreas). Each import is validated, mapped column-by-column
into a normalized relational schema (`Cirugia` + 1:1 child tables for prophylaxis, infection,
microbiology, follow-up, preventive measures), and then aggregated for dashboard charts (infection
rate, prophylaxis adequacy, infections by department/pathogen/site, surgeries per month).

## Commands

All commands run from `backend/` (there is no root build file).

```
# Windows
.\mvnw.cmd spring-boot:run      # run the API (needs Postgres up first)
.\mvnw.cmd test                 # run all tests
.\mvnw.cmd test -Dtest=BackendApplicationTests   # run a single test class
.\mvnw.cmd clean package        # build the jar

# start the database the app expects on localhost:5433
docker compose up -d            # run from repo root
```

There is no linter/formatter configured in the project.

The datasource is hardcoded in `backend/src/main/resources/application.properties` to
`jdbc:postgresql://localhost:5433/preventiva_db` (user/pass `preventiva_user`/`preventiva_pass`),
matching the `postgres` service in the root `docker-compose.yml`. `spring.jpa.hibernate.ddl-auto=update`,
so schema changes are applied automatically from entities on startup — there are no migration files
(no Flyway/Liquibase).

On first run, `DataSeeder` (`backend/src/main/java/com/preventiva/backend/config/DataSeeder.java`)
seeds roles, a demo hospital, an admin user (`admin@preventiva.local` / `admin1234`), one `Servicio`
per surgical department, and a full `PlantillaExcel` + `MapeoColumnaExcel` set per department. All
seeding methods are idempotent (`...SiNoExiste` = "...if it doesn't exist") so they're safe to re-run.

## Architecture

### Package layout (`backend/src/main/java/com/preventiva/backend/`)
- `entity/` — JPA entities.
- `enums/` — shared enums (see below).
- `repository/` — Spring Data JPA repositories.
- `dto/` — Lombok `@Builder` request/response DTOs.
- `service/interfaces/` + `service/impl/` — one interface + one impl per service.
- `controller/` — thin `@RestController`s, all under `/api/...`, delegating straight to a service.
- `util/` — `WorkbookLoader` (Excel/CSV file parsing) and `TextNormalizer` (matching helper).
- `config/` — `SecurityConfig` and `DataSeeder`.

### Domain model
`Hospital` → `Servicio` (surgical department) → `Cirugia` (a single surgery, the aggregate root).
`Cirugia` has five `@OneToOne` child tables, each populated from the same Excel row:
`Profilaxis`, `InfeccionQuirurgica` (which itself has a child `Microbiologia`),
`SeguimientoPostoperatorio`, `MedidasPreventivas`. `Usuario` has a `Rol` (ADMIN/MEDICO).

### Excel/CSV import pipeline
This is the most complex part of the codebase and spans `ExcelValidationServiceImpl`,
`ExcelImportServiceImpl`, `WorkbookLoader`, and `TextNormalizer`. Understanding it requires reading
all of these together:

1. **Template-driven mapping**: every hospital department has a `PlantillaExcel`, and each template
   has a list of `MapeoColumnaExcel` rows: `nombreColumnaExcel` (expected Excel header text) →
   `campoDestino` (a `CampoDestino` enum value, the normalized target field) → `tipoDato`
   (`TipoDatoExcel`: TEXTO/ENTERO/DECIMAL/FECHA/BOOLEANO) → `obligatoria` (required?). This lets each
   department's Excel have different column names/order while mapping onto the same `Cirugia`+child
   schema. `CampoDestino.IGNORAR` marks columns that are recognized but intentionally dropped.
2. **Header matching is accent/case-insensitive**: `TextNormalizer.normalize()` (NFD-strip accents,
   uppercase, collapse whitespace) is used to match Excel headers against `MapeoColumnaExcel` names,
   and also to interpret free-text boolean/adequacy values. Whenever you touch matching logic, run it
   through `TextNormalizer.normalize()` first — do not compare raw strings.
2. **Two-stage validation before import**: `POST /api/importaciones/validar` checks headers only
   (which columns are recognized / which required columns are missing). `POST
   /api/importaciones/validar-filas` validates every data row's value against its mapped
   `TipoDatoExcel` and `obligatoria` flag, producing `ErrorFilaExcelDto`s. `POST
   /api/importaciones/importar` re-runs row validation internally and only writes rows if the whole
   file is valid; otherwise it persists an `ImportacionExcel` with `estado=RECHAZADA` and records every
   error in `ErrorImportacionExcel`, importing nothing.
3. **Excel and CSV are parsed by separate, largely duplicated code paths**: `WorkbookLoader.esCsv()`
   branches early, and both `ExcelValidationServiceImpl` and `ExcelImportServiceImpl` have parallel
   `...` / `...Csv` method pairs (e.g. `validarFila`/`validarFilaCsv`,
   `importarFilasValidas`/`importarFilasValidasCsv`). **A fix to one path usually needs the same fix in
   the other.** CSV parsing auto-detects the separator (`;`, `,`, or tab) and character encoding
   (UTF-16 BOM, UTF-8, falling back to windows-1252) heuristically, since these files come from
   hospital data exports of unknown provenance.
4. **Row filtering heuristic**: `esFilaClinicaPrincipal`/`esFilaClinicaPrincipalCsv` treats a row as
   real data only if it has a value in the HC (historia clínica / patient record number) column *and*
   at least one "strong" clinical field (surgery date, procedure, CIE-10 code, duration, prophylaxis
   fields, or ILQ) — this filters out blank/subtotal/comment rows that real hospital exports contain.
5. **Merged Excel cells**: `obtenerCeldaConSoporteCombinadas` resolves a blank cell inside a merged
   region by looking up the region's top-left cell.
6. **Loose boolean/adequacy vocabulary**: both validation and import parse a fixed Spanish
   vocabulary (`SI/S/NO/N/TRUE/FALSE/VERDADERO/FALSO/1/0/X/POSITIVO/NEGATIVO/ADECUADA/INADECUADA/NO
   ADECUADA`) rather than strict booleans — this list is duplicated across validation and import; keep
   it in sync if you add values.
7. **Deduplication**: a surgery is only inserted if no existing `Cirugia` matches on
   `(hc, fechaCirugia, cie10)` (`existsByHcAndFechaCirugiaAndCie10`), so re-importing the same file is
   idempotent at the row level.

### Dashboard endpoints
`DashboardController`/`DashboardServiceImpl` (`/api/dashboard/...`) compute all aggregates in-memory
via `findAll()` + Java stream grouping (no SQL aggregation) — acceptable at hospital-department data
volumes but worth knowing before assuming there's a query to optimize.

### Security
`SecurityConfig` currently `permitAll()`s every request (CSRF disabled, no authentication wired up).
`Usuario`/`Rol` and a `BCryptPasswordEncoder` bean exist and the seeded admin user has a hashed
password, but there is no login endpoint or session/JWT handling yet — don't assume any endpoint is
actually protected.
