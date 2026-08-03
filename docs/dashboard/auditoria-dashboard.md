# Auditoría de dashboard, métricas y diseño visual (Fase 6.9A)

Auditoría del sistema actual de métricas/KPIs/paneles/widgets/dashboard, backend y frontend, antes
de implementar mejoras visuales o edición tipo drag & drop. Solo lectura de código (y, donde se
indica, verificación puntual de endpoints reales) — **no se ha modificado backend, frontend ni
Docker en esta fase**.

---

## 1. Backend de métricas y paneles

### 1. Qué representa una métrica

`MetricaClinica`: un indicador calculable sobre los registros de un dataset. Tiene `codigo`,
`nombre`, `descripcion`, `tipoMetrica` (`CONTEO`, `PORCENTAJE`, `PROMEDIO`, `SUMA`, `DISTRIBUCION`),
una `configuracion` en JSONB (filtros, campo de valor, campo de agrupación, numerador/denominador
según el tipo — `ConfiguracionMetricaDto`), `unidad`, `decimales`, `orden` y `activa`. Es
independiente de cómo se visualiza: una misma métrica puede aparecer en varios widgets con
visualizaciones distintas (`PanelMetrica.tipoVisualizacion` es propio del widget, no de la métrica).

### 2. Qué representa un KPI

No existe una entidad "KPI" separada. Un KPI es, simplemente, una `MetricaClinica` mostrada con
`tipoVisualizacion = KPI` (o `TARJETA`) en un widget cuyo resultado es un valor único (`ACTUAL`, sin
`items`). Si esa misma métrica tuviera `items` (por ejemplo, una `DISTRIBUCION`), el frontend cae a
tabla en vez de al número grande (ver más abajo, `DashboardWidgetRenderer`).

### 3. Qué representa un panel

`PanelClinico`: la agrupación de widgets que forma un dashboard. Tiene `codigo`, `nombre`,
`descripcion`, `orden` y `activo`. Un dataset puede tener varios paneles (el "dashboard inicial" es,
técnicamente, un panel más, identificado solo por convención de código: `dashboard_inicial`).

### 4. Qué representa un widget

`PanelMetrica`: la relación entre un panel y una métrica, más su configuración de visualización.
Tiene `tituloPersonalizado`/`descripcionPersonalizada` (opcionales, si no se usa el de la métrica),
`tipoVisualizacion` (enum: `KPI, TARJETA, TABLA, BARRAS, LINEAS, DONUT, PIE`), `orden`, `ancho`,
`activa`, `tipoResultadoWidget` (`ACTUAL`/`SERIE_TEMPORAL`/`COMPARATIVA`, opcional — si es `null` se
infiere, ver punto 11) y `configuracionWidget` (JSONB: `granularidad`, `campoFecha`,
`campoSegmentacion`, `campoAgrupacion` — `ConfiguracionWidgetDto`).

### 5. Dónde se guarda el tipo de visualización

`PanelMetrica.tipoVisualizacion` (columna `tipo_visualizacion`, enum de texto). Es propiedad del
widget, no de la métrica: la misma métrica puede tener un widget en `BARRAS` en un panel y otro en
`TABLA` en otro.

### 6. Dónde se guarda el orden

`PanelMetrica.orden` (entero simple). El dashboard ordena los widgets con
`Comparator.comparing(PanelMetrica::getOrden)` (`DashboardPanelServiceImpl.obtenerDashboard`) — es
un **orden lineal (1D)**, no una posición en una rejilla 2D.

### 7. Si existe posición/tamaño

Solo parcialmente: existe `ancho` (entero, pensado como fracción de una rejilla de 12 columnas —
los valores usados hoy son 3/6/12, ver `reglasMetricas.ts` y `DashboardWidgetRenderer.module.css`:
`grid-column: span var(--span, 3)`). **No existe alto/altura, ni fila, ni coordenadas x/y.** La
altura de cada widget la decide el propio contenido (gráfico SVG de alto fijo, tabla de alto
variable) dentro de un CSS Grid con `align-items: start` — no hay control de la posición vertical,
solo el orden de aparición y cuántas columnas ocupa.

