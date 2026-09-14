import type { ReactNode } from 'react'
import type { InformeClinicoResponseDto, PaginaInformeResponseDto } from '../../api/types'
import { BloqueInformeRenderer } from './BloqueInformeRenderer'
import styles from './Informe.module.css'

interface HojaInformeProps {
  informe: InformeClinicoResponseDto
  pagina: PaginaInformeResponseDto
  numero: number
  total: number
  /** Controles de edición sobre cada bloque; ausentes en la vista previa. */
  controlesBloque?: (bloqueId: number, indice: number, total: number) => ReactNode
}

/**
 * Una hoja del informe (Fase 6.9Q).
 *
 * <p>Mide 210 × 297 mm de verdad y coloca los bloques sobre una rejilla de 12
 * columnas. Guardar el ancho en columnas y no en píxeles es lo que hace que el
 * documento aguante un cambio de fuente, de navegador o el salto a PDF: una
 * posición absoluta se descuadra en cuanto un título ocupa una línea más.
 */
export function HojaInforme({ informe, pagina, numero, total, controlesBloque }: HojaInformeProps) {
  const horizontal = pagina.orientacion === 'HORIZONTAL'
  const fecha = informe.generadoEn ?? informe.actualizadoEn

  return (
    <article className={`${styles.hoja} ${horizontal ? styles.hojaHorizontal : ''}`}>
      <header className={styles.cabeceraHoja}>
        <span>{informe.tituloVisible}</span>
        <span>
          Página {numero} de {total}
        </span>
      </header>

      {pagina.bloques.length === 0 ? (
        <p className={styles.hojaVacia}>
          Empieza añadiendo indicadores, gráficas, tablas o texto.
        </p>
      ) : (
        pagina.bloques.map((bloque, indice) => (
          <div
            key={bloque.id}
            className={styles.bloque}
            // El ancho viaja como número de columnas, no como medida física.
            style={{ ['--span' as string]: bloque.ancho }}
          >
            {controlesBloque && (
              <div className={styles.controlesBloque}>
                {controlesBloque(bloque.id, indice, pagina.bloques.length)}
              </div>
            )}
            <div className={controlesBloque ? styles.marcoEdicion : undefined}>
              <BloqueInformeRenderer bloque={bloque} />
            </div>
          </div>
        ))
      )}

      {/* Trazabilidad: cuándo se consultó y de qué datasets sale. Sin ids. */}
      <footer className={styles.pieHoja}>
        <span>Consultado: {formatearFecha(fecha)}</span>
        {informe.datasetsUtilizados && informe.datasetsUtilizados.length > 0 && (
          <span>Datos de: {informe.datasetsUtilizados.join(' · ')}</span>
        )}
      </footer>
    </article>
  )
}

function formatearFecha(iso: string | null): string {
  if (!iso) return '—'
  const fecha = new Date(iso)
  if (Number.isNaN(fecha.getTime())) return '—'
  const dd = String(fecha.getDate()).padStart(2, '0')
  const mm = String(fecha.getMonth() + 1).padStart(2, '0')
  const hh = String(fecha.getHours()).padStart(2, '0')
  const mi = String(fecha.getMinutes()).padStart(2, '0')
  return `${dd}/${mm}/${fecha.getFullYear()} ${hh}:${mi}`
}
