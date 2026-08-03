# Demo cronometrada — 7 minutos

Versión compacta, para defensa con tiempo limitado, del guion completo en
`docs/demo/guion-demo-mvp.md` (que sigue siendo la referencia detallada). Usa el mismo archivo:
`docs/demo/ilq_demo_mvp.csv`.

**Red de seguridad para toda la demo**: el dataset `DEMO_MVP_ILQ_part2` ya existe, activo, con su
dashboard inicial ya generado (verificado en la fase 6.8G.3). Si algo falla en cualquier bloque de
la importación en vivo, se puede saltar directamente a `/datasets` → `DEMO_MVP_ILQ_part2` y seguir
la defensa desde ahí sin perder el hilo. Tenlo abierto en una pestaña de repuesto antes de empezar.

---

### Minuto 0-1 — Inicio y problema

**Pantalla**: Inicio (`http://localhost:5173`).

**Decir**: "Los hospitales exportan sus registros de vigilancia de infección quirúrgica a Excel o
CSV, con nombres de columna y formatos distintos según quién exporte. Normalmente eso se limpia a
mano en el propio Excel, sin dejar rastro de qué se corrigió. Esta aplicación lo automatiza: detecta
las columnas, valida los datos, permite corregir sin tocar el archivo original, y genera un
dashboard en minutos."

**No tocar**: no entrar todavía en "Configuración avanzada" ni en `/datasets` — se enseña al final.

**Riesgo de fallo**: el frontend no ha arrancado o no conecta con el backend (badge de estado en
rojo en la esquina).

**Plan B**: si el badge de estado indica que el backend no responde, dilo en voz alta ("el backend
no ha arrancado, un segundo") y arráncalo ahí mismo (`mvnw.cmd spring-boot:run` ya debería estar
lanzado desde antes — ver `checklist-defensa.md`); mientras carga, sigue explicando el problema de
palabra sin depender de la pantalla.

---

### Minuto 1-2 — Subida de CSV/Excel

**Pantalla**: "Crear dashboard" → paso "Sube el archivo".

**Decir**: "Subo un archivo real de exportación, con los errores típicos de una exportación
hospitalaria ya incluidos a propósito: fechas mal escritas, un identificador de paciente vacío,
formatos de fecha distintos entre filas."

**Acción**: subir `ilq_demo_mvp.csv`, pulsar "Analizar archivo".

**No tocar**: no abrir "Opciones avanzadas" (índice de hoja / fila de cabecera) salvo que algo salga
mal con la detección — no hace falta para este CSV.

**Riesgo de fallo**: el archivo no se encuentra en el selector (ruta distinta en el portátil de la
defensa).

**Plan B**: ten el CSV también en el escritorio o en una carpeta de acceso directo, por si el
selector de archivos no recuerda la ruta del repositorio.

---

### Minuto 2-3 — Columnas detectadas y dataset

**Pantalla**: paso "Revisa las columnas detectadas" → paso "Nombre del dashboard".

**Decir**: "La aplicación ha adivinado, solo por el nombre de cada columna, el tipo de dato y el
rol clínico — identificador de paciente, fecha principal, diagnóstico... Sin plantilla previa, sin
configurar nada." Poner un nombre (p. ej. "ILQ Demo") y pulsar "Crear e importar".

**No tocar**: no entrar a editar manualmente el tipo de cada columna una por una — con señalar 2-3
basta para transmitir la idea.

**Riesgo de fallo**: la app marca columnas "sospechosas" o duplicadas (no debería pasar con este
CSV, pero si la fila de cabecera se leyó mal).

**Plan B**: si aparece ese aviso, es que la fila de cabecera no se leyó bien; vuelve a "Subir
archivo" y confirma que "Fila de cabecera" está en `0`. Si no hay tiempo de arreglarlo, salta al
dataset ya existente (ver red de seguridad arriba).

---

### Minuto 3-4 — Errores, corrección y exclusión

**Pantalla**: panel "Corregir errores en la app" (tras pulsar "Corregir errores en la app" en la
pantalla de validación).

**Decir**: "El archivo no entra limpio a la primera, y es intencionado. A partir de aquí trabajo
sobre una copia interna: el archivo original no se modifica en ningún momento." Corrige la fecha de
cirugía inválida de una fila (editar la celda y guardar). Luego localiza la fila casi vacía y
exclúyela: "este registro no tiene datos suficientes; en vez de perder tiempo completándolo, lo
excluyo — queda registrado que se excluyó, no se pierde silenciosamente."

