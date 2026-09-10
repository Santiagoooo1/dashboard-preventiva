package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.AdvertenciaComparacionDto;
import com.preventiva.backend.dto.CeldaComparacionDto;
import com.preventiva.backend.dto.ComparacionInteranualRequestDto;
import com.preventiva.backend.dto.ComparacionInteranualResponseDto;
import com.preventiva.backend.dto.ConfiguracionMetricaDto;
import com.preventiva.backend.dto.FiltroGrupoDto;
import com.preventiva.backend.dto.FiltroMetricaDto;
import com.preventiva.backend.dto.ItemDistribucionDto;
import com.preventiva.backend.dto.SerieAnualDto;
import com.preventiva.backend.dto.ValorCategoriaDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.entity.DatasetClinico;
import com.preventiva.backend.entity.RegistroClinicoGenerico;
import com.preventiva.backend.enums.EstadoResultadoMetrica;
import com.preventiva.backend.enums.Granularidad;
import com.preventiva.backend.enums.OperadorFiltro;
import com.preventiva.backend.enums.TipoComparacionInteranual;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.enums.TipoMetrica;
import com.preventiva.backend.enums.TratamientoNulos;
import com.preventiva.backend.repository.CampoClinicoRepository;
import com.preventiva.backend.repository.DatasetClinicoRepository;
import com.preventiva.backend.repository.RegistroClinicoGenericoRepository;
import com.preventiva.backend.service.interfaces.ComparacionInteranualService;
import com.preventiva.backend.util.MetricaCalculoBasico;
import com.preventiva.backend.util.RegistroClinicoGenericoValueReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Compara un mismo concepto clínico entre varios años (Fase 6.9O).
 *
 * <p>La unión es analítica: no se copia ni un registro a ninguna tabla nueva.
 * Cada dataset conserva su independencia y su trazabilidad, y la comparación se
 * calcula al vuelo. Una tabla física con los tres años dentro sería un cuarto
 * sitio donde los datos pueden quedar desfasados respecto al original.
 *
 * <p>Los años no se deducen del nombre del fichero —«ILQ_2026» puede contener
 * dos años, o ninguno de 2026— sino de las fechas que tienen los registros. Por
 * eso la unidad de comparación es el par dataset-año: un dataset con tres años
 * produce tres series, y tres datasets de un año producen otras tres, sin que el
 * resto del cálculo tenga que distinguirlos.
 *
 * <p>Todo el cálculo pasa por {@link MetricaCalculoBasico}, el mismo motor que
 * usan las métricas y las series temporales. No hay una segunda fórmula de la
 * tasa que pueda separarse de la primera.
 */
@Service
@RequiredArgsConstructor
public class ComparacionInteranualServiceImpl implements ComparacionInteranualService {

    private static final String CAMPO_FECHA_EVENTO = "fechaEvento";
    private static final int DECIMALES = 2;

    private final DatasetClinicoRepository datasetClinicoRepository;
    private final CampoClinicoRepository campoClinicoRepository;
    private final RegistroClinicoGenericoRepository registroClinicoGenericoRepository;

