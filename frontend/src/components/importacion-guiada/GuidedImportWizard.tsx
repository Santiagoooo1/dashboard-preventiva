import { useMemo, useState } from 'react'
import { Link } from 'react-router'
import { detectarColumnasPlantilla } from '../../api/plantillasImportacionApi'
import { crearImportacionTrabajo, descartarImportacionTrabajo } from '../../api/importacionesTrabajoApi'
import { activarDataset, descartarDatasetBorrador } from '../../api/datasetApi'
import type { ImportarDesdeTrabajoResponseDto } from '../../api/types'
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
  detectarColumnasDuplicadas,
  detectarColumnasSospechosas,
  detectarRevisionesClinicas,
  desmarcarDuplicadasAutomaticamente,
  establecerFechaPrincipal,
  inicializarCamposClave,
  validarColumnasParaCrear,
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
import { CorreccionFilasTrabajoPanel } from './CorreccionFilasTrabajoPanel'
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

  // Copia interna de trabajo (Fase 6.8C.3): permite corregir/excluir filas sin
  // modificar el archivo original. Solo existe entre "Corregir errores en la
  // app" y su importación o descarte.
  const [importacionTrabajoId, setImportacionTrabajoId] = useState<number | null>(null)
  const [creandoCopiaTrabajo, setCreandoCopiaTrabajo] = useState(false)
  // Datos propios de la importación desde copia de trabajo, para completar el
  // resultado genérico del asistente (que no conoce filasExcluidas).
  const [extraResultadoTrabajo, setExtraResultadoTrabajo] = useState<{
    filasExcluidas: number
    resumen: string
    importacionTrabajoId: number
  } | null>(null)

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

  // Columnas cuyo nombre parece un valor (TRUE/FALSE/SÍ/NO/0/1/vacío): probable
  // fila de cabecera mal elegida. Se recalcula con cada cambio de columnas.
  const columnasSospechosas = useMemo(() => detectarColumnasSospechosas(columnas), [columnas])

  // Columnas con el mismo nombre (exacto o normalizado) entre sí.
  const columnasDuplicadas = useMemo(() => detectarColumnasDuplicadas(columnas), [columnas])
  const gruposDuplicados = useMemo(() => {
    const vistos = new Set<string>()
    const grupos: { indice: number; veces: number; columnasAfectadas: string[] }[] = []
    for (const [indice, grupo] of columnasDuplicadas) {
      const clave = grupo.columnasAfectadas.join('|')
      if (vistos.has(clave)) continue
      vistos.add(clave)
      grupos.push({ indice, veces: grupo.veces, columnasAfectadas: grupo.columnasAfectadas })
    }
    return grupos
  }, [columnasDuplicadas])
  const hayDuplicadasActivas = columnas.some((c) => c.usar && columnasDuplicadas.has(c.indiceColumna))

  // Abre "Opciones avanzadas" automáticamente cuando el último análisis dejó
  // columnas sospechosas o duplicadas: el usuario probablemente necesita
  // cambiar la fila de cabecera sin tener que desplegar la sección a mano.
  const sugerirRevisarCabecera = columnasSospechosas.size > 0 || columnasDuplicadas.size > 0

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
    // Defensivo: normalmente ya se descartó al salir de "Corregir filas", pero
    // si quedara una copia de trabajo colgando no debe sobrevivir al cambio de archivo.
    if (importacionTrabajoId !== null) {
      descartarImportacionTrabajo(importacionTrabajoId).catch(() => {})
    }
    setArchivo(nuevo)
    setColumnas([])
    setError(null)
    setIntentoNumero(1)
    setResultado(null)
    setMostrarErroresTecnicos(false)
    setImportacionTrabajoId(null)
    setExtraResultadoTrabajo(null)
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
    if (hayDuplicadasActivas) {
      setError('Hay columnas repetidas en el archivo. Cambia la fila de cabecera o desmarca columnas duplicadas antes de continuar.')
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
    setExtraResultadoTrabajo(null)

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
    // Última comprobación antes de crear nada: repite aquí porque esta no es
    // la única puerta de entrada al orquestador (revalidarCambios es la otra,
    // y no pasa por irAConfiguracion).
    const mensajeInvalido = validarColumnasParaCrear(columnas)
    if (mensajeInvalido) {
      setError(mensajeInvalido)
      setPaso('columnas')
      return
    }
    // El código del primer intento es la base para sufijar las revalidaciones
    // (respeta lo que el usuario haya editado, p. ej. CARDIO_2026 → CARDIO_2026_2).
    if (intentoNumero === 1) setCodigoBase(config.codigo.trim())
    await ejecutarImportacionConConfig(config)
  }

  // Revalida desde Columnas: mismo archivo, columnas ya corregidas, código con
  // el siguiente sufijo (no se reutiliza el dataset parcial del intento previo).
  const revalidarCambios = async () => {
    if (!archivo) return
    const mensajeInvalido = validarColumnasParaCrear(columnas)
    if (mensajeInvalido) {
      setError(mensajeInvalido)
      return
    }
    const siguiente = intentoNumero + 1
    setIntentoNumero(siguiente)
    const nuevoCodigo = `${codigoBase}_${siguiente}`
    const configSiguiente = { ...config, codigo: nuevoCodigo }
    setConfig(configSiguiente)
    await ejecutarImportacionConConfig(configSiguiente)
  }

  // "Corregir errores en la app": crea una copia interna de trabajo con el
  // mismo archivo/plantilla que ya se usó para validar, y pasa al panel de
  // corrección de filas. La plantilla ya existe en este punto (se creó en el
  // paso 3 del orquestador, antes de llegar a validar-filas).
  const corregirEnApp = async () => {
    if (!archivo || resultado?.plantillaId == null) return
    setError(null)
    setCreandoCopiaTrabajo(true)
    try {
      const creado = await crearImportacionTrabajo({
        archivo,
        plantillaId: resultado.plantillaId,
        indiceHoja: Number(indiceHoja) || 0,
        filaCabecera: Number(filaCabecera) || 0,
      })
      setImportacionTrabajoId(creado.importacionTrabajo.id)
      setPaso('correccion-filas')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo crear la copia de trabajo.')
    } finally {
      setCreandoCopiaTrabajo(false)
    }
  }

  // Reutiliza ImportResultPanel: completa el resultado del asistente con la
  // importación genérica creada desde la copia de trabajo, y guarda aparte lo
  // que ese panel no conoce (filas excluidas, resumen propio del backend).
  const onImportadoDesdeTrabajo = async (res: ImportarDesdeTrabajoResponseDto) => {
    const datasetIdActual = resultado?.datasetId ?? null
    setResultado((actual) => ({
      datasetId: actual?.datasetId ?? null,
      plantillaId: actual?.plantillaId ?? null,
      camposCreados: actual?.camposCreados ?? 0,
      mapeosCreados: actual?.mapeosCreados ?? 0,
      validacionColumnas: actual?.validacionColumnas ?? null,
      validacionFilas: actual?.validacionFilas ?? null,
      importacion: res.importacionGenerica,
      pasoFallido: null,
      error: null,
    }))
    setExtraResultadoTrabajo({
      filasExcluidas: res.filasExcluidas,
      resumen: res.resumen,
      importacionTrabajoId: res.importacionTrabajo.id,
    })
    setImportacionTrabajoId(null)
    setPaso('resultado')

    // La importación ya se completó con éxito: promover el dataset a ACTIVO
    // es un paso de limpieza, no debe hacer fracasar un resultado que ya es bueno.
    if (datasetIdActual !== null) {
      try {
        await activarDataset(datasetIdActual)
      } catch {
        // Si falla, el dataset queda BORRADOR pese a tener datos importados.
      }
    }
  }

  // "Volver y subir otro archivo" desde el panel de corrección: la copia de
  // trabajo ya se descarta dentro del panel antes de llamar a este callback.
  // El dataset creado para este intento seguía en BORRADOR (no llegó a
  // importarse), así que se descarta también: no debe quedar como una prueba
  // huérfana en el listado de datasets.
  const volverASubirDesdeCorreccion = async () => {
    const datasetIdActual = resultado?.datasetId ?? null
    setImportacionTrabajoId(null)
    cambiarArchivo(null)
    setPaso('subir')
    if (datasetIdActual !== null) {
      try {
        await descartarDatasetBorrador(datasetIdActual)
      } catch {
        // Puede fallar si ya no está en BORRADOR/VALIDANDO; no es bloqueante
        // y el dataset seguirá disponible en "Pruebas y borradores".
      }
    }
  }

  // Reinicio completo del asistente: usado tanto por "Cancelar creación"
  // (tras descartar lo que hubiera) como por "Crear otro dashboard" desde el
  // resultado (ahí no hay nada que descartar: el dataset ya quedó ACTIVO).
  const reiniciarAsistente = () => {
    setArchivo(null)
    setColumnas([])
    setConfig({ nombre: '', codigo: '', descripcion: '' })
    setErroresConfig({})
    setResultado(null)
    setMostrarErroresTecnicos(false)
    setImportacionTrabajoId(null)
    setExtraResultadoTrabajo(null)
    setIntentoNumero(1)
    setPaso('subir')
  }

  const crearOtroDashboard = () => {
    reiniciarAsistente()
    setError(null)
  }

  // "Cancelar creación": disponible desde cualquier paso mientras el dataset
  // siga en BORRADOR. Si ya se activó (importación ya completada), no se
  // descarta: se avisa y se redirige a empezar de cero igualmente.
  const cancelarCreacion = async () => {
    const datasetIdActual = resultado?.datasetId ?? null
    if (
      !window.confirm(
        'Se descartará el dataset en borrador y sus datos temporales de esta importación. El archivo original no se modifica. ¿Continuar?',
      )
    ) {
      return
    }
    let mensajeError: string | null = null
    if (datasetIdActual !== null) {
      try {
        await descartarDatasetBorrador(datasetIdActual)
      } catch {
        mensajeError = 'Este dataset ya fue activado. Puedes archivarlo desde la pantalla de datasets.'
      }
    }
    reiniciarAsistente()
    setError(mensajeError)
  }

  const desmarcarDuplicadas = () => {
    setColumnas((actual) => desmarcarDuplicadasAutomaticamente(actual))
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
      {paso !== 'creando' && paso !== 'resultado' && paso !== 'correccion-filas' && <ErrorBanner mensaje={error} />}

      {paso === 'subir' && (
        <Card title="Sube el archivo">
          <p className={styles.intro}>
            Sube un archivo clínico y la aplicación detectará sus columnas para crear un dashboard automáticamente.
          </p>
          <FileDropzone archivo={archivo} onArchivoSeleccionado={cambiarArchivo} />
          <details
            className={styles.avanzadas}
            style={{ marginTop: 'var(--spacing-md)' }}
            open={sugerirRevisarCabecera || undefined}
          >
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
            <p className={styles.campoClaveAyuda}>
              Indica qué fila contiene los nombres de las columnas. La primera fila es 0.
            </p>
            <p className={styles.campoClaveAyuda}>
              Si la aplicación detecta columnas como TRUE, FALSE, SÍ, NO, 0, 1 o nombres repetidos, probablemente la
              fila de cabecera no es correcta.
            </p>
          </details>
          <div className={styles.acciones}>
            <button type="button" className="btn btnPrimary" disabled={!archivo || analizando} onClick={analizar}>
              {analizando ? 'Analizando…' : 'Analizar archivo'}
            </button>
            {resultado?.datasetId != null && (
              <button type="button" className="btn btnDanger" onClick={cancelarCreacion}>
                Cancelar creación
              </button>
            )}
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
                <button
                  type="button"
                  className="btn btnPrimary"
                  disabled={creandoCopiaTrabajo || resultado?.plantillaId == null}
                  onClick={corregirEnApp}
                >
                  {creandoCopiaTrabajo ? 'Creando copia de trabajo…' : 'Corregir errores en la app'}
                </button>
                <button type="button" className="btn btnSecondary" onClick={() => setPaso('subir')}>
                  Corregir archivo y volver a subirlo
                </button>
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
                <button type="button" className="btn btnSecondary" onClick={revalidarCambios}>
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

          {hayDuplicadasActivas && (
            <Card title="Columnas repetidas detectadas">
              <p className={styles.intro}>
                Hay columnas con el mismo nombre o nombres equivalentes. Puedes desmarcar una de ellas o cambiar la
                fila de cabecera.
              </p>
              <ul className={styles.listaProgreso}>
                {gruposDuplicados.map((g) => (
                  <li key={g.indice}>
                    <strong>{g.columnasAfectadas[0]}</strong> ({g.veces} veces): {g.columnasAfectadas.join(', ')}
                  </li>
                ))}
              </ul>
              <div className={styles.acciones}>
                <button type="button" className="btn btnSecondary" onClick={desmarcarDuplicadas}>
                  Desmarcar duplicadas automáticamente
                </button>
                <button type="button" className="btn btnSecondary" onClick={() => setPaso('subir')}>
                  Cambiar fila de cabecera
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
              columnasDuplicadas={columnasDuplicadas}
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

      {paso === 'correccion-filas' && importacionTrabajoId !== null && (
        <CorreccionFilasTrabajoPanel
          importacionTrabajoId={importacionTrabajoId}
          onImportado={onImportadoDesdeTrabajo}
          onVolver={volverASubirDesdeCorreccion}
          onVolverAColumnas={() => {
            setImportacionTrabajoId(null)
            setPaso('columnas')
          }}
          onCancelarCreacion={cancelarCreacion}
        />
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

      {paso === 'resultado' && resultado && (
        <ImportResultPanel
          resultado={resultado}
          onVolverAColumnas={() => setPaso('columnas')}
          onCrearOtroDashboard={crearOtroDashboard}
          extra={extraResultadoTrabajo ?? undefined}
        />
      )}
    </div>
  )
}
