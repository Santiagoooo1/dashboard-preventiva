package com.preventiva.backend.entity;

import com.preventiva.backend.dto.ErrorImportacionTrabajoDto;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

/**
 * Una fila materializada de la copia de trabajo. {@code valoresOriginales} se
 * escribe una sola vez al crear la copia y no se sobrescribe nunca; las
 * correcciones se superponen en {@code valoresCorregidos} (solo overrides).
 * El valor efectivo de una celda es el corregido si existe, si no el original.
 */
@Entity
@Table(name = "filas_importacion_trabajo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilaImportacionTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "importacion_trabajo_id", nullable = false)
    private ImportacionTrabajo importacionTrabajo;

    /** Número de fila en el archivo original (1-based, igual que en los errores de validación). */
    @Column(name = "numero_fila_original", nullable = false)
    private Integer numeroFilaOriginal;

    /** Valores tal cual se leyeron del archivo, por nombre de columna origen. Inmutable. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valores_originales", columnDefinition = "jsonb")
    private Map<String, String> valoresOriginales;

    /** Solo overrides introducidos por el usuario, por nombre de columna origen. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valores_corregidos", columnDefinition = "jsonb")
    private Map<String, String> valoresCorregidos;

    @Column(nullable = false)
    @Builder.Default
    private Boolean excluida = false;

    /** Foto del último cálculo de validación; se sobrescribe entera en cada revalidación. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "errores_actuales", columnDefinition = "jsonb")
    private List<ErrorImportacionTrabajoDto> erroresActuales;
}
