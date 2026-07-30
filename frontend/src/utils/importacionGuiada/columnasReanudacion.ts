import type { ColumnaReanudacionDto, TipoDato } from '../../api/types'
import type { ColumnaConfigurada, RolClinico } from './sugerenciasColumnas'
import { esCampoComunDesdeRol, sugerirCodigoInterno, sugerirRolClinico } from './sugerenciasColumnas'

// Campos clínicos "canónicos" (ver CODIGO_CANONICO en sugerenciasColumnas.ts):
// si el código del campo ya mapeado coincide con uno de estos, se conoce su
// rol con certeza y no hace falta adivinarlo por el nombre de columna.
const ROL_POR_CODIGO_CANONICO: Partial<Record<string, RolClinico>> = {
  pacienteCodigo: 'paciente',
  fechaEvento: 'fecha',
  servicio: 'servicio',
  diagnostico: 'diagnostico',
  procedimiento: 'procedimiento',
  edad: 'edad',
  sexo: 'sexo',
}

const TIPOS_DATO_VALIDOS = new Set<TipoDato>(['TEXTO', 'ENTERO', 'DECIMAL', 'FECHA', 'BOOLEANO'])

function esTipoDatoValido(tipo: string | null): tipo is TipoDato {
  return tipo !== null && TIPOS_DATO_VALIDOS.has(tipo as TipoDato)
}

/**
 * Convierte las columnas reconstruidas por el backend (a partir de
 * columnasPresentes/erroresGlobales de la copia de trabajo) al formato que
 * usa el asistente para la revisión de columnas. Ver Fase 6.8E.2.1: permite
 * mostrar "Campos clave" y "Columnas detectadas" con datos reales al
 * reanudar un borrador, en vez de una pantalla vacía.
 */
export function convertirColumnasReanudacion(dtos: ColumnaReanudacionDto[]): ColumnaConfigurada[] {
  return dtos.map((dto, indice) => {
    const nombreVisible = dto.nombreVisible || dto.nombreOriginal
    const rol: RolClinico = !dto.mapeada
      ? 'ignorar'
      : (dto.campoClinicoCodigo && ROL_POR_CODIGO_CANONICO[dto.campoClinicoCodigo]) || sugerirRolClinico(nombreVisible)

    return {
      indiceColumna: indice,
      nombreOriginal: dto.nombreOriginal,
      usar: dto.usar,
      nombreVisible,
      codigoInterno: dto.campoClinicoCodigo ?? sugerirCodigoInterno(nombreVisible),
      tipoDato: esTipoDatoValido(dto.tipoDato) ? dto.tipoDato : 'TEXTO',
      rol,
      esComun: esCampoComunDesdeRol(rol),
      obligatorio: dto.obligatorio,
    }
  })
}
