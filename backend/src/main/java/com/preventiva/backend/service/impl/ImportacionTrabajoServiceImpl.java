package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.CrearImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.ErrorFilaImportacionGenericaDto;
import com.preventiva.backend.dto.ErrorImportacionTrabajoDto;
import com.preventiva.backend.dto.FilaImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.ImportacionGenericaResponseDto;
import com.preventiva.backend.dto.ImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.ImportarDesdeTrabajoResponseDto;
import com.preventiva.backend.dto.PaginaFilasImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.RevalidarImportacionTrabajoResponseDto;
import com.preventiva.backend.entity.ErrorImportacionGenerica;
import com.preventiva.backend.entity.FilaImportacionTrabajo;
import com.preventiva.backend.entity.ImportacionGenerica;
import com.preventiva.backend.entity.ImportacionTrabajo;
import com.preventiva.backend.entity.MapeoCampoImportacion;
import com.preventiva.backend.entity.PlantillaImportacion;
import com.preventiva.backend.enums.EstadoImportacion;
import com.preventiva.backend.enums.EstadoImportacionTrabajo;
import com.preventiva.backend.enums.OrigenImportacion;
import com.preventiva.backend.enums.SeveridadError;
import com.preventiva.backend.enums.TipoErrorImportacion;
import com.preventiva.backend.repository.ErrorImportacionGenericaRepository;
import com.preventiva.backend.repository.FilaImportacionTrabajoRepository;
import com.preventiva.backend.repository.ImportacionGenericaRepository;
import com.preventiva.backend.repository.ImportacionTrabajoRepository;
import com.preventiva.backend.repository.MapeoCampoImportacionRepository;
import com.preventiva.backend.repository.PlantillaImportacionRepository;
import com.preventiva.backend.repository.RegistroClinicoGenericoRepository;
import com.preventiva.backend.service.interfaces.ImportacionTrabajoService;
import com.preventiva.backend.util.CampoClinicoValueEvaluator;
import com.preventiva.backend.util.RegistroClinicoGenericoBuilder;
import com.preventiva.backend.util.TextNormalizer;
import com.preventiva.backend.util.WorkbookLoader;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Copia interna de trabajo de una importación. El archivo original se conserva
 * como referencia inmutable; las correcciones se guardan aparte. La validación
 * de filas reutiliza {@link CampoClinicoValueEvaluator}, las mismas reglas que
 * el endpoint /validar-filas del motor genérico.
 */
@Service
@RequiredArgsConstructor
public class ImportacionTrabajoServiceImpl implements ImportacionTrabajoService {

    private final ImportacionTrabajoRepository importacionTrabajoRepository;
    private final FilaImportacionTrabajoRepository filaImportacionTrabajoRepository;
    private final PlantillaImportacionRepository plantillaImportacionRepository;
    private final MapeoCampoImportacionRepository mapeoCampoImportacionRepository;
    private final ImportacionGenericaRepository importacionGenericaRepository;
    private final ErrorImportacionGenericaRepository errorImportacionGenericaRepository;
    private final RegistroClinicoGenericoRepository registroClinicoGenericoRepository;

    private final DataFormatter dataFormatter = new DataFormatter();

