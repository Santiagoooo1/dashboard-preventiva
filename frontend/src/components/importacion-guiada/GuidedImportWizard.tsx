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

  // Cambiar de archivo reinicia el asistente: cualquier análisis previo deja de
  // corresponder al fichero actual.
  const cambiarArchivo = (nuevo: File | null) => {
    setArchivo(nuevo)
    setColumnas([])
    setError(null)
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
      setConfig({
        nombre: sugerirNombreDataset(archivo.name),
        codigo: sugerirCodigoDataset(sugerirNombreDataset(archivo.name)),
        descripcion: '',
      })
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

      {paso === 'resultado' && resultado && <ImportResultPanel resultado={resultado} />}
    </div>
  )
}
