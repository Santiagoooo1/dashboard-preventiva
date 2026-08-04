package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.CategoriaPerfilDto;
import com.preventiva.backend.dto.ColumnaSubconjuntoDto;
import com.preventiva.backend.dto.FilaSubconjuntoDto;
import com.preventiva.backend.dto.FiltroMetricaDto;
import com.preventiva.backend.dto.PerfilCategoricoDto;
import com.preventiva.backend.dto.PerfilFechaDto;
import com.preventiva.backend.dto.PerfilNumericoDto;
import com.preventiva.backend.dto.SubconjuntoPaginaDto;
import com.preventiva.backend.dto.SubconjuntoPerfilDto;
import com.preventiva.backend.dto.SubconjuntoRequestDto;
import com.preventiva.backend.dto.SubconjuntoResumenDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.entity.PanelClinico;
import com.preventiva.backend.entity.RegistroClinicoGenerico;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.repository.CampoClinicoRepository;
import com.preventiva.backend.repository.PanelClinicoRepository;
import com.preventiva.backend.repository.SubconjuntoConsultaRepository;
import com.preventiva.backend.service.interfaces.SubconjuntoClinicoService;
import com.preventiva.backend.util.CampoIndividuoResolver;
import com.preventiva.backend.util.FiltroSqlBuilder;
import com.preventiva.backend.util.RegistroClinicoGenericoValueReader;
import com.preventiva.backend.util.TextNormalizer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Detalle del subconjunto. Filtros, orden, paginación, COUNT, COUNT DISTINCT y
 * agregados del perfil se resuelven EN BASE DE DATOS mediante
 * {@link SubconjuntoConsultaRepository}: solo se materializan en memoria las
 * filas de la página que se está pintando.
 */
@Service
@RequiredArgsConstructor
public class SubconjuntoClinicoServiceImpl implements SubconjuntoClinicoService {

    private static final int TAMANO_POR_DEFECTO = 25;
    /** Tope duro: evita que una petición manipulada arrastre el dataset entero. */
    private static final int TAMANO_MAXIMO = 200;
    private static final int MAX_CATEGORIAS_PERFIL = 8;
    /** Un campo con demasiados valores distintos (HC, texto libre) no describe al grupo. */
    private static final int MAX_CARDINALIDAD_PERFIL = 25;
    private static final int MAX_CAMPOS_PERFIL = 10;

    private static final List<String> PRIORIDAD_COLUMNAS = List.of(
            "fechaEvento", "sexo", "edad", "asa", "procedimiento",
            "infeccionLocalizacionQuirurgica", "cie10", "servicio");

    private final PanelClinicoRepository panelClinicoRepository;
    private final CampoClinicoRepository campoClinicoRepository;
    private final SubconjuntoConsultaRepository consultaRepository;

    // ------------------------------------------------------------------
    // Vistas
    // ------------------------------------------------------------------

    @Override
    public SubconjuntoResumenDto resumen(Long panelId, SubconjuntoRequestDto request) {
        Contexto ctx = preparar(panelId, request);

        long totalRegistros = consultaRepository.contarRegistros(ctx.datasetId, ctx.where);
        Long pacientes = ctx.campoIndividuo != null
                ? consultaRepository.contarIndividuosDistintos(ctx.datasetId, ctx.where, ctx.campoIndividuo)
                : null;

        Object[] rango = consultaRepository.rangoFechaEvento(ctx.datasetId, ctx.where);

        return SubconjuntoResumenDto.builder()
                .totalRegistros(totalRegistros)
                .totalPacientesUnicos(pacientes)
                .campoIndividuo(ctx.campoIndividuo != null ? ctx.campoIndividuo.getCodigo() : null)
                .etiquetaCampoIndividuo(ctx.campoIndividuo != null ? ctx.campoIndividuo.getEtiqueta() : null)
                .tienePacientes(ctx.campoIndividuo != null)
                .periodoDesde(aLocalDate(rango[0]))
                .periodoHasta(aLocalDate(rango[1]))
                .build();
    }

