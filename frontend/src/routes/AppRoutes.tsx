import { Route, Routes } from 'react-router'
import { MainLayout } from '../layout/MainLayout'
import { HomePage } from '../pages/HomePage'
import { CrearDashboardPage } from '../pages/CrearDashboardPage'
import { DatasetsPage } from '../pages/DatasetsPage'
import { DatasetFormPage } from '../pages/DatasetFormPage'
import { DatasetDetailPage } from '../pages/DatasetDetailPage'
import { DatasetCamposPage } from '../pages/DatasetCamposPage'
import { CampoFormPage } from '../pages/CampoFormPage'
import { DatasetMetricasPage } from '../pages/DatasetMetricasPage'
import { MetricaFormPage } from '../pages/MetricaFormPage'
import { DatasetImportPage } from '../pages/DatasetImportPage'
import { PlantillasImportacionPage } from '../pages/PlantillasImportacionPage'
import { PlantillaImportacionFormPage } from '../pages/PlantillaImportacionFormPage'
import { PlantillaMapeosPage } from '../pages/PlantillaMapeosPage'
import { PanelesPage } from '../pages/PanelesPage'
import { PanelFormPage } from '../pages/PanelFormPage'
import { PanelWidgetsPage } from '../pages/PanelWidgetsPage'
import { CatalogoPage } from '../pages/CatalogoPage'
import { PanelDashboardPage } from '../pages/PanelDashboardPage'
import { NotFoundPage } from '../pages/NotFoundPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<MainLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/crear-dashboard" element={<CrearDashboardPage />} />
        <Route path="/datasets" element={<DatasetsPage />} />
        <Route path="/datasets/nuevo" element={<DatasetFormPage />} />
        <Route path="/datasets/:datasetId" element={<DatasetDetailPage />} />
        <Route path="/datasets/:datasetId/editar" element={<DatasetFormPage />} />
        <Route path="/datasets/:datasetId/campos" element={<DatasetCamposPage />} />
        <Route path="/datasets/:datasetId/campos/nuevo" element={<CampoFormPage />} />
        <Route path="/datasets/:datasetId/campos/:campoId/editar" element={<CampoFormPage />} />
        <Route path="/datasets/:datasetId/metricas" element={<DatasetMetricasPage />} />
        <Route path="/datasets/:datasetId/metricas/nueva" element={<MetricaFormPage />} />
        <Route path="/datasets/:datasetId/metricas/:metricaId/editar" element={<MetricaFormPage />} />
        <Route path="/datasets/:datasetId/importar" element={<DatasetImportPage />} />
        <Route path="/datasets/:datasetId/plantillas" element={<PlantillasImportacionPage />} />
        <Route path="/datasets/:datasetId/plantillas/nueva" element={<PlantillaImportacionFormPage />} />
        <Route path="/datasets/:datasetId/plantillas/:plantillaId/editar" element={<PlantillaImportacionFormPage />} />
        <Route path="/datasets/:datasetId/plantillas/:plantillaId/mapeos" element={<PlantillaMapeosPage />} />
        <Route path="/datasets/:datasetId/paneles" element={<PanelesPage />} />
        <Route path="/datasets/:datasetId/paneles/nuevo" element={<PanelFormPage />} />
        <Route path="/datasets/:datasetId/paneles/:panelId/editar" element={<PanelFormPage />} />
        <Route path="/datasets/:datasetId/paneles/:panelId/widgets" element={<PanelWidgetsPage />} />
        <Route path="/catalogo" element={<CatalogoPage />} />
        <Route path="/paneles/:panelId/dashboard" element={<PanelDashboardPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
