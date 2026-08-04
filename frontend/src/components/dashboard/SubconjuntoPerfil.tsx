import type { PerfilCategoricoDto, SubconjuntoPerfilDto } from '../../api/types'
import { formatNumber } from '../../utils/formatters'
import { formatearFecha, formatearValorCategoria } from './formatoSubconjunto'
import styles from './SubconjuntoPanel.module.css'

interface SubconjuntoPerfilProps {
  perfil: SubconjuntoPerfilDto | null
  cargando: boolean
}

/** Bloque de distribución, común a campos categóricos y booleanos. */
function BloqueCategorico({ dato }: { dato: PerfilCategoricoDto }) {
  return (
    <div className={styles.perfilBloque}>
      <h4 className={styles.perfilTitulo}>{dato.etiqueta}</h4>
      <ul className={styles.perfilLista}>
        {dato.categorias.map((c) => (
          <li key={c.valor} className={styles.perfilItem}>
            <span className={styles.perfilCategoria}>{formatearValorCategoria(c.valor, dato.tipoDato)}</span>
            <span className={styles.perfilBarra} aria-hidden="true">
              <span className={styles.perfilBarraRelleno} style={{ width: `${Math.min(100, c.porcentaje ?? 0)}%` }} />
            </span>
            <span className={styles.perfilCifra}>
              {c.conteo} · {formatNumber(c.porcentaje)} %
            </span>
          </li>
        ))}
        {dato.otrasCategorias > 0 && (
          <li className={styles.perfilItem}>
            <span className={styles.perfilCategoria}>Otros</span>
            <span className={styles.perfilCifra}>{dato.otrasCategorias} categorías más</span>
          </li>
        )}
      </ul>
      <p className={styles.perfilPie}>
        {dato.valoresValidos} válidos · {dato.valoresAusentes} sin dato
      </p>
    </div>
  )
}

export function SubconjuntoPerfil({ perfil, cargando }: SubconjuntoPerfilProps) {
  if (cargando && !perfil) {
    return (
      <p className={styles.cargando} role="status" aria-live="polite">
        Calculando el perfil del grupo…
      </p>
    )
  }

  if (!perfil) return null

  if (perfil.totalRegistros === 0) {
    return (
      <div className={styles.vacio}>
        <p className={styles.vacioTitulo}>No hay registros en este subconjunto.</p>
        <p className={styles.vacioTexto}>Revisa el contexto global o elimina la selección actual.</p>
      </div>
    )
  }

  return (
    <div className={styles.perfil}>
      {/* Denominador explícito: sin esto no se sabría si un 50 % es sobre
          pacientes o sobre registros. */}
      <p className={styles.perfilBase}>
        Distribuciones calculadas sobre <strong>registros clínicos</strong>
        {perfil.registrosPorPaciente !== null && (
          <> · {formatNumber(perfil.registrosPorPaciente)} registros por paciente</>
        )}
      </p>

      {perfil.numericos.length > 0 && (
        <div className={styles.perfilSeccion}>
          <h3 className={styles.perfilSeccionTitulo}>Variables numéricas</h3>
          <div className={styles.tablaScroll}>
            <table className={styles.tabla}>
              <thead>
                <tr>
                  <th scope="col">Variable</th>
                  <th scope="col" className={styles.celdaNumerica}>Media</th>
                  <th scope="col" className={styles.celdaNumerica}>Mediana</th>
                  <th scope="col" className={styles.celdaNumerica}>Mínimo</th>
                  <th scope="col" className={styles.celdaNumerica}>Máximo</th>
                  <th scope="col" className={styles.celdaNumerica}>Válidos</th>
                  <th scope="col" className={styles.celdaNumerica}>Sin dato</th>
                </tr>
              </thead>
              <tbody>
                {perfil.numericos.map((n) => (
                  <tr key={n.campo}>
                    <td>{n.etiqueta}</td>
                    <td className={styles.celdaNumerica}>{formatNumber(n.media)}</td>
                    <td className={styles.celdaNumerica}>{formatNumber(n.mediana)}</td>
                    <td className={styles.celdaNumerica}>{formatNumber(n.minimo)}</td>
                    <td className={styles.celdaNumerica}>{formatNumber(n.maximo)}</td>
                    <td className={styles.celdaNumerica}>{n.valoresValidos}</td>
                    <td className={styles.celdaNumerica}>{n.valoresAusentes}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {(perfil.booleanos.length > 0 || perfil.categoricos.length > 0) && (
        <div className={styles.perfilSeccion}>
          <h3 className={styles.perfilSeccionTitulo}>Características del grupo</h3>
          <div className={styles.perfilGrid}>
            {perfil.booleanos.map((b) => (
              <BloqueCategorico key={b.campo} dato={b} />
            ))}
            {perfil.categoricos.map((c) => (
              <BloqueCategorico key={c.campo} dato={c} />
            ))}
          </div>
        </div>
      )}

      {perfil.fechas.length > 0 && (
        <div className={styles.perfilSeccion}>
          <h3 className={styles.perfilSeccionTitulo}>Periodo cubierto</h3>
          <ul className={styles.perfilLista}>
            {perfil.fechas.map((f) => (
              <li key={f.campo} className={styles.perfilItem}>
                <span className={styles.perfilCategoria}>{f.etiqueta}</span>
                <span className={styles.perfilCifra}>
                  {f.primera ? formatearFecha(f.primera) : 'Sin dato'} —{' '}
                  {f.ultima ? formatearFecha(f.ultima) : 'Sin dato'}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