    @Override
    public SubconjuntoPaginaDto registros(Long panelId, SubconjuntoRequestDto request) {
        Contexto ctx = preparar(panelId, request);
        List<ColumnaSubconjuntoDto> columnas = columnasDe(ctx, true);

        long total = consultaRepository.contarRegistrosConBusqueda(
                ctx.datasetId, ctx.where, ctx.campoIndividuo, ctx.busqueda);

        int tamano = tamanoDe(request);
        int pagina = paginaDe(request);

        CampoClinico campoOrden = request != null && request.getOrdenCampo() != null
                ? ctx.camposPorCodigo.get(request.getOrdenCampo()) : null;
        boolean desc = request != null && "DESC".equalsIgnoreCase(request.getOrdenDireccion());

        // Solo llegan a memoria los IDs de ESTA página, y luego sus filas.
        List<Long> ids = consultaRepository.idsPaginaRegistros(
                ctx.datasetId, ctx.where, campoOrden, desc, ctx.campoIndividuo, ctx.busqueda,
                pagina * tamano, tamano);

        Map<Long, RegistroClinicoGenerico> porId = consultaRepository.cargarPorIds(ids);

        List<FilaSubconjuntoDto> filas = ids.stream()
                .map(porId::get)
                .filter(Objects::nonNull)
                .map(r -> FilaSubconjuntoDto.builder()
                        .clave(String.valueOf(r.getId()))
                        .valores(valoresDe(r, ctx, columnas))
                        .build())
                .toList();

        return construirPagina(filas, total, pagina, tamano, columnas);
    }

    @Override
    public SubconjuntoPaginaDto pacientes(Long panelId, SubconjuntoRequestDto request) {
        Contexto ctx = preparar(panelId, request);

        if (ctx.campoIndividuo == null) {
            throw new IllegalArgumentException("Este conjunto de datos no tiene un identificador individual disponible.");
        }

        List<ColumnaSubconjuntoDto> columnas = columnasDe(ctx, false);

        long total = consultaRepository.contarIndividuosConBusqueda(
                ctx.datasetId, ctx.where, ctx.campoIndividuo, ctx.busqueda);

        int tamano = tamanoDe(request);
        int pagina = paginaDe(request);

        // GROUP BY + LIMIT/OFFSET: solo los identificadores de esta página.
        List<String> identificadores = consultaRepository.paginaIndividuos(
                ctx.datasetId, ctx.where, ctx.campoIndividuo, ctx.busqueda, pagina * tamano, tamano);

        // Y solo los registros de esos pacientes, para poder agregarlos.
        Map<String, List<RegistroClinicoGenerico>> porIndividuo = new LinkedHashMap<>();
        for (String id : identificadores) porIndividuo.put(id, new ArrayList<>());

        for (RegistroClinicoGenerico r : consultaRepository.registrosDeIndividuos(
                ctx.datasetId, ctx.where, ctx.campoIndividuo, identificadores)) {
            String id = textoDe(r, ctx.campoIndividuo);
            List<RegistroClinicoGenerico> lista = id != null ? porIndividuo.get(id) : null;
            if (lista != null) lista.add(r);
        }

        List<FilaSubconjuntoDto> filas = identificadores.stream()
                .map(id -> filaPaciente(id, porIndividuo.getOrDefault(id, List.of()), ctx, columnas))
                .toList();

        return construirPagina(filas, total, pagina, tamano, columnas);
    }

    @Override
    public SubconjuntoPerfilDto perfil(Long panelId, SubconjuntoRequestDto request) {
        Contexto ctx = preparar(panelId, request);

        long totalRegistros = consultaRepository.contarRegistros(ctx.datasetId, ctx.where);
        Long pacientes = ctx.campoIndividuo != null
                ? consultaRepository.contarIndividuosDistintos(ctx.datasetId, ctx.where, ctx.campoIndividuo)
                : null;

        List<PerfilNumericoDto> numericos = new ArrayList<>();
        List<PerfilCategoricoDto> categoricos = new ArrayList<>();
        List<PerfilCategoricoDto> booleanos = new ArrayList<>();
        List<PerfilFechaDto> fechas = new ArrayList<>();

        for (CampoClinico campo : camposPerfil(ctx)) {
            switch (campo.getTipoDato()) {
                case ENTERO, DECIMAL -> numericos.add(perfilNumerico(campo, ctx));
                case FECHA -> fechas.add(perfilFecha(campo, ctx));
                case BOOLEANO -> booleanos.add(perfilCategorico(campo, ctx));
                case TEXTO -> {
                    PerfilCategoricoDto p = perfilCategorico(campo, ctx);
                    if (p.getCategorias().size() + p.getOtrasCategorias() <= MAX_CARDINALIDAD_PERFIL) {
                        categoricos.add(p);
                    }
                }
            }
        }

        return SubconjuntoPerfilDto.builder()
                .baseCalculo("REGISTROS")
                .totalRegistros(totalRegistros)
                .totalPacientesUnicos(pacientes)
                .registrosPorPaciente(
                        pacientes != null && pacientes > 0 ? redondear((double) totalRegistros / pacientes) : null)
                .numericos(numericos)
                .categoricos(categoricos)
                .booleanos(booleanos)
                .fechas(fechas)
                .build();
    }

