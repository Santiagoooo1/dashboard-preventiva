import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import type { CampoClinicoRequestDto, CampoClinicoResponseDto, OpcionCatalogoDto } from '../api/types'
import { actualizarCampo, crearCampo, listarCampos, obtenerDataset } from '../api/datasetApi'
import { getCatalogo } from '../api/frontendCatalogApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { CampoForm } from '../components/datasets/CampoForm'
import type { CampoFormValores } from '../components/datasets/CampoForm'
import styles from './DatasetFormPage.module.css'

interface DatosCampoForm {
  datasetNombre: string
  tiposDato: OpcionCatalogoDto[]
  campoExistente: CampoClinicoResponseDto | null
}

export function CampoFormPage() {
  const { datasetId, campoId } = useParams<{ datasetId: string; campoId?: string }>()
  const navigate = useNavigate()
  const esEdicion = campoId !== undefined

  const { data, loading, error } = useApiResource<DatosCampoForm>(
    async (signal) => {
      const [catalogo, dataset, campos] = await Promise.all([
        getCatalogo(signal),
        obtenerDataset(datasetId ?? '', signal),
        campoId ? listarCampos(datasetId ?? '', signal) : Promise.resolve<CampoClinicoResponseDto[]>([]),
      ])
      const campoExistente = campoId ? campos.find((c) => String(c.id) === campoId) ?? null : null
      return { datasetNombre: dataset.nombre, tiposDato: catalogo.tiposDato, campoExistente }
    },
    [datasetId, campoId],
  )

  const [errorBackend, setErrorBackend] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  const guardar = async (payload: CampoClinicoRequestDto) => {
    setErrorBackend(null)
    setGuardando(true)
    try {
      if (esEdicion && campoId) {
        await actualizarCampo(datasetId ?? '', campoId, payload)
      } else {
        await crearCampo(datasetId ?? '', payload)
      }
      navigate(`/datasets/${datasetId}/campos`)
    } catch (err) {
      setErrorBackend(err instanceof Error ? err.message : 'Error al guardar el campo.')
      setGuardando(false)
    }
  }

  const valorInicial: CampoFormValores = data?.campoExistente
    ? {
        codigo: data.campoExistente.codigo,
        etiqueta: data.campoExistente.etiqueta,
        tipoDato: data.campoExistente.tipoDato,
        esComun: data.campoExistente.esComun,
        obligatorio: data.campoExistente.obligatorio,
        orden: data.campoExistente.orden === null ? '' : String(data.campoExistente.orden),
      }
    : { codigo: '', etiqueta: '', tipoDato: '', esComun: false, obligatorio: false, orden: '' }

  const campoNoEncontrado = esEdicion && data !== null && data.campoExistente === null

  return (
    <div className={styles.page}>
      <h1>{esEdicion ? 'Editar campo' : 'Nuevo campo'}</h1>
      <StateContainer loading={loading} error={error} empty={data === null}>
        {data && (
          <>
            <p>
              Dataset: <strong>{data.datasetNombre}</strong>
            </p>
            {campoNoEncontrado ? (
              <div className={styles.bannerError} role="alert">
                No se ha encontrado el campo solicitado (puede estar desactivado o no pertenecer a este dataset).
              </div>
            ) : (
              <>
                {errorBackend && (
                  <div className={styles.bannerError} role="alert">
                    {errorBackend}
                  </div>
                )}
                <Card title="Datos del campo">
                  <CampoForm
                    valorInicial={valorInicial}
                    tiposDato={data.tiposDato}
                    onSubmit={guardar}
                    guardando={guardando}
                    textoBoton={esEdicion ? 'Guardar cambios' : 'Crear campo'}
                    cancelarHref={`/datasets/${datasetId}/campos`}
                  />
                </Card>
              </>
            )}
          </>
        )}
      </StateContainer>
    </div>
  )
}