    // ------------------------------------------------------------------
    // Crear
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public CrearImportacionTrabajoResponseDto crear(
            MultipartFile archivo, Long plantillaId, Integer indiceHoja, Integer filaCabecera) {
        PlantillaImportacion plantilla = obtenerPlantillaActivaOLanzar(plantillaId);
        List<MapeoCampoImportacion> mapeos = obtenerMapeosActivosOLanzar(plantillaId);

        Map<String, MapeoCampoImportacion> mapeosPorNombreNormalizado = mapeos.stream()
                .collect(Collectors.toMap(
                        m -> TextNormalizer.normalize(m.getNombreColumnaOrigen()),
                        m -> m,
                        (a, b) -> a));

        int indiceHojaEfectivo = indiceHoja != null ? indiceHoja : 0;
        int filaCabeceraEfectiva = resolverFilaCabecera(filaCabecera, plantilla);

        Materializacion materializacion;
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
            if (WorkbookLoader.esCsv(archivo)) {
                materializacion = materializarCsv(archivo, filaCabeceraEfectiva, mapeos, mapeosPorNombreNormalizado);
            } else {
                materializacion = materializarExcel(
                        archivo, indiceHojaEfectivo, filaCabeceraEfectiva, mapeos, mapeosPorNombreNormalizado);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo leer el archivo: " + e.getMessage());
        }

        if (materializacion.filas.isEmpty()) {
            materializacion.erroresGlobales.add(ErrorImportacionTrabajoDto.builder()
                    .numeroFila(filaCabeceraEfectiva + 1)
                    .tipoError("SIN_FILAS_CLINICAS")
                    .mensaje("El archivo no contiene ninguna fila clínica reconocible "
                            + "(ningún campo mapeado tiene valor en ninguna fila).")
                    .severidad(SeveridadError.ERROR.name())
                    .build());
        }

        ImportacionTrabajo trabajo = ImportacionTrabajo.builder()
                .dataset(plantilla.getDataset())
                .plantilla(plantilla)
                .nombreArchivoOriginal(archivo.getOriginalFilename())
                // El archivo original se conserva como referencia inmutable;
                // las correcciones se guardan aparte.
                .contenidoArchivo(contenido)
                .origen(WorkbookLoader.esCsv(archivo) ? OrigenImportacion.CSV : OrigenImportacion.EXCEL)
                .indiceHoja(indiceHojaEfectivo)
                .filaCabecera(filaCabeceraEfectiva)
                .estado(EstadoImportacionTrabajo.EN_EDICION)
                .totalFilasLeidas(materializacion.filas.size())
                .columnasPresentes(materializacion.columnasPresentes)
                .erroresGlobales(materializacion.erroresGlobales)
                .fechaCreacion(LocalDateTime.now())
                .build();

        trabajo = importacionTrabajoRepository.save(trabajo);

        List<FilaImportacionTrabajo> filas = new ArrayList<>();
        for (FilaMaterializada filaMaterializada : materializacion.filas) {
            filas.add(FilaImportacionTrabajo.builder()
                    .importacionTrabajo(trabajo)
                    .numeroFilaOriginal(filaMaterializada.numeroFilaOriginal)
                    .valoresOriginales(filaMaterializada.valores)
                    .valoresCorregidos(new HashMap<>())
                    .excluida(false)
                    .erroresActuales(List.of())
                    .build());
        }
        filas = filaImportacionTrabajoRepository.saveAll(filas);

        // Primera revalidación: deja erroresActuales y estado coherentes.
        revalidarInterno(trabajo, filas, mapeos);
        importacionTrabajoRepository.save(trabajo);
        filaImportacionTrabajoRepository.saveAll(filas);

        ImportacionTrabajoResponseDto resumen = construirResumen(trabajo, filas);
        return CrearImportacionTrabajoResponseDto.builder()
                .importacionTrabajo(resumen)
                .resumen(textoResumen(resumen))
                .build();
    }

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public ImportacionTrabajoResponseDto obtenerPorId(Long id) {
        ImportacionTrabajo trabajo = obtenerTrabajoOLanzar(id);
        List<FilaImportacionTrabajo> filas =
                filaImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(id);
        return construirResumen(trabajo, filas);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaFilasImportacionTrabajoResponseDto listarFilas(
            Long id, int page, int size, boolean soloConErrores) {
        obtenerTrabajoOLanzar(id);
        Pageable pageable = PageRequest.of(page, size);

        Page<FilaImportacionTrabajo> pagina = soloConErrores
                ? filaImportacionTrabajoRepository.findConErrores(id, pageable)
                : filaImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(id, pageable);

        return PaginaFilasImportacionTrabajoResponseDto.builder()
                .content(pagina.getContent().stream().map(this::mapearFila).toList())
                .page(pagina.getNumber())
                .size(pagina.getSize())
                .totalElements(pagina.getTotalElements())
                .totalPages(pagina.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ErrorImportacionTrabajoDto> listarErrores(Long id) {
        ImportacionTrabajo trabajo = obtenerTrabajoOLanzar(id);
        List<FilaImportacionTrabajo> filas =
                filaImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(id);

        List<ErrorImportacionTrabajoDto> errores = new ArrayList<>();
        if (trabajo.getErroresGlobales() != null) {
            errores.addAll(trabajo.getErroresGlobales());
        }
        for (FilaImportacionTrabajo fila : filas) {
            if (Boolean.TRUE.equals(fila.getExcluida())) {
                continue;
            }
            if (fila.getErroresActuales() != null) {
                errores.addAll(fila.getErroresActuales());
            }
        }
        return errores;
    }

    // ------------------------------------------------------------------
    // Revalidar / descartar
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public RevalidarImportacionTrabajoResponseDto revalidar(Long id) {
        ImportacionTrabajo trabajo = obtenerTrabajoOLanzar(id);
        exigirEditable(trabajo);

        List<FilaImportacionTrabajo> filas =
                filaImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(id);
        List<MapeoCampoImportacion> mapeos = obtenerMapeosActivosOLanzar(trabajo.getPlantilla().getId());

        revalidarInterno(trabajo, filas, mapeos);
        importacionTrabajoRepository.save(trabajo);
        filaImportacionTrabajoRepository.saveAll(filas);

        ImportacionTrabajoResponseDto resumen = construirResumen(trabajo, filas);
        return RevalidarImportacionTrabajoResponseDto.builder()
                .importacionTrabajo(resumen)
                .resumen(textoResumen(resumen))
                .build();
    }

    @Override
    @Transactional
    public void descartar(Long id) {
        ImportacionTrabajo trabajo = obtenerTrabajoOLanzar(id);

        if (trabajo.getEstado() == EstadoImportacionTrabajo.IMPORTADA) {
            throw new IllegalArgumentException("La copia de trabajo ya fue importada y no puede descartarse.");
        }

        trabajo.setEstado(EstadoImportacionTrabajo.DESCARTADA);
        importacionTrabajoRepository.save(trabajo);
    }

    // ------------------------------------------------------------------
    // Mutaciones (excluir/incluir, corregir, deshacer)
    // Cada mutación revalida automáticamente y devuelve el resumen
    // actualizado, para que el frontend no tenga que recordar revalidar.
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public RevalidarImportacionTrabajoResponseDto actualizarExclusion(
            Long id, Integer numeroFila, Boolean excluida) {
        ImportacionTrabajo trabajo = obtenerTrabajoOLanzar(id);
        exigirEditable(trabajo);

        if (excluida == null) {
            throw new IllegalArgumentException("Debes indicar si la fila queda excluida o no.");
        }

        FilaImportacionTrabajo fila = obtenerFilaOLanzar(id, numeroFila);
        fila.setExcluida(excluida);

        return revalidarYResumir(trabajo);
    }

    @Override
    @Transactional
    public RevalidarImportacionTrabajoResponseDto excluirSimilares(
            Long id, String tipoError, String nombreColumna) {
        ImportacionTrabajo trabajo = obtenerTrabajoOLanzar(id);
        exigirEditable(trabajo);

        if (tipoError == null || tipoError.isBlank() || nombreColumna == null || nombreColumna.isBlank()) {
            throw new IllegalArgumentException("Debes indicar el tipo de error y la columna del problema.");
        }

        List<FilaImportacionTrabajo> filas =
                filaImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(id);

        // "Similar" en sentido estricto: mismo tipoError y misma nombreColumna.
        for (FilaImportacionTrabajo fila : filas) {
            if (Boolean.TRUE.equals(fila.getExcluida()) || fila.getErroresActuales() == null) {
                continue;
            }
            boolean coincide = fila.getErroresActuales().stream().anyMatch(e ->
                    tipoError.equals(e.getTipoError()) && nombreColumna.equals(e.getNombreColumna()));
            if (coincide) {
                fila.setExcluida(true);
            }
        }

        return revalidarYResumir(trabajo);
    }

    @Override
    @Transactional
    public RevalidarImportacionTrabajoResponseDto corregirCelda(
            Long id, Integer numeroFila, String columna, String valor) {
        ImportacionTrabajo trabajo = obtenerTrabajoOLanzar(id);
        exigirEditable(trabajo);

        FilaImportacionTrabajo fila = obtenerFilaOLanzar(id, numeroFila);
        exigirColumnaConocida(trabajo, fila, columna);

        // Mapa nuevo para que Hibernate detecte el cambio en la columna JSONB.
        Map<String, String> corregidos = fila.getValoresCorregidos() != null
                ? new HashMap<>(fila.getValoresCorregidos())
                : new HashMap<>();
        corregidos.put(columna, valor);
        fila.setValoresCorregidos(corregidos);

        return revalidarYResumir(trabajo);
    }

    @Override
    @Transactional
    public RevalidarImportacionTrabajoResponseDto deshacerCorreccionCelda(
            Long id, Integer numeroFila, String columna) {
        ImportacionTrabajo trabajo = obtenerTrabajoOLanzar(id);
        exigirEditable(trabajo);

        FilaImportacionTrabajo fila = obtenerFilaOLanzar(id, numeroFila);

        if (fila.getValoresCorregidos() != null && fila.getValoresCorregidos().containsKey(columna)) {
            Map<String, String> corregidos = new HashMap<>(fila.getValoresCorregidos());
            corregidos.remove(columna);
            fila.setValoresCorregidos(corregidos);
        }

        return revalidarYResumir(trabajo);
    }

    // ------------------------------------------------------------------
    // Deshacer en bloque (Fase 6.8C.4): ninguno de estos métodos toca
    // contenidoArchivo ni valoresOriginales, solo el estado mutable de cada
    // fila (valoresCorregidos / excluida).
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public RevalidarImportacionTrabajoResponseDto deshacerTodasLasCorreccionesDeFila(Long id, Integer numeroFila) {
        ImportacionTrabajo trabajo = obtenerTrabajoOLanzar(id);
        exigirEditable(trabajo);

        FilaImportacionTrabajo fila = obtenerFilaOLanzar(id, numeroFila);
        fila.setValoresCorregidos(new HashMap<>());

        return revalidarYResumir(trabajo);
    }

    @Override
    @Transactional
    public RevalidarImportacionTrabajoResponseDto deshacerTodasLasCorrecciones(Long id) {
        ImportacionTrabajo trabajo = obtenerTrabajoOLanzar(id);
        exigirEditable(trabajo);

        List<FilaImportacionTrabajo> filas =
                filaImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(id);
        for (FilaImportacionTrabajo fila : filas) {
            fila.setValoresCorregidos(new HashMap<>());
        }

        return revalidarYResumir(trabajo);
    }

    @Override
    @Transactional
    public RevalidarImportacionTrabajoResponseDto deshacerTodasLasExclusiones(Long id) {
        ImportacionTrabajo trabajo = obtenerTrabajoOLanzar(id);
        exigirEditable(trabajo);

        List<FilaImportacionTrabajo> filas =
                filaImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(id);
        for (FilaImportacionTrabajo fila : filas) {
            fila.setExcluida(false);
        }

        return revalidarYResumir(trabajo);
    }

    @Override
    @Transactional
    public RevalidarImportacionTrabajoResponseDto restaurarOriginal(Long id) {
        ImportacionTrabajo trabajo = obtenerTrabajoOLanzar(id);
        exigirEditable(trabajo);

        List<FilaImportacionTrabajo> filas =
                filaImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(id);
        for (FilaImportacionTrabajo fila : filas) {
            fila.setValoresCorregidos(new HashMap<>());
            fila.setExcluida(false);
        }

        return revalidarYResumir(trabajo);
    }

    // ------------------------------------------------------------------
    // Corrección asistida (Fase 6.8C.4)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public RevalidarImportacionTrabajoResponseDto rellenarColumna(
            Long id, String nombreColumna, String tipoError, String valor, boolean soloFilasConEsteProblema) {
        ImportacionTrabajo trabajo = obtenerTrabajoOLanzar(id);
        exigirEditable(trabajo);

        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Debes indicar un valor para rellenar la columna.");
        }
        exigirColumnaConocidaPorNombre(trabajo, nombreColumna);

        List<FilaImportacionTrabajo> filas =
                filaImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(id);

        for (FilaImportacionTrabajo fila : filas) {
            if (Boolean.TRUE.equals(fila.getExcluida())) {
                continue;
            }

            boolean coincide = !soloFilasConEsteProblema || tieneErrorActivo(fila, nombreColumna, tipoError);
            if (!coincide) {
                continue;
            }

            Map<String, String> corregidos = fila.getValoresCorregidos() != null
                    ? new HashMap<>(fila.getValoresCorregidos())
                    : new HashMap<>();
            corregidos.put(nombreColumna, valor);
            fila.setValoresCorregidos(corregidos);
        }

        return revalidarYResumir(trabajo);
    }

    @Override
    @Transactional
    public RevalidarImportacionTrabajoResponseDto normalizarColumna(Long id, String nombreColumna, String estrategia) {
        ImportacionTrabajo trabajo = obtenerTrabajoOLanzar(id);
        exigirEditable(trabajo);
        exigirColumnaConocidaPorNombre(trabajo, nombreColumna);

        List<FilaImportacionTrabajo> filas =
                filaImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(id);

        for (FilaImportacionTrabajo fila : filas) {
            if (Boolean.TRUE.equals(fila.getExcluida())) {
                continue;
            }

            String valorEfectivo = valorEfectivo(fila, nombreColumna);
            String normalizado = normalizarValor(valorEfectivo, estrategia);
            if (normalizado == null || normalizado.equals(valorEfectivo)) {
                continue;
            }

            Map<String, String> corregidos = fila.getValoresCorregidos() != null
                    ? new HashMap<>(fila.getValoresCorregidos())
                    : new HashMap<>();
            corregidos.put(nombreColumna, normalizado);
            fila.setValoresCorregidos(corregidos);
        }

        return revalidarYResumir(trabajo);
    }

    private boolean tieneErrorActivo(FilaImportacionTrabajo fila, String nombreColumna, String tipoError) {
        if (fila.getErroresActuales() == null) {
            return false;
        }
        return fila.getErroresActuales().stream().anyMatch(e ->
                nombreColumna.equals(e.getNombreColumna()) && (tipoError == null || tipoError.equals(e.getTipoError())));
    }

    private void exigirColumnaConocidaPorNombre(ImportacionTrabajo trabajo, String columna) {
        if (columna == null || columna.isBlank()) {
            throw new IllegalArgumentException("Debes indicar la columna.");
        }
        List<MapeoCampoImportacion> mapeos =
                mapeoCampoImportacionRepository.findByPlantillaIdAndActivoTrue(trabajo.getPlantilla().getId());
        boolean enMapeos = mapeos.stream().anyMatch(m -> columna.equals(m.getNombreColumnaOrigen()));
        if (!enMapeos) {
            throw new IllegalArgumentException("La columna '" + columna + "' no existe en esta importación.");
        }
    }

    /**
     * Normaliza un valor según la estrategia elegida. Devuelve null si el
     * valor está vacío o no se reconoce (en cuyo caso la fila no se toca:
     * mejor dejar el error visible que adivinar un dato clínico).
     */
    private String normalizarValor(String valorOriginal, String estrategia) {
        if (valorOriginal == null || valorOriginal.isBlank()) {
            return null;
        }
        String valor = valorOriginal.trim();
        return switch (estrategia) {
            case "TEXTO_TRIM" -> valor.replaceAll("\\s+", " ");
            case "NUMERO" -> valor.replace(",", ".");
            case "BOOLEANO" -> normalizarBooleano(valor);
            case "FECHA" -> normalizarFecha(valor);
            default -> null;
        };
    }

    private String normalizarBooleano(String valor) {
        String normalizado = TextNormalizer.normalize(valor);
        if (CampoClinicoValueEvaluator.VALORES_VERDADEROS.contains(normalizado)) {
            return "SI";
        }
        if (CampoClinicoValueEvaluator.VALORES_FALSOS.contains(normalizado)) {
            return "NO";
        }
        return null;
    }

    private String normalizarFecha(String valor) {
        for (java.time.format.DateTimeFormatter formato : FORMATOS_FECHA_NORMALIZAR) {
            try {
                return java.time.LocalDate.parse(valor, formato).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (java.time.format.DateTimeParseException ignored) {
                // Probamos el siguiente formato.
            }
        }
        return null;
    }

    private static final List<java.time.format.DateTimeFormatter> FORMATOS_FECHA_NORMALIZAR = List.of(
            java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy"),
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            java.time.format.DateTimeFormatter.ofPattern("d-M-yyyy"),
            java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            java.time.format.DateTimeFormatter.ofPattern("d/M/yy"),
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy"),
            java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);

    // ------------------------------------------------------------------
    // Importar desde la copia interna
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public ImportarDesdeTrabajoResponseDto importarDesdeTrabajo(Long id) {
        ImportacionTrabajo trabajo = obtenerTrabajoOLanzar(id);
        exigirEditable(trabajo);

        if (trabajo.getEstado() != EstadoImportacionTrabajo.LISTA_PARA_IMPORTAR) {
            throw new IllegalArgumentException(
                    "La copia de trabajo tiene errores pendientes. Corrige o excluye filas y revalida antes de importar.");
        }

        List<FilaImportacionTrabajo> filas =
                filaImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(id);
        List<MapeoCampoImportacion> mapeos = obtenerMapeosActivosOLanzar(trabajo.getPlantilla().getId());

        // Revalidación defensiva: el estado guardado debería estar al día, pero
        // así la importación nunca depende de una foto obsoleta.
        revalidarInterno(trabajo, filas, mapeos);
        if (trabajo.getEstado() != EstadoImportacionTrabajo.LISTA_PARA_IMPORTAR) {
            importacionTrabajoRepository.save(trabajo);
            filaImportacionTrabajoRepository.saveAll(filas);
            throw new IllegalArgumentException(
                    "Hay errores bloqueantes activos en filas no excluidas. No se puede importar.");
        }

        PlantillaImportacion plantilla = trabajo.getPlantilla();

        ImportacionGenerica importacion = ImportacionGenerica.builder()
                .nombreArchivo(java.util.UUID.randomUUID() + "_"
                        + (trabajo.getNombreArchivoOriginal() != null ? trabajo.getNombreArchivoOriginal() : "archivo"))
                .nombreOriginal(trabajo.getNombreArchivoOriginal())
                .fechaImportacion(LocalDateTime.now())
                .filasLeidas(trabajo.getTotalFilasLeidas())
                .filasImportadas(0)
                .filasConError(0)
                .estado(EstadoImportacion.PENDIENTE)
                .plantilla(plantilla)
                .usuario(null)
                .build();
        importacion = importacionGenericaRepository.save(importacion);

        // Trazabilidad: las advertencias vigentes se persisten igual que en la
        // importación genérica directa.
        List<ErrorImportacionTrabajoDto> advertencias = listarErroresActivos(trabajo, filas);
        guardarErroresGenerica(importacion, advertencias);

        Set<String> presentes = trabajo.getColumnasPresentes() != null
                ? new HashSet<>(trabajo.getColumnasPresentes())
                : Set.of();
        List<MapeoCampoImportacion> mapeosPresentes = mapeos.stream()
                .filter(m -> presentes.contains(m.getNombreColumnaOrigen()))
                .toList();

        // La importación lee exclusivamente la copia interna (valor corregido
        // sobre valor original); nunca se vuelve a leer el archivo.
        int filasImportadas = 0;
        int filasExcluidas = 0;
        for (FilaImportacionTrabajo fila : filas) {
            if (Boolean.TRUE.equals(fila.getExcluida())) {
                filasExcluidas++;
                continue;
            }

            List<RegistroClinicoGenericoBuilder.CampoValor> valores = new ArrayList<>();
            for (MapeoCampoImportacion mapeo : mapeosPresentes) {
                valores.add(new RegistroClinicoGenericoBuilder.CampoValor(
                        mapeo, valorEfectivo(fila, mapeo.getNombreColumnaOrigen())));
            }

            registroClinicoGenericoRepository.save(
                    RegistroClinicoGenericoBuilder.construir(plantilla, importacion, valores));
            filasImportadas++;
        }

        boolean tieneAdvertencias = !advertencias.isEmpty();
        importacion.setFilasImportadas(filasImportadas);
        importacion.setEstado(tieneAdvertencias
                ? EstadoImportacion.IMPORTADA_CON_ERRORES
                : EstadoImportacion.IMPORTADA);
        importacionGenericaRepository.save(importacion);

        trabajo.setEstado(EstadoImportacionTrabajo.IMPORTADA);
        trabajo.setImportacionGenerica(importacion);
        importacionTrabajoRepository.save(trabajo);
        filaImportacionTrabajoRepository.saveAll(filas);

        ImportacionTrabajoResponseDto resumenTrabajo = construirResumen(trabajo, filas);

        return ImportarDesdeTrabajoResponseDto.builder()
                .importacionTrabajo(resumenTrabajo)
                .importacionGenerica(mapImportacionGenericaToDto(importacion, advertencias.size()))
                .filasImportadas(filasImportadas)
                .filasExcluidas(filasExcluidas)
                .resumen("Se importaron " + filasImportadas + " fila(s) desde la copia de trabajo. "
                        + filasExcluidas + " fila(s) excluida(s), " + advertencias.size() + " advertencia(s).")
                .build();
    }

    private RevalidarImportacionTrabajoResponseDto revalidarYResumir(ImportacionTrabajo trabajo) {
        List<FilaImportacionTrabajo> filas =
                filaImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(trabajo.getId());
        List<MapeoCampoImportacion> mapeos = obtenerMapeosActivosOLanzar(trabajo.getPlantilla().getId());

        revalidarInterno(trabajo, filas, mapeos);
        importacionTrabajoRepository.save(trabajo);
        filaImportacionTrabajoRepository.saveAll(filas);

        ImportacionTrabajoResponseDto resumen = construirResumen(trabajo, filas);
        return RevalidarImportacionTrabajoResponseDto.builder()
                .importacionTrabajo(resumen)
                .resumen(textoResumen(resumen))
                .build();
    }

    private List<ErrorImportacionTrabajoDto> listarErroresActivos(
            ImportacionTrabajo trabajo, List<FilaImportacionTrabajo> filas) {
        List<ErrorImportacionTrabajoDto> errores = new ArrayList<>();
        if (trabajo.getErroresGlobales() != null) {
            errores.addAll(trabajo.getErroresGlobales());
        }
        for (FilaImportacionTrabajo fila : filas) {
            if (Boolean.TRUE.equals(fila.getExcluida()) || fila.getErroresActuales() == null) {
                continue;
            }
            errores.addAll(fila.getErroresActuales());
        }
        return errores;
    }

    private void guardarErroresGenerica(
            ImportacionGenerica importacion, List<ErrorImportacionTrabajoDto> errores) {
        for (ErrorImportacionTrabajoDto errorDto : errores) {
            TipoErrorImportacion tipoError;
            try {
                tipoError = TipoErrorImportacion.valueOf(errorDto.getTipoError());
            } catch (Exception e) {
                tipoError = TipoErrorImportacion.ERROR_DESCONOCIDO;
            }

            SeveridadError severidad;
            try {
                severidad = SeveridadError.valueOf(errorDto.getSeveridad());
            } catch (Exception e) {
                severidad = SeveridadError.ERROR;
            }

            errorImportacionGenericaRepository.save(ErrorImportacionGenerica.builder()
                    .importacionGenerica(importacion)
                    .numeroFila(errorDto.getNumeroFila())
                    .nombreColumna(errorDto.getNombreColumna())
                    .valorOriginal(errorDto.getValorOriginal())
                    .tipoError(tipoError)
                    .severidad(severidad)
                    .mensaje(errorDto.getMensaje())
                    .build());
        }
    }

    private ImportacionGenericaResponseDto mapImportacionGenericaToDto(
            ImportacionGenerica importacion, int totalAdvertencias) {
        PlantillaImportacion plantilla = importacion.getPlantilla();

        return ImportacionGenericaResponseDto.builder()
                .importacionId(importacion.getId())
                .nombreArchivo(importacion.getNombreOriginal())
                .plantillaId(plantilla != null ? plantilla.getId() : null)
                .datasetId(plantilla != null && plantilla.getDataset() != null ? plantilla.getDataset().getId() : null)
                .filasLeidas(importacion.getFilasLeidas())
                .filasImportadas(importacion.getFilasImportadas())
                .filasConError(importacion.getFilasConError())
                .totalAdvertencias(totalAdvertencias)
                .estado(importacion.getEstado().name())
                .mensaje("Importación realizada desde la copia de trabajo.")
                .build();
    }

    private void exigirColumnaConocida(
            ImportacionTrabajo trabajo, FilaImportacionTrabajo fila, String columna) {
        boolean enOriginales = fila.getValoresOriginales() != null
                && fila.getValoresOriginales().containsKey(columna);
        if (enOriginales) {
            return;
        }

        List<MapeoCampoImportacion> mapeos =
                mapeoCampoImportacionRepository.findByPlantillaIdAndActivoTrue(trabajo.getPlantilla().getId());
        boolean enMapeos = mapeos.stream().anyMatch(m -> columna.equals(m.getNombreColumnaOrigen()));
        if (!enMapeos) {
            throw new IllegalArgumentException(
                    "La columna '" + columna + "' no existe en esta importación.");
        }
    }

    private FilaImportacionTrabajo obtenerFilaOLanzar(Long importacionTrabajoId, Integer numeroFila) {
        return filaImportacionTrabajoRepository
                .findByImportacionTrabajoIdAndNumeroFilaOriginal(importacionTrabajoId, numeroFila)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe la fila " + numeroFila + " en la copia de trabajo " + importacionTrabajoId));
    }

    // ------------------------------------------------------------------
    // Revalidación interna (mismas reglas que /validar-filas)
    // ------------------------------------------------------------------

    private void revalidarInterno(
            ImportacionTrabajo trabajo,
            List<FilaImportacionTrabajo> filas,
            List<MapeoCampoImportacion> mapeos) {
        // Solo se evalúan las columnas que estaban presentes en el archivo: las
        // columnas obligatorias ausentes ya generan un único error global, igual
        // que en el motor actual (no un error por fila).
        Set<String> presentes = trabajo.getColumnasPresentes() != null
                ? new HashSet<>(trabajo.getColumnasPresentes())
                : Set.of();
        List<MapeoCampoImportacion> mapeosPresentes = mapeos.stream()
                .filter(m -> presentes.contains(m.getNombreColumnaOrigen()))
                .toList();

        for (FilaImportacionTrabajo fila : filas) {
            List<ErrorImportacionTrabajoDto> errores = new ArrayList<>();

            for (MapeoCampoImportacion mapeo : mapeosPresentes) {
                String valorEfectivo = valorEfectivo(fila, mapeo.getNombreColumnaOrigen());
                CampoClinicoValueEvaluator.Resultado resultado =
                        CampoClinicoValueEvaluator.evaluar(mapeo, valorEfectivo, fila.getNumeroFilaOriginal());

                if (resultado.getError() != null) {
                    errores.add(convertirError(resultado.getError()));
                }
            }

            fila.setErroresActuales(errores);
        }

        boolean importable = esImportable(trabajo, filas);
        trabajo.setEstado(importable
                ? EstadoImportacionTrabajo.LISTA_PARA_IMPORTAR
                : EstadoImportacionTrabajo.EN_EDICION);
        trabajo.setFechaUltimaRevalidacion(LocalDateTime.now());
    }

    /** Valor efectivo de una celda: el corregido si existe, si no el original. */
    private String valorEfectivo(FilaImportacionTrabajo fila, String nombreColumna) {
        Map<String, String> corregidos = fila.getValoresCorregidos();
        if (corregidos != null && corregidos.containsKey(nombreColumna)) {
            return corregidos.get(nombreColumna);
        }
        Map<String, String> originales = fila.getValoresOriginales();
        return originales != null ? originales.get(nombreColumna) : null;
    }

    private boolean esImportable(ImportacionTrabajo trabajo, List<FilaImportacionTrabajo> filas) {
        boolean globalConError = trabajo.getErroresGlobales() != null && trabajo.getErroresGlobales().stream()
                .anyMatch(e -> SeveridadError.ERROR.name().equals(e.getSeveridad()));
        if (globalConError) {
            return false;
        }

        return filas.stream()
                .filter(f -> !Boolean.TRUE.equals(f.getExcluida()))
                .noneMatch(f -> contieneError(f.getErroresActuales()));
    }

    private boolean contieneError(List<ErrorImportacionTrabajoDto> errores) {
        return errores != null && errores.stream()
                .anyMatch(e -> SeveridadError.ERROR.name().equals(e.getSeveridad()));
    }

    private ErrorImportacionTrabajoDto convertirError(ErrorFilaImportacionGenericaDto error) {
        return ErrorImportacionTrabajoDto.builder()
                .numeroFila(error.getNumeroFila())
                .nombreColumna(error.getNombreColumna())
                .valorOriginal(error.getValorOriginal())
                .tipoError(error.getTipoError())
                .severidad(error.getSeveridad())
                .mensaje(error.getMensaje())
                .build();
    }

    // ------------------------------------------------------------------
    // Materialización (una sola vez, al crear)
    // ------------------------------------------------------------------

    private static final class FilaMaterializada {
        private final int numeroFilaOriginal;
        private final Map<String, String> valores;

        private FilaMaterializada(int numeroFilaOriginal, Map<String, String> valores) {
            this.numeroFilaOriginal = numeroFilaOriginal;
            this.valores = valores;
        }
    }

    private static final class Materializacion {
        private final List<FilaMaterializada> filas = new ArrayList<>();
        private final List<String> columnasPresentes = new ArrayList<>();
        private final List<ErrorImportacionTrabajoDto> erroresGlobales = new ArrayList<>();
    }

    private Materializacion materializarExcel(
            MultipartFile archivo,
            int indiceHoja,
            int filaCabecera,
            List<MapeoCampoImportacion> mapeos,
            Map<String, MapeoCampoImportacion> mapeosPorNombreNormalizado) throws Exception {
        Materializacion resultado = new Materializacion();

        try (Workbook workbook = WorkbookLoader.abrirWorkbook(archivo)) {
            Sheet sheet = obtenerHoja(workbook, indiceHoja);
            Row headerRow = sheet.getRow(filaCabecera);

            if (headerRow == null) {
                throw new IllegalArgumentException("El Excel no contiene fila de cabeceras en la fila indicada.");
            }

            Map<Integer, MapeoCampoImportacion> mapeosPorIndiceColumna = new LinkedHashMap<>();
            for (Cell cell : headerRow) {
                String nombreColumna = obtenerValorCeldaComoTexto(cell);
                if (nombreColumna == null || nombreColumna.isBlank()) {
                    continue;
                }
                MapeoCampoImportacion mapeo =
                        mapeosPorNombreNormalizado.get(TextNormalizer.normalize(nombreColumna));
                if (mapeo != null) {
                    mapeosPorIndiceColumna.put(cell.getColumnIndex(), mapeo);
                } else {
                    resultado.erroresGlobales.add(errorColumnaNoReconocida(nombreColumna, filaCabecera));
                }
            }

            registrarColumnasPresentes(resultado, mapeosPorIndiceColumna, mapeos, filaCabecera);

            int ultimaFila = sheet.getLastRowNum();
            for (int i = filaCabecera + 1; i <= ultimaFila; i++) {
                Row row = sheet.getRow(i);
                if (filaVacia(row)) {
                    continue;
                }

                Map<String, String> valores = new LinkedHashMap<>();
                for (Map.Entry<Integer, MapeoCampoImportacion> entry : mapeosPorIndiceColumna.entrySet()) {
                    Cell cell = obtenerCeldaConSoporteCombinadas(sheet, i, entry.getKey());
                    valores.put(entry.getValue().getNombreColumnaOrigen(), obtenerValorCeldaComoTexto(cell));
                }

                if (valores.values().stream().noneMatch(v -> v != null && !v.isBlank())) {
                    continue;
                }

                resultado.filas.add(new FilaMaterializada(i + 1, valores));
            }
        }

        return resultado;
    }

    private Materializacion materializarCsv(
            MultipartFile archivo,
            int filaCabecera,
            List<MapeoCampoImportacion> mapeos,
            Map<String, MapeoCampoImportacion> mapeosPorNombreNormalizado) throws Exception {
        Materializacion resultado = new Materializacion();

        boolean cabeceraProcesada = false;
        Map<Integer, MapeoCampoImportacion> mapeosPorIndiceColumna = new LinkedHashMap<>();

        try (BufferedReader reader = WorkbookLoader.abrirReaderCsv(archivo)) {
            String linea;
            int numeroFila = 0;
            Character separador = null;

            while ((linea = reader.readLine()) != null) {
                if (separador == null && !linea.isBlank()) {
                    separador = WorkbookLoader.detectarSeparador(linea);
                }

                if (numeroFila < filaCabecera) {
                    numeroFila++;
                    continue;
                }

                if (numeroFila == filaCabecera) {
                    if (linea.isBlank()) {
                        throw new IllegalArgumentException("El CSV no contiene fila de cabeceras en la fila indicada.");
                    }

                    List<String> cabeceras = WorkbookLoader.parsearLineaCsv(linea, separador);
                    for (int i = 0; i < cabeceras.size(); i++) {
                        String nombreColumna = cabeceras.get(i);
                        if (nombreColumna == null || nombreColumna.isBlank()) {
                            continue;
                        }
                        MapeoCampoImportacion mapeo =
                                mapeosPorNombreNormalizado.get(TextNormalizer.normalize(nombreColumna));
                        if (mapeo != null) {
                            mapeosPorIndiceColumna.put(i, mapeo);
                        } else {
                            resultado.erroresGlobales.add(errorColumnaNoReconocida(nombreColumna, filaCabecera));
                        }
                    }

                    registrarColumnasPresentes(resultado, mapeosPorIndiceColumna, mapeos, filaCabecera);

                    cabeceraProcesada = true;
                    numeroFila++;
                    continue;
                }

                if (linea.isBlank()) {
                    numeroFila++;
                    continue;
                }

                List<String> valoresCsv = WorkbookLoader.parsearLineaCsv(linea, separador);

                Map<String, String> valores = new LinkedHashMap<>();
                for (Map.Entry<Integer, MapeoCampoImportacion> entry : mapeosPorIndiceColumna.entrySet()) {
                    valores.put(entry.getValue().getNombreColumnaOrigen(), obtenerValorCsv(valoresCsv, entry.getKey()));
                }

                if (valores.values().stream().anyMatch(v -> v != null && !v.isBlank())) {
                    resultado.filas.add(new FilaMaterializada(numeroFila + 1, valores));
                }

                numeroFila++;
            }

            if (!cabeceraProcesada) {
                throw new IllegalArgumentException("El CSV no contiene fila de cabeceras en la fila indicada.");
            }
        }

        return resultado;
    }

    private void registrarColumnasPresentes(
            Materializacion resultado,
            Map<Integer, MapeoCampoImportacion> mapeosPorIndiceColumna,
            List<MapeoCampoImportacion> mapeos,
            int filaCabecera) {
        Set<String> presentes = mapeosPorIndiceColumna.values().stream()
                .map(MapeoCampoImportacion::getNombreColumnaOrigen)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        resultado.columnasPresentes.addAll(presentes);

        for (MapeoCampoImportacion mapeo : mapeos) {
            if (!Boolean.TRUE.equals(mapeo.getObligatorio())) {
                continue;
            }
            if (!presentes.contains(mapeo.getNombreColumnaOrigen())) {
                resultado.erroresGlobales.add(ErrorImportacionTrabajoDto.builder()
                        .numeroFila(filaCabecera + 1)
                        .nombreColumna(mapeo.getNombreColumnaOrigen())
                        .tipoError("COLUMNA_OBLIGATORIA_FALTANTE")
                        .mensaje("El campo clínico obligatorio '" + mapeo.getCampoClinico().getCodigo()
                                + "' no tiene columna origen en el archivo.")
                        .severidad(SeveridadError.ERROR.name())
                        .build());
            }
        }
    }

    private ErrorImportacionTrabajoDto errorColumnaNoReconocida(String nombreColumna, int filaCabecera) {
        return ErrorImportacionTrabajoDto.builder()
                .numeroFila(filaCabecera + 1)
                .nombreColumna(nombreColumna)
                .tipoError("COLUMNA_NO_RECONOCIDA")
                .mensaje("La columna no está mapeada en la plantilla y será ignorada en la importación.")
                .severidad(SeveridadError.ADVERTENCIA.name())
                .build();
    }

    // ------------------------------------------------------------------
    // Resumen y mapeos a DTO
    // ------------------------------------------------------------------

    private ImportacionTrabajoResponseDto construirResumen(
            ImportacionTrabajo trabajo, List<FilaImportacionTrabajo> filas) {
        int totalErrores = 0;
        int totalAdvertencias = 0;

        if (trabajo.getErroresGlobales() != null) {
            for (ErrorImportacionTrabajoDto error : trabajo.getErroresGlobales()) {
                if (SeveridadError.ERROR.name().equals(error.getSeveridad())) {
                    totalErrores++;
                } else {
                    totalAdvertencias++;
                }
            }
        }

        int filasExcluidas = 0;
        for (FilaImportacionTrabajo fila : filas) {
            if (Boolean.TRUE.equals(fila.getExcluida())) {
                filasExcluidas++;
                continue;
            }
            if (fila.getErroresActuales() == null) {
                continue;
            }
            for (ErrorImportacionTrabajoDto error : fila.getErroresActuales()) {
                if (SeveridadError.ERROR.name().equals(error.getSeveridad())) {
                    totalErrores++;
                } else {
                    totalAdvertencias++;
                }
            }
        }

        return ImportacionTrabajoResponseDto.builder()
                .id(trabajo.getId())
                .datasetId(trabajo.getDataset().getId())
                .plantillaId(trabajo.getPlantilla().getId())
                .nombreArchivoOriginal(trabajo.getNombreArchivoOriginal())
                .origen(trabajo.getOrigen() != null ? trabajo.getOrigen().name() : null)
                .indiceHoja(trabajo.getIndiceHoja())
                .filaCabecera(trabajo.getFilaCabecera())
                .estado(trabajo.getEstado().name())
                .totalFilasLeidas(trabajo.getTotalFilasLeidas())
                .totalFilasExcluidas(filasExcluidas)
                .totalErrores(totalErrores)
                .totalAdvertencias(totalAdvertencias)
                .importable(trabajo.getEstado() == EstadoImportacionTrabajo.LISTA_PARA_IMPORTAR)
                .fechaCreacion(trabajo.getFechaCreacion())
                .fechaUltimaRevalidacion(trabajo.getFechaUltimaRevalidacion())
                .build();
    }

    private String textoResumen(ImportacionTrabajoResponseDto resumen) {
        if (Boolean.TRUE.equals(resumen.getImportable())) {
            return resumen.getTotalFilasLeidas() + " fila(s) leída(s), "
                    + resumen.getTotalFilasExcluidas() + " excluida(s), "
                    + resumen.getTotalAdvertencias() + " advertencia(s). La copia de trabajo es importable.";
        }
        return resumen.getTotalFilasLeidas() + " fila(s) leída(s), "
                + resumen.getTotalErrores() + " error(es) pendiente(s). Corrige o excluye filas antes de importar.";
    }

    private FilaImportacionTrabajoResponseDto mapearFila(FilaImportacionTrabajo fila) {
        return FilaImportacionTrabajoResponseDto.builder()
                .id(fila.getId())
                .numeroFilaOriginal(fila.getNumeroFilaOriginal())
                .excluida(fila.getExcluida())
                .valoresOriginales(fila.getValoresOriginales())
                .valoresCorregidos(fila.getValoresCorregidos())
                .errores(fila.getErroresActuales())
                .build();
    }

    // ------------------------------------------------------------------
    // Ayudantes de lectura (mismos criterios que el motor genérico)
    // ------------------------------------------------------------------

    private boolean filaVacia(Row row) {
        if (row == null) {
            return true;
        }
        for (Cell cell : row) {
            String valor = obtenerValorCeldaComoTexto(cell);
            if (valor != null && !valor.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private Cell obtenerCeldaConSoporteCombinadas(Sheet sheet, int fila, int columna) {
        Row row = sheet.getRow(fila);
        Cell cell = row != null ? row.getCell(columna, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) : null;
        String valorDirecto = obtenerValorCeldaComoTexto(cell);

        if (valorDirecto != null && !valorDirecto.isBlank()) {
            return cell;
        }

        for (CellRangeAddress rango : sheet.getMergedRegions()) {
            if (rango.isInRange(fila, columna)) {
                Row filaPrincipal = sheet.getRow(rango.getFirstRow());
                if (filaPrincipal == null) {
                    return cell;
                }
                return filaPrincipal.getCell(rango.getFirstColumn(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            }
        }

        return cell;
    }

    private String obtenerValorCeldaComoTexto(Cell cell) {
        if (cell == null) {
            return "";
        }
        return dataFormatter.formatCellValue(cell);
    }

    private String obtenerValorCsv(List<String> valores, int indiceColumna) {
        if (valores == null || indiceColumna < 0 || indiceColumna >= valores.size()) {
            return "";
        }
        String valor = valores.get(indiceColumna);
        return valor == null ? "" : valor.trim();
    }

    private Sheet obtenerHoja(Workbook workbook, int indiceHoja) {
        int totalHojas = workbook.getNumberOfSheets();

        if (totalHojas == 0) {
            throw new IllegalArgumentException("El Excel no contiene hojas.");
        }

        if (indiceHoja < 0 || indiceHoja >= totalHojas) {
            throw new IllegalArgumentException(
                    "El índice de hoja no es válido. El archivo tiene " + totalHojas + " hoja(s).");
        }

        return workbook.getSheetAt(indiceHoja);
    }

    private int resolverFilaCabecera(Integer filaCabecera, PlantillaImportacion plantilla) {
        if (filaCabecera != null) {
            return filaCabecera;
        }
        return plantilla.getFilaCabecera() != null ? plantilla.getFilaCabecera() : 0;
    }

    private void exigirEditable(ImportacionTrabajo trabajo) {
        if (trabajo.getEstado() == EstadoImportacionTrabajo.IMPORTADA) {
            throw new IllegalArgumentException("La copia de trabajo ya fue importada.");
        }
        if (trabajo.getEstado() == EstadoImportacionTrabajo.DESCARTADA) {
            throw new IllegalArgumentException("La copia de trabajo fue descartada.");
        }
    }

    private ImportacionTrabajo obtenerTrabajoOLanzar(Long id) {
        return importacionTrabajoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe la copia de trabajo con id: " + id));
    }

    private PlantillaImportacion obtenerPlantillaActivaOLanzar(Long plantillaId) {
        PlantillaImportacion plantilla = plantillaImportacionRepository.findById(plantillaId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe la plantilla de importación con id: " + plantillaId));

        if (!Boolean.TRUE.equals(plantilla.getActiva())) {
            throw new IllegalArgumentException("La plantilla está desactivada.");
        }

        return plantilla;
    }

    private List<MapeoCampoImportacion> obtenerMapeosActivosOLanzar(Long plantillaId) {
        List<MapeoCampoImportacion> mapeos = mapeoCampoImportacionRepository.findByPlantillaIdAndActivoTrue(plantillaId);

        if (mapeos.isEmpty()) {
            throw new IllegalArgumentException("La plantilla no tiene mapeos activos.");
        }

        return mapeos;
    }
}