    // ------------------------------------------------------------------
    // Contexto
    // ------------------------------------------------------------------

    /** Metadata + WHERE ya traducido. NO contiene registros: nada se precarga. */
    private record Contexto(
            Long datasetId,
            Map<String, CampoClinico> camposPorCodigo,
            FiltroSqlBuilder.Where where,
            CampoClinico campoIndividuo,
            String busqueda) {
    }

    private Contexto preparar(Long panelId, SubconjuntoRequestDto request) {
        PanelClinico panel = panelClinicoRepository.findById(panelId)
                .orElseThrow(() -> new NoSuchElementException("No existe el panel con id: " + panelId));
        Long datasetId = panel.getDataset().getId();

        Map<String, CampoClinico> camposPorCodigo = campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId)
                .stream()
                .collect(Collectors.toMap(CampoClinico::getCodigo, c -> c, (a, b) -> a));

        List<FiltroMetricaDto> filtros = request != null && request.getFiltros() != null
                ? request.getFiltros() : List.of();

        // Valida los campos y produce el WHERE parametrizado.
        FiltroSqlBuilder.Where where = FiltroSqlBuilder.construir(filtros, camposPorCodigo);

        // El campo del request es solo una PISTA: el backend resuelve cuál es
        // el identificador admisible del dataset y solo lo acepta si coinciden.
        // Sin esto, pedir `sexo` como campoIndividuo devolvía "2 pacientes
        // únicos" cuando en realidad eran dos sexos.
        CampoClinico individuo = CampoIndividuoResolver
                .resolverConPista(camposPorCodigo.values(), request != null ? request.getCampoIndividuo() : null)
                .orElse(null);

        String busqueda = request != null && request.getBusquedaIndividuo() != null
                && !request.getBusquedaIndividuo().isBlank()
                ? TextNormalizer.normalize(request.getBusquedaIndividuo()) : null;

