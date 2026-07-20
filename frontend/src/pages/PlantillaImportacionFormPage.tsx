import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import type { PlantillaImportacionRequestDto } from '../api/types'
import { obtenerDataset } from '../api/datasetApi'
import {
  actualizarPlantillaImportacion,
  crearPlantillaImportacion,
  obtenerPlantillaImportacion,
} from '../api/plantillasImportacionApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ErrorBanner } from '../components/ErrorBanner'
import { PlantillaImportacionForm } from '../components/importacion/PlantillaImportacionForm'
import type { PlantillaFormValores } from '../components/importacion/PlantillaImportacionForm'
import styles from './DatasetFormPage.module.css'

interface DatosPlantillaForm {
  datasetCodigo: string
  valores: PlantillaFormValores
}

export function PlantillaImportacionFormPage() {
  const { datasetId, plantillaId } = useParams<{ datasetId: string; plantillaId?: string }>()
  const navigate = useNavigate()
  const esEdicion = plantillaId !== undefined

  const { data, loading, error } = useApiResource<DatosPlantillaForm>(
    async (signal) => {
      const [dataset, plantilla] = await Promise.all([
        obtenerDataset(datasetId ?? '', signal),
        plantillaId ? obtenerPlantillaImportacion(plantillaId, signal) : Promise.resolve(null),
      ])
      return {
        datasetCodigo: dataset.codigo,
        valores: plantilla
          ? {
              nombre: plantilla.nombre,
              descripcion: plantilla.descripcion ?? '',
              origen: plantilla.origen ?? '',
              filaCabecera: plantilla.filaCabecera === null ? '' : String(plantilla.filaCabecera),
            }
          : { nombre: '', descripcion: '', origen: 'EXCEL', filaCabecera: '0' },
      }
    },
    [datasetId, plantillaId],
  )

  const [errorBackend, setErrorBackend] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  const guardar = async (payload: PlantillaImportacionRequestDto) => {
    setErrorBackend(null)
    setGuardando(true)
    try {
      if (esEdicion && plantillaId) {
        await actualizarPlantillaImportacion(plantillaId, payload)
        navigate(`/datasets/${datasetId}/plantillas`)
      } else {
        const creada = await crearPlantillaImportacion(datasetId ?? '', payload)
        navigate(`/datasets/${datasetId}/plantillas/${creada.id}/mapeos`)
      }
    } catch (err) {
      setErrorBackend(err instanceof Error ? err.message : 'Error al guardar la plantilla.')
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
                { label: 'Plantillas', to: `/datasets/${datasetId}/plantillas` },
                { label: esEdicion ? 'Editar plantilla' : 'Nueva plantilla' },
              ]}
            />
            <h1>{esEdicion ? 'Editar plantilla de importación' : 'Nueva plantilla de importación'}</h1>
            <ErrorBanner mensaje={errorBackend} />
            <Card title="Datos de la plantilla">
              <PlantillaImportacionForm
                valorInicial={data.valores}
                onSubmit={guardar}
                guardando={guardando}
                textoBoton={esEdicion ? 'Guardar cambios' : 'Crear plantilla'}
                cancelarHref={`/datasets/${datasetId}/plantillas`}
              />
            </Card>
          </>
        )}
      </StateContainer>
    </div>
  )
}
