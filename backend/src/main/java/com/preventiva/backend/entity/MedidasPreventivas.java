package com.preventiva.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "medidas_preventivas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedidasPreventivas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cirugia_id", nullable = false, unique = true)
    private Cirugia cirugia;

    @Column(name = "monitorizacion_temperatura", length = 150)
    private String monitorizacionTemperatura;

    @Column(name = "glucemia_intraoperatoria", length = 150)
    private String glucemiaIntraoperatoria;

    @Column(name = "eliminacion_vello", length = 150)
    private String eliminacionVello;

    @Column(name = "momento_eliminacion_vello", length = 150)
    private String momentoEliminacionVello;

    @Column(name = "tecnica_eliminacion_vello", length = 150)
    private String tecnicaEliminacionVello;

    @Column(name = "otra_tecnica_eliminacion_vello", length = 255)
    private String otraTecnicaEliminacionVello;

    @Column(name = "antisepsia_piel", length = 255)
    private String antisepsiaPiel;
}