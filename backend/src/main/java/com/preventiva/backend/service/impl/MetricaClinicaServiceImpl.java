package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.ConfiguracionMetricaDto;
import com.preventiva.backend.dto.EjecucionMetricaRequestDto;
import com.preventiva.backend.dto.FiltroGrupoDto;
import com.preventiva.backend.dto.FiltroMetricaDto;
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
import com.preventiva.backend.util.MetricaCalculoBasico;
import com.preventiva.backend.util.OperacionMetricaUtil;
import com.preventiva.backend.util.RolAnaliticoUtil;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
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

    /**
     * Ejecuta la métrica delegando en {@link MetricaCalculoBasico}, el mismo
     * motor que usan la serie temporal y la comparativa. Aquí solo se elige la
     * población (rango de fechas + filtros globales) y se envuelve el resultado
     * en su DTO: no hay ni un cálculo propio, para que una métrica no pueda
     * valer una cosa en el KPI y otra en su serie.
     */
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

        MetricaCalculoBasico.Resultado resultado = MetricaCalculoBasico.calcular(
                metrica.getTipoMetrica(), metrica.getConfiguracion(), registros,
                resolverCampo, metrica.getDecimales());

        return mapResultado(metrica, resultado);
    }

    private ResultadoMetricaResponseDto mapResultado(
            MetricaClinica metrica, MetricaCalculoBasico.Resultado resultado) {
        ConfiguracionMetricaDto config = metrica.getConfiguracion();

        return ResultadoMetricaResponseDto.builder()
                .metricaId(metrica.getId())
                .codigo(metrica.getCodigo())
                .nombre(metrica.getNombre())
                .tipoMetrica(metrica.getTipoMetrica().name())
                .valor(resultado.getValor())
                .valorTexto(resultado.getValorTexto())
                .estado(resultado.getEstadoNombre())
                .unidad(metrica.getUnidad())
                .totalNumerador(resultado.getTotalNumerador())
                .totalDenominador(resultado.getTotalDenominador())
                .etiquetaNumerador(config != null ? config.getEtiquetaNumerador() : null)
                .etiquetaDenominador(config != null ? config.getEtiquetaDenominador() : null)
                .items(resultado.getItems())
                .build();
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

    private List<FiltroMetricaDto> filtrosONull(List<FiltroMetricaDto> filtros) {
        return filtros != null ? filtros : List.of();
    }

    /**
     * Valida la configuración contra el catálogo real de campos y operaciones.
     *
     * <p>El frontend ya oculta lo incompatible, pero esta es la comprobación que
     * cuenta: un POST directo con una media sobre un campo de texto, un campo de
     * otro dataset o uno desactivado se rechaza aquí igualmente.
     */
    private void validarConfiguracion(
            TipoMetrica tipoMetrica, ConfiguracionMetricaDto config, Map<String, CampoClinico> camposPorCodigo) {
        if (tipoMetrica == null) {
            throw new IllegalArgumentException("El tipo de métrica es obligatorio.");
        }

        if (config == null) {
            throw new IllegalArgumentException("La configuración de la métrica es obligatoria.");
        }

        if (OperacionMetricaUtil.requiereNumeradorYDenominador(tipoMetrica)) {
            if (config.getNumerador() == null || config.getDenominador() == null) {
                throw new IllegalArgumentException(tipoMetrica + " requiere 'numerador' y 'denominador'.");
            }
            validarFiltros(filtrosONull(config.getNumerador().getFiltros()), camposPorCodigo);
            validarFiltros(filtrosONull(config.getDenominador().getFiltros()), camposPorCodigo);
        }

        if (OperacionMetricaUtil.requiereCampoValor(tipoMetrica)) {
            validarCampoObjetivo(tipoMetrica, config.getCampoValor(), "campoValor", camposPorCodigo);
        }

        if (OperacionMetricaUtil.requiereCampoAgrupacion(tipoMetrica)) {
            CampoClinico agrupacion =
                    validarCampoObjetivo(tipoMetrica, config.getCampoAgrupacion(), "campoAgrupacion", camposPorCodigo);

            // Un identificador tiene tantas categorías como individuos: la
            // «distribución» sería una barra por paciente y la «categoría más
            // frecuente», el paciente con más intervenciones disfrazado de KPI.
            if (RolAnaliticoUtil.esIdentificador(agrupacion, camposPorCodigo.values())) {
                throw new IllegalArgumentException(
                        "El campo '" + agrupacion.getCodigo() + "' identifica al paciente: no puede usarse para "
                                + tipoMetrica + ". Para contar individuos usa CONTEO_DISTINTO.");
            }
        }

        if (config.getMaxCategorias() != null && config.getMaxCategorias() <= 0) {
            throw new IllegalArgumentException("El número máximo de categorías debe ser mayor que cero.");
        }

        // Los filtros base valen para todas las operaciones, incluida PORCENTAJE:
        // son los que acotan la población y hacen condicional el porcentaje.
        validarFiltros(filtrosONull(config.getFiltros()), camposPorCodigo);
    }

    private CampoClinico validarCampoObjetivo(
            TipoMetrica tipoMetrica, String codigo, String nombreAtributo,
            Map<String, CampoClinico> camposPorCodigo) {

        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException(tipoMetrica + " requiere '" + nombreAtributo + "'.");
        }

        CampoClinico campo = obtenerCampoOLanzar(codigo, camposPorCodigo);
        Set<TipoDatoExcel> tiposValidos = OperacionMetricaUtil.tiposDatoValidos(tipoMetrica);

        if (!tiposValidos.isEmpty() && !tiposValidos.contains(campo.getTipoDato())) {
            throw new IllegalArgumentException(
                    "El campo '" + codigo + "' es de tipo " + campo.getTipoDato() + " y " + tipoMetrica
                            + " requiere " + tiposValidos.stream().map(Enum::name).sorted().toList() + ".");
        }

        return campo;
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
        normalizada.setTratamientoNulos(original.getTratamientoNulos());
        normalizada.setEtiquetaNumerador(textoONull(original.getEtiquetaNumerador()));
        normalizada.setEtiquetaDenominador(textoONull(original.getEtiquetaDenominador()));
        normalizada.setMaxCategorias(original.getMaxCategorias());

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

        return OperacionMetricaUtil.esPorcentual(tipoMetrica) ? "%" : null;
    }

    private String textoONull(String valor) {
        return valor != null && !valor.isBlank() ? valor.trim() : null;
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
