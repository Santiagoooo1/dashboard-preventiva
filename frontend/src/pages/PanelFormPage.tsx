import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import type { CompatibilidadDashboardIlqDto, PanelClinicoRequestDto, PropuestaDashboardResponseDto } from '../api/types'
import { obtenerDataset } from '../api/datasetApi'
import { obtenerPropuestaDashboard } from '../api/propuestaDashboardApi'
import { comprobarCompatibilidadIlq } from '../api/dashboardIlqApi'
import { actualizarPanel, crearPanel, obtenerPanel } from '../api/panelesApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ErrorBanner } from '../components/ErrorBanner'
import { PanelForm } from '../components/paneles/PanelForm'
import { DashboardRecomendadoPanel } from '../components/paneles/DashboardRecomendadoPanel'
import { DashboardIlqCard } from '../components/dashboard/DashboardIlqCard'
import type { PanelFormValores } from '../components/paneles/PanelForm'
import styles from './DatasetFormPage.module.css'

interface DatosPanelForm {
  datasetCodigo: string
  valores: PanelFormValores
  /** Solo al crear: qué se podría montar automáticamente. */
  propuesta: PropuestaDashboardResponseDto | null
  ilq: CompatibilidadDashboardIlqDto | null
}

/** Dos formas de empezar un dashboard. La recomendada es la principal. */
type Modo = 'RECOMENDADO' | 'VACIO'

export function PanelFormPage() {
  const { datasetId, panelId } = useParams<{ datasetId: string; panelId?: string }>()
  const navigate = useNavigate()
  const esEdicion = panelId !== undefined

  const { data, loading, error } = useApiResource<DatosPanelForm>(
    async (signal) => {
      const [dataset, panel, propuesta, ilq] = await Promise.all([
        obtenerDataset(datasetId ?? '', signal),
        panelId ? obtenerPanel(panelId, signal) : Promise.resolve(null),
        // Solo al crear: al editar un panel existente no se propone nada.
        panelId ? Promise.resolve(null) : obtenerPropuestaDashboard(datasetId ?? '', signal).catch(() => null),
        panelId ? Promise.resolve(null) : comprobarCompatibilidadIlq(datasetId ?? '', signal).catch(() => null),
      ])
      return {
        propuesta,
        ilq,
        datasetCodigo: dataset.codigo,
        valores: panel
          ? {
              codigo: panel.codigo,
              nombre: panel.nombre,
              descripcion: panel.descripcion ?? '',
              orden: String(panel.orden ?? ''),
            }
          : { codigo: '', nombre: '', descripcion: '', orden: '' },
      }
    },
    [datasetId, panelId],
  )

  const [errorBackend, setErrorBackend] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)
  // Al crear se empieza por el recomendado: llevar a un panel vacío sin
  // explicar nada era justo lo que dejaba al usuario sin saber qué hacer.
  const [modo, setModo] = useState<Modo>('RECOMENDADO')

  const guardar = async (payload: PanelClinicoRequestDto) => {
    setErrorBackend(null)
    setGuardando(true)
    try {
      if (esEdicion && panelId) {
        await actualizarPanel(panelId, payload)
        navigate(`/datasets/${datasetId}/paneles`)
      } else {
        const creado = await crearPanel(datasetId ?? '', payload)
        navigate(`/datasets/${datasetId}/paneles/${creado.id}/widgets`)
      }
    } catch (err) {
      setErrorBackend(err instanceof Error ? err.message : 'Error al guardar el panel.')
      setGuardando(false)
    }
  }

  return (
    <div className={styles.page}>
      <StateContainer loading={loading} error={error} empty={data === null}>
        {data && (
          <>
            <Breadcrumbs
              items={[
                { label: 'Datasets', to: '/datasets' },
                { label: data.datasetCodigo, to: `/datasets/${datasetId}` },
                { label: 'Paneles', to: `/datasets/${datasetId}/paneles` },
                { label: esEdicion ? 'Editar panel' : 'Nuevo panel' },
              ]}
            />
            <h1>{esEdicion ? 'Editar panel' : 'Nuevo dashboard'}</h1>
            <ErrorBanner mensaje={errorBackend} />

            {/* Si el dataset es de vigilancia de ILQ, esa plantilla va primero:
                está verificada clínicamente y el genérico no la sustituye. */}
            {!esEdicion && data.ilq?.compatible && (
              <DashboardIlqCard datasetId={datasetId ?? ''} soloSiCompatible />
            )}

            {!esEdicion && (
              <div className={styles.modos} role="radiogroup" aria-label="Cómo empezar el dashboard">
                <label className={styles.modo}>
                  <input
                    type="radio"
                    name="modoDashboard"
                    checked={modo === 'RECOMENDADO'}
                    onChange={() => setModo('RECOMENDADO')}
                  />
                  <span>
                    <strong>Dashboard recomendado</strong>
                    <span className={styles.modoAyuda}>
                      Se compone a partir de los campos fundamentales y obligatorios del dataset. Verás qué
                      widgets se crearán antes de confirmar.
                    </span>
                  </span>
                </label>
                <label className={styles.modo}>
                  <input
                    type="radio"
                    name="modoDashboard"
                    checked={modo === 'VACIO'}
                    onChange={() => setModo('VACIO')}
                  />
                  <span>
                    <strong>Dashboard vacío</strong>
                    <span className={styles.modoAyuda}>
                      Solo el panel; los widgets los añades tú uno a uno.
                    </span>
                  </span>
                </label>
              </div>
            )}

            {!esEdicion && modo === 'RECOMENDADO' && data.propuesta ? (
              <DashboardRecomendadoPanel
                datasetId={datasetId ?? ''}
                propuesta={data.propuesta}
                onCancelar={() => setModo('VACIO')}
              />
            ) : (
              <Card title={esEdicion ? 'Datos del panel' : 'Datos del dashboard vacío'}>
                <PanelForm
                  valorInicial={data.valores}
                  onSubmit={guardar}
                  guardando={guardando}
                  textoBoton={esEdicion ? 'Guardar cambios' : 'Crear panel'}
                  cancelarHref={`/datasets/${datasetId}/paneles`}
                />
              </Card>
            )}
          </>
        )}
      </StateContainer>
    </div>
  )
}
