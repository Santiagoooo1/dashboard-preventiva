package com.preventiva.backend.entity;

import com.preventiva.backend.enums.EstadoDatasetClinico;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "datasets_clinicos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatasetClinico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospital_id")
    private Hospital hospital;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    // Nula en filas creadas antes de esta columna: se trata como ACTIVO en el
    // servicio, así que no hace falta backfill ni columna NOT NULL con
    // ddl-auto=update.
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_dataset", length = 20)
    private EstadoDatasetClinico estadoDataset;
}
