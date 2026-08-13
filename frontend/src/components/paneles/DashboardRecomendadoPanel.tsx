import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import type {
  MetricaClinicaRequestDto,
  PropuestaDashboardResponseDto,
  TipoVisualizacion,
} from '../../api/types'
import { crearMetrica, listarMetricas } from '../../api/metricasApi'
import { actualizarConfiguracionWidget, anadirWidget, crearPanel } from '../../api/panelesApi'
import { Card } from '../Card'
import { FormField } from '../FormField'
import { ErrorBanner } from '../ErrorBanner'
import { ETIQUETA_VISUALIZACION_OFRECIDA, normalizarTipoVisualizacion } from '../dashboard/visualizacionesCompatibles'
import styles from './DashboardRecomendadoPanel.module.css'

interface DashboardRecomendadoPanelProps {
  datasetId: string
  propuesta: PropuestaDashboardResponseDto
  /** Vuelve al modo «dashboard vacío». */
  onCancelar: () => void
}

/**
 * Dashboard recomendado: previsualización y creación (Fase 6.9I.4.1).
 *
 * <p>Antes, crear un panel llevaba directamente a una pantalla de widgets
 * vacía: el usuario acababa de pedir un dashboard y se encontraba con la nada.
 * Aquí ve de antemano qué se le va a montar, con el porqué de cada widget, y
 * puede quitar lo que no quiera antes de que exista.
 *
 * <p>No inventa cálculos: las propuestas vienen del backend, que las ha
 * puntuado y ordenado. Aquí solo se pintan y se crean con los endpoints
 * normales de métricas y widgets.
 */
