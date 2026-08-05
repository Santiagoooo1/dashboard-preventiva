package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.DatasetClinicoResponseDto;
import com.preventiva.backend.dto.OperacionDisponibleDto;
import com.preventiva.backend.dto.PerfilCampoDto;
import com.preventiva.backend.dto.PerfilCamposResponseDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.entity.DatasetClinico;
import com.preventiva.backend.enums.RolAnaliticoCampo;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.enums.TipoMetrica;
import com.preventiva.backend.repository.CampoClinicoRepository;
import com.preventiva.backend.repository.DatasetClinicoRepository;
import com.preventiva.backend.repository.PerfilCampoRepository;
import com.preventiva.backend.service.interfaces.PerfilCampoService;
import com.preventiva.backend.util.CampoIndividuoResolver;
import com.preventiva.backend.util.OperacionMetricaUtil;
import com.preventiva.backend.util.RolAnaliticoUtil;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Construye el perfil analítico de cada columna (Fase 6.9I.2).
 *
 * <p>Es lo que convierte «una columna llamada adecuacionProfilaxis de tipo
 * TEXTO» en «una categoría con tres valores, 249 informados de 280, sobre la
 * que tiene sentido pedir una distribución». Todas las cuentas salen de la base
 * de datos; aquí solo se interpretan.
 */
@Service
@RequiredArgsConstructor
public class PerfilCampoServiceImpl implements PerfilCampoService {

    /** Cuántos valores de ejemplo se enseñan. Suficiente para reconocer la columna, nada más. */
    private static final int MAX_VALORES_EJEMPLO = 8;

    /** Mismo umbral que usa el selector de visualizaciones para dejar de ofrecer donut. */
    private static final int MAX_CATEGORIAS_DONUT = 8;

    /** Por encima de esto, una categoría deja de ser cómoda incluso en barras. */
    private static final int UMBRAL_CARDINALIDAD_MEDIA = 12;

    private final DatasetClinicoRepository datasetClinicoRepository;
    private final CampoClinicoRepository campoClinicoRepository;
    private final PerfilCampoRepository perfilCampoRepository;

    @Override
    @Transactional(readOnly = true)
    public PerfilCamposResponseDto obtenerPerfilCampos(Long datasetId) {
        DatasetClinico dataset = datasetClinicoRepository.findById(datasetId)
                .orElseThrow(() -> new NoSuchElementException("No existe el dataset con id: " + datasetId));

        List<CampoClinico> campos = campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId);

        String campoIndividuo = CampoIndividuoResolver.resolver(campos)
                .map(CampoClinico::getCodigo)
                .orElse(null);

        List<PerfilCampoDto> perfiles = campos.stream()
                .map(campo -> perfilDe(datasetId, campo, campos))
                .toList();

        long totalRegistros = perfiles.stream().mapToLong(PerfilCampoDto::getTotalRegistros).max().orElse(0L);

