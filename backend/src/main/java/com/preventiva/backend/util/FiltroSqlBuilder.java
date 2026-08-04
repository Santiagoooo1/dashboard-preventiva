package com.preventiva.backend.util;

import com.preventiva.backend.dto.FiltroMetricaDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.enums.TipoDatoExcel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Traduce los filtros clínicos a SQL para poder aplicar WHERE, ORDER BY, LIMIT
 * y OFFSET en la base de datos (Fase 6.9H.3).
 *
 * <p>Existe porque el detalle del subconjunto pagina tablas que pueden tener
 * decenas de miles de filas: cargar el dataset entero en memoria para después
 * hacer {@code subList} no escala. El resto del motor de métricas sigue usando
 * {@link FiltroMetricaEvaluator} en memoria, sobre volúmenes agregados.
 *
 * <p><b>La semántica debe coincidir EXACTAMENTE con FiltroMetricaEvaluator</b>,
 * o el detalle mostraría una población distinta de la que resumen los gráficos.
 * Por eso:
 * <ul>
 *   <li>TEXTO se compara normalizado igual que {@code TextNormalizer.normalize}:
 *       sin acentos, sin espacios repetidos, en mayúsculas. Se usa
 *       {@code translate()} en vez de {@code unaccent()} porque esa extensión
 *       no está instalada y hacerlo exigiría una migración.</li>
 *   <li>BOOLEANO usa el mismo vocabulario español que
 *       {@code RegistroClinicoGenericoValueReader.coercionarBooleano}.</li>
 *   <li>Los campos JSONB se leen con {@code ->>} y se castean al tipo declarado
 *       en CampoClinico.</li>
 * </ul>
 *
 * <p>Seguridad: los nombres de campo NUNCA se concatenan tal cual. Solo se
 * admiten campos que existen en {@code CampoClinico} para el dataset, y los
 * dinámicos viajan como parámetro de {@code ->>}. Todos los valores van
 * parametrizados.
 */
public final class FiltroSqlBuilder {

    /** Acentos que NFD+strip elimina y que aquí se replican sin extensiones. */
    private static final String ACENTUADAS = "áàäâãéèëêíìïîóòöôõúùüûñçÁÀÄÂÃÉÈËÊÍÌÏÎÓÒÖÔÕÚÙÜÛÑÇ";
    private static final String SIN_ACENTO = "aaaaaeeeeiiiiooooouuuuncAAAAAEEEEIIIIOOOOOUUUUNC";

    /** Mismo vocabulario que RegistroClinicoGenericoValueReader. */
    private static final List<String> VERDADEROS =
            List.of("SI", "S", "TRUE", "VERDADERO", "1", "X", "POSITIVO", "ADECUADA");

    /** Campos comunes → columna real de registros_clinicos_genericos. */
    private static final Map<String, String> COLUMNAS_COMUNES = Map.of(
            "pacienteCodigo", "r.paciente_codigo",
            "fechaEvento", "r.fecha_evento",
            "servicio", "r.servicio",
            "tipoEvento", "r.tipo_evento",
            "procedimiento", "r.procedimiento",
            "diagnostico", "r.diagnostico",
            "edad", "r.edad",
            "sexo", "r.sexo");

    private FiltroSqlBuilder() {
    }

    /** Fragmento SQL + parámetros con nombre, listos para una query nativa. */
    public record Where(String sql, Map<String, Object> parametros) {
    }

    /**
     * Construye el WHERE del subconjunto. Devuelve "1=1" si no hay filtros.
     *
     * @param camposPorCodigo campos ACTIVOS del dataset: cualquier filtro que
     *                        apunte fuera de este mapa se rechaza.
     */
    public static Where construir(List<FiltroMetricaDto> filtros, Map<String, CampoClinico> camposPorCodigo) {
        if (filtros == null || filtros.isEmpty()) {
            return new Where("1=1", Map.of());
        }

        List<String> condiciones = new ArrayList<>();
        Map<String, Object> parametros = new LinkedHashMap<>();
        int indice = 0;

        for (FiltroMetricaDto filtro : filtros) {
            CampoClinico campo = camposPorCodigo.get(filtro.getCampo());
            if (campo == null) {
                throw new IllegalArgumentException(
                        "El campo '" + filtro.getCampo() + "' no existe o no está activo en este dataset.");
            }
            if (filtro.getOperador() == null) {
                throw new IllegalArgumentException("Cada filtro debe indicar un operador.");
            }

            condiciones.add(condicion(campo, filtro, indice++, parametros));
        }

        return new Where(String.join(" AND ", condiciones), parametros);
    }

