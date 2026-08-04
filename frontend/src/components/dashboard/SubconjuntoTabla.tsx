import type { ColumnaSubconjuntoDto, SubconjuntoPaginaDto } from '../../api/types'
import { esNumerica, formatearCelda } from './formatoSubconjunto'
import styles from './SubconjuntoPanel.module.css'

interface SubconjuntoTablaProps {
  pagina: SubconjuntoPaginaDto | null
  columnas: ColumnaSubconjuntoDto[]
  cargando: boolean
  /** Solo en la vista Pacientes: cuántos registros agrupa cada fila. */
  mostrarNumeroRegistros: boolean
  orden: { campo: string; direccion: 'ASC' | 'DESC' } | null
  onOrdenar: (campo: string) => void
}

export function SubconjuntoTabla({
  pagina,
  columnas,
  cargando,
  mostrarNumeroRegistros,
  orden,
  onOrdenar,
}: SubconjuntoTablaProps) {
  if (cargando && !pagina) {
    return (
      <p className={styles.cargando} role="status" aria-live="polite">
        Cargando datos del subconjunto…
      </p>
    )
  }

  if (!pagina) return null

  // Sin filas no se pinta una tabla con encabezados vacíos.
  if (pagina.totalElementos === 0) {
    return (
      <div className={styles.vacio}>
        <p className={styles.vacioTitulo}>No hay registros en este subconjunto.</p>
        <p className={styles.vacioTexto}>Revisa el contexto global o elimina la selección actual.</p>
      </div>
    )
  }

  return (
    <div className={`${styles.tablaScroll} ${cargando ? styles.tablaCargando : ''}`}>
      <table className={styles.tabla}>
        <caption className={styles.caption}>
          {mostrarNumeroRegistros ? 'Pacientes del subconjunto' : 'Registros clínicos del subconjunto'}
        </caption>
        <thead>
          <tr>
            {columnas.map((c) => {
              const activa = orden?.campo === c.codigo
              return (
                <th
                  key={c.codigo}
                  scope="col"
                  aria-sort={activa ? (orden.direccion === 'ASC' ? 'ascending' : 'descending') : 'none'}
                  className={esNumerica(c) ? styles.celdaNumerica : undefined}
                >
                  <button type="button" className={styles.botonOrden} onClick={() => onOrdenar(c.codigo)}>
                    {c.etiqueta}
                    <span aria-hidden="true">{activa ? (orden.direccion === 'ASC' ? ' ▲' : ' ▼') : ''}</span>
                  </button>
                </th>
              )
            })}
            {mostrarNumeroRegistros && (
              <th scope="col" className={styles.celdaNumerica}>
                Registros
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {pagina.contenido.map((fila) => (
            <tr key={fila.clave}>
              {columnas.map((c) => (
                <td key={c.codigo} className={esNumerica(c) ? styles.celdaNumerica : undefined}>
                  {formatearCelda(fila.valores[c.codigo], c)}
                </td>
              ))}
              {mostrarNumeroRegistros && <td className={styles.celdaNumerica}>{fila.numeroRegistros}</td>}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
