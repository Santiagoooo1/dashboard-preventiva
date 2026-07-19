import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import type { PanelClinicoRequestDto } from '../api/types'
import { actualizarPanel, crearPanel, obtenerPanel } from '../api/panelesApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { PanelForm } from '../components/paneles/PanelForm'
import type { PanelFormValores } from '../components/paneles/PanelForm'
import styles from './DatasetFormPage.module.css'

export function PanelFormPage() {
  const { datasetId, panelId } = useParams<{ datasetId: string; panelId?: string }>()
  const navigate = useNavigate()
  const esEdicion = panelId !== undefined

  const { data, loading, error } = useApiResource<PanelFormValores>(
    async (signal) => {
      if (!panelId) {
        return { codigo: '', nombre: '', descripcion: '', orden: '' }
      }
      const panel = await obtenerPanel(panelId, signal)
      return {
        codigo: panel.codigo,
        nombre: panel.nombre,
        descripcion: panel.descripcion ?? '',
        orden: String(panel.orden ?? ''),
      }
    },
    [panelId],
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
      <h1>{esEdicion ? 'Editar panel' : 'Nuevo panel'}</h1>
      <StateContainer loading={loading} error={error} empty={data === null}>
        {data && (
          <>
            {errorBackend && (
              <div className={styles.bannerError} role="alert">
                {errorBackend}
              </div>
            )}
            <Card title="Datos del panel">
              <PanelForm
                valorInicial={data}
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
