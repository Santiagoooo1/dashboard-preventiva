package com.preventiva.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "profilaxis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Profilaxis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cirugia_id", nullable = false, unique = true)
    private Cirugia cirugia;

    @Column(name = "indicacion_profilaxis", length = 100)
    private String indicacionProfilaxis;

    @Column(name = "administracion_profilaxis", length = 100)
    private String administracionProfilaxis;

    @Column(name = "profilaxis_drago", length = 100)
    private String profilaxisDrago;

    @Column(name = "adecuacion_profilaxis", length = 50)
    private String adecuacionProfilaxis;

    @Column(name = "motivo_inadecuacion", columnDefinition = "TEXT")
    private String motivoInadecuacion;

    @Column(name = "validacion_dosis_adicionales", length = 150)
    private String validacionDosisAdicionales;
}