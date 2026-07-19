package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.CatalogoFrontendResponseDto;
import com.preventiva.backend.dto.EstructuraConfiguracionMetricaDto;
import com.preventiva.backend.dto.OpcionCatalogoDto;
import com.preventiva.backend.dto.OperadorFiltroCatalogoDto;
import com.preventiva.backend.dto.ReglasCompatibilidadDto;
import com.preventiva.backend.dto.ResolucionTipoResultadoDto;
import com.preventiva.backend.dto.TipoMetricaCatalogoDto;
import com.preventiva.backend.dto.TipoVisualizacionCatalogoDto;
import com.preventiva.backend.enums.Granularidad;
import com.preventiva.backend.enums.OperadorFiltro;
import com.preventiva.backend.enums.PoliticaCampoFaltante;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.enums.TipoMetrica;
import com.preventiva.backend.enums.TipoResultadoWidget;
import com.preventiva.backend.enums.TipoVisualizacion;
import com.preventiva.backend.service.interfaces.FrontendCatalogoService;
import com.preventiva.backend.util.OperadorFiltroCompatibilidadUtil;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class FrontendCatalogoServiceImpl implements FrontendCatalogoService {

    @Override
    public CatalogoFrontendResponseDto obtenerCatalogo() {
        return CatalogoFrontendResponseDto.builder()
                .tipoMetricas(construirTipoMetricas())
                .operadoresFiltro(construirOperadoresFiltro())
                .tipoVisualizaciones(construirTipoVisualizaciones())
                .tipoResultadoWidget(construirTipoResultadoWidget())
                .granularidades(construirGranularidades())
                .tiposDato(construirTiposDato())
                .politicasCampoFaltante(construirPoliticasCampoFaltante())
                .reglasCompatibilidad(construirReglasCompatibilidad())
                .build();
    }

    private List<TipoMetricaCatalogoDto> construirTipoMetricas() {
        return List.of(
                TipoMetricaCatalogoDto.builder()
                        .codigo(TipoMetrica.CONTEO.name())
                        .nombre("Conteo")
                        .descripcion("Cuenta el número de registros que cumplen los filtros.")
                        .requiereCampoValor(false)
                        .requiereCampoAgrupacion(false)
                        .permiteFiltros(true)
                        .permiteSerieTemporal(true)
                        .permiteComparativa(true)
                        .estructuraConfiguracion(EstructuraConfiguracionMetricaDto.builder()
                                .camposRequeridos(List.of())
                                .camposOpcionales(List.of("filtros"))
                                .build())
                        .ejemploConfiguracion(Map.of("filtros", List.of()))
                        .build(),
                TipoMetricaCatalogoDto.builder()
                        .codigo(TipoMetrica.PORCENTAJE.name())
                        .nombre("Porcentaje")
                        .descripcion("Calcula numerador/denominador, cada uno con sus propios filtros.")
                        .requiereCampoValor(false)
                        .requiereCampoAgrupacion(false)
                        .permiteFiltros(true)
                        .permiteSerieTemporal(true)
                        .permiteComparativa(true)
                        .estructuraConfiguracion(EstructuraConfiguracionMetricaDto.builder()
                                .camposRequeridos(List.of("numerador", "denominador"))
                                .camposOpcionales(List.of("filtros"))
                                .build())
                        .ejemploConfiguracion(Map.of(
                                "numerador", Map.of("filtros", List.of(
                                        Map.of("campo", "<campo>", "operador", "EQ", "valor", "<valor>"))),
                                "denominador", Map.of("filtros", List.of())))
                        .build(),
                TipoMetricaCatalogoDto.builder()
                        .codigo(TipoMetrica.PROMEDIO.name())
                        .nombre("Promedio")
                        .descripcion("Calcula la media de un campo numérico (ENTERO o DECIMAL).")
                        .requiereCampoValor(true)
                        .requiereCampoAgrupacion(false)
                        .permiteFiltros(true)
                        .permiteSerieTemporal(true)
                        .permiteComparativa(true)
                        .estructuraConfiguracion(EstructuraConfiguracionMetricaDto.builder()
                                .camposRequeridos(List.of("campoValor"))
                                .camposOpcionales(List.of("filtros"))
                                .build())
                        .ejemploConfiguracion(Map.of("campoValor", "<campo_numerico>", "filtros", List.of()))
                        .build(),
                TipoMetricaCatalogoDto.builder()
                        .codigo(TipoMetrica.SUMA.name())
                        .nombre("Suma")
                        .descripcion("Calcula la suma de un campo numérico (ENTERO o DECIMAL).")
                        .requiereCampoValor(true)
                        .requiereCampoAgrupacion(false)
                        .permiteFiltros(true)
                        .permiteSerieTemporal(true)
                        .permiteComparativa(true)
                        .estructuraConfiguracion(EstructuraConfiguracionMetricaDto.builder()
                                .camposRequeridos(List.of("campoValor"))
                                .camposOpcionales(List.of("filtros"))
                                .build())
                        .ejemploConfiguracion(Map.of("campoValor", "<campo_numerico>", "filtros", List.of()))
                        .build(),
                TipoMetricaCatalogoDto.builder()
                        .codigo(TipoMetrica.DISTRIBUCION.name())
                        .nombre("Distribución")
                        .descripcion("Cuenta registros agrupados por un campo. No admite serie temporal ni comparativa.")
                        .requiereCampoValor(false)
                        .requiereCampoAgrupacion(true)
                        .permiteFiltros(false)
                        .permiteSerieTemporal(false)
                        .permiteComparativa(false)
                        .estructuraConfiguracion(EstructuraConfiguracionMetricaDto.builder()
                                .camposRequeridos(List.of("campoAgrupacion"))
                                .camposOpcionales(List.of())
                                .build())
                        .ejemploConfiguracion(Map.of("campoAgrupacion", "<campo_agrupable>"))
                        .build());
    }

    private List<OperadorFiltroCatalogoDto> construirOperadoresFiltro() {
        return List.of(OperadorFiltro.values()).stream()
                .map(operador -> OperadorFiltroCatalogoDto.builder()
                        .codigo(operador.name())
                        .nombre(nombreOperador(operador))
                        .tiposDatoCompatibles(OperadorFiltroCompatibilidadUtil.tiposCompatibles(operador).stream()
                                .map(Enum::name).toList())
                        .requiereValor(OperadorFiltroCompatibilidadUtil.requiereValor(operador))
                        .requiereLista(OperadorFiltroCompatibilidadUtil.requiereLista(operador))
                        .build())
                .toList();
    }

    private String nombreOperador(OperadorFiltro operador) {
        return switch (operador) {
            case EQ -> "Igual";
            case NE -> "Distinto";
            case IN -> "En lista";
            case NOT_IN -> "No en lista";
            case GT -> "Mayor que";
            case GTE -> "Mayor o igual que";
            case LT -> "Menor que";
            case LTE -> "Menor o igual que";
            case IS_NULL -> "Sin valor";
            case NOT_NULL -> "Con valor";
            case CONTAINS -> "Contiene";
        };
    }

    private List<TipoVisualizacionCatalogoDto> construirTipoVisualizaciones() {
        return List.of(
                TipoVisualizacionCatalogoDto.builder().codigo(TipoVisualizacion.KPI.name()).nombre("KPI")
                        .tipoResultadoPorDefecto(TipoResultadoWidget.ACTUAL.name()).build(),
                TipoVisualizacionCatalogoDto.builder().codigo(TipoVisualizacion.TARJETA.name()).nombre("Tarjeta")
                        .tipoResultadoPorDefecto(TipoResultadoWidget.ACTUAL.name()).build(),
                TipoVisualizacionCatalogoDto.builder().codigo(TipoVisualizacion.LINEAS.name()).nombre("Líneas")
                        .tipoResultadoPorDefecto(null).build(),
                TipoVisualizacionCatalogoDto.builder().codigo(TipoVisualizacion.BARRAS.name()).nombre("Barras")
                        .tipoResultadoPorDefecto(null).build(),
                TipoVisualizacionCatalogoDto.builder().codigo(TipoVisualizacion.TABLA.name()).nombre("Tabla")
                        .tipoResultadoPorDefecto(null).build(),
                TipoVisualizacionCatalogoDto.builder().codigo(TipoVisualizacion.DONUT.name()).nombre("Donut")
                        .tipoResultadoPorDefecto(null).build(),
                TipoVisualizacionCatalogoDto.builder().codigo(TipoVisualizacion.PIE.name()).nombre("Circular")
                        .tipoResultadoPorDefecto(null).build());
    }

    private List<OpcionCatalogoDto> construirTipoResultadoWidget() {
        return List.of(
                OpcionCatalogoDto.builder().codigo(TipoResultadoWidget.ACTUAL.name()).nombre("Valor actual")
                        .descripcion("Muestra el resultado directo de la métrica, sin serie ni agrupación.").build(),
                OpcionCatalogoDto.builder().codigo(TipoResultadoWidget.SERIE_TEMPORAL.name()).nombre("Serie temporal")
                        .descripcion("Muestra la evolución de la métrica a lo largo del tiempo.").build(),
                OpcionCatalogoDto.builder().codigo(TipoResultadoWidget.COMPARATIVA.name()).nombre("Comparativa")
                        .descripcion("Compara el valor de la métrica agrupado por un campo.").build());
    }

    private List<OpcionCatalogoDto> construirGranularidades() {
        return List.of(
                OpcionCatalogoDto.builder().codigo(Granularidad.MES.name()).nombre("Mes").descripcion(null).build(),
                OpcionCatalogoDto.builder().codigo(Granularidad.TRIMESTRE.name()).nombre("Trimestre").descripcion(null).build(),
                OpcionCatalogoDto.builder().codigo(Granularidad.ANIO.name()).nombre("Año").descripcion(null).build());
    }

    private List<OpcionCatalogoDto> construirTiposDato() {
        return List.of(
                OpcionCatalogoDto.builder().codigo(TipoDatoExcel.TEXTO.name()).nombre("Texto").descripcion(null).build(),
                OpcionCatalogoDto.builder().codigo(TipoDatoExcel.ENTERO.name()).nombre("Entero").descripcion(null).build(),
                OpcionCatalogoDto.builder().codigo(TipoDatoExcel.DECIMAL.name()).nombre("Decimal").descripcion(null).build(),
                OpcionCatalogoDto.builder().codigo(TipoDatoExcel.FECHA.name()).nombre("Fecha").descripcion(null).build(),
                OpcionCatalogoDto.builder().codigo(TipoDatoExcel.BOOLEANO.name()).nombre("Booleano").descripcion(null).build());
    }

    private List<OpcionCatalogoDto> construirPoliticasCampoFaltante() {
        return List.of(
                OpcionCatalogoDto.builder().codigo(PoliticaCampoFaltante.ERROR.name()).nombre("Error")
                        .descripcion("Bloquea la importación si falta el valor.").build(),
                OpcionCatalogoDto.builder().codigo(PoliticaCampoFaltante.ADVERTENCIA.name()).nombre("Advertencia")
                        .descripcion("Permite continuar la importación pero registra un aviso.").build(),
                OpcionCatalogoDto.builder().codigo(PoliticaCampoFaltante.IGNORAR.name()).nombre("Ignorar")
                        .descripcion("Omite el campo sin registrar ningún aviso.").build(),
                OpcionCatalogoDto.builder().codigo(PoliticaCampoFaltante.NULO.name()).nombre("Nulo")
                        .descripcion("Asigna valor nulo sin bloquear la importación ni avisar.").build());
    }

    private ReglasCompatibilidadDto construirReglasCompatibilidad() {
        return ReglasCompatibilidadDto.builder()
                .resolucionTipoResultadoWidget(List.of(
                        resolucion(TipoVisualizacion.KPI, TipoResultadoWidget.ACTUAL, TipoResultadoWidget.ACTUAL, TipoResultadoWidget.ACTUAL),
                        resolucion(TipoVisualizacion.TARJETA, TipoResultadoWidget.ACTUAL, TipoResultadoWidget.ACTUAL, TipoResultadoWidget.ACTUAL),
                        resolucion(TipoVisualizacion.LINEAS, TipoResultadoWidget.ACTUAL, TipoResultadoWidget.SERIE_TEMPORAL, TipoResultadoWidget.SERIE_TEMPORAL),
                        resolucion(TipoVisualizacion.BARRAS, TipoResultadoWidget.ACTUAL, TipoResultadoWidget.COMPARATIVA, TipoResultadoWidget.ACTUAL),
                        resolucion(TipoVisualizacion.TABLA, TipoResultadoWidget.ACTUAL, TipoResultadoWidget.COMPARATIVA, TipoResultadoWidget.ACTUAL),
                        resolucion(TipoVisualizacion.DONUT, TipoResultadoWidget.ACTUAL, TipoResultadoWidget.COMPARATIVA, TipoResultadoWidget.ACTUAL),
                        resolucion(TipoVisualizacion.PIE, TipoResultadoWidget.ACTUAL, TipoResultadoWidget.COMPARATIVA, TipoResultadoWidget.ACTUAL)))
                .build();
    }

    private ResolucionTipoResultadoDto resolucion(
            TipoVisualizacion tipoVisualizacion,
            TipoResultadoWidget siDistribucion,
            TipoResultadoWidget conAgrupacion,
            TipoResultadoWidget sinAgrupacion) {
        return ResolucionTipoResultadoDto.builder()
                .tipoVisualizacion(tipoVisualizacion.name())
                .tipoResultadoSiDistribucion(siDistribucion.name())
                .tipoResultadoConAgrupacion(conAgrupacion.name())
                .tipoResultadoSinAgrupacion(sinAgrupacion.name())
                .build();
    }
}
