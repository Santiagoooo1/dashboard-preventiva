# Dashboard Preventiva

Dashboard clínico automático desde Excel/CSV. La aplicación permite transformar archivos clínicos
con errores habituales en datasets validados, trazables y visualizables mediante dashboards
iniciales generados automáticamente.

## Qué problema resuelve

Los hospitales exportan sus registros de vigilancia de infección de localización quirúrgica (ILQ)
a Excel/CSV, con nombres de columna, formatos de fecha y vocabularios distintos según el servicio y
la persona que exporta. Montar un dashboard fiable a partir de eso normalmente implica: adivinar el
formato de cada archivo, limpiar datos a mano en el propio Excel, y confiar en que nadie perdió el
rastro de qué se corrigió y por qué. Dashboard Preventiva automatiza ese proceso: detecta las
columnas, valida cada fila contra el tipo de dato esperado, permite corregir/excluir/normalizar sin
tocar el archivo original, registra cada cambio, y genera un dashboard inicial con indicadores
clínicos en cuanto los datos quedan importados.

## Público objetivo

Personal de un servicio hospitalario (medicina preventiva, cirugía, traumatología, etc.) responsable
de vigilancia epidemiológica, que necesita convertir exportaciones periódicas en indicadores sin
depender de un desarrollador para cada archivo nuevo. También sirve como base técnica para quien
tenga que mantener o ampliar la aplicación.

## Flujo principal

1. Subir un archivo Excel/CSV clínico desde "Crear dashboard".
2. La aplicación detecta las columnas y sugiere su tipo de dato y rol clínico.
3. Se valida el archivo fila a fila y se muestran los errores encontrados.
4. Se corrigen, excluyen, rellenan o normalizan los datos problemáticos sobre una copia interna
   (el archivo original nunca se modifica).
5. Se importan los registros corregidos.
6. Se genera (o se abre, si ya existe) un dashboard inicial con métricas y paneles automáticos.
7. Se puede consultar en cualquier momento la trazabilidad completa de la importación.

Ver el recorrido completo, paso a paso, en [`docs/guia-usuario.md`](docs/guia-usuario.md).

## Funcionalidades principales

- Asistente guiado de importación (`/crear-dashboard`): detección automática de columnas por
  nombre, sin necesidad de configurar plantillas de antemano.
- Copia de trabajo interna: corrección de celdas, exclusión de filas, relleno y normalización de
  columnas en bloque, deshacer, y restauración al estado original — todo sin tocar el archivo subido.
- Reconocimiento de vocabulario clínico: valores de ausencia ("NO CONSTA", "N/A"...), formatos de
  fecha mixtos, vocabulario Sí/No ampliado, y casos especiales como "paciente sigue ingresado".
- Trazabilidad: historial ordenado y auditable de cada corrección, exclusión, relleno, normalización
  e importación.
- Reanudación de borradores: si una importación se interrumpe, se retoma exactamente donde se dejó,
  sin duplicar datasets.
- Dashboard inicial automático: métricas y panel generados a partir de los campos importados,
  accesible tanto justo tras importar como desde cualquier dataset activo más adelante.
- Zona avanzada (`/datasets`): gestión manual de campos, plantillas, métricas y paneles para quien
  necesite ir más allá del dashboard inicial.
- Tests críticos de backend sobre la copia de trabajo, la reanudación y la trazabilidad.

## Stack técnico

- **Backend**: Spring Boot 3.5 (Java 21), Spring Data JPA, PostgreSQL, Apache POI (lectura de
  Excel).
- **Frontend**: React 19 + Vite, TypeScript, React Router.
- **Base de datos**: PostgreSQL 16 vía Docker Compose.
- Sin frameworks de test frontend ni migraciones de esquema (`ddl-auto=update`) — ver
  [`docs/estado-mvp.md`](docs/estado-mvp.md) para el detalle de limitaciones.

Resumen técnico completo en [`docs/arquitectura.md`](docs/arquitectura.md).

## Cómo arrancar el proyecto

Guía completa, con solución de problemas frecuentes, en
[`docs/guia-instalacion.md`](docs/guia-instalacion.md). Resumen:

```
# Backend
cd /d C:\Users\santi\dashboard-preventiva
docker compose up -d
cd backend
mvnw.cmd spring-boot:run

# Frontend (otra terminal)
cd /d C:\Users\santi\dashboard-preventiva\frontend
npm install
npm run dev
```

Frontend en `http://localhost:5173`, backend en `http://localhost:8080/api/health`.

## Cómo probar la demo

Todo el material de demo (CSV realista con errores intencionados, guion paso a paso y checklist)
está en [`docs/demo/`](docs/demo/README.md). Es la forma más rápida de ver el flujo completo, de
principio a fin, en 5-10 minutos.

## Estado actual del MVP

El flujo completo (importar → corregir → importar → dashboard inicial → trazabilidad → reanudar/
descartar borradores) funciona de principio a fin y está verificado tanto por tests de backend como
por un ensayo manual con datos reales. Detalle completo, incluidas limitaciones conocidas y próximos
pasos, en [`docs/estado-mvp.md`](docs/estado-mvp.md).

## Limitaciones conocidas

- Seguridad desactivada (`permitAll()` en todos los endpoints): no apta para producción tal cual.
- Sin migraciones de base de datos (Flyway/Liquibase); el esquema se actualiza con
  `ddl-auto=update`.
- Pensada para ejecutarse en local; no hay despliegue en la nube configurado.
- El dashboard inicial usa heurísticas por nombre de columna, no aprendizaje ni configuración por
  hospital.

Lista completa en [`docs/estado-mvp.md`](docs/estado-mvp.md).

## Próximos pasos recomendados

Autenticación real, migraciones de base de datos, empaquetado para despliegue, tests de frontend y
exportación de resultados (PDF/Excel). Detalle y justificación en
[`docs/estado-mvp.md`](docs/estado-mvp.md).

## Documentación

| Documento | Contenido |
|---|---|
| [`docs/guia-instalacion.md`](docs/guia-instalacion.md) | Requisitos, arranque paso a paso, problemas frecuentes. |
| [`docs/guia-usuario.md`](docs/guia-usuario.md) | Cómo usar la aplicación de principio a fin. |
| [`docs/arquitectura.md`](docs/arquitectura.md) | Resumen técnico de backend, frontend y flujo de datos. |
| [`docs/estado-mvp.md`](docs/estado-mvp.md) | Qué está completado, limitaciones y próximos pasos. |
| [`docs/demo/`](docs/demo/README.md) | Material de demo: CSV, guion y checklist. |
| `CLAUDE.md` | Guía de trabajo para agentes/IA sobre este repositorio. |
