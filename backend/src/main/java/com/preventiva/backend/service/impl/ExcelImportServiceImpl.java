package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.ErrorFilaExcelDto;
import com.preventiva.backend.dto.ErrorImportacionExcelResponseDto;
import com.preventiva.backend.dto.ImportacionExcelResponseDto;
import com.preventiva.backend.dto.ValidacionFilasExcelResponseDto;
import com.preventiva.backend.entity.*;
import com.preventiva.backend.enums.CampoDestino;
import com.preventiva.backend.enums.EstadoImportacion;
import com.preventiva.backend.enums.SeveridadError;
import com.preventiva.backend.enums.TipoErrorImportacion;
import com.preventiva.backend.repository.*;
import com.preventiva.backend.service.interfaces.ExcelImportService;
import com.preventiva.backend.service.interfaces.ExcelValidationService;
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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExcelImportServiceImpl implements ExcelImportService {

    private final ExcelValidationService excelValidationService;

    private final PlantillaExcelRepository plantillaExcelRepository;
    private final MapeoColumnaExcelRepository mapeoColumnaExcelRepository;

    private final ServicioRepository servicioRepository;
    private final CirugiaRepository cirugiaRepository;
    private final ProfilaxisRepository profilaxisRepository;
    private final InfeccionQuirurgicaRepository infeccionQuirurgicaRepository;
    private final MicrobiologiaRepository microbiologiaRepository;
    private final SeguimientoPostoperatorioRepository seguimientoPostoperatorioRepository;
    private final MedidasPreventivasRepository medidasPreventivasRepository;

    private final ImportacionExcelRepository importacionExcelRepository;
    private final ErrorImportacionExcelRepository errorImportacionExcelRepository;

    private final DataFormatter dataFormatter = new DataFormatter();

    @Override
    @Transactional
    public ImportacionExcelResponseDto importarExcel(
            MultipartFile archivo,
            Long plantillaId,
            Integer indiceHoja,
            Integer filaCabecera) {
        if (indiceHoja == null) {
            indiceHoja = 0;
        }

        if (filaCabecera == null) {
            filaCabecera = 0;
        }

        PlantillaExcel plantilla = plantillaExcelRepository.findById(plantillaId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la plantilla con id: " + plantillaId));

        ValidacionFilasExcelResponseDto validacion = excelValidationService.validarFilasExcel(
                archivo,
                plantillaId,
                indiceHoja,
                filaCabecera);

        boolean importable = Boolean.TRUE.equals(validacion.getImportable());

        ImportacionExcel importacion = ImportacionExcel.builder()
                .nombreArchivo(generarNombreInterno(archivo))
                .nombreOriginal(archivo.getOriginalFilename())
                .fechaImportacion(LocalDateTime.now())
                .filasLeidas(validacion.getTotalFilasLeidas())
                .filasImportadas(0)
                .filasConError(validacion.getFilasConError())
                .estado(importable
                        ? EstadoImportacion.PENDIENTE
                        : EstadoImportacion.RECHAZADA)
                .plantilla(plantilla)
                .usuario(null)
                .build();

        importacion = importacionExcelRepository.save(importacion);

        // Se guardan tanto los errores bloqueantes como las advertencias, se importe
        // o no, para poder consultarlos después vía GET /api/importaciones/{id}/errores.
        guardarErroresImportacion(importacion, validacion.getErrores());

        if (!importable) {
            return ImportacionExcelResponseDto.builder()
                    .importacionId(importacion.getId())
                    .nombreArchivo(archivo.getOriginalFilename())
                    .plantillaId(plantilla.getId())
                    .codigoPlantilla(plantilla.getCodigo())
                    .filasLeidas(validacion.getTotalFilasLeidas())
                    .filasImportadas(0)
                    .filasConError(validacion.getFilasConError())
                    .totalAdvertencias(validacion.getTotalAdvertencias())
                    .estado(importacion.getEstado().name())
                    .mensaje("El archivo tiene errores bloqueantes. No se ha importado ningún registro.")
                    .build();
        }

        int filasImportadas = importarFilasValidas(
                archivo,
                plantilla,
                plantillaId,
                indiceHoja,
                filaCabecera);

        boolean tieneAdvertencias = validacion.getTotalAdvertencias() != null
                && validacion.getTotalAdvertencias() > 0;

        importacion.setFilasImportadas(filasImportadas);
        importacion.setFilasConError(0);
        importacion.setEstado(tieneAdvertencias
                ? EstadoImportacion.IMPORTADA_CON_ERRORES
                : EstadoImportacion.IMPORTADA);

        importacionExcelRepository.save(importacion);

        return ImportacionExcelResponseDto.builder()
                .importacionId(importacion.getId())
                .nombreArchivo(archivo.getOriginalFilename())
                .plantillaId(plantilla.getId())
                .codigoPlantilla(plantilla.getCodigo())
                .filasLeidas(validacion.getTotalFilasLeidas())
                .filasImportadas(filasImportadas)
                .filasConError(0)
                .totalAdvertencias(validacion.getTotalAdvertencias())
                .estado(importacion.getEstado().name())
                .mensaje(tieneAdvertencias
                        ? "Importación realizada con advertencias. Consulte GET /api/importaciones/"
                                + importacion.getId() + "/errores para el detalle."
                        : "Importación realizada correctamente.")
                .build();
    }

    @Override
    public List<ErrorImportacionExcelResponseDto> listarErrores(Long importacionId) {
        importacionExcelRepository.findById(importacionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la importación con id: " + importacionId));

        return errorImportacionExcelRepository.findByImportacionId(importacionId)
                .stream()
                .map(this::mapErrorImportacionToDto)
                .toList();
    }

    private ErrorImportacionExcelResponseDto mapErrorImportacionToDto(ErrorImportacionExcel error) {
        return ErrorImportacionExcelResponseDto.builder()
                .id(error.getId())
                .numeroFila(error.getNumeroFila())
                .nombreColumna(error.getNombreColumna())
                .valorOriginal(error.getValorOriginal())
                .tipoError(error.getTipoError().name())
                .severidad(error.getSeveridad() != null
                        ? error.getSeveridad().name()
                        : SeveridadError.ERROR.name())
                .mensaje(error.getMensaje())
                .build();
    }

    private int importarFilasValidas(
            MultipartFile archivo,
            PlantillaExcel plantilla,
            Long plantillaId,
            Integer indiceHoja,
            Integer filaCabecera) {
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
            return importarFilasValidasCsv(
                    archivo,
                    plantilla,
                    filaCabecera,
                    mapeosPorNombreNormalizado);
        }

        int filasImportadas = 0;

        try (Workbook workbook = WorkbookLoader.abrirWorkbook(archivo)) {

            Sheet sheet = obtenerHoja(workbook, indiceHoja);
            Row headerRow = sheet.getRow(filaCabecera);

            if (headerRow == null) {
                throw new IllegalArgumentException("El Excel no contiene fila de cabeceras en la fila indicada.");
            }

            Map<Integer, MapeoColumnaExcel> mapeosPorIndiceColumna = obtenerMapeosPorIndiceColumna(
                    headerRow,
                    mapeosPorNombreNormalizado);

            int ultimaFila = sheet.getLastRowNum();

            for (int i = filaCabecera + 1; i <= ultimaFila; i++) {
                Row row = sheet.getRow(i);

                if (filaVacia(row)) {
                    continue;
                }

                if (!esFilaClinicaPrincipal(row, mapeosPorIndiceColumna)) {
                    continue;
                }

                Map<CampoDestino, Cell> celdasPorCampo = obtenerCeldasPorCampo(
                        row,
                        sheet,
                        i,
                        mapeosPorIndiceColumna);

                boolean registroImportado = guardarRegistroClinico(celdasPorCampo, plantilla);

                if (registroImportado) {
                    filasImportadas++;
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Error al importar el archivo Excel: " + e.getMessage(), e);
        }

        return filasImportadas;
    }

    private int importarFilasValidasCsv(
            MultipartFile archivo,
            PlantillaExcel plantilla,
            Integer filaCabecera,
            Map<String, MapeoColumnaExcel> mapeosPorNombreNormalizado) {
        int filasImportadas = 0;
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

                Map<CampoDestino, String> valoresPorCampo = obtenerValoresPorCampoCsv(
                        valores,
                        mapeosPorIndiceColumna);

                boolean registroImportado = guardarRegistroClinicoCsv(valoresPorCampo, plantilla);

                if (registroImportado) {
                    filasImportadas++;
                }

                numeroFila++;
            }

            if (!cabeceraProcesada) {
                throw new IllegalArgumentException("El CSV no contiene fila de cabeceras en la fila indicada.");
            }

        } catch (Exception e) {
            throw new RuntimeException("Error al importar el archivo CSV: " + e.getMessage(), e);
        }

        return filasImportadas;
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

    private Map<CampoDestino, String> obtenerValoresPorCampoCsv(
            List<String> valores,
            Map<Integer, MapeoColumnaExcel> mapeosPorIndiceColumna) {
        Map<CampoDestino, String> resultado = new HashMap<>();

        for (Map.Entry<Integer, MapeoColumnaExcel> entry : mapeosPorIndiceColumna.entrySet()) {
            Integer indiceColumna = entry.getKey();
            MapeoColumnaExcel mapeo = entry.getValue();

            if (mapeo.getCampoDestino() == CampoDestino.IGNORAR) {
                continue;
            }

            resultado.put(
                    mapeo.getCampoDestino(),
                    obtenerValorCsv(valores, indiceColumna));
        }

        return resultado;
    }

    private boolean guardarRegistroClinicoCsv(
            Map<CampoDestino, String> valores,
            PlantillaExcel plantilla) {
        Servicio servicio = obtenerServicioCsv(valores, plantilla);

        String hc = leerTextoCsv(valores, CampoDestino.CIRUGIA_HC);
        LocalDate fechaCirugia = leerFechaCsv(valores, CampoDestino.CIRUGIA_FECHA_CIRUGIA);
        String cie10 = leerTextoCsv(valores, CampoDestino.CIRUGIA_CIE10);

        boolean yaExiste = cirugiaRepository.existsByHcAndFechaCirugiaAndCie10(
                hc,
                fechaCirugia,
                cie10);

        if (yaExiste) {
            return false;
        }

        Cirugia cirugia = Cirugia.builder()
                .hc(hc)
                .sexo(leerTextoCsv(valores, CampoDestino.CIRUGIA_SEXO))
                .edad(leerEnteroCsv(valores, CampoDestino.CIRUGIA_EDAD))
                .fechaIngreso(leerFechaCsv(valores, CampoDestino.CIRUGIA_FECHA_INGRESO))
                .fechaAlta(leerFechaCsv(valores, CampoDestino.CIRUGIA_FECHA_ALTA))
                .fechaCirugia(fechaCirugia)
                .procedimiento(leerTextoCsv(valores, CampoDestino.CIRUGIA_PROCEDIMIENTO))
                .cie10(cie10)
                .duracionMinutos(leerEnteroCsv(valores, CampoDestino.CIRUGIA_DURACION_MINUTOS))
                .cirugiaUrgente(leerBooleanoCsv(valores, CampoDestino.CIRUGIA_URGENTE))
                .asa(leerTextoCsv(valores, CampoDestino.CIRUGIA_ASA))
                .gradoContaminacionInicial(leerTextoCsv(valores, CampoDestino.CIRUGIA_GRADO_CONTAMINACION_INICIAL))
                .gradoContaminacionFinal(leerTextoCsv(valores, CampoDestino.CIRUGIA_GRADO_CONTAMINACION_FINAL))
                .abordajeQuirurgico(leerTextoCsv(valores, CampoDestino.CIRUGIA_ABORDAJE_QUIRURGICO))
                .multirresistentes(leerBooleanoCsv(valores, CampoDestino.CIRUGIA_MULTIRRESISTENTES))
                .servicio(servicio)
                .build();

        cirugia = cirugiaRepository.save(cirugia);

        Profilaxis profilaxis = Profilaxis.builder()
                .cirugia(cirugia)
                .indicacionProfilaxis(leerTextoCsv(valores, CampoDestino.PROFILAXIS_INDICACION))
                .administracionProfilaxis(leerTextoCsv(valores, CampoDestino.PROFILAXIS_ADMINISTRACION))
                .profilaxisDrago(leerTextoCsv(valores, CampoDestino.PROFILAXIS_DRAGO))
                .adecuacionProfilaxis(leerTextoCsv(valores, CampoDestino.PROFILAXIS_ADECUACION))
                .motivoInadecuacion(leerTextoCsv(valores, CampoDestino.PROFILAXIS_MOTIVO_INADECUACION))
                .validacionDosisAdicionales(leerTextoCsv(valores, CampoDestino.PROFILAXIS_VALIDACION_DOSIS_ADICIONALES))
                .build();

        profilaxisRepository.save(profilaxis);

        InfeccionQuirurgica infeccion = InfeccionQuirurgica.builder()
                .cirugia(cirugia)
                .tieneIlq(leerBooleanoCsv(valores, CampoDestino.ILQ_TIENE))
                .fechaIlq(leerFechaCsv(valores, CampoDestino.ILQ_FECHA))
                .fechaFinVigilancia(leerFechaCsv(valores, CampoDestino.ILQ_FECHA_FIN_VIGILANCIA))
                .localizacionInfeccion(leerTextoCsv(valores, CampoDestino.ILQ_LOCALIZACION))
                .reingresoPorIlq(leerBooleanoCsv(valores, CampoDestino.ILQ_REINGRESO))
                .fechaReingreso(leerFechaCsv(valores, CampoDestino.ILQ_FECHA_REINGRESO))
                .build();

        infeccion = infeccionQuirurgicaRepository.save(infeccion);

        Microbiologia microbiologia = Microbiologia.builder()
                .infeccion(infeccion)
                .cultivoIlq(leerBooleanoCsv(valores, CampoDestino.MICRO_CULTIVO_ILQ))
                .tipoMuestra(leerTextoCsv(valores, CampoDestino.MICRO_TIPO_MUESTRA))
                .otraMuestra(leerTextoCsv(valores, CampoDestino.MICRO_OTRA_MUESTRA))
                .resultadoCultivo(leerTextoCsv(valores, CampoDestino.MICRO_RESULTADO_CULTIVO))
                .microorganismo(leerTextoCsv(valores, CampoDestino.MICRO_MICROORGANISMO))
                .resistencia(leerTextoCsv(valores, CampoDestino.MICRO_RESISTENCIA))
                .otraResistencia(leerTextoCsv(valores, CampoDestino.MICRO_OTRA_RESISTENCIA))
                .build();

        microbiologiaRepository.save(microbiologia);

        SeguimientoPostoperatorio seguimiento = SeguimientoPostoperatorio.builder()
                .cirugia(cirugia)
                .edadMesesMenor2(leerEnteroCsv(valores, CampoDestino.SEGUIMIENTO_EDAD_MESES_MENOR_2))
                .exitusPostCirugia(leerBooleanoCsv(valores, CampoDestino.SEGUIMIENTO_EXITUS_POST_CIRUGIA))
                .motivoAlta(leerTextoCsv(valores, CampoDestino.SEGUIMIENTO_MOTIVO_ALTA))
                .comentarios(leerTextoCsv(valores, CampoDestino.SEGUIMIENTO_COMENTARIOS))
                .build();

        seguimientoPostoperatorioRepository.save(seguimiento);

        MedidasPreventivas medidas = MedidasPreventivas.builder()
                .cirugia(cirugia)
                .monitorizacionTemperatura(leerTextoCsv(valores, CampoDestino.PREVENTIVA_MONITORIZACION_TEMPERATURA))
                .glucemiaIntraoperatoria(leerTextoCsv(valores, CampoDestino.PREVENTIVA_GLUCEMIA_INTRAOPERATORIA))
                .eliminacionVello(leerTextoCsv(valores, CampoDestino.PREVENTIVA_ELIMINACION_VELLO))
                .momentoEliminacionVello(leerTextoCsv(valores, CampoDestino.PREVENTIVA_MOMENTO_ELIMINACION_VELLO))
                .tecnicaEliminacionVello(leerTextoCsv(valores, CampoDestino.PREVENTIVA_TECNICA_ELIMINACION_VELLO))
                .otraTecnicaEliminacionVello(
                        leerTextoCsv(valores, CampoDestino.PREVENTIVA_OTRA_TECNICA_ELIMINACION_VELLO))
                .antisepsiaPiel(leerTextoCsv(valores, CampoDestino.PREVENTIVA_ANTISEPSIA_PIEL))
                .build();

        medidasPreventivasRepository.save(medidas);

        return true;
    }

    private Servicio obtenerServicioCsv(
            Map<CampoDestino, String> valores,
            PlantillaExcel plantilla) {
        String valorServicio = leerTextoCsv(valores, CampoDestino.CIRUGIA_SERVICIO);

        if (valorServicio != null && !valorServicio.isBlank()) {
            String normalizado = TextNormalizer.normalize(valorServicio);

            Optional<Servicio> servicio = servicioRepository.findAll()
                    .stream()
                    .filter(s -> TextNormalizer.normalize(s.getCodigo()).equals(normalizado)
                            || TextNormalizer.normalize(s.getNombre()).equals(normalizado))
                    .findFirst();

            if (servicio.isPresent()) {
                return servicio.get();
            }
        }

        if (plantilla.getServicio() != null) {
            return plantilla.getServicio();
        }

        return null;
    }

    private String leerTextoCsv(Map<CampoDestino, String> valores, CampoDestino campoDestino) {
        String valor = valores.get(campoDestino);

        if (valor == null || valor.isBlank()) {
            return null;
        }

        return valor.trim();
    }

    private Integer leerEnteroCsv(Map<CampoDestino, String> valores, CampoDestino campoDestino) {
        String valor = leerTextoCsv(valores, campoDestino);

        if (valor == null) {
            return null;
        }

        try {
            String limpio = valor.replace(",", ".");
            double numero = Double.parseDouble(limpio);
            return (int) numero;
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean leerBooleanoCsv(Map<CampoDestino, String> valores, CampoDestino campoDestino) {
        String valor = leerTextoCsv(valores, campoDestino);

        if (valor == null) {
            return null;
        }

        String normalizado = TextNormalizer.normalize(valor);

        if (Set.of("SI", "S", "TRUE", "VERDADERO", "1", "X", "POSITIVO", "ADECUADA").contains(normalizado)) {
            return true;
        }

        if (Set.of("NO", "N", "FALSE", "FALSO", "0", "NEGATIVO", "INADECUADA", "NO ADECUADA").contains(normalizado)) {
            return false;
        }

        return null;
    }

    private LocalDate leerFechaCsv(Map<CampoDestino, String> valores, CampoDestino campoDestino) {
        String valor = leerTextoCsv(valores, campoDestino);

        if (valor == null) {
            return null;
        }

        if (campoDestino == CampoDestino.CIRUGIA_FECHA_ALTA
                && TextNormalizer.normalize(valor).startsWith("SIGUE INGRESADO")) {
            return null;
        }

        List<DateTimeFormatter> formatos = List.of(
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("d-M-yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("d/M/yy"),
                DateTimeFormatter.ofPattern("dd/MM/yy"),
                DateTimeFormatter.ISO_LOCAL_DATE);

        for (DateTimeFormatter formato : formatos) {
            try {
                return LocalDate.parse(valor.trim(), formato);
            } catch (Exception ignored) {
                // Probamos el siguiente formato.
            }
        }

        return null;
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

    private boolean guardarRegistroClinico(
            Map<CampoDestino, Cell> celdas,
            PlantillaExcel plantilla) {
        Servicio servicio = obtenerServicio(celdas, plantilla);

        String hc = leerTexto(celdas, CampoDestino.CIRUGIA_HC);
        LocalDate fechaCirugia = leerFecha(celdas, CampoDestino.CIRUGIA_FECHA_CIRUGIA);
        String cie10 = leerTexto(celdas, CampoDestino.CIRUGIA_CIE10);

        boolean yaExiste = cirugiaRepository.existsByHcAndFechaCirugiaAndCie10(
                hc,
                fechaCirugia,
                cie10);

        if (yaExiste) {
            return false;
        }

        Cirugia cirugia = Cirugia.builder()
                .hc(hc)
                .sexo(leerTexto(celdas, CampoDestino.CIRUGIA_SEXO))
                .edad(leerEntero(celdas, CampoDestino.CIRUGIA_EDAD))
                .fechaIngreso(leerFecha(celdas, CampoDestino.CIRUGIA_FECHA_INGRESO))
                .fechaAlta(leerFecha(celdas, CampoDestino.CIRUGIA_FECHA_ALTA))
                .fechaCirugia(fechaCirugia)
                .procedimiento(leerTexto(celdas, CampoDestino.CIRUGIA_PROCEDIMIENTO))
                .cie10(cie10)
                .duracionMinutos(leerEntero(celdas, CampoDestino.CIRUGIA_DURACION_MINUTOS))
                .cirugiaUrgente(leerBooleano(celdas, CampoDestino.CIRUGIA_URGENTE))
                .asa(leerTexto(celdas, CampoDestino.CIRUGIA_ASA))
                .gradoContaminacionInicial(leerTexto(celdas, CampoDestino.CIRUGIA_GRADO_CONTAMINACION_INICIAL))
                .gradoContaminacionFinal(leerTexto(celdas, CampoDestino.CIRUGIA_GRADO_CONTAMINACION_FINAL))
                .abordajeQuirurgico(leerTexto(celdas, CampoDestino.CIRUGIA_ABORDAJE_QUIRURGICO))
                .multirresistentes(leerBooleano(celdas, CampoDestino.CIRUGIA_MULTIRRESISTENTES))
                .servicio(servicio)
                .build();

        cirugia = cirugiaRepository.save(cirugia);

        Profilaxis profilaxis = Profilaxis.builder()
                .cirugia(cirugia)
                .indicacionProfilaxis(leerTexto(celdas, CampoDestino.PROFILAXIS_INDICACION))
                .administracionProfilaxis(leerTexto(celdas, CampoDestino.PROFILAXIS_ADMINISTRACION))
                .profilaxisDrago(leerTexto(celdas, CampoDestino.PROFILAXIS_DRAGO))
                .adecuacionProfilaxis(leerTexto(celdas, CampoDestino.PROFILAXIS_ADECUACION))
                .motivoInadecuacion(leerTexto(celdas, CampoDestino.PROFILAXIS_MOTIVO_INADECUACION))
                .validacionDosisAdicionales(leerTexto(celdas, CampoDestino.PROFILAXIS_VALIDACION_DOSIS_ADICIONALES))
                .build();

        profilaxisRepository.save(profilaxis);

        InfeccionQuirurgica infeccion = InfeccionQuirurgica.builder()
                .cirugia(cirugia)
                .tieneIlq(leerBooleano(celdas, CampoDestino.ILQ_TIENE))
                .fechaIlq(leerFecha(celdas, CampoDestino.ILQ_FECHA))
                .fechaFinVigilancia(leerFecha(celdas, CampoDestino.ILQ_FECHA_FIN_VIGILANCIA))
                .localizacionInfeccion(leerTexto(celdas, CampoDestino.ILQ_LOCALIZACION))
                .reingresoPorIlq(leerBooleano(celdas, CampoDestino.ILQ_REINGRESO))
                .fechaReingreso(leerFecha(celdas, CampoDestino.ILQ_FECHA_REINGRESO))
                .build();

        infeccion = infeccionQuirurgicaRepository.save(infeccion);

        Microbiologia microbiologia = Microbiologia.builder()
                .infeccion(infeccion)
                .cultivoIlq(leerBooleano(celdas, CampoDestino.MICRO_CULTIVO_ILQ))
                .tipoMuestra(leerTexto(celdas, CampoDestino.MICRO_TIPO_MUESTRA))
                .otraMuestra(leerTexto(celdas, CampoDestino.MICRO_OTRA_MUESTRA))
                .resultadoCultivo(leerTexto(celdas, CampoDestino.MICRO_RESULTADO_CULTIVO))
                .microorganismo(leerTexto(celdas, CampoDestino.MICRO_MICROORGANISMO))
                .resistencia(leerTexto(celdas, CampoDestino.MICRO_RESISTENCIA))
                .otraResistencia(leerTexto(celdas, CampoDestino.MICRO_OTRA_RESISTENCIA))
                .build();

        microbiologiaRepository.save(microbiologia);

        SeguimientoPostoperatorio seguimiento = SeguimientoPostoperatorio.builder()
                .cirugia(cirugia)
                .edadMesesMenor2(leerEntero(celdas, CampoDestino.SEGUIMIENTO_EDAD_MESES_MENOR_2))
                .exitusPostCirugia(leerBooleano(celdas, CampoDestino.SEGUIMIENTO_EXITUS_POST_CIRUGIA))
                .motivoAlta(leerTexto(celdas, CampoDestino.SEGUIMIENTO_MOTIVO_ALTA))
                .comentarios(leerTexto(celdas, CampoDestino.SEGUIMIENTO_COMENTARIOS))
                .build();

        seguimientoPostoperatorioRepository.save(seguimiento);

        MedidasPreventivas medidas = MedidasPreventivas.builder()
                .cirugia(cirugia)
                .monitorizacionTemperatura(leerTexto(celdas, CampoDestino.PREVENTIVA_MONITORIZACION_TEMPERATURA))
                .glucemiaIntraoperatoria(leerTexto(celdas, CampoDestino.PREVENTIVA_GLUCEMIA_INTRAOPERATORIA))
                .eliminacionVello(leerTexto(celdas, CampoDestino.PREVENTIVA_ELIMINACION_VELLO))
                .momentoEliminacionVello(leerTexto(celdas, CampoDestino.PREVENTIVA_MOMENTO_ELIMINACION_VELLO))
                .tecnicaEliminacionVello(leerTexto(celdas, CampoDestino.PREVENTIVA_TECNICA_ELIMINACION_VELLO))
                .otraTecnicaEliminacionVello(leerTexto(celdas, CampoDestino.PREVENTIVA_OTRA_TECNICA_ELIMINACION_VELLO))
                .antisepsiaPiel(leerTexto(celdas, CampoDestino.PREVENTIVA_ANTISEPSIA_PIEL))
                .build();

        medidasPreventivasRepository.save(medidas);

        return true;
    }

    private Servicio obtenerServicio(
            Map<CampoDestino, Cell> celdas,
            PlantillaExcel plantilla) {
        String valorServicio = leerTexto(celdas, CampoDestino.CIRUGIA_SERVICIO);

        if (valorServicio != null && !valorServicio.isBlank()) {
            String normalizado = TextNormalizer.normalize(valorServicio);

            Optional<Servicio> servicio = servicioRepository.findAll()
                    .stream()
                    .filter(s -> TextNormalizer.normalize(s.getCodigo()).equals(normalizado)
                            || TextNormalizer.normalize(s.getNombre()).equals(normalizado))
                    .findFirst();

            if (servicio.isPresent()) {
                return servicio.get();
            }
        }

        if (plantilla.getServicio() != null) {
            return plantilla.getServicio();
        }

        return null;
    }

    private Map<CampoDestino, Cell> obtenerCeldasPorCampo(
            Row row,
            Sheet sheet,
            int indiceFila,
            Map<Integer, MapeoColumnaExcel> mapeosPorIndiceColumna) {
        Map<CampoDestino, Cell> resultado = new HashMap<>();

        for (Map.Entry<Integer, MapeoColumnaExcel> entry : mapeosPorIndiceColumna.entrySet()) {
            Integer indiceColumna = entry.getKey();
            MapeoColumnaExcel mapeo = entry.getValue();

            if (mapeo.getCampoDestino() == CampoDestino.IGNORAR) {
                continue;
            }

            Cell cell = obtenerCeldaConSoporteCombinadas(sheet, indiceFila, indiceColumna);
            resultado.put(mapeo.getCampoDestino(), cell);
        }

        return resultado;
    }

    private void guardarErroresImportacion(
            ImportacionExcel importacion,
            List<ErrorFilaExcelDto> errores) {
        for (ErrorFilaExcelDto errorDto : errores) {
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

            ErrorImportacionExcel error = ErrorImportacionExcel.builder()
                    .importacion(importacion)
                    .numeroFila(errorDto.getNumeroFila())
                    .nombreColumna(errorDto.getNombreColumna())
                    .valorOriginal(errorDto.getValorOriginal())
                    .tipoError(tipoError)
                    .severidad(severidad)
                    .mensaje(errorDto.getMensaje())
                    .build();

            errorImportacionExcelRepository.save(error);
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

    private String leerTexto(Map<CampoDestino, Cell> celdas, CampoDestino campoDestino) {
        Cell cell = celdas.get(campoDestino);
        String valor = obtenerValorCeldaComoTexto(cell);

        if (valor == null || valor.isBlank()) {
            return null;
        }

        return valor.trim();
    }

    private Integer leerEntero(Map<CampoDestino, Cell> celdas, CampoDestino campoDestino) {
        String valor = leerTexto(celdas, campoDestino);

        if (valor == null) {
            return null;
        }

        try {
            String limpio = valor.replace(",", ".");
            double numero = Double.parseDouble(limpio);
            return (int) numero;
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean leerBooleano(Map<CampoDestino, Cell> celdas, CampoDestino campoDestino) {
        String valor = leerTexto(celdas, campoDestino);

        if (valor == null) {
            return null;
        }

        String normalizado = TextNormalizer.normalize(valor);

        if (Set.of("SI", "S", "TRUE", "VERDADERO", "1", "X", "POSITIVO", "ADECUADA").contains(normalizado)) {
            return true;
        }

        if (Set.of("NO", "N", "FALSE", "FALSO", "0", "NEGATIVO", "INADECUADA", "NO ADECUADA").contains(normalizado)) {
            return false;
        }

        return null;
    }

    private LocalDate leerFecha(Map<CampoDestino, Cell> celdas, CampoDestino campoDestino) {
        Cell cell = celdas.get(campoDestino);

        if (cell == null) {
            return null;
        }

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }

        String valor = obtenerValorCeldaComoTexto(cell);

        if (valor == null || valor.isBlank()) {
            return null;
        }

        if (campoDestino == CampoDestino.CIRUGIA_FECHA_ALTA
                && TextNormalizer.normalize(valor).startsWith("SIGUE INGRESADO")) {
            return null;
        }

        List<DateTimeFormatter> formatos = List.of(
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("d-M-yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("d/M/yy"),
                DateTimeFormatter.ofPattern("dd/MM/yy"),
                DateTimeFormatter.ISO_LOCAL_DATE);

        for (DateTimeFormatter formato : formatos) {
            try {
                return LocalDate.parse(valor.trim(), formato);
            } catch (Exception ignored) {
                // Probamos el siguiente formato.
            }
        }

        return null;
    }

    private String obtenerValorCeldaComoTexto(Cell cell) {
        if (cell == null) {
            return "";
        }

        return dataFormatter.formatCellValue(cell);
    }

    private String generarNombreInterno(MultipartFile archivo) {
        String nombreOriginal = archivo.getOriginalFilename();

        if (nombreOriginal == null || nombreOriginal.isBlank()) {
            nombreOriginal = "archivo.xlsx";
        }

        return UUID.randomUUID() + "_" + nombreOriginal;
    }
}
