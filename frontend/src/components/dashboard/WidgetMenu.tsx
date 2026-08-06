import { useEffect, useId, useRef, useState } from 'react'
import styles from './WidgetMenu.module.css'

export interface OpcionMenuWidget {
  etiqueta: string
  onSeleccionar: () => void
  /** Deshabilitada: se muestra igualmente, con el motivo. */
  deshabilitada?: boolean
  motivoDeshabilitada?: string
  /** Acción destructiva: se destaca y pide confirmación en el contenedor. */
  destructiva?: boolean
}

interface WidgetMenuProps {
  /** Nombre del widget, para que el aria-label diga de cuál se trata. */
  titulo: string
  opciones: OpcionMenuWidget[]
}

/**
 * Menú de acciones de un widget (Fase 6.9I.4.1).
 *
 * <p>Cuatro botones permanentes en cada tarjeta competirían con el propio dato,
 * que es lo que el médico ha venido a mirar. Se recogen tras un botón que solo
 * pesa lo que pesa: se abre con ratón o teclado, se cierra al pulsar fuera o
 * con Escape, y devuelve el foco al botón al cerrarse.
 *
 * <p>Las acciones no disponibles aparecen deshabilitadas CON su motivo, no
 * ocultas: si desaparecieran sin más, el usuario no sabría que existen ni por
 * qué no puede usarlas.
 */
export function WidgetMenu({ titulo, opciones }: WidgetMenuProps) {
  const [abierto, setAbierto] = useState(false)
  const contenedorRef = useRef<HTMLDivElement>(null)
  const botonRef = useRef<HTMLButtonElement>(null)
  const menuId = useId()

  useEffect(() => {
    if (!abierto) return

    const alPulsarFuera = (e: MouseEvent) => {
      if (!contenedorRef.current?.contains(e.target as Node)) setAbierto(false)
    }
    const alTeclear = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setAbierto(false)
        // Devolver el foco: si no, al cerrar con Escape el foco se pierde en el
        // documento y la navegación por teclado empieza otra vez desde arriba.
        botonRef.current?.focus()
      }
    }

    document.addEventListener('mousedown', alPulsarFuera)
    document.addEventListener('keydown', alTeclear)
    return () => {
      document.removeEventListener('mousedown', alPulsarFuera)
      document.removeEventListener('keydown', alTeclear)
    }
  }, [abierto])

  const seleccionar = (opcion: OpcionMenuWidget) => {
    if (opcion.deshabilitada) return
    setAbierto(false)
    opcion.onSeleccionar()
  }

  return (
    <div className={styles.contenedor} ref={contenedorRef}>
      <button
        ref={botonRef}
        type="button"
        className={styles.boton}
        aria-label={`Acciones de ${titulo}`}
        aria-haspopup="menu"
        aria-expanded={abierto}
        aria-controls={abierto ? menuId : undefined}
        onClick={() => setAbierto((v) => !v)}
      >
        <span aria-hidden="true">⋮</span>
      </button>

      {abierto && (
        <div className={styles.menu} id={menuId} role="menu">
          {opciones.map((opcion) => (
            <button
              key={opcion.etiqueta}
              type="button"
              role="menuitem"
              className={`${styles.opcion} ${opcion.destructiva ? styles.opcionDestructiva : ''}`}
              disabled={opcion.deshabilitada}
              title={opcion.deshabilitada ? opcion.motivoDeshabilitada : undefined}
              onClick={() => seleccionar(opcion)}
            >
              <span>{opcion.etiqueta}</span>
              {opcion.deshabilitada && opcion.motivoDeshabilitada && (
                <span className={styles.motivo}>{opcion.motivoDeshabilitada}</span>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
