import { useEffect, useMemo, useState } from 'react'
import type {
  BloqueInformeRequestDto,
  CatalogoInformeResponseDto,
  IndicadorDisponibleDto,
  TipoBloqueInforme,
  TipoVisualizacion,
  WidgetDisponibleDto,
} from '../../api/types'
import { obtenerCatalogoInforme } from '../../api/informesApi'
import { Card } from '../Card'
import { FormField } from '../FormField'
import styles from './Informe.module.css'

interface BibliotecaElementosProps {
  onAnadir: (bloque: BloqueInformeRequestDto) => void
  onAnadirVarios: (bloques: BloqueInformeRequestDto[]) => void
  onAbrirComparacion: () => void
}

type FiltroTipo = '' | 'KPI' | 'GRAFICA' | 'TABLA'

/**
 * Panel «Añadir contenido» (Fases 6.9Q y 6.9Q.1).
 *
 * <p>La fuente principal son los widgets que el usuario ya tiene en sus
 * dashboards, con su configuración exacta. Antes esto partía de las métricas e
 * inventaba la representación —«gráfica» quería decir BARRAS—, así que una
 * «Evolución mensual de ILQ» que en el dashboard es una línea temporal mensual
 * aterrizaba en el informe como unas barras que no se parecían a lo que el
 * médico había aprobado.
 *
 * <p>Las métricas que no están en ningún dashboard siguen disponibles, pero en
 * una sección aparte y diciéndolo: ahí sí hay que elegir cómo se representan.
 */
