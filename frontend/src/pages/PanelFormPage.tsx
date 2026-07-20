import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import type { PanelClinicoRequestDto } from '../api/types'
import { obtenerDataset } from '../api/datasetApi'
import { actualizarPanel, crearPanel, obtenerPanel } from '../api/panelesApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ErrorBanner } from '../components/ErrorBanner'
import { PanelForm } from '../components/paneles/PanelForm'
import type { PanelFormValores } from '../components/paneles/PanelForm'
import styles from './DatasetFormPage.module.css'

interface DatosPanelForm {
  datasetCodigo: string
  valores: PanelFormValores
}

export function PanelFormPage() {
  const { datasetId, panelId } = useParams<{ datasetId: string; panelId?: string }>()
  const navigate = useNavigate()
  const esEdicion = panelId !== undefined

  const { data, loading, error } = useApiResource<DatosPanelForm>(
    async (signal) => {
      const [dataset, panel] = await Promise.all([
        obtenerDataset(datasetId ?? '', signal),
        panelId ? obtenerPanel(panelId, signal) : Promise.resolve(null),
      ])
      return {
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
            <h1>{esEdicion ? 'Editar panel' : 'Nuevo panel'}</h1>
            <ErrorBanner mensaje={errorBackend} />
            <Card title="Datos del panel">
              <PanelForm
                valorInicial={data.valores}
                onSubmit={guardar}
                guardando={guardando}
                textoBoton={esEdicion ? 'Guardar cambios' : 'Crear panel'}
                cancelarHref={`/datasets/${datasetId}/paneles`}
              />
            </Card>
          </>
        )}
      </StateContainer>
    </div>
  )
}