        return new Contexto(datasetId, camposPorCodigo, where, individuo, busqueda);
    }

    // ------------------------------------------------------------------
    // Columnas
    // ------------------------------------------------------------------

    private List<ColumnaSubconjuntoDto> columnasDe(Contexto ctx, boolean paraRegistros) {
        List<CampoClinico> campos = new ArrayList<>(ctx.camposPorCodigo.values());
        campos.sort(comparadorPrioridad());

        List<ColumnaSubconjuntoDto> columnas = new ArrayList<>();
        int orden = 0;

        if (ctx.campoIndividuo != null) {
            columnas.add(ColumnaSubconjuntoDto.builder()
                    .codigo(ctx.campoIndividuo.getCodigo())
                    .etiqueta(ctx.campoIndividuo.getEtiqueta())
                    .tipoDato(ctx.campoIndividuo.getTipoDato().name())
                    .orden(orden++)
                    .identificador(true)
                    .build());
        }

        for (CampoClinico c : campos) {
            if (ctx.campoIndividuo != null && c.getCodigo().equals(ctx.campoIndividuo.getCodigo())) continue;
            if (!paraRegistros && !agregableEnPacientes(c)) continue;

            columnas.add(ColumnaSubconjuntoDto.builder()
                    .codigo(c.getCodigo())
                    .etiqueta(c.getEtiqueta())
                    .tipoDato(c.getTipoDato().name())
                    .orden(orden++)
                    .identificador(false)
                    .build());
        }

        return columnas;
    }

    private Comparator<CampoClinico> comparadorPrioridad() {
        return Comparator
                .comparingInt((CampoClinico c) -> {
                    int i = PRIORIDAD_COLUMNAS.indexOf(c.getCodigo());
                    return i >= 0 ? i : PRIORIDAD_COLUMNAS.size();
                })
                .thenComparing(c -> c.getOrden() != null ? c.getOrden() : Integer.MAX_VALUE)
                .thenComparing(CampoClinico::getCodigo);
    }

    /** Solo los tipos con una regla de agregación por paciente definida (ver `valorAgregado`). */
    private boolean agregableEnPacientes(CampoClinico campo) {
        return campo.getTipoDato() != null;
    }

    private Map<String, Object> valoresDe(RegistroClinicoGenerico r, Contexto ctx, List<ColumnaSubconjuntoDto> columnas) {
        Map<String, Object> valores = new LinkedHashMap<>();
        for (ColumnaSubconjuntoDto col : columnas) {
            CampoClinico campo = ctx.camposPorCodigo.get(col.getCodigo());
            if (campo == null) continue;
            Object crudo = RegistroClinicoGenericoValueReader.leerValorCrudo(r, campo);
            valores.put(col.getCodigo(), RegistroClinicoGenericoValueReader.coercionar(crudo, campo.getTipoDato()));
        }
        return valores;
    }

    // ------------------------------------------------------------------
    // Agregación por paciente
    // ------------------------------------------------------------------

    private FilaSubconjuntoDto filaPaciente(
            String identificador, List<RegistroClinicoGenerico> registros, Contexto ctx,
            List<ColumnaSubconjuntoDto> columnas) {

        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put(ctx.campoIndividuo.getCodigo(), identificador);

        for (ColumnaSubconjuntoDto col : columnas) {
            if (col.isIdentificador()) continue;
            CampoClinico campo = ctx.camposPorCodigo.get(col.getCodigo());
            if (campo == null) continue;
            valores.put(col.getCodigo(), valorAgregado(campo, registros));
        }

        return FilaSubconjuntoDto.builder()
                .clave(identificador)
                .numeroRegistros(registros.size())
                .valores(valores)
                .build();
    }

    /**
     * Reglas de agregación por paciente, explícitas y sin promedios ocultos:
     *  - FECHA → la ÚLTIMA del subconjunto para ese paciente.
     *  - ENTERO/DECIMAL → valor del registro MÁS RECIENTE (p. ej. la última edad
     *    registrada). No se promedian: la media de las edades de un mismo
     *    paciente no significa nada clínicamente.
     *  - TEXTO/BOOLEANO → el valor si es único; "Varios" si el paciente tiene
     *    valores distintos, para no ocultar la discrepancia.
     */
    private Object valorAgregado(CampoClinico campo, List<RegistroClinicoGenerico> registros) {
        List<Object> valores = registros.stream()
                .map(r -> RegistroClinicoGenericoValueReader.coercionar(
                        RegistroClinicoGenericoValueReader.leerValorCrudo(r, campo), campo.getTipoDato()))
                .filter(Objects::nonNull)
                .toList();

        if (valores.isEmpty()) return null;

        if (campo.getTipoDato() == TipoDatoExcel.FECHA) {
            return valores.stream().map(v -> (LocalDate) v).max(LocalDate::compareTo).orElse(null);
        }

        if (campo.getTipoDato() == TipoDatoExcel.ENTERO || campo.getTipoDato() == TipoDatoExcel.DECIMAL) {
            return valorMasReciente(campo, registros);
        }

        List<Object> distintos = valores.stream().distinct().toList();
        return distintos.size() == 1 ? distintos.get(0) : "Varios";
    }

    private Object valorMasReciente(CampoClinico campo, List<RegistroClinicoGenerico> registros) {
        return registros.stream()
                .sorted(Comparator.comparing(
                        RegistroClinicoGenerico::getFechaEvento,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .map(r -> RegistroClinicoGenericoValueReader.coercionar(
                        RegistroClinicoGenericoValueReader.leerValorCrudo(r, campo), campo.getTipoDato()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    // ------------------------------------------------------------------
    // Paginación
    // ------------------------------------------------------------------

    private int tamanoDe(SubconjuntoRequestDto request) {
        int solicitado = request != null && request.getTamano() != null ? request.getTamano() : TAMANO_POR_DEFECTO;
        return Math.min(Math.max(1, solicitado), TAMANO_MAXIMO);
    }

    private int paginaDe(SubconjuntoRequestDto request) {
        return Math.max(0, request != null && request.getPagina() != null ? request.getPagina() : 0);
    }

    private SubconjuntoPaginaDto construirPagina(
            List<FilaSubconjuntoDto> filas, long total, int pagina, int tamano,
            List<ColumnaSubconjuntoDto> columnas) {

        return SubconjuntoPaginaDto.builder()
                .contenido(filas)
                .pagina(pagina)
                .tamano(tamano)
                .totalElementos(total)
                .totalPaginas(total == 0 ? 0 : (int) Math.ceil((double) total / tamano))
                .columnas(columnas)
                .build();
    }

    // ------------------------------------------------------------------
    // Perfil (agregados en BD)
    // ------------------------------------------------------------------

    private List<CampoClinico> camposPerfil(Contexto ctx) {
        return ctx.camposPorCodigo.values().stream()
                // El identificador describe al individuo, no al grupo.
                .filter(c -> ctx.campoIndividuo == null || !c.getCodigo().equals(ctx.campoIndividuo.getCodigo()))
                .sorted(comparadorPrioridad())
                .limit(MAX_CAMPOS_PERFIL)
                .toList();
    }

    private PerfilNumericoDto perfilNumerico(CampoClinico campo, Contexto ctx) {
        // count, avg, percentile_cont(0.5), min, max, count(*)
        Object[] fila = consultaRepository.estadisticosNumericos(ctx.datasetId, ctx.where, campo);

        long validos = numero(fila[0]) != null ? numero(fila[0]).longValue() : 0;
        long total = numero(fila[5]) != null ? numero(fila[5]).longValue() : 0;

        return PerfilNumericoDto.builder()
                .campo(campo.getCodigo())
                .etiqueta(campo.getEtiqueta())
                .valoresValidos(validos)
                .valoresAusentes(total - validos)
                .media(redondearNullable(fila[1]))
                .mediana(redondearNullable(fila[2]))
                .minimo(redondearNullable(fila[3]))
                .maximo(redondearNullable(fila[4]))
                .build();
    }

    private PerfilCategoricoDto perfilCategorico(CampoClinico campo, Contexto ctx) {
        List<Object[]> filas = consultaRepository.distribucion(ctx.datasetId, ctx.where, campo);

        long ausentes = 0;
        List<CategoriaPerfilDto> conValor = new ArrayList<>();
        long validos = 0;

        for (Object[] f : filas) {
            String valor = f[0] != null ? String.valueOf(f[0]) : null;
            long conteo = numero(f[1]).longValue();
            if (valor == null || valor.isBlank()) {
                ausentes += conteo;
            } else {
                validos += conteo;
                conValor.add(CategoriaPerfilDto.builder().valor(valor).conteo(conteo).build());
            }
        }

        final long denominador = validos;
        List<CategoriaPerfilDto> conPorcentaje = conValor.stream()
                .map(c -> CategoriaPerfilDto.builder()
                        .valor(c.getValor())
                        .conteo(c.getConteo())
                        // Porcentaje sobre valores VÁLIDOS; el denominador viaja
                        // en valoresValidos para que el frontend pueda mostrarlo.
                        .porcentaje(denominador == 0 ? null : redondear(c.getConteo() * 100.0 / denominador))
                        .build())
                .toList();

        List<CategoriaPerfilDto> principales = conPorcentaje.stream().limit(MAX_CATEGORIAS_PERFIL).toList();

        return PerfilCategoricoDto.builder()
                .campo(campo.getCodigo())
                .etiqueta(campo.getEtiqueta())
                .tipoDato(campo.getTipoDato().name())
                .valoresValidos(validos)
                .valoresAusentes(ausentes)
                .categorias(principales)
                .otrasCategorias(conPorcentaje.size() - principales.size())
                .build();
    }

    private PerfilFechaDto perfilFecha(CampoClinico campo, Contexto ctx) {
        Object[] fila = consultaRepository.rangoFechas(ctx.datasetId, ctx.where, campo);

        long validos = numero(fila[0]) != null ? numero(fila[0]).longValue() : 0;
        long total = numero(fila[3]) != null ? numero(fila[3]).longValue() : 0;

        return PerfilFechaDto.builder()
                .campo(campo.getCodigo())
                .etiqueta(campo.getEtiqueta())
                .valoresValidos(validos)
                .valoresAusentes(total - validos)
                .primera(aLocalDate(fila[1]))
                .ultima(aLocalDate(fila[2]))
                .build();
    }

    // ------------------------------------------------------------------

    private String textoDe(RegistroClinicoGenerico r, CampoClinico campo) {
        Object v = RegistroClinicoGenericoValueReader.leerValorCrudo(r, campo);
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }

    private LocalDate aLocalDate(Object valor) {
        if (valor == null) return null;
        if (valor instanceof LocalDate d) return d;
        if (valor instanceof Date d) return d.toLocalDate();
        return LocalDate.parse(String.valueOf(valor));
    }

    private Number numero(Object valor) {
        return valor instanceof Number n ? n : null;
    }

    private Double redondearNullable(Object valor) {
        Number n = numero(valor);
        if (n == null) {
            return valor instanceof BigDecimal bd ? redondear(bd.doubleValue()) : null;
        }
        return redondear(n.doubleValue());
    }

    private Double redondear(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
