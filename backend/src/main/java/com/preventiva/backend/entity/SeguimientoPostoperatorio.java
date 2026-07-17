package com.preventiva.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "seguimientos_postoperatorios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeguimientoPostoperatorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cirugia_id", nullable = false, unique = true)
    private Cirugia cirugia;

    @Column(name = "edad_meses_menor_2")
    private Integer edadMesesMenor2;

    @Column(name = "exitus_post_cirugia")
    private Boolean exitusPostCirugia;

    @Column(name = "motivo_alta", length = 255)
    private String motivoAlta;

    @Column(columnDefinition = "TEXT")
    private String comentarios;
}