import { useMemo, useState } from 'react'
import { Link } from 'react-router'
import { detectarColumnasPlantilla } from '../../api/plantillasImportacionApi'
import {
  ejecutarAsistente,
  PASOS_INICIALES,
} from '../../utils/importacionGuiada/orquestador'
import type { PasoProgreso, ResultadoAsistente } from '../../utils/importacionGuiada/orquestador'
import {
  sugerirCodigoDataset,
  sugerirColumnas,
  sugerirNombreDataset,
} from '../../utils/importacionGuiada/sugerenciasColumnas'
import type { ColumnaConfigurada } from '../../utils/importacionGuiada/sugerenciasColumnas'
import {
  detectarColumnasSospechosas,
  detectarRevisionesClinicas,
  establecerFechaPrincipal,
  inicializarCamposClave,
} from '../../utils/importacionGuiada/camposClave'
import {
  agruparErroresPorColumnaIndice,
  construirProblemasPorColumna,
  esCorreccionSegura,
} from '../../utils/importacionGuiada/sugerenciasErrores'
import { Card } from '../Card'
import { ErrorBanner } from '../ErrorBanner'
import { FileDropzone } from '../importacion/FileDropzone'
import { ImportErrorsTable } from '../importacion/ImportErrorsTable'
import { WizardStepIndicator } from './WizardStepIndicator'
import type { ClaveWizard } from './WizardStepIndicator'
import { CamposClavePanel } from './CamposClavePanel'
import { DetectedColumnsTable } from './DetectedColumnsTable'
import { DatasetBasicConfigForm } from './DatasetBasicConfigForm'
import type { ConfigDataset } from './DatasetBasicConfigForm'
import { ImportProgressPanel } from './ImportProgressPanel'
import { ImportResultPanel } from './ImportResultPanel'
import styles from './ImportacionGuiada.module.css'

