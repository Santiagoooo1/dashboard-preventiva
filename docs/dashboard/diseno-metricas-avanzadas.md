# Diseño — constructor avanzado de métricas (Fase 6.9D)

Auditoría y propuesta de diseño, sin implementar. Basado en lectura directa del código
(`MetricaClinicaServiceImpl`, `MetricaAnaliticaServiceImpl`, `MetricaCalculoBasico`,
`WidgetConfigForm`, `MetricaConfigForm`, `FiltroBuilder`), no en suposiciones.

## Hallazgo principal, antes de entrar en detalle

El backend ya es considerablemente más flexible de lo que la UI deja ver. La mayoría de los casos de
uso planteados (A-F) **ya funcionan hoy**, combinando dos formularios que hoy están desconectados: la
configuración de la propia métrica (filtros, campo de valor) y la configuración del widget que la
muestra (agrupación, segmentación). El problema no es de motor de cálculo, es de que **nada en la
aplicación explica que ambos formularios trabajan juntos**. Los huecos reales (tabla cruzada,
múltiples métricas en un widget, cohortes por regla arbitraria) son mucho más acotados de lo que
parecía al plantear la fase.

---

## 1. Estado actual

### Qué representa hoy una métrica

`MetricaClinica`: `tipoMetrica` (`CONTEO`, `PORCENTAJE`, `PROMEDIO`, `SUMA`, `DISTRIBUCION`) +
`configuracion` (JSONB, `ConfiguracionMetricaDto`):

```java
class ConfiguracionMetricaDto {
    List<FiltroMetricaDto> filtros;     // CONTEO, PROMEDIO, SUMA, DISTRIBUCION
    FiltroGrupoDto numerador;           // solo PORCENTAJE
    FiltroGrupoDto denominador;         // solo PORCENTAJE
    String campoValor;                  // solo PROMEDIO, SUMA
    String campoAgrupacion;             // solo DISTRIBUCION (una única dimensión)
}
class FiltroMetricaDto { String campo; OperadorFiltro operador; Object valor; }
class FiltroGrupoDto { List<FiltroMetricaDto> filtros; }
```

`OperadorFiltro`: `EQ, NE, IN, NOT_IN, GT, GTE, LT, LTE, IS_NULL, NOT_NULL, CONTAINS` — ya bastante
completo, con compatibilidad por tipo de dato ya resuelta en el catálogo
(`CampoMetricaMetadataDto.operadoresCompatibles`) y ya expuesta en frontend (`FiltroBuilder.tsx`, con
selector de campo → operador → input tipado según el tipo de dato).

### Qué representa hoy un widget (más allá de la visualización)

`PanelMetrica` (el widget) tiene, **además** de `tipoVisualizacion`: `tipoResultadoWidget`
(`ACTUAL`/`SERIE_TEMPORAL`/`COMPARATIVA`, o `null` = automático) y `configuracionWidget` (JSONB,
`ConfiguracionWidgetDto`):

```java
class ConfiguracionWidgetDto {
    Granularidad granularidad;      // solo SERIE_TEMPORAL
    String campoFecha;              // solo SERIE_TEMPORAL
    String campoSegmentacion;       // solo SERIE_TEMPORAL — segunda dimensión (tiempo × campo)
    String campoAgrupacion;         // solo COMPARATIVA — agrupa el resultado por un campo
}
```

**Esto ya existe y ya tiene UI** (`WidgetConfigForm.tsx`, accesible desde "Configurar widgets" →
"Configurar" en cada fila). Es la pieza que hace posibles la mayoría de los casos de uso sin tocar
nada.

### Qué cálculos soporta hoy cada tipo, exactamente

- **CONTEO**: cuenta registros que cumplen `filtros` (lista AND).
- **PORCENTAJE**: `numerador.filtros` / `denominador.filtros`, dos listas de filtros independientes;
  el resultado es `(count(numerador) / count(denominador)) * 100`.
- **PROMEDIO** / **SUMA**: sobre `campoValor` (debe ser ENTERO o DECIMAL), tras aplicar `filtros`.
- **DISTRIBUCION**: cuenta registros agrupados por `campoAgrupacion` (una dimensión), tras aplicar
  `filtros`. Es la única que ya trae agrupación **dentro de la propia métrica**, sin depender del
  widget.

### Cómo se combina con el widget: el mecanismo que ya soporta "X por Y"

