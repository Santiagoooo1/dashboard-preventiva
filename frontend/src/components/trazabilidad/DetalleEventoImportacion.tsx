import { formatearDetalleObjeto, formatearValorDetalle } from '../../utils/trazabilidad/parseDetalleEvento'
import styles from './Trazabilidad.module.css'

interface DetalleEventoImportacionProps {
  detalle: unknown
}

const MAX_TEXTO_LARGO = 300
const MAX_ITEMS_LISTA_RAIZ = 10

// El backend genera `detalle` como JSON válido en la práctica, pero esta
// pantalla debe seguir funcionando aunque llegue texto plano o JSON
// malformado (no debe romper la vista de trazabilidad).
export function DetalleEventoImportacion({ detalle }: DetalleEventoImportacionProps) {
  if (detalle === null || detalle === undefined || detalle === '') {
    return null
  }

  if (typeof detalle === 'string') {
    const truncado = detalle.length > MAX_TEXTO_LARGO
    return (
      <div className={styles.detalleContenedor}>
        <p className={styles.detalleTexto} title={truncado ? detalle : undefined}>
          {truncado ? `${detalle.slice(0, MAX_TEXTO_LARGO)}…` : detalle}
        </p>
      </div>
    )
  }

  if (Array.isArray(detalle)) {
    const items = detalle.slice(0, MAX_ITEMS_LISTA_RAIZ).map((item) => formatearValorDetalle(item))
    const resto = detalle.length - items.length
    return (
      <div className={styles.detalleContenedor}>
        <ul className={styles.detalleLista}>
          {items.map((texto, indice) => (
            <li key={indice}>{texto}</li>
          ))}
        </ul>
        {resto > 0 && <p className={styles.detalleTexto}>y {resto} más</p>}
      </div>
    )
  }

  if (typeof detalle === 'object') {
    const pares = formatearDetalleObjeto(detalle as Record<string, unknown>)
    if (pares.length === 0) return null
    return (
      <dl className={`${styles.detalleContenedor} ${styles.detalleLista}`}>
        {pares.map((par) => (
          <div key={par.clave} className={styles.detalleItem}>
            <dt>{par.etiqueta}:</dt>
            <dd>{par.valor}</dd>
          </div>
        ))}
      </dl>
    )
  }

  return (
    <div className={styles.detalleContenedor}>
      <p className={styles.detalleTexto}>{String(detalle)}</p>
    </div>
  )
}
