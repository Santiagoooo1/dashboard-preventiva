# Estado del MVP

## Funcionalidades completadas

- Asistente guiado de importación, con detección automática de columnas por nombre (sin plantilla
  previa).
- Validación de filas contra el tipo de dato y la obligatoriedad de cada columna, con
  reconocimiento de vocabulario clínico (valores de ausencia, formatos de fecha mixtos, vocabulario
  Sí/No ampliado, caso especial "paciente sigue ingresado").
- Copia interna de trabajo, independiente del archivo original.
- Corrección de celdas, individual y con deshacer.
- Exclusión de filas (individual y en bloque, por tipo de problema).
- Rellenar columna (corrección en bloque para un mismo problema repetido).
- Normalizar columna (unifica formatos de fecha/booleano/número/texto).
- Restaurar copia al estado original (deshace todo de golpe).
- Importación desde la copia de trabajo corregida.
- Activación de dataset tras una importación con éxito.
- Dashboard inicial automático: generación de métricas y panel a partir de los campos importados,
  accesible tanto tras importar como desde cualquier dataset activo, con comprobación de
  idempotencia (no duplica si ya existe).
- Trazabilidad completa y ordenada de cada acción sobre una copia de trabajo.
- Reanudación de borradores interrumpidos, sin duplicar datasets ni perder correcciones.
- Descarte de borradores (con confirmación, sin pantallas fantasma tras el descarte).
- Suite de tests críticos de backend (17 tests, integración contra Postgres real) sobre la copia de
  trabajo, sus correcciones, la trazabilidad y la reanudación de borradores.
- Material de demo completo: CSV realista con errores intencionados, guion paso a paso y checklist.

## Limitaciones actuales

- **Seguridad**: `SecurityConfig` permite todas las peticiones (`permitAll()`); no hay login,
  sesión ni JWT. El usuario admin sembrado por `DataSeeder` no protege nada todavía.
- **No lista para producción tal cual**: falta autenticación, autorización por rol, y revisión de
  seguridad general antes de exponer la aplicación fuera de un entorno controlado.
- **Sin migraciones de base de datos**: no hay Flyway/Liquibase; el esquema se genera con
  `spring.jpa.hibernate.ddl-auto=update`, lo que no es seguro para evolucionar un esquema en
  producción.
- **Pensada para entorno local**: no hay Dockerfile de la aplicación ni configuración de
  despliegue; solo Postgres corre en Docker.
- **Sin despliegue cloud**: no hay pipeline de CI/CD ni infraestructura configurada más allá del
  entorno de desarrollo local.
- **Dashboard inicial heurístico**: las métricas propuestas se basan en coincidencias de nombre de
  columna (`reglasMetricas.ts`), no en un análisis semántico real ni en configuración por hospital;
  puede proponer o pasar por alto indicadores según cómo esté nombrada la columna.
- **Métricas/paneles avanzados son configuración manual**: ir más allá del dashboard inicial (
  indicadores a medida, filtros específicos) requiere usar la zona avanzada.
- **Sin tests de frontend**: la cobertura automática está solo en el backend; la verificación de
  frontend depende de build/lint y revisión manual.
- **Código heredado sin retirar**: el pipeline de importación específico de Cirugía/ILQ
  (`Cirugia`, `PlantillaExcel`, `ImportacionExcelController`...) sigue en el backend pero ya no lo
  usa el frontend — pendiente de decidir si se retira o se documenta como alternativa.

## Próximos pasos recomendados

1. **Seguridad/autenticación**: login real (sesión o JWT), proteger endpoints, roles ADMIN/MEDICO
   ya modelados en el dominio pero sin aplicar.
2. **Migraciones de base de datos**: introducir Flyway o Liquibase antes de que el esquema crezca
   más, y desactivar `ddl-auto=update`.
3. **Empaquetado y despliegue**: Dockerfile de la aplicación, variables de entorno para
   configuración (hoy la cadena de conexión está fija en `application.properties`), y un objetivo
   de despliegue (cloud o on-premise).
4. **Tests de frontend**: al menos smoke tests del asistente guiado y de la corrección de filas.
5. **Mejoras visuales**: pulido adicional de la experiencia en pantallas grandes/proyector para
   demos, y revisión de accesibilidad básica.
6. **Exportación de resultados**: PDF/Excel del dashboard y de los indicadores, para compartir sin
   necesidad de acceder a la aplicación.
7. **Roles de usuario**: distinguir en la UI (no solo en el dominio) entre quien importa datos y
   quien solo consulta dashboards.
8. **Hardening clínico**: revisión con un perfil clínico real de qué vocabulario/formatos faltan
   por cubrir, más allá de los ya identificados en las fases de pulido.
9. **Decidir el destino del pipeline heredado**: retirarlo (reduce superficie de mantenimiento) o
   documentarlo formalmente como vía alternativa si todavía tiene uso previsto.
