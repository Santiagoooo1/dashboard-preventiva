import { useState } from 'react'
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
import { Card } from '../Card'
import { ErrorBanner } from '../ErrorBanner'
import { FileDropzone } from '../importacion/FileDropzone'
import { WizardStepIndicator } from './WizardStepIndicator'
import type { ClaveWizard } from './WizardStepIndicator'
import { DetectedColumnsTable } from './DetectedColumnsTable'
import { DatasetBasicConfigForm } from './DatasetBasicConfigForm'
import type { ConfigDataset } from './DatasetBasicConfigForm'
import { ImportProgressPanel } from './ImportProgressPanel'
import { ImportResultPanel } from './ImportResultPanel'
import { ImportErrorRecoveryPanel } from './ImportErrorRecoveryPanel'
import type { CorreccionColumna } from './ImportErrorRecoveryPanel'
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

  // Código base sugerido (sin sufijo) e intento actual: en cada reintento se usa
  // un código nuevo (base_2, base_3…) para no chocar con el dataset parcial.
  const [codigoBase, setCodigoBase] = useState('')
  const [intentoNumero, setIntentoNumero] = useState(1)

  // Cambiar de archivo reinicia el asistente: cualquier análisis previo deja de
  // corresponder al fichero actual.
  const cambiarArchivo = (nuevo: File | null) => {
    setArchivo(nuevo)
    setColumnas([])
    setError(null)
    setIntentoNumero(1)
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
      const cols = sugerirColumnas(
        deteccion.columnas.map((c) => ({ indiceColumna: c.indiceColumna, nombreOriginal: c.nombreOriginal })),
      )
      setColumnas(cols)
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

  const crearEImportar = async () => {
    if (!archivo || !validarConfig()) return
    // El código del primer intento es la base para sufijar los reintentos
    // (respeta lo que el usuario haya editado, p. ej. CARDIO_2026 → CARDIO_2026_2).
    if (intentoNumero === 1) setCodigoBase(config.codigo.trim())
    setPaso('creando')
    setProgreso(PASOS_INICIALES.map((p) => ({ ...p })))

    const res = await ejecutarAsistente(
      {
        archivo,
        indiceHoja: Number(indiceHoja) || 0,
        filaCabecera: Number(filaCabecera) || 0,
        dataset: { nombre: config.nombre.trim(), codigo: config.codigo.trim(), descripcion: config.descripcion.trim() },
        columnas,
      },
      (clave, estado, mensaje) => {
        setProgreso((actual) =>
          actual.map((p) => (p.clave === clave ? { ...p, estado, mensaje } : p)),
        )
      },
    )
    setResultado(res)
    setPaso('resultado')
  }

  // Aplica una corrección del panel de recuperación al instante sobre la columna.
  const aplicarCorreccionColumna = (indiceColumna: number, cambios: CorreccionColumna) => {
    setColumnas((actual) =>
      actual.map((c) => (c.indiceColumna === indiceColumna ? { ...c, ...cambios } : c)),
    )
  }

  // Vuelve a columnas conservando archivo y correcciones, con un código nuevo
  // para el siguiente intento (evita colisión con el dataset parcial ya creado).
  const volverACorregirYReintentar = () => {
    const siguiente = intentoNumero + 1
    setIntentoNumero(siguiente)
    setConfig((actual) => ({ ...actual, codigo: `${codigoBase}_${siguiente}` }))
    setError(null)
    setPaso('columnas')
  }

  // Fallo recuperable: validar-filas devolvió errores de fila (no importable).
  const esFalloRecuperable =
    resultado !== null &&
    resultado.importacion === null &&
    (resultado.validacionFilas?.errores.length ?? 0) > 0

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
        <Card title="Columnas detectadas">
          <p className={styles.intro}>
            Revisa las columnas que ha detectado la aplicación. Puedes cambiar el nombre visible, el tipo de dato o su
            rol, y desmarcar las que no quieras usar.
          </p>
          <DetectedColumnsTable columnas={columnas} onChange={setColumnas} />
          <div className={styles.acciones}>
            <button type="button" className="btn btnSecondary" onClick={() => setPaso('subir')}>
              Atrás
            </button>
            <button type="button" className="btn btnPrimary" onClick={irAConfiguracion}>
              Continuar
            </button>
          </div>
        </Card>
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

      {paso === 'resultado' &&
        resultado &&
        (esFalloRecuperable ? (
          <ImportErrorRecoveryPanel
            resultado={resultado}
            columnas={columnas}
            onCorregirColumna={aplicarCorreccionColumna}
            onReintentar={volverACorregirYReintentar}
          />
        ) : (
          <ImportResultPanel resultado={resultado} />
        ))}
    </div>
  )
}
