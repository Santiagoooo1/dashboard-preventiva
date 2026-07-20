import type { ValidacionImportacionGenericaResponseDto } from '../../api/types'
import { DataTable } from '../DataTable'
import styles from './Importacion.module.css'

interface ImportValidationSummaryProps {
  validacion: ValidacionImportacionGenericaResponseDto
}

export function ImportValidationSummary({ validacion }: ImportValidationSummaryProps) {
  return (
    <div>
      <ul className={styles.metricas}>
        <li>
          Importable:{' '}
          <span className={validacion.importable ? styles.badgeOk : styles.badgeError}>
            {validacion.importable ? 'Sí' : 'No'}
          </span>
        </li>
        <li>Columnas detectadas: {validacion.totalColumnasDetectadas}</li>
        <li>Reconocidas: {validacion.totalColumnasReconocidas}</li>
        <li>No reconocidas: {validacion.totalColumnasNoReconocidas}</li>
      </ul>
      <p className={styles.resumen}>{validacion.resumen}</p>

      {validacion.camposObligatoriosFaltantes.length > 0 && (
        <div className={styles.bloque}>
          <p className={styles.tituloBloque}>Campos obligatorios que faltan</p>
          <p className={styles.faltantes} role="alert">
            {validacion.camposObligatoriosFaltantes.join(', ')}
          </p>
        </div>
      )}

      {validacion.advertencias && validacion.advertencias.length > 0 && (
        <div className={styles.bloque}>
          <p className={styles.tituloBloque}>Advertencias</p>
          <ul className={`${styles.listaSimple} ${styles.avisos}`}>
            {validacion.advertencias.map((a, i) => (
              <li key={i}>{a}</li>
            ))}
          </ul>
        </div>
      )}

      {validacion.columnasNoReconocidas.length > 0 && (
        <div className={styles.bloque}>
          <p className={styles.tituloBloque}>Columnas no reconocidas</p>
          <ul className={styles.listaSimple}>
            {validacion.columnasNoReconocidas.map((c, i) => (
              <li key={i}>{c}</li>
            ))}
          </ul>
        </div>
      )}

      <div className={styles.bloque}>
        <p className={styles.tituloBloque}>Columnas detectadas en el archivo</p>
        <DataTable
          columns={[
            { key: 'indiceColumna', header: '#' },
            { key: 'nombreColumna', header: 'Columna' },
            {
              key: 'reconocida',
              header: 'Reconocida',
              render: (c) => (
                <span className={c.reconocida ? styles.badgeOk : styles.badgeError}>
                  {c.reconocida ? 'Sí' : 'No'}
                </span>
              ),
            },
            { key: 'campoClinicoCodigo', header: 'Campo destino', render: (c) => c.campoClinicoCodigo ?? '—' },
            { key: 'tipoDato', header: 'Tipo', render: (c) => c.tipoDato ?? '—' },
          ]}
          rows={validacion.columnasDetectadas}
          getRowKey={(c, i) => `${c.indiceColumna}-${i}`}
        />
      </div>
    </div>
  )
}
