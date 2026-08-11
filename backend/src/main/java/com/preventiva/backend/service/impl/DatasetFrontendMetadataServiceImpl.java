package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.CampoClinicoMetadataDto;
import com.preventiva.backend.dto.CampoMetricaMetadataDto;
import com.preventiva.backend.dto.CampoRolesDto;
import com.preventiva.backend.dto.DatasetClinicoResponseDto;
import com.preventiva.backend.dto.DatasetFrontendMetadataResponseDto;
import com.preventiva.backend.dto.MetadataMetricasResponseDto;
import com.preventiva.backend.dto.MetricaClinicaResponseDto;
import com.preventiva.backend.dto.PanelClinicoResponseDto;
import com.preventiva.backend.dto.PlantillaImportacionResponseDto;
import com.preventiva.backend.dto.ResumenConfiguracionDatasetDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.entity.DatasetClinico;
import com.preventiva.backend.entity.MetricaClinica;
import com.preventiva.backend.entity.PanelClinico;
import com.preventiva.backend.entity.PlantillaImportacion;
import com.preventiva.backend.repository.CampoClinicoRepository;
import com.preventiva.backend.repository.DatasetClinicoRepository;
import com.preventiva.backend.repository.MetricaClinicaRepository;
import com.preventiva.backend.repository.PanelClinicoRepository;
import com.preventiva.backend.repository.PlantillaImportacionRepository;
import com.preventiva.backend.service.interfaces.DatasetFrontendMetadataService;
import com.preventiva.backend.util.CampoRolesUtil;
import com.preventiva.backend.util.OperadorFiltroCompatibilidadUtil;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class DatasetFrontendMetadataServiceImpl implements DatasetFrontendMetadataService {

    private final DatasetClinicoRepository datasetClinicoRepository;
    private final CampoClinicoRepository campoClinicoRepository;
    private final MetricaClinicaRepository metricaClinicaRepository;
    private final PanelClinicoRepository panelClinicoRepository;
    private final PlantillaImportacionRepository plantillaImportacionRepository;

    @Override
    public DatasetFrontendMetadataResponseDto obtenerMetadataDataset(Long datasetId) {
        DatasetClinico dataset = obtenerDatasetOLanzar(datasetId);
        List<CampoClinico> campos = campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId);

        List<CampoClinicoMetadataDto> camposDto = campos.stream()
                .map(this::mapCampoMetadata)
                .toList();

        List<MetricaClinicaResponseDto> metricas = metricaClinicaRepository.findByDatasetIdAndActivaTrue(datasetId)
                .stream().map(this::mapMetrica).toList();

        List<PanelClinicoResponseDto> paneles = panelClinicoRepository.findByDatasetIdAndActivoTrue(datasetId)
                .stream().map(this::mapPanel).toList();

        List<PlantillaImportacionResponseDto> plantillas =
                plantillaImportacionRepository.findByDatasetIdAndActivaTrue(datasetId)
                        .stream().map(this::mapPlantilla).toList();

        ResumenConfiguracionDatasetDto resumen = ResumenConfiguracionDatasetDto.builder()
                .totalCampos(campos.size())
                .totalMetricas(metricas.size())
                .totalPaneles(paneles.size())
                .totalPlantillasImportacion(plantillas.size())
                .build();

        return DatasetFrontendMetadataResponseDto.builder()
                .dataset(mapDataset(dataset))
                .campos(camposDto)
                .camposFiltrables(campos.stream().filter(c -> CampoRolesUtil.esFiltrable(c.getTipoDato()))
                        .map(CampoClinico::getCodigo).toList())
                .camposNumericos(campos.stream().filter(c -> CampoRolesUtil.esNumerico(c.getTipoDato()))
                        .map(CampoClinico::getCodigo).toList())
                .camposAgrupables(campos.stream().filter(c -> CampoRolesUtil.esAgrupable(c.getTipoDato()))
                        .map(CampoClinico::getCodigo).toList())
                .camposFecha(campos.stream().filter(c -> CampoRolesUtil.esFecha(c.getTipoDato()))
                        .map(CampoClinico::getCodigo).toList())
                .metricas(metricas)
                .paneles(paneles)
                .plantillasImportacion(plantillas)
                .resumenConfiguracion(resumen)
                .build();
    }

    @Override
    public MetadataMetricasResponseDto obtenerMetadataMetricas(Long datasetId) {
        DatasetClinico dataset = obtenerDatasetOLanzar(datasetId);
        List<CampoClinico> campos = campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId);

        List<CampoMetricaMetadataDto> camposDto = campos.stream()
                .map(campo -> CampoMetricaMetadataDto.builder()
                        .codigo(campo.getCodigo())
                        .etiqueta(campo.getEtiqueta())
                        .tipoDato(campo.getTipoDato().name())
                        .esComun(campo.getEsComun())
                        .operadoresCompatibles(OperadorFiltroCompatibilidadUtil
                                .operadoresCompatibles(campo.getTipoDato()).stream().map(Enum::name).toList())
                        .utilizableComoCampoValor(CampoRolesUtil.esNumerico(campo.getTipoDato()))
                        .utilizableComoCampoAgrupacion(CampoRolesUtil.esAgrupable(campo.getTipoDato()))
                        .utilizableComoCampoFecha(CampoRolesUtil.esFecha(campo.getTipoDato()))
                        .prioridadDashboard(campo.getPrioridadDashboard().name())
                        .build())
                .toList();

        return MetadataMetricasResponseDto.builder()
                .dataset(mapDataset(dataset))
                .campos(camposDto)
                .build();
    }

    private CampoClinicoMetadataDto mapCampoMetadata(CampoClinico campo) {
        CampoRolesDto roles = CampoRolesDto.builder()
                .filtrable(CampoRolesUtil.esFiltrable(campo.getTipoDato()))
                .agrupable(CampoRolesUtil.esAgrupable(campo.getTipoDato()))
                .numerico(CampoRolesUtil.esNumerico(campo.getTipoDato()))
                .fecha(CampoRolesUtil.esFecha(campo.getTipoDato()))
                .build();

        return CampoClinicoMetadataDto.builder()
                .id(campo.getId())
                .codigo(campo.getCodigo())
                .etiqueta(campo.getEtiqueta())
                .tipoDato(campo.getTipoDato().name())
                .esComun(campo.getEsComun())
                .obligatorio(campo.getObligatorio())
                .activo(campo.getActivo())
                .roles(roles)
                .prioridadDashboard(campo.getPrioridadDashboard().name())
                .build();
    }

    private DatasetClinicoResponseDto mapDataset(DatasetClinico dataset) {
        return DatasetClinicoResponseDto.builder()
                .id(dataset.getId())
                .codigo(dataset.getCodigo())
                .nombre(dataset.getNombre())
                .descripcion(dataset.getDescripcion())
                .hospitalId(dataset.getHospital() != null ? dataset.getHospital().getId() : null)
                .hospitalNombre(dataset.getHospital() != null ? dataset.getHospital().getNombre() : null)
                .activo(dataset.getActivo())
                .build();
    }

    private MetricaClinicaResponseDto mapMetrica(MetricaClinica metrica) {
        return MetricaClinicaResponseDto.builder()
                .id(metrica.getId())
                .datasetId(metrica.getDataset().getId())
                .codigo(metrica.getCodigo())
                .nombre(metrica.getNombre())
                .descripcion(metrica.getDescripcion())
                .tipoMetrica(metrica.getTipoMetrica().name())
                .configuracion(metrica.getConfiguracion())
                .unidad(metrica.getUnidad())
                .decimales(metrica.getDecimales())
                .orden(metrica.getOrden())
                .activa(metrica.getActiva())
                .build();
    }

    private PanelClinicoResponseDto mapPanel(PanelClinico panel) {
        return PanelClinicoResponseDto.builder()
                .id(panel.getId())
                .datasetId(panel.getDataset().getId())
                .codigo(panel.getCodigo())
                .nombre(panel.getNombre())
                .descripcion(panel.getDescripcion())
                .orden(panel.getOrden())
                .activo(panel.getActivo())
                .build();
    }

    private PlantillaImportacionResponseDto mapPlantilla(PlantillaImportacion plantilla) {
        return PlantillaImportacionResponseDto.builder()
                .id(plantilla.getId())
                .datasetId(plantilla.getDataset().getId())
                .datasetCodigo(plantilla.getDataset().getCodigo())
                .nombre(plantilla.getNombre())
                .descripcion(plantilla.getDescripcion())
                .origen(plantilla.getOrigen() != null ? plantilla.getOrigen().name() : null)
                .filaCabecera(plantilla.getFilaCabecera())
                .activa(plantilla.getActiva())
                .build();
    }

    private DatasetClinico obtenerDatasetOLanzar(Long datasetId) {
        return datasetClinicoRepository.findById(datasetId)
                .orElseThrow(() -> new NoSuchElementException("No existe el dataset con id: " + datasetId));
    }
}
