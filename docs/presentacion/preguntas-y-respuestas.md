# Preguntas y respuestas de defensa

Respuestas honestas, defendibles, y coherentes con `docs/estado-mvp.md`, `docs/arquitectura.md` y
`docs/guia-usuario.md`. Ninguna promete producción, seguridad completa ni despliegue cloud que no
existan.

## 1. ¿Qué problema resuelve exactamente?

Convertir exportaciones Excel/CSV de vigilancia de infección quirúrgica —con los errores habituales
de una exportación real: nombres de columna distintos, fechas mal escritas, campos vacíos— en un
dataset validado, corregible sin tocar el archivo original, y con un dashboard de indicadores
generado automáticamente.

## 2. ¿Por qué no usar directamente Excel o Power BI?

Excel no valida ni deja rastro de qué se corrigió; limpiar datos ahí es manual, no repetible y no
auditable. Power BI (u otra herramienta de BI genérica) es potente para visualizar, pero no resuelve
la parte previa: detectar y corregir errores de una exportación clínica con vocabulario específico
(fechas mixtas, "paciente sigue ingresado", valores de ausencia clínica). Esta aplicación cubre
justo ese hueco — importar y corregir de forma guiada— y además genera un dashboard inicial sin
configurar nada; no pretende sustituir a una herramienta de BI para análisis avanzado.

## 3. ¿Qué aporta frente a un dashboard manual?

Tres cosas que un proceso manual no da gratis: (1) corrección sin tocar el archivo original —se
trabaja sobre una copia interna—, (2) trazabilidad de cada cambio (qué se corrigió, cuándo, valor
anterior y nuevo), y (3) un dashboard inicial generado automáticamente en cuanto los datos están
importados, sin que alguien tenga que configurar métricas y paneles a mano para empezar a ver algo.

## 4. ¿Qué pasa si el archivo viene con errores?

Se detectan fila a fila contra el tipo de dato esperado y se muestran agrupados por columna,
separando lo que bloquea la importación de lo que es solo advertencia. Se pueden corregir celda a
celda, excluir filas irrecuperables, rellenar un mismo valor en bloque para un problema repetido, o
normalizar formatos inconsistentes (fechas, booleanos). Nada de esto modifica el archivo subido.

## 5. ¿Se modifica el archivo original?

No, nunca. Todas las correcciones ocurren sobre una copia interna (`ImportacionTrabajo`); el archivo
que se sube no se reescribe en ningún punto del proceso, y se puede volver a intentar desde cero en
cualquier momento.

## 6. ¿Cómo se garantiza trazabilidad?

Cada acción sobre la copia interna (crearla, corregir una celda, excluir una fila, rellenar o
normalizar una columna, restaurar al original, importar) queda registrada como un evento con fecha,
tipo y —cuando aplica— valor anterior y nuevo (`EventoImportacionTrabajo`). Se puede consultar en
cualquier momento desde la app, tanto durante la corrección como después de importar.

## 7. ¿Qué pasa con datos clínicos sensibles?

Aquí hay que ser honestos: **la aplicación, tal como está, no tiene control de acceso ni cifrado
específico para datos sensibles** —no hay autenticación activa (ver pregunta 9)—. No está pensada
para usarse hoy con datos identificativos de pacientes reales fuera de un entorno interno y
controlado; sería el primer bloque de trabajo (junto con autenticación) antes de manejar datos
clínicos reales fuera de un entorno de pruebas.

## 8. ¿Está listo para producción?

No. Es un MVP funcional: el flujo completo funciona de principio a fin y está probado, pero faltan
autenticación, migraciones de base de datos versionadas, y un objetivo de despliegue. Está detallado
sin ambigüedad en `docs/estado-mvp.md`.

## 9. ¿Por qué no hay autenticación todavía?

Priorización de MVP: el foco de esta fase era demostrar que el flujo completo (importar, corregir,
trazar, generar dashboard) funciona y aporta valor de principio a fin. `Usuario`/`Rol` ya están
modelados en el dominio (`ADMIN`/`MEDICO`) y hay un `BCryptPasswordEncoder` configurado, pero no hay
login ni protección de endpoints —`SecurityConfig` permite todas las peticiones—. Es el primer punto
de la lista de próximos pasos, no un descuido invisible.

## 10. ¿Por qué usas ddl-auto=update?

Por velocidad durante el desarrollo del MVP: el esquema se genera automáticamente desde las
entidades, sin tener que mantener migraciones a mano en cada fase. Es una decisión consciente y
temporal, no la elección para un sistema en producción — `docs/estado-mvp.md` lo marca explícitamente
como algo a sustituir por Flyway/Liquibase antes de evolucionar el esquema de forma segura.

## 11. ¿Qué limitaciones tiene el dashboard inicial?