### 8. Si existe configuración visual adicional

Sí, pero limitada y ad hoc: `ConfiguracionWidgetDto` (granularidad, campo de fecha, de segmentación,
de agrupación) decide *qué calcula* el widget (serie temporal vs. comparativa), no *cómo se ve*
(color, icono, decimales por widget — los decimales están en la métrica, no en el widget). No hay
campos de estilo (color de acento, icono, tamaño de fuente) en ningún nivel.

### 9. Qué endpoints crean métricas

- `POST /api/datasets-clinicos/{datasetId}/metricas` — crea una métrica persistida.
- `POST /api/datasets-clinicos/{datasetId}/metricas/preview` — evalúa una configuración de métrica
  sin persistirla (usado también, fuera del contexto de edición manual, por
  `datasetTieneRegistros()` en el frontend para comprobar si un dataset tiene datos).
- `PUT /api/metricas-clinicas/{id}` — actualizar; `DELETE /api/metricas-clinicas/{id}` — desactivar
  (borrado lógico, `activa=false`, no elimina filas).

### 10. Qué endpoints crean paneles/widgets

- `POST /api/datasets-clinicos/{datasetId}/paneles` — crear panel.
- `POST /api/paneles-clinicos/{id}/metricas` — añadir un widget (métrica + visualización) a un
  panel.
- `PUT /api/paneles-clinicos/{id}/metricas/{panelMetricaId}` — actualizar un widget completo
  (incluye `orden` y `ancho`: ya se podría reordenar/redimensionar por columnas hoy mismo llamando a
  este endpoint, solo que no hay UI que lo haga arrastrando).
- `PUT /api/paneles-clinicos/{panelId}/metricas/{panelMetricaId}/configuracion-widget` — solo
  `tipoResultadoWidget`/`configuracionWidget` (qué calcula, no cómo se ve).
- `DELETE /api/paneles-clinicos/{id}/metricas/{panelMetricaId}` — desactivar (quitar del panel).

No existe un endpoint de "guardar el layout completo del panel de una vez" (por ejemplo, un `PUT`
con la lista completa de widgets y su nuevo orden/ancho en una sola llamada) — hoy, reordenar N
widgets exigiría N llamadas `PUT` individuales.

### 11. Qué endpoint ejecuta el dashboard

`POST /api/paneles-clinicos/{id}/dashboard` (`DashboardPanelController.obtenerDashboard` →
`DashboardPanelServiceImpl`). Por cada `PanelMetrica` activo (con su métrica también activa),
ordenado por `orden`:

1. Resuelve qué tipo de resultado calcular (`resolverTipoResultado`): si el widget fija
   `tipoResultadoWidget` explícitamente, se usa ese; si no, se infiere de
   `tipoVisualizacion`+`tipoMetrica`+si hay `campoAgrupacion` configurado (p. ej. `KPI`/`TARJETA` →
   siempre `ACTUAL`; `BARRAS`/`TABLA`/`DONUT`/`PIE` con `campoAgrupacion` → `COMPARATIVA`).
2. Ejecuta ese cálculo (`ejecutarActual`/`ejecutarSerieTemporal`/`ejecutarComparativa`, delegando en
   `MetricaClinicaService`/`MetricaAnaliticaService`).
3. Si falla, el widget individual queda en `estado=ERROR` con un mensaje — **un widget roto no
   tumba el resto del dashboard**, cada uno se calcula de forma aislada (`try/catch` por widget).

También existen, aparte, `POST /api/metricas-clinicas/{id}/serie-temporal` y `.../comparativa` (para
recalcular una métrica suelta con otros filtros/agrupación sin pasar por un panel entero) y
`GET /api/paneles-clinicos/{id}/dashboard-metadata` (metadata para construir formularios: qué campos
de fecha/agrupación son válidos para cada widget).

### 12. Si ya hay base para drag & drop persistente