    @Override
    @Transactional(readOnly = true)
    public ComparacionInteranualResponseDto comparar(ComparacionInteranualRequestDto request) {
        List<Long> datasetIds = request.getDatasetIds() == null ? List.of() : request.getDatasetIds();
        if (datasetIds.isEmpty()) {
            throw new IllegalArgumentException("Debes indicar al menos un dataset para comparar.");
        }
        if (request.getCodigoCanonico() == null || request.getCodigoCanonico().isBlank()) {
            throw new IllegalArgumentException("Debes indicar qué concepto clínico quieres comparar.");
        }

        TipoComparacionInteranual tipo = request.getTipoComparacion() != null
                ? request.getTipoComparacion()
                : TipoComparacionInteranual.TASA;
        Granularidad granularidad = request.getGranularidad() != null
                ? request.getGranularidad()
                : Granularidad.MES;

        // --- Compatibilidad: se comprueba ANTES de calcular nada ---
        List<DatasetClinico> datasets = new ArrayList<>();
        for (Long id : datasetIds) {
            datasets.add(datasetClinicoRepository.findById(id).orElseThrow(
                    () -> new NoSuchElementException("No existe el dataset clínico con id: " + id)));
        }

        Map<Long, Map<String, CampoClinico>> camposPorDataset = new LinkedHashMap<>();
        for (DatasetClinico d : datasets) {
            camposPorDataset.put(d.getId(), campoClinicoRepository.findByDatasetIdAndActivoTrue(d.getId()).stream()
                    .collect(Collectors.toMap(
                            c -> c.getCodigo().toLowerCase(), Function.identity(), (a, b) -> a)));
        }

        Optional<String> incompatibilidad =
                comprobarCompatibilidad(datasets, camposPorDataset, request, tipo);
        if (incompatibilidad.isPresent()) {
            return ComparacionInteranualResponseDto.builder()
                    .codigoCanonico(request.getCodigoCanonico())
                    .tipoComparacion(tipo.name())
                    .granularidad(granularidad.name())
                    .comparable(false)
                    .motivoNoComparable(incompatibilidad.get())
                    .series(List.of())
                    .periodos(List.of())
                    .categorias(List.of())
                    .advertencias(List.of())
                    .build();
        }

        // --- Datos: UNA consulta por dataset, nunca una por año o por mes ---
        List<String> periodos = periodosDe(granularidad);
        List<SerieAnualDto> series = new ArrayList<>();
        Set<String> categorias = new LinkedHashSet<>();
        List<AdvertenciaComparacionDto> advertencias = new ArrayList<>();
        Map<Long, Set<Integer>> aniosPorDataset = new LinkedHashMap<>();

        for (DatasetClinico dataset : datasets) {
            Map<String, CampoClinico> campos = camposPorDataset.get(dataset.getId());
            CampoClinico campoFecha = campos.get(CAMPO_FECHA_EVENTO.toLowerCase());
            CampoClinico campoAnalizado = campos.get(request.getCodigoCanonico().toLowerCase());
            Function<String, CampoClinico> resolver = codigo ->
                    codigo == null ? null : campos.get(codigo.toLowerCase());

            List<RegistroClinicoGenerico> registros =
                    registroClinicoGenericoRepository.findByDatasetId(dataset.getId());

            // Se agrupa en memoria por (año, periodo): una pasada, sin volver a
            // la base por cada celda.
            Map<Integer, Map<String, List<RegistroClinicoGenerico>>> porAnioYPeriodo = new LinkedHashMap<>();
            for (RegistroClinicoGenerico registro : registros) {
                LocalDate fecha = leerFecha(registro, campoFecha);
                if (fecha == null) {
                    // Sin fecha no pertenece a ningún año: no se inventa uno.
                    continue;
                }
                porAnioYPeriodo
                        .computeIfAbsent(fecha.getYear(), a -> new LinkedHashMap<>())
                        .computeIfAbsent(etiquetaPeriodo(fecha, granularidad), p -> new ArrayList<>())
                        .add(registro);
            }

            Set<Integer> aniosDelDataset = new TreeSet<>(porAnioYPeriodo.keySet());
            aniosPorDataset.put(dataset.getId(), aniosDelDataset);
            if (aniosDelDataset.size() > 1) {
                advertencias.add(AdvertenciaComparacionDto.builder()
                        .codigo("DATASET_MULTIANUAL")
                        .mensaje(String.format(
                                "«%s» contiene registros de %d años (%s): se compara cada uno por separado.",
                                dataset.getNombre(), aniosDelDataset.size(),
                                aniosDelDataset.stream().map(String::valueOf).collect(Collectors.joining(", "))))
                        .build());
            }

            List<Integer> aniosPedidos = request.getAnios() == null || request.getAnios().isEmpty()
                    ? List.copyOf(aniosDelDataset)
                    : aniosDelDataset.stream().filter(request.getAnios()::contains).toList();

            for (Integer anio : aniosPedidos) {
                Map<String, List<RegistroClinicoGenerico>> porPeriodo = porAnioYPeriodo.get(anio);
                List<RegistroClinicoGenerico> todosDelAnio = porPeriodo.values().stream()
                        .flatMap(List::stream).toList();

                ConfiguracionMetricaDto config = configuracionDe(tipo, request, campoAnalizado);

                List<CeldaComparacionDto> celdas = periodos.stream()
                        .map(p -> celda(p, porPeriodo.getOrDefault(p, List.of()), tipo, config, resolver, categorias))
                        .toList();
                CeldaComparacionDto total = celda("TOTAL", todosDelAnio, tipo, config, resolver, categorias);

                series.add(SerieAnualDto.builder()
                        .datasetId(dataset.getId())
                        .datasetCodigo(dataset.getCodigo())
                        .datasetNombre(dataset.getNombre())
                        .anio(anio)
                        .etiqueta(etiquetaSerie(dataset, anio, datasets.size()))
                        .celdas(celdas)
                        .total(total)
                        .unidadVariacion(tipo == TipoComparacionInteranual.TASA ? "pp" : "absoluta")
                        .build());
            }
        }

        series.sort(Comparator.comparing(SerieAnualDto::getAnio)
                .thenComparing(SerieAnualDto::getDatasetId));

        // La variación se calcula al final, cuando ya sabemos el orden.
        calcularVariaciones(series, periodos, tipo);
        advertencias.addAll(detectarSolapamientos(datasets, aniosPorDataset));

        // La unión de categorías: una que solo exista en un año se conserva,
        // porque su ausencia en los demás es justo lo que hay que ver.
        List<String> categoriasOrdenadas = categorias.stream().sorted().toList();
        if (tipo == TipoComparacionInteranual.DISTRIBUCION) {
            completarCategorias(series, categoriasOrdenadas);
        }

        return ComparacionInteranualResponseDto.builder()
                .codigoCanonico(request.getCodigoCanonico())
                .etiquetaConcepto(etiquetaConcepto(camposPorDataset, request.getCodigoCanonico()))
                .tipoComparacion(tipo.name())
                .granularidad(granularidad.name())
                .periodos(periodos)
                .series(series)
                .categorias(tipo == TipoComparacionInteranual.DISTRIBUCION ? categoriasOrdenadas : List.of())
                .comparable(true)
                .advertencias(advertencias)
                .build();
    }