export function GuidedImportWizard() {
  const [paso, setPaso] = useState<ClaveWizard>('subir')

  const [archivo, setArchivo] = useState<File | null>(null)
  const [indiceHoja, setIndiceHoja] = useState('0')
  const [filaCabecera, setFilaCabecera] = useState('0')
  const [analizando, setAnalizando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [columnas, setColumnas] = useState<ColumnaConfigurada[]>([])
  const [config, setConfig] = useState<ConfigDataset>({ nombre: '', codigo: '', descripcion: '' })
  const [erroresConfig, setErroresConfig] = useState<Record<string, string>>({})

  const [progreso, setProgreso] = useState<PasoProgreso[]>(PASOS_INICIALES)
  const [resultado, setResultado] = useState<ResultadoAsistente | null>(null)
  const [mostrarErroresTecnicos, setMostrarErroresTecnicos] = useState(false)

  // Código base sugerido (sin sufijo) e intento actual: en cada revalidación se
  // usa un código nuevo (base_2, base_3…) para no chocar con el dataset parcial.
  const [codigoBase, setCodigoBase] = useState('')
  const [intentoNumero, setIntentoNumero] = useState(1)

  // Problemas del último intento de validación, agrupados por columna. Se
  // recalculan en cada render: cuando el usuario aplica una corrección, la
  // columna deja de aparecer como problema aunque no se haya revalidado aún.
  const problemasPorColumna = useMemo(() => {
    const errores = resultado?.validacionFilas?.errores
    if (!errores || errores.length === 0) return new Map()
    return construirProblemasPorColumna(errores, columnas)
  }, [resultado, columnas])

  // Errores de fila sin deduplicar, para la tabla "Ver filas afectadas" de los
  // campos críticos (identificador de paciente / fecha principal).
  const erroresPorColumna = useMemo(() => {
    const errores = resultado?.validacionFilas?.errores
    if (!errores || errores.length === 0) return new Map()
    return agruparErroresPorColumnaIndice(errores, columnas)
  }, [resultado, columnas])

  // Columnas cuyo nombre parece un valor (TRUE/FALSE/0/1/vacío): probable
  // fila de cabecera mal elegida. Se recalcula con cada cambio de columnas.
  const columnasSospechosas = useMemo(() => detectarColumnasSospechosas(columnas), [columnas])

  // Se mantiene aunque el usuario ya haya corregido localmente todas las
  // columnas señaladas: sin esto, el botón "Revalidar cambios" desaparecería
  // en cuanto se resuelve el último problema visible, dejando al usuario sin
  // forma de reintentar la importación.
  const huboFalloRecuperable =
    resultado !== null && resultado.importacion === null && (resultado.validacionFilas?.errores.length ?? 0) > 0
  // Revisiones clínicas no bloqueantes (heurísticas por nombre), recalculadas
  // cada vez que cambian las columnas para reflejar ediciones al instante.
  const revisionesClinicas = useMemo(() => detectarRevisionesClinicas(columnas), [columnas])

  const modoCorreccion = problemasPorColumna.size > 0
  const listaProblemas = [...problemasPorColumna.values()]
  const totalConErrores = listaProblemas.filter((p) => p.estado === 'error').length
  const totalConAdvertencias = listaProblemas.filter((p) => p.estado === 'advertencia').length
  const totalConSugerencias = listaProblemas.filter(
    (p) => p.correccion.tipoSugerido !== null || p.correccion.marcarNoObligatorio,
  ).length

  // Cambiar de archivo reinicia el asistente: cualquier análisis o resultado
  // previo deja de corresponder al fichero actual.
  const cambiarArchivo = (nuevo: File | null) => {
    setArchivo(nuevo)
    setColumnas([])
    setError(null)
    setIntentoNumero(1)
    setResultado(null)
    setMostrarErroresTecnicos(false)
  }

  const analizar = async () => {
    if (!archivo) return
    setError(null)
    setAnalizando(true)
    try {
      const deteccion = await detectarColumnasPlantilla({
        archivo,
        indiceHoja: Number(indiceHoja) || 0,
        filaCabecera: Number(filaCabecera) || 0,
      })
      const cols = inicializarCamposClave(
        sugerirColumnas(
          deteccion.columnas.map((c) => ({ indiceColumna: c.indiceColumna, nombreOriginal: c.nombreOriginal })),
        ),
      )
      setColumnas(cols)
      setResultado(null)
      setMostrarErroresTecnicos(false)
      const nombreSugerido = sugerirNombreDataset(archivo.name)
      setIntentoNumero(1)
      setConfig({ nombre: nombreSugerido, codigo: sugerirCodigoDataset(nombreSugerido), descripcion: '' })
      setPaso('columnas')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo analizar el archivo.')
    } finally {
      setAnalizando(false)
    }
  }

  const irAConfiguracion = () => {
    const usadas = columnas.filter((c) => c.usar && c.rol !== 'ignorar')
    if (usadas.length === 0) {
      setError('Selecciona al menos una columna para continuar.')
      return
    }
    const haySospechosaActiva = columnas.some((c) => c.usar && columnasSospechosas.has(c.indiceColumna))
    if (haySospechosaActiva) {
      setError('Hay columnas sospechosas seleccionadas. Ignóralas o cambia la fila de cabecera antes de continuar.')
      return
    }
    setError(null)
    setPaso('configuracion')
  }

  const validarConfig = (): boolean => {
    const errores: Record<string, string> = {}
    if (!config.nombre.trim()) errores.nombre = 'El nombre es obligatorio.'
    if (!config.codigo.trim()) errores.codigo = 'El identificador es obligatorio.'
    setErroresConfig(errores)
    return Object.keys(errores).length === 0
  }

  // Ejecuta la cadena completa (crear dataset/campos/plantilla/mapeos, validar
  // e importar) con la configuración dada. Si el fallo es recuperable (errores
  // de fila), vuelve al paso Columnas en vez de a una pantalla de resultado.
  const ejecutarImportacionConConfig = async (configEjecucion: ConfigDataset) => {
    if (!archivo) return
    setPaso('creando')
    setProgreso(PASOS_INICIALES.map((p) => ({ ...p })))

    const res = await ejecutarAsistente(
      {
        archivo,
        indiceHoja: Number(indiceHoja) || 0,
        filaCabecera: Number(filaCabecera) || 0,
        dataset: {
          nombre: configEjecucion.nombre.trim(),
          codigo: configEjecucion.codigo.trim(),
          descripcion: configEjecucion.descripcion.trim(),
        },
        columnas,
      },
      (clave, estado, mensaje) => {
        setProgreso((actual) => actual.map((p) => (p.clave === clave ? { ...p, estado, mensaje } : p)))
      },
    )
    setResultado(res)
    const fueRecuperable = res.importacion === null && (res.validacionFilas?.errores.length ?? 0) > 0
    setPaso(fueRecuperable ? 'columnas' : 'resultado')
  }

  const crearEImportar = async () => {
    if (!archivo || !validarConfig()) return
    // El código del primer intento es la base para sufijar las revalidaciones
    // (respeta lo que el usuario haya editado, p. ej. CARDIO_2026 → CARDIO_2026_2).
    if (intentoNumero === 1) setCodigoBase(config.codigo.trim())
    await ejecutarImportacionConConfig(config)
  }

  // Revalida desde Columnas: mismo archivo, columnas ya corregidas, código con
  // el siguiente sufijo (no se reutiliza el dataset parcial del intento previo).
  const revalidarCambios = async () => {
    if (!archivo) return
    const siguiente = intentoNumero + 1
    setIntentoNumero(siguiente)
    const nuevoCodigo = `${codigoBase}_${siguiente}`
    const configSiguiente = { ...config, codigo: nuevoCodigo }
    setConfig(configSiguiente)
    await ejecutarImportacionConConfig(configSiguiente)
  }

  const aplicarTodasSugerenciasSeguras = () => {
    setColumnas((actual) =>
      actual.map((c) => {
        const problema = problemasPorColumna.get(c.indiceColumna)
        if (!problema || !esCorreccionSegura(c, problema.correccion)) return c
        const cambios: Partial<ColumnaConfigurada> = {}
        if (problema.correccion.tipoSugerido) cambios.tipoDato = problema.correccion.tipoSugerido
        if (problema.correccion.marcarNoObligatorio) cambios.obligatorio = false
        return { ...c, ...cambios }
      }),
    )
  }

  const marcarNoObligatoriasVacias = () => {
    setColumnas((actual) =>
      actual.map((c) => {
        const problema = problemasPorColumna.get(c.indiceColumna)
        if (!problema?.correccion.marcarNoObligatorio) return c
        return { ...c, obligatorio: false }
      }),
    )
  }

  const cambiarFechaPrincipal = (indiceColumna: number) => {
    setColumnas((actual) => establecerFechaPrincipal(actual, indiceColumna))
  }

  return (
    <div className={styles.page}>
      <WizardStepIndicator actual={paso} />
      {paso !== 'creando' && paso !== 'resultado' && <ErrorBanner mensaje={error} />}

      {paso === 'subir' && (
        <Card title="Sube el archivo">
          <p className={styles.intro}>
            Sube un archivo clínico y la aplicación detectará sus columnas para crear un dashboard automáticamente.
          </p>
          <FileDropzone archivo={archivo} onArchivoSeleccionado={cambiarArchivo} />
          <details className={styles.avanzadas} style={{ marginTop: 'var(--spacing-md)' }}>
            <summary>Opciones avanzadas</summary>
            <div className={styles.avanzadasGrid}>
              <label>
                Índice de hoja (Excel)
                <input type="number" min={0} value={indiceHoja} onChange={(e) => setIndiceHoja(e.target.value)} />
              </label>
              <label>
                Fila de cabecera
                <input type="number" min={0} value={filaCabecera} onChange={(e) => setFilaCabecera(e.target.value)} />
              </label>
            </div>
          </details>
          <div className={styles.acciones}>
            <button type="button" className="btn btnPrimary" disabled={!archivo || analizando} onClick={analizar}>
              {analizando ? 'Analizando…' : 'Analizar archivo'}
            </button>
          </div>
        </Card>
      )}

      {paso === 'columnas' && (
        <>
          {huboFalloRecuperable && (
            <Card title="Revisa las columnas detectadas">
              <p className={styles.intro}>Hay columnas que necesitan revisión antes de importar.</p>
              {modoCorreccion ? (
                <>
                  <p className={styles.resumenProblemas}>
                    {totalConErrores} columna{totalConErrores === 1 ? '' : 's'} con errores · {totalConAdvertencias}{' '}
                    con advertencias · {totalConSugerencias} sugerencia{totalConSugerencias === 1 ? '' : 's'}{' '}
                    disponible{totalConSugerencias === 1 ? '' : 's'}
                  </p>
                  <p>
                    Corrige los tipos de dato, marca columnas como no obligatorias o ignora las columnas que no
                    quieras importar.
                  </p>
                </>
              ) : (
                <p>Has corregido las columnas señaladas. Pulsa «Revalidar cambios» para volver a intentarlo.</p>
              )}
              {resultado?.datasetId != null && (
                <p>
                  Se creó un dataset parcial en el intento anterior. Puedes revisarlo en{' '}
                  <Link to={`/datasets/${resultado.datasetId}`}>modo avanzado</Link> o continuar con una nueva
                  importación corregida.
                </p>
              )}
              <div className={styles.acciones}>
                {modoCorreccion && (
                  <>
                    <button type="button" className="btn btnSecondary" onClick={aplicarTodasSugerenciasSeguras}>
                      Aplicar todas las sugerencias seguras
                    </button>
                    <button type="button" className="btn btnSecondary" onClick={marcarNoObligatoriasVacias}>
                      Marcar como no obligatorias las columnas con valores vacíos
                    </button>
                  </>
                )}
                <button type="button" className="btn btnPrimary" onClick={revalidarCambios}>
                  Revalidar cambios
                </button>
                <button
                  type="button"
                  className="btn btnSecondary"
                  onClick={() => setMostrarErroresTecnicos((v) => !v)}
                >
                  Ver errores técnicos
                </button>
              </div>
            </Card>
          )}

          <CamposClavePanel columnas={columnas} onChange={setColumnas} problemasPorColumna={problemasPorColumna} />

          <Card title="Columnas detectadas">
            {!modoCorreccion && (
              <p className={styles.intro}>
                Revisa las columnas que ha detectado la aplicación. Puedes cambiar el nombre visible, el tipo de dato
                o su rol, y desmarcar las que no quieras usar.
              </p>
            )}
            <DetectedColumnsTable
              columnas={columnas}
              onChange={setColumnas}
              problemasPorColumna={problemasPorColumna}
              erroresPorColumna={erroresPorColumna}
              revisionesClinicas={revisionesClinicas}
              columnasSospechosas={columnasSospechosas}
              onEstablecerFechaPrincipal={cambiarFechaPrincipal}
              onVolverASubir={() => setPaso('subir')}
            />
            <div className={styles.acciones}>
              <button type="button" className="btn btnSecondary" onClick={() => setPaso('subir')}>
                Atrás
              </button>
              <button type="button" className="btn btnPrimary" onClick={irAConfiguracion}>
                Continuar
              </button>
            </div>
          </Card>

          {mostrarErroresTecnicos && resultado?.validacionFilas && (
            <Card title="Detalle técnico de errores">
              <p className={styles.intro}>
                Esta información puede ayudar a revisar el archivo original, pero no es necesaria para continuar.
              </p>
              <ImportErrorsTable errores={resultado.validacionFilas.errores} />
            </Card>
          )}
        </>
      )}

      {paso === 'configuracion' && (
        <Card title="Nombre del dashboard">
          <DatasetBasicConfigForm valores={config} onChange={setConfig} errores={erroresConfig} />
          <div className={styles.acciones}>
            <button type="button" className="btn btnSecondary" onClick={() => setPaso('columnas')}>
              Atrás
            </button>
            <button type="button" className="btn btnPrimary" onClick={crearEImportar}>
              Crear e importar
            </button>
          </div>
        </Card>
      )}

      {paso === 'creando' && (
        <Card title="Creando el dashboard e importando los datos">
          <ImportProgressPanel pasos={progreso} />
        </Card>
      )}

      {paso === 'resultado' && resultado && <ImportResultPanel resultado={resultado} />}
    </div>
  )
}
