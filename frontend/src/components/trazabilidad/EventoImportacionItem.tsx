import type { EventoImportacionTrabajoDto } from '../../api/types'
import { categoriaEvento, textoEvento } from '../../utils/trazabilidad/textosEventos'
import { parseDetalleEvento } from '../../utils/trazabilidad/parseDetalleEvento'
import { formatearFechaHora } from '../../utils/trazabilidad/fechas'
import { DetalleEventoImportacion } from './DetalleEventoImportacion'
import styles from './Trazabilidad.module.css'

interface EventoImportacionItemProps {
  evento: EventoImportacionTrabajoDto
}

const MAX_VALOR_VISIBLE = 60

// Los valores de celda pueden ser texto clínico largo: se recortan visualmente
// pero el valor completo sigue disponible vía `title` (Fase 6.8D.2, punto 10).
function valorRecortado(valor: string): { texto: string; completo: string | undefined } {
  if (valor.length <= MAX_VALOR_VISIBLE) return { texto: valor, completo: undefined }
  return { texto: `${valor.slice(0, MAX_VALOR_VISIBLE)}…`, completo: valor }
}

export function EventoImportacionItem({ evento }: EventoImportacionItemProps) {
  const categoria = categoriaEvento(evento.tipoEvento)
  const detalle = parseDetalleEvento(evento.detalle)

  const anterior = evento.valorAnterior !== null ? valorRecortado(evento.valorAnterior) : null
  const nuevo = evento.valorNuevo !== null ? valorRecortado(evento.valorNuevo) : null

  return (
    <li className={`${styles.eventoItem} ${styles[`categoria-${categoria}`]}`}>
      <div className={styles.eventoCabecera}>
        <span className={styles.eventoFecha}>{formatearFechaHora(evento.fechaEvento)}</span>
        <p className={styles.eventoTexto}>{textoEvento(evento.tipoEvento)}</p>
        {evento.actor && <span className={styles.eventoActor}>{evento.actor}</span>}
      </div>

      {(evento.numeroFilaOriginal !== null || evento.nombreColumna !== null) && (
        <p className={styles.eventoUbicacion}>
          {evento.numeroFilaOriginal !== null && <>Fila {evento.numeroFilaOriginal}</>}
          {evento.numeroFilaOriginal !== null && evento.nombreColumna !== null && ' · '}
          {evento.nombreColumna !== null && <>Columna: {evento.nombreColumna}</>}
        </p>
      )}

      {(anterior !== null || nuevo !== null) && (
        <p className={styles.eventoValores}>
          {anterior !== null && (
            <span className={styles.valorAnterior} title={anterior.completo}>
              {anterior.texto}
            </span>
          )}
          {nuevo !== null && (
            <span className={styles.valorNuevo} title={nuevo.completo}>
              {nuevo.texto}
            </span>
          )}
        </p>
      )}

      <DetalleEventoImportacion detalle={detalle} />
    </li>
  )
}