    // ------------------------------------------------------------------
    // Compatibilidad
    // ------------------------------------------------------------------

    /**
     * Comprueba que la comparación tiene sentido antes de producir números.
     *
     * <p>Un resultado a medias es peor que ninguno: una columna vacía en 2024 se
     * lee como «ese año no hubo casos», cuando lo que pasa es que ese año no se
     * recogía el dato.
     */
    private Optional<String> comprobarCompatibilidad(
            List<DatasetClinico> datasets,
            Map<Long, Map<String, CampoClinico>> camposPorDataset,
            ComparacionInteranualRequestDto request,
            TipoComparacionInteranual tipo) {

        String codigo = request.getCodigoCanonico().toLowerCase();
        TipoDatoExcel tipoComun = null;

        for (DatasetClinico d : datasets) {
            Map<String, CampoClinico> campos = camposPorDataset.get(d.getId());

            CampoClinico campoFecha = campos.get(CAMPO_FECHA_EVENTO.toLowerCase());
            if (campoFecha == null || campoFecha.getTipoDato() != TipoDatoExcel.FECHA) {
                return Optional.of(String.format(
                        "No se puede comparar: «%s» no tiene una fecha de evento con la que situar los registros "
                                + "en el tiempo.", d.getNombre()));
            }

            CampoClinico campo = campos.get(codigo);
            if (campo == null) {
                return Optional.of(String.format(
                        "No se puede comparar este indicador porque el dataset «%s» no contiene %s.",
                        d.getNombre(), request.getCodigoCanonico()));
            }

            if (tipoComun == null) {
                tipoComun = campo.getTipoDato();
            } else if (tipoComun != campo.getTipoDato()) {
                return Optional.of(String.format(
                        "El campo %s tiene tipos incompatibles entre los datasets seleccionados: "
                                + "en unos es %s y en «%s» es %s.",
                        request.getCodigoCanonico(), tipoComun, d.getNombre(), campo.getTipoDato()));
            }

            Optional<String> filtroInvalido = comprobarFiltros(request.getFiltros(), campos, d);
            if (filtroInvalido.isPresent()) {
                return filtroInvalido;
            }
        }

        return comprobarTipoAdmiteComparacion(tipo, tipoComun, request.getCodigoCanonico());
    }

    /** Un filtro que no exista en todos los datasets daría una comparación engañosa. */
    private Optional<String> comprobarFiltros(
            List<FiltroMetricaDto> filtros, Map<String, CampoClinico> campos, DatasetClinico dataset) {
        if (filtros == null) {
            return Optional.empty();
        }
        for (FiltroMetricaDto filtro : filtros) {
            if (filtro.getCampo() == null) {
                continue;
            }
            if (!campos.containsKey(filtro.getCampo().toLowerCase())) {
                return Optional.of(String.format(
                        "No se puede aplicar el filtro por %s: el dataset «%s» no tiene ese campo, "
                                + "así que la comparación no sería equivalente.",
                        filtro.getCampo(), dataset.getNombre()));
            }
        }
        return Optional.empty();
    }

