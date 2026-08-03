# Guía de usuario

Cómo usar Dashboard Preventiva de principio a fin, sin necesidad de conocimientos técnicos.

## 1. Entrar en Inicio

La pantalla de inicio (`/`) resume el flujo recomendado y da acceso directo a crear un dashboard o
a la configuración avanzada.

## 2. Pulsar "Crear dashboard"

Desde Inicio, o desde el menú superior, entra en el asistente guiado (`/crear-dashboard`).

## 3. Subir CSV/Excel

Sube el archivo clínico exportado por el hospital. No hace falta prepararlo de antemano ni definir
columnas: la aplicación lee la cabecera directamente.

## 4. Revisar columnas

La aplicación detecta, por el nombre de cada columna, su tipo de dato (texto, número, fecha, Sí/No)
y su rol clínico (identificador de paciente, fecha principal, diagnóstico...). Puedes corregir
cualquier sugerencia, cambiar el tipo de una columna, marcarla como no obligatoria, o decidir no
usarla.

## 5. Configurar dataset

Da un nombre y un identificador al dashboard que se va a crear.

## 6. Corregir errores

Si el archivo tiene datos que no se pueden interpretar (una fecha imposible, un número mal escrito,
un identificador de paciente vacío), la aplicación los agrupa por columna y tipo de problema.
Puedes corregir cada celda directamente en la pantalla.

**Importante: a partir de aquí trabajas sobre una copia interna. El archivo que subiste no se
modifica en ningún momento**, así que puedes probar, corregir y deshacer sin miedo a estropear el
original.

## 7. Excluir filas inválidas

Si una fila no tiene datos suficientes para ser útil (por ejemplo, casi todos los campos vacíos),
puedes excluirla directamente en vez de corregirla campo a campo. Las filas excluidas no se
importan, pero quedan registradas: puedes revisarlas o incluirlas de nuevo más tarde.

## 8. Rellenar columnas

Cuando el mismo problema se repite en muchas filas (por ejemplo, una columna opcional vacía),
"Rellenar columna" aplica el mismo valor a todas las filas afectadas de una vez, en lugar de
corregirlas una a una.

## 9. Normalizar fechas

Si una columna de fechas tiene formatos distintos entre filas (aunque todos sean válidos:
`01/02/2026`, `1-2-2026`, `2026-02-01`...), "Normalizar columna" los deja todos en el mismo
formato.

## 10. Importar

Cuando ya no queden errores bloqueantes, se importan los datos corregidos. El resultado muestra
cuántas filas se importaron, cuántas se excluyeron y cuántas advertencias quedaron (advertencias que
no bloquean, como campos opcionales sin informar).

## 11. Crear/ver dashboard inicial

Justo después de importar —o en cualquier momento posterior, desde el detalle de un dataset activo—
puedes generar un dashboard inicial automáticamente: la aplicación propone métricas básicas (total
de registros, edad media, indicadores clínicos relevantes, distribución por sexo/procedimiento/
diagnóstico...) y las agrupa en un panel, sin que tengas que configurar nada a mano. Si el dashboard
ya existe, el mismo botón te lleva directamente a verlo.

## 12. Consultar trazabilidad

En cualquier momento (desde el resultado de la importación o desde la propia copia de trabajo)
puedes abrir el historial de trazabilidad: qué se corrigió, qué se excluyó, qué se rellenó o
normalizó, y cuándo. **Todas las correcciones quedan trazadas**, con el valor anterior y el nuevo.

## 13. Reanudar borrador

Si interrumpes una importación a mitad (cierras el navegador, se corta la conexión...), el dataset
queda como "borrador". Desde "Datasets" → "Pruebas y borradores" → "Continuar creación" retomas
exactamente el mismo punto en el que lo dejaste, sin crear un dataset duplicado ni perder las
correcciones ya hechas.

## 14. Gestionar datasets activos

Desde "Datasets" ves todos los datasets ya importados (activos). Cada uno permite: ver su detalle,
ver o crear su dashboard, y acceder a la configuración avanzada (campos, plantillas, métricas,
paneles).

## 15. Flujo principal vs. zona avanzada

La aplicación distingue dos niveles:

- **Flujo principal** (recomendado para empezar): "Crear dashboard" + "Ver/crear dashboard inicial".
  Cubre el caso de uso habitual — importar un archivo y tener indicadores en minutos — sin que el
  usuario tenga que entender conceptos como "métrica" o "panel".
- **Zona avanzada** (`/datasets`, marcada como "Avanzado" en el menú): gestión manual de campos,
  plantillas de importación, métricas y paneles. **Las métricas y paneles de la zona avanzada son
  configuración manual**: sirven para ir más allá de lo que genera el dashboard inicial (indicadores
  a medida, paneles adicionales, filtros específicos), pero no son necesarios para el uso normal de
  la aplicación. El dashboard inicial es siempre el camino recomendado para empezar con un dataset
  nuevo.
