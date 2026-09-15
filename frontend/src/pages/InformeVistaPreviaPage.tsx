import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import type { InformeClinicoResponseDto } from '../api/types'
import { obtenerInformeConResultados } from '../api/informesApi'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'
import { HojaInforme } from '../components/informes/HojaInforme'
import styles from '../components/informes/Informe.module.css'

/**
 * Vista previa del informe (Fase 6.9Q).
 *
 * <p>Sin controles de edición y con las hojas tal como se imprimirán. Los datos
 * llegan resueltos en una sola llamada —no una por bloque—, así que lo que se ve
 * aquí es exactamente lo que llevará el PDF de 6.9R.
 */
export function InformeVistaPreviaPage() {
  const { informeId } = useParams()
  const [informe, setInforme] = useState<InformeClinicoResponseDto | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!informeId) return
    obtenerInformeConResultados(informeId)
      .then(setInforme)
      .catch((e) =>
        setError(e instanceof Error ? e.message : 'No se pudo cargar la vista previa del informe.'),
      )
  }, [informeId])

  if (error) {
    return (
      <Card title="Vista previa">
        <ErrorBanner mensaje={error} />
        <Link className="btn btnSecondary" to="/informes">
          Volver a informes
        </Link>
      </Card>
    )
  }

  if (!informe) {
    return (
      <Card title="Vista previa">
        <p className="stateLoading">Calculando el informe…</p>
      </Card>
    )
  }

  const paginas = informe.paginas ?? []

  return (
    <div>
      {/* Esta barra no se imprime: la regla @media print la oculta. No repite el
          título del informe, que ya encabeza cada hoja y es el que irá al PDF. */}
      <div className={styles.cabeceraPagina} style={{ marginBottom: 'var(--spacing-md)' }}>
        <span className={styles.acciones}>
          {/* Abre la ruta de impresión en lugar de imprimir esta pantalla: allí
              no hay navegación ni botones que ocultar, así que lo que sale por
              la impresora es el documento y nada más. */}
          <Link
            className="btn btnPrimary"
            to={`/informes/${informe.id}/imprimir?auto=1`}
            target="_blank"
            rel="noopener"
          >
            Imprimir
          </Link>
          <Link className="btn btnSecondary" to={`/informes/${informe.id}/editar`}>
            Editar
          </Link>
          <Link className="btn btnSecondary" to="/informes">
            Volver
          </Link>
        </span>
      </div>

      <div className={styles.lienzo}>
        {paginas.map((pagina, i) => (
          <HojaInforme
            key={pagina.id}
            informe={informe}
            pagina={pagina}
            numero={i + 1}
            total={paginas.length}
            avisarDesbordamiento
          />
        ))}
      </div>
    </div>
  )
}