    private Optional<String> comprobarTipoAdmiteComparacion(
            TipoComparacionInteranual tipo, TipoDatoExcel tipoDato, String codigo) {
        boolean admite = switch (tipo) {
            case TASA, RECUENTO -> tipoDato == TipoDatoExcel.BOOLEANO;
            case DISTRIBUCION -> tipoDato == TipoDatoExcel.TEXTO || tipoDato == TipoDatoExcel.BOOLEANO;
            case RESUMEN_NUMERICO -> tipoDato == TipoDatoExcel.ENTERO || tipoDato == TipoDatoExcel.DECIMAL;
        };
        if (admite) {
            return Optional.empty();
        }
        return Optional.of(String.format(
                "El campo %s es de tipo %s y no admite una comparación de tipo %s.",
                codigo, tipoDato, tipo));
    }

    // ------------------------------------------------------------------
    // Cálculo
    // ------------------------------------------------------------------

    /** Traduce el tipo de comparación a la configuración que sabe leer el motor. */
    private ConfiguracionMetricaDto configuracionDe(
            TipoComparacionInteranual tipo, ComparacionInteranualRequestDto request, CampoClinico campo) {
        ConfiguracionMetricaDto config = new ConfiguracionMetricaDto();
        config.setFiltros(request.getFiltros() == null ? List.of() : request.getFiltros());

        switch (tipo) {
            case TASA -> {
                // El denominador son los registros con el dato documentado: un
                // NULL no es un «no», es un «no se sabe».
                config.setNumerador(grupo(filtro(campo.getCodigo(), OperadorFiltro.EQ, true)));
                config.setDenominador(grupo(filtro(campo.getCodigo(), OperadorFiltro.NOT_NULL, null)));
            }
            case RECUENTO -> {
                // Solo los positivos: se acota la población base y se cuenta.
                // Un NULL no entra —no es un caso— y un false tampoco.
                List<FiltroMetricaDto> conPositivos = new ArrayList<>(config.getFiltros());
                conPositivos.add(filtro(campo.getCodigo(), OperadorFiltro.EQ, true));
                config.setFiltros(conPositivos);
            }
            case DISTRIBUCION -> {
                config.setCampoAgrupacion(campo.getCodigo());
                // Un registro sin valor no es un caso de ninguna categoría: si
                // entrara, «Sin dato» se colaría en la unión de categorías y
                // competiría con las clínicas en la matriz.
                config.setTratamientoNulos(TratamientoNulos.EXCLUIR);
            }
            case RESUMEN_NUMERICO -> config.setCampoValor(campo.getCodigo());
        }
        return config;
    }

