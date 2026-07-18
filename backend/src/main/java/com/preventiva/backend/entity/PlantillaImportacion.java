package com.preventiva.backend.entity;

import com.preventiva.backend.enums.OrigenImportacion;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "plantillas_importacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlantillaImportacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false)
    private DatasetClinico dataset;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrigenImportacion origen;

    @Column(name = "fila_cabecera")
    private Integer filaCabecera;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activa = true;
}
