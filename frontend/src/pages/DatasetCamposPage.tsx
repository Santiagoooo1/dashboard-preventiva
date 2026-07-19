import { useState } from 'react'
import { Link, useParams } from 'react-router'
import type { CampoClinicoRequestDto } from '../api/types'
import { crearCampo, listarCampos, obtenerDataset } from '../api/datasetApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { DataTable } from '../components/DataTable'
import { CampoRowActions } from '../components/datasets/CampoRowActions'
import styles from './DatasetCamposPage.module.css'

const CAMPOS_BASICOS: CampoClinicoRequestDto[] = [
  { codigo: 'pacienteCodigo', etiqueta: 'Código paciente', tipoDato: 'TEXTO', esComun: true, obligatorio: true, orden: 1 },
  { codigo: 'fechaEvento', etiqueta: 'Fecha evento', tipoDato: 'FECHA', esComun: true, obligatorio: true, orden: 2 },
  { codigo: 'servicio', etiqueta: 'Servicio', tipoDato: 'TEXTO', esComun: true, obligatorio: false, orden: 3 },
]

export function DatasetCamposPage() {
  const { datasetId } = useParams<{ datasetId: string }>()

  const { data, loading, error, reload } = useApiResource(
    async (signal) => {
      const [dataset, campos] = await Promise.all([
        obtenerDataset(datasetId ?? '', signal),
        listarCampos(datasetId ?? '', signal),
      ])
      return { dataset, campos }
    },
    [datasetId],
  )

  const [errorAccion, setErrorAccion] = useState<string | null>(null)
  const [creandoBasicos, setCreandoBasicos] = useState(false)

  const crearBasicos = async () => {
    setErrorAccion(null)
    setCreandoBasicos(true)
    try {
      for (const campo of CAMPOS_BASICOS) {
        await crearCampo(datasetId ?? '', campo)
      }
    } catch (err) {
      setErrorAccion(err instanceof Error ? err.message : 'Error al crear los campos básicos.')
    } finally {
      setCreandoBasicos(false)
      reload()
    }
  }

  return (
    <div className={styles.page}>
      <StateContainer loading={loading} error={error} empty={data === null}>
        {data && (
          <>
            <div className={styles.cabecera}>
              <h1>Campos de {data.dataset.nombre}</h1>
              <div className={styles.botonesCabecera}>
                <Link className={styles.nuevo} to={`/datasets/${datasetId}/campos/nuevo`}>
                  + Nuevo campo
                </Link>
              </div>
            </div>
            <p>
              <Link to={`/datasets/${datasetId}`}>← Volver al dataset</Link>
            </p>

            {errorAccion && (
              <div className={styles.bannerError} role="alert">
                {errorAccion}
              </div>
            )}

            {data.campos.length === 0 ? (
              <div className={styles.vacio}>
                <p>Este dataset no tiene campos activos.</p>
                <button
                  type="button"
                  className={styles.recomendados}
                  disabled={creandoBasicos}
                  onClick={crearBasicos}
                >
                  Crear campos básicos recomendados
                </button>
              </div>
            ) : (
              <Card title="Campos clínicos">
                <DataTable
                  columns={[
                    { key: 'codigo', header: 'Código' },
                    { key: 'etiqueta', header: 'Etiqueta' },
                    { key: 'tipoDato', header: 'Tipo' },
                    { key: 'esComun', header: 'Común', render: (c) => (c.esComun ? 'Sí' : 'No') },
                    { key: 'obligatorio', header: 'Obligatorio', render: (c) => (c.obligatorio ? 'Sí' : 'No') },
                    { key: 'orden', header: 'Orden', render: (c) => c.orden ?? '—' },
                    { key: 'activo', header: 'Activo', render: (c) => (c.activo ? 'Sí' : 'No') },
                    {
                      key: 'acciones',
                      header: 'Acciones',
                      render: (c) => (
                        <CampoRowActions
                          datasetId={datasetId ?? ''}
                          campo={c}
                          onError={setErrorAccion}
                          onEliminado={reload}
                        />
                      ),
                    },
                  ]}
                  rows={data.campos}
                  getRowKey={(c) => c.id}
                />
              </Card>
            )}
          </>
        )}
      </StateContainer>
    </div>
  )
}
