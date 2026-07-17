package com.preventiva.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "cirugias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cirugia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String hc;

    @Column(length = 20)
    private String sexo;

    private Integer edad;

    @Column(name = "fecha_ingreso")
    private LocalDate fechaIngreso;

    @Column(name = "fecha_alta")
    private LocalDate fechaAlta;

    @Column(name = "fecha_cirugia")
    private LocalDate fechaCirugia;

    @Column(length = 255)
    private String procedimiento;

    @Column(length = 50)
    private String cie10;

    @Column(name = "duracion_minutos")
    private Integer duracionMinutos;

    @Column(name = "cirugia_urgente")
    private Boolean cirugiaUrgente;

    @Column(length = 50)
    private String asa;

    @Column(name = "grado_contaminacion_inicial", length = 100)
    private String gradoContaminacionInicial;

    @Column(name = "grado_contaminacion_final", length = 100)
    private String gradoContaminacionFinal;

    @Column(name = "abordaje_quirurgico", length = 100)
    private String abordajeQuirurgico;

    @Column(name = "multirresistentes")
    private Boolean multirresistentes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servicio_id")
    private Servicio servicio;
}
