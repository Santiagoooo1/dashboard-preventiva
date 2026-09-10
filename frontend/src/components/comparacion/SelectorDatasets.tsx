import { useEffect, useId, useMemo, useRef, useState } from 'react'
import type { DatasetClinicoResponseDto } from '../../api/types'
import styles from './SelectorDatasets.module.css'

interface SelectorDatasetsProps {
  datasets: DatasetClinicoResponseDto[]
  seleccionados: number[]
  /** Marca o desmarca uno; la lógica de selección vive fuera. */
  onAlternar: (id: number) => void
  onLimpiar: () => void
}

/**
 * Selección múltiple de datasets en un desplegable (Fase 6.9P.2).
 *
 * <p>Antes la lista iba desplegada en la página. Con tres datasets se leía
 * bien; con treinta, el resto del formulario —variable, granularidad, el botón
 * de comparar— quedaba fuera de la pantalla y no se veía que hubiera nada más.
 *
 * <p>Es solo presentación: el estado sigue siendo el mismo array de ids y se
 * sigue alternando de uno en uno. Marcar un dataset NO cierra el panel, porque
 * lo normal aquí es seleccionar varios seguidos.
 */
export function SelectorDatasets({
  datasets,
  seleccionados,
  onAlternar,
  onLimpiar,
}: SelectorDatasetsProps) {
  const [abierto, setAbierto] = useState(false)
  const [busqueda, setBusqueda] = useState('')
  const contenedor = useRef<HTMLDivElement>(null)
  const campoBusqueda = useRef<HTMLInputElement>(null)
  const idPanel = useId()

  // Cerrar al pulsar fuera o con Escape. Se registra solo mientras está
  // abierto: un listener global permanente es una fuga silenciosa.
  useEffect(() => {
    if (!abierto) return

    const alPulsarFuera = (evento: MouseEvent) => {
      if (contenedor.current && !contenedor.current.contains(evento.target as Node)) {
        setAbierto(false)
      }
    }
    const alPulsarTecla = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape') setAbierto(false)
    }

    document.addEventListener('mousedown', alPulsarFuera)
    document.addEventListener('keydown', alPulsarTecla)
    return () => {
      document.removeEventListener('mousedown', alPulsarFuera)
      document.removeEventListener('keydown', alPulsarTecla)
    }
  }, [abierto])

  // Al abrir, el foco va a la búsqueda: con muchos datasets es lo primero que
  // se necesita, y deja el panel operable solo con teclado.
  useEffect(() => {
    if (abierto) campoBusqueda.current?.focus()
  }, [abierto])

  const filtrados = useMemo(() => {
    const termino = busqueda.trim().toLowerCase()
    if (termino === '') return datasets
    return datasets.filter((d) => d.nombre.toLowerCase().includes(termino))
  }, [datasets, busqueda])

  return (
    <div className={styles.contenedor} ref={contenedor}>
      <button
        type="button"
        className={styles.disparador}
        aria-expanded={abierto}
        aria-controls={idPanel}
        onClick={() => setAbierto((a) => !a)}
      >
        <span>{textoSeleccion(datasets, seleccionados)}</span>
        <span aria-hidden="true" className={styles.flecha}>
          ▾
        </span>
      </button>

      {abierto && (
        <div className={styles.panel} id={idPanel} role="group" aria-label="Datasets a comparar">
          {datasets.length === 0 ? (
            <p className={styles.vacio}>No hay datasets disponibles.</p>
          ) : (
            <>
              <input
                ref={campoBusqueda}
                type="search"
                className={styles.busqueda}
                placeholder="Buscar dataset…"
                value={busqueda}
                onChange={(e) => setBusqueda(e.target.value)}
                aria-label="Buscar dataset"
              />

              {filtrados.length === 0 ? (
                <p className={styles.vacio}>No se encontraron datasets.</p>
              ) : (
                <ul className={styles.lista}>
                  {filtrados.map((d) => (
                    <li key={d.id}>
                      <label className={styles.opcion}>
                        <input
                          type="checkbox"
                          checked={seleccionados.includes(d.id)}
                          onChange={() => onAlternar(d.id)}
                        />
                        <span>{d.nombre}</span>
                      </label>
                    </li>
                  ))}
                </ul>
              )}

              {seleccionados.length > 0 && (
                <div className={styles.pie}>
                  <span className={styles.resumen}>{textoSeleccion(datasets, seleccionados)}</span>
                  <button type="button" className={styles.limpiar} onClick={onLimpiar}>
                    Limpiar selección
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  )
}

/**
 * Qué dice el control cerrado.
 *
 * <p>Con pocos datasets se nombran, que es más útil que un número. A partir de
 * tres, el recuento: la alternativa es un botón que crece hasta romper la fila.
 */
function textoSeleccion(datasets: DatasetClinicoResponseDto[], seleccionados: number[]): string {
  if (seleccionados.length === 0) return 'Selecciona datasets'
  if (seleccionados.length === 1) return '1 dataset seleccionado'
  if (seleccionados.length <= 3) {
    const nombres = seleccionados
      .map((id) => datasets.find((d) => d.id === id)?.nombre)
      .filter((n): n is string => Boolean(n))
    if (nombres.length === seleccionados.length) return nombres.join(' · ')
  }
  return `${seleccionados.length} datasets seleccionados`
}
