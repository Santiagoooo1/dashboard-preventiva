import { useState } from 'react'
import { Link, useParams } from 'react-router'
import type {
  DeteccionColumnasResponseDto,
  MapeoCampoImportacionRequestDto,
  MapeoCampoImportacionResponseDto,
} from '../api/types'
import { listarCampos, obtenerDataset } from '../api/datasetApi'
import { getCatalogo } from '../api/frontendCatalogApi'
import {
  actualizarMapeoPlantilla,
  crearMapeoPlantilla,
  detectarColumnasPlantilla,
  listarMapeosPlantilla,
  obtenerPlantillaImportacion,
} from '../api/plantillasImportacionApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { DataTable } from '../components/DataTable'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ErrorBanner } from '../components/ErrorBanner'
import { FileDropzone } from '../components/importacion/FileDropzone'
import { MapeoImportacionForm } from '../components/importacion/MapeoImportacionForm'
import type { MapeoFormValores } from '../components/importacion/MapeoImportacionForm'
import { MapeoRowActions } from '../components/importacion/MapeoRowActions'
import styles from './PlantillaMapeosPage.module.css'

type FormAbierto = { tipo: 'nuevo' } | { tipo: 'editar'; mapeoId: number } | null

const VALORES_NUEVO: MapeoFormValores = {
  nombreColumnaOrigen: '',
  campoClinicoId: '',
  tipoDato: '',
  obligatorio: false,
  politicaCampoFaltante: '',
  valorPorDefecto: '',
  orden: '',
}

