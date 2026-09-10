package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.ComparativaRequestDto;
import com.preventiva.backend.dto.ConfiguracionMetricaDto;
import com.preventiva.backend.dto.ComparativaResponseDto;
import com.preventiva.backend.dto.FiltroMetricaDto;
import com.preventiva.backend.dto.ItemComparativaDto;
import com.preventiva.backend.dto.PanelMetricaSerieDto;
import com.preventiva.backend.dto.PanelSerieTemporalResponseDto;
import com.preventiva.backend.dto.PuntoSerieDto;
import com.preventiva.backend.dto.SerieSegmentadaDto;
import com.preventiva.backend.dto.SerieTemporalRequestDto;
import com.preventiva.backend.dto.SerieTemporalResponseDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.entity.MetricaClinica;
import com.preventiva.backend.entity.PanelClinico;
import com.preventiva.backend.entity.PanelMetrica;
import com.preventiva.backend.entity.RegistroClinicoGenerico;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.enums.TipoMetrica;
import com.preventiva.backend.repository.CampoClinicoRepository;
import com.preventiva.backend.repository.MetricaClinicaRepository;
import com.preventiva.backend.repository.PanelClinicoRepository;
import com.preventiva.backend.repository.PanelMetricaRepository;
import com.preventiva.backend.repository.RegistroClinicoGenericoRepository;
import com.preventiva.backend.service.interfaces.MetricaAnaliticaService;
import com.preventiva.backend.util.FiltroMetricaEvaluator;
import com.preventiva.backend.util.MetricaCalculoBasico;
import com.preventiva.backend.util.OperacionMetricaUtil;
import com.preventiva.backend.util.PeriodoTemporalUtil;
import com.preventiva.backend.util.PeriodoTemporalUtil.Periodo;
import com.preventiva.backend.util.RegistroClinicoGenericoValueReader;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetricaAnaliticaServiceImpl implements MetricaAnaliticaService {

    private static final String CAMPO_FECHA_DEFECTO = "fechaEvento";

    private final MetricaClinicaRepository metricaClinicaRepository;
    private final CampoClinicoRepository campoClinicoRepository;
    private final RegistroClinicoGenericoRepository registroClinicoGenericoRepository;
    private final PanelClinicoRepository panelClinicoRepository;
    private final PanelMetricaRepository panelMetricaRepository;

    @Override
    public SerieTemporalResponseDto serieTemporal(Long metricaId, SerieTemporalRequestDto request) {
        MetricaClinica metrica = obtenerMetricaOLanzar(metricaId);
        return calcularSerieTemporal(metrica, request);
    }

    @Override
    public ComparativaResponseDto comparativa(Long metricaId, ComparativaRequestDto request) {
        MetricaClinica metrica = obtenerMetricaOLanzar(metricaId);
        return calcularComparativa(metrica, request);
    }

    @Override
    public PanelSerieTemporalResponseDto serieTemporalPanel(Long panelId, SerieTemporalRequestDto request) {
        PanelClinico panel = panelClinicoRepository.findById(panelId)
                .orElseThrow(() -> new NoSuchElementException("No existe el panel con id: " + panelId));

        List<PanelMetricaSerieDto> resultados = panelMetricaRepository.findByPanelIdAndActivaTrue(panelId)
                .stream()
                .filter(pm -> Boolean.TRUE.equals(pm.getMetrica().getActiva()))
                .filter(pm -> admiteSerie(pm.getMetrica()))
                .sorted(Comparator.comparing(PanelMetrica::getOrden))
                .map(pm -> construirSeriePanel(pm, request))
                .toList();

        return PanelSerieTemporalResponseDto.builder()
                .panelId(panel.getId())
                .codigo(panel.getCodigo())
                .nombre(panel.getNombre())
                .datasetId(panel.getDataset().getId())
                .resultados(resultados)
                .build();
    }

    private PanelMetricaSerieDto construirSeriePanel(PanelMetrica panelMetrica, SerieTemporalRequestDto request) {
        SerieTemporalResponseDto serie = calcularSerieTemporal(panelMetrica.getMetrica(), request);

        String titulo = panelMetrica.getTituloPersonalizado() != null
                ? panelMetrica.getTituloPersonalizado()
                : panelMetrica.getMetrica().getNombre();

        return PanelMetricaSerieDto.builder()
                .panelMetricaId(panelMetrica.getId())
                .metricaId(panelMetrica.getMetrica().getId())
                .codigo(panelMetrica.getMetrica().getCodigo())
                .titulo(titulo)
                .serie(serie)
                .build();
    }

    private SerieTemporalResponseDto calcularSerieTemporal(MetricaClinica metrica, SerieTemporalRequestDto request) {
        Long datasetId = metrica.getDataset().getId();
        Map<String, CampoClinico> camposPorCodigo = obtenerCamposActivosPorCodigo(datasetId);
        validarTipoPermitido(metrica, camposPorCodigo);

        String codigoCampoFecha = request.getCampoFecha() != null ? request.getCampoFecha() : CAMPO_FECHA_DEFECTO;
        CampoClinico campoFecha = obtenerCampoOLanzar(codigoCampoFecha, camposPorCodigo);

        if (campoFecha.getTipoDato() != TipoDatoExcel.FECHA) {
            throw new IllegalArgumentException("El campo '" + codigoCampoFecha + "' debe ser de tipo FECHA.");
        }

        Function<String, CampoClinico> resolverCampo = camposPorCodigo::get;
        List<FiltroMetricaDto> filtrosGlobales = filtrosONull(request.getFiltrosGlobales());

        List<RegistroConFecha> registrosConFecha = registroClinicoGenericoRepository.findByDatasetId(datasetId)
                .stream()
                .filter(r -> FiltroMetricaEvaluator.cumpleTodos(r, filtrosGlobales, resolverCampo))
                .map(r -> new RegistroConFecha(r, leerFecha(r, campoFecha)))
                .filter(rf -> rf.fecha() != null)
                .toList();

        RangoEfectivo rango = resolverRango(registrosConFecha, request.getFechaDesde(), request.getFechaHasta());

        if (rango == null) {
            return respuestaSerieVacia(metrica, request);
        }

        List<Periodo> periodos = PeriodoTemporalUtil.generarSecuencia(
                rango.desde(), rango.hasta(), request.getGranularidad());

        if (periodos.isEmpty()) {
            return respuestaSerieVacia(metrica, request);
        }

        if (request.getCampoSegmentacion() == null || request.getCampoSegmentacion().isBlank()) {
            Map<String, List<RegistroClinicoGenerico>> registrosPorPeriodo = registrosConFecha.stream()
                    .collect(Collectors.groupingBy(
                            rf -> PeriodoTemporalUtil.calcularPeriodo(rf.fecha(), request.getGranularidad()).etiqueta(),
                            Collectors.mapping(RegistroConFecha::registro, Collectors.toList())));

            List<PuntoSerieDto> puntos = periodos.stream()
                    .map(periodo -> construirPunto(
                            periodo, registrosPorPeriodo.getOrDefault(periodo.etiqueta(), List.of()),
                            metrica, resolverCampo))
                    .toList();

            // El total se calcula de una vez sobre todos los registros, no
            // sumando ni promediando los puntos: para un porcentaje, la media
            // de las tasas mensuales pondera igual un mes de 8 casos y uno de
            // 22, y no es la tasa del periodo.
            PuntoSerieDto total = construirTotal(
                    rango, registrosConFecha.stream().map(RegistroConFecha::registro).toList(),
                    metrica, resolverCampo);

            return SerieTemporalResponseDto.builder()
                    .metricaId(metrica.getId())
                    .codigo(metrica.getCodigo())
                    .tipoMetrica(metrica.getTipoMetrica().name())
                    .granularidad(request.getGranularidad().name())
                    .puntos(puntos)
                    .total(total)
                    .build();
        }

        CampoClinico campoSegmentacion = obtenerCampoOLanzar(request.getCampoSegmentacion(), camposPorCodigo);

        Map<String, Map<String, List<RegistroClinicoGenerico>>> porSegmentoYPeriodo = registrosConFecha.stream()
                .collect(Collectors.groupingBy(
                        rf -> etiquetaSegmento(rf.registro(), campoSegmentacion),
                        Collectors.groupingBy(
                                rf -> PeriodoTemporalUtil.calcularPeriodo(rf.fecha(), request.getGranularidad()).etiqueta(),
                                Collectors.mapping(RegistroConFecha::registro, Collectors.toList()))));

        List<SerieSegmentadaDto> series = porSegmentoYPeriodo.keySet().stream()
                .sorted()
                .map(segmento -> {
                    Map<String, List<RegistroClinicoGenerico>> porPeriodo = porSegmentoYPeriodo.get(segmento);
                    List<PuntoSerieDto> puntos = periodos.stream()
                            .map(periodo -> construirPunto(
                                    periodo, porPeriodo.getOrDefault(periodo.etiqueta(), List.of()),
                                    metrica, resolverCampo))
                            .toList();
                    List<RegistroClinicoGenerico> todosDelSegmento = porPeriodo.values().stream()
                            .flatMap(List::stream)
                            .toList();
                    return SerieSegmentadaDto.builder()
                            .etiqueta(segmento)
                            .puntos(puntos)
                            .total(construirTotal(rango, todosDelSegmento, metrica, resolverCampo))
                            .build();
                })
                .toList();

        return SerieTemporalResponseDto.builder()
                .metricaId(metrica.getId())
                .codigo(metrica.getCodigo())
                .tipoMetrica(metrica.getTipoMetrica().name())
                .granularidad(request.getGranularidad().name())
                .segmentadoPor(request.getCampoSegmentacion())
                .series(series)
                .total(construirTotal(
                        rango, registrosConFecha.stream().map(RegistroConFecha::registro).toList(),
                        metrica, resolverCampo))
                .build();
    }

    private ComparativaResponseDto calcularComparativa(MetricaClinica metrica, ComparativaRequestDto request) {
        Long datasetId = metrica.getDataset().getId();
        Map<String, CampoClinico> camposPorCodigo = obtenerCamposActivosPorCodigo(datasetId);
        validarTipoPermitido(metrica, camposPorCodigo);
        CampoClinico campoAgrupacion = obtenerCampoOLanzar(request.getCampoAgrupacion(), camposPorCodigo);
        Function<String, CampoClinico> resolverCampo = camposPorCodigo::get;
        List<FiltroMetricaDto> filtrosGlobales = filtrosONull(request.getFiltrosGlobales());

        List<RegistroClinicoGenerico> registros = registroClinicoGenericoRepository.findByDatasetId(datasetId)
                .stream()
                .filter(r -> cumpleRangoFechaEvento(r, request.getFechaDesde(), request.getFechaHasta()))
                .filter(r -> FiltroMetricaEvaluator.cumpleTodos(r, filtrosGlobales, resolverCampo))
                .toList();

        Map<String, List<RegistroClinicoGenerico>> porGrupo = registros.stream()
                .collect(Collectors.groupingBy(r -> etiquetaSegmento(r, campoAgrupacion)));

        List<ItemComparativaDto> items = porGrupo.keySet().stream()
                .sorted()
                .map(etiqueta -> {
                    MetricaCalculoBasico.Resultado resultado = MetricaCalculoBasico.calcular(
                            metrica.getTipoMetrica(), metrica.getConfiguracion(),
                            porGrupo.get(etiqueta), resolverCampo, metrica.getDecimales());

                    return ItemComparativaDto.builder()
                            .etiqueta(etiqueta)
                            .valor(resultado.getValor())
                            .totalNumerador(resultado.getTotalNumerador())
                            .totalDenominador(resultado.getTotalDenominador())
                            .estado(resultado.getEstadoNombre())
                            .build();
                })
                .toList();

        return ComparativaResponseDto.builder()
                .metricaId(metrica.getId())
                .codigo(metrica.getCodigo())
                .tipoMetrica(metrica.getTipoMetrica().name())
                .agrupadoPor(request.getCampoAgrupacion())
                .items(items)
                .build();
    }

    /**
     * Fila TOTAL: el mismo cálculo que un punto, pero sobre todos los registros
     * del rango a la vez. Reutiliza {@link #construirPunto} para que la fila de
     * totales no pueda separarse nunca de las filas que resume.
     */
    private PuntoSerieDto construirTotal(
            RangoEfectivo rango, List<RegistroClinicoGenerico> registros,
            MetricaClinica metrica, Function<String, CampoClinico> resolverCampo) {
        Periodo periodoTotal = new Periodo("TOTAL", rango.desde(), rango.hasta());
        return construirPunto(periodoTotal, registros, metrica, resolverCampo);
    }

    private PuntoSerieDto construirPunto(
            Periodo periodo, List<RegistroClinicoGenerico> registros,
            MetricaClinica metrica, Function<String, CampoClinico> resolverCampo) {
        MetricaCalculoBasico.Resultado resultado = MetricaCalculoBasico.calcular(
                metrica.getTipoMetrica(), metrica.getConfiguracion(), registros, resolverCampo, metrica.getDecimales());

        return PuntoSerieDto.builder()
                .periodo(periodo.etiqueta())
                .fechaInicio(periodo.fechaInicio())
                .fechaFin(periodo.fechaFin())
                .valor(resultado.getValor())
                .totalNumerador(resultado.getTotalNumerador())
                .totalDenominador(resultado.getTotalDenominador())
                .estado(resultado.getEstadoNombre())
                .build();
    }

    /** Igual que `validarTipoPermitido`, pero sin lanzar: para filtrar el panel. */
    private boolean admiteSerie(MetricaClinica metrica) {
        Map<String, CampoClinico> campos = obtenerCamposActivosPorCodigo(metrica.getDataset().getId());
        try {
            validarTipoPermitido(metrica, campos);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private RangoEfectivo resolverRango(List<RegistroConFecha> registrosConFecha, LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null) {
            return new RangoEfectivo(desde, hasta);
        }

        List<LocalDate> candidatos = registrosConFecha.stream()
                .map(RegistroConFecha::fecha)
                .filter(f -> desde == null || !f.isBefore(desde))
                .filter(f -> hasta == null || !f.isAfter(hasta))
                .toList();

        if (candidatos.isEmpty()) {
            return null;
        }

        LocalDate minFecha = candidatos.stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate maxFecha = candidatos.stream().max(LocalDate::compareTo).orElseThrow();

        return new RangoEfectivo(desde != null ? desde : minFecha, hasta != null ? hasta : maxFecha);
    }

    private SerieTemporalResponseDto respuestaSerieVacia(MetricaClinica metrica, SerieTemporalRequestDto request) {
        SerieTemporalResponseDto.SerieTemporalResponseDtoBuilder builder = SerieTemporalResponseDto.builder()
                .metricaId(metrica.getId())
                .codigo(metrica.getCodigo())
                .tipoMetrica(metrica.getTipoMetrica().name())
                .granularidad(request.getGranularidad().name());

        if (request.getCampoSegmentacion() != null && !request.getCampoSegmentacion().isBlank()) {
            builder.segmentadoPor(request.getCampoSegmentacion()).series(List.of());
        } else {
            builder.puntos(List.of());
        }

        return builder.build();
    }

    /**
     * Solo entran en una serie o una comparativa las métricas cuyo resultado es
     * un número que se pueda poner en un eje.
     *
     * <p>Quedan fuera las que devuelven un reparto (DISTRIBUCION), una etiqueta
     * (CATEGORIA_PRINCIPAL) o una fecha (MINIMO/MAXIMO sobre campo FECHA):
     * dibujarlas exigiría un resultado de dos dimensiones que ni el DTO ni los
     * renderers admiten hoy. Se rechaza con un mensaje explícito en vez de
     * producir una serie de nulos.
     */
    private void validarTipoPermitido(MetricaClinica metrica, Map<String, CampoClinico> camposPorCodigo) {
        TipoMetrica tipoMetrica = metrica.getTipoMetrica();
        ConfiguracionMetricaDto config = metrica.getConfiguracion();

        String codigoCampoValor = config != null ? config.getCampoValor() : null;
        CampoClinico campoValor = codigoCampoValor != null ? camposPorCodigo.get(codigoCampoValor) : null;
        TipoDatoExcel tipoDatoCampoValor = campoValor != null ? campoValor.getTipoDato() : null;

        if (!OperacionMetricaUtil.produceValorNumerico(tipoMetrica, tipoDatoCampoValor)) {
            throw new IllegalArgumentException(
                    tipoMetrica + " no produce un valor numérico, así que no admite serie temporal ni comparativa;"
                            + " use la ejecución normal de la métrica.");
        }
    }

    private boolean cumpleRangoFechaEvento(RegistroClinicoGenerico registro, LocalDate desde, LocalDate hasta) {
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

    private LocalDate leerFecha(RegistroClinicoGenerico registro, CampoClinico campoFecha) {
        Object valorCrudo = RegistroClinicoGenericoValueReader.leerValorCrudo(registro, campoFecha);
        Object valor = RegistroClinicoGenericoValueReader.coercionar(valorCrudo, TipoDatoExcel.FECHA);

        return valor instanceof LocalDate fecha ? fecha : null;
    }

    private String etiquetaSegmento(RegistroClinicoGenerico registro, CampoClinico campo) {
        Object valorCrudo = RegistroClinicoGenericoValueReader.leerValorCrudo(registro, campo);
        Object valor = RegistroClinicoGenericoValueReader.coercionar(valorCrudo, campo.getTipoDato());

        return valor != null ? String.valueOf(valor) : "Sin dato";
    }

    private List<FiltroMetricaDto> filtrosONull(List<FiltroMetricaDto> filtros) {
        return filtros != null ? filtros : List.of();
    }

    private Map<String, CampoClinico> obtenerCamposActivosPorCodigo(Long datasetId) {
        return campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId)
                .stream()
                .collect(Collectors.toMap(CampoClinico::getCodigo, c -> c, (a, b) -> a));
    }

    private CampoClinico obtenerCampoOLanzar(String codigo, Map<String, CampoClinico> camposPorCodigo) {
        CampoClinico campo = camposPorCodigo.get(codigo);

        if (campo == null) {
            throw new IllegalArgumentException("El campo '" + codigo + "' no existe o no está activo en este dataset.");
        }

        return campo;
    }

    private MetricaClinica obtenerMetricaOLanzar(Long id) {
        return metricaClinicaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe la métrica con id: " + id));
    }

    private record RegistroConFecha(RegistroClinicoGenerico registro, LocalDate fecha) {
    }

    private record RangoEfectivo(LocalDate desde, LocalDate hasta) {
    }
}
