import type { EventoImportacionTrabajoDto } from '../../api/types'
import { EventoImportacionItem } from './EventoImportacionItem'
import styles from './Trazabilidad.module.css'

interface TimelineEventosImportacionProps {
  eventos: EventoImportacionTrabajoDto[]
}

export function TimelineEventosImportacion({ eventos }: TimelineEventosImportacionProps) {
  if (eventos.length === 0) {
    return <p className="stateEmpty">Todavía no hay eventos de trazabilidad para esta importación.</p>
  }

  // Orden cronológico: el backend ya los devuelve ordenados, pero se ordena
  // aquí también por si el origen (trabajo vs. genérica) difiere.
  const ordenados = [...eventos].sort(
    (a, b) => new Date(a.fechaEvento).getTime() - new Date(b.fechaEvento).getTime(),
  )

  return (
    <ul className={styles.timeline}>
      {ordenados.map((evento) => (
        <EventoImportacionItem key={evento.id} evento={evento} />
      ))}
    </ul>
  )
}