export function PlantillaMapeosPage() {
  const { datasetId, plantillaId } = useParams<{ datasetId: string; plantillaId: string }>()
  const [formAbierto, setFormAbierto] = useState<FormAbierto>(null)
  const [errorAccion, setErrorAccion] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  const [archivoDeteccion, setArchivoDeteccion] = useState<File | null>(null)
  const [deteccion, setDeteccion] = useState<DeteccionColumnasResponseDto | null>(null)
  const [detectando, setDetectando] = useState(false)
  const [mostrarDeteccion, setMostrarDeteccion] = useState(false)

  const { data, loading, error, reload } = useApiResource(
    async (signal) => {
      const [dataset, plantilla, mapeos, campos, catalogo] = await Promise.all([
        obtenerDataset(datasetId ?? '', signal),
        obtenerPlantillaImportacion(plantillaId ?? '', signal),
        listarMapeosPlantilla(plantillaId ?? '', signal),
        listarCampos(datasetId ?? '', signal),
        getCatalogo(signal),
      ])
      return { dataset, plantilla, mapeos, campos, catalogo }
    },
    [datasetId, plantillaId],
  )

  const cerrarForm = () => setFormAbierto(null)

  const guardarMapeo = async (payload: MapeoCampoImportacionRequestDto) => {
    setErrorAccion(null)
    setGuardando(true)
    try {
      if (formAbierto?.tipo === 'editar') {
        await actualizarMapeoPlantilla(plantillaId ?? '', formAbierto.mapeoId, payload)
      } else {
        await crearMapeoPlantilla(plantillaId ?? '', payload)
      }
      setFormAbierto(null)
      setGuardando(false)
      reload()
    } catch (err) {
      setErrorAccion(err instanceof Error ? err.message : 'Error al guardar el mapeo.')
      setGuardando(false)
    }
  }

  const detectar = async () => {
    if (!archivoDeteccion) return
    setErrorAccion(null)
    setDetectando(true)
    try {
      const resultado = await detectarColumnasPlantilla({
        archivo: archivoDeteccion,
        indiceHoja: 0,
        filaCabecera: data?.plantilla.filaCabecera ?? 0,
      })
      setDeteccion(resultado)
    } catch (err) {
      setErrorAccion(err instanceof Error ? err.message : 'Error al detectar columnas.')
    } finally {
      setDetectando(false)
    }
  }

  const valoresEdicion = (m: MapeoCampoImportacionResponseDto): MapeoFormValores => ({
    nombreColumnaOrigen: m.nombreColumnaOrigen,
    campoClinicoId: String(m.campoClinicoId),
    tipoDato: m.tipoDato,
    obligatorio: m.obligatorio,
    politicaCampoFaltante: m.politicaCampoFaltante ?? '',
    valorPorDefecto: m.valorPorDefecto ?? '',
    orden: m.orden === null ? '' : String(m.orden),
  })

  const columnasSugeridas = deteccion?.columnas.map((c) => c.nombreOriginal) ?? []

  return (
    <div className={styles.page}>
      <StateContainer loading={loading} error={error} empty={data === null}>
        {data && (
          <>
            <Breadcrumbs
              items={[
                { label: 'Datasets', to: '/datasets' },
                { label: data.dataset.codigo, to: `/datasets/${datasetId}` },
                { label: 'Plantillas', to: `/datasets/${datasetId}/plantillas` },
                { label: `Mapeos de ${data.plantilla.nombre}` },
              ]}
            />
            <div className={styles.cabecera}>
              <h1>Mapeos de {data.plantilla.nombre}</h1>
              <div className={styles.botonesCabecera}>
                <button
                  type="button"
                  className="btn btnPrimary"
                  onClick={() => setFormAbierto({ tipo: 'nuevo' })}
                >
                  + Nuevo mapeo
                </button>
                <button
                  type="button"
                  className="btn btnSecondary"
                  onClick={() => setMostrarDeteccion((v) => !v)}
                >
                  Detectar columnas desde archivo
                </button>
                <Link className="btn btnSecondary" to={`/datasets/${datasetId}/importar`}>
                  Ir a importar
                </Link>
              </div>
            </div>

            <Card title="Plantilla">
              <ul className={styles.datosPlantilla}>
                <li>Origen: {data.plantilla.origen ?? '—'}</li>
                <li>Fila de cabecera: {data.plantilla.filaCabecera ?? '—'}</li>
                <li>Activa: {data.plantilla.activa ? 'Sí' : 'No'}</li>
              </ul>
            </Card>

            <ErrorBanner mensaje={errorAccion} />

            {mostrarDeteccion && (
              <Card title="Detectar columnas desde un archivo">
                <div className={styles.deteccion}>
                  <p className={styles.ayuda}>
                    Sube un archivo de ejemplo para ver sus cabeceras reales. Después podrás elegirlas al escribir
                    el nombre de columna del mapeo.
                  </p>
                  <FileDropzone archivo={archivoDeteccion} onArchivoSeleccionado={setArchivoDeteccion} />
                  <div>
                    <button
                      type="button"
                      className="btn btnPrimary"
                      disabled={!archivoDeteccion || detectando}
                      onClick={detectar}
                    >
                      {detectando ? 'Detectando…' : 'Detectar columnas'}
                    </button>
                  </div>
                  {deteccion && (
                    <DataTable
                      columns={[
                        { key: 'indiceColumna', header: '#' },
                        { key: 'nombreOriginal', header: 'Nombre original' },
                        { key: 'nombreNormalizado', header: 'Nombre normalizado' },
                      ]}
                      rows={deteccion.columnas}
                      getRowKey={(c) => c.indiceColumna}
                    />
                  )}
                </div>
              </Card>
            )}

            {formAbierto && (
              <Card title={formAbierto.tipo === 'nuevo' ? 'Nuevo mapeo' : 'Editar mapeo'}>
                <MapeoImportacionForm
                  valorInicial={
                    formAbierto.tipo === 'editar'
                      ? valoresEdicion(data.mapeos.find((m) => m.id === formAbierto.mapeoId)!)
                      : VALORES_NUEVO
                  }
                  campos={data.campos}
                  tiposDato={data.catalogo.tiposDato}
                  politicas={data.catalogo.politicasCampoFaltante}
                  columnasSugeridas={columnasSugeridas}
                  onSubmit={guardarMapeo}
                  onCancelar={cerrarForm}
                  guardando={guardando}
                  textoBoton={formAbierto.tipo === 'nuevo' ? 'Crear mapeo' : 'Guardar cambios'}
                />
              </Card>
            )}

            {data.mapeos.length === 0 ? (
              <div>
                <p className="stateEmpty">
                  Esta plantilla no tiene mapeos. Sin mapeos, el archivo no podrá reconocerse al importar.
                </p>
                <button type="button" className="btn btnPrimary" onClick={() => setFormAbierto({ tipo: 'nuevo' })}>
                  Nuevo mapeo
                </button>
              </div>
            ) : (
              <Card title="Mapeos de columnas">
                <DataTable
                  columns={[
                    { key: 'nombreColumnaOrigen', header: 'Columna del archivo' },
                    {
                      key: 'campo',
                      header: 'Campo clínico',
                      render: (m) => `${m.campoClinicoEtiqueta} (${m.campoClinicoCodigo})`,
                    },
                    { key: 'tipoDato', header: 'Tipo' },
                    { key: 'obligatorio', header: 'Obligatorio', render: (m) => (m.obligatorio ? 'Sí' : 'No') },
                    {
                      key: 'politicaCampoFaltante',
                      header: 'Si falta',
                      render: (m) => m.politicaCampoFaltante ?? '—',
                    },
                    { key: 'valorPorDefecto', header: 'Por defecto', render: (m) => m.valorPorDefecto ?? '—' },
                    { key: 'orden', header: 'Orden', render: (m) => m.orden ?? '—' },
                    { key: 'activo', header: 'Activo', render: (m) => (m.activo ? 'Sí' : 'No') },
                    {
                      key: 'acciones',
                      header: 'Acciones',
                      render: (m) => (
                        <MapeoRowActions
                          plantillaId={plantillaId ?? ''}
                          mapeo={m}
                          onEditar={() => setFormAbierto({ tipo: 'editar', mapeoId: m.id })}
                          onError={setErrorAccion}
                          onEliminado={reload}
                        />
                      ),
                    },
                  ]}
                  rows={data.mapeos}
                  getRowKey={(m) => m.id}
                />
              </Card>
            )}
          </>
        )}
      </StateContainer>
    </div>
  )
}
