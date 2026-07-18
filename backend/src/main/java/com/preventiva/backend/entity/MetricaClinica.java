package com.preventiva.backend.entity;

import com.preventiva.backend.dto.ConfiguracionMetricaDto;
import com.preventiva.backend.enums.TipoMetrica;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "metricas_clinicas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricaClinica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false)
    private DatasetClinico dataset;

    @Column(nullable = false, length = 100)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_metrica", nullable = false, length = 30)
    private TipoMetrica tipoMetrica;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuracion", columnDefinition = "jsonb", nullable = false)
    private ConfiguracionMetricaDto configuracion;

    @Column(length = 20)
    private String unidad;

    @Column(nullable = false)
    @Builder.Default
    private Integer decimales = 2;

    @Column(nullable = false)
    @Builder.Default
    private Integer orden = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activa = true;
}
