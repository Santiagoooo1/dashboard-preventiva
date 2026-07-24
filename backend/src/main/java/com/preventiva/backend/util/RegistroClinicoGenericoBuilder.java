package com.preventiva.backend.util;

import com.preventiva.backend.entity.ImportacionGenerica;
import com.preventiva.backend.entity.MapeoCampoImportacion;
import com.preventiva.backend.entity.PlantillaImportacion;
import com.preventiva.backend.entity.RegistroClinicoGenerico;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Construye un {@link RegistroClinicoGenerico} a partir de pares (mapeo, valor
 * en crudo). Es la única implementación de las reglas de asignación de campos
 * comunes y datos dinámicos: la comparten la importación genérica directa y la
 * importación desde copia de trabajo, para que ambas rutas produzcan registros
 * idénticos. No persiste: el llamante decide cuándo guardar.
 */
public final class RegistroClinicoGenericoBuilder {

    private static final Set<String> CAMPOS_COMUNES = Set.of(
            "pacienteCodigo", "fechaEvento", "servicio", "tipoEvento",
            "procedimiento", "diagnostico", "edad", "sexo");

    public record CampoValor(MapeoCampoImportacion mapeo, String valor) {
    }

    private RegistroClinicoGenericoBuilder() {
    }

    public static RegistroClinicoGenerico construir(
            PlantillaImportacion plantilla,
            ImportacionGenerica importacionGenerica,
            List<CampoValor> valores) {
        Map<String, Object> datosDinamicos = new HashMap<>();

        RegistroClinicoGenerico registro = RegistroClinicoGenerico.builder()
                .dataset(plantilla.getDataset())
                .hospital(plantilla.getDataset().getHospital())
                .importacion(importacionGenerica)
                .fechaCreacion(LocalDateTime.now())
                .build();

        for (CampoValor par : valores) {
            MapeoCampoImportacion mapeo = par.mapeo();

            CampoClinicoValueEvaluator.Resultado resultado =
                    CampoClinicoValueEvaluator.evaluar(mapeo, par.valor(), 0);

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
        return registro;
    }

    private static void asignarCampoComun(RegistroClinicoGenerico registro, String codigo, Object valor) {
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
}
