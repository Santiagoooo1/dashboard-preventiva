package com.preventiva.backend.entity;

import com.preventiva.backend.dto.ErrorImportacionTrabajoDto;
import com.preventiva.backend.enums.EstadoImportacionTrabajo;
import com.preventiva.backend.enums.OrigenImportacion;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Copia interna de trabajo de una importación. El archivo original se conserva
 * como referencia inmutable ({@code contenidoArchivo} se escribe una sola vez
 * al crear la copia y ningún flujo posterior lo modifica); las correcciones y
 * exclusiones se guardan aparte, en {@link FilaImportacionTrabajo}.
 */
@Entity
@Table(name = "importaciones_trabajo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportacionTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false)
    private DatasetClinico dataset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plantilla_id", nullable = false)
    private PlantillaImportacion plantilla;

    @Column(name = "nombre_archivo_original", length = 255)
    private String nombreArchivoOriginal;

    // Bytes del archivo tal cual se subió. Inmutable por diseño: se asigna en
    // la creación y ningún endpoint posterior lo recibe ni lo reescribe.
    @Column(name = "contenido_archivo", columnDefinition = "bytea", nullable = false)
    private byte[] contenidoArchivo;

    // SHA-256 en hexadecimal de contenidoArchivo, calculado una única vez al
    // crear la copia (Fase 6.8D.1). Sirve para demostrar que las correcciones
    // posteriores no tocan el archivo original. Nullable: las copias creadas
    // antes de este campo no tienen hash retroactivo.
    @Column(name = "hash_archivo_original", length = 64)
    private String hashArchivoOriginal;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrigenImportacion origen;

    @Column(name = "indice_hoja")
    private Integer indiceHoja;

    @Column(name = "fila_cabecera")
    private Integer filaCabecera;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EstadoImportacionTrabajo estado;

    @Column(name = "total_filas_leidas")
    private Integer totalFilasLeidas;

    /** Columnas del archivo que casaron con un mapeo activo (nombre de columna origen del mapeo). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "columnas_presentes", columnDefinition = "jsonb")
    private List<String> columnasPresentes;

    /** Errores de nivel archivo/cabecera (columna obligatoria faltante, sin filas clínicas...). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "errores_globales", columnDefinition = "jsonb")
    private List<ErrorImportacionTrabajoDto> erroresGlobales;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "importacion_generica_id")
    private ImportacionGenerica importacionGenerica;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_ultima_revalidacion")
    private LocalDateTime fechaUltimaRevalidacion;
}
