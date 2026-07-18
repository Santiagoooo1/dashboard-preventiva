package com.preventiva.backend.entity;

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

    @Column(name = "es_comun", nullable = false)
    @Builder.Default
    private Boolean esComun = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean obligatorio = false;

    private Integer orden;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
