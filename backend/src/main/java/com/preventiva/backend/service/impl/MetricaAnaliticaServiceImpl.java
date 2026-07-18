package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.ComparativaRequestDto;
import com.preventiva.backend.dto.ComparativaResponseDto;
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
import com.preventiva.backend.util.MetricaCalculoBasico;
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
                .filter(pm -> pm.getMetrica().getTipoMetrica() != TipoMetrica.DISTRIBUCION)
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
        validarTipoPermitido(metrica.getTipoMetrica());

        Long datasetId = metrica.getDataset().getId();
        Map<String, CampoClinico> camposPorCodigo = obtenerCamposActivosPorCodigo(datasetId);

        String codigoCampoFecha = request.getCampoFecha() != null ? request.getCampoFecha() : CAMPO_FECHA_DEFECTO;
        CampoClinico campoFecha = obtenerCampoOLanzar(codigoCampoFecha, camposPorCodigo);

        if (campoFecha.getTipoDato() != TipoDatoExcel.FECHA) {
            throw new IllegalArgumentException("El campo '" + codigoCampoFecha + "' debe ser de tipo FECHA.");
        }

        List<RegistroConFecha> registrosConFecha = registroClinicoGenericoRepository.findByDatasetId(datasetId)
                .stream()
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

        Function<String, CampoClinico> resolverCampo = camposPorCodigo::get;

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

            return SerieTemporalResponseDto.builder()
                    .metricaId(metrica.getId())
                    .codigo(metrica.getCodigo())
                    .tipoMetrica(metrica.getTipoMetrica().name())
                    .granularidad(request.getGranularidad().name())
                    .puntos(puntos)
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
                    return SerieSegmentadaDto.builder().etiqueta(segmento).puntos(puntos).build();
                })
                .toList();

        return SerieTemporalResponseDto.builder()
                .metricaId(metrica.getId())
                .codigo(metrica.getCodigo())
                .tipoMetrica(metrica.getTipoMetrica().name())
                .granularidad(request.getGranularidad().name())
                .segmentadoPor(request.getCampoSegmentacion())
                .series(series)
                .build();
    }

    private ComparativaResponseDto calcularComparativa(MetricaClinica metrica, ComparativaRequestDto request) {
        validarTipoPermitido(metrica.getTipoMetrica());

        Long datasetId = metrica.getDataset().getId();
        Map<String, CampoClinico> camposPorCodigo = obtenerCamposActivosPorCodigo(datasetId);
        CampoClinico campoAgrupacion = obtenerCampoOLanzar(request.getCampoAgrupacion(), camposPorCodigo);
        Function<String, CampoClinico> resolverCampo = camposPorCodigo::get;

        List<RegistroClinicoGenerico> registros = registroClinicoGenericoRepository.findByDatasetId(datasetId)
                .stream()
                .filter(r -> cumpleRangoFechaEvento(r, request.getFechaDesde(), request.getFechaHasta()))
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
                .build();
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

    private void validarTipoPermitido(TipoMetrica tipoMetrica) {
        if (tipoMetrica == TipoMetrica.DISTRIBUCION) {
            throw new IllegalArgumentException(
                    "DISTRIBUCION no admite serie temporal ni comparativa; use la ejecución normal de la métrica.");
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