export function DashboardRecomendadoPanel({
  datasetId,
  propuesta,
  onCancelar,
}: DashboardRecomendadoPanelProps) {
  const navigate = useNavigate()

  const [nombre, setNombre] = useState('Dashboard principal')
  const [codigo, setCodigo] = useState('dashboard_principal')
  const [descripcion, setDescripcion] = useState('')

  // Todas marcadas de entrada: la propuesta ya está acotada y ordenada, así que
  // el caso normal es aceptarla entera.
  const [descartadas, setDescartadas] = useState<Set<string>>(new Set())

  const [errorBackend, setErrorBackend] = useState<string | null>(null)
  const [creando, setCreando] = useState(false)
  const [progreso, setProgreso] = useState<string | null>(null)

  const seleccionadas = useMemo(
    () => propuesta.propuestas.filter((p) => !descartadas.has(p.codigoMetrica)),
    [propuesta.propuestas, descartadas],
  )

  const alternar = (codigoMetrica: string) => {
    setDescartadas((previas) => {
      const siguientes = new Set(previas)
      if (siguientes.has(codigoMetrica)) siguientes.delete(codigoMetrica)
      else siguientes.add(codigoMetrica)
      return siguientes
    })
  }

  const crear = async () => {
    setErrorBackend(null)

    if (!nombre.trim() || !codigo.trim()) {
      setErrorBackend('El nombre y el código del dashboard son obligatorios.')
      return
    }
    if (seleccionadas.length === 0) {
      setErrorBackend('Selecciona al menos un widget, o crea un dashboard vacío.')
      return
    }

    setCreando(true)
    try {
      // Códigos ya usados: la propuesta puede coincidir con una métrica que el
      // usuario creara antes, y el backend rechaza códigos duplicados.
      const existentes = new Set(
        (await listarMetricas(datasetId)).map((m) => m.codigo.toLowerCase()),
      )

      const panel = await crearPanel(datasetId, {
        codigo: codigo.trim(),
        nombre: nombre.trim(),
        descripcion: descripcion.trim() || null,
        orden: 0,
      })

      let creados = 0
      for (const p of seleccionadas) {
        setProgreso(`Creando ${creados + 1} de ${seleccionadas.length}: ${p.nombre}…`)

        const codigoMetrica = codigoLibre(p.codigoMetrica, existentes)
        existentes.add(codigoMetrica.toLowerCase())

        const peticion: MetricaClinicaRequestDto = {
          codigo: codigoMetrica,
          nombre: p.nombre,
          descripcion: p.descripcion,
          tipoMetrica: p.tipoMetrica,
          configuracion: p.configuracion,
          unidad: p.unidad,
          decimales: p.decimales,
          orden: p.orden,
        }

        const metrica = await crearMetrica(datasetId, peticion)

        const widget = await anadirWidget(panel.id, {
          metricaId: metrica.id,
          tipoVisualizacion: p.tipoVisualizacion as TipoVisualizacion,
          tituloPersonalizado: null,
          descripcionPersonalizada: null,
          ancho: p.ancho,
          orden: p.orden,
        })

        // La forma del resultado vive en el widget, no en la métrica.
        if (p.tipoResultado !== 'ACTUAL' && p.configuracionWidget) {
          await actualizarConfiguracionWidget(panel.id, widget.id, {
            tipoResultado: p.tipoResultado,
            configuracionWidget: p.configuracionWidget,
          })
        }

        creados++
      }

      navigate(`/paneles/${panel.id}/dashboard`)
    } catch (err) {
      setErrorBackend(
        err instanceof Error
          ? `No se pudo completar el dashboard recomendado. ${err.message}`
          : 'No se pudo completar el dashboard recomendado.',
      )
      setCreando(false)
      setProgreso(null)
    }
  }

  if (!propuesta.suficiente) {
    return (
      <Card title="Dashboard recomendado">
        <p className={styles.mensaje}>
          No hay campos suficientes para generar un dashboard recomendado.{' '}
          {propuesta.motivoInsuficiente}
        </p>
        <button type="button" className="btn btnSecondary" onClick={onCancelar}>
          Crear un dashboard vacío
        </button>
      </Card>
    )
  }

  return (
    <Card title="Dashboard recomendado" className={styles.tarjeta}>
      <p className={styles.mensaje}>
        Se crearán <strong>{seleccionadas.length} indicadores y gráficos</strong> a partir de los campos
        marcados como fundamentales o importantes y de los que tienen más valor analítico. Puedes quitar los
        que no necesites antes de crearlo.
      </p>

      <ErrorBanner mensaje={errorBackend} />

      <div className={styles.grid}>
        <FormField label="Nombre del dashboard">
          <input value={nombre} onChange={(e) => setNombre(e.target.value)} disabled={creando} />
        </FormField>
        <FormField label="Código interno" help="Identificador estable dentro del dataset.">
          <input value={codigo} onChange={(e) => setCodigo(e.target.value)} disabled={creando} />
        </FormField>
        <div className={styles.anchoCompleto}>
          <FormField label="Descripción (opcional)">
            <input value={descripcion} onChange={(e) => setDescripcion(e.target.value)} disabled={creando} />
          </FormField>
        </div>
      </div>

      <ul className={styles.propuestas}>
        {propuesta.propuestas.map((p) => (
          <li key={p.codigoMetrica}>
            <label className={`${styles.propuesta} ${descartadas.has(p.codigoMetrica) ? styles.descartada : ''}`}>
              <input
                type="checkbox"
                checked={!descartadas.has(p.codigoMetrica)}
                onChange={() => alternar(p.codigoMetrica)}
                disabled={creando}
              />
              <span className={styles.contenido}>
                <span className={styles.cabecera}>
                  <span className={styles.nombre}>{p.nombre}</span>
                  <span className={styles.vista}>
                    {ETIQUETA_VISUALIZACION_OFRECIDA[normalizarTipoVisualizacion(p.tipoVisualizacion)]}
                  </span>
                </span>
                <span className={styles.motivo}>{p.motivo}</span>
                {p.descripcion && <span className={styles.descripcion}>{p.descripcion}</span>}
                {p.advertencia && <span className={styles.advertencia}>{p.advertencia}</span>}
              </span>
            </label>
          </li>
        ))}
      </ul>

      {(propuesta.filtrosRecomendados?.length > 0 || propuesta.dimensionesRecomendadas?.length > 0) && (
        // Informativo: el dashboard se crea igual sin esto. Plegado para no
        // competir con la decisión principal, que es qué widgets entran.
        <details className={styles.recomendaciones}>
          <summary className={styles.recomendacionesResumen}>
            Campos sugeridos para filtrar y agrupar
          </summary>
          <div className={styles.listasRecomendadas}>
            {propuesta.filtrosRecomendados?.length > 0 && (
              <div>
                <p className={styles.listaTitulo}>Para filtrar</p>
                <ul className={styles.listaCampos}>
                  {propuesta.filtrosRecomendados.map((c) => (
                    <li key={c.codigo} title={c.motivo}>
                      {c.etiqueta}
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {propuesta.dimensionesRecomendadas?.length > 0 && (
              <div>
                <p className={styles.listaTitulo}>Para agrupar o segmentar</p>
                <ul className={styles.listaCampos}>
                  {propuesta.dimensionesRecomendadas.map((c) => (
                    <li key={c.codigo} title={c.motivo}>
                      {c.etiqueta}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        </details>
      )}

      {progreso && <p className={styles.progreso}>{progreso}</p>}

      <div className={styles.acciones}>
        <button type="button" className="btn btnPrimary" disabled={creando} onClick={crear}>
          {creando ? 'Creando…' : `Crear dashboard con ${seleccionadas.length} widgets`}
        </button>
        <button type="button" className="btn btnSecondary" disabled={creando} onClick={onCancelar}>
          Prefiero un dashboard vacío
        </button>
      </div>
    </Card>
  )
}

/**
 * Evita chocar con una métrica que el usuario ya tuviera con ese código: el
 * backend rechaza duplicados, y fallar a mitad de la creación dejaría el
 * dashboard incompleto.
 */
function codigoLibre(base: string, usados: Set<string>): string {
  if (!usados.has(base.toLowerCase())) return base
  let n = 2
  while (usados.has(`${base}_${n}`.toLowerCase())) n += 1
  return `${base}_${n}`
}
