import { useRef, useState } from 'react'
import styles from './FileDropzone.module.css'

const EXTENSIONES_PERMITIDAS = ['.xlsx', '.xls', '.csv']
const TAMANO_MAXIMO_BYTES = 20 * 1024 * 1024 // el backend acepta hasta 20 MB

interface FileDropzoneProps {
  archivo: File | null
  onArchivoSeleccionado: (archivo: File | null) => void
}

function formatearTamano(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

function validarArchivo(archivo: File): string | null {
  const nombre = archivo.name.toLowerCase()
  if (!EXTENSIONES_PERMITIDAS.some((ext) => nombre.endsWith(ext))) {
    return `Formato no admitido. Usa un archivo ${EXTENSIONES_PERMITIDAS.join(', ')}.`
  }
  if (archivo.size > TAMANO_MAXIMO_BYTES) {
    return `El archivo supera el máximo de 20 MB (ocupa ${formatearTamano(archivo.size)}).`
  }
  return null
}

export function FileDropzone({ archivo, onArchivoSeleccionado }: FileDropzoneProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [arrastrando, setArrastrando] = useState(false)
  const [errorArchivo, setErrorArchivo] = useState<string | null>(null)

  const procesar = (seleccionado: File | undefined) => {
    if (!seleccionado) return
    const error = validarArchivo(seleccionado)
    if (error) {
      setErrorArchivo(error)
      onArchivoSeleccionado(null)
      return
    }
    setErrorArchivo(null)
    onArchivoSeleccionado(seleccionado)
  }

  const quitar = () => {
    setErrorArchivo(null)
    onArchivoSeleccionado(null)
    if (inputRef.current) {
      inputRef.current.value = ''
    }
  }

  if (archivo) {
    return (
      <div>
        <div className={styles.archivo}>
          <div>
            <p className={styles.archivoNombre}>{archivo.name}</p>
            <p className={styles.archivoMeta}>
              {formatearTamano(archivo.size)} · {archivo.type || 'tipo desconocido'}
            </p>
          </div>
          <button type="button" className="btn btnSecondary" onClick={quitar}>
            Quitar archivo
          </button>
        </div>
        {errorArchivo && (
          <p className={styles.errorArchivo} role="alert">
            {errorArchivo}
          </p>
        )}
      </div>
    )
  }

  return (
    <div>
      <div
        className={`${styles.zona} ${arrastrando ? styles.arrastrando : ''}`}
        role="button"
        tabIndex={0}
        onClick={() => inputRef.current?.click()}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault()
            inputRef.current?.click()
          }
        }}
        onDragOver={(e) => {
          e.preventDefault()
          setArrastrando(true)
        }}
        onDragLeave={() => setArrastrando(false)}
        onDrop={(e) => {
          e.preventDefault()
          setArrastrando(false)
          procesar(e.dataTransfer.files?.[0])
        }}
      >
        <p className={styles.principal}>Arrastra aquí el archivo o haz clic para seleccionarlo</p>
        <p className={styles.formatos}>Formatos admitidos: .xlsx, .xls y .csv · Máximo 20 MB</p>
      </div>
      <input
        ref={inputRef}
        className={styles.oculto}
        type="file"
        accept=".xlsx,.xls,.csv"
        onChange={(e) => procesar(e.target.files?.[0])}
      />
      {errorArchivo && (
        <p className={styles.errorArchivo} role="alert">
          {errorArchivo}
        </p>
      )}
    </div>
  )
}
