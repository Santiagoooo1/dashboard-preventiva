package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.ConfiguracionMetricaDto;
import com.preventiva.backend.dto.EjecucionMetricaRequestDto;
import com.preventiva.backend.dto.FiltroGrupoDto;
import com.preventiva.backend.dto.FiltroMetricaDto;
import com.preventiva.backend.dto.ItemDistribucionDto;
import com.preventiva.backend.dto.MetricaClinicaRequestDto;
import com.preventiva.backend.dto.MetricaClinicaResponseDto;
import com.preventiva.backend.dto.PreviewMetricaRequestDto;
import com.preventiva.backend.dto.ResultadoMetricaResponseDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.entity.DatasetClinico;
import com.preventiva.backend.entity.MetricaClinica;
import com.preventiva.backend.entity.RegistroClinicoGenerico;
import com.preventiva.backend.enums.OperadorFiltro;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.enums.TipoMetrica;
import com.preventiva.backend.repository.CampoClinicoRepository;
import com.preventiva.backend.repository.DatasetClinicoRepository;
import com.preventiva.backend.repository.MetricaClinicaRepository;
import com.preventiva.backend.repository.RegistroClinicoGenericoRepository;
import com.preventiva.backend.service.interfaces.MetricaClinicaService;
import com.preventiva.backend.util.FiltroMetricaEvaluator;
import com.preventiva.backend.util.RegistroClinicoGenericoValueReader;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetricaClinicaServiceImpl implements MetricaClinicaService {

    private final MetricaClinicaRepository metricaClinicaRepository;
    private final DatasetClinicoRepository datasetClinicoRepository;
    private final CampoClinicoRepository campoClinicoRepository;
    private final RegistroClinicoGenericoRepository registroClinicoGenericoRepository;

    @Override
    public List<MetricaClinicaResponseDto> listarPorDataset(Long datasetId) {
        obtenerDatasetOLanzar(datasetId);

        return metricaClinicaRepository.findByDatasetIdAndActivaTrue(datasetId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public MetricaClinicaResponseDto obtenerPorId(Long id) {
        return mapToDto(obtenerMetricaOLanzar(id));
    }

    @Override
    public MetricaClinicaResponseDto crear(Long datasetId, MetricaClinicaRequestDto request) {
        DatasetClinico dataset = obtenerDatasetActivoOLanzar(datasetId);

        metricaClinicaRepository.findByDatasetIdAndCodigoIgnoreCaseAndActivaTrue(datasetId, request.getCodigo())
                .ifPresent(m -> {
                    throw new IllegalArgumentException(
                            "Ya existe una métrica activa con el código: " + request.getCodigo());
                });

        Map<String, CampoClinico> camposPorCodigo = obtenerCamposActivosPorCodigo(datasetId);
        validarConfiguracion(request.getTipoMetrica(), request.getConfiguracion(), camposPorCodigo);

        MetricaClinica metrica = MetricaClinica.builder()
                .dataset(dataset)
                .codigo(request.getCodigo())
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .tipoMetrica(request.getTipoMetrica())
                .configuracion(normalizarConfiguracion(request.getConfiguracion()))
                .unidad(resolverUnidad(request.getUnidad(), request.getTipoMetrica()))
                .decimales(request.getDecimales() != null ? request.getDecimales() : 2)
                .orden(request.getOrden() != null ? request.getOrden() : 0)
                .activa(true)
                .build();

        return mapToDto(metricaClinicaRepository.save(metrica));
    }

    @Override
    public MetricaClinicaResponseDto actualizar(Long id, MetricaClinicaRequestDto request) {
        MetricaClinica metrica = obtenerMetricaOLanzar(id);
        Long datasetId = metrica.getDataset().getId();

        metricaClinicaRepository.findByDatasetIdAndCodigoIgnoreCaseAndActivaTrue(datasetId, request.getCodigo())
                .filter(m -> !m.getId().equals(id))
                .ifPresent(m -> {
                    throw new IllegalArgumentException(
                            "Ya existe otra métrica activa con el código: " + request.getCodigo());
                });

        Map<String, CampoClinico> camposPorCodigo = obtenerCamposActivosPorCodigo(datasetId);
        validarConfiguracion(request.getTipoMetrica(), request.getConfiguracion(), camposPorCodigo);

        metrica.setCodigo(request.getCodigo());
        metrica.setNombre(request.getNombre());
        metrica.setDescripcion(request.getDescripcion());
        metrica.setTipoMetrica(request.getTipoMetrica());
        metrica.setConfiguracion(normalizarConfiguracion(request.getConfiguracion()));
        metrica.setUnidad(resolverUnidad(request.getUnidad(), request.getTipoMetrica()));
        metrica.setDecimales(request.getDecimales() != null ? request.getDecimales() : 2);
        metrica.setOrden(request.getOrden() != null ? request.getOrden() : 0);

        return mapToDto(metricaClinicaRepository.save(metrica));
    }

    @Override
    public void desactivar(Long id) {
        MetricaClinica metrica = obtenerMetricaOLanzar(id);
        metrica.setActiva(false);
        metricaClinicaRepository.save(metrica);
    }

    @Override
    public ResultadoMetricaResponseDto ejecutar(Long id, EjecucionMetricaRequestDto request) {
        MetricaClinica metrica = obtenerMetricaOLanzar(id);
        LocalDate fechaDesde = request != null ? request.getFechaDesde() : null;
        LocalDate fechaHasta = request != null ? request.getFechaHasta() : null;
        List<FiltroMetricaDto> filtrosGlobales = request != null ? request.getFiltrosGlobales() : null;

        return calcular(metrica, fechaDesde, fechaHasta, filtrosGlobales);
    }

    @Override
    public ResultadoMetricaResponseDto preview(Long datasetId, PreviewMetricaRequestDto request) {
        DatasetClinico dataset = obtenerDatasetActivoOLanzar(datasetId);
        MetricaClinicaRequestDto metricaRequest = request.getMetrica();

        Map<String, CampoClinico> camposPorCodigo = obtenerCamposActivosPorCodigo(datasetId);
        validarConfiguracion(metricaRequest.getTipoMetrica(), metricaRequest.getConfiguracion(), camposPorCodigo);

        MetricaClinica metricaTemporal = MetricaClinica.builder()
                .dataset(dataset)
                .codigo(metricaRequest.getCodigo())
                .nombre(metricaRequest.getNombre())
                .descripcion(metricaRequest.getDescripcion())
                .tipoMetrica(metricaRequest.getTipoMetrica())
                .configuracion(normalizarConfiguracion(metricaRequest.getConfiguracion()))
                .unidad(resolverUnidad(metricaRequest.getUnidad(), metricaRequest.getTipoMetrica()))
                .decimales(metricaRequest.getDecimales() != null ? metricaRequest.getDecimales() : 2)
                .orden(metricaRequest.getOrden() != null ? metricaRequest.getOrden() : 0)
                .activa(true)
                .build();

        return calcular(metricaTemporal, request.getFechaDesde(), request.getFechaHasta(), null);
    }

    private ResultadoMetricaResponseDto calcular(
            MetricaClinica metrica, LocalDate fechaDesde, LocalDate fechaHasta,
            List<FiltroMetricaDto> filtrosGlobales) {
        Long datasetId = metrica.getDataset().getId();
        Map<String, CampoClinico> camposPorCodigo = obtenerCamposActivosPorCodigo(datasetId);
        Function<String, CampoClinico> resolverCampo = camposPorCodigo::get;
        List<FiltroMetricaDto> filtrosGlobalesEfectivos = filtrosONull(filtrosGlobales);

        List<RegistroClinicoGenerico> registros = registroClinicoGenericoRepository.findByDatasetId(datasetId)
                .stream()
                .filter(r -> cumpleRangoFecha(r, fechaDesde, fechaHasta))
                .filter(r -> FiltroMetricaEvaluator.cumpleTodos(r, filtrosGlobalesEfectivos, resolverCampo))
                .toList();

        ConfiguracionMetricaDto config = metrica.getConfiguracion();

        return switch (metrica.getTipoMetrica()) {
            case CONTEO -> calcularConteo(metrica, registros, config, resolverCampo);
            case PORCENTAJE -> calcularPorcentaje(metrica, registros, config, resolverCampo);
            case PROMEDIO -> calcularPromedio(metrica, registros, config, resolverCampo);
            case SUMA -> calcularSuma(metrica, registros, config, resolverCampo);
            case DISTRIBUCION -> calcularDistribucion(metrica, registros, config, resolverCampo);
        };
    }

    private boolean cumpleRangoFecha(RegistroClinicoGenerico registro, LocalDate desde, LocalDate hasta) {
        if (desde == null && hasta == null) {
            return true;
        }

        LocalDate fecha = registro.getFechaEvento();

        if (fecha == null) {
            return false;
        }

        if (desde != null && fecha.isBefore(desde)) {
            return false;
        }

        return hasta == null || !fecha.isAfter(hasta);
    }

    private ResultadoMetricaResponseDto calcularConteo(
            MetricaClinica metrica, List<RegistroClinicoGenerico> registros,
            ConfiguracionMetricaDto config, Function<String, CampoClinico> resolverCampo) {
        List<FiltroMetricaDto> filtros = filtrosONull(config.getFiltros());

        long total = registros.stream()
                .filter(r -> FiltroMetricaEvaluator.cumpleTodos(r, filtros, resolverCampo))
                .count();

        return construirResultadoEscalar(metrica, total);
    }

    private ResultadoMetricaResponseDto calcularPorcentaje(
            MetricaClinica metrica, List<RegistroClinicoGenerico> registros,
            ConfiguracionMetricaDto config, Function<String, CampoClinico> resolverCampo) {
        List<FiltroMetricaDto> filtrosNum = config.getNumerador() != null
                ? filtrosONull(config.getNumerador().getFiltros()) : List.of();
        List<FiltroMetricaDto> filtrosDen = config.getDenominador() != null
                ? filtrosONull(config.getDenominador().getFiltros()) : List.of();

        long numerador = registros.stream()
                .filter(r -> FiltroMetricaEvaluator.cumpleTodos(r, filtrosNum, resolverCampo))
                .count();
        long denominador = registros.stream()
                .filter(r -> FiltroMetricaEvaluator.cumpleTodos(r, filtrosDen, resolverCampo))
                .count();

        double valor = denominador == 0 ? 0.0 : (numerador * 100.0) / denominador;

        return ResultadoMetricaResponseDto.builder()
                .metricaId(metrica.getId())
                .codigo(metrica.getCodigo())
                .nombre(metrica.getNombre())
                .tipoMetrica(metrica.getTipoMetrica().name())
                .valor(redondear(valor, metrica.getDecimales()))
                .unidad(metrica.getUnidad())
                .totalNumerador(numerador)
                .totalDenominador(denominador)
                .build();
    }

    private ResultadoMetricaResponseDto calcularPromedio(
            MetricaClinica metrica, List<RegistroClinicoGenerico> registros,
            ConfiguracionMetricaDto config, Function<String, CampoClinico> resolverCampo) {
        List<FiltroMetricaDto> filtros = filtrosONull(config.getFiltros());
        CampoClinico campoValor = resolverCampo.apply(config.getCampoValor());

        if (campoValor == null) {
            return construirResultadoEscalar(metrica, 0.0);
        }

        List<Double> valores = registros.stream()
                .filter(r -> FiltroMetricaEvaluator.cumpleTodos(r, filtros, resolverCampo))
                .map(r -> leerNumero(r, campoValor))
                .filter(Objects::nonNull)
                .toList();

        double promedio = valores.isEmpty()
                ? 0.0
                : valores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        return construirResultadoEscalar(metrica, promedio);
    }

    private ResultadoMetricaResponseDto calcularSuma(
            MetricaClinica metrica, List<RegistroClinicoGenerico> registros,
            ConfiguracionMetricaDto config, Function<String, CampoClinico> resolverCampo) {
        List<FiltroMetricaDto> filtros = filtrosONull(config.getFiltros());
        CampoClinico campoValor = resolverCampo.apply(config.getCampoValor());

        if (campoValor == null) {
            return construirResultadoEscalar(metrica, 0.0);
        }

        double suma = registros.stream()
                .filter(r -> FiltroMetricaEvaluator.cumpleTodos(r, filtros, resolverCampo))
                .map(r -> leerNumero(r, campoValor))
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        return construirResultadoEscalar(metrica, suma);
    }

    private ResultadoMetricaResponseDto calcularDistribucion(
            MetricaClinica metrica, List<RegistroClinicoGenerico> registros,
            ConfiguracionMetricaDto config, Function<String, CampoClinico> resolverCampo) {
        List<FiltroMetricaDto> filtros = filtrosONull(config.getFiltros());
        CampoClinico campoAgrupacion = resolverCampo.apply(config.getCampoAgrupacion());

        if (campoAgrupacion == null) {
            return ResultadoMetricaResponseDto.builder()
                    .metricaId(metrica.getId())
                    .codigo(metrica.getCodigo())
                    .nombre(metrica.getNombre())
                    .tipoMetrica(metrica.getTipoMetrica().name())
                    .unidad(metrica.getUnidad())
                    .items(List.of())
                    .build();
        }

        Map<String, Long> conteos = new TreeMap<>();

        for (RegistroClinicoGenerico registro : registros) {
            if (!FiltroMetricaEvaluator.cumpleTodos(registro, filtros, resolverCampo)) {
                continue;
            }

            Object valorCrudo = RegistroClinicoGenericoValueReader.leerValorCrudo(registro, campoAgrupacion);
            Object valor = RegistroClinicoGenericoValueReader.coercionar(valorCrudo, campoAgrupacion.getTipoDato());

            String etiqueta = valor != null ? String.valueOf(valor) : "Sin dato";
            conteos.merge(etiqueta, 1L, Long::sum);
        }

        List<ItemDistribucionDto> items = conteos.entrySet().stream()
                .map(e -> ItemDistribucionDto.builder().etiqueta(e.getKey()).valor(e.getValue()).build())
                .toList();

        return ResultadoMetricaResponseDto.builder()
                .metricaId(metrica.getId())
                .codigo(metrica.getCodigo())
                .nombre(metrica.getNombre())
                .tipoMetrica(metrica.getTipoMetrica().name())
                .unidad(metrica.getUnidad())
                .items(items)
                .build();
    }

    private Double leerNumero(RegistroClinicoGenerico registro, CampoClinico campoValor) {
        Object valorCrudo = RegistroClinicoGenericoValueReader.leerValorCrudo(registro, campoValor);
        Object valor = RegistroClinicoGenericoValueReader.coercionar(valorCrudo, campoValor.getTipoDato());

        return valor instanceof Number n ? n.doubleValue() : null;
    }

    private ResultadoMetricaResponseDto construirResultadoEscalar(MetricaClinica metrica, double valor) {
        return ResultadoMetricaResponseDto.builder()
                .metricaId(metrica.getId())
                .codigo(metrica.getCodigo())
                .nombre(metrica.getNombre())
                .tipoMetrica(metrica.getTipoMetrica().name())
                .valor(redondear(valor, metrica.getDecimales()))
                .unidad(metrica.getUnidad())
                .build();
    }

    private Double redondear(double valor, Integer decimales) {
        int escala = decimales != null ? decimales : 2;
        return BigDecimal.valueOf(valor).setScale(escala, RoundingMode.HALF_UP).doubleValue();
    }

    private List<FiltroMetricaDto> filtrosONull(List<FiltroMetricaDto> filtros) {
        return filtros != null ? filtros : List.of();
    }

    private void validarConfiguracion(
            TipoMetrica tipoMetrica, ConfiguracionMetricaDto config, Map<String, CampoClinico> camposPorCodigo) {
        if (config == null) {
            throw new IllegalArgumentException("La configuración de la métrica es obligatoria.");
        }

        switch (tipoMetrica) {
            case CONTEO -> validarFiltros(filtrosONull(config.getFiltros()), camposPorCodigo);
            case PORCENTAJE -> {
                if (config.getNumerador() == null || config.getDenominador() == null) {
                    throw new IllegalArgumentException("PORCENTAJE requiere 'numerador' y 'denominador'.");
                }
                validarFiltros(filtrosONull(config.getNumerador().getFiltros()), camposPorCodigo);
                validarFiltros(filtrosONull(config.getDenominador().getFiltros()), camposPorCodigo);
            }
            case PROMEDIO, SUMA -> {
                if (config.getCampoValor() == null || config.getCampoValor().isBlank()) {
                    throw new IllegalArgumentException(tipoMetrica + " requiere 'campoValor'.");
                }

                CampoClinico campoValor = obtenerCampoOLanzar(config.getCampoValor(), camposPorCodigo);

                if (campoValor.getTipoDato() != TipoDatoExcel.ENTERO && campoValor.getTipoDato() != TipoDatoExcel.DECIMAL) {
                    throw new IllegalArgumentException(
                            "El campo '" + config.getCampoValor() + "' debe ser ENTERO o DECIMAL para " + tipoMetrica + ".");
                }

                validarFiltros(filtrosONull(config.getFiltros()), camposPorCodigo);
            }
            case DISTRIBUCION -> {
                if (config.getCampoAgrupacion() == null || config.getCampoAgrupacion().isBlank()) {
                    throw new IllegalArgumentException("DISTRIBUCION requiere 'campoAgrupacion'.");
                }

                obtenerCampoOLanzar(config.getCampoAgrupacion(), camposPorCodigo);
                validarFiltros(filtrosONull(config.getFiltros()), camposPorCodigo);
            }
        }
    }

    private void validarFiltros(List<FiltroMetricaDto> filtros, Map<String, CampoClinico> camposPorCodigo) {
        for (FiltroMetricaDto filtro : filtros) {
            if (filtro.getCampo() == null || filtro.getCampo().isBlank()) {
                throw new IllegalArgumentException("Cada filtro debe indicar un campo.");
            }

            if (filtro.getOperador() == null) {
                throw new IllegalArgumentException("Cada filtro debe indicar un operador.");
            }

            CampoClinico campo = obtenerCampoOLanzar(filtro.getCampo(), camposPorCodigo);
            validarOperadorCompatible(filtro.getOperador(), campo.getTipoDato(), filtro.getCampo());
        }
    }

    private void validarOperadorCompatible(OperadorFiltro operador, TipoDatoExcel tipoDato, String codigoCampo) {
        boolean esOrdenable = tipoDato == TipoDatoExcel.ENTERO
                || tipoDato == TipoDatoExcel.DECIMAL
                || tipoDato == TipoDatoExcel.FECHA;

        boolean esComparacionOrden = operador == OperadorFiltro.GT || operador == OperadorFiltro.GTE
                || operador == OperadorFiltro.LT || operador == OperadorFiltro.LTE;

        if (esComparacionOrden && !esOrdenable) {
            throw new IllegalArgumentException(
                    "El operador " + operador + " solo es válido sobre campos ENTERO, DECIMAL o FECHA (campo '"
                            + codigoCampo + "').");
        }

        if (operador == OperadorFiltro.CONTAINS && tipoDato != TipoDatoExcel.TEXTO) {
            throw new IllegalArgumentException(
                    "El operador CONTAINS solo es válido sobre campos TEXTO (campo '" + codigoCampo + "').");
        }
    }

    private CampoClinico obtenerCampoOLanzar(String codigo, Map<String, CampoClinico> camposPorCodigo) {
        CampoClinico campo = camposPorCodigo.get(codigo);

        if (campo == null) {
            throw new IllegalArgumentException("El campo '" + codigo + "' no existe o no está activo en este dataset.");
        }

        return campo;
    }

    private Map<String, CampoClinico> obtenerCamposActivosPorCodigo(Long datasetId) {
        return campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId)
                .stream()
                .collect(Collectors.toMap(CampoClinico::getCodigo, c -> c, (a, b) -> a));
    }

    private ConfiguracionMetricaDto normalizarConfiguracion(ConfiguracionMetricaDto original) {
        ConfiguracionMetricaDto normalizada = new ConfiguracionMetricaDto();
        normalizada.setFiltros(filtrosONull(original.getFiltros()));
        normalizada.setCampoValor(original.getCampoValor());
        normalizada.setCampoAgrupacion(original.getCampoAgrupacion());

        if (original.getNumerador() != null) {
            FiltroGrupoDto numerador = new FiltroGrupoDto();
            numerador.setFiltros(filtrosONull(original.getNumerador().getFiltros()));
            normalizada.setNumerador(numerador);
        }

        if (original.getDenominador() != null) {
            FiltroGrupoDto denominador = new FiltroGrupoDto();
            denominador.setFiltros(filtrosONull(original.getDenominador().getFiltros()));
            normalizada.setDenominador(denominador);
        }

        return normalizada;
    }

    private String resolverUnidad(String unidad, TipoMetrica tipoMetrica) {
        if (unidad != null) {
            return unidad;
        }

        return tipoMetrica == TipoMetrica.PORCENTAJE ? "%" : null;
    }

    private DatasetClinico obtenerDatasetOLanzar(Long datasetId) {
        return datasetClinicoRepository.findById(datasetId)
                .orElseThrow(() -> new NoSuchElementException("No existe el dataset con id: " + datasetId));
    }

    private DatasetClinico obtenerDatasetActivoOLanzar(Long datasetId) {
        DatasetClinico dataset = obtenerDatasetOLanzar(datasetId);

        if (!Boolean.TRUE.equals(dataset.getActivo())) {
            throw new IllegalArgumentException("El dataset está desactivado.");
        }

        return dataset;
    }

    private MetricaClinica obtenerMetricaOLanzar(Long id) {
        return metricaClinicaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe la métrica con id: " + id));
    }

    private MetricaClinicaResponseDto mapToDto(MetricaClinica metrica) {
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
}
