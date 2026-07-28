package com.preventiva.backend.entity;

import com.preventiva.backend.enums.TipoEventoImportacionTrabajo;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Registro de auditoría de una acción sobre una {@link ImportacionTrabajo}:
 * qué se hizo, sobre qué fila/columna, y con qué valores. Es solo lectura una
 * vez creado (no se actualiza ni se borra salvo cuando se descarta la copia
 * de trabajo en cascada, ver {@code descartarBorrador} en
 * DatasetClinicoServiceImpl).
 */
@Entity
@Table(name = "eventos_importacion_trabajo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoImportacionTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "importacion_trabajo_id", nullable = false)
    private ImportacionTrabajo importacionTrabajo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 50)
    private TipoEventoImportacionTrabajo tipoEvento;

    @Column(name = "numero_fila_original")
    private Integer numeroFilaOriginal;

    @Column(name = "nombre_columna", length = 255)
    private String nombreColumna;

    @Column(name = "valor_anterior", columnDefinition = "TEXT")
    private String valorAnterior;

    @Column(name = "valor_nuevo", columnDefinition = "TEXT")
    private String valorNuevo;

    // JSON simple (vía ObjectMapper ya presente por spring-boot-starter-web,
    // no es una librería nueva), con un resumen acotado de la acción. Nunca
    // el archivo completo ni todas las filas: ver punto 10 del encargo.
    @Column(columnDefinition = "TEXT")
    private String detalle;

    @Column(name = "fecha_evento", nullable = false)
    private LocalDateTime fechaEvento;

    @Column(length = 100)
    private String actor;
}
