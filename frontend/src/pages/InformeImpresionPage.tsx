import { useEffect, useState } from 'react'
import { useParams, useSearchParams } from 'react-router'
import type { InformeClinicoResponseDto } from '../api/types'
import { obtenerInformeConResultados } from '../api/informesApi'
import { HojaInforme } from '../components/informes/HojaInforme'
import { elementosDe } from '../components/informes/paginacion'
import { comillasCss, formatearFecha, resumirDatasets } from '../components/informes/textoInforme'
import styles from '../components/informes/Informe.module.css'

/**
 * El documento y nada más (Fase 6.9R.1).
 *
 * <p>Vive FUERA de `MainLayout`: sin navegación, sin botones, sin tarjetas de
 * la aplicación. Se intentó primero ocultar el cromo con reglas `@media print`
 * desde la vista previa, y esa vía obliga a ir persiguiendo cada componente
 * nuevo que alguien añada a la interfaz; en cuanto se escapa uno, aparece
 * impreso en el informe de un hospital. Aquí no hay nada que ocultar porque no
 * hay nada más en la página.
 *
 * <p>Una sola llamada a `/informes/:id/resultados`, la misma que la vista
 * previa, y el mismo `HojaInforme`. Es también la ruta que el backend abre en
 * Chrome headless para generar el PDF (6.9R.2), así que impresión manual y
 * descarga automática comparten un único maquetado y no pueden divergir.
 */
export function InformeImpresionPage() {
  const { informeId } = useParams()
  const [parametros] = useSearchParams()
  const [informe, setInforme] = useState<InformeClinicoResponseDto | null>(null)
  const [error, setError] = useState<string | null>(null)

  // `?auto=1` lanza el diálogo de impresión al terminar de pintar. Sin el
  // parámetro la ruta es solo una vista limpia, útil para revisar el documento
  // sin que salte un diálogo encima.
  const automatico = parametros.get('auto') === '1'

  useEffect(() => {
    if (!informeId) return
    obtenerInformeConResultados(informeId)
      .then(setInforme)
      .catch((e) => setError(e instanceof Error ? e.message : 'No se pudo cargar el informe.'))
  }, [informeId])

  useEffect(() => {
    if (!informe || !automatico) return
    // Dos marcos: el primero pinta el DOM, el segundo deja al navegador aplicar
    // el layout antes de medir las páginas. Imprimir en el mismo tick produce
    // documentos con la última hoja a medio componer.
    const id = requestAnimationFrame(() => requestAnimationFrame(() => window.print()))
    return () => cancelAnimationFrame(id)
  }, [informe, automatico])

  if (error) {
    return <p className={styles.avisoImpresion}>{error}</p>
  }

  if (!informe) {
    // Marcador que la 6.9R.2 puede esperar antes de capturar: mientras exista,
    // el documento no está listo.
    return (
      <p className={styles.avisoImpresion} data-informe-estado="calculando">
        Calculando el informe…
      </p>
    )
  }

  const paginas = informe.paginas ?? []

  return (
    <div className={styles.documento} data-informe-estado="listo">
      {/* Cabecera y pie FÍSICOS: se repiten en cada hoja del PDF, que es algo
          que el contenido del flujo no puede hacer por sí solo. */}
      <style>{reglaPagina(informe)}</style>

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
  )
}

/**
 * Construye el `@page` con cabecera, pie y numeración física.
 *
 * <p>Se genera en tiempo de ejecución porque el título y la fecha son del
 * informe. Lo natural sería `string-set` + `string()`, pero se probó contra el
 * Chrome instalado (152) y las margin boxes salen vacías: Chromium no
 * implementa las cadenas con nombre de CSS GCPM. Los contadores `counter(page)`
 * y `counter(pages)` sí funcionan, y son los que dan la numeración de páginas
 * FÍSICAS —las hojas reales del PDF— que la numeración lógica de la hoja no
 * puede conocer.
 */
function reglaPagina(informe: InformeClinicoResponseDto): string {
  const titulo = comillasCss(informe.tituloVisible)
  const generado = comillasCss(`Generado: ${formatearFecha(informe.generadoEn ?? informe.actualizadoEn)}`)
  const datasets = informe.datasetsUtilizados?.length
    ? comillasCss(`Datos de: ${resumirDatasets(informe.datasetsUtilizados)}`)
    : '""'

  return `
@page {
  size: A4 portrait;
  /* Margen inferior mayor: es donde se apoya el pie físico. */
  margin: 16mm 12mm 18mm;

  @top-left { content: ${titulo}; font-size: 8pt; color: #555; }

  @bottom-left { content: ${generado}; font-size: 7.5pt; color: #555; }
  @bottom-center { content: ${datasets}; font-size: 7.5pt; color: #555; }
  @bottom-right { content: "Página " counter(page) " de " counter(pages); font-size: 7.5pt; color: #555; }
}`
}
