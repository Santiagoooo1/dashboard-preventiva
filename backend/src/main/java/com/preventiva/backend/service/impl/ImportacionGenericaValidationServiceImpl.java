package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.ColumnaMapeadaDto;
import com.preventiva.backend.dto.ErrorFilaImportacionGenericaDto;
import com.preventiva.backend.dto.ValidacionFilasImportacionGenericaResponseDto;
import com.preventiva.backend.dto.ValidacionImportacionGenericaResponseDto;
import com.preventiva.backend.entity.MapeoCampoImportacion;
import com.preventiva.backend.entity.PlantillaImportacion;
import com.preventiva.backend.enums.SeveridadError;
import com.preventiva.backend.repository.MapeoCampoImportacionRepository;
import com.preventiva.backend.repository.PlantillaImportacionRepository;
import com.preventiva.backend.service.interfaces.ImportacionGenericaValidationService;
import com.preventiva.backend.util.CampoClinicoValueEvaluator;
import com.preventiva.backend.util.TextNormalizer;
import com.preventiva.backend.util.WorkbookLoader;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImportacionGenericaValidationServiceImpl implements ImportacionGenericaValidationService {

    private final PlantillaImportacionRepository plantillaImportacionRepository;
    private final MapeoCampoImportacionRepository mapeoCampoImportacionRepository;

    private final DataFormatter dataFormatter = new DataFormatter();

    @Override
    public ValidacionImportacionGenericaResponseDto validarCabeceras(
            MultipartFile archivo,
            Long plantillaId,
            Integer indiceHoja,
            Integer filaCabecera) {
        PlantillaImportacion plantilla = obtenerPlantillaActivaOLanzar(plantillaId);
        List<MapeoCampoImportacion> mapeos = obtenerMapeosActivosOLanzar(plantillaId);

        Map<String, MapeoCampoImportacion> mapeosPorNombreNormalizado = mapeos.stream()
                .collect(Collectors.toMap(
                        m -> TextNormalizer.normalize(m.getNombreColumnaOrigen()),
                        m -> m,
                        (a, b) -> a));

        int indiceHojaEfectivo = indiceHoja != null ? indiceHoja : 0;
        int filaCabeceraEfectiva = resolverFilaCabecera(filaCabecera, plantilla);

        LinkedHashMap<Integer, String> cabeceras;

        try {
            cabeceras = WorkbookLoader.leerCabecerasConIndice(archivo, indiceHojaEfectivo, filaCabeceraEfectiva);
        } catch (Exception e) {
            return respuestaCabecerasIlegible(archivo, plantilla, e);
        }

        List<ColumnaMapeadaDto> columnasDetectadas = new ArrayList<>();
        List<String> columnasNoReconocidas = new ArrayList<>();
        Set<String> presentesNormalizadas = new HashSet<>();

        for (Map.Entry<Integer, String> entry : cabeceras.entrySet()) {
            String nombreOriginal = entry.getValue();
            String normalizado = TextNormalizer.normalize(nombreOriginal);
            presentesNormalizadas.add(normalizado);

            MapeoCampoImportacion mapeo = mapeosPorNombreNormalizado.get(normalizado);

            if (mapeo != null) {
                columnasDetectadas.add(ColumnaMapeadaDto.builder()
                        .indiceColumna(entry.getKey())
                        .nombreColumna(nombreOriginal)
                        .reconocida(true)
                        .campoClinicoCodigo(mapeo.getCampoClinico().getCodigo())
                        .tipoDato(mapeo.getTipoDato().name())
                        .build());
            } else {
                columnasNoReconocidas.add(nombreOriginal);

                columnasDetectadas.add(ColumnaMapeadaDto.builder()
                        .indiceColumna(entry.getKey())
                        .nombreColumna(nombreOriginal)
                        .reconocida(false)
                        .campoClinicoCodigo(null)
                        .tipoDato(null)
                        .build());
            }
        }

        List<String> camposObligatoriosFaltantes = mapeos.stream()
                .filter(m -> Boolean.TRUE.equals(m.getObligatorio()))
                .filter(m -> !presentesNormalizadas.contains(TextNormalizer.normalize(m.getNombreColumnaOrigen())))
                .map(MapeoCampoImportacion::getNombreColumnaOrigen)
                .distinct()
                .toList();

        int reconocidas = (int) columnasDetectadas.stream()
                .filter(c -> Boolean.TRUE.equals(c.getReconocida()))
                .count();

        boolean importable = camposObligatoriosFaltantes.isEmpty();
        boolean valida = columnasNoReconocidas.isEmpty() && camposObligatoriosFaltantes.isEmpty();

        List<String> advertencias = columnasNoReconocidas.stream()
                .map(c -> "Columna '" + c + "' no reconocida; será ignorada en la importación.")
                .toList();

        return ValidacionImportacionGenericaResponseDto.builder()
                .nombreArchivo(archivo.getOriginalFilename())
                .plantillaId(plantilla.getId())
                .datasetId(plantilla.getDataset().getId())
                .totalColumnasDetectadas(cabeceras.size())
                .totalColumnasReconocidas(reconocidas)
                .totalColumnasNoReconocidas(columnasNoReconocidas.size())
                .columnasDetectadas(columnasDetectadas)
                .columnasNoReconocidas(columnasNoReconocidas)
                .camposObligatoriosFaltantes(camposObligatoriosFaltantes)
                .valida(valida)
                .importable(importable)
                .advertencias(advertencias)
                .resumen(construirResumenCabecera(columnasNoReconocidas.size(), camposObligatoriosFaltantes.size(), importable))
                .build();
    }

    @Override
    public ValidacionFilasImportacionGenericaResponseDto validarFilas(
            MultipartFile archivo,
            Long plantillaId,
            Integer indiceHoja,
            Integer filaCabecera) {
        PlantillaImportacion plantilla = obtenerPlantillaActivaOLanzar(plantillaId);
        List<MapeoCampoImportacion> mapeos = obtenerMapeosActivosOLanzar(plantillaId);

        Map<String, MapeoCampoImportacion> mapeosPorNombreNormalizado = mapeos.stream()
                .collect(Collectors.toMap(
                        m -> TextNormalizer.normalize(m.getNombreColumnaOrigen()),
                        m -> m,
                        (a, b) -> a));

        int indiceHojaEfectivo = indiceHoja != null ? indiceHoja : 0;
        int filaCabeceraEfectiva = resolverFilaCabecera(filaCabecera, plantilla);

        try {
            if (WorkbookLoader.esCsv(archivo)) {
                return validarFilasCsv(archivo, plantilla, indiceHojaEfectivo, filaCabeceraEfectiva, mapeos, mapeosPorNombreNormalizado);
            }

            return validarFilasExcel(archivo, plantilla, indiceHojaEfectivo, filaCabeceraEfectiva, mapeos, mapeosPorNombreNormalizado);

        } catch (Exception e) {
            return respuestaFilasIlegible(archivo, plantilla, indiceHojaEfectivo, filaCabeceraEfectiva, e);
        }
    }

    private ValidacionFilasImportacionGenericaResponseDto validarFilasExcel(
            MultipartFile archivo,
            PlantillaImportacion plantilla,
            int indiceHoja,
            int filaCabecera,
            List<MapeoCampoImportacion> mapeos,
            Map<String, MapeoCampoImportacion> mapeosPorNombreNormalizado) throws Exception {
        List<ErrorFilaImportacionGenericaDto> errores = new ArrayList<>();
        int totalFilasLeidas = 0;
        int filasValidas = 0;
        int filasConError = 0;
        int filasConAdvertencia = 0;

        try (Workbook workbook = WorkbookLoader.abrirWorkbook(archivo)) {
            Sheet sheet = obtenerHoja(workbook, indiceHoja);
            Row headerRow = sheet.getRow(filaCabecera);

            if (headerRow == null) {
                throw new IllegalArgumentException("El Excel no contiene fila de cabeceras en la fila indicada.");
            }

            Map<Integer, MapeoCampoImportacion> mapeosPorIndiceColumna =
                    obtenerMapeosPorIndiceColumna(headerRow, mapeosPorNombreNormalizado);

            agregarAdvertenciasColumnasNoReconocidas(headerRow, mapeosPorNombreNormalizado, errores, filaCabecera);
            validarColumnasObligatorias(mapeos, obtenerColumnasPresentesNormalizadas(headerRow), errores, filaCabecera);

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

                if (!tieneAlgunValorMapeado(valoresPorIndice, mapeosPorIndiceColumna)) {
                    continue;
                }

                totalFilasLeidas++;

                int erroresAntes = errores.size();

                for (Map.Entry<Integer, MapeoCampoImportacion> entry : mapeosPorIndiceColumna.entrySet()) {
                    String valor = valoresPorIndice.get(entry.getKey());
                    CampoClinicoValueEvaluator.Resultado resultado =
                            CampoClinicoValueEvaluator.evaluar(entry.getValue(), valor, i + 1);

                    if (resultado.getError() != null) {
                        errores.add(resultado.getError());
                    }
                }

                List<ErrorFilaImportacionGenericaDto> nuevos = errores.subList(erroresAntes, errores.size());

                if (contieneSeveridad(nuevos, SeveridadError.ERROR)) {
                    filasConError++;
                } else if (contieneSeveridad(nuevos, SeveridadError.ADVERTENCIA)) {
                    filasConAdvertencia++;
                } else {
                    filasValidas++;
                }
            }

            if (totalFilasLeidas == 0) {
                errores.add(construirErrorSinFilas(filaCabecera));
            }
        }

        return construirRespuestaFilas(archivo.getOriginalFilename(), plantilla, indiceHoja, filaCabecera,
                totalFilasLeidas, filasValidas, filasConError, filasConAdvertencia, errores);
    }

    private ValidacionFilasImportacionGenericaResponseDto validarFilasCsv(
            MultipartFile archivo,
            PlantillaImportacion plantilla,
            int indiceHoja,
            int filaCabecera,
            List<MapeoCampoImportacion> mapeos,
            Map<String, MapeoCampoImportacion> mapeosPorNombreNormalizado) throws Exception {
        List<ErrorFilaImportacionGenericaDto> errores = new ArrayList<>();
        int totalFilasLeidas = 0;
        int filasValidas = 0;
        int filasConError = 0;
        int filasConAdvertencia = 0;

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
                    if (linea.isBlank()) {
                        throw new IllegalArgumentException("El CSV no contiene fila de cabeceras en la fila indicada.");
                    }

                    List<String> cabeceras = WorkbookLoader.parsearLineaCsv(linea, separador);
                    mapeosPorIndiceColumna = obtenerMapeosPorIndiceColumnaCsv(cabeceras, mapeosPorNombreNormalizado);

                    agregarAdvertenciasColumnasNoReconocidasCsv(cabeceras, mapeosPorNombreNormalizado, errores, filaCabecera);
                    validarColumnasObligatorias(mapeos, obtenerColumnasPresentesNormalizadasCsv(cabeceras), errores, filaCabecera);

                    cabeceraProcesada = true;
                    numeroFila++;
                    continue;
                }

                if (!cabeceraProcesada) {
                    throw new IllegalArgumentException("El CSV no contiene fila de cabeceras en la fila indicada.");
                }

                if (linea.isBlank()) {
                    numeroFila++;
                    continue;
                }

                List<String> valores = WorkbookLoader.parsearLineaCsv(linea, separador);

                if (filaCsvVacia(valores)) {
                    numeroFila++;
                    continue;
                }

                if (!tieneAlgunValorMapeadoCsv(valores, mapeosPorIndiceColumna)) {
                    numeroFila++;
                    continue;
                }

                totalFilasLeidas++;

                int erroresAntes = errores.size();

                for (Map.Entry<Integer, MapeoCampoImportacion> entry : mapeosPorIndiceColumna.entrySet()) {
                    String valor = obtenerValorCsv(valores, entry.getKey());
                    CampoClinicoValueEvaluator.Resultado resultado =
                            CampoClinicoValueEvaluator.evaluar(entry.getValue(), valor, numeroFila + 1);

                    if (resultado.getError() != null) {
                        errores.add(resultado.getError());
                    }
                }

                List<ErrorFilaImportacionGenericaDto> nuevos = errores.subList(erroresAntes, errores.size());

                if (contieneSeveridad(nuevos, SeveridadError.ERROR)) {
                    filasConError++;
                } else if (contieneSeveridad(nuevos, SeveridadError.ADVERTENCIA)) {
                    filasConAdvertencia++;
                } else {
                    filasValidas++;
                }

                numeroFila++;
            }

            if (!cabeceraProcesada) {
                throw new IllegalArgumentException("El CSV no contiene fila de cabeceras en la fila indicada.");
            }

            if (totalFilasLeidas == 0) {
                errores.add(construirErrorSinFilas(filaCabecera));
            }
        }

        return construirRespuestaFilas(archivo.getOriginalFilename(), plantilla, indiceHoja, filaCabecera,
                totalFilasLeidas, filasValidas, filasConError, filasConAdvertencia, errores);
    }

    private ValidacionFilasImportacionGenericaResponseDto construirRespuestaFilas(
            String nombreArchivo,
            PlantillaImportacion plantilla,
            int indiceHoja,
            int filaCabecera,
            int totalFilasLeidas,
            int filasValidas,
            int filasConError,
            int filasConAdvertencia,
            List<ErrorFilaImportacionGenericaDto> errores) {
        List<ErrorFilaImportacionGenericaDto> erroresBloqueantes = filtrarPorSeveridad(errores, SeveridadError.ERROR);
        List<ErrorFilaImportacionGenericaDto> advertencias = filtrarPorSeveridad(errores, SeveridadError.ADVERTENCIA);

        boolean importable = erroresBloqueantes.isEmpty();
        boolean valida = errores.isEmpty();

        return ValidacionFilasImportacionGenericaResponseDto.builder()
                .nombreArchivo(nombreArchivo)
                .plantillaId(plantilla.getId())
                .datasetId(plantilla.getDataset().getId())
                .indiceHoja(indiceHoja)
                .filaCabecera(filaCabecera)
                .totalFilasLeidas(totalFilasLeidas)
                .filasValidas(filasValidas)
                .filasConError(filasConError)
                .filasConAdvertencia(filasConAdvertencia)
                .errores(errores)
                .erroresBloqueantes(erroresBloqueantes)
                .advertencias(advertencias)
                .totalAdvertencias(advertencias.size())
                .valida(valida)
                .importable(importable)
                .resumen(construirResumenFilas(totalFilasLeidas, filasConAdvertencia, advertencias.size(), importable))
                .build();
    }

    private ValidacionImportacionGenericaResponseDto respuestaCabecerasIlegible(
            MultipartFile archivo, PlantillaImportacion plantilla, Exception e) {
        return ValidacionImportacionGenericaResponseDto.builder()
                .nombreArchivo(archivo.getOriginalFilename())
                .plantillaId(plantilla.getId())
                .datasetId(plantilla.getDataset().getId())
                .totalColumnasDetectadas(0)
                .totalColumnasReconocidas(0)
                .totalColumnasNoReconocidas(0)
                .columnasDetectadas(List.of())
                .columnasNoReconocidas(List.of())
                .camposObligatoriosFaltantes(List.of())
                .valida(false)
                .importable(false)
                .advertencias(List.of())
                .resumen("No se pudo leer el archivo: " + e.getMessage())
                .build();
    }

    private ValidacionFilasImportacionGenericaResponseDto respuestaFilasIlegible(
            MultipartFile archivo, PlantillaImportacion plantilla, int indiceHoja, int filaCabecera, Exception e) {
        ErrorFilaImportacionGenericaDto error = ErrorFilaImportacionGenericaDto.builder()
                .numeroFila(filaCabecera + 1)
                .nombreColumna(null)
                .valorOriginal(null)
                .tipoError("ARCHIVO_ILEGIBLE")
                .mensaje("No se pudo leer el archivo: " + e.getMessage())
                .severidad(SeveridadError.ERROR.name())
                .build();

        List<ErrorFilaImportacionGenericaDto> errores = List.of(error);

        return ValidacionFilasImportacionGenericaResponseDto.builder()
                .nombreArchivo(archivo.getOriginalFilename())
                .plantillaId(plantilla.getId())
                .datasetId(plantilla.getDataset().getId())
                .indiceHoja(indiceHoja)
                .filaCabecera(filaCabecera)
                .totalFilasLeidas(0)
                .filasValidas(0)
                .filasConError(0)
                .filasConAdvertencia(0)
                .errores(errores)
                .erroresBloqueantes(errores)
                .advertencias(List.of())
                .totalAdvertencias(0)
                .valida(false)
                .importable(false)
                .resumen("No se pudo leer el archivo: " + e.getMessage())
                .build();
    }

    private void agregarAdvertenciasColumnasNoReconocidas(
            Row headerRow,
            Map<String, MapeoCampoImportacion> mapeosPorNombreNormalizado,
            List<ErrorFilaImportacionGenericaDto> errores,
            int filaCabecera) {
        for (Cell cell : headerRow) {
            String nombreColumna = obtenerValorCeldaComoTexto(cell);

            if (nombreColumna == null || nombreColumna.isBlank()) {
                continue;
            }

            String normalizada = TextNormalizer.normalize(nombreColumna);

            if (!mapeosPorNombreNormalizado.containsKey(normalizada)) {
                errores.add(ErrorFilaImportacionGenericaDto.builder()
                        .numeroFila(filaCabecera + 1)
                        .nombreColumna(nombreColumna)
                        .valorOriginal(null)
                        .tipoError("COLUMNA_NO_RECONOCIDA")
                        .mensaje("La columna no está mapeada en la plantilla y será ignorada en la importación.")
                        .severidad(SeveridadError.ADVERTENCIA.name())
                        .build());
            }
        }
    }

    private void agregarAdvertenciasColumnasNoReconocidasCsv(
            List<String> cabeceras,
            Map<String, MapeoCampoImportacion> mapeosPorNombreNormalizado,
            List<ErrorFilaImportacionGenericaDto> errores,
            int filaCabecera) {
        for (String nombreColumna : cabeceras) {
            if (nombreColumna == null || nombreColumna.isBlank()) {
                continue;
            }

            String normalizada = TextNormalizer.normalize(nombreColumna);

            if (!mapeosPorNombreNormalizado.containsKey(normalizada)) {
                errores.add(ErrorFilaImportacionGenericaDto.builder()
                        .numeroFila(filaCabecera + 1)
                        .nombreColumna(nombreColumna)
                        .valorOriginal(null)
                        .tipoError("COLUMNA_NO_RECONOCIDA")
                        .mensaje("La columna no está mapeada en la plantilla y será ignorada en la importación.")
                        .severidad(SeveridadError.ADVERTENCIA.name())
                        .build());
            }
        }
    }

    private void validarColumnasObligatorias(
            List<MapeoCampoImportacion> mapeos,
            Set<String> columnasPresentesNormalizadas,
            List<ErrorFilaImportacionGenericaDto> errores,
            int filaCabecera) {
        for (MapeoCampoImportacion mapeo : mapeos) {
            if (!Boolean.TRUE.equals(mapeo.getObligatorio())) {
                continue;
            }

            String columnaNormalizada = TextNormalizer.normalize(mapeo.getNombreColumnaOrigen());

            if (!columnasPresentesNormalizadas.contains(columnaNormalizada)) {
                errores.add(ErrorFilaImportacionGenericaDto.builder()
                        .numeroFila(filaCabecera + 1)
                        .nombreColumna(mapeo.getNombreColumnaOrigen())
                        .valorOriginal(null)
                        .tipoError("COLUMNA_OBLIGATORIA_FALTANTE")
                        .mensaje("El campo clínico obligatorio '" + mapeo.getCampoClinico().getCodigo()
                                + "' no tiene columna origen en el archivo.")
                        .severidad(SeveridadError.ERROR.name())
                        .build());
            }
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

            String normalizada = TextNormalizer.normalize(nombreColumna);
            MapeoCampoImportacion mapeo = mapeosPorNombreNormalizado.get(normalizada);

            if (mapeo != null) {
                resultado.put(cell.getColumnIndex(), mapeo);
            }
        }

        return resultado;
    }

    private Map<Integer, MapeoCampoImportacion> obtenerMapeosPorIndiceColumnaCsv(
            List<String> cabeceras, Map<String, MapeoCampoImportacion> mapeosPorNombreNormalizado) {
        Map<Integer, MapeoCampoImportacion> resultado = new HashMap<>();

        for (int i = 0; i < cabeceras.size(); i++) {
            String nombreColumna = cabeceras.get(i);

            if (nombreColumna == null || nombreColumna.isBlank()) {
                continue;
            }

            String normalizada = TextNormalizer.normalize(nombreColumna);
            MapeoCampoImportacion mapeo = mapeosPorNombreNormalizado.get(normalizada);

            if (mapeo != null) {
                resultado.put(i, mapeo);
            }
        }

        return resultado;
    }

    private Set<String> obtenerColumnasPresentesNormalizadas(Row headerRow) {
        Set<String> resultado = new HashSet<>();

        for (Cell cell : headerRow) {
            String nombreColumna = obtenerValorCeldaComoTexto(cell);

            if (nombreColumna != null && !nombreColumna.isBlank()) {
                resultado.add(TextNormalizer.normalize(nombreColumna));
            }
        }

        return resultado;
    }

    private Set<String> obtenerColumnasPresentesNormalizadasCsv(List<String> cabeceras) {
        Set<String> resultado = new HashSet<>();

        for (String nombreColumna : cabeceras) {
            if (nombreColumna != null && !nombreColumna.isBlank()) {
                resultado.add(TextNormalizer.normalize(nombreColumna));
            }
        }

        return resultado;
    }

    private boolean tieneAlgunValorMapeado(Map<Integer, String> valores, Map<Integer, MapeoCampoImportacion> mapeosPorIndice) {
        return mapeosPorIndice.keySet().stream()
                .map(valores::get)
                .anyMatch(v -> v != null && !v.isBlank());
    }

    private boolean tieneAlgunValorMapeadoCsv(List<String> valores, Map<Integer, MapeoCampoImportacion> mapeosPorIndice) {
        return mapeosPorIndice.keySet().stream()
                .map(i -> obtenerValorCsv(valores, i))
                .anyMatch(v -> v != null && !v.isBlank());
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

    private boolean filaCsvVacia(List<String> valores) {
        if (valores == null || valores.isEmpty()) {
            return true;
        }

        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return false;
            }
        }

        return true;
    }

    private String obtenerValorCsv(List<String> valores, int indiceColumna) {
        if (valores == null || indiceColumna < 0 || indiceColumna >= valores.size()) {
            return "";
        }

        String valor = valores.get(indiceColumna);

        return valor == null ? "" : valor.trim();
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

    private boolean contieneSeveridad(List<ErrorFilaImportacionGenericaDto> lista, SeveridadError severidad) {
        return lista.stream().anyMatch(e -> severidad.name().equals(e.getSeveridad()));
    }

    private List<ErrorFilaImportacionGenericaDto> filtrarPorSeveridad(
            List<ErrorFilaImportacionGenericaDto> errores, SeveridadError severidad) {
        return errores.stream().filter(e -> severidad.name().equals(e.getSeveridad())).toList();
    }

    private ErrorFilaImportacionGenericaDto construirErrorSinFilas(int filaCabecera) {
        return ErrorFilaImportacionGenericaDto.builder()
                .numeroFila(filaCabecera + 1)
                .nombreColumna(null)
                .valorOriginal(null)
                .tipoError("SIN_FILAS_CLINICAS")
                .mensaje("El archivo no contiene ninguna fila clínica reconocible "
                        + "(ningún campo mapeado tiene valor en ninguna fila).")
                .severidad(SeveridadError.ERROR.name())
                .build();
    }

    private String construirResumenCabecera(int totalNoReconocidas, int totalObligatoriasFaltantes, boolean importable) {
        if (!importable) {
            return "Faltan " + totalObligatoriasFaltantes + " columna(s) obligatoria(s). El archivo no es importable.";
        }

        if (totalNoReconocidas > 0) {
            return totalNoReconocidas + " columna(s) no reconocida(s) serán ignoradas. El archivo es importable.";
        }

        return "Todas las columnas fueron reconocidas. El archivo es importable.";
    }

    private String construirResumenFilas(int totalFilasLeidas, int filasConAdvertencia, int totalAdvertencias, boolean importable) {
        if (!importable) {
            return "El archivo tiene errores bloqueantes y no puede importarse.";
        }

        if (totalAdvertencias > 0) {
            return totalFilasLeidas + " fila(s) leída(s), " + filasConAdvertencia + " con advertencia(s) ("
                    + totalAdvertencias + " advertencia(s) en total). El archivo es importable.";
        }

        return totalFilasLeidas + " fila(s) leída(s) sin errores ni advertencias. El archivo es importable.";
    }

    private int resolverFilaCabecera(Integer filaCabecera, PlantillaImportacion plantilla) {
        if (filaCabecera != null) {
            return filaCabecera;
        }

        return plantilla.getFilaCabecera() != null ? plantilla.getFilaCabecera() : 0;
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