`MetricaAnaliticaServiceImpl.calcularComparativa()`: agrupa los registros del dataset por
`configuracionWidget.campoAgrupacion` (un único campo) y, **para cada grupo, vuelve a ejecutar el
cálculo completo de la métrica** (`MetricaCalculoBasico.calcular`, la misma lógica de
`calcularConteo`/`calcularPorcentaje`/`calcularPromedio`/`calcularSuma`, incluidos los filtros propios
de la métrica). Es decir: **cualquier métrica CONTEO/PORCENTAJE/PROMEDIO/SUMA ya se puede desglosar
por una categoría**, sin que el motor de cálculo necesite ningún cambio — solo hace falta que el
widget tenga `tipoResultadoWidget=COMPARATIVA` y `campoAgrupacion` configurado.

De la misma forma, `calcularSerieTemporal()` ya soporta agrupar por **tiempo (periodo) × un segundo
campo categórico** (`campoSegmentacion`), devolviendo varias series nombradas.

### Endpoints

- `POST /api/datasets-clinicos/{datasetId}/metricas/preview` — evalúa una métrica **sin persistir**,
  pero **solo el cálculo base** (sin agrupación de widget: no acepta `campoAgrupacion`/
  `campoSegmentacion`). Para previsualizar "cómo quedaría agrupado por X" hay que crear la métrica y
  el widget de verdad y luego consultar el dashboard — no hay preview de la versión agrupada. **Gap
  de UX identificado**, no de cálculo.
- `POST /api/metricas-clinicas/{id}/comparativa`, `POST /api/metricas-clinicas/{id}/serie-temporal` —
  ya persistidas, requieren id.
- `PUT /api/paneles-clinicos/{panelId}/metricas/{panelMetricaId}/configuracion-widget` — ya permite
  fijar `campoAgrupacion`/`campoSegmentacion`/`granularidad` (usado por `WidgetConfigForm`, ya con UI).
- `GET /api/paneles-clinicos/{id}/dashboard-metadata` — ya expone, por widget,
  `tipoResultadosPermitidos` y los campos válidos para agrupar/segmentar/fechar
  (`camposAgrupacionPermitidos`, `camposFechaPermitidos`).
- `GET /api/catalogo-frontend` — ya expone, por tipo de métrica,
  `requiereCampoValor`/`requiereCampoAgrupacion`/`permiteFiltros`/`permiteSerieTemporal`/
  `permiteComparativa`/`estructuraConfiguracion`/`ejemploConfiguracion`. **Este catálogo ya es, en la
  práctica, la especificación de un asistente guiado** — solo falta una UI que lo use así.

### Qué ya está en backend pero no llega bien al frontend

Nada está "solo en backend": `campoAgrupacion`/`campoSegmentacion` del widget SÍ tienen formulario
(`WidgetConfigForm`). Lo que falta no es exponer un campo oculto, es **la conexión narrativa entre
crear la métrica y configurar el widget** (dos pantallas separadas, sin ningún texto que las
relacione) y el **preview de la versión agrupada** (sí existe el dato, no existe el endpoint de
previsualización).

### Qué no existe en absoluto

1. Agrupación en **dos dimensiones categóricas simultáneas** (tabla cruzada: fila × columna).
2. Una métrica de PORCENTAJE/PROMEDIO agrupada **dos veces** (p. ej. "% infección por sexo Y
   procedimiento" a la vez) — mismo problema que la tabla cruzada.
3. Un widget con **más de una métrica** dentro de la misma tarjeta.
4. **Cohortes definidas por regla** (conjuntos de filtros con nombre, más allá de
   numerador/denominador de PORCENTAJE) reutilizables para agrupar por ellos.

---

## 2. Casos de uso objetivo — ¿posibles hoy?

| # | Caso | ¿Hoy? | Cómo (o qué falta) |
|---|---|---|---|
| A | Conteo por procedimiento | **Sí** | Métrica `DISTRIBUCION` (campoAgrupacion=procedimiento), o `CONTEO` + widget `COMPARATIVA` (campoAgrupacion=procedimiento). Ambas rutas ya tienen UI. |
| B | Conteo por procedimiento, filtrando infección=Sí | **Sí** | Métrica `CONTEO`/`DISTRIBUCION` con `filtros=[infeccion EQ true]` + widget `COMPARATIVA`/agrupación por procedimiento. Los filtros de la métrica se aplican dentro de cada grupo. |
| C | % de infección por sexo | **Sí** | Métrica `PORCENTAJE` (numerador=infeccion=Sí, denominador=sin filtro) + widget `COMPARATIVA` campoAgrupacion=sexo. `calcularComparativa` reejecuta el % completo por grupo. |
| D | % de infección por procedimiento | **Sí** | Igual que C, campoAgrupacion=procedimiento. |
| E | Edad media por ASA | **Sí** | Métrica `PROMEDIO` (campoValor=edad) + widget `COMPARATIVA` campoAgrupacion=asa. |
| F | Registros por mes segmentados por sexo | **Sí** | Métrica `CONTEO` + widget `SERIE_TEMPORAL` (granularidad=MES, campoSegmentacion=sexo). Ya renderiza varias series con leyenda (límite actual: 4 series, `MAX_SERIES_LINEA`). |
| G | Tabla cruzada procedimiento × infección | **No** | Ningún cálculo agrupa por 2 campos categóricos a la vez. Requiere backend nuevo (sección 5). |
| H | Tabla cruzada sexo × procedimiento | **No** | Mismo gap que G. |
| I | Widget con 2 métricas (total registros + total infecciones) | **Depende** | Si basta con verlas juntas: **ya posible hoy** (dos widgets uno junto a otro, sin cambios). Si se exige una única tarjeta con ambos valores dentro: no existe (`PanelMetrica` es 1:1 con `MetricaClinica`). |
| J | Comparativa entre dos cohortes | **Depende** | Si "cohorte" = valor de un campo ya existente (servicio A vs. B): **ya posible hoy** vía comparativa. Si "cohorte" = grupo definido por una regla de filtros (p. ej. ASA≥3 vs. resto): no existe — ver propuesta de "grupos con nombre" en la sección 3. |

**Conclusión de esta sección**: de los 10 casos planteados, **6 ya son posibles hoy sin tocar una
línea de código** (A-F), 2 son un gap real y acotado (G, H, la misma idea), y 2 dependen de qué se
entienda exactamente por el enunciado (I, J) — en su lectura más simple también ya son posibles hoy.

---

## 3. Propuesta de modelo (extendiendo lo que ya existe, sin tocar base de datos)

Todo esto vive en columnas `jsonb` ya existentes (`metricas_clinicas.configuracion`,
`panel_metricas.configuracion_widget`): **no hace falta ninguna migración de esquema**, solo añadir
campos opcionales a los DTOs que Hibernate ya serializa como JSON.

### 3.1 Tabla cruzada — extensión de `ConfiguracionWidgetDto`

```java
class ConfiguracionWidgetDto {
    // ...campos existentes...
    String campoAgrupacionSecundario; // nuevo, opcional. Si está presente junto a
                                       // campoAgrupacion y tipoResultadoWidget=TABLA_CRUZADA,
                                       // se calcula una matriz fila×columna en vez de una lista.
}
```

Nuevo valor de enum `TipoResultadoWidget.TABLA_CRUZADA` (junto a `ACTUAL`/`SERIE_TEMPORAL`/
`COMPARATIVA`) y nuevo DTO de respuesta:

```java
class TablaCruzadaResponseDto {
    List<String> filas;         // valores de campoAgrupacion
    List<String> columnas;      // valores de campoAgrupacionSecundario
    List<List<Double>> celdas;  // celdas[fila][columna] = valor de la métrica para esa intersección
}
```

El cálculo reutiliza `MetricaCalculoBasico.calcular` igual que hoy `calcularComparativa`, solo que
agrupando dos veces (`groupingBy(fila, groupingBy(columna, ...))`) en vez de una. No hace falta
ninguna estructura de datos nueva en Postgres: sigue leyendo `RegistroClinicoGenerico` igual que
ahora.

### 3.2 Cohortes con nombre — generalización del patrón numerador/denominador

`PORCENTAJE` ya tiene el germen de esto (`numerador`/`denominador` son, en esencia, dos "grupos con
nombre" con sus propios filtros). Generalizarlo a N grupos, reutilizable por cualquier tipo de
métrica:

```java
class ConfiguracionMetricaDto {
    // ...campos existentes...
    List<CohorteDto> cohortes; // nuevo, opcional
}
class CohorteDto {
    String etiqueta;               // "ASA alto", "Cirugía urgente"...
    List<FiltroMetricaDto> filtros;
}
```

Cuando `cohortes` está presente, el widget podría ofrecer un nuevo `tipoResultadoWidget=COHORTES`
que calcula la métrica una vez por cada cohorte definido (reutilizando `FiltroMetricaEvaluator` y
`MetricaCalculoBasico` tal cual) y devuelve una lista `{etiqueta, valor}` — misma forma que
`ComparativaResponseDto`, así que **el frontend no necesita un componente nuevo para pintarlo**,
solo reconocer el nuevo tipo de resultado.

### 3.3 Ejemplo de configuración completa (caso D, ya posible hoy)

```json
// MetricaClinica.configuracion (PORCENTAJE)
{
  "numerador": { "filtros": [{ "campo": "infeccionLocalizacionQuirurgica", "operador": "EQ", "valor": true }] },
  "denominador": { "filtros": [] }
}
// PanelMetrica.configuracionWidget
{
  "campoAgrupacion": "procedimiento"
}
```

No hace falta inventar nada para este caso — es exactamente lo que ya acepta la API hoy.

---

## 4. Visualizaciones necesarias por caso

| Caso | Visualización | ¿Cubierto por los SVG actuales? |
|---|---|---|
| A-E (comparativa/distribución) | Barras (simples), donut si pocas categorías, tabla | Sí, ya implementado (Fase 6.9C). |
| F (serie segmentada) | Líneas multi-serie con leyenda | Sí, ya implementado (límite 4 series). |
| Barras **agrupadas** (2 dimensiones, una por color) | Nueva variante de `BarChartWidget` | **No existe**: el componente actual solo dibuja una serie de barras. Barras agrupadas/apiladas exigirían una reescritura no trivial del cálculo de bandas (`crearBandas` en `escalas.ts` asume una barra por categoría, no N series por categoría). |
| Barras **apiladas** | Igual que arriba | **No existe**, mismo motivo. |
| G, H (tabla cruzada) | Tabla con filas/columnas (grid HTML) | No existe ningún componente de tabla 2D hoy (`DataTable` es de una sola dimensión: lista de filas con columnas fijas). Es una tabla nueva, no una gráfica — más sencilla de construir que barras agrupadas/apiladas. |
| Tabla resumen (fallback general) | Ya existe (`WidgetActual`/`WidgetComparativa`/`WidgetSerieTemporal` vía `TableWidget`) | Sí. |

**Veredicto sobre los SVG manuales**: para lo que ya existe (barras/líneas/donut de una dimensión)
siguen siendo suficientes y de buena calidad (ver auditoría de la fase 6.9A). Para **barras
agrupadas/apiladas** (necesarias si se quiere visualizar una tabla cruzada como gráfico, no solo como
tabla) sí empiezan a quedarse cortos: el modelo de escalas actual (`crearBandas`, pensado para una
barra por categoría) no está diseñado para varias series por categoría, y añadirlo a mano es
significativamente más trabajo que seguir extendiendo el SVG propio. **Si se llega a necesitar
barras agrupadas/apiladas, ese es el momento de reconsiderar una librería de gráficos** — no hace
falta decidirlo ahora, porque la tabla cruzada (G, H) puede resolverse primero como tabla, sin
gráfico.

---

## 5. Cambios mínimos de backend propuestos

Solo si se decide implementar tabla cruzada y/o cohortes (nada de esto hace falta para A-F, que ya
funcionan):

1. **Nuevo valor de enum** `TipoResultadoWidget.TABLA_CRUZADA` (y opcionalmente `COHORTES`).
2. **Nuevo campo opcional** `campoAgrupacionSecundario` en `ConfiguracionWidgetDto` (JSONB, sin
   migración).
3. **Nuevo campo opcional** `cohortes: List<CohorteDto>` en `ConfiguracionMetricaDto` (JSONB, sin
   migración), y el DTO `CohorteDto` nuevo.
4. **Nuevo método** `calcularTablaCruzada` en `MetricaAnaliticaServiceImpl`, análogo a
   `calcularComparativa` pero con doble `groupingBy`.
5. **Nuevo endpoint** `POST /api/metricas-clinicas/{id}/tabla-cruzada` (y su variante de panel si
   hace falta, como ya existe `serie-temporal`/`serieTemporalPanel`).
6. **Extender el preview** (`MetricaClinicaService.preview`) para aceptar, opcionalmente,
   `campoAgrupacion`/`campoAgrupacionSecundario`/`campoSegmentacion` **antes de guardar nada** — hoy
   el preview solo calcula la versión sin agrupar. Esto es independiente de tabla cruzada: ya
   mejoraría el caso C/D/E/F (poder ver el resultado agrupado antes de crear métrica + widget).
7. **Tests**: extender la suite existente de tests de integración (no hay tests de este subsistema
   hoy visibles en `backend/src/test` más allá de los de importación) — al menos un test de
   `calcularTablaCruzada` con datos reales (2 dimensiones, verificar total de celdas) y uno de
   `preview` con agrupación.

**Nada de esto requiere tocar Postgres**: todo vive en las columnas `jsonb` ya existentes
(`configuracion`, `configuracion_widget`).

---

## 6. Propuesta de UI progresiva (frontend)

### Problema actual de UX (no de capacidad)

Hoy, para lograr el caso D ("% infección por procedimiento") un usuario tiene que: 1) crear una
métrica PORCENTAJE (formulario de métrica), sin que nada le diga que "por procedimiento" se
configura en OTRO sitio; 2) ir a "Configurar widgets"; 3) añadir un widget con esa métrica; 4) pulsar
"Configurar" (un botón distinto de "Editar") y ahí, en un tercer formulario, elegir
`tipoResultado=COMPARATIVA` y `campoAgrupacion=procedimiento`. Tres pantallas, ninguna que explique
la relación entre ellas.

### Modo simple (por defecto)

- Nombre.
- Cálculo (CONTEO/PORCENTAJE/PROMEDIO/SUMA/DISTRIBUCION) — como hoy.
- Campo (solo si aplica) — como hoy.
- Filtros — como hoy (`FiltroBuilder`, ya bueno).
- Visualización — como hoy (Fase 6.9C).

Sin cambios: el modo simple de hoy ya es razonable para métricas sin desglose.

### Modo avanzado (nuevo, plegable, "¿Quieres desglosar este resultado por una categoría?")

- **Agrupar por** (un campo) — hoy vive en "Configurar widget"; en modo avanzado debería ofrecerse
  **en el mismo formulario que crea la métrica**, con una vista previa inmediata (necesita el punto 6
  de la sección 5: preview con agrupación).
- **Segmentar por** (un segundo campo, solo si "Agrupar por" es temporal/serie) — igual, hoy vive en
  otro formulario.
- **Tabla cruzada** (agrupar por dos campos a la vez) — nuevo, solo visible si se implementa la
  sección 5.
- **Filtros** — ya existe, se mantiene igual.
- **Vista previa**: tabla/gráfico de ejemplo con los primeros resultados, actualizada al vuelo según
  se cambian agrupación/segmentación/filtros — hoy no existe para la versión agrupada.

La idea central: **un único formulario con una sección plegable "Avanzado"**, en vez de dos
pantallas separadas sin relación aparente. Esto no exige rehacer `MetricaConfigForm` ni
`WidgetConfigForm` desde cero — se pueden **componer** ambos dentro de un único flujo, ocultando la
distinción técnica métrica/widget que hoy el usuario no tiene por qué conocer.

---

## 7. Fases de implementación propuestas

1. **6.9D** *(esta fase)* — auditoría y diseño. Sin código.
2. **6.9E** — unificar la UX de métrica + widget en un solo formulario progresivo (simple/avanzado),
   **sin tocar backend**: los casos A-F ya funcionan, solo falta conectar los formularios existentes
   y añadir el plegable "Avanzado" con agrupar/segmentar reutilizando `WidgetConfigForm` tal cual.
3. **6.9F** — extender `preview` para aceptar agrupación/segmentación antes de guardar (backend
   pequeño + frontend: vista previa en vivo en el modo avanzado).
4. **6.9G** — tabla cruzada: `TABLA_CRUZADA` + `campoAgrupacionSecundario` + endpoint + componente de
   tabla 2D en frontend (casos G, H).
5. **6.9H** *(opcional, solo si se necesita de verdad)* — cohortes con nombre (`CohorteDto`) para
   agrupar por reglas arbitrarias, más allá de valores de campo existentes (caso J en su lectura
   estricta).
6. **6.9I** *(opcional, solo si "una tarjeta con 2 métricas" es un requisito real y no basta con dos
   widgets uno al lado del otro)* — tarjeta compuesta con más de una métrica (caso I en su lectura
   estricta).

Cada fase es independiente y ninguna bloquea a las siguientes; 6.9E ya entrega valor real (los casos
A-F, hoy escondidos) sin ningún riesgo de backend.
