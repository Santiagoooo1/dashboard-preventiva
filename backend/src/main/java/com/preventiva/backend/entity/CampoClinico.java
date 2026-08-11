package com.preventiva.backend.entity;

import com.preventiva.backend.enums.PrioridadDashboardCampo;
import com.preventiva.backend.enums.TipoDatoExcel;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "campos_clinicos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampoClinico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false)
    private DatasetClinico dataset;

    @Column(nullable = false, length = 100)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String etiqueta;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_dato", nullable = false, length = 50)
    private TipoDatoExcel tipoDato;

    /** Pertenece al modelo clínico común (paciente, fecha, procedimiento…). */
    @Column(name = "es_comun", nullable = false)
    @Builder.Default
    private Boolean esComun = false;

    /** El dato debe venir informado al importar. Regla de calidad, no de análisis. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean obligatorio = false;

    /**
     * Relevancia para los dashboards. Independiente de {@code esComun} y
     * {@code obligatorio}: ver {@link PrioridadDashboardCampo}.
     *
     * <p>Se persiste como texto para que la columna se lea sola en la base de
     * datos y sobreviva a reordenar el enum.
     *
     * <p>Nullable a propósito: {@code ddl-auto=update} añade la columna vacía en
     * las filas que ya existían, y {@code BackfillPrioridadDashboard} las
     * rellena al arrancar. El getter nunca devuelve null (ver más abajo).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "prioridad_dashboard", length = 20)
    @Builder.Default
    private PrioridadDashboardCampo prioridadDashboard = PrioridadDashboardCampo.NORMAL;

    /**
     * Nunca null: una fila anterior al backfill, o creada por una ruta que no
     * fije el valor, se comporta como NORMAL en vez de reventar el ranking.
     */
    public PrioridadDashboardCampo getPrioridadDashboard() {
        return prioridadDashboard != null ? prioridadDashboard : PrioridadDashboardCampo.NORMAL;
    }

    private Integer orden;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
