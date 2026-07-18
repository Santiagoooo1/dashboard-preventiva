package com.preventiva.backend.entity;

import com.preventiva.backend.enums.TipoVisualizacion;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "panel_metricas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PanelMetrica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "panel_id", nullable = false)
    private PanelClinico panel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "metrica_id", nullable = false)
    private MetricaClinica metrica;

    @Column(name = "titulo_personalizado", length = 150)
    private String tituloPersonalizado;

    @Column(name = "descripcion_personalizada", length = 500)
    private String descripcionPersonalizada;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_visualizacion", nullable = false, length = 30)
    private TipoVisualizacion tipoVisualizacion;

    @Column(nullable = false)
    @Builder.Default
    private Integer orden = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer ancho = 3;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activa = true;
}
