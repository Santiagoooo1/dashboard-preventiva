import { Link, useParams } from 'react-router'
import { useApiResource } from '../hooks/useApiResource'
import { ejecutarDashboard } from '../api/dashboardApi'
import { StateContainer } from '../components/StateContainer'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { WidgetCard } from '../components/widgets/WidgetCard'
import styles from './PanelDashboardPage.module.css'

export function PanelDashboardPage() {
  const { panelId } = useParams<{ panelId: string }>()
  const { data, loading, error } = useApiResource((signal) => ejecutarDashboard(panelId ?? '', signal), [panelId])

  return (
    <div className={styles.page}>
      <StateContainer
        loading={loading}
        error={error}
        empty={data !== null && data.widgets.length === 0}
        emptyMessage="Este panel no tiene widgets activos. Configura los widgets para ver resultados."
        emptyAction={
          data && (
            <Link
              className="btn btnPrimary"
              to={`/datasets/${data.dataset.id}/paneles/${data.panel.id}/widgets`}
            >
              Configurar widgets
            </Link>
          )
        }
      >
        {data && (
          <>
            <Breadcrumbs
              items={[
                { label: 'Datasets', to: '/datasets' },
                { label: data.dataset.codigo, to: `/datasets/${data.dataset.id}` },
                { label: 'Paneles', to: `/datasets/${data.dataset.id}/paneles` },
                { label: `Dashboard de ${data.panel.codigo}` },
              ]}
            />
            <div className={styles.cabecera}>
              <h1>{data.panel.nombre}</h1>
              <Link
                className="btn btnSecondary"
                to={`/datasets/${data.dataset.id}/paneles/${data.panel.id}/widgets`}
              >
                Configurar widgets
              </Link>
            </div>
            <p className={styles.subtitle}>
              {data.dataset.nombre} · {data.resumen.widgetsOk} OK / {data.resumen.widgetsConError} con error de{' '}
              {data.resumen.totalWidgets} widgets
            </p>
            <div className={styles.grid}>
              {data.widgets.map((widget) => (
                <WidgetCard key={widget.panelMetricaId} widget={widget} />
              ))}
            </div>
          </>
        )}
      </StateContainer>
    </div>
  )
}
