package com.preventiva.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "registros_clinicos_genericos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistroClinicoGenerico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false)
    private DatasetClinico dataset;

    @Column(name = "paciente_codigo", length = 100)
    private String pacienteCodigo;

    @Column(name = "fecha_evento")
    private LocalDate fechaEvento;

    @Column(length = 150)
    private String servicio;

    @Column(name = "tipo_evento", length = 150)
    private String tipoEvento;

    @Column(length = 255)
    private String procedimiento;

    @Column(length = 255)
    private String diagnostico;

    private Integer edad;

    @Column(length = 20)
    private String sexo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospital_id")
    private Hospital hospital;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "importacion_id")
    private ImportacionGenerica importacion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_dinamicos", columnDefinition = "jsonb")
    private Map<String, Object> datosDinamicos;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;
}
