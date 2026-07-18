package com.preventiva.backend.entity;

import com.preventiva.backend.enums.EstadoImportacion;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "importaciones_genericas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportacionGenerica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @Column(name = "nombre_original", length = 255)
    private String nombreOriginal;

    @Column(name = "fecha_importacion", nullable = false)
    private LocalDateTime fechaImportacion;

    @Column(name = "filas_leidas")
    private Integer filasLeidas;

    @Column(name = "filas_importadas")
    private Integer filasImportadas;

    @Column(name = "filas_con_error")
    private Integer filasConError;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EstadoImportacion estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plantilla_id")
    private PlantillaImportacion plantilla;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
}
