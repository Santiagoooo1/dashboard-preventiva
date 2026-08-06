package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Si un dataset admite el dashboard clínico de ILQ (Fase 6.9I.4).
 *
 * <p>Se responde siempre, también cuando no es compatible: un dataset que no
 * sea de vigilancia de ILQ no es un error, simplemente no le corresponde este
 * dashboard.
 */
@Getter
@Setter
@Builder
public class CompatibilidadDashboardIlqDto {

    private Long datasetId;
    private String datasetCodigo;

    /** Están todos los campos esenciales y activos. */
    private Boolean compatible;

    /** Ya hay un panel con el código de la plantilla: aplicar sería actualizar. */
    private Boolean yaAplicado;

    /** Id del panel si ya existe, para poder abrirlo directamente. */
    private Long panelId;

    private List<String> camposEncontrados;
    private List<String> camposAusentes;

    /** Cuántas métricas y widgets creará o actualizará la plantilla. */
    private int totalElementos;
}
