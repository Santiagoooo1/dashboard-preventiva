package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.BloqueInformeRequestDto;
import com.preventiva.backend.dto.BloqueInformeResponseDto;
import com.preventiva.backend.dto.ComparacionInteranualRequestDto;
import com.preventiva.backend.dto.ComparacionInteranualResponseDto;
import com.preventiva.backend.dto.DashboardPanelRequestDto;
import com.preventiva.backend.dto.DashboardWidgetDto;
import com.preventiva.backend.dto.InformeClinicoRequestDto;
import com.preventiva.backend.dto.InformeClinicoResponseDto;
import com.preventiva.backend.dto.PaginaInformeResponseDto;
import com.preventiva.backend.entity.BloqueInforme;
import com.preventiva.backend.entity.InformeClinico;
import com.preventiva.backend.entity.MetricaClinica;
import com.preventiva.backend.entity.PaginaInforme;
import com.preventiva.backend.entity.PanelMetrica;
import com.preventiva.backend.enums.OrientacionPagina;
import com.preventiva.backend.enums.TipoBloqueInforme;
import com.preventiva.backend.enums.TipoVisualizacion;
import com.preventiva.backend.repository.BloqueInformeRepository;
import com.preventiva.backend.repository.InformeClinicoRepository;
import com.preventiva.backend.repository.MetricaClinicaRepository;
import com.preventiva.backend.repository.PaginaInformeRepository;
import com.preventiva.backend.service.interfaces.ComparacionInteranualService;
import com.preventiva.backend.service.interfaces.DashboardPanelService;
import com.preventiva.backend.service.interfaces.InformeClinicoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Informes clínicos: montar un documento con lo que ya se calcula en otro sitio
 * (Fase 6.9Q).
 *
 * <p>Un bloque analítico no guarda su resultado, guarda a qué apunta. Copiar
 * «tasa = 3,23 %» dentro del informe lo convertiría en una foto que envejece sin
 * avisar: se abriría meses después con la cifra del día en que se montó. Lo que
 * se congela es el PDF, no la plantilla.
 *
 * <p>Los KPI, gráficas y tablas se resuelven con
 * {@link DashboardPanelService#calcularWidget}, y las comparaciones con
 * {@link ComparacionInteranualService}. No hay una segunda ruta analítica: un
 * informe y un dashboard que muestren lo mismo no pueden discrepar.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InformeClinicoServiceImpl implements InformeClinicoService {

    private static final int ANCHO_MAXIMO = 12;

    private final InformeClinicoRepository informeRepository;
    private final PaginaInformeRepository paginaRepository;
    private final BloqueInformeRepository bloqueRepository;
    private final MetricaClinicaRepository metricaRepository;
    private final DashboardPanelService dashboardPanelService;
    private final ComparacionInteranualService comparacionService;

    // ------------------------------------------------------------------
    // Informe
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<InformeClinicoResponseDto> listar() {
        return informeRepository.findByActivoTrueOrderByActualizadoEnDesc().stream()
                .map(this::mapResumen)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InformeClinicoResponseDto obtenerEstructura(Long id) {
        return mapCompleto(obtenerOLanzar(id), false);
    }

    @Override
    @Transactional(readOnly = true)
    public InformeClinicoResponseDto obtenerConResultados(Long id) {
        return mapCompleto(obtenerOLanzar(id), true);
    }

    @Override
    @Transactional
    public InformeClinicoResponseDto crear(InformeClinicoRequestDto request) {
        LocalDateTime ahora = LocalDateTime.now();
        String visible = tituloVisible(request.getNombre(), request.getTitulo());
        InformeClinico informe = InformeClinico.builder()
                .nombre(visible)
                .titulo(visible)
                .descripcion(request.getDescripcion())
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .activo(true)
                .build();
        informe = informeRepository.save(informe);

        // Un informe nuevo nace con una hoja: una lista de páginas vacía no es un
        // punto de partida, es una pantalla en la que no se puede hacer nada.
        crearPagina(informe, 0, OrientacionPagina.VERTICAL);

        return mapCompleto(informeRepository.findById(informe.getId()).orElseThrow(), false);
    }

    @Override
    @Transactional
    public InformeClinicoResponseDto actualizar(Long id, InformeClinicoRequestDto request) {
        InformeClinico informe = obtenerOLanzar(id);
        // Las dos columnas se escriben siempre con el mismo texto: si se
        // permitiera guardar nombre «Informe A» y título «Informe B», volvería
        // la ambigüedad que esta fase quita de la interfaz.
        String visible = tituloVisible(request.getNombre(), request.getTitulo());
        informe.setNombre(visible);
        informe.setTitulo(visible);
        informe.setDescripcion(request.getDescripcion());
        tocar(informe);
        return mapCompleto(informeRepository.save(informe), false);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        InformeClinico informe = obtenerOLanzar(id);
        // Borrado lógico, como el resto de entidades del proyecto: un informe
        // puede haberse compartido y su rastro sigue siendo útil.
        informe.setActivo(false);
        tocar(informe);
        informeRepository.save(informe);
    }

    // ------------------------------------------------------------------
    // Páginas
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public PaginaInformeResponseDto anadirPagina(Long informeId) {
        InformeClinico informe = obtenerOLanzar(informeId);
        int siguiente = paginaRepository.findByInformeIdOrderByOrdenAscIdAsc(informeId).size();
        PaginaInforme pagina = crearPagina(informe, siguiente, OrientacionPagina.VERTICAL);
        tocar(informe);
        return mapPagina(pagina, false);
    }

    @Override
    @Transactional
    public PaginaInformeResponseDto duplicarPagina(Long informeId, Long paginaId) {
        InformeClinico informe = obtenerOLanzar(informeId);
        PaginaInforme original = obtenerPaginaOLanzar(informeId, paginaId);

        PaginaInforme copia = crearPagina(informe, original.getOrden() + 1, original.getOrientacion());
        for (BloqueInforme bloque : bloqueRepository.findByPaginaIdOrderByOrdenAscIdAsc(paginaId)) {
            // Se copian las referencias, no los resultados: la página duplicada
            // apunta a las mismas métricas y se recalcula igual que la original.
            bloqueRepository.save(BloqueInforme.builder()
                    .pagina(copia)
                    .tipoBloque(bloque.getTipoBloque())
                    .orden(bloque.getOrden())
                    .ancho(bloque.getAncho())
                    .metrica(bloque.getMetrica())
                    .tipoVisualizacion(bloque.getTipoVisualizacion())
                    .tipoResultadoWidget(bloque.getTipoResultadoWidget())
                    .configuracionWidget(bloque.getConfiguracionWidget())
                    .configuracionComparacion(bloque.getConfiguracionComparacion())
                    .contenidoTexto(bloque.getContenidoTexto())
                    .tituloPersonalizado(bloque.getTituloPersonalizado())
                    .build());
        }

        renumerarPaginas(informeId);
        tocar(informe);
        return mapPagina(paginaRepository.findById(copia.getId()).orElseThrow(), false);
    }

    @Override
    @Transactional
    public void eliminarPagina(Long informeId, Long paginaId) {
        InformeClinico informe = obtenerOLanzar(informeId);
        PaginaInforme pagina = obtenerPaginaOLanzar(informeId, paginaId);

        if (paginaRepository.findByInformeIdOrderByOrdenAscIdAsc(informeId).size() <= 1) {
            throw new IllegalArgumentException(
                    "Un informe necesita al menos una página. Vacíala en vez de eliminarla.");
        }

        bloqueRepository.deleteAll(bloqueRepository.findByPaginaIdOrderByOrdenAscIdAsc(paginaId));
        paginaRepository.delete(pagina);
        renumerarPaginas(informeId);
        tocar(informe);
    }

    @Override
    @Transactional
    public InformeClinicoResponseDto moverPagina(Long informeId, Long paginaId, int nuevaPosicion) {
        InformeClinico informe = obtenerOLanzar(informeId);
        List<PaginaInforme> paginas = paginaRepository.findByInformeIdOrderByOrdenAscIdAsc(informeId);
        PaginaInforme pagina = obtenerPaginaOLanzar(informeId, paginaId);

        paginas.remove(pagina);
        paginas.add(Math.max(0, Math.min(nuevaPosicion, paginas.size())), pagina);
        for (int i = 0; i < paginas.size(); i++) {
            paginas.get(i).setOrden(i);
        }
        paginaRepository.saveAll(paginas);
        tocar(informe);

        return mapCompleto(informeRepository.findById(informeId).orElseThrow(), false);
    }

    // ------------------------------------------------------------------
    // Bloques
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public BloqueInformeResponseDto anadirBloque(
            Long informeId, Long paginaId, BloqueInformeRequestDto request) {
        InformeClinico informe = obtenerOLanzar(informeId);
        PaginaInforme pagina = obtenerPaginaOLanzar(informeId, paginaId);
        validar(request);

        int siguiente = bloqueRepository.findByPaginaIdOrderByOrdenAscIdAsc(paginaId).size();
        BloqueInforme bloque = BloqueInforme.builder()
                .pagina(pagina)
                .tipoBloque(request.getTipoBloque())
                .orden(request.getOrden() != null ? request.getOrden() : siguiente)
                .ancho(anchoValido(request))
                .metrica(resolverMetrica(request))
                .tipoVisualizacion(visualizacionPara(request))
                .tipoResultadoWidget(request.getTipoResultadoWidget())
                .configuracionWidget(request.getConfiguracionWidget())
                .configuracionComparacion(request.getConfiguracionComparacion())
                .contenidoTexto(request.getContenidoTexto())
                .tituloPersonalizado(request.getTituloPersonalizado())
                .build();

        bloque = bloqueRepository.save(bloque);
        tocar(informe);
        return mapBloque(bloque, false, new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    @Override
    @Transactional
    public BloqueInformeResponseDto actualizarBloque(
            Long informeId, Long paginaId, Long bloqueId, BloqueInformeRequestDto request) {
        InformeClinico informe = obtenerOLanzar(informeId);
        BloqueInforme bloque = obtenerBloqueOLanzar(informeId, paginaId, bloqueId);
        validar(request);

        bloque.setTipoBloque(request.getTipoBloque());
        bloque.setAncho(anchoValido(request));
        bloque.setMetrica(resolverMetrica(request));
        bloque.setTipoVisualizacion(visualizacionPara(request));
        bloque.setTipoResultadoWidget(request.getTipoResultadoWidget());
        bloque.setConfiguracionWidget(request.getConfiguracionWidget());
        bloque.setConfiguracionComparacion(request.getConfiguracionComparacion());
        bloque.setContenidoTexto(request.getContenidoTexto());
        bloque.setTituloPersonalizado(request.getTituloPersonalizado());
        if (request.getOrden() != null) {
            bloque.setOrden(request.getOrden());
        }

        bloque = bloqueRepository.save(bloque);
        tocar(informe);
        return mapBloque(bloque, false, new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    @Override
    @Transactional
    public void eliminarBloque(Long informeId, Long paginaId, Long bloqueId) {
        InformeClinico informe = obtenerOLanzar(informeId);
        BloqueInforme bloque = obtenerBloqueOLanzar(informeId, paginaId, bloqueId);
        bloqueRepository.delete(bloque);
        renumerarBloques(paginaId);
        tocar(informe);
    }

    @Override
    @Transactional
    public PaginaInformeResponseDto moverBloque(
            Long informeId, Long paginaId, Long bloqueId, int nuevaPosicion) {
        InformeClinico informe = obtenerOLanzar(informeId);
        BloqueInforme bloque = obtenerBloqueOLanzar(informeId, paginaId, bloqueId);

        List<BloqueInforme> bloques = bloqueRepository.findByPaginaIdOrderByOrdenAscIdAsc(paginaId);
        bloques.removeIf(b -> b.getId().equals(bloque.getId()));
        bloques.add(Math.max(0, Math.min(nuevaPosicion, bloques.size())), bloque);
        for (int i = 0; i < bloques.size(); i++) {
            bloques.get(i).setOrden(i);
        }
        bloqueRepository.saveAll(bloques);
        tocar(informe);

        return mapPagina(paginaRepository.findById(paginaId).orElseThrow(), false);
    }

    // ------------------------------------------------------------------
    // Validación
    // ------------------------------------------------------------------

    /**
     * Cada tipo de bloque exige lo suyo. Se comprueba al guardar y no al pintar:
     * un bloque a medias guardado reaparece roto cada vez que se abre el informe.
     */
    private void validar(BloqueInformeRequestDto request) {
        TipoBloqueInforme tipo = request.getTipoBloque();

        switch (tipo) {
            case KPI, GRAFICA, TABLA -> {
                if (request.getMetricaId() == null) {
                    throw new IllegalArgumentException(
                            "Un bloque de tipo " + tipo + " necesita el indicador del que sale el dato.");
                }
            }
            case COMPARACION_INTERANUAL -> {
                ComparacionInteranualRequestDto config = request.getConfiguracionComparacion();
                if (config == null || config.getDatasetIds() == null || config.getDatasetIds().isEmpty()
                        || config.getCodigoCanonico() == null || config.getCodigoCanonico().isBlank()) {
                    throw new IllegalArgumentException(
                            "Una comparación necesita al menos un dataset y el concepto a comparar.");
                }
            }
            case TITULO, SUBTITULO, TEXTO -> {
                if (request.getContenidoTexto() == null || request.getContenidoTexto().isBlank()) {
                    throw new IllegalArgumentException("Escribe el texto del bloque.");
                }
            }
            case SEPARADOR, SALTO_PAGINA -> {
                // No necesitan nada más.
            }
        }
    }

    private MetricaClinica resolverMetrica(BloqueInformeRequestDto request) {
        if (request.getMetricaId() == null) {
            return null;
        }
        return metricaRepository.findById(request.getMetricaId()).orElseThrow(
                () -> new NoSuchElementException(
                        "No existe el indicador con id: " + request.getMetricaId()));
    }

    private int anchoValido(BloqueInformeRequestDto request) {
        if (request.getAncho() == null) {
            // Los KPI caben cuatro por fila; el resto ocupa la fila entera.
            return request.getTipoBloque() == TipoBloqueInforme.KPI ? 3 : ANCHO_MAXIMO;
        }
        return Math.max(1, Math.min(ANCHO_MAXIMO, request.getAncho()));
    }

    private TipoVisualizacion visualizacionPara(BloqueInformeRequestDto request) {
        if (request.getTipoVisualizacion() != null) {
            return request.getTipoVisualizacion();
        }
        return switch (request.getTipoBloque()) {
            case KPI -> TipoVisualizacion.KPI;
            case TABLA -> TipoVisualizacion.TABLA;
            case GRAFICA -> TipoVisualizacion.BARRAS;
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // Mapeo y cálculo
    // ------------------------------------------------------------------

    /**
     * El título que se enseña, con una prioridad estable: {@code titulo} si
     * tiene contenido y, si no, {@code nombre}.
     *
     * <p>Los informes creados antes de la 6.9Q.2 pueden traer los dos campos
     * distintos, o el título vacío. Siguen abriéndose: aquí no se corrige nada,
     * solo se elige cuál mostrar. La unificación ocurre al guardar.
     */
    private String tituloVisible(InformeClinico informe) {
        return tituloVisible(informe.getNombre(), informe.getTitulo());
    }

    private String tituloVisible(String nombre, String titulo) {
        if (titulo != null && !titulo.isBlank()) {
            return titulo.trim();
        }
        return nombre != null ? nombre.trim() : null;
    }

    private InformeClinicoResponseDto mapResumen(InformeClinico informe) {
        return InformeClinicoResponseDto.builder()
                .id(informe.getId())
                .tituloVisible(tituloVisible(informe))
                .nombre(informe.getNombre())
                .titulo(informe.getTitulo())
                .descripcion(informe.getDescripcion())
                .creadoEn(informe.getCreadoEn())
                .actualizadoEn(informe.getActualizadoEn())
                .totalPaginas(paginaRepository.findByInformeIdOrderByOrdenAscIdAsc(informe.getId()).size())
                .build();
    }

    private InformeClinicoResponseDto mapCompleto(InformeClinico informe, boolean conResultados) {
        // Cachés de la petición: dos bloques que apunten al mismo indicador o a
        // la misma comparación comparten resultado en vez de calcularlo dos
        // veces. Viven solo durante esta llamada.
        Map<String, DashboardWidgetDto> widgets = new LinkedHashMap<>();
        Map<String, ComparacionInteranualResponseDto> comparaciones = new LinkedHashMap<>();
        Set<String> datasets = new LinkedHashSet<>();

        List<PaginaInformeResponseDto> paginas = new ArrayList<>();
        for (PaginaInforme pagina : paginaRepository.findByInformeIdOrderByOrdenAscIdAsc(informe.getId())) {
            List<BloqueInformeResponseDto> bloques = new ArrayList<>();
            for (BloqueInforme bloque : bloqueRepository.findByPaginaIdOrderByOrdenAscIdAsc(pagina.getId())) {
                BloqueInformeResponseDto dto = mapBloque(bloque, conResultados, widgets, comparaciones);
                if (dto.getDatasetNombre() != null) {
                    datasets.add(dto.getDatasetNombre());
                }
                bloques.add(dto);
            }
            paginas.add(PaginaInformeResponseDto.builder()
                    .id(pagina.getId())
                    .orden(pagina.getOrden())
                    .orientacion(pagina.getOrientacion().name())
                    .bloques(bloques)
                    .build());
        }

        return InformeClinicoResponseDto.builder()
                .id(informe.getId())
                .tituloVisible(tituloVisible(informe))
                .nombre(informe.getNombre())
                .titulo(informe.getTitulo())
                .descripcion(informe.getDescripcion())
                .creadoEn(informe.getCreadoEn())
                .actualizadoEn(informe.getActualizadoEn())
                .totalPaginas(paginas.size())
                .paginas(paginas)
                .generadoEn(LocalDateTime.now())
                .datasetsUtilizados(List.copyOf(datasets))
                .build();
    }

    private PaginaInformeResponseDto mapPagina(PaginaInforme pagina, boolean conResultados) {
        Map<String, DashboardWidgetDto> widgets = new LinkedHashMap<>();
        Map<String, ComparacionInteranualResponseDto> comparaciones = new LinkedHashMap<>();
        return PaginaInformeResponseDto.builder()
                .id(pagina.getId())
                .orden(pagina.getOrden())
                .orientacion(pagina.getOrientacion().name())
                .bloques(bloqueRepository.findByPaginaIdOrderByOrdenAscIdAsc(pagina.getId()).stream()
                        .map(b -> mapBloque(b, conResultados, widgets, comparaciones))
                        .toList())
                .build();
    }

    private BloqueInformeResponseDto mapBloque(
            BloqueInforme bloque, boolean conResultados,
            Map<String, DashboardWidgetDto> cacheWidgets,
            Map<String, ComparacionInteranualResponseDto> cacheComparaciones) {

        BloqueInformeResponseDto.BloqueInformeResponseDtoBuilder builder =
                BloqueInformeResponseDto.builder()
                        .id(bloque.getId())
                        .tipoBloque(bloque.getTipoBloque().name())
                        .orden(bloque.getOrden())
                        .ancho(bloque.getAncho())
                        .tipoVisualizacion(nombreDe(bloque.getTipoVisualizacion()))
                        .tipoResultadoWidget(nombreDe(bloque.getTipoResultadoWidget()))
                        .configuracionWidget(bloque.getConfiguracionWidget())
                        .configuracionComparacion(bloque.getConfiguracionComparacion())
                        .contenidoTexto(bloque.getContenidoTexto())
                        .tituloPersonalizado(bloque.getTituloPersonalizado())
                        .disponible(true);

        MetricaClinica metrica = leerMetrica(bloque);
        if (metrica != null) {
            builder.metricaId(metrica.getId())
                    .datasetId(metrica.getDataset().getId())
                    .datasetNombre(metrica.getDataset().getNombre());
            if (bloque.getTituloPersonalizado() == null) {
                builder.tituloPersonalizado(metrica.getNombre());
            }
        }

        boolean necesitaMetrica = switch (bloque.getTipoBloque()) {
            case KPI, GRAFICA, TABLA -> true;
            default -> false;
        };

        // Una métrica borrada no puede tumbar el informe entero: el bloque llega
        // marcado y el usuario decide si lo quita o lo reemplaza.
        if (necesitaMetrica && metrica == null) {
            return builder
                    .disponible(false)
                    .motivoNoDisponible("El indicador de este bloque ya no está disponible.")
                    .build();
        }

        if (!conResultados) {
            return builder.build();
        }

        try {
            if (necesitaMetrica) {
                builder.widget(widgetDe(bloque, metrica, cacheWidgets));
            } else if (bloque.getTipoBloque() == TipoBloqueInforme.COMPARACION_INTERANUAL) {
                builder.comparacion(comparacionDe(bloque, cacheComparaciones));
            }
        } catch (Exception e) {
            // Igual que en el dashboard: el bloque que falla lo dice, y los
            // demás siguen viéndose.
            log.warn("No se pudo calcular el bloque {} del informe: {}", bloque.getId(), e.getMessage());
            builder.disponible(false)
                    .motivoNoDisponible("Este elemento no se ha podido calcular: " + e.getMessage());
        }

        return builder.build();
    }

    /**
     * Resuelve el widget montando un {@link PanelMetrica} transitorio: el bloque
     * describe exactamente lo mismo que un widget de panel, así que puede pasar
     * por el motor del dashboard sin traducir nada.
     */
    private DashboardWidgetDto widgetDe(
            BloqueInforme bloque, MetricaClinica metrica, Map<String, DashboardWidgetDto> cache) {

        String clave = metrica.getId() + "|" + bloque.getTipoVisualizacion() + "|"
                + bloque.getTipoResultadoWidget() + "|" + bloque.getConfiguracionWidget();

        return cache.computeIfAbsent(clave, k -> {
            PanelMetrica transitorio = PanelMetrica.builder()
                    .metrica(metrica)
                    .tituloPersonalizado(bloque.getTituloPersonalizado())
                    .tipoVisualizacion(bloque.getTipoVisualizacion() != null
                            ? bloque.getTipoVisualizacion()
                            : TipoVisualizacion.KPI)
                    .tipoResultadoWidget(bloque.getTipoResultadoWidget())
                    .configuracionWidget(bloque.getConfiguracionWidget())
                    .orden(bloque.getOrden())
                    .ancho(bloque.getAncho())
                    .activa(true)
                    .build();
            return dashboardPanelService.calcularWidget(transitorio, new DashboardPanelRequestDto());
        });
    }

    private ComparacionInteranualResponseDto comparacionDe(
            BloqueInforme bloque, Map<String, ComparacionInteranualResponseDto> cache) {
        ComparacionInteranualRequestDto config = bloque.getConfiguracionComparacion();
        String clave = config.getDatasetIds() + "|" + config.getCodigoCanonico() + "|"
                + config.getTipoComparacion() + "|" + config.getGranularidad();
        return cache.computeIfAbsent(clave, k -> comparacionService.comparar(config));
    }

    /** Lee la métrica sin reventar si la fila ya no existe (referencia rota). */
    private MetricaClinica leerMetrica(BloqueInforme bloque) {
        try {
            MetricaClinica metrica = bloque.getMetrica();
            if (metrica == null) {
                return null;
            }
            // Fuerza la carga del proxy: si la fila desapareció, se ve aquí.
            metrica.getNombre();
            return metrica;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Ayudantes
    // ------------------------------------------------------------------

    private PaginaInforme crearPagina(InformeClinico informe, int orden, OrientacionPagina orientacion) {
        return paginaRepository.save(PaginaInforme.builder()
                .informe(informe)
                .orden(orden)
                .orientacion(orientacion)
                .build());
    }

    private void renumerarPaginas(Long informeId) {
        List<PaginaInforme> paginas = paginaRepository.findByInformeIdOrderByOrdenAscIdAsc(informeId);
        for (int i = 0; i < paginas.size(); i++) {
            paginas.get(i).setOrden(i);
        }
        paginaRepository.saveAll(paginas);
    }

    private void renumerarBloques(Long paginaId) {
        List<BloqueInforme> bloques = bloqueRepository.findByPaginaIdOrderByOrdenAscIdAsc(paginaId);
        for (int i = 0; i < bloques.size(); i++) {
            bloques.get(i).setOrden(i);
        }
        bloqueRepository.saveAll(bloques);
    }

    private void tocar(InformeClinico informe) {
        informe.setActualizadoEn(LocalDateTime.now());
        informeRepository.save(informe);
    }

    private InformeClinico obtenerOLanzar(Long id) {
        InformeClinico informe = informeRepository.findById(id).orElseThrow(
                () -> new NoSuchElementException("No existe el informe con id: " + id));
        if (!Boolean.TRUE.equals(informe.getActivo())) {
            throw new NoSuchElementException("Este informe se ha eliminado.");
        }
        return informe;
    }

    private PaginaInforme obtenerPaginaOLanzar(Long informeId, Long paginaId) {
        PaginaInforme pagina = paginaRepository.findById(paginaId).orElseThrow(
                () -> new NoSuchElementException("No existe la página con id: " + paginaId));
        if (!pagina.getInforme().getId().equals(informeId)) {
            throw new IllegalArgumentException("Esa página no pertenece a este informe.");
        }
        return pagina;
    }

    private BloqueInforme obtenerBloqueOLanzar(Long informeId, Long paginaId, Long bloqueId) {
        BloqueInforme bloque = bloqueRepository.findById(bloqueId).orElseThrow(
                () -> new NoSuchElementException("No existe el bloque con id: " + bloqueId));
        if (!bloque.getPagina().getId().equals(paginaId)
                || !bloque.getPagina().getInforme().getId().equals(informeId)) {
            throw new IllegalArgumentException("Ese bloque no pertenece a esta página.");
        }
        return bloque;
    }

    private String nombreDe(Enum<?> valor) {
        return valor == null ? null : valor.name();
    }
}
