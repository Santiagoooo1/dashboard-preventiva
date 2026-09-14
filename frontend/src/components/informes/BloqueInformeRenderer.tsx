import type { BloqueInformeResponseDto, ComparacionInteranualRequestDto } from '../../api/types'
import { DashboardWidgetRenderer } from '../dashboard/DashboardWidgetRenderer'
import { ETIQUETA_COMPARACION } from '../comparacion/conceptosComparables'
import { MatrizCategoriasInteranual, MatrizInteranual } from '../comparacion/MatrizInteranual'
import { ResumenAnual } from '../comparacion/ResumenAnual'
import { GraficaInteranual } from '../comparacion/GraficaInteranual'
import styles from './Informe.module.css'

interface BloqueInformeRendererProps {
  bloque: BloqueInformeResponseDto
}

/**
 * Pinta un bloque del informe (Fase 6.9Q).
 *
 * <p>Reutiliza los componentes reales del dashboard y de la comparación, no
 * capturas ni copias: lo que se ve en el informe es el mismo gráfico vectorial
 * que en su pantalla de origen. Eso es lo que permitirá que el PDF de 6.9R
 * salga con calidad y texto seleccionable en vez de una imagen pixelada.
 *
 * <p>El texto se pinta como texto, nunca como HTML: los informes los escriben
 * personas y no hay razón para aceptar marcado arbitrario.
 */
export function BloqueInformeRenderer({ bloque }: BloqueInformeRendererProps) {
  // Una referencia rota no tumba el documento: se dice qué pasa y se sigue.
  if (!bloque.disponible) {
    return (
      <div className={styles.bloqueNoDisponible}>
        <p>{bloque.motivoNoDisponible ?? 'Este elemento ya no está disponible.'}</p>
      </div>
    )
  }

  switch (bloque.tipoBloque) {
    case 'TITULO':
      return <h2 className={styles.titulo}>{bloque.contenidoTexto}</h2>

    case 'SUBTITULO':
      return <h3 className={styles.subtitulo}>{bloque.contenidoTexto}</h3>

    case 'TEXTO':
      // `white-space: pre-wrap` conserva los saltos de línea sin necesidad de
      // interpretar marcado.
      return <p className={styles.texto}>{bloque.contenidoTexto}</p>

    case 'SEPARADOR':
      return <hr className={styles.separador} />

    case 'SALTO_PAGINA':
      return <div className={styles.saltoPagina} aria-hidden="true" />

    case 'COMPARACION_INTERANUAL': {
      const comparacion = bloque.comparacion
      // Mientras llega el cálculo se describe la configuración en vez de dejar
      // un hueco mudo: con varias comparaciones en la misma hoja no se sabría
      // cuál es cuál.
      if (!comparacion) return <ComparacionPendiente config={bloque.configuracionComparacion} />
      if (!comparacion.comparable) {
        return (
          <div className={styles.bloqueNoDisponible}>
            <p>{comparacion.motivoNoComparable}</p>
          </div>
        )
      }
      return (
        <div>
          <h4 className={styles.tituloBloque}>{comparacion.etiquetaConcepto}</h4>
          <ResumenAnual comparacion={comparacion} />
          {comparacion.tipoComparacion === 'DISTRIBUCION' ? (
            <MatrizCategoriasInteranual comparacion={comparacion} />
          ) : (
            <MatrizInteranual comparacion={comparacion} />
          )}
          <GraficaInteranual comparacion={comparacion} />
        </div>
      )
    }

    default: {
      // KPI, GRAFICA y TABLA comparten renderizador con el dashboard.
      const widget = bloque.widget
      if (!widget) return <PendienteDeCalculo />
      return (
        <div className={styles.bloqueWidget}>
          <h4 className={styles.tituloBloque}>{bloque.tituloPersonalizado ?? widget.titulo}</h4>
          <DashboardWidgetRenderer widget={widget} />
        </div>
      )
    }
  }
}

/**
 * Hueco mientras llega el resultado.
 *
 * <p>Desde la 6.9Q.3 el editor también pinta cifras reales, así que esto ya no
 * es un estado permanente: solo se ve el instante entre añadir un bloque y
 * recibir su cálculo. Ocupa sitio a propósito, para que la hoja no dé un salto
 * cuando el dato entra.
 */
function PendienteDeCalculo() {
  return <div className={styles.esqueleto} aria-label="Calculando…" />
}

function ComparacionPendiente({ config }: { config?: ComparacionInteranualRequestDto | null }) {
  if (!config) return <PendienteDeCalculo />
  const datasets = config.datasetIds.length
  return (
    <p className={styles.pendiente}>
      Comparación entre años de «{config.codigoCanonico}» ({ETIQUETA_COMPARACION[config.tipoComparacion]},
      por {config.granularidad.toLowerCase()}, {datasets} {datasets === 1 ? 'dataset' : 'datasets'}).
      Calculando…
    </p>
  )
}