export function BibliotecaElementos({
  onAnadir,
  onAnadirVarios,
  onAbrirComparacion,
}: BibliotecaElementosProps) {
  const [catalogo, setCatalogo] = useState<CatalogoInformeResponseDto | null>(null)
  const [busqueda, setBusqueda] = useState('')
  const [datasetFiltro, setDatasetFiltro] = useState('')
  const [tipoFiltro, setTipoFiltro] = useState<FiltroTipo>('')
  const [marcados, setMarcados] = useState<Set<number>>(new Set())
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    obtenerCatalogoInforme()
      .then(setCatalogo)
      .catch(() => setCatalogo({ dashboards: [], indicadoresSinDashboard: [] }))
      .finally(() => setCargando(false))
  }, [])

  const datasets = useMemo(() => {
    const vistos = new Map<number, string>()
    catalogo?.dashboards.forEach((d) => vistos.set(d.datasetId, d.datasetNombre))
    catalogo?.indicadoresSinDashboard.forEach((i) => vistos.set(i.datasetId, i.datasetNombre))
    return [...vistos].map(([id, nombre]) => ({ id, nombre }))
  }, [catalogo])

  const dashboardsFiltrados = useMemo(() => {
    const termino = busqueda.trim().toLowerCase()
    return (catalogo?.dashboards ?? [])
      .filter((d) => !datasetFiltro || String(d.datasetId) === datasetFiltro)
      .map((d) => ({
        ...d,
        widgets: d.widgets.filter((w) => {
          if (tipoFiltro && categoriaDe(w) !== tipoFiltro) return false
          if (termino === '') return true
          return (
            w.titulo.toLowerCase().includes(termino) ||
            d.panelNombre.toLowerCase().includes(termino) ||
            d.datasetNombre.toLowerCase().includes(termino)
          )
        }),
      }))
      .filter((d) => d.widgets.length > 0)
  }, [catalogo, busqueda, datasetFiltro, tipoFiltro])

  const sueltosFiltrados = useMemo(() => {
    const termino = busqueda.trim().toLowerCase()
    return (catalogo?.indicadoresSinDashboard ?? []).filter((i) => {
      if (datasetFiltro && String(i.datasetId) !== datasetFiltro) return false
      if (termino === '') return true
      return i.nombre.toLowerCase().includes(termino) || i.datasetNombre.toLowerCase().includes(termino)
    })
  }, [catalogo, busqueda, datasetFiltro])

  const alternarMarcado = (panelMetricaId: number) => {
    setMarcados((actual) => {
      const siguiente = new Set(actual)
      if (siguiente.has(panelMetricaId)) siguiente.delete(panelMetricaId)
      else siguiente.add(panelMetricaId)
      return siguiente
    })
  }

  const anadirMarcados = () => {
    const todos = (catalogo?.dashboards ?? []).flatMap((d) => d.widgets)
    const bloques = todos.filter((w) => marcados.has(w.panelMetricaId)).map(bloqueDesdeWidget)
    if (bloques.length === 0) return
    onAnadirVarios(bloques)
    setMarcados(new Set())
  }

  return (
    <Card title="Añadir contenido" className={styles.biblioteca}>
      <FormField label="Buscar">
        <input
          type="search"
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
          placeholder="Buscar widget…"
        />
      </FormField>

      <div className={styles.filtros}>
        <select
          value={datasetFiltro}
          onChange={(e) => setDatasetFiltro(e.target.value)}
          aria-label="Filtrar por dataset"
        >
          <option value="">Todos los datasets</option>
          {datasets.map((d) => (
            <option key={d.id} value={String(d.id)}>
              {d.nombre}
            </option>
          ))}
        </select>
        <select
          value={tipoFiltro}
          onChange={(e) => setTipoFiltro(e.target.value as FiltroTipo)}
          aria-label="Filtrar por tipo"
        >
          <option value="">Todos los tipos</option>
          <option value="KPI">KPI</option>
          <option value="GRAFICA">Gráficas</option>
          <option value="TABLA">Tablas</option>
        </select>
      </div>

      <div className={styles.seccionBiblioteca}>
        <h4>Widgets de dashboard</h4>
        {cargando ? (
          <p className={styles.pendiente}>Cargando…</p>
        ) : dashboardsFiltrados.length === 0 ? (
          <p className={styles.pendiente}>
            {(catalogo?.dashboards.length ?? 0) === 0
              ? 'Todavía no hay dashboards con widgets.'
              : 'Ningún widget coincide con la búsqueda.'}
          </p>
        ) : (
          <>
            {dashboardsFiltrados.map((d) => (
              <div key={d.panelId} className={styles.grupoDashboard}>
                <p className={styles.nombreDashboard}>
                  {d.panelNombre}
                  <span className={styles.elementoOrigen}>{d.datasetNombre}</span>
                </p>
                <ul className={styles.listaElementos}>
                  {d.widgets.map((w) => (
                    <li key={w.panelMetricaId} className={styles.filaWidget}>
                      <label className={styles.marcaWidget}>
                        <input
                          type="checkbox"
                          checked={marcados.has(w.panelMetricaId)}
                          onChange={() => alternarMarcado(w.panelMetricaId)}
                        />
                      </label>
                      <button
                        type="button"
                        className={styles.elemento}
                        onClick={() => onAnadir(bloqueDesdeWidget(w))}
                        // El tipo se muestra para que se vea que el informe
                        // conservará la representación del dashboard.
                        title={`Añadir como ${etiquetaTipo(w)}`}
                      >
                        {w.titulo}
                        <span className={styles.elementoOrigen}>{etiquetaTipo(w)}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
            {marcados.size > 0 && (
              <button type="button" className="btn btnPrimary" onClick={anadirMarcados}>
                Añadir {marcados.size} seleccionados
              </button>
            )}
          </>
        )}
      </div>

      {sueltosFiltrados.length > 0 && (
        <div className={styles.seccionBiblioteca}>
          <h4>Otros indicadores</h4>
          <p className={styles.pendiente}>
            No están en ningún dashboard: se añaden como KPI y podrás cambiar su tamaño después.
          </p>
          <ul className={styles.listaElementos}>
            {sueltosFiltrados.map((i) => (
              <li key={i.metricaId}>
                <button
                  type="button"
                  className={styles.elemento}
                  onClick={() => onAnadir(bloqueDesdeIndicador(i))}
                >
                  {i.nombre}
                  <span className={styles.elementoOrigen}>{i.datasetNombre}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className={styles.seccionBiblioteca}>
        <h4>Comparaciones</h4>
        <ul className={styles.listaElementos}>
          <li>
            <button type="button" className={styles.elemento} onClick={onAbrirComparacion}>
              + Añadir comparación entre años
            </button>
          </li>
        </ul>
      </div>

      <div className={styles.seccionBiblioteca}>
        <h4>Contenido</h4>
        <ul className={styles.listaElementos}>
          {(
            [
              // «de sección» a propósito: estos bloques son contenido dentro
              // del documento, no el título general, que se edita arriba.
              ['TITULO', 'Título de sección', 'Nueva sección'],
              ['SUBTITULO', 'Subtítulo de sección', 'Nuevo subtítulo'],
              ['TEXTO', 'Texto', 'Escribe aquí.'],
            ] as const
          ).map(([tipo, etiqueta, texto]) => (
            <li key={tipo}>
              <button
                type="button"
                className={styles.elemento}
                onClick={() => onAnadir({ tipoBloque: tipo, contenidoTexto: texto, ancho: 12 })}
              >
                {etiqueta}
              </button>
            </li>
          ))}
          <li>
            <button
              type="button"
              className={styles.elemento}
              onClick={() => onAnadir({ tipoBloque: 'SEPARADOR', ancho: 12 })}
            >
              Separador
            </button>
          </li>
          <li>
            <button
              type="button"
              className={styles.elemento}
              onClick={() => onAnadir({ tipoBloque: 'SALTO_PAGINA', ancho: 12 })}
            >
              Salto de página
            </button>
          </li>
        </ul>
      </div>
    </Card>
  )
}

/** En qué cajón del filtro cae un widget, según cómo se represente. */
function categoriaDe(widget: WidgetDisponibleDto): FiltroTipo {
  if (widget.tipoVisualizacion === 'KPI' || widget.tipoVisualizacion === 'TARJETA') return 'KPI'
  if (widget.tipoVisualizacion === 'TABLA') return 'TABLA'
  return 'GRAFICA'
}

function etiquetaTipo(widget: WidgetDisponibleDto): string {
  const vista = widget.tipoVisualizacion ?? 'KPI'
  const temporal = widget.tipoResultadoWidget === 'SERIE_TEMPORAL'
  const granularidad = widget.configuracionWidget?.granularidad
  if (temporal && granularidad) return `${vista} · por ${granularidad.toLowerCase()}`
  return vista
}

/**
 * Convierte un widget del dashboard en un bloque de informe conservando su
 * configuración COMPLETA: visualización, forma del resultado, granularidad y
 * ancho. Es justo lo que faltaba antes.
 */
function bloqueDesdeWidget(widget: WidgetDisponibleDto): BloqueInformeRequestDto {
  return {
    tipoBloque: tipoBloqueDe(widget),
    metricaId: widget.metricaId,
    tipoVisualizacion: widget.tipoVisualizacion,
    tipoResultadoWidget: widget.tipoResultadoWidget,
    configuracionWidget: widget.configuracionWidget,
    ancho: widget.ancho,
    // Solo el título propio. Copiar aquí el nombre de la métrica lo congelaría:
    // renombrar el indicador dejaría el informe con el nombre viejo.
    tituloPersonalizado: widget.tituloPersonalizado,
  }
}

function tipoBloqueDe(widget: WidgetDisponibleDto): TipoBloqueInforme {
  const categoria = categoriaDe(widget)
  if (categoria === 'KPI') return 'KPI'
  if (categoria === 'TABLA') return 'TABLA'
  return 'GRAFICA'
}

/** Sin dashboard de origen no hay representación que heredar: entra como KPI. */
function bloqueDesdeIndicador(indicador: IndicadorDisponibleDto): BloqueInformeRequestDto {
  const vista: TipoVisualizacion = indicador.tipoMetrica === 'DISTRIBUCION' ? 'BARRAS' : 'KPI'
  return {
    tipoBloque: indicador.tipoMetrica === 'DISTRIBUCION' ? 'GRAFICA' : 'KPI',
    metricaId: indicador.metricaId,
    tipoVisualizacion: vista,
    ancho: vista === 'KPI' ? 3 : 12,
  }
}
