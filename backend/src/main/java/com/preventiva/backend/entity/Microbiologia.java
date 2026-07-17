package com.preventiva.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "microbiologias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Microbiologia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "infeccion_id", nullable = false, unique = true)
    private InfeccionQuirurgica infeccion;

    @Column(name = "cultivo_ilq")
    private Boolean cultivoIlq;

    @Column(columnDefinition = "TEXT")
    private String tipoMuestra;

    @Column(columnDefinition = "TEXT")
    private String otraMuestra;

    @Column(columnDefinition = "TEXT")
    private String resultadoCultivo;

    @Column(columnDefinition = "TEXT")
    private String microorganismo;

    @Column(columnDefinition = "TEXT")
    private String resistencia;

    @Column(columnDefinition = "TEXT")
    private String otraResistencia;
}