**Parcial.** Ya existe: `orden` (persistible, editable vía `PUT`) y `ancho` (columnas de una rejilla
de 12, también persistible). **No existe**: alto/fila/posición 2D, ni un endpoint de guardado de
layout en bloque. Es decir, hay base de sobra para "arrastrar para reordenar" y "cambiar cuántas
columnas ocupa", pero no para una rejilla libre estilo "mover a cualquier posición x/y y
redimensionar libremente en ambos ejes" sin añadir columnas nuevas a `PanelMetrica` (o una tabla de
layout aparte).

---

## 2. Frontend de dashboard

### 1. Cómo se renderiza actualmente un KPI

`KpiWidget.tsx`: número grande (`font-size: 2.75rem`, `font-weight: 600`) + unidad opcional +
subtítulo opcional +, si la métrica es de tipo `PORCENTAJE`, "X de Y" debajo. Sin icono, sin color
de acento configurable, sin tendencia/sparkline, sin comparación con el periodo anterior.

### 2. Cómo se renderiza actualmente una gráfica

Con **SVG dibujado a mano** (`BarChartWidget`, `LineChartWidget`, `PieChartWidget`), usando
primitivas propias de escala/bandas (`charts/escalas.ts`). No son componentes triviales: manejan
tooltip on-hover, leyenda, huecos de datos nulos en líneas (sin interpolar, con marca de punto
aislado), agrupación en "Otros" a partir del quinto sector en donut/pie, y cambio automático de
barras verticales a horizontales cuando hay muchas categorías o etiquetas largas
(`convieneHorizontal`). El `viewBox` de cada SVG es de tamaño fijo (p. ej. 480×260) y escala
proporcionalmente al contenedor (`width:100%; height:auto`), así que es responsive en el sentido de
"se encoge", pero **no reordena ni ajusta su contenido** (número de ticks, longitud de etiquetas) al
espacio disponible real.

### 3. Qué librería de gráficas se usa ahora, si alguna

**Ninguna.** Es SVG e hijos de React de mano, deliberadamente (comentario explícito en
`escalas.ts`: "No hay dependencias externas a propósito"). `package.json` del frontend solo tiene
`react`, `react-dom` y `react-router` como dependencias de producción.

### 4. Qué tipos de visualización existen

`KPI`, `TARJETA`, `TABLA`, `BARRAS`, `LINEAS`, `DONUT`, `PIE` (enum `TipoVisualizacion`, compartido
backend/frontend). `DashboardWidgetRenderer` decide, para cada tipo, qué componente usar según la
forma real del resultado (`resultadoActual` simple, `items` de distribución/comparativa, o
`serieTemporal`) — con una cascada de "si no encaja, cae a tabla" (p. ej. una serie temporal con más
de 4 series se muestra en tabla en vez de un gráfico de líneas ilegible).

### 5. Qué parte es CSS puro

El layout del dashboard (`PanelDashboardPage.module.css`: CSS Grid de 12 columnas + media queries),
el ancho de cada widget (`DashboardWidgetRenderer.module.css`, variable `--span`), y el sistema de
diseño base (`styles/variables.css`, `styles/global.css`: colores, espaciados, radios, sombras,
tipografía del cuerpo, botones). Los propios gráficos son SVG generado en JS, no CSS.

### 6. Qué parte está acoplada al backend

La forma del `DashboardWidgetDto` (`tipoVisualizacion`, `tipoResultado`, y uno de
`resultadoActual`/`serieTemporal`/`comparativa` según el caso) dicta directamente qué rama de
`DashboardWidgetRenderer` se ejecuta — el frontend no decide nada por sí mismo sobre qué calcular,
solo cómo pintar lo que ya llega calculado. Esto es bueno para consistencia, pero significa que
cualquier cambio de layout/tamaño en frontend (por ejemplo, una rejilla libre con alto variable) no
necesita tocar esta parte del contrato, mientras que cambiar *qué* se calcula sí requeriría tocar
ambos lados.

### 7. Qué limitaciones visuales hay

- Tamaño de gráfico fijo en píxeles (no se adapta al ancho real del widget más allá de escalar
  proporcionalmente): un widget de `ancho=3` y uno de `ancho=12` con la misma gráfica de barras
  tienen el mismo `viewBox` interno, solo cambia cuánto se encoge visualmente.