Es heurístico: decide qué métricas proponer por coincidencias de texto en el nombre de la columna
(`reglasMetricas.ts`), no por un análisis semántico real ni por configuración específica de cada
hospital. Puede proponer un indicador que no encaje perfectamente, u omitir uno relevante si la
columna tiene un nombre poco habitual. Por eso conviene el corte con la zona avanzada: siempre se
puede ajustar o añadir métricas a mano.

## 12. ¿Qué parte es más compleja técnicamente?

La copia interna de trabajo y su corrección asistida: mantener corregible/deshacer/excluible cada
celda y fila sin tocar el archivo original, revalidar tras cada cambio, y decidir qué cuenta como
"importable", todo evitando que dos acciones concurrentes dejen el estado inconsistente. Muy cerca
en complejidad: la heurística de detección de columnas del frontend (adivinar tipo de dato y rol
clínico solo por el nombre) y la reconstrucción de columnas al reanudar un borrador sin tener ya el
archivo original en el navegador.

## 13. ¿Qué mejorarías con más tiempo?

Por este orden: autenticación real, migraciones de base de datos, y tests de frontend (hoy la
cobertura automática es solo de backend). Después, exportación de resultados y roles de usuario
visibles en la UI, no solo modelados en el dominio.

## 14. ¿Cómo escalarías esto a varios hospitales?

Hoy no hay aislamiento por hospital activado en el flujo real: `DatasetClinico` tiene un campo
`hospitalId` en el modelo, pero el asistente guiado no lo rellena todavía —cualquier dataset creado
hoy no queda asociado a un hospital concreto—. Escalarlo implicaría: activar y exigir ese vínculo,
añadir autenticación con el hospital como límite de acceso (multi-tenancy), y decidir si cada
hospital tiene su propia base de datos o comparten una con aislamiento lógico por filas.

## 15. ¿Cómo probarías que funciona?

Ya está probado en dos niveles: 17 tests de integración de backend contra una base de datos Postgres
real (no mocks/H2), cubriendo la copia de trabajo, sus correcciones, la trazabilidad y la reanudación
de borradores; y un ensayo manual end-to-end con un dataset real
(`DEMO_MVP_ILQ_part2`, visible ahora mismo en `/datasets`) que demuestra el flujo completo, incluida
la reanudación y el descarte de borradores. Lo que falta —y está reconocido— son tests automáticos de
frontend.

## 16. ¿Qué riesgos tiene el enfoque heurístico?

Falsos positivos/negativos al detectar tipo de columna o proponer métricas si el nombre de la
columna es ambiguo o poco habitual (p. ej. una columna llamada de forma muy distinta a lo esperado
no se detectaría bien). Se mitiga dejando siempre corregible la sugerencia antes de continuar —el
usuario revisa y ajusta antes de crear nada—, pero no hay aprendizaje ni configuración por hospital
que reduzca ese riesgo con el tiempo.

## 17. ¿Qué diferencia hay entre flujo principal y zona avanzada?

El flujo principal (`/crear-dashboard`) es el camino recomendado: subir un archivo y llegar a un
dashboard sin que el usuario tenga que entender conceptos como "métrica" o "panel". La zona avanzada
(`/datasets`) es gestión manual de campos, plantillas, métricas y paneles, para quien necesite ir más
allá de lo que genera el dashboard inicial —no es necesaria para el uso normal de la aplicación—.

## 18. ¿Por qué hay dos pipelines de importación en el backend?

Por evolución del proyecto: el primero (`Cirugia` + tablas hijas, `PlantillaExcel` fija por
departamento) fue el diseño inicial, pensado para un esquema clínico fijo. Según el proyecto avanzó
se construyó uno genérico y configurable (`DatasetClinico`/`CampoClinico`/`ImportacionTrabajo`), que
es el que usa hoy todo el frontend. El primero sigue compilando pero ya no lo llama la UI —es deuda
técnica reconocida, no una elección de arquitectura activa—, y está pendiente decidir si se retira o
se documenta formalmente como alternativa.

## 19. ¿Qué harías para desplegarlo?

En este orden: autenticación real y migraciones de base de datos primero (no tiene sentido
desplegar sin eso), luego un Dockerfile de la aplicación (hoy solo Postgres corre en Docker),
externalizar configuración por variables de entorno (hoy la cadena de conexión está fija en
`application.properties`), y un pipeline de CI/CD mínimo antes de elegir un objetivo de despliegue
concreto (cloud u on-premise, según dónde vaya a vivir el dato clínico).

## 20. ¿Qué aprendiste desarrollándolo?

Que el trabajo más valioso no fue la importación en sí, sino diseñar un sistema donde corregir datos
sea seguro (sin tocar el original) y trazable, porque eso es lo que un entorno clínico realmente
exige. También aprendí a base de iteración que las heurísticas de detección automática necesitan
siempre un punto de revisión humano antes de continuar —no basta con "adivinar bien la mayoría de
las veces"— y que documentar honestamente las limitaciones (en vez de ocultarlas) hace el proyecto
más fácil de defender, no menos.
