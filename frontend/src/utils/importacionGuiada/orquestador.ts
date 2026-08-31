import type {
  ImportacionGenericaResponseDto,
  OrigenImportacion,
  ValidacionFilasImportacionGenericaResponseDto,
  ValidacionImportacionGenericaResponseDto,
} from '../../api/types'
import { activarDataset, asegurarCampos, crearDataset } from '../../api/datasetApi'
import { crearMapeoPlantilla, crearPlantillaImportacion } from '../../api/plantillasImportacionApi'
import {
  importarGenerico,
  validarFilasImportacionGenerica,
  validarImportacionGenerica,
} from '../../api/importacionesApi'
import type { ColumnaConfigurada } from './sugerenciasColumnas'

export type ClavePaso =
  | 'dataset'
  | 'campos'
  | 'plantilla'
  | 'mapeos'
  | 'validar-columnas'
  | 'validar-filas'
  | 'importar'

export type EstadoPaso = 'pendiente' | 'en-curso' | 'correcto' | 'error'

export interface PasoProgreso {
  clave: ClavePaso
  etiqueta: string
  estado: EstadoPaso
  mensaje?: string
}

export const PASOS_INICIALES: PasoProgreso[] = [
  { clave: 'dataset', etiqueta: 'Creando el dashboard', estado: 'pendiente' },
  { clave: 'campos', etiqueta: 'Creando las columnas', estado: 'pendiente' },
  { clave: 'plantilla', etiqueta: 'Preparando la importación', estado: 'pendiente' },
  { clave: 'mapeos', etiqueta: 'Relacionando columnas', estado: 'pendiente' },
  { clave: 'validar-columnas', etiqueta: 'Validando el archivo', estado: 'pendiente' },
  { clave: 'validar-filas', etiqueta: 'Validando las filas', estado: 'pendiente' },
  { clave: 'importar', etiqueta: 'Importando registros', estado: 'pendiente' },
]

export interface EntradaOrquestador {
  archivo: File
  indiceHoja: number
  filaCabecera: number
  dataset: { nombre: string; codigo: string; descripcion: string }
  columnas: ColumnaConfigurada[]
  /**
   * Si se indica, reutiliza este dataset (BORRADOR reanudado) en vez de crear
   * uno nuevo: `dataset.{nombre,codigo,descripcion}` se ignoran en ese caso.
   * Ver Fase 6.8E.2 — reanudar un borrador no debe crear un dataset duplicado.
   */
  datasetIdExistente?: number
}

export interface ResultadoAsistente {
  datasetId: number | null
  plantillaId: number | null
  camposCreados: number
  mapeosCreados: number
  validacionColumnas: ValidacionImportacionGenericaResponseDto | null
  validacionFilas: ValidacionFilasImportacionGenericaResponseDto | null
  importacion: ImportacionGenericaResponseDto | null
  /** Paso donde se detuvo, si hubo error. */
  pasoFallido: ClavePaso | null
  error: string | null
}

type OnProgreso = (paso: ClavePaso, estado: EstadoPaso, mensaje?: string) => void

function origenDesdeArchivo(nombre: string): OrigenImportacion {
  return nombre.toLowerCase().endsWith('.csv') ? 'CSV' : 'EXCEL'
}

/**
 * Ejecuta toda la cadena de creación e importación componiendo las funciones ya
 * existentes de src/api. No hace fetch directo. Se detiene en el primer error y
 * devuelve el estado parcial (incluido el datasetId si llegó a crearse), para
 * que la UI pueda enlazar al dataset y continuar en modo avanzado.
 */