    /** Expresión SQL que devuelve el valor del campo, ya casteado a su tipo. */
    public static String expresionValor(CampoClinico campo) {
        String bruto = expresionBruta(campo);

        return switch (campo.getTipoDato()) {
            case TEXTO -> bruto;
            case ENTERO, DECIMAL -> esComun(campo)
                    ? bruto
                    // Un JSONB puede traer basura donde se espera un número: el
                    // regex evita que el cast reviente toda la consulta, igual
                    // que el try/catch de coercionarEntero.
                    : "(CASE WHEN " + bruto + " ~ '^-?[0-9]+(\\.[0-9]+)?$' THEN (" + bruto + ")::numeric END)";
            case FECHA -> esComun(campo)
                    ? bruto
                    : "(CASE WHEN " + bruto + " ~ '^\\d{4}-\\d{2}-\\d{2}$' THEN (" + bruto + ")::date END)";
            case BOOLEANO -> "(CASE WHEN " + normalizar(bruto) + " IN (" + literalesVerdaderos() + ") THEN true"
                    + " WHEN " + normalizar(bruto) + " IN ('NO','N','FALSE','FALSO','0','NEGATIVO','INADECUADA','NO ADECUADA') THEN false END)";
        };
    }

    /** Expresión normalizada para comparar TEXTO (equivale a TextNormalizer). */
    public static String expresionTextoNormalizado(CampoClinico campo) {
        return normalizar(expresionBruta(campo));
    }

    private static String expresionBruta(CampoClinico campo) {
        if (esComun(campo)) {
            String columna = COLUMNAS_COMUNES.get(campo.getCodigo());
            if (columna == null) {
                throw new IllegalArgumentException("Campo común no reconocido: " + campo.getCodigo());
            }
            // Los comunes de tipo texto se comparan como texto.
            return columna;
        }
        // Campo dinámico: la CLAVE va parametrizada, nunca concatenada.
        return "(r.datos_dinamicos ->> :campo_" + indiceClave(campo) + ")";
    }

    /**
     * Un campo se lee de columna propia solo si está marcado como común Y
     * existe realmente esa columna. Un campo marcado {@code esComun} cuyo
     * código no esté en el mapa vive igualmente en el JSONB.
     *
     * <p>Público porque quien construya la consulta necesita el MISMO criterio
     * para saber si debe pasar la clave del campo como parámetro: usar solo el
     * flag `esComun` dejaba la consulta sin el parámetro `:campo_x` y fallaba
     * con «No argument for named parameter».
     */
    public static boolean esColumnaComun(CampoClinico campo) {
        return Boolean.TRUE.equals(campo.getEsComun()) && COLUMNAS_COMUNES.containsKey(campo.getCodigo());
    }

    private static boolean esComun(CampoClinico campo) {
        return esColumnaComun(campo);
    }

    /**
     * Los campos dinámicos necesitan su clave como parámetro. Para no arrastrar
     * un contador por toda la construcción se usa el propio código como nombre
     * de parámetro, saneado a [a-z0-9_] (nunca se inyecta el código en el SQL:
     * el saneado solo genera un IDENTIFICADOR de parámetro).
     */
    private static String indiceClave(CampoClinico campo) {
        return campo.getCodigo().toLowerCase().replaceAll("[^a-z0-9]", "_");
    }

    private static String normalizar(String expresion) {
        return "upper(btrim(regexp_replace(translate(" + expresion + ", '" + ACENTUADAS + "', '" + SIN_ACENTO
                + "'), '\\s+', ' ', 'g')))";
    }

    private static String literalesVerdaderos() {
        return String.join(",", VERDADEROS.stream().map(v -> "'" + v + "'").toList());
    }