- Altura no controlable: un dashboard con KPIs cortos y gráficas largas en la misma fila queda con
  "escalones" (cada tarjeta con su propia altura de contenido, alineadas arriba).
- Sin modo oscuro (`variables.css` solo define `:root`, sin `prefers-color-scheme` ni tema
  alternativo).
- Sin tokens tipográficos formales (tamaños de fuente puestos a mano por componente: `2.75rem`,
  `1.6rem`, `0.85rem`... sin una escala documentada ni reutilizada de forma sistemática).
- Sin foco visible/accesibilidad explícita más allá de lo que da el navegador por defecto (no hay
  `:focus-visible` personalizado en `global.css`).

### 8. Qué componentes habría que rediseñar primero

Por impacto visual y frecuencia de uso: `KpiWidget` (la primera impresión de cualquier dashboard),
`DashboardWidgetRenderer`/`PanelDashboardPage` (el contenedor y la rejilla en sí), y los tres charts
SVG (`BarChartWidget`/`LineChartWidget`/`PieChartWidget`) para dar más aire, mejor legibilidad de
ejes y una paleta más rica que la mínima actual de 4 colores + "Otros".

---

## 3. Calidad visual actual — clasificación de problemas

| Problema | Severidad | Detalle |
|---|---|---|
| Altura de widgets no alineada en la misma fila | MEDIO | CSS Grid con `align-items: start`; sin filas de altura fija, un dashboard mixto (KPIs + gráficas) queda visualmente irregular. |
| Sin modo oscuro | BAJO | Solo hay tokens de tema claro en `variables.css`. |
| Escala tipográfica no sistematizada | BAJO/MEDIO | Tamaños de fuente puestos ad hoc por componente, sin una escala documentada. |
| Gráficos de tamaño de viewBox fijo | MEDIO | Se escalan pero no reflowan contenido (ticks, longitud de etiqueta) al espacio real. |
| Paleta de gráficos limitada a 4 colores + "Otros" | MEDIO | Suficiente para pocas categorías; con datasets de más variedad clínica puede quedarse corta. |
| Edición de widgets como formulario crudo (orden/ancho en texto) | ALTO | `PanelWidgetsPage`/`WidgetForm`: no hay vista previa ni manipulación visual, solo campos numéricos — funciona, pero no "se ve" como un editor de dashboard. |
| Sin feedback de "guardando/guardado" granular en edición de widgets | BAJO | Existe un `guardando` global pero no confirmación visual por campo. |
| Estados vacíos (sin datos, tipo incompatible) | BAJO | Ya están cubiertos con mensajes claros (`ChartEmptyState`), no bloquean el uso. |
| Responsive del grid | BAJO | Ya implementado con 3 breakpoints (12/6/1 columnas) — funciona razonablemente bien. |
| Diferencia modo usuario/avanzado | BAJO | Ya existe la distinción (dashboard inicial vs. `/paneles/.../widgets`); es coherente, solo podría reforzarse visualmente (p. ej. un badge "modo avanzado" en la pantalla de widgets). |
| Nada es BLOQUEANTE | — | El dashboard es utilizable de principio a fin hoy mismo; los problemas son de pulido, no de funcionalidad rota. |

**Conclusión de esta sección**: no hay nada que impida usar el dashboard hoy. El problema real es que
se ve "de admin panel" (formularios CRUD, tarjetas homogéneas, sin jerarquía fuerte) en vez de "de
producto" — que es exactamente lo que motivó esta fase.

---

## 4. Arquitectura propuesta para un dashboard editable

### Cómo debería funcionar

- **Modo visualización** (el que existe hoy, por defecto): solo lectura, sin controles de
  arrastre/resize visibles — es lo que ve cualquier usuario que solo consulta el dashboard.
- **Modo edición** (nuevo, activado con un botón "Editar dashboard" visible solo para quien ya entra
  a "Configurar widgets"): los widgets muestran asas de arrastre y, al menos en una primera fase,
  controles simples de ancho (no necesariamente un resize de arrastre libre desde el primer momento
  — ver fases 6.9D/6.9E más abajo).
