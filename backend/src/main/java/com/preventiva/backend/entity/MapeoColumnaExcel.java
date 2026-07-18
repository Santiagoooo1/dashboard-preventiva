package com.preventiva.backend.entity;

import com.preventiva.backend.enums.CampoDestino;
import com.preventiva.backend.enums.PoliticaCampoFaltante;
import com.preventiva.backend.enums.TipoDatoExcel;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mapeos_columnas_excel")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MapeoColumnaExcel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plantilla_id", nullable = false)
    private PlantillaExcel plantilla;

    @Column(name = "nombre_columna_excel", nullable = false, length = 255)
    private String nombreColumnaExcel;

    @Enumerated(EnumType.STRING)
    @Column(name = "campo_destino", nullable = false, length = 100)
    private CampoDestino campoDestino;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_dato", nullable = false, length = 50)
    private TipoDatoExcel tipoDato;

    @Column(nullable = false)
    @Builder.Default
    private Boolean obligatoria = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_campo_faltante", length = 30)
    private PoliticaCampoFaltante politicaCampoFaltante;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activa = true;
}