export async function ejecutarAsistente(
  entrada: EntradaOrquestador,
  onProgreso: OnProgreso,
): Promise<ResultadoAsistente> {
  const resultado: ResultadoAsistente = {
    datasetId: null,
    plantillaId: null,
    camposCreados: 0,
    mapeosCreados: 0,
    validacionColumnas: null,
    validacionFilas: null,
    importacion: null,
    pasoFallido: null,
    error: null,
  }

  const columnasUsadas = entrada.columnas.filter((c) => c.usar && c.rol !== 'ignorar')

  const fallar = (paso: ClavePaso, err: unknown): ResultadoAsistente => {
    const mensaje = err instanceof Error ? err.message : 'Error inesperado.'
    onProgreso(paso, 'error', mensaje)
    resultado.pasoFallido = paso
    resultado.error = mensaje
    return resultado
  }

  // 1. Dataset
  // Se crea como BORRADOR: hasta que la importación no se complete con éxito
  // (más abajo) no se considera un dataset utilizable, y no debe aparecer en
  // el listado normal de datasets si el asistente falla o se abandona a medias.
  // Si se reanuda un borrador existente, se reutiliza su id en vez de crear
  // otro dataset (evita duplicados al continuar una prueba abandonada).
  onProgreso('dataset', 'en-curso')
  let datasetId: number
  if (entrada.datasetIdExistente !== undefined) {
    datasetId = entrada.datasetIdExistente
    resultado.datasetId = datasetId
    onProgreso('dataset', 'correcto')
  } else {
    try {
      const ds = await crearDataset({
        codigo: entrada.dataset.codigo,
        nombre: entrada.dataset.nombre,
        descripcion: entrada.dataset.descripcion || null,
        estadoDataset: 'BORRADOR',
      })
      datasetId = ds.id
      resultado.datasetId = datasetId
      onProgreso('dataset', 'correcto')
    } catch (err) {
      return fallar('dataset', err)
    }
  }

  // 2. Campos (guarda código → id para los mapeos)
  //
  // Se «aseguran», no se crean uno a uno: al reanudar un borrador el asistente
  // vuelve a declarar las mismas columnas, y crearlas otra vez chocaba con la
  // protección de códigos únicos ("Ya existe un campo clínico con el código
  // 'pacienteCodigo'"). El backend reutiliza los que ya existan —conservando su
  // id y su configuración— y crea solo los que falten.
  onProgreso('campos', 'en-curso')
  const idPorCodigo = new Map<string, number>()
  try {
    const campos = await asegurarCampos(
      datasetId,
      columnasUsadas.map((columna) => ({
        codigo: columna.codigoInterno,
        etiqueta: columna.nombreVisible,
        tipoDato: columna.tipoDato,
        esComun: columna.esComun,
        obligatorio: columna.obligatorio,
        orden: columna.indiceColumna,
      })),
    )
    campos.forEach((campo) => idPorCodigo.set(campo.codigo, campo.id))
    resultado.camposCreados = campos.length
    onProgreso('campos', 'correcto')
  } catch (err) {
    return fallar('campos', err)
  }

  // 3. Plantilla
  onProgreso('plantilla', 'en-curso')
  let plantillaId: number
  try {
    const plantilla = await crearPlantillaImportacion(datasetId, {
      nombre: `Importación de ${entrada.dataset.nombre}`,
      origen: origenDesdeArchivo(entrada.archivo.name),
      filaCabecera: entrada.filaCabecera,
    })
    plantillaId = plantilla.id
    resultado.plantillaId = plantillaId
    onProgreso('plantilla', 'correcto')
  } catch (err) {
    return fallar('plantilla', err)
  }

  // 4. Mapeos (nombreColumnaOrigen = cabecera original del archivo)
  onProgreso('mapeos', 'en-curso')
  try {
    for (const columna of columnasUsadas) {
      const campoClinicoId = idPorCodigo.get(columna.codigoInterno)
      if (campoClinicoId === undefined) continue
      await crearMapeoPlantilla(plantillaId, {
        nombreColumnaOrigen: columna.nombreOriginal,
        campoClinicoId,
        tipoDato: columna.tipoDato,
        obligatorio: columna.obligatorio,
      })
      resultado.mapeosCreados += 1
    }
    onProgreso('mapeos', 'correcto')
  } catch (err) {
    return fallar('mapeos', err)
  }

  const payloadImport = {
    archivo: entrada.archivo,
    plantillaId,
    indiceHoja: entrada.indiceHoja,
    filaCabecera: entrada.filaCabecera,
  }

  // 5. Validar columnas
  onProgreso('validar-columnas', 'en-curso')
  try {
    resultado.validacionColumnas = await validarImportacionGenerica(payloadImport)
    onProgreso('validar-columnas', 'correcto')
  } catch (err) {
    return fallar('validar-columnas', err)
  }

  // 6. Validar filas
  onProgreso('validar-filas', 'en-curso')
  try {
    const filas = await validarFilasImportacionGenerica(payloadImport)
    resultado.validacionFilas = filas
    if (!filas.importable) {
      onProgreso('validar-filas', 'error', 'El archivo tiene filas no válidas y no puede importarse.')
      resultado.pasoFallido = 'validar-filas'
      resultado.error = 'El archivo tiene filas no válidas y no puede importarse.'
      return resultado
    }
    onProgreso('validar-filas', 'correcto')
  } catch (err) {
    return fallar('validar-filas', err)
  }

  // 7. Importar
  onProgreso('importar', 'en-curso')
  try {
    const importacion = await importarGenerico(payloadImport)
    resultado.importacion = importacion
    // /importar responde 200 aunque rechace: se decide por el estado.
    if (importacion.estado === 'RECHAZADA') {
      onProgreso('importar', 'error', importacion.mensaje)
      resultado.pasoFallido = 'importar'
      resultado.error = importacion.mensaje
      return resultado
    }
    onProgreso('importar', 'correcto')
  } catch (err) {
    return fallar('importar', err)
  }

  // La importación ya se completó con éxito: promover el dataset a ACTIVO es
  // un paso de limpieza, no debe hacer fracasar un resultado que ya es bueno.
  try {
    await activarDataset(datasetId)
  } catch {
    // Si falla, el dataset queda BORRADOR pese a tener datos importados; se
    // puede reintentar entrando al dataset en modo avanzado.
  }

  return resultado
}