        return PerfilCamposResponseDto.builder()
                .dataset(mapDataset(dataset))
                .totalRegistros(totalRegistros)
                .campos(perfiles)
                .campoIndividuo(campoIndividuo)
                .umbralCardinalidadCategorica(RolAnaliticoUtil.UMBRAL_CARDINALIDAD_CATEGORICA)
                .maxCategoriasDonut(MAX_CATEGORIAS_DONUT)
                .build();
    }

    private PerfilCampoDto perfilDe(Long datasetId, CampoClinico campo, List<CampoClinico> todosLosCampos) {
        Object[] fila = perfilCampoRepository.estadisticas(datasetId, campo);

        long total = numero(fila[0]);
        long informados = numero(fila[1]);
        long distintos = numero(fila[2]);
        String minimo = texto(fila[3]);
        String maximo = texto(fila[4]);

        RolAnaliticoCampo rol = RolAnaliticoUtil.rol(campo, todosLosCampos, distintos);
        boolean esIndividuo = RolAnaliticoUtil.esIdentificador(campo, todosLosCampos);

        // Mínimo y máximo solo se enseñan donde orientan (¿de qué años son los
        // datos?, ¿qué rango de edad?). En un texto serían el primer y el último
        // valor alfabéticos, que no dicen nada.
        boolean tieneRango = campo.getTipoDato() == TipoDatoExcel.ENTERO
                || campo.getTipoDato() == TipoDatoExcel.DECIMAL
                || campo.getTipoDato() == TipoDatoExcel.FECHA;

        return PerfilCampoDto.builder()
                .codigo(campo.getCodigo())
                .etiqueta(campo.getEtiqueta())
                .tipoDato(campo.getTipoDato().name())
                .activo(campo.getActivo())
                .rolSugerido(rol.name())
                .rolesAlternativos(rolesAlternativos(campo, rol).stream().map(Enum::name).toList())
                .totalRegistros(total)
                .valoresInformados(informados)
                .valoresSinDato(total - informados)
                .valoresDistintos(distintos)
                .cardinalidad(clasificarCardinalidad(distintos))
                .completitud(porcentaje(informados, total))
                .valorMinimo(tieneRango ? minimo : null)
                .valorMaximo(tieneRango ? maximo : null)
                .valoresEjemplo(valoresEjemplo(datasetId, campo, rol))
                .operaciones(operacionesDe(campo, rol))
                .operacionesPorRol(operacionesPorRol(campo, rol))
                .esIdentificadorIndividuo(esIndividuo)
                .build();
    }

    /**
     * Reinterpretaciones que el usuario puede elegir sin que el resultado pase a
     * ser falso.
     *
     * <p>Solo se permiten las que no cambian cómo se leen los datos: un texto
     * categórico puede tratarse como texto libre (y al revés), porque en ambos
     * casos el valor se lee igual. Lo que NO se ofrece es interpretar un texto
     * como numérico o una columna cualquiera como fecha: eso obligaría a parsear
     * y produciría medias calculadas solo sobre las filas que casualmente
     * convierten.
     */
    private List<RolAnaliticoCampo> rolesAlternativos(CampoClinico campo, RolAnaliticoCampo rolActual) {
        if (campo.getTipoDato() != TipoDatoExcel.TEXTO) {
            return List.of();
        }

        List<RolAnaliticoCampo> alternativos = new ArrayList<>();

        if (rolActual != RolAnaliticoCampo.CATEGORICO) alternativos.add(RolAnaliticoCampo.CATEGORICO);
        if (rolActual != RolAnaliticoCampo.TEXTO_LIBRE) alternativos.add(RolAnaliticoCampo.TEXTO_LIBRE);
        if (rolActual != RolAnaliticoCampo.IDENTIFICADOR) alternativos.add(RolAnaliticoCampo.IDENTIFICADOR);

        return List.copyOf(alternativos);
    }

    /**
     * Valores de ejemplo. En texto libre no se devuelven: son observaciones
     * clínicas y no deben acabar en una lista desplegable ni en una leyenda.
     */
    private List<String> valoresEjemplo(Long datasetId, CampoClinico campo, RolAnaliticoCampo rol) {
        if (rol == RolAnaliticoCampo.TEXTO_LIBRE || rol == RolAnaliticoCampo.IDENTIFICADOR
                || rol == RolAnaliticoCampo.FECHA) {
            return List.of();
        }

        return perfilCampoRepository.valoresFrecuentes(datasetId, campo, MAX_VALORES_EJEMPLO)
                .stream()
                .map(fila -> texto(fila[0]))
                .filter(v -> v != null && !v.isBlank())
                .toList();
    }

    private String clasificarCardinalidad(long distintos) {
        if (distintos <= MAX_CATEGORIAS_DONUT) return "BAJA";
        if (distintos <= UMBRAL_CARDINALIDAD_MEDIA) return "MEDIA";
        return "ALTA";
    }

    /** El rol sugerido y todas sus alternativas, cada uno con sus operaciones. */
    private Map<String, List<OperacionDisponibleDto>> operacionesPorRol(
            CampoClinico campo, RolAnaliticoCampo rolSugerido) {
        Map<String, List<OperacionDisponibleDto>> porRol = new LinkedHashMap<>();
        porRol.put(rolSugerido.name(), operacionesDe(campo, rolSugerido));

        for (RolAnaliticoCampo alternativo : rolesAlternativos(campo, rolSugerido)) {
            porRol.put(alternativo.name(), operacionesDe(campo, alternativo));
        }

        return porRol;
    }

    private List<OperacionDisponibleDto> operacionesDe(CampoClinico campo, RolAnaliticoCampo rol) {
        return OperacionMetricaUtil.operacionesPara(rol).stream()
                .map(tipo -> OperacionDisponibleDto.builder()
                        .codigo(tipo.name())
                        .nombre(nombreOperacion(tipo, rol))
                        .explicacion(explicacionOperacion(tipo, campo, rol))
                        .sufijoCodigo(sufijoCodigo(tipo))
                        .planResultado(planResultado(tipo))
                        .visualizacionRecomendada(visualizacionRecomendada(tipo))
                        .porcentual(OperacionMetricaUtil.esPorcentual(tipo))
                        .exigeTopN(OperacionMetricaUtil.exigeTopN(tipo, rol))
                        .advertencia(advertencia(tipo, rol))
                        .build())
                .toList();
    }

    private String nombreOperacion(TipoMetrica tipo, RolAnaliticoCampo rol) {
        return switch (tipo) {
            case CONTEO -> "Número de registros";
            case CONTEO_DISTINTO -> rol == RolAnaliticoCampo.IDENTIFICADOR
                    ? "Pacientes distintos"
                    : "Valores distintos";
            case COMPLETITUD -> "Completitud (% informado)";
            case PORCENTAJE -> rol == RolAnaliticoCampo.BOOLEANO ? "Porcentaje de Sí" : "Porcentaje condicional";
            case DISTRIBUCION -> rol == RolAnaliticoCampo.BOOLEANO ? "Reparto Sí / No / Sin dato" : "Distribución";
            case PROMEDIO -> "Media";
            case MEDIANA -> "Mediana";
            case SUMA -> "Suma";
            case MINIMO -> rol == RolAnaliticoCampo.FECHA ? "Primera fecha" : "Mínimo";
            case MAXIMO -> rol == RolAnaliticoCampo.FECHA ? "Última fecha" : "Máximo";
            case CATEGORIA_PRINCIPAL -> "Categoría más frecuente";
        };
    }

    private String explicacionOperacion(TipoMetrica tipo, CampoClinico campo, RolAnaliticoCampo rol) {
        String etiqueta = campo.getEtiqueta();

        return switch (tipo) {
            case CONTEO -> "Cuenta los registros que cumplen los filtros indicados.";
            case CONTEO_DISTINTO -> "Cuenta cuántos valores distintos y no vacíos tiene " + etiqueta + ".";
            case COMPLETITUD -> "Porcentaje de registros con " + etiqueta
                    + " informado, sobre el total evaluado.";
            case PORCENTAJE -> rol == RolAnaliticoCampo.BOOLEANO
                    ? "Porcentaje de registros con " + etiqueta + " = Sí, sobre los que tienen dato."
                    : "Porcentaje entre un numerador y un denominador definidos con filtros.";
            case DISTRIBUCION -> "Cuenta los registros de cada valor de " + etiqueta + ".";
            case PROMEDIO -> "Promedio de " + etiqueta + ", excluyendo los registros sin dato.";
            case MEDIANA -> "Valor central de " + etiqueta + ": la mitad de los casos queda por debajo.";
            case SUMA -> "Suma de " + etiqueta + " en los registros con dato.";
            case MINIMO -> rol == RolAnaliticoCampo.FECHA
                    ? "La fecha más antigua registrada en " + etiqueta + "."
                    : "El valor más bajo de " + etiqueta + ".";
            case MAXIMO -> rol == RolAnaliticoCampo.FECHA
                    ? "La fecha más reciente registrada en " + etiqueta + "."
                    : "El valor más alto de " + etiqueta + ".";
            case CATEGORIA_PRINCIPAL -> "El valor más repetido de " + etiqueta + " y qué porcentaje representa.";
        };
    }

    private String sufijoCodigo(TipoMetrica tipo) {
        return switch (tipo) {
            case CONTEO -> "_conteo";
            case CONTEO_DISTINTO -> "_distintos";
            case COMPLETITUD -> "_completitud";
            case PORCENTAJE -> "_porcentaje";
            case DISTRIBUCION -> "_distribucion";
            case PROMEDIO -> "_promedio";
            case MEDIANA -> "_mediana";
            case SUMA -> "_suma";
            case MINIMO -> "_minimo";
            case MAXIMO -> "_maximo";
            case CATEGORIA_PRINCIPAL -> "_categoria_principal";
        };
    }

    /** Forma del resultado. Determina qué visualizaciones ofrece el frontend. */
    private String planResultado(TipoMetrica tipo) {
        return tipo == TipoMetrica.DISTRIBUCION ? "AGRUPADO" : "UNICO";
    }

    private String visualizacionRecomendada(TipoMetrica tipo) {
        return tipo == TipoMetrica.DISTRIBUCION ? "BARRAS" : "KPI";
    }

    private String advertencia(TipoMetrica tipo, RolAnaliticoCampo rol) {
        if (OperacionMetricaUtil.exigeTopN(tipo, rol)) {
            return "Este campo tiene muchos valores distintos: la distribución se limitará a las categorías"
                    + " más frecuentes y el resto se agrupará en «Otros».";
        }

        if (tipo == TipoMetrica.CATEGORIA_PRINCIPAL && rol == RolAnaliticoCampo.TEXTO_LIBRE) {
            return "Con muchos valores distintos, la categoría más frecuente puede representar un porcentaje muy"
                    + " pequeño del total.";
        }

        return null;
    }

    private Double porcentaje(long parte, long total) {
        if (total == 0) {
            return null;
        }
        return BigDecimal.valueOf((parte * 100.0) / total).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private long numero(Object valor) {
        return valor instanceof Number n ? n.longValue() : 0L;
    }

    private String texto(Object valor) {
        return valor != null ? String.valueOf(valor) : null;
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
}
