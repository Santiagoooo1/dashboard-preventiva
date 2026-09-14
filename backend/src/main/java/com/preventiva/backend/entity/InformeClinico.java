package com.preventiva.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Un informe clínico: la PLANTILLA de un documento, no el documento generado
 * (Fase 6.9Q).
 *
 * <p>No guarda ningún resultado calculado. Guarda a qué apuntan sus bloques,
 * así que abrirlo mañana da las cifras de mañana. Un PDF descargado hoy sigue
 * diciendo lo de hoy aunque la plantilla cambie después: son cosas distintas y
 * conviene que lo sigan siendo.
 */
@Entity
@Table(name = "informes_clinicos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InformeClinico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre con el que el usuario lo encuentra en su lista. */
    @Column(nullable = false, length = 150)
    private String nombre;

    /** Título impreso en la portada del documento. */
    @Column(length = 200)
    private String titulo;

    @Column(length = 1000)
    private String descripcion;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Builder.Default
    @Column(nullable = false)
    private Boolean activo = true;

    @Builder.Default
    @OneToMany(mappedBy = "informe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC, id ASC")
    private List<PaginaInforme> paginas = new ArrayList<>();
}
