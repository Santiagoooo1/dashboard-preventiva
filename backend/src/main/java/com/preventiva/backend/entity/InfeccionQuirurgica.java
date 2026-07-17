package com.preventiva.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "infecciones_quirurgicas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InfeccionQuirurgica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cirugia_id", nullable = false, unique = true)
    private Cirugia cirugia;

    @Column(name = "tiene_ilq")
    private Boolean tieneIlq;

    @Column(name = "fecha_ilq")
    private LocalDate fechaIlq;

    @Column(name = "fecha_fin_vigilancia")
    private LocalDate fechaFinVigilancia;

    @Column(name = "localizacion_infeccion", length = 150)
    private String localizacionInfeccion;

    @Column(name = "reingreso_por_ilq")
    private Boolean reingresoPorIlq;

    @Column(name = "fecha_reingreso")
    private LocalDate fechaReingreso;
}