- **Drag & drop**: reordenar widgets arrastrando dentro del mismo panel. Con el modelo de datos
  actual (`orden` 1D), esto es un *reordenamiento de lista*, no una colocación libre en una rejilla
  2D — encaja de forma natural con una librería de "sortable list", no con una de "grid layout".
- **Resize**: en una primera fase, cambiar el `ancho` (3/6/12) con un control explícito (botón o
  select), no con un asa de arrastre — más simple, cero riesgo de layout roto, y ya cubierto por el
  dato que existe hoy. Un resize de arrastre libre (y con alto variable) es una fase posterior y
  requiere ampliar el modelo de datos (ver abajo).
- **Guardar layout**: al salir del modo edición (o en cada cambio, con debounce), persistir
  orden/ancho de los widgets modificados. Hoy exigiría una llamada `PUT` por widget cambiado; merece
  la pena valorar un endpoint de guardado en bloque si el número de widgets por panel crece.
- **Restablecer layout**: no existe hoy ningún concepto de "layout por defecto" guardado aparte —
  restablecer tendría que ser "recrear el dashboard inicial" (ya existe, `crearDashboardInicial`) o
  añadir un campo de layout "de fábrica" si se quiere una función de reset más fina.
- **Responsive**: ya resuelto razonablemente por el CSS Grid actual (12/6/1 columnas); un modo
  edición debería mantener ese comportamiento y, simplemente, no ofrecer arrastre en el breakpoint
  de una sola columna (no aporta nada reordenar una lista que ya es de una columna).
- **Estructura de datos para el layout**: con el modelo actual (`orden` + `ancho`) alcanza para
  "reordenar + cambiar ancho". Para una rejilla verdaderamente libre (posición y alto arbitrarios)
  haría falta añadir `fila`/`alto` (o `x`/`y`/`w`/`h`) a `PanelMetrica`, o una tabla de layout aparte
  si se quiere desacoplar "definición del widget" de "dónde se coloca en este panel concreto".

### Cambios mínimos necesarios en backend (si se implementara la fase de reordenar)

- Ninguno estructural: `orden` y `ancho` ya existen y ya son editables vía
  `PUT /api/paneles-clinicos/{id}/metricas/{panelMetricaId}`.
- Recomendable (no obligatorio): un endpoint de guardado en bloque, p. ej.
  `PUT /api/paneles-clinicos/{id}/metricas/orden` con una lista `[{panelMetricaId, orden, ancho}]`,
  para no disparar N peticiones al soltar un widget arrastrado. Alcance pequeño: un método de
  servicio que itere y guarde, más el DTO de request.

### Cambios mínimos necesarios en frontend (si se implementara la fase de reordenar)

- Un "modo edición" en `PanelDashboardPage` (o una vista dedicada) que envuelva
  `DashboardWidgetRenderer` con un contenedor arrastrable.
- Estado local optimista del orden mientras se arrastra, con guardado al soltar.
- Un control de ancho simple (ciclar 3 → 6 → 12, o un select) en modo edición.
- Nada de esto exige tocar `DashboardWidgetRenderer` en sí (sigue pintando igual); el cambio vive en
  el contenedor que lo envuelve.

### Comparación de opciones

**A. CSS Grid manual sin drag & drop.**
Coste mínimo (ya está prácticamente hecho): solo faltaría exponer un control de ancho más visual y,
quizá, botones "subir/bajar" para reordenar sin arrastre real. Cero dependencias nuevas, cero riesgo.
No da la experiencia de "arrastrar" que se pide evaluar, aunque cubre "editable" de forma más humilde.

