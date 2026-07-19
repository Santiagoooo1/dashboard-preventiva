import type { SerieTemporalResponseDto } from '../../api/types'
import { DataTable } from '../DataTable'

interface WidgetSerieTemporalProps {
  serie: SerieTemporalResponseDto
}

export function WidgetSerieTemporal({ serie }: WidgetSerieTemporalProps) {
  if (serie.puntos) {
    return (
      <DataTable
        columns={[
          { key: 'periodo', header: 'Periodo' },
          { key: 'valor', header: 'Valor' },
          { key: 'totalNumerador', header: 'Numerador', render: (p) => p.totalNumerador ?? '—' },
          { key: 'totalDenominador', header: 'Denominador', render: (p) => p.totalDenominador ?? '—' },
        ]}
        rows={serie.puntos}
        getRowKey={(p) => p.periodo}
      />
    )
  }

  if (serie.series) {
    return (
      <>
        {serie.series.map((segmento) => (
          <div key={segmento.etiqueta}>
            <h4>{segmento.etiqueta}</h4>
            <DataTable
              columns={[
                { key: 'periodo', header: 'Periodo' },
                { key: 'valor', header: 'Valor' },
              ]}
              rows={segmento.puntos}
              getRowKey={(p) => p.periodo}
            />
          </div>
        ))}
      </>
    )
  }

  return <p>Sin puntos.</p>
}
