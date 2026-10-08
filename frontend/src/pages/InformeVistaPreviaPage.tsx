import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import type { InformeClinicoResponseDto } from '../api/types'
import { obtenerInformeConResultados } from '../api/informesApi'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'
import { descargarPdfDeInforme } from '../components/informes/descargaPdf'
import { HojaInforme } from '../components/informes/HojaInforme'
import { elementosDe } from '../components/informes/paginacion'
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
  const [generando, setGenerando] = useState(false)
  // Separado del error de carga: que falle una descarga no puede hacer
  // desaparecer el informe que el usuario está mirando.
  const [errorPdf, setErrorPdf] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!informeId) return
    obtenerInformeConResultados(informeId)
      .then(setInforme)
      .catch((e) =>
        setError(e instanceof Error ? e.message : 'No se pudo cargar la vista previa del informe.'),
      )
  }, [informeId])

  const descargarPdf = async () => {
    if (!informeId || generando) return
    setGenerando(true)
    setErrorPdf(null)
    try {
      await descargarPdfDeInforme(informeId)
    } catch (e) {
      // El backend manda un mensaje ya saneado (por ejemplo, que falta
      // configurar la ruta de Chrome); si no lo hay, una frase genérica.
      setErrorPdf(e instanceof Error && e.message ? e.message : 'No se pudo generar el PDF.')
    } finally {
      setGenerando(false)
    }
  }

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
          <button
            type="button"
            className="btn btnPrimary"
            disabled={generando}
            onClick={descargarPdf}
          >
            {generando ? 'Generando PDF…' : 'Descargar PDF'}
          </button>
          {/* Se conserva la impresión manual: sirve para tirar a papel o para
              elegir impresora, cosas que la descarga no cubre. Abre la misma
              ruta que usa el backend para el PDF, no esta pantalla. */}
          <Link
            className="btn btnSecondary"
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

      <ErrorBanner mensaje={errorPdf} />

      <div className={styles.lienzo}>
        {paginas.map((pagina, i) => (
          <HojaInforme
            key={pagina.id}
            informe={informe}
            elementos={elementosDe(pagina.bloques)}
            orientacion={pagina.orientacion}
            numero={i + 1}
            total={paginas.length}
          />
        ))}
      </div>
    </div>
  )
}