    private static String condicion(
            CampoClinico campo, FiltroMetricaDto filtro, int indice, Map<String, Object> parametros) {

        String p = "v" + indice;
        if (!esComun(campo)) {
            parametros.put("campo_" + indiceClave(campo), campo.getCodigo());
        }

        boolean texto = campo.getTipoDato() == TipoDatoExcel.TEXTO;
        String valorSql = texto ? expresionTextoNormalizado(campo) : expresionValor(campo);

        return switch (filtro.getOperador()) {
            case IS_NULL -> valorSql + " IS NULL";
            case NOT_NULL -> valorSql + " IS NOT NULL";
            case EQ -> {
                parametros.put(p, valorComparable(campo, filtro.getValor()));
                yield "(" + valorSql + " = " + castParametro(campo, p) + ")";
            }
            // NE incluye los nulos, igual que el evaluador Java
            // (`valor == null || !iguales(...)`).
            case NE -> {
                parametros.put(p, valorComparable(campo, filtro.getValor()));
                yield "(" + valorSql + " IS NULL OR " + valorSql + " <> " + castParametro(campo, p) + ")";
            }
            // IN/NOT_IN se expanden a parámetros sueltos en vez de pasar un
            // array: el driver serializa Object[] como bytea y el cast a text[]
            // falla. Un IN con N parámetros es además más portable.
            case IN -> {
                String lista = expandirLista(campo, filtro.getValor(), p, parametros);
                yield lista.isEmpty() ? "false" : "(" + valorSql + " IN (" + lista + "))";
            }
            case NOT_IN -> {
                String lista = expandirLista(campo, filtro.getValor(), p, parametros);
                yield lista.isEmpty()
                        ? "true"
                        : "(" + valorSql + " IS NULL OR " + valorSql + " NOT IN (" + lista + "))";
            }
            case GT, GTE, LT, LTE -> {
                parametros.put(p, valorComparable(campo, filtro.getValor()));
                yield "(" + valorSql + " " + simbolo(filtro.getOperador().name()) + " " + castParametro(campo, p) + ")";
            }
            case CONTAINS -> {
                parametros.put(p, TextNormalizer.normalize(String.valueOf(filtro.getValor())));
                yield "(" + expresionTextoNormalizado(campo) + " LIKE '%' || CAST(:" + p + " AS text) || '%')";
            }
        };
    }

    private static String simbolo(String operador) {
        return switch (operador) {
            case "GT" -> ">";
            case "GTE" -> ">=";
            case "LT" -> "<";
            default -> "<=";
        };
    }

    private static String castParametro(CampoClinico campo, String p) {
        return switch (campo.getTipoDato()) {
            case TEXTO -> "CAST(:" + p + " AS text)";
            case ENTERO, DECIMAL -> "CAST(:" + p + " AS numeric)";
            case FECHA -> "CAST(:" + p + " AS date)";
            case BOOLEANO -> "CAST(:" + p + " AS boolean)";
        };
    }

    /**
     * Registra un parámetro por elemento de la lista y devuelve los marcadores
     * ya casteados. Cadena vacía si la lista viene vacía o no es una colección:
     * el llamante lo traduce a `false`/`true`, igual que hace el evaluador Java
     * cuando `perteneceALista` recibe algo que no es una colección.
     */
    private static String expandirLista(
            CampoClinico campo, Object valor, String prefijo, Map<String, Object> parametros) {

        if (!(valor instanceof Collection<?> coleccion) || coleccion.isEmpty()) {
            return "";
        }

        List<String> marcadores = new ArrayList<>();
        int i = 0;
        for (Object elemento : coleccion) {
            String nombre = prefijo + "_" + i++;
            parametros.put(nombre, valorComparable(campo, elemento));
            marcadores.add(castParametro(campo, nombre));
        }
        return String.join(", ", marcadores);
    }

    /** Valor listo para el parámetro, con la MISMA coerción que el evaluador Java. */
    private static Object valorComparable(CampoClinico campo, Object valor) {
        if (valor == null) return null;

        return switch (campo.getTipoDato()) {
            case TEXTO -> TextNormalizer.normalize(String.valueOf(valor));
            case ENTERO, DECIMAL -> new java.math.BigDecimal(
                    String.valueOf(valor).trim().replace(",", "."));
            case FECHA -> java.time.LocalDate.parse(String.valueOf(valor).trim());
            case BOOLEANO -> VERDADEROS.contains(TextNormalizer.normalize(String.valueOf(valor)));
        };
    }

}