**B. React Grid Layout (`react-grid-layout`).**
Da de fábrica drag + resize + persistencia de layout + breakpoints responsive, con un modelo de
datos `{x, y, w, h}` por elemento. Pero: (1) asume filas de altura fija en unidades de rejilla —
encaja mal con el patrón actual de "alto según contenido" (KPI corto, tabla larga), y forzarlo
exigiría fijar alturas por tipo de widget o vivir con recorte/scroll interno; (2) es una dependencia
relativamente pesada, con su propio CSS a importar y su propia gestión de breakpoints que solaparía
con la que ya existe; (3) el modelo de datos que exige (`x`/`y`/`w`/`h`) es más de lo que el caso de
uso actual necesita (una lista reordenable con ancho, no una rejilla libre) — sería sobre-ingeniería
para la primera fase de "poder arrastrar para reordenar".

**C. dnd-kit (`@dnd-kit/core` + `@dnd-kit/sortable`).**
Da una lista arrastrable/reordenable (con soporte de teclado y accesibilidad ya resueltos), sin
imponer un modelo de rejilla — encaja de forma natural con el `orden` 1D que ya existe hoy, sin
tener que añadir `x`/`y`/alto todavía. Es más ligero y modular que react-grid-layout, y no exige un
sistema de alturas fijas: cada widget conserva su alto por contenido, solo cambia su posición en el
flujo. El resize (cambio de `ancho`) se resolvería aparte, con un control simple, no con un asa de
arrastre — eso puede añadirse después sin rehacer la base de reordenamiento.

**D. Implementación propia (drag nativo del navegador o eventos de puntero a mano).**
Control total y cero dependencias, pero reinventir accesibilidad de teclado, soporte táctil y
detección de colisión con la calidad que ya da una librería mantenida es un esfuerzo considerable
para un beneficio marginal frente a la opción C.

### Recomendación

**dnd-kit (opción C)** para la primera fase de edición (reordenar arrastrando + control de ancho
simple), y **posponer** una rejilla libre estilo react-grid-layout a una fase posterior y solo si de
verdad se necesita posicionamiento arbitrario (no solo reordenar una lista) — momento en el que
también habría que ampliar el modelo de datos (`fila`/`alto` o `x`/`y`/`w`/`h`) de todos modos, así
que no tiene sentido adoptar esa complejidad antes de necesitarla. Justificación: encaja con el
modelo de datos que ya existe (mínimo cambio), es más ligero, y no fuerza un sistema de alturas fijas
que hoy no existe y que rompería el patrón visual actual (widgets de alto variable por contenido).

---

## 5. Fases de mejora propuestas

### 6.9A — Auditoría actual
*(esta fase)* Objetivo: entender el estado real antes de tocar nada. Alcance: solo documentación.
Archivos: `docs/dashboard/auditoria-dashboard.md`. Riesgos: ninguno (no se toca código). Criterio de
éxito: este documento, aprobado.

### 6.9B — Rediseño visual estático del dashboard
Objetivo: mejorar jerarquía visual, espaciado, tipografía y tarjetas KPI sin tocar la lógica de
datos ni añadir interacción nueva. Alcance: CSS y estructura de componentes existentes
(`KpiWidget`, `Card`, `DashboardWidgetRenderer`, `PanelDashboardPage`), posible ampliación de
`variables.css` (escala tipográfica, quizá tokens de tema oscuro). Archivos probables:
`*.module.css` de `components/dashboard/`, `styles/variables.css`, `styles/global.css`. Riesgos:
romper el responsive actual si se cambia el grid sin revisar los 3 breakpoints; bajo si se prueba
visualmente antes de dar por cerrada la fase. Criterios de éxito: mismo comportamiento funcional,
mejor legibilidad y jerarquía a ojo, sin regresión en `npm run build`/`lint`.

### 6.9C — Mejora de gráficas y KPIs
Objetivo: pulir los tres charts SVG (mejor manejo de espacio, más colores en la paleta si hace
falta, KPI con más contexto —p. ej. comparación simple con el periodo anterior si el dato ya está
disponible—). Alcance: `BarChartWidget`/`LineChartWidget`/`PieChartWidget`/`charts/escalas.ts`,
`KpiWidget`. Sin librería nueva (se mantiene el enfoque SVG propio, ya de buena calidad). Archivos
probables: los mismos componentes de charts + su CSS. Riesgos: medio — tocar la lógica de escalas
puede introducir regresiones sutiles en casos límite (un solo dato, todos ceros, valores negativos);
mitigar con pruebas manuales de esos casos límite antes de cerrar. Criterios de éxito: los mismos
casos límite ya cubiertos hoy (datos vacíos, un único valor, muchas categorías) se siguen viendo
bien.

