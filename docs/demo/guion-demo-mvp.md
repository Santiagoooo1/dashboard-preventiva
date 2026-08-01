# Guion de demo — Dashboard Preventiva (5-10 minutos)

Usa el archivo `ilq_demo_mvp.csv` de esta misma carpeta. Antes de empezar, revisa
`checklist-demo.md`.

---

**Paso 1 — Inicio**
Acción: abrir la app en `http://localhost:5173`.
Mensaje: "Esta herramienta convierte exportaciones clínicas de Excel/CSV en dashboards de
vigilancia de infección quirúrgica, sin programar ni depender de un informático para cada
cambio."
Resultado esperado: pantalla de inicio con el flujo recomendado y el botón "Crear dashboard desde
Excel/CSV".

**Paso 2 — Ir a "Crear dashboard"**
Acción: pulsar "Crear dashboard desde Excel/CSV".
Mensaje: "Partimos de cero: no hace falta configurar nada antes de subir el archivo."
Resultado esperado: asistente guiado, paso "Sube el archivo".

**Paso 3 — Subida de archivo**
Acción: subir `ilq_demo_mvp.csv` y pulsar "Analizar archivo".
Mensaje: "Este archivo es realista: tiene 15 registros con los errores típicos de una exportación
hospitalaria — fechas mal escritas, un identificador de paciente vacío, formatos de fecha
distintos, un campo numérico con 'NO CONSTA'…"
Resultado esperado: la app pasa al paso "Revisa las columnas detectadas".

**Paso 4 — Columnas detectadas automáticamente**
Acción: mostrar la tabla de columnas detectadas (HC, SEXO, EDAD, FECHA CIRUGÍA…).
Mensaje: "La aplicación ha adivinado, solo por el nombre de cada columna, si es texto, número,
fecha o sí/no, y qué papel clínico cumple (paciente, fecha, diagnóstico…). Podemos corregir
cualquier sugerencia aquí mismo, por ejemplo cambiar 'CIRUGÍA URGENTE' a tipo Sí/No."
Resultado esperado: columnas con tipo y rol ya rellenados; el campo HC marcado como identificador
de paciente obligatorio.
Riesgo que resuelve: cada hospital exporta con nombres y orden de columnas distintos; no hace
falta una plantilla fija ni tocar código para adaptarse.

**Paso 5 — Configurar el dataset**
Acción: continuar a "Nombre del dashboard", poner un nombre (p. ej. "ILQ Demo") y pulsar "Crear e
importar".
Mensaje: "Con esto se crea el dataset, sus campos y la importación queda lista para validarse."
Resultado esperado: la app valida el archivo y detecta errores bloqueantes.

**Paso 6 — Errores detectados**
Acción: mostrar el resumen "columnas con errores / advertencias / sugerencias disponibles".
Mensaje: "El archivo no entra limpio a la primera, y es intencionado: así vemos cómo la app ayuda
a corregirlo sin tener que volver a Excel."
Resultado esperado: pantalla "Revisa las columnas detectadas" con errores agrupados (fecha de
cirugía inválida, edad no numérica, HC vacío).
Riesgo que resuelve: sin esto, un solo dato mal escrito obligaría a editar el Excel original y
volver a subirlo entero, perdiendo todo el trabajo de revisión ya hecho.

**Paso 7 — Corregir errores en la app**
Acción: pulsar "Corregir errores en la app".
Mensaje: "A partir de aquí trabajamos sobre una copia interna. El archivo original no se toca en
ningún momento."
Resultado esperado: panel "Corregir errores en la app" con los problemas agrupados por columna.

**Paso 8 — Corregir una celda**
Acción: abrir el grupo de "fecha de cirugía" con formato inválido, editar el valor de esa celda a
una fecha válida y guardar.
Mensaje: "Corregimos el dato directamente aquí, celda a celda si hace falta."
Resultado esperado: el error desaparece de la lista tras guardar.

**Paso 9 — Excluir una fila**
Acción: localizar la fila casi vacía (HC vacío, edad "ND") y excluirla en vez de corregirla.
Mensaje: "Este registro no tiene datos suficientes para ser útil. En vez de perder tiempo
completándolo a mano, lo excluimos: no se importa, pero queda registrado que se excluyó y por
qué."
Resultado esperado: la fila pasa a "Filas excluidas"; deja de contar como error bloqueante.
Riesgo que resuelve: registros basura o incompletos ya no obligan a limpiar el archivo fuera de la
app antes de importar.

**Paso 10 — Rellenar columna**
Acción: en el grupo "MICROOR." (vacío en la mayoría de las filas, porque solo se informa cuando
hay cultivo), usar "Rellenar columna" con el valor "No informado".
Mensaje: "Cuando el mismo problema se repite en muchas filas — aquí en unas diez de golpe —, no
hace falta corregirlas una a una."
Resultado esperado: todas las filas activas con esa columna vacía quedan con el valor aplicado y
desaparecen del grupo de advertencias. Las dos filas que sí tienen microorganismo informado
(las que tuvieron infección) no se tocan.

**Paso 11 — Normalizar columna**
Acción: en una columna de fecha con formatos mixtos (`1/2/2026`, `10-01-2026`, `05/01/2026`), usar
"Normalizar columna".
Mensaje: "Aunque todos estos formatos son válidos, no son consistentes. Normalizar los deja todos
iguales, listos para analizarse."
Resultado esperado: los valores de esa columna quedan en el mismo formato.

**Paso 12 — Caso clínico especial**
Acción: señalar la fila con "FECHA ALTA: SIGUE INGRESADO".
Mensaje: "Esto no es un error de escritura: es que el paciente sigue ingresado y no tiene fecha de
alta todavía. La aplicación lo reconoce como un caso clínico válido, no como un dato roto."
Resultado esperado: esa fila aparece como advertencia informativa, no como error bloqueante.
Riesgo que resuelve: evita que la validación fuerce a "inventar" una fecha de alta que no existe.

**Paso 13 — Importar**
Acción: pulsar "Importar datos corregidos".
Mensaje: "Con los errores bloqueantes resueltos, importamos los datos corregidos."
Resultado esperado: pantalla "Datos importados correctamente" con el resumen (filas importadas,
excluidas, advertencias).

**Paso 14 — Ver resultado y crear dashboard inicial**
Acción: pulsar "Crear dashboard inicial".
Mensaje: "La aplicación sugiere y crea automáticamente los indicadores más relevantes a partir de
las columnas que acabamos de importar: tasa de infección, adecuación de profilaxis, cirugías por
mes…"
Resultado esperado: dashboard con widgets ya generados, sin configuración manual.

**Paso 15 — Consultar trazabilidad**
Acción: volver al resultado y pulsar "Ver trazabilidad de la importación".
Mensaje: "Cada corrección, exclusión y normalización queda registrada con fecha y valor anterior/
nuevo. Esto es clave para auditoría en un entorno clínico."
Resultado esperado: línea de tiempo con los eventos en orden (copia creada, celda corregida, fila
excluida, columna rellenada, importación realizada).

**Paso 16 — Ver dataset y cerrar con valor del producto**
Acción: ir a "Ver dataset" y mostrar campos/métricas/paneles.
Mensaje: "Desde aquí se puede seguir ajustando el dashboard, añadir más indicadores o revisar la
configuración completa. Y si alguien interrumpe la importación a mitad — cierra el navegador,
falla la conexión — el dataset queda como borrador y se retoma exactamente donde se dejó, sin
perder las correcciones ya hechas."
Resultado esperado: detalle del dataset con su configuración y, opcionalmente, mostrar
"Pruebas y borradores" en `/datasets`.
