import type { ImportacionTrabajoResponseDto } from '../../api/types'
import styles from './CorreccionFilasTrabajo.module.css'

interface ResumenImportacionTrabajoProps {
  trabajo: ImportacionTrabajoResponseDto
}

export function ResumenImportacionTrabajo({ trabajo }: ResumenImportacionTrabajoProps) {
  const filasAImportar = trabajo.totalFilasLeidas - trabajo.totalFilasExcluidas

  return (
    <div>
      <p className={styles.avisoCopiaTrabajo}>
        Las correcciones se aplican solo a esta importación. Tu archivo original no se modificará.
      </p>
      <ul className={styles.resumen}>
        <li>Se importarán {filasAImportar} fila(s).</li>
        <li>Se excluirán {trabajo.totalFilasExcluidas} fila(s).</li>
        <li>Hay {trabajo.totalErrores} error(es) pendiente(s).</li>
        {trabajo.totalAdvertencias > 0 && <li>{trabajo.totalAdvertencias} advertencia(s).</li>}
      </ul>
    </div>
  )
}