    private CeldaComparacionDto celda(
            String periodo, List<RegistroClinicoGenerico> registros, TipoComparacionInteranual tipo,
            ConfiguracionMetricaDto config, Function<String, CampoClinico> resolver, Set<String> categorias) {

        return switch (tipo) {
            case TASA -> {
                MetricaCalculoBasico.Resultado r = MetricaCalculoBasico.calcular(
                        TipoMetrica.PORCENTAJE, config, registros, resolver, DECIMALES);
                yield CeldaComparacionDto.builder()
                        .periodo(periodo)
                        .valor(r.getValor())
                        .numerador(r.getTotalNumerador())
                        .denominador(r.getTotalDenominador())
                        .estado(r.getEstadoNombre())
                        .build();
            }
            case RECUENTO -> {
                MetricaCalculoBasico.Resultado r = MetricaCalculoBasico.calcular(
                        TipoMetrica.CONTEO, config, registros, resolver, DECIMALES);
                // Sin numerador ni denominador: un recuento no es una fracción y
                // enseñarlo como tal invitaría a leerlo como porcentaje.
                yield CeldaComparacionDto.builder()
                        .periodo(periodo)
                        .valor(r.getValor())
                        .estado(r.getEstadoNombre())
                        .build();
            }
            case DISTRIBUCION -> {
                MetricaCalculoBasico.Resultado r = MetricaCalculoBasico.calcular(
                        TipoMetrica.DISTRIBUCION, config, registros, resolver, DECIMALES);
                List<ValorCategoriaDto> valores = new ArrayList<>();
                long total = 0;
                for (ItemDistribucionDto item : r.getItems() == null ? List.<ItemDistribucionDto>of() : r.getItems()) {
                    categorias.add(item.getEtiqueta());
                    valores.add(ValorCategoriaDto.builder()
                            .categoria(item.getEtiqueta())
                            .valor(item.getValor() == null ? 0.0 : item.getValor().doubleValue())
                            .build());
                    total += item.getValor() == null ? 0 : item.getValor().longValue();
                }
                yield CeldaComparacionDto.builder()
                        .periodo(periodo)
                        .valor((double) total)
                        .denominador(total)
                        .categorias(valores)
                        .estado(EstadoResultadoMetrica.OK.name())
                        .build();
            }
            case RESUMEN_NUMERICO -> {
                MetricaCalculoBasico.Resultado media = MetricaCalculoBasico.calcular(
                        TipoMetrica.PROMEDIO, config, registros, resolver, DECIMALES);
                MetricaCalculoBasico.Resultado min = MetricaCalculoBasico.calcular(
                        TipoMetrica.MINIMO, config, registros, resolver, DECIMALES);
                MetricaCalculoBasico.Resultado max = MetricaCalculoBasico.calcular(
                        TipoMetrica.MAXIMO, config, registros, resolver, DECIMALES);
                yield CeldaComparacionDto.builder()
                        .periodo(periodo)
                        .valor(media.getValor())
                        .media(media.getValor())
                        .minimo(min.getValor())
                        .maximo(max.getValor())
                        .numerador(media.getTotalNumerador())
                        .denominador(media.getTotalDenominador())
                        .estado(media.getEstadoNombre())
                        .build();
            }
        };
    }

    /**
     * Variación de cada serie frente a la anterior.
     *
     * <p>En porcentajes, la diferencia va en puntos porcentuales. De 3,1 % a
     * 1,8 % son −1,3 pp; decir «−41,9 %» contesta otra pregunta y se confunde
     * con la tasa. La relativa se puede ofrecer después, etiquetada como tal.
     */
    private void calcularVariaciones(
            List<SerieAnualDto> series, List<String> periodos, TipoComparacionInteranual tipo) {
        for (int i = 1; i < series.size(); i++) {
            SerieAnualDto actual = series.get(i);
            SerieAnualDto anterior = series.get(i - 1);

            List<CeldaComparacionDto> variacion = new ArrayList<>();
            for (int p = 0; p < periodos.size(); p++) {
                variacion.add(diferencia(periodos.get(p), actual.getCeldas().get(p), anterior.getCeldas().get(p)));
            }
            actual.setVariacion(variacion);
            actual.setVariacionTotal(diferencia("TOTAL", actual.getTotal(), anterior.getTotal()));
        }
    }

    private CeldaComparacionDto diferencia(String periodo, CeldaComparacionDto actual, CeldaComparacionDto anterior) {
        Double a = actual == null ? null : actual.getValor();
        Double b = anterior == null ? null : anterior.getValor();
        if (a == null || b == null) {
            // Sin uno de los dos no hay diferencia que dar: inventar un 0 diría
            // «no ha cambiado», que es distinto de «no se puede saber».
            return CeldaComparacionDto.builder()
                    .periodo(periodo)
                    .valor(null)
                    .estado(EstadoResultadoMetrica.SIN_BASE_EVALUABLE.name())
                    .build();
        }
        return CeldaComparacionDto.builder()
                .periodo(periodo)
                .valor(redondear(a - b))
                .estado(EstadoResultadoMetrica.OK.name())
                .build();
    }

    /** Toda serie enseña todas las categorías; las que no tuvo, a cero. */
    private void completarCategorias(List<SerieAnualDto> series, List<String> categorias) {
        for (SerieAnualDto serie : series) {
            List<CeldaComparacionDto> celdas = new ArrayList<>(serie.getCeldas());
            celdas.add(serie.getTotal());
            for (CeldaComparacionDto celda : celdas) {
                Map<String, Double> presentes = celda.getCategorias() == null
                        ? Map.of()
                        : celda.getCategorias().stream().collect(Collectors.toMap(
                                ValorCategoriaDto::getCategoria, ValorCategoriaDto::getValor, (x, y) -> x));
                celda.setCategorias(categorias.stream()
                        .map(c -> ValorCategoriaDto.builder()
                                .categoria(c)
                                .valor(presentes.getOrDefault(c, 0.0))
                                .build())
                        .toList());
            }
        }
    }

