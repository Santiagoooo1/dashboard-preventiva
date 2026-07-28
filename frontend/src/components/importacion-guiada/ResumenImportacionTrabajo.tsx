import type { ImportacionTrabajoResponseDto } from '../../api/types'
import styles from './CorreccionFilasTrabajo.module.css'

interface ResumenImportacionTrabajoProps {
  trabajo: ImportacionTrabajoResponseDto
  bloqueado: boolean
  onImportar: () => void
}

// Casos A-D: el mensaje principal depende únicamente de si quedan errores
// bloqueantes, no del total de advertencias (que nunca deben leerse como
// "trabajo pendiente obligatorio").
export function ResumenImportacionTrabajo({ trabajo, bloqueado, onImportar }: ResumenImportacionTrabajoProps) {
  const filasAImportar = trabajo.totalFilasLeidas - trabajo.totalFilasExcluidas
  const puedeImportar = trabajo.importable && trabajo.estado === 'LISTA_PARA_IMPORTAR'
  const sinErroresBloqueantes = trabajo.totalErrores === 0

  return (
    <div>
      <p className={styles.avisoCopiaTrabajo}>
        Las correcciones se aplican solo a esta importación. Tu archivo original no se modificará.
      </p>
      <ul className={styles.resumen}>
        <li>Se importarán {filasAImportar} fila(s).</li>
        {trabajo.totalFilasExcluidas > 0 && <li>Se excluirán {trabajo.totalFilasExcluidas} fila(s).</li>}
      </ul>

      {sinErroresBloqueantes ? (
        <div className={styles.estadoPositivo}>
          <p className={styles.estadoTitulo}>
            {trabajo.totalAdvertencias > 0
              ? 'La importación ya puede continuar.'
              : 'La copia de trabajo está lista para importar.'}
          </p>
          {puedeImportar && (
            <button type="button" className="btn btnPrimary" disabled={bloqueado} onClick={onImportar}>
              Importar datos corregidos
            </button>
          )}
          {trabajo.totalAdvertencias > 0 && (
            <>
              <p className={styles.notaAdvertencia}>
                Hay {trabajo.totalAdvertencias} advertencia{trabajo.totalAdvertencias === 1 ? '' : 's'} opcional
                {trabajo.totalAdvertencias === 1 ? '' : 'es'} oculta{trabajo.totalAdvertencias === 1 ? '' : 's'}.
              </p>
              <p className={styles.notaAdvertencia}>
                Puedes importar ahora o revisar las advertencias opcionales.
              </p>
            </>
          )}
        </div>
      ) : (
        <div className={styles.estadoBloqueante}>
          <p className={styles.estadoTitulo}>
            Hay {trabajo.totalErrores} error{trabajo.totalErrores === 1 ? '' : 'es'} que impide
            {trabajo.totalErrores === 1 ? '' : 'n'} importar.
          </p>
        </div>
      )}
    </div>
  )
}
