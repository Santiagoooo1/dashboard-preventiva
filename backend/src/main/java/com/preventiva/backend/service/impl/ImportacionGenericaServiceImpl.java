package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.ErrorFilaImportacionGenericaDto;
import com.preventiva.backend.dto.ErrorImportacionGenericaResponseDto;
import com.preventiva.backend.dto.ImportacionGenericaResponseDto;
import com.preventiva.backend.dto.ValidacionFilasImportacionGenericaResponseDto;
import com.preventiva.backend.entity.ErrorImportacionGenerica;
import com.preventiva.backend.entity.ImportacionGenerica;
import com.preventiva.backend.entity.MapeoCampoImportacion;
import com.preventiva.backend.entity.PlantillaImportacion;
import com.preventiva.backend.entity.RegistroClinicoGenerico;
import com.preventiva.backend.enums.EstadoImportacion;
import com.preventiva.backend.enums.SeveridadError;
import com.preventiva.backend.enums.TipoErrorImportacion;
import com.preventiva.backend.repository.ErrorImportacionGenericaRepository;
import com.preventiva.backend.repository.ImportacionGenericaRepository;
import com.preventiva.backend.repository.MapeoCampoImportacionRepository;
import com.preventiva.backend.repository.PlantillaImportacionRepository;
import com.preventiva.backend.repository.RegistroClinicoGenericoRepository;
import com.preventiva.backend.service.interfaces.ImportacionGenericaService;
import com.preventiva.backend.service.interfaces.ImportacionGenericaValidationService;
import com.preventiva.backend.util.CampoClinicoValueEvaluator;
import com.preventiva.backend.util.TextNormalizer;
import com.preventiva.backend.util.WorkbookLoader;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImportacionGenericaServiceImpl implements ImportacionGenericaService {

    private static final Set<String> CAMPOS_COMUNES = Set.of(
            "pacienteCodigo", "fechaEvento", "servicio", "tipoEvento",
            "procedimiento", "diagnostico", "edad", "sexo");

    private final ImportacionGenericaValidationService validationService;
    private final PlantillaImportacionRepository plantillaImportacionRepository;
    private final MapeoCampoImportacionRepository mapeoCampoImportacionRepository;
    private final ImportacionGenericaRepository importacionGenericaRepository;
    private final ErrorImportacionGenericaRepository errorImportacionGenericaRepository;
    private final RegistroClinicoGenericoRepository registroClinicoGenericoRepository;

    private final DataFormatter dataFormatter = new DataFormatter();

    @Override
    @Transactional
    public ImportacionGenericaResponseDto importar(
            MultipartFile archivo,
            Long plantillaId,
            Integer indiceHoja,
            Integer filaCabecera) {
        PlantillaImportacion plantilla = plantillaImportacionRepository.findById(plantillaId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe la plantilla de importación con id: " + plantillaId));

        if (!Boolean.TRUE.equals(plantilla.getActiva())) {
            throw new IllegalArgumentException("La plantilla está desactivada.");
        }

        int indiceHojaEfectivo = indiceHoja != null ? indiceHoja : 0;
        int filaCabeceraEfectiva = filaCabecera != null
                ? filaCabecera
                : (plantilla.getFilaCabecera() != null ? plantilla.getFilaCabecera() : 0);

        ValidacionFilasImportacionGenericaResponseDto validacion = validationService.validarFilas(
                archivo, plantillaId, indiceHojaEfectivo, filaCabeceraEfectiva);

        boolean importable = Boolean.TRUE.equals(validacion.getImportable());

        ImportacionGenerica importacion = ImportacionGenerica.builder()
                .nombreArchivo(generarNombreInterno(archivo))
                .nombreOriginal(archivo.getOriginalFilename())
                .fechaImportacion(LocalDateTime.now())
                .filasLeidas(validacion.getTotalFilasLeidas())
                .filasImportadas(0)
                .filasConError(validacion.getFilasConError())
                .estado(importable ? EstadoImportacion.PENDIENTE : EstadoImportacion.RECHAZADA)
                .plantilla(plantilla)
                .usuario(null)
                .build();

        importacion = importacionGenericaRepository.save(importacion);

        guardarErrores(importacion, validacion.getErrores());

        if (!importable) {
            return mapImportacionToDto(importacion, validacion.getTotalAdvertencias(),
                    "El archivo tiene errores bloqueantes. No se ha importado ningún registro.");
        }

        List<MapeoCampoImportacion> mapeos = mapeoCampoImportacionRepository.findByPlantillaIdAndActivoTrue(plantillaId);

        int filasImportadas;

        try {
            filasImportadas = importarFilas(archivo, plantilla, mapeos, indiceHojaEfectivo, filaCabeceraEfectiva, importacion);
        } catch (Exception e) {
            throw new RuntimeException("Error al importar el archivo: " + e.getMessage(), e);
        }

        boolean tieneAdvertencias = validacion.getTotalAdvertencias() != null && validacion.getTotalAdvertencias() > 0;

        importacion.setFilasImportadas(filasImportadas);
        importacion.setFilasConError(0);
        importacion.setEstado(tieneAdvertencias ? EstadoImportacion.IMPORTADA_CON_ERRORES : EstadoImportacion.IMPORTADA);
        importacionGenericaRepository.save(importacion);

        return mapImportacionToDto(importacion, validacion.getTotalAdvertencias(),
                tieneAdvertencias
                        ? "Importación realizada con advertencias. Consulte GET /api/importaciones-genericas/"
                                + importacion.getId() + "/errores para el detalle."
                        : "Importación realizada correctamente.");
    }

    @Override
    public ImportacionGenericaResponseDto obtenerPorId(Long id) {
        ImportacionGenerica importacion = importacionGenericaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe la importación con id: " + id));

        long totalAdvertencias = errorImportacionGenericaRepository.findByImportacionGenericaId(id).stream()
                .filter(e -> e.getSeveridad() == SeveridadError.ADVERTENCIA)
                .count();

        return mapImportacionToDto(importacion, (int) totalAdvertencias, null);
    }

    @Override
    public List<ErrorImportacionGenericaResponseDto> listarErrores(Long importacionId) {
        importacionGenericaRepository.findById(importacionId)
                .orElseThrow(() -> new NoSuchElementException("No existe la importación con id: " + importacionId));

        return errorImportacionGenericaRepository.findByImportacionGenericaId(importacionId)
                .stream()
                .map(this::mapErrorToDto)
                .toList();
    }

    private int importarFilas(
            MultipartFile archivo,
            PlantillaImportacion plantilla,
            List<MapeoCampoImportacion> mapeos,
            int indiceHoja,
            int filaCabecera,
            ImportacionGenerica importacionGenerica) throws Exception {
        Map<String, MapeoCampoImportacion> mapeosPorNombreNormalizado = mapeos.stream()
                .collect(Collectors.toMap(
                        m -> TextNormalizer.normalize(m.getNombreColumnaOrigen()),
                        m -> m,
                        (a, b) -> a));

        if (WorkbookLoader.esCsv(archivo)) {
            return importarFilasCsv(archivo, plantilla, filaCabecera, mapeosPorNombreNormalizado, importacionGenerica);
        }

        return importarFilasExcel(archivo, plantilla, indiceHoja, filaCabecera, mapeosPorNombreNormalizado, importacionGenerica);
    }

    private int importarFilasExcel(
            MultipartFile archivo,
            PlantillaImportacion plantilla,
            int indiceHoja,
            int filaCabecera,
            Map<String, MapeoCampoImportacion> mapeosPorNombreNormalizado,
            ImportacionGenerica importacionGenerica) throws Exception {
        int filasImportadas = 0;

        try (Workbook workbook = WorkbookLoader.abrirWorkbook(archivo)) {
            Sheet sheet = workbook.getSheetAt(indiceHoja);
            Row headerRow = sheet.getRow(filaCabecera);
            Map<Integer, MapeoCampoImportacion> mapeosPorIndiceColumna =
                    obtenerMapeosPorIndiceColumna(headerRow, mapeosPorNombreNormalizado);

            int ultimaFila = sheet.getLastRowNum();

            for (int i = filaCabecera + 1; i <= ultimaFila; i++) {
                Row row = sheet.getRow(i);

                if (filaVacia(row)) {
                    continue;
                }

                Map<Integer, String> valoresPorIndice = new HashMap<>();

                for (Integer indiceColumna : mapeosPorIndiceColumna.keySet()) {
                    Cell cell = obtenerCeldaConSoporteCombinadas(sheet, i, indiceColumna);
                    valoresPorIndice.put(indiceColumna, obtenerValorCeldaComoTexto(cell));
                }

                boolean tieneDato = mapeosPorIndiceColumna.keySet().stream()
                        .map(valoresPorIndice::get)
                        .anyMatch(v -> v != null && !v.isBlank());

                if (!tieneDato) {
                    continue;
                }

                guardarRegistro(plantilla, mapeosPorIndiceColumna, valoresPorIndice, importacionGenerica);
                filasImportadas++;
            }
        }

        return filasImportadas;
    }

    private int importarFilasCsv(
            MultipartFile archivo,
            PlantillaImportacion plantilla,
            int filaCabecera,
            Map<String, MapeoCampoImportacion> mapeosPorNombreNormalizado,
            ImportacionGenerica importacionGenerica) throws Exception {
        int filasImportadas = 0;
        boolean cabeceraProcesada = false;
        Map<Integer, MapeoCampoImportacion> mapeosPorIndiceColumna = new HashMap<>();

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
                        }
                    }

                    cabeceraProcesada = true;
                    numeroFila++;
                    continue;
                }

                if (!cabeceraProcesada || linea.isBlank()) {
                    numeroFila++;
                    continue;
                }

                List<String> valores = WorkbookLoader.parsearLineaCsv(linea, separador);

                boolean tieneDato = mapeosPorIndiceColumna.keySet().stream()
                        .map(i -> obtenerValorCsv(valores, i))
                        .anyMatch(v -> v != null && !v.isBlank());

                if (!tieneDato) {
                    numeroFila++;
                    continue;
                }

                Map<Integer, String> valoresPorIndice = new HashMap<>();

                for (Integer indiceColumna : mapeosPorIndiceColumna.keySet()) {
                    valoresPorIndice.put(indiceColumna, obtenerValorCsv(valores, indiceColumna));
                }

                guardarRegistro(plantilla, mapeosPorIndiceColumna, valoresPorIndice, importacionGenerica);
                filasImportadas++;

                numeroFila++;
            }
        }

        return filasImportadas;
    }

    private void guardarRegistro(
            PlantillaImportacion plantilla,
            Map<Integer, MapeoCampoImportacion> mapeosPorIndiceColumna,
            Map<Integer, String> valoresPorIndice,
            ImportacionGenerica importacionGenerica) {
        Map<String, Object> datosDinamicos = new HashMap<>();

        RegistroClinicoGenerico registro = RegistroClinicoGenerico.builder()
                .dataset(plantilla.getDataset())
                .hospital(plantilla.getDataset().getHospital())
                .importacion(importacionGenerica)
                .fechaCreacion(LocalDateTime.now())
                .build();

        for (Map.Entry<Integer, MapeoCampoImportacion> entry : mapeosPorIndiceColumna.entrySet()) {
            MapeoCampoImportacion mapeo = entry.getValue();
            String valorOriginal = valoresPorIndice.get(entry.getKey());

            CampoClinicoValueEvaluator.Resultado resultado =
                    CampoClinicoValueEvaluator.evaluar(mapeo, valorOriginal, 0);

            if (!resultado.isPresente()) {
                continue;
            }

            Object valorFinal = resultado.getValor();
            String codigo = mapeo.getCampoClinico().getCodigo();

            if (Boolean.TRUE.equals(mapeo.getCampoClinico().getEsComun()) && CAMPOS_COMUNES.contains(codigo)) {
                asignarCampoComun(registro, codigo, valorFinal);
            } else if (valorFinal instanceof LocalDate fecha) {
                datosDinamicos.put(codigo, fecha.toString());
            } else {
                datosDinamicos.put(codigo, valorFinal);
            }
        }

        registro.setDatosDinamicos(datosDinamicos);
        registroClinicoGenericoRepository.save(registro);
    }

    private void asignarCampoComun(RegistroClinicoGenerico registro, String codigo, Object valor) {
        switch (codigo) {
            case "pacienteCodigo" -> registro.setPacienteCodigo(valor != null ? valor.toString() : null);
            case "fechaEvento" -> registro.setFechaEvento(valor instanceof LocalDate fecha ? fecha : null);
            case "servicio" -> registro.setServicio(valor != null ? valor.toString() : null);
            case "tipoEvento" -> registro.setTipoEvento(valor != null ? valor.toString() : null);
            case "procedimiento" -> registro.setProcedimiento(valor != null ? valor.toString() : null);
            case "diagnostico" -> registro.setDiagnostico(valor != null ? valor.toString() : null);
            case "edad" -> registro.setEdad(valor instanceof Integer edad ? edad : null);
            case "sexo" -> registro.setSexo(valor != null ? valor.toString() : null);
            default -> {
                // No debería ocurrir: CAMPOS_COMUNES ya filtra los códigos válidos.
            }
        }
    }

    private void guardarErrores(ImportacionGenerica importacion, List<ErrorFilaImportacionGenericaDto> errores) {
        for (ErrorFilaImportacionGenericaDto errorDto : errores) {
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

            ErrorImportacionGenerica error = ErrorImportacionGenerica.builder()
                    .importacionGenerica(importacion)
                    .numeroFila(errorDto.getNumeroFila())
                    .nombreColumna(errorDto.getNombreColumna())
                    .valorOriginal(errorDto.getValorOriginal())
                    .tipoError(tipoError)
                    .severidad(severidad)
                    .mensaje(errorDto.getMensaje())
                    .build();

            errorImportacionGenericaRepository.save(error);
        }
    }

    private Map<Integer, MapeoCampoImportacion> obtenerMapeosPorIndiceColumna(
            Row headerRow, Map<String, MapeoCampoImportacion> mapeosPorNombreNormalizado) {
        Map<Integer, MapeoCampoImportacion> resultado = new HashMap<>();

        for (Cell cell : headerRow) {
            String nombreColumna = obtenerValorCeldaComoTexto(cell);

            if (nombreColumna == null || nombreColumna.isBlank()) {
                continue;
            }

            MapeoCampoImportacion mapeo = mapeosPorNombreNormalizado.get(TextNormalizer.normalize(nombreColumna));

            if (mapeo != null) {
                resultado.put(cell.getColumnIndex(), mapeo);
            }
        }

        return resultado;
    }

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

    private String generarNombreInterno(MultipartFile archivo) {
        String nombreOriginal = archivo.getOriginalFilename();

        if (nombreOriginal == null || nombreOriginal.isBlank()) {
            nombreOriginal = "archivo.csv";
        }

        return UUID.randomUUID() + "_" + nombreOriginal;
    }

    private ImportacionGenericaResponseDto mapImportacionToDto(
            ImportacionGenerica importacion, Integer totalAdvertencias, String mensaje) {
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
                .mensaje(mensaje)
                .build();
    }

    private ErrorImportacionGenericaResponseDto mapErrorToDto(ErrorImportacionGenerica error) {
        return ErrorImportacionGenericaResponseDto.builder()
                .id(error.getId())
                .numeroFila(error.getNumeroFila())
                .nombreColumna(error.getNombreColumna())
                .valorOriginal(error.getValorOriginal())
                .tipoError(error.getTipoError().name())
                .severidad(error.getSeveridad() != null ? error.getSeveridad().name() : SeveridadError.ERROR.name())
                .mensaje(error.getMensaje())
                .build();
    }
}