    // ------------------------------------------------------------------
    // Solapamiento
    // ------------------------------------------------------------------

    /**
     * Avisa si dos datasets cubren el mismo año.
     *
     * <p>No se deduplica nada: la aplicación no puede saber si un paciente de
     * «todo 2025» y otro de «enero-junio 2025» son la misma intervención. Lo
     * único honesto es decirlo y que lo revise quien conoce los datos.
     */
    private List<AdvertenciaComparacionDto> detectarSolapamientos(
            List<DatasetClinico> datasets, Map<Long, Set<Integer>> aniosPorDataset) {
        List<AdvertenciaComparacionDto> avisos = new ArrayList<>();
        for (int i = 0; i < datasets.size(); i++) {
            for (int j = i + 1; j < datasets.size(); j++) {
                DatasetClinico a = datasets.get(i);
                DatasetClinico b = datasets.get(j);
                Set<Integer> comunes = new TreeSet<>(aniosPorDataset.getOrDefault(a.getId(), Set.of()));
                comunes.retainAll(aniosPorDataset.getOrDefault(b.getId(), Set.of()));
                if (!comunes.isEmpty()) {
                    avisos.add(AdvertenciaComparacionDto.builder()
                            .codigo("PERIODOS_SOLAPADOS")
                            .mensaje(String.format(
                                    "«%s» y «%s» contienen registros del mismo periodo (%s). Revisa que no "
                                            + "representen los mismos casos: no se han eliminado duplicados.",
                                    a.getNombre(), b.getNombre(),
                                    comunes.stream().map(String::valueOf).collect(Collectors.joining(", "))))
                            .build());
                }
            }
        }
        return avisos;
    }

    // ------------------------------------------------------------------
    // Ayudantes
    // ------------------------------------------------------------------

    /** Periodos del eje, sin año: son la parte comparable entre series. */
    private List<String> periodosDe(Granularidad granularidad) {
        return switch (granularidad) {
            case MES -> List.of("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12");
            case TRIMESTRE -> List.of("T1", "T2", "T3", "T4");
            case ANIO -> List.of("Año");
        };
    }

    private String etiquetaPeriodo(LocalDate fecha, Granularidad granularidad) {
        return switch (granularidad) {
            case MES -> String.format("%02d", fecha.getMonthValue());
            case TRIMESTRE -> "T" + ((fecha.getMonthValue() - 1) / 3 + 1);
            case ANIO -> "Año";
        };
    }

    /**
     * Etiqueta de la columna. Con un solo dataset basta el año; con varios se
     * antepone el nombre, porque dos datasets pueden aportar el mismo año.
     */
    private String etiquetaSerie(DatasetClinico dataset, Integer anio, int totalDatasets) {
        return totalDatasets == 1 ? String.valueOf(anio) : dataset.getNombre() + " · " + anio;
    }

    private String etiquetaConcepto(
            Map<Long, Map<String, CampoClinico>> camposPorDataset, String codigoCanonico) {
        return camposPorDataset.values().stream()
                .map(campos -> campos.get(codigoCanonico.toLowerCase()))
                .filter(c -> c != null && c.getEtiqueta() != null && !c.getEtiqueta().isBlank())
                .map(CampoClinico::getEtiqueta)
                .findFirst()
                .orElse(codigoCanonico);
    }

    /** Misma lectura que usan las series temporales: un solo criterio de fecha. */
    private LocalDate leerFecha(RegistroClinicoGenerico registro, CampoClinico campo) {
        Object crudo = RegistroClinicoGenericoValueReader.leerValorCrudo(registro, campo);
        Object valor = RegistroClinicoGenericoValueReader.coercionar(crudo, TipoDatoExcel.FECHA);
        return valor instanceof LocalDate fecha ? fecha : null;
    }

    private Double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    private FiltroMetricaDto filtro(String campo, OperadorFiltro operador, Object valor) {
        FiltroMetricaDto f = new FiltroMetricaDto();
        f.setCampo(campo);
        f.setOperador(operador);
        f.setValor(valor);
        return f;
    }

    private FiltroGrupoDto grupo(FiltroMetricaDto... filtros) {
        FiltroGrupoDto g = new FiltroGrupoDto();
        g.setFiltros(List.of(filtros));
        return g;
    }
}
