package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.ColumnaExcelDetectadaDto;
import com.preventiva.backend.dto.ErrorFilaExcelDto;
import com.preventiva.backend.dto.ValidacionExcelResponseDto;
import com.preventiva.backend.dto.ValidacionFilasExcelResponseDto;
import com.preventiva.backend.entity.MapeoColumnaExcel;
import com.preventiva.backend.entity.PlantillaExcel;
import com.preventiva.backend.enums.CampoDestino;
import com.preventiva.backend.enums.SeveridadError;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.repository.MapeoColumnaExcelRepository;
import com.preventiva.backend.repository.PlantillaExcelRepository;
import com.preventiva.backend.service.interfaces.ExcelValidationService;
import com.preventiva.backend.util.ClinicalValueNormalizer;
import com.preventiva.backend.util.TextNormalizer;
import com.preventiva.backend.util.WorkbookLoader;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExcelValidationServiceImpl implements ExcelValidationService {

    private final PlantillaExcelRepository plantillaExcelRepository;
    private final MapeoColumnaExcelRepository mapeoColumnaExcelRepository;

    private final DataFormatter dataFormatter = new DataFormatter();

    @Override
    public ValidacionExcelResponseDto validarExcel(
            MultipartFile archivo,
            Long plantillaId,
            Integer indiceHoja) {
        PlantillaExcel plantilla = plantillaExcelRepository.findById(plantillaId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la plantilla con id: " + plantillaId));

        List<MapeoColumnaExcel> mapeos = mapeoColumnaExcelRepository
                .findByPlantillaIdAndActivaTrue(plantillaId);

        Map<String, MapeoColumnaExcel> mapeosPorNombreNormalizado = mapeos.stream()
                .collect(Collectors.toMap(
                        m -> TextNormalizer.normalize(m.getNombreColumnaExcel()),
                        m -> m,
                        (m1, m2) -> m1));

        List<String> columnasExcel = leerCabecerasExcel(archivo, indiceHoja, 0);

        List<ColumnaExcelDetectadaDto> columnasDetectadas = new ArrayList<>();
        List<String> columnasNoReconocidas = new ArrayList<>();

        Set<String> columnasExcelNormalizadas = columnasExcel.stream()
                .map(TextNormalizer::normalize)
                .collect(Collectors.toSet());

        for (String columna : columnasExcel) {
            String normalizada = TextNormalizer.normalize(columna);
            MapeoColumnaExcel mapeo = mapeosPorNombreNormalizado.get(normalizada);

            if (mapeo != null) {
                columnasDetectadas.add(
                        ColumnaExcelDetectadaDto.builder()
                                .nombreColumna(columna)
                                .reconocida(true)
                                .campoDestino(mapeo.getCampoDestino().name())
                                .tipoDato(mapeo.getTipoDato().name())
                                .build());
            } else {
                columnasNoReconocidas.add(columna);

                columnasDetectadas.add(
                        ColumnaExcelDetectadaDto.builder()
                                .nombreColumna(columna)
                                .reconocida(false)
                                .campoDestino(null)
                                .tipoDato(null)
                                .build());
            }
        }

        List<String> columnasObligatoriasFaltantes = mapeos.stream()
                .filter(m -> Boolean.TRUE.equals(m.getObligatoria()))
                .filter(m -> !columnasExcelNormalizadas.contains(
                        TextNormalizer.normalize(m.getNombreColumnaExcel())))
                .map(MapeoColumnaExcel::getNombreColumnaExcel)
                .distinct()
                .toList();

        int reconocidas = columnasDetectadas.stream()
                .filter(c -> Boolean.TRUE.equals(c.getReconocida()))
                .toList()
                .size();

        // Las columnas no reconocidas ya no bloquean la importación: solo generan
        // advertencia informativa. Únicamente las columnas obligatorias faltantes bloquean.
        boolean importable = columnasObligatoriasFaltantes.isEmpty();

        // "valida" mantiene su significado original (archivo perfecto, sin nada que revisar).
        boolean valida = columnasNoReconocidas.isEmpty()
                && columnasObligatoriasFaltantes.isEmpty();

        List<String> advertencias = columnasNoReconocidas.stream()
                .map(columna -> "Columna '" + columna + "' no reconocida; será ignorada en la importación.")
                .toList();

        return ValidacionExcelResponseDto.builder()
                .nombreArchivo(archivo.getOriginalFilename())
                .plantillaId(plantilla.getId())
                .codigoPlantilla(plantilla.getCodigo())
                .totalColumnasDetectadas(columnasExcel.size())
                .totalColumnasReconocidas(reconocidas)
                .totalColumnasNoReconocidas(columnasNoReconocidas.size())
                .columnasDetectadas(columnasDetectadas)
                .columnasNoReconocidas(columnasNoReconocidas)
                .columnasObligatoriasFaltantes(columnasObligatoriasFaltantes)
                .valida(valida)
                .importable(importable)
                .advertencias(advertencias)
                .resumen(construirResumenCabecera(
                        columnasNoReconocidas.size(),
                        columnasObligatoriasFaltantes.size(),
                        importable))
                .build();
    }

    @Override
    public ValidacionFilasExcelResponseDto validarFilasExcel(
            MultipartFile archivo,
            Long plantillaId,
            Integer indiceHoja,
            Integer filaCabecera) {
        PlantillaExcel plantilla = plantillaExcelRepository.findById(plantillaId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la plantilla con id: " + plantillaId));

        List<MapeoColumnaExcel> mapeos = mapeoColumnaExcelRepository
                .findByPlantillaIdAndActivaTrue(plantillaId);

        Map<String, MapeoColumnaExcel> mapeosPorNombreNormalizado = mapeos.stream()
                .collect(Collectors.toMap(
                        m -> TextNormalizer.normalize(m.getNombreColumnaExcel()),
                        m -> m,
                        (m1, m2) -> m1));

        if (indiceHoja == null) {
            indiceHoja = 0;
        }

        if (filaCabecera == null) {
            filaCabecera = 0;
        }

        if (WorkbookLoader.esCsv(archivo)) {
            return validarFilasCsv(
                    archivo,
                    plantilla,
                    plantillaId,
                    indiceHoja,
                    filaCabecera,
                    mapeos,
                    mapeosPorNombreNormalizado);
        }

        List<ErrorFilaExcelDto> errores = new ArrayList<>();

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

            Map<Integer, MapeoColumnaExcel> mapeosPorIndiceColumna = obtenerMapeosPorIndiceColumna(
                    headerRow,
                    mapeosPorNombreNormalizado);

            Set<String> columnasPresentesNormalizadas = obtenerColumnasPresentesNormalizadas(headerRow);

            validarColumnasObligatorias(
                    mapeos,
                    columnasPresentesNormalizadas,
                    errores,
                    filaCabecera);

            agregarAdvertenciasColumnasNoReconocidas(
                    headerRow,
                    mapeosPorNombreNormalizado,
                    errores,
                    filaCabecera);

            int ultimaFila = sheet.getLastRowNum();

            for (int i = filaCabecera + 1; i <= ultimaFila; i++) {
                Row row = sheet.getRow(i);

                if (filaVacia(row)) {
                    continue;
                }

                if (!esFilaClinicaPrincipal(row, mapeosPorIndiceColumna)) {
                    continue;
                }

                totalFilasLeidas++;

                int erroresAntesDeFila = errores.size();

                validarFila(row, sheet, i, mapeosPorIndiceColumna, errores);

                List<ErrorFilaExcelDto> nuevosDeFila = errores.subList(erroresAntesDeFila, errores.size());

                if (contieneSeveridad(nuevosDeFila, SeveridadError.ERROR)) {
                    filasConError++;
                } else if (contieneSeveridad(nuevosDeFila, SeveridadError.ADVERTENCIA)) {
                    filasConAdvertencia++;
                } else {
                    filasValidas++;
                }
            }

            if (totalFilasLeidas == 0) {
                errores.add(ErrorFilaExcelDto.builder()
                        .numeroFila(filaCabecera + 1)
                        .nombreColumna(null)
                        .valorOriginal(null)
                        .tipoError("SIN_FILAS_CLINICAS")
                        .mensaje("El archivo no contiene ninguna fila clínica reconocible "
                                + "(falta HC o un dato clínico relevante en todas las filas).")
                        .severidad(SeveridadError.ERROR.name())
                        .build());
            }

        } catch (Exception e) {
            throw new RuntimeException("Error al validar filas del archivo Excel: " + e.getMessage(), e);
        }

        return construirRespuestaFilas(
                archivo.getOriginalFilename(),
                plantilla,
                indiceHoja,
                filaCabecera,
                totalFilasLeidas,
                filasValidas,
                filasConError,
                filasConAdvertencia,
                errores);
    }

    private List<String> leerCabecerasExcel(
            MultipartFile archivo,
            Integer indiceHoja,
            Integer filaCabecera) {
        try {
            if (WorkbookLoader.esCsv(archivo)) {
                return WorkbookLoader.leerCabecerasCsv(archivo, filaCabecera);
            }

            try (Workbook workbook = WorkbookLoader.abrirWorkbook(archivo)) {

                if (indiceHoja == null) {
                    indiceHoja = 0;
                }

                if (filaCabecera == null) {
                    filaCabecera = 0;
                }

                Sheet sheet = obtenerHoja(workbook, indiceHoja);

                Row headerRow = sheet.getRow(filaCabecera);

                if (headerRow == null) {
                    throw new IllegalArgumentException("El Excel no contiene fila de cabeceras.");
                }

                List<String> cabeceras = new ArrayList<>();

                for (Cell cell : headerRow) {
                    String valor = obtenerValorCeldaComoTexto(cell);

                    if (valor != null && !valor.isBlank()) {
                        cabeceras.add(valor.trim());
                    }
                }

                return cabeceras;
            }

        } catch (Exception e) {
            throw new RuntimeException("Error al leer el archivo Excel/CSV: " + e.getMessage(), e);
        }
    }

    private ValidacionFilasExcelResponseDto validarFilasCsv(
            MultipartFile archivo,
            PlantillaExcel plantilla,
            Long plantillaId,
            Integer indiceHoja,
            Integer filaCabecera,
            List<MapeoColumnaExcel> mapeos,
            Map<String, MapeoColumnaExcel> mapeosPorNombreNormalizado) {
        List<ErrorFilaExcelDto> errores = new ArrayList<>();

        int totalFilasLeidas = 0;
        int filasValidas = 0;
        int filasConError = 0;
        int filasConAdvertencia = 0;

        boolean cabeceraProcesada = false;
        Map<Integer, MapeoColumnaExcel> mapeosPorIndiceColumna = new HashMap<>();

        try (BufferedReader reader = WorkbookLoader.abrirReaderCsv(archivo)) {
            String linea;
            int numeroFila = 0;
            Character separador = null;

            while ((linea = reader.readLine()) != null) {
                if (separador == null && linea != null && !linea.isBlank()) {
                    separador = WorkbookLoader.detectarSeparador(linea);
                }

                if (numeroFila < filaCabecera) {
                    numeroFila++;
                    continue;
                }

                if (numeroFila == filaCabecera) {
                    if (linea == null || linea.isBlank()) {
                        throw new IllegalArgumentException("El CSV no contiene fila de cabeceras en la fila indicada.");
                    }

                    List<String> cabeceras = WorkbookLoader.parsearLineaCsv(linea, separador);
                    mapeosPorIndiceColumna = obtenerMapeosPorIndiceColumnaCsv(
                            cabeceras,
                            mapeosPorNombreNormalizado);

                    Set<String> columnasPresentesNormalizadas = obtenerColumnasPresentesNormalizadasCsv(cabeceras);

                    validarColumnasObligatorias(
                            mapeos,
                            columnasPresentesNormalizadas,
                            errores,
                            filaCabecera);

                    agregarAdvertenciasColumnasNoReconocidasCsv(
                            cabeceras,
                            mapeosPorNombreNormalizado,
                            errores,
                            filaCabecera);

                    cabeceraProcesada = true;
                    numeroFila++;
                    continue;
                }

                if (!cabeceraProcesada) {
                    throw new IllegalArgumentException("El CSV no contiene fila de cabeceras en la fila indicada.");
                }

                if (linea == null || linea.isBlank()) {
                    numeroFila++;
                    continue;
                }

                List<String> valores = WorkbookLoader.parsearLineaCsv(linea, separador);

                if (filaCsvVacia(valores)) {
                    numeroFila++;
                    continue;
                }

                if (!esFilaClinicaPrincipalCsv(valores, mapeosPorIndiceColumna)) {
                    numeroFila++;
                    continue;
                }

                totalFilasLeidas++;

                int erroresAntesDeFila = errores.size();

                validarFilaCsv(valores, numeroFila, mapeosPorIndiceColumna, errores);

                List<ErrorFilaExcelDto> nuevosDeFila = errores.subList(erroresAntesDeFila, errores.size());

                if (contieneSeveridad(nuevosDeFila, SeveridadError.ERROR)) {
                    filasConError++;
                } else if (contieneSeveridad(nuevosDeFila, SeveridadError.ADVERTENCIA)) {
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
                errores.add(ErrorFilaExcelDto.builder()
                        .numeroFila(filaCabecera + 1)
                        .nombreColumna(null)
                        .valorOriginal(null)
                        .tipoError("SIN_FILAS_CLINICAS")
                        .mensaje("El archivo no contiene ninguna fila clínica reconocible "
                                + "(falta HC o un dato clínico relevante en todas las filas).")
                        .severidad(SeveridadError.ERROR.name())
                        .build());
            }

        } catch (Exception e) {
            throw new RuntimeException("Error al validar filas del archivo CSV: " + e.getMessage(), e);
        }

        return construirRespuestaFilas(
                archivo.getOriginalFilename(),
                plantilla,
                indiceHoja,
                filaCabecera,
                totalFilasLeidas,
                filasValidas,
                filasConError,
                filasConAdvertencia,
                errores);
    }

    private ValidacionFilasExcelResponseDto construirRespuestaFilas(
            String nombreArchivo,
            PlantillaExcel plantilla,
            Integer indiceHoja,
            Integer filaCabecera,
            int totalFilasLeidas,
            int filasValidas,
            int filasConError,
            int filasConAdvertencia,
            List<ErrorFilaExcelDto> errores) {
        List<ErrorFilaExcelDto> erroresBloqueantes = filtrarPorSeveridad(errores, SeveridadError.ERROR);
        List<ErrorFilaExcelDto> advertencias = filtrarPorSeveridad(errores, SeveridadError.ADVERTENCIA);

        boolean importable = erroresBloqueantes.isEmpty();
        boolean valida = errores.isEmpty();

        return ValidacionFilasExcelResponseDto.builder()
                .nombreArchivo(nombreArchivo)
                .plantillaId(plantilla.getId())
                .codigoPlantilla(plantilla.getCodigo())
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
                .resumen(construirResumenFilas(
                        totalFilasLeidas,
                        filasConAdvertencia,
                        advertencias.size(),
                        importable))
                .build();
    }

    private boolean contieneSeveridad(List<ErrorFilaExcelDto> lista, SeveridadError severidad) {
        return lista.stream()
                .anyMatch(e -> severidad.name().equals(e.getSeveridad()));
    }

    private List<ErrorFilaExcelDto> filtrarPorSeveridad(List<ErrorFilaExcelDto> errores, SeveridadError severidad) {
        return errores.stream()
                .filter(e -> severidad.name().equals(e.getSeveridad()))
                .toList();
    }

    private String severidadSegunObligatoriedad(MapeoColumnaExcel mapeo) {
        return Boolean.TRUE.equals(mapeo.getObligatoria())
                ? SeveridadError.ERROR.name()
                : SeveridadError.ADVERTENCIA.name();
    }

    private String construirResumenCabecera(
            int totalColumnasNoReconocidas,
            int totalColumnasObligatoriasFaltantes,
            boolean importable) {
        if (!importable) {
            return "Faltan " + totalColumnasObligatoriasFaltantes + " columna(s) obligatoria(s). "
                    + "El archivo no es importable.";
        }

        if (totalColumnasNoReconocidas > 0) {
            return totalColumnasNoReconocidas + " columna(s) no reconocida(s) serán ignoradas. "
                    + "El archivo es importable.";
        }

        return "Todas las columnas fueron reconocidas. El archivo es importable.";
    }

    private String construirResumenFilas(
            int totalFilasLeidas,
            int filasConAdvertencia,
            int totalAdvertencias,
            boolean importable) {
        if (!importable) {
            return "El archivo tiene errores bloqueantes y no puede importarse.";
        }

        if (totalAdvertencias > 0) {
            return totalFilasLeidas + " fila(s) leída(s), " + filasConAdvertencia
                    + " con advertencia(s) (" + totalAdvertencias + " advertencia(s) en total). "
                    + "El archivo es importable.";
        }

        return totalFilasLeidas + " fila(s) leída(s) sin errores ni advertencias. El archivo es importable.";
    }

    private void agregarAdvertenciasColumnasNoReconocidas(
            Row headerRow,
            Map<String, MapeoColumnaExcel> mapeosPorNombreNormalizado,
            List<ErrorFilaExcelDto> errores,
            Integer filaCabecera) {
        for (Cell cell : headerRow) {
            String nombreColumna = obtenerValorCeldaComoTexto(cell);

            if (nombreColumna == null || nombreColumna.isBlank()) {
                continue;
            }

            String normalizada = TextNormalizer.normalize(nombreColumna);

            if (!mapeosPorNombreNormalizado.containsKey(normalizada)) {
                errores.add(ErrorFilaExcelDto.builder()
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
            Map<String, MapeoColumnaExcel> mapeosPorNombreNormalizado,
            List<ErrorFilaExcelDto> errores,
            Integer filaCabecera) {
        for (String nombreColumna : cabeceras) {
            if (nombreColumna == null || nombreColumna.isBlank()) {
                continue;
            }

            String normalizada = TextNormalizer.normalize(nombreColumna);

            if (!mapeosPorNombreNormalizado.containsKey(normalizada)) {
                errores.add(ErrorFilaExcelDto.builder()
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

    private Map<Integer, MapeoColumnaExcel> obtenerMapeosPorIndiceColumnaCsv(
            List<String> cabeceras,
            Map<String, MapeoColumnaExcel> mapeosPorNombreNormalizado) {
        Map<Integer, MapeoColumnaExcel> resultado = new HashMap<>();

        for (int i = 0; i < cabeceras.size(); i++) {
            String nombreColumna = cabeceras.get(i);

            if (nombreColumna == null || nombreColumna.isBlank()) {
                continue;
            }

            String normalizada = TextNormalizer.normalize(nombreColumna);
            MapeoColumnaExcel mapeo = mapeosPorNombreNormalizado.get(normalizada);

            if (mapeo != null) {
                resultado.put(i, mapeo);
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

    private boolean esFilaClinicaPrincipalCsv(
            List<String> valores,
            Map<Integer, MapeoColumnaExcel> mapeosPorIndiceColumna) {
        boolean tieneHc = false;
        boolean tieneDatoClinicoFuerte = false;

        Set<CampoDestino> camposFuertes = Set.of(
                CampoDestino.CIRUGIA_FECHA_CIRUGIA,
                CampoDestino.CIRUGIA_PROCEDIMIENTO,
                CampoDestino.CIRUGIA_CIE10,
                CampoDestino.CIRUGIA_DURACION_MINUTOS,
                CampoDestino.PROFILAXIS_DRAGO,
                CampoDestino.PROFILAXIS_ADECUACION,
                CampoDestino.ILQ_TIENE);

        for (Map.Entry<Integer, MapeoColumnaExcel> entry : mapeosPorIndiceColumna.entrySet()) {
            Integer indiceColumna = entry.getKey();
            MapeoColumnaExcel mapeo = entry.getValue();

            String valor = obtenerValorCsv(valores, indiceColumna);

            boolean tieneValor = valor != null && !valor.isBlank();

            if (!tieneValor) {
                continue;
            }

            if (mapeo.getCampoDestino() == CampoDestino.CIRUGIA_HC) {
                tieneHc = true;
            }

            if (camposFuertes.contains(mapeo.getCampoDestino())) {
                tieneDatoClinicoFuerte = true;
            }
        }

        return tieneHc && tieneDatoClinicoFuerte;
    }

    private void validarFilaCsv(
            List<String> valores,
            int indiceFila,
            Map<Integer, MapeoColumnaExcel> mapeosPorIndiceColumna,
            List<ErrorFilaExcelDto> errores) {
        for (Map.Entry<Integer, MapeoColumnaExcel> entry : mapeosPorIndiceColumna.entrySet()) {
            Integer indiceColumna = entry.getKey();
            MapeoColumnaExcel mapeo = entry.getValue();

            if (mapeo.getCampoDestino() == CampoDestino.IGNORAR) {
                continue;
            }

            String valor = obtenerValorCsv(valores, indiceColumna);

            boolean valorVacio = valor == null || valor.isBlank();

            if (Boolean.TRUE.equals(mapeo.getObligatoria()) && valorVacio) {
                errores.add(ErrorFilaExcelDto.builder()
                        .numeroFila(indiceFila + 1)
                        .nombreColumna(mapeo.getNombreColumnaExcel())
                        .valorOriginal(valor)
                        .tipoError("VALOR_OBLIGATORIO_VACIO")
                        .mensaje("El valor es obligatorio y está vacío.")
                        .severidad(SeveridadError.ERROR.name())
                        .build());

                continue;
            }

            if (valorVacio) {
                continue;
            }

            if (mapeo.getCampoDestino() == CampoDestino.CIRUGIA_FECHA_ALTA
                    && ClinicalValueNormalizer.esPacienteSigueIngresado(valor)) {
                errores.add(ErrorFilaExcelDto.builder()
                        .numeroFila(indiceFila + 1)
                        .nombreColumna(mapeo.getNombreColumnaExcel())
                        .valorOriginal(valor)
                        .tipoError("PACIENTE_SIGUE_INGRESADO")
                        .mensaje("El paciente sigue ingresado; no se registra fecha de alta.")
                        .severidad(SeveridadError.ADVERTENCIA.name())
                        .build());

                continue;
            }

            if (ClinicalValueNormalizer.esValorAusenteClinico(valor, mapeo.getCampoDestino())) {
                errores.add(ErrorFilaExcelDto.builder()
                        .numeroFila(indiceFila + 1)
                        .nombreColumna(mapeo.getNombreColumnaExcel())
                        .valorOriginal(valor)
                        .tipoError("VALOR_AUSENTE_CLINICO")
                        .mensaje("El valor indica ausencia de registro clínico; se importará como vacío.")
                        .severidad(SeveridadError.ADVERTENCIA.name())
                        .build());

                continue;
            }

            validarTipoDatoCsv(valor, indiceFila, mapeo, errores);
        }
    }

    private void validarTipoDatoCsv(
            String valor,
            int indiceFila,
            MapeoColumnaExcel mapeo,
            List<ErrorFilaExcelDto> errores) {
        TipoDatoExcel tipoDato = mapeo.getTipoDato();

        switch (tipoDato) {
            case ENTERO -> validarEntero(valor, indiceFila, mapeo, errores);
            case DECIMAL -> validarDecimal(valor, indiceFila, mapeo, errores);
            case FECHA -> validarFecha(null, valor, indiceFila, mapeo, errores);
            case BOOLEANO -> validarBooleano(valor, indiceFila, mapeo, errores);
            case TEXTO -> {
                // No necesita validación estricta.
            }
        }
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

        if (valor == null) {
            return "";
        }

        return valor.trim();
    }

    private Sheet obtenerHoja(Workbook workbook, Integer indiceHoja) {
        int totalHojas = workbook.getNumberOfSheets();

        if (totalHojas == 0) {
            throw new IllegalArgumentException("El Excel no contiene hojas.");
        }

        if (indiceHoja == null) {
            indiceHoja = 0;
        }

        if (indiceHoja < 0 || indiceHoja >= totalHojas) {
            throw new IllegalArgumentException(
                    "El índice de hoja no es válido. El archivo tiene "
                            + totalHojas
                            + " hoja(s). Índice recibido: "
                            + indiceHoja);
        }

        return workbook.getSheetAt(indiceHoja);
    }

    private Map<Integer, MapeoColumnaExcel> obtenerMapeosPorIndiceColumna(
            Row headerRow,
            Map<String, MapeoColumnaExcel> mapeosPorNombreNormalizado) {
        Map<Integer, MapeoColumnaExcel> resultado = new HashMap<>();

        for (Cell cell : headerRow) {
            String nombreColumna = obtenerValorCeldaComoTexto(cell);

            if (nombreColumna == null || nombreColumna.isBlank()) {
                continue;
            }

            String normalizada = TextNormalizer.normalize(nombreColumna);
            MapeoColumnaExcel mapeo = mapeosPorNombreNormalizado.get(normalizada);

            if (mapeo != null) {
                resultado.put(cell.getColumnIndex(), mapeo);
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

    private void validarColumnasObligatorias(
            List<MapeoColumnaExcel> mapeos,
            Set<String> columnasPresentesNormalizadas,
            List<ErrorFilaExcelDto> errores,
            Integer filaCabecera) {
        for (MapeoColumnaExcel mapeo : mapeos) {
            if (!Boolean.TRUE.equals(mapeo.getObligatoria())) {
                continue;
            }

            String columnaNormalizada = TextNormalizer.normalize(mapeo.getNombreColumnaExcel());

            if (!columnasPresentesNormalizadas.contains(columnaNormalizada)) {
                errores.add(ErrorFilaExcelDto.builder()
                        .numeroFila(filaCabecera + 1)
                        .nombreColumna(mapeo.getNombreColumnaExcel())
                        .valorOriginal(null)
                        .tipoError("COLUMNA_OBLIGATORIA_FALTANTE")
                        .mensaje("La columna obligatoria no existe en el Excel.")
                        .severidad(SeveridadError.ERROR.name())
                        .build());
            }
        }
    }

    private boolean esFilaClinicaPrincipal(
            Row row,
            Map<Integer, MapeoColumnaExcel> mapeosPorIndiceColumna) {
        boolean tieneHc = false;
        boolean tieneDatoClinicoFuerte = false;

        Set<CampoDestino> camposFuertes = Set.of(
                CampoDestino.CIRUGIA_FECHA_CIRUGIA,
                CampoDestino.CIRUGIA_PROCEDIMIENTO,
                CampoDestino.CIRUGIA_CIE10,
                CampoDestino.CIRUGIA_DURACION_MINUTOS,
                CampoDestino.PROFILAXIS_DRAGO,
                CampoDestino.PROFILAXIS_ADECUACION,
                CampoDestino.ILQ_TIENE);

        for (Map.Entry<Integer, MapeoColumnaExcel> entry : mapeosPorIndiceColumna.entrySet()) {
            Integer indiceColumna = entry.getKey();
            MapeoColumnaExcel mapeo = entry.getValue();

            Cell cell = row.getCell(indiceColumna, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String valor = obtenerValorCeldaComoTexto(cell);

            boolean tieneValor = valor != null && !valor.isBlank();

            if (!tieneValor) {
                continue;
            }

            if (mapeo.getCampoDestino() == CampoDestino.CIRUGIA_HC) {
                tieneHc = true;
            }

            if (camposFuertes.contains(mapeo.getCampoDestino())) {
                tieneDatoClinicoFuerte = true;
            }
        }

        return tieneHc && tieneDatoClinicoFuerte;
    }

    private void validarFila(
            Row row,
            Sheet sheet,
            int indiceFila,
            Map<Integer, MapeoColumnaExcel> mapeosPorIndiceColumna,
            List<ErrorFilaExcelDto> errores) {
        for (Map.Entry<Integer, MapeoColumnaExcel> entry : mapeosPorIndiceColumna.entrySet()) {
            Integer indiceColumna = entry.getKey();
            MapeoColumnaExcel mapeo = entry.getValue();

            if (mapeo.getCampoDestino() == CampoDestino.IGNORAR) {
                continue;
            }

            Cell cell = obtenerCeldaConSoporteCombinadas(sheet, indiceFila, indiceColumna);
            String valor = obtenerValorCeldaComoTexto(cell);

            boolean valorVacio = valor == null || valor.isBlank();

            if (Boolean.TRUE.equals(mapeo.getObligatoria()) && valorVacio) {
                errores.add(ErrorFilaExcelDto.builder()
                        .numeroFila(indiceFila + 1)
                        .nombreColumna(mapeo.getNombreColumnaExcel())
                        .valorOriginal(valor)
                        .tipoError("VALOR_OBLIGATORIO_VACIO")
                        .mensaje("El valor es obligatorio y está vacío.")
                        .severidad(SeveridadError.ERROR.name())
                        .build());

                continue;
            }

            if (valorVacio) {
                continue;
            }

            if (mapeo.getCampoDestino() == CampoDestino.CIRUGIA_FECHA_ALTA
                    && ClinicalValueNormalizer.esPacienteSigueIngresado(valor)) {
                errores.add(ErrorFilaExcelDto.builder()
                        .numeroFila(indiceFila + 1)
                        .nombreColumna(mapeo.getNombreColumnaExcel())
                        .valorOriginal(valor)
                        .tipoError("PACIENTE_SIGUE_INGRESADO")
                        .mensaje("El paciente sigue ingresado; no se registra fecha de alta.")
                        .severidad(SeveridadError.ADVERTENCIA.name())
                        .build());

                continue;
            }

            if (ClinicalValueNormalizer.esValorAusenteClinico(valor, mapeo.getCampoDestino())) {
                errores.add(ErrorFilaExcelDto.builder()
                        .numeroFila(indiceFila + 1)
                        .nombreColumna(mapeo.getNombreColumnaExcel())
                        .valorOriginal(valor)
                        .tipoError("VALOR_AUSENTE_CLINICO")
                        .mensaje("El valor indica ausencia de registro clínico; se importará como vacío.")
                        .severidad(SeveridadError.ADVERTENCIA.name())
                        .build());

                continue;
            }

            validarTipoDato(cell, valor, indiceFila, mapeo, errores);
        }
    }

    private void validarTipoDato(
            Cell cell,
            String valor,
            int indiceFila,
            MapeoColumnaExcel mapeo,
            List<ErrorFilaExcelDto> errores) {
        TipoDatoExcel tipoDato = mapeo.getTipoDato();

        switch (tipoDato) {
            case ENTERO -> validarEntero(valor, indiceFila, mapeo, errores);
            case DECIMAL -> validarDecimal(valor, indiceFila, mapeo, errores);
            case FECHA -> validarFecha(cell, valor, indiceFila, mapeo, errores);
            case BOOLEANO -> validarBooleano(valor, indiceFila, mapeo, errores);
            case TEXTO -> {
                // No necesita validación estricta.
            }
        }
    }

    private void validarEntero(
            String valor,
            int indiceFila,
            MapeoColumnaExcel mapeo,
            List<ErrorFilaExcelDto> errores) {
        try {
            String limpio = valor.trim().replace(",", ".");

            double numero = Double.parseDouble(limpio);

            if (numero % 1 != 0) {
                throw new NumberFormatException("Tiene decimales");
            }

        } catch (Exception e) {
            errores.add(ErrorFilaExcelDto.builder()
                    .numeroFila(indiceFila + 1)
                    .nombreColumna(mapeo.getNombreColumnaExcel())
                    .valorOriginal(valor)
                    .tipoError("FORMATO_NUMERO_INVALIDO")
                    .mensaje("El valor debe ser un número entero.")
                    .severidad(severidadSegunObligatoriedad(mapeo))
                    .build());
        }
    }

    private void validarDecimal(
            String valor,
            int indiceFila,
            MapeoColumnaExcel mapeo,
            List<ErrorFilaExcelDto> errores) {
        try {
            String limpio = valor.trim().replace(",", ".");
            Double.parseDouble(limpio);

        } catch (Exception e) {
            errores.add(ErrorFilaExcelDto.builder()
                    .numeroFila(indiceFila + 1)
                    .nombreColumna(mapeo.getNombreColumnaExcel())
                    .valorOriginal(valor)
                    .tipoError("FORMATO_NUMERO_INVALIDO")
                    .mensaje("El valor debe ser un número decimal válido.")
                    .severidad(severidadSegunObligatoriedad(mapeo))
                    .build());
        }
    }

    private void validarFecha(
            Cell cell,
            String valor,
            int indiceFila,
            MapeoColumnaExcel mapeo,
            List<ErrorFilaExcelDto> errores) {
        if (cell != null
                && cell.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {
            return;
        }

        String valorNormalizado = TextNormalizer.normalize(valor);

        if (mapeo.getCampoDestino() == CampoDestino.CIRUGIA_FECHA_ALTA
                && valorNormalizado.contains("INGRESAD")) {
            return;
        }

        List<DateTimeFormatter> formatos = List.of(
                DateTimeFormatter.ofPattern("d/M/yy"),
                DateTimeFormatter.ofPattern("dd/MM/yy"),
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("d-M-yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ISO_LOCAL_DATE);

        for (DateTimeFormatter formato : formatos) {
            try {
                LocalDate.parse(valor.trim(), formato);
                return;
            } catch (DateTimeParseException ignored) {
                // Probamos el siguiente formato.
            }
        }

        errores.add(ErrorFilaExcelDto.builder()
                .numeroFila(indiceFila + 1)
                .nombreColumna(mapeo.getNombreColumnaExcel())
                .valorOriginal(valor)
                .tipoError("FORMATO_FECHA_INVALIDO")
                .mensaje("La fecha no tiene un formato válido.")
                .severidad(severidadSegunObligatoriedad(mapeo))
                .build());
    }

    private void validarBooleano(
            String valor,
            int indiceFila,
            MapeoColumnaExcel mapeo,
            List<ErrorFilaExcelDto> errores) {
        String normalizado = TextNormalizer.normalize(valor);

        Set<String> valoresValidos = Set.of(
                "SI",
                "S",
                "NO",
                "N",
                "TRUE",
                "FALSE",
                "VERDADERO",
                "FALSO",
                "1",
                "0",
                "X",
                "ADECUADA",
                "INADECUADA",
                "NO ADECUADA",
                "POSITIVO",
                "NEGATIVO");

        if (!valoresValidos.contains(normalizado)) {
            errores.add(ErrorFilaExcelDto.builder()
                    .numeroFila(indiceFila + 1)
                    .nombreColumna(mapeo.getNombreColumnaExcel())
                    .valorOriginal(valor)
                    .tipoError("FORMATO_BOOLEANO_INVALIDO")
                    .mensaje("El valor debe ser interpretable como Sí/No.")
                    .severidad(severidadSegunObligatoriedad(mapeo))
                    .build());
        }
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

        Cell cell = null;

        if (row != null) {
            cell = row.getCell(columna, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        }

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

                return filaPrincipal.getCell(
                        rango.getFirstColumn(),
                        Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
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
}
