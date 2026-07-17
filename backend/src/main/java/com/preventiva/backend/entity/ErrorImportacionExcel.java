package com.preventiva.backend.entity;

import com.preventiva.backend.enums.SeveridadError;
import com.preventiva.backend.enums.TipoErrorImportacion;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "errores_importacion_excel")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorImportacionExcel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "importacion_id", nullable = false)
    private ImportacionExcel importacion;

    @Column(name = "numero_fila")
    private Integer numeroFila;

    @Column(name = "nombre_columna", length = 255)
    private String nombreColumna;

    @Column(name = "valor_original", columnDefinition = "TEXT")
    private String valorOriginal;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_error", nullable = false, length = 100)
    private TipoErrorImportacion tipoError;

    @Enumerated(EnumType.STRING)
    @Column(name = "severidad", length = 20)
    private SeveridadError severidad;

    @Column(columnDefinition = "TEXT")
    private String mensaje;
}