### 6.9D — Modelo persistente de layout (preparación, sin drag todavía)
Objetivo: preparar el terreno de datos/API para editar sin todavía exponer arrastre en la UI.
Alcance backend: valorar el endpoint de guardado en bloque de orden/ancho (opcional, no
obligatorio dado que el `PUT` individual ya existe). Alcance frontend: un "modo edición" con
controles explícitos (no arrastre aún) para reordenar (subir/bajar) y cambiar ancho, sobre
`PanelWidgetsPage` o una vista nueva. Archivos probables: `PanelClinicoController`/`PanelMetricaService`
(si se añade el endpoint en bloque), `PanelWidgetsPage.tsx`, nuevo componente de "modo edición".
Riesgos: bajo — es aditivo, no reemplaza nada existente. Criterios de éxito: se puede reordenar y
cambiar ancho sin recargar la página y sin arrastre, como paso intermedio verificable antes de
añadir dnd-kit.

### 6.9E — Drag & resize en modo edición
Objetivo: incorporar `dnd-kit` para arrastrar y reordenar widgets visualmente, con guardado
automático del nuevo orden. Alcance: nueva dependencia (`@dnd-kit/core`, `@dnd-kit/sortable`),
contenedor de arrastre alrededor de `DashboardWidgetRenderer` en modo edición. Resize real
(arrastre para redimensionar, no solo ciclar ancho) queda como alcance opcional de esta fase o se
pospone si exige ampliar el modelo de datos a alto variable. Archivos probables:
`PanelDashboardPage.tsx` (o una vista de edición dedicada), nuevo componente de "widget arrastrable",
`package.json` (nueva dependencia). Riesgos: medio-alto — primera dependencia de interacción nueva
del proyecto; cuidado con accesibilidad de teclado (dnd-kit la da, pero hay que verificar que se usa
bien) y con no romper el modo visualización normal. Criterios de éxito: arrastrar reordena
visualmente y persiste (recargar la página mantiene el nuevo orden); modo visualización sin cambios.

### 6.9F — Biblioteca de widgets y configuración avanzada
Objetivo: hacer más fácil añadir/configurar widgets sin pasar por formularios crudos (selector
visual de tipo de visualización con preview, configuración avanzada más guiada). Alcance: rediseño
de `WidgetForm`/`WidgetConfigForm`, posible catálogo visual de tipos de widget. Archivos probables:
`components/paneles/WidgetForm.tsx`, `WidgetConfigForm.tsx`, `PanelWidgetsPage.tsx`. Riesgos: bajo —
es UI de configuración, no afecta al cálculo ni al modo visualización. Criterios de éxito: añadir o
configurar un widget se siente como parte del producto, no como un CRUD administrativo genérico.

---

## Resumen ejecutivo

- El backend ya tiene todo lo necesario (dato y endpoints) para **reordenar** y **cambiar ancho** de
  widgets; lo que falta es la interacción de arrastre en el frontend, no el dato.
- No hay ninguna base para una **rejilla libre** (posición y alto arbitrarios): eso exigiría ampliar
  el modelo de datos, y se recomienda posponerlo hasta que de verdad se necesite.
- Los gráficos son SVG hechos a mano, sin librería, y de calidad razonable ya hoy (tooltips,
  leyenda, casos límite cubiertos) — el problema visual no es la falta de gráficas, es la falta de
  jerarquía/pulido alrededor de ellas.
- Ningún problema encontrado es bloqueante; todos son de pulido (MEDIO/ALTO como mucho), lo cual
  encaja con que el dashboard ya es funcional de principio a fin.
- Recomendación de librería para drag & drop: **dnd-kit**, no react-grid-layout, por encajar mejor
  con el modelo de datos y el patrón visual (alto variable) que ya existe.