**No tocar**: no demuestres en vivo "rellenar columna" ni "normalizar columna" si vas justo de
tiempo — nómbralos ("también hay rellenar en bloque y normalizar fechas, os las enseño luego si hay
interés") en vez de ejecutarlos, para no comerte el minuto siguiente.

**Riesgo de fallo**: al guardar la celda corregida, la fecha introducida no tiene el formato
esperado y el error no desaparece.

**Plan B**: usa una fecha con formato `dd/mm/aaaa` (p. ej. `07/01/2026`) — es el formato más seguro,
soportado siempre. Si el error persiste, sigue adelante e importa igualmente señalando que "quedaría
pendiente de corregir, y no bloquea el resto del archivo".

---

### Minuto 4-5 — Importación y trazabilidad

**Pantalla**: botón "Importar datos corregidos" → pantalla de resultado → "Ver trazabilidad de la
importación".

**Decir**: "Con los errores bloqueantes resueltos, importo." (esperar el resultado) "Aquí se ve
cuántas filas se importaron, cuántas se excluyeron y cuántas advertencias quedaron sin bloquear
nada." Abrir trazabilidad: "cada corrección y exclusión queda registrada con fecha y valor anterior/
nuevo — clave para auditoría en un entorno clínico."

**No tocar**: no navegues a "Ver dataset" todavía — eso se deja para el minuto 6-7.

**Riesgo de fallo**: la importación tarda más de lo esperado o el navegador parece congelado.

**Plan B**: son 15 filas, debería ser prácticamente instantáneo; si tarda, sigue hablando de
trazabilidad mientras carga. Si falla de verdad, salta directamente a `DEMO_MVP_ILQ_part2` (ya
importado) y muestra su trazabilidad en su lugar — el histórico de eventos ya está ahí.

---

### Minuto 5-6 — Dashboard inicial

**Pantalla**: botón "Crear dashboard inicial" desde el resultado de la importación.

**Decir**: "Un solo clic genera automáticamente los indicadores relevantes a partir de las columnas
que acabo de importar: total de registros, edad media, indicadores clínicos booleanos, distribución
por sexo y procedimiento... sin configurar nada a mano." Dejar que navegue al dashboard y señalar 2-3
widgets concretos.

**No tocar**: no entres a "Configurar widgets" ni edites nada del panel en directo — el objetivo es
enseñar que ya está generado, no editarlo.

**Riesgo de fallo**: alguna métrica queda "omitida" (mensaje "se creó con algunos indicadores
omitidos").

**Plan B**: es un comportamiento normal y ya documentado (no todas las columnas dan pie a un
indicador claro): dilo en voz alta ("algún indicador se omite si el campo no encaja, no bloquea el
resto") y sigue. Si el dashboard no carga en absoluto, abre directamente el de
`DEMO_MVP_ILQ_part2` desde `/datasets` como respaldo.

---

### Minuto 6-7 — Dataset activo, arquitectura y cierre

**Pantalla**: `/datasets` → fila `DEMO_MVP_ILQ_part2` (o el dataset recién creado) → "Ver dashboard".

**Decir**: "Este mismo dashboard se puede volver a abrir en cualquier momento desde 'Datasets', sin
tener que reimportar nada; y si alguien interrumpe una importación a mitad, el dataset queda como
borrador y se retoma exactamente donde se dejó." Cierre técnico breve: "Por debajo, el backend es
Spring Boot con Postgres, y el frontend React con Vite; todo el flujo que acabáis de ver pasa por un
modelo de dataset configurable, no por un esquema fijo — así se adapta a distintos servicios sin
tocar código. Es un MVP: funciona de principio a fin y está probado, pero seguridad y despliegue
siguen pendientes, como se detalla en la documentación."

**No tocar**: no entres a "Configurar métricas"/"Configurar paneles" salvo que pregunten
explícitamente — son zona avanzada, no hace falta para el cierre.

**Riesgo de fallo**: quedarse sin tiempo antes de llegar al cierre técnico.

**Plan B**: el cierre ("es un MVP, funciona de principio a fin, seguridad/despliegue pendientes")
es la única frase que no puede faltar; si el tiempo aprieta, sacrifica el detalle de arquitectura
pero no el cierre ni el reconocimiento de limitaciones.
