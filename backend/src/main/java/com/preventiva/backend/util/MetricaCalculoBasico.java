package com.preventiva.backend.util;

import com.preventiva.backend.dto.ConfiguracionMetricaDto;
import com.preventiva.backend.dto.FiltroMetricaDto;
import com.preventiva.backend.dto.ItemDistribucionDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.entity.RegistroClinicoGenerico;
import com.preventiva.backend.enums.EstadoResultadoMetrica;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.enums.TipoMetrica;
import com.preventiva.backend.enums.TratamientoNulos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * El ejecutor de métricas. Único: la ejecución puntual, la serie temporal y la
 * comparativa entran todas por {@link #calcular}, de modo que una métrica vale
 * exactamente lo mismo se mire donde se mire.
 *
 * <p>Antes de la Fase 6.9I.2 esta clase resolvía las series y
 * {@code MetricaClinicaServiceImpl} repetía los mismos cuatro cálculos por su
 * cuenta, con diferencias reales entre ambas copias (una media sin valores
 * daba {@code null} aquí y {@code 0.0} allí). Ahora hay una implementación y el
 * servicio delega.
 */
public class MetricaCalculoBasico {

    /** Cuando una distribución recorta a Top N, el resto se agrupa bajo esta etiqueta. */
    public static final String ETIQUETA_OTROS = "Otros";

    /** Cómo se nombra la ausencia de dato. Nunca se confunde con un «No». */
    public static final String ETIQUETA_SIN_DATO = "Sin dato";

    private MetricaCalculoBasico() {
    }

    public static Resultado calcular(
            TipoMetrica tipoMetrica,
            ConfiguracionMetricaDto configuracion,
            List<RegistroClinicoGenerico> registros,
            Function<String, CampoClinico> resolverCampo,
            Integer decimales) {

        // Los filtros base acotan la población ANTES de cualquier cálculo, y en
        // PORCENTAJE se aplican también al numerador y al denominador: eso es
        // exactamente lo que hace condicional a un porcentaje.
        List<FiltroMetricaDto> filtrosBase = filtrosONull(configuracion.getFiltros());
        List<RegistroClinicoGenerico> base = registros.stream()
                .filter(r -> FiltroMetricaEvaluator.cumpleTodos(r, filtrosBase, resolverCampo))
                .toList();

        return switch (tipoMetrica) {
            case CONTEO -> Resultado.deValor(redondear((double) base.size(), decimales));
            case PORCENTAJE -> calcularPorcentaje(configuracion, base, resolverCampo, decimales);
            case PROMEDIO -> calcularPromedio(configuracion, base, resolverCampo, decimales);
            case SUMA -> calcularSuma(configuracion, base, resolverCampo, decimales);
            case MEDIANA -> calcularMediana(configuracion, base, resolverCampo, decimales);
            case MINIMO -> calcularExtremo(configuracion, base, resolverCampo, decimales, true);
            case MAXIMO -> calcularExtremo(configuracion, base, resolverCampo, decimales, false);
            case CONTEO_DISTINTO -> calcularConteoDistinto(configuracion, base, resolverCampo, decimales);
            case COMPLETITUD -> calcularCompletitud(configuracion, base, resolverCampo, decimales);
            case DISTRIBUCION -> calcularDistribucion(configuracion, base, resolverCampo);
            case CATEGORIA_PRINCIPAL -> calcularCategoriaPrincipal(configuracion, base, resolverCampo, decimales);
        };
    }

    // ------------------------------------------------------------------
    // Porcentaje (y porcentaje condicional)
    // ------------------------------------------------------------------

    private static Resultado calcularPorcentaje(
            ConfiguracionMetricaDto configuracion, List<RegistroClinicoGenerico> base,
            Function<String, CampoClinico> resolverCampo, Integer decimales) {

        List<FiltroMetricaDto> filtrosNum = configuracion.getNumerador() != null
                ? filtrosONull(configuracion.getNumerador().getFiltros()) : List.of();
        List<FiltroMetricaDto> filtrosDen = configuracion.getDenominador() != null
                ? filtrosONull(configuracion.getDenominador().getFiltros()) : List.of();

        long numerador = base.stream()
                .filter(r -> FiltroMetricaEvaluator.cumpleTodos(r, filtrosNum, resolverCampo))
                .count();
        long denominador = base.stream()
                .filter(r -> FiltroMetricaEvaluator.cumpleTodos(r, filtrosDen, resolverCampo))
                .count();

        return porcentajeOSinBase(numerador, denominador, decimales);
    }

    private static Resultado calcularCompletitud(
            ConfiguracionMetricaDto configuracion, List<RegistroClinicoGenerico> base,
            Function<String, CampoClinico> resolverCampo, Integer decimales) {

        CampoClinico campo = resolverCampo.apply(configuracion.getCampoValor());

        if (campo == null) {
            return Resultado.sinBase(0L, 0L);
        }

        long informados = base.stream().filter(r -> leerValor(r, campo) != null).count();

        return porcentajeOSinBase(informados, base.size(), decimales);
    }

    /**
     * Denominador cero devuelve {@code null} + SIN_BASE_EVALUABLE, nunca un
     * {@code 0.0} que en pantalla se confundiría con un 0 % real ni un NaN.
     */
    private static Resultado porcentajeOSinBase(long numerador, long denominador, Integer decimales) {
        if (denominador == 0) {
            return Resultado.sinBase(numerador, 0L);
        }

        double valor = (numerador * 100.0) / denominador;
        return Resultado.dePorcentaje(redondear(valor, decimales), numerador, denominador);
    }

    // ------------------------------------------------------------------
    // Agregados numéricos
    // ------------------------------------------------------------------

    private static Resultado calcularPromedio(
            ConfiguracionMetricaDto configuracion, List<RegistroClinicoGenerico> base,
            Function<String, CampoClinico> resolverCampo, Integer decimales) {

        List<Double> valores = numerosDe(configuracion, base, resolverCampo);

        // Sin ningún valor informado la media no es cero: no hay media.
        if (valores.isEmpty()) {
            return Resultado.sinBase(0L, (long) base.size());
        }

        double promedio = valores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return Resultado.deValorConBase(redondear(promedio, decimales), valores.size(), base.size());
    }

    private static Resultado calcularSuma(
            ConfiguracionMetricaDto configuracion, List<RegistroClinicoGenerico> base,
            Function<String, CampoClinico> resolverCampo, Integer decimales) {

        List<Double> valores = numerosDe(configuracion, base, resolverCampo);

        // Una suma sin sumandos sí es cero: sumar nada da cero, a diferencia de promediar nada.
        double suma = valores.stream().mapToDouble(Double::doubleValue).sum();
        return Resultado.deValorConBase(redondear(suma, decimales), valores.size(), base.size());
    }

    private static Resultado calcularMediana(
            ConfiguracionMetricaDto configuracion, List<RegistroClinicoGenerico> base,
            Function<String, CampoClinico> resolverCampo, Integer decimales) {

        List<Double> valores = new ArrayList<>(numerosDe(configuracion, base, resolverCampo));

        if (valores.isEmpty()) {
            return Resultado.sinBase(0L, (long) base.size());
        }

        valores.sort(Comparator.naturalOrder());
        int medio = valores.size() / 2;

        // Interpolación lineal en tamaño par, igual que percentile_cont(0.5) de
        // Postgres: así la mediana calculada aquí y la comprobada en SQL coinciden.
        double mediana = valores.size() % 2 == 1
                ? valores.get(medio)
                : (valores.get(medio - 1) + valores.get(medio)) / 2.0;

        return Resultado.deValorConBase(redondear(mediana, decimales), valores.size(), base.size());
    }

    private static Resultado calcularExtremo(
            ConfiguracionMetricaDto configuracion, List<RegistroClinicoGenerico> base,
            Function<String, CampoClinico> resolverCampo, Integer decimales, boolean minimo) {

        CampoClinico campo = resolverCampo.apply(configuracion.getCampoValor());

        if (campo == null) {
            return Resultado.sinBase(0L, (long) base.size());
        }

        if (campo.getTipoDato() == TipoDatoExcel.FECHA) {
            List<LocalDate> fechas = base.stream()
                    .map(r -> leerValor(r, campo))
                    .filter(LocalDate.class::isInstance)
                    .map(LocalDate.class::cast)
                    .toList();

            if (fechas.isEmpty()) {
                return Resultado.sinBase(0L, (long) base.size());
            }

            LocalDate extremo = minimo
                    ? fechas.stream().min(LocalDate::compareTo).orElseThrow()
                    : fechas.stream().max(LocalDate::compareTo).orElseThrow();

            return Resultado.deTexto(extremo.toString(), fechas.size(), base.size());
        }

        List<Double> valores = numerosDe(configuracion, base, resolverCampo);

        if (valores.isEmpty()) {
            return Resultado.sinBase(0L, (long) base.size());
        }

        double extremo = minimo
                ? valores.stream().mapToDouble(Double::doubleValue).min().orElseThrow()
                : valores.stream().mapToDouble(Double::doubleValue).max().orElseThrow();

        return Resultado.deValorConBase(redondear(extremo, decimales), valores.size(), base.size());
    }

    // ------------------------------------------------------------------
    // Conteo distinto
    // ------------------------------------------------------------------

    private static Resultado calcularConteoDistinto(
            ConfiguracionMetricaDto configuracion, List<RegistroClinicoGenerico> base,
            Function<String, CampoClinico> resolverCampo, Integer decimales) {

        CampoClinico campo = resolverCampo.apply(configuracion.getCampoValor());

        if (campo == null) {
            return Resultado.sinBase(0L, (long) base.size());
        }

        // Mismo criterio que `count(DISTINCT ...)` en SQL: se descartan los nulos
        // y las cadenas en blanco, y no se normaliza el texto (dos códigos que
        // solo difieren en mayúsculas son dos códigos distintos, como en la base).
        Set<String> distintos = new LinkedHashSet<>();

        for (RegistroClinicoGenerico registro : base) {
            Object valor = leerValor(registro, campo);
            if (valor == null) continue;
            String texto = String.valueOf(valor).trim();
            if (texto.isEmpty()) continue;
            distintos.add(texto);
        }

        return Resultado.deValorConBase(redondear((double) distintos.size(), decimales), distintos.size(), base.size());
    }

    // ------------------------------------------------------------------
    // Distribución y categoría principal
    // ------------------------------------------------------------------

    private static Resultado calcularDistribucion(
            ConfiguracionMetricaDto configuracion, List<RegistroClinicoGenerico> base,
            Function<String, CampoClinico> resolverCampo) {

        CampoClinico campo = resolverCampo.apply(configuracion.getCampoAgrupacion());

        if (campo == null) {
            return Resultado.deItems(List.of());
        }

        boolean incluirNulos = configuracion.getTratamientoNulos() != TratamientoNulos.EXCLUIR;
        Map<String, Long> conteos = new TreeMap<>();

        for (RegistroClinicoGenerico registro : base) {
            Object valor = leerValor(registro, campo);

            if (valor == null) {
                if (incluirNulos) conteos.merge(ETIQUETA_SIN_DATO, 1L, Long::sum);
                continue;
            }

            conteos.merge(etiquetaDe(valor), 1L, Long::sum);
        }

        return Resultado.deItems(aplicarTopN(conteos, configuracion.getMaxCategorias()));
    }

    /**
     * Recorta a las N categorías más frecuentes y agrupa el resto en «Otros».
     *
     * <p>«Otros» es solo presentación: no se puede filtrar por él ni existe como
     * valor en los datos. Sin recorte se conserva el orden alfabético de
     * siempre, para no alterar las distribuciones ya guardadas.
     */
    private static List<ItemDistribucionDto> aplicarTopN(Map<String, Long> conteos, Integer maxCategorias) {
        if (maxCategorias == null || maxCategorias <= 0 || conteos.size() <= maxCategorias) {
            return conteos.entrySet().stream()
                    .map(e -> ItemDistribucionDto.builder().etiqueta(e.getKey()).valor(e.getValue()).build())
                    .toList();
        }

        List<Map.Entry<String, Long>> ordenadas = new ArrayList<>(conteos.entrySet());
        ordenadas.sort(Map.Entry.<String, Long>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()));

        List<ItemDistribucionDto> items = new ArrayList<>();
        long resto = 0L;

        for (int i = 0; i < ordenadas.size(); i++) {
            Map.Entry<String, Long> entrada = ordenadas.get(i);
            if (i < maxCategorias) {
                items.add(ItemDistribucionDto.builder().etiqueta(entrada.getKey()).valor(entrada.getValue()).build());
            } else {
                resto += entrada.getValue();
            }
        }

        if (resto > 0) {
            items.add(ItemDistribucionDto.builder().etiqueta(ETIQUETA_OTROS).valor(resto).build());
        }

        return List.copyOf(items);
    }

    private static Resultado calcularCategoriaPrincipal(
            ConfiguracionMetricaDto configuracion, List<RegistroClinicoGenerico> base,
            Function<String, CampoClinico> resolverCampo, Integer decimales) {

        CampoClinico campo = resolverCampo.apply(configuracion.getCampoAgrupacion());

        if (campo == null) {
            return Resultado.sinBase(0L, (long) base.size());
        }

        boolean incluirNulos = configuracion.getTratamientoNulos() != TratamientoNulos.EXCLUIR;
        Map<String, Long> conteos = new LinkedHashMap<>();
        long evaluados = 0L;

        for (RegistroClinicoGenerico registro : base) {
            Object valor = leerValor(registro, campo);

            if (valor == null) {
                if (!incluirNulos) continue;
                conteos.merge(ETIQUETA_SIN_DATO, 1L, Long::sum);
                evaluados++;
                continue;
            }

            conteos.merge(etiquetaDe(valor), 1L, Long::sum);
            evaluados++;
        }

        if (conteos.isEmpty()) {
            return Resultado.sinBase(0L, (long) base.size());
        }

        // Empate resuelto alfabéticamente: sin desempate estable, dos ejecuciones
        // sobre los mismos datos podrían devolver categorías distintas.
        Map.Entry<String, Long> principal = conteos.entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue()
                        .thenComparing(Comparator.comparing(Map.Entry<String, Long>::getKey).reversed()))
                .orElseThrow();

        double porcentaje = (principal.getValue() * 100.0) / evaluados;

        return Resultado.deCategoriaPrincipal(
                principal.getKey(), redondear(porcentaje, decimales), principal.getValue(), evaluados);
    }

    // ------------------------------------------------------------------

    private static List<Double> numerosDe(
            ConfiguracionMetricaDto configuracion, List<RegistroClinicoGenerico> base,
            Function<String, CampoClinico> resolverCampo) {

        CampoClinico campo = resolverCampo.apply(configuracion.getCampoValor());

        if (campo == null) {
            return List.of();
        }

        return base.stream()
                .map(r -> leerValor(r, campo))
                .filter(Number.class::isInstance)
                .map(v -> ((Number) v).doubleValue())
                .filter(Objects::nonNull)
                .toList();
    }

    private static Object leerValor(RegistroClinicoGenerico registro, CampoClinico campo) {
        Object valorCrudo = RegistroClinicoGenericoValueReader.leerValorCrudo(registro, campo);
        return RegistroClinicoGenericoValueReader.coercionar(valorCrudo, campo.getTipoDato());
    }

    /** Los booleanos se etiquetan en clínico (Sí/No), no como «true»/«false». */
    private static String etiquetaDe(Object valor) {
        if (valor instanceof Boolean b) {
            return b ? "Sí" : "No";
        }
        return String.valueOf(valor);
    }

    private static List<FiltroMetricaDto> filtrosONull(List<FiltroMetricaDto> filtros) {
        return filtros != null ? filtros : List.of();
    }

    private static Double redondear(Double valor, Integer decimales) {
        if (valor == null) {
            return null;
        }

        int escala = decimales != null ? decimales : 2;
        return BigDecimal.valueOf(valor).setScale(escala, RoundingMode.HALF_UP).doubleValue();
    }

    public static final class Resultado {
        private final Double valor;
        private final String valorTexto;
        private final Long totalNumerador;
        private final Long totalDenominador;
        private final EstadoResultadoMetrica estado;
        private final List<ItemDistribucionDto> items;

        private Resultado(
                Double valor, String valorTexto, Long totalNumerador, Long totalDenominador,
                EstadoResultadoMetrica estado, List<ItemDistribucionDto> items) {
            this.valor = valor;
            this.valorTexto = valorTexto;
            this.totalNumerador = totalNumerador;
            this.totalDenominador = totalDenominador;
            this.estado = estado;
            this.items = items;
        }

        static Resultado deValor(Double valor) {
            return new Resultado(valor, null, null, null, EstadoResultadoMetrica.OK, null);
        }

        static Resultado deValorConBase(Double valor, long informados, long evaluados) {
            return new Resultado(valor, null, informados, evaluados, EstadoResultadoMetrica.OK, null);
        }

        static Resultado dePorcentaje(Double valor, long numerador, long denominador) {
            return new Resultado(valor, null, numerador, denominador, EstadoResultadoMetrica.OK, null);
        }

        static Resultado deTexto(String valorTexto, long informados, long evaluados) {
            return new Resultado(null, valorTexto, informados, evaluados, EstadoResultadoMetrica.OK, null);
        }

        static Resultado deCategoriaPrincipal(String etiqueta, Double porcentaje, long frecuencia, long evaluados) {
            return new Resultado(porcentaje, etiqueta, frecuencia, evaluados, EstadoResultadoMetrica.OK, null);
        }

        static Resultado deItems(List<ItemDistribucionDto> items) {
            return new Resultado(null, null, null, null, EstadoResultadoMetrica.OK, items);
        }

        static Resultado sinBase(Long numerador, Long denominador) {
            return new Resultado(null, null, numerador, denominador, EstadoResultadoMetrica.SIN_BASE_EVALUABLE, null);
        }

        public Double getValor() {
            return valor;
        }

        public String getValorTexto() {
            return valorTexto;
        }

        public Long getTotalNumerador() {
            return totalNumerador;
        }

        public Long getTotalDenominador() {
            return totalDenominador;
        }

        public EstadoResultadoMetrica getEstado() {
            return estado;
        }

        public String getEstadoNombre() {
            return estado.name();
        }

        public List<ItemDistribucionDto> getItems() {
            return items;
        }
    }
}
