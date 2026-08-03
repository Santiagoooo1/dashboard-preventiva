# Guion de defensa — Dashboard Preventiva

Defensa oral estructurada, pensada para hablar entre 5 y 8 minutos (sin contar la demo en vivo, que
tiene su propio guion en `demo-en-7-minutos.md`). Construido íntegramente a partir de lo que ya está
documentado en `README.md`, `docs/estado-mvp.md`, `docs/arquitectura.md` y `docs/guia-usuario.md` —
nada aquí es una historia nueva.

## Versión de 60 segundos — "¿De qué va tu proyecto?"

> Dashboard Preventiva convierte exportaciones Excel/CSV de vigilancia de infección quirúrgica en
> dashboards clínicos, automáticamente. El problema real es que esos archivos vienen con errores
> habituales — fechas mal escritas, campos vacíos, formatos distintos entre exportaciones — y
> normalmente eso se limpia a mano en el propio Excel, sin dejar rastro de qué se cambió. Mi
> aplicación detecta las columnas solas, valida cada fila, permite corregir o excluir datos sobre
> una copia interna sin tocar el archivo original, registra cada cambio, y en cuanto los datos están
> importados genera un dashboard inicial con indicadores clínicos ya calculados. Es un MVP: funciona
> de principio a fin y está verificado con tests y con datos reales, pero seguridad y despliegue
> todavía están pendientes.

---

## 1. Introducción breve

Dashboard Preventiva es una aplicación web para convertir exportaciones clínicas de Excel/CSV en
dashboards de vigilancia de infección de localización quirúrgica (ILQ), sin que el usuario tenga que
programar ni depender de un desarrollador para cada archivo nuevo.

## 2. Problema detectado

Cada hospital/servicio exporta sus registros con nombres de columna, formatos de fecha y
vocabularios distintos, y esos archivos casi nunca entran limpios a la primera. La forma habitual de
resolverlo es editar el Excel a mano — lo cual no deja constancia de qué se cambió, obliga a repetir
el trabajo si hay que volver a exportar, y hace que montar un dashboard fiable dependa de una persona
concreta que "sabe cómo limpiar ese archivo".

## 3. Solución propuesta

Un asistente guiado que: detecta las columnas del archivo por su nombre, valida cada fila contra el
tipo de dato esperado, permite corregir/excluir/rellenar/normalizar los datos problemáticos sobre una
**copia interna de trabajo** (el archivo original nunca se toca), registra cada corrección en un
historial trazable, y genera automáticamente un dashboard inicial con métricas clínicas en cuanto los
datos quedan importados.

## 4. Público objetivo

Personal de un servicio hospitalario responsable de vigilancia epidemiológica (medicina preventiva,
cirugía, traumatología...) que necesita convertir exportaciones periódicas en indicadores sin
depender de un desarrollador para cada archivo. También sirve como base técnica para quien tenga que
mantener o ampliar la aplicación.

## 5. Flujo principal

Subir archivo → revisar columnas detectadas → corregir errores sobre la copia interna (corregir
celda, excluir fila, rellenar columna, normalizar fechas) → importar → dashboard inicial generado
automáticamente → trazabilidad consultable en cualquier momento. Si el proceso se interrumpe, el
dataset queda como borrador y se retoma exactamente donde se dejó.

## 6. Arquitectura técnica

Backend Spring Boot 3.5 / Java 21 + PostgreSQL; frontend React 19 + Vite + TypeScript, sin librería
de estado global. El backend tiene, de hecho, **dos pipelines de importación**: uno heredado
específico de Cirugía/ILQ (que ya no usa el frontend) y el genérico y configurable que sí está detrás
de todo el flujo actual (`DatasetClinico` + `CampoClinico` + `PlantillaImportacion` +
`ImportacionTrabajo` → `RegistroClinicoGenerico` → `MetricaClinica`/`PanelClinico`). Detalle completo
en `docs/arquitectura.md`.

## 7. Decisiones importantes

- **Copia de trabajo separada del archivo original**: permite corregir, deshacer y reintentar sin
  riesgo, y sin depender de volver a exportar el archivo.
- **Detección de columnas por heurística de nombre**, no por plantilla fija: cada hospital puede
  nombrar sus columnas distinto sin que haga falta tocar código.
- **Trazabilidad como ciudadano de primera clase**: cada corrección queda registrada con su valor
  anterior y nuevo, pensado para un entorno donde la auditoría importa.
- **Dashboard inicial automático en vez de exigir configuración manual**: el valor debe verse en
  minutos, no después de configurar métricas y paneles a mano.
- **Tests de integración contra Postgres real** (no H2/mocks) para los flujos críticos, porque son
  los que más fácil se rompen con cambios.

## 8. Qué hace bien el MVP

- El flujo completo funciona de principio a fin: importar, corregir, excluir, rellenar, normalizar,
  importar, generar dashboard, consultar trazabilidad, reanudar o descartar un borrador.
- Reconoce vocabulario clínico real (valores de ausencia como "NO CONSTA", el caso "paciente sigue
  ingresado", formatos de fecha mixtos), no solo validación genérica de tipos.
- El dashboard inicial es idempotente: no duplica métricas ni paneles si ya existen.
- Está verificado por 17 tests de integración de backend contra base de datos real, y por un ensayo
  manual con un dataset real (`DEMO_MVP_ILQ_part2`) que demuestra el flujo entero, incluida la
  reanudación y el descarte de borradores.

## 9. Limitaciones actuales

Con la misma honestidad que en `docs/estado-mvp.md`: la seguridad está desactivada
(`permitAll()`, sin login), no hay migraciones de base de datos (`ddl-auto=update`), no hay
despliegue en la nube, y el dashboard inicial usa heurísticas por nombre de columna, no un análisis
semántico real. Ir más allá del dashboard inicial (indicadores a medida) requiere configuración
manual en la zona avanzada. Es un MVP funcional, no un producto listo para producción.

## 10. Próximos pasos

Por prioridad: autenticación real y protección de endpoints, migraciones de base de datos
(Flyway/Liquibase), empaquetado y despliegue, tests de frontend, y exportación de resultados
(PDF/Excel). Detalle completo en `docs/estado-mvp.md`.

## 11. Cierre

Dashboard Preventiva demuestra, de principio a fin, que se puede pasar de un Excel clínico con
errores típicos a un dashboard con indicadores en minutos, sin programar y sin perder la trazabilidad
de lo que se corrigió. Es un MVP — no un producto de producción — pero el flujo completo funciona,
está probado, y el camino para cerrar las limitaciones conocidas ya está identificado.
