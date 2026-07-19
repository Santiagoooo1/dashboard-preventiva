import { Route, Routes } from 'react-router'
import { MainLayout } from '../layout/MainLayout'
import { HomePage } from '../pages/HomePage'
import { DatasetsPage } from '../pages/DatasetsPage'
import { DatasetDetailPage } from '../pages/DatasetDetailPage'
import { DatasetMetricasPage } from '../pages/DatasetMetricasPage'
import { MetricaFormPage } from '../pages/MetricaFormPage'
import { CatalogoPage } from '../pages/CatalogoPage'
import { PanelDashboardPage } from '../pages/PanelDashboardPage'
import { NotFoundPage } from '../pages/NotFoundPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<MainLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/datasets" element={<DatasetsPage />} />
        <Route path="/datasets/:datasetId" element={<DatasetDetailPage />} />
        <Route path="/datasets/:datasetId/metricas" element={<DatasetMetricasPage />} />
        <Route path="/datasets/:datasetId/metricas/nueva" element={<MetricaFormPage />} />
        <Route path="/datasets/:datasetId/metricas/:metricaId/editar" element={<MetricaFormPage />} />
        <Route path="/catalogo" element={<CatalogoPage />} />
        <Route path="/paneles/:panelId/dashboard" element={<PanelDashboardPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
