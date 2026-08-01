# Demo MVP — Dashboard Preventiva

Explicación del flujo principal de la aplicación, pensada para una demo oral de 5-10 minutos.
El archivo de datos y el guion paso a paso están en esta misma carpeta
(`ilq_demo_mvp.csv` y `guion-demo-mvp.md`).

## Qué es

Dashboard Preventiva convierte exportaciones clínicas (Excel/CSV) en dashboards de vigilancia de
infección de localización quirúrgica (ILQ) sin que nadie tenga que programar ni escribir SQL. El
usuario sube un archivo, la aplicación detecta las columnas, valida los datos, permite corregir
errores sin tocar el archivo original, y genera un dashboard inicial con indicadores clínicos.

## Flujo principal

1. **Entrar en la app** (`/`) — pantalla de inicio con el flujo recomendado y acceso directo a
   "Crear dashboard".
2. **Ir a "Crear dashboard"** (`/crear-dashboard`) — arranca el asistente guiado.
3. **Subir un CSV/Excel clínico** — se usa `ilq_demo_mvp.csv`, un archivo realista con errores
   habituales de una exportación hospitalaria.
4. **Revisar columnas detectadas** — la aplicación infiere, por el nombre de cada columna, su tipo
   de dato (texto, número, fecha, sí/no) y su rol clínico (paciente, fecha, diagnóstico...). El
   usuario puede corregir cualquier sugerencia antes de continuar.
5. **Configurar dataset** — nombre e identificador del dashboard a crear.
6. **Detectar errores** — la aplicación valida cada fila contra el tipo de dato esperado y marca
   fechas imposibles, números no numéricos, identificadores de paciente vacíos, etc.
7. **Corregir errores** — dentro de la propia app, sin modificar el archivo original: se trabaja
   sobre una copia interna.
8. **Excluir una fila si procede** — para registros irrecuperables (p. ej. una fila casi vacía),
   en vez de corregir campo a campo.
9. **Usar "rellenar columna"** — aplica un mismo valor a todas las filas que comparten un problema
   (p. ej. "No informado" en una columna opcional vacía en varias filas).
10. **Usar "normalizar columna"** — unifica formatos dispares (fechas escritas de formas distintas,
    booleanos como `1`/`0` vs `SI`/`NO`) a un formato consistente.
11. **Importar** — una vez el archivo es importable, se cargan los registros corregidos.
12. **Ver resultado** — resumen de filas importadas, excluidas y advertencias.
13. **Crear/ver dashboard inicial** — la aplicación sugiere y crea automáticamente métricas y un
    panel a partir de las columnas importadas.
14. **Consultar trazabilidad** — historial completo y ordenado de qué se corrigió, excluyó o
    normalizó, y cuándo.
15. **Ver dataset creado** — detalle del dataset: campos, métricas, paneles.
16. **Reanudar un borrador si se interrumpe** — si el proceso se corta a medias, el dataset queda
    en "Pruebas y borradores" y se puede continuar exactamente donde se dejó.

## Por qué importa cada paso

- Los pasos 4-6 evitan la plantilla rígida: cada hospital/servicio exporta sus columnas en un
  orden y con nombres distintos, y la app se adapta sin configuración previa.
- Los pasos 7-10 son el núcleo del valor añadido: los errores de una exportación real (fechas mal
  escritas, campos vacíos, formatos mixtos) se resuelven sin depender de que alguien edite el
  Excel original y lo vuelva a subir a ciegas.
- El paso 8 (excluir) reconoce que no todos los errores merecen corregirse fila a fila.
- El paso 14 (trazabilidad) da garantía de auditoría: cualquier corrección queda registrada.
- El paso 16 (reanudación) evita perder trabajo si la persona que importa se interrumpe.
