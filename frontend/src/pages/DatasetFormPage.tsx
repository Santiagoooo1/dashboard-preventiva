import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import type { DatasetClinicoRequestDto } from '../api/types'
import { actualizarDataset, crearDataset, obtenerDataset } from '../api/datasetApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { DatasetForm } from '../components/datasets/DatasetForm'
import type { DatasetFormValores } from '../components/datasets/DatasetForm'
import styles from './DatasetFormPage.module.css'

export function DatasetFormPage() {
  const { datasetId } = useParams<{ datasetId?: string }>()
  const navigate = useNavigate()
  const esEdicion = datasetId !== undefined

  const { data, loading, error } = useApiResource<DatasetFormValores | null>(
    async (signal) => {
      if (!datasetId) {
        return { codigo: '', nombre: '', descripcion: '' }
      }
      const dataset = await obtenerDataset(datasetId, signal)
      return {
        codigo: dataset.codigo,
        nombre: dataset.nombre,
        descripcion: dataset.descripcion ?? '',
      }
    },
    [datasetId],
  )

  const [errorBackend, setErrorBackend] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  const guardar = async (payload: DatasetClinicoRequestDto) => {
    setErrorBackend(null)
    setGuardando(true)
    try {
      if (esEdicion && datasetId) {
        await actualizarDataset(datasetId, payload)
        navigate(`/datasets/${datasetId}`)
      } else {
        const creado = await crearDataset(payload)
        navigate(`/datasets/${creado.id}/campos`)
      }
    } catch (err) {
      setErrorBackend(err instanceof Error ? err.message : 'Error al guardar el dataset.')
      setGuardando(false)
    }
  }

  return (
    <div className={styles.page}>
      <h1>{esEdicion ? 'Editar dataset' : 'Nuevo dataset'}</h1>
      <StateContainer loading={loading} error={error} empty={data === null}>
        {data && (
          <>
            {errorBackend && (
              <div className={styles.bannerError} role="alert">
                {errorBackend}
              </div>
            )}
            <Card title="Datos del dataset">
              <DatasetForm
                valorInicial={data}
                onSubmit={guardar}
                guardando={guardando}
                textoBoton={esEdicion ? 'Guardar cambios' : 'Crear dataset'}
                cancelarHref={esEdicion ? `/datasets/${datasetId}` : '/datasets'}
              />
            </Card>
          </>
        )}
      </StateContainer>
    </div>
  )
}
