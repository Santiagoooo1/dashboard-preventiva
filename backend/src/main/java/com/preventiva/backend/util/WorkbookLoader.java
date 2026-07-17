package com.preventiva.backend.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class WorkbookLoader {

    private WorkbookLoader() {
    }

    public static Workbook abrirWorkbook(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("No se ha recibido ningún archivo.");
        }

        if (esCsv(archivo)) {
            return convertirCsvAWorkbook(archivo);
        }

        try {
            return WorkbookFactory.create(archivo.getInputStream());
        } catch (Exception e) {
            throw new RuntimeException(
                    "No se pudo abrir el archivo. Debe ser un Excel válido (.xlsx, .xls) o un CSV válido (.csv).",
                    e);
        }
    }

    public static boolean esCsv(MultipartFile archivo) {
        String nombre = archivo.getOriginalFilename();
        String contentType = archivo.getContentType();

        boolean nombreCsv = nombre != null && nombre.toLowerCase().endsWith(".csv");
        boolean contentTypeCsv = contentType != null && (contentType.equalsIgnoreCase("text/csv")
                || contentType.equalsIgnoreCase("application/csv")
                || contentType.equalsIgnoreCase("application/vnd.ms-excel")
                || contentType.equalsIgnoreCase("text/plain"));

        return nombreCsv || contentTypeCsv;
    }

    public static BufferedReader abrirReaderCsv(MultipartFile archivo) {
        try {
            String contenido = leerContenidoCsv(archivo);
            return new BufferedReader(new StringReader(contenido));
        } catch (Exception e) {
            throw new RuntimeException("Error al abrir el CSV: " + e.getMessage(), e);
        }
    }

    public static List<String> leerCabecerasCsv(MultipartFile archivo, Integer filaCabecera) {
        try {
            if (filaCabecera == null) {
                filaCabecera = 0;
            }

            try (BufferedReader reader = abrirReaderCsv(archivo)) {
                String linea;
                int numeroFila = 0;
                Character separador = null;

                while ((linea = reader.readLine()) != null) {
                    if (linea.isBlank()) {
                        numeroFila++;
                        continue;
                    }

                    if (separador == null) {
                        separador = detectarSeparador(linea);
                    }

                    if (numeroFila == filaCabecera) {
                        List<String> valores = parsearLineaCsv(linea, separador);
                        return valores.stream()
                                .map(String::trim)
                                .filter(v -> !v.isBlank())
                                .toList();
                    }

                    numeroFila++;
                }
            }

            throw new IllegalArgumentException("El CSV no contiene fila de cabeceras en la fila indicada.");

        } catch (Exception e) {
            throw new RuntimeException("Error al leer cabeceras del CSV: " + e.getMessage(), e);
        }
    }

    private static Workbook convertirCsvAWorkbook(MultipartFile archivo) {
        try {
            String contenido = leerContenidoCsv(archivo);
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("CSV");

            try (BufferedReader reader = new BufferedReader(new StringReader(contenido))) {
                String linea;
                int numeroFila = 0;
                Character separador = null;

                while ((linea = reader.readLine()) != null) {
                    if (linea.isBlank()) {
                        continue;
                    }

                    if (separador == null) {
                        separador = detectarSeparador(linea);
                    }

                    Row row = sheet.createRow(numeroFila);
                    List<String> valores = parsearLineaCsv(linea, separador);

                    for (int i = 0; i < valores.size(); i++) {
                        Cell cell = row.createCell(i);
                        cell.setCellValue(valores.get(i));
                    }

                    numeroFila++;
                }
            }

            return workbook;

        } catch (Exception e) {
            throw new RuntimeException("No se pudo convertir el CSV a un libro Excel interno.", e);
        }
    }

    private static String leerContenidoCsv(MultipartFile archivo) throws Exception {
        byte[] bytes = archivo.getBytes();

        if (bytes.length >= 2) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;

            if (b0 == 0xFF && b1 == 0xFE) {
                return limpiarBom(new String(bytes, StandardCharsets.UTF_16LE));
            }

            if (b0 == 0xFE && b1 == 0xFF) {
                return limpiarBom(new String(bytes, StandardCharsets.UTF_16BE));
            }
        }

        String contenido = new String(bytes, StandardCharsets.UTF_8);

        if (contenido.contains("\uFFFD")) {
            contenido = new String(bytes, Charset.forName("windows-1252"));
        }

        return limpiarBom(contenido);
    }

    private static String limpiarBom(String contenido) {
        if (contenido != null && contenido.startsWith("\uFEFF")) {
            return contenido.substring(1);
        }

        return contenido;
    }

    public static char detectarSeparador(String lineaCabecera) {
        int puntoYComa = contarCaracter(lineaCabecera, ';');
        int coma = contarCaracter(lineaCabecera, ',');
        int tabulador = contarCaracter(lineaCabecera, '\t');

        if (tabulador > puntoYComa && tabulador > coma) {
            return '\t';
        }

        if (puntoYComa >= coma) {
            return ';';
        }

        return ',';
    }

    private static int contarCaracter(String texto, char caracter) {
        int contador = 0;

        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) == caracter) {
                contador++;
            }
        }

        return contador;
    }

    public static List<String> parsearLineaCsv(String linea, char separador) {
        List<String> valores = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean dentroDeComillas = false;

        for (int i = 0; i < linea.length(); i++) {
            char caracter = linea.charAt(i);

            if (caracter == '"') {
                if (dentroDeComillas && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    actual.append('"');
                    i++;
                } else {
                    dentroDeComillas = !dentroDeComillas;
                }
            } else if (caracter == separador && !dentroDeComillas) {
                valores.add(actual.toString().trim());
                actual.setLength(0);
            } else {
                actual.append(caracter);
            }
        }

        valores.add(actual.toString().trim());

        return valores;
    }
}
