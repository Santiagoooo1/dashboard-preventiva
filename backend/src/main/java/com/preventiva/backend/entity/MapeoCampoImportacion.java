package com.preventiva.backend.entity;

import com.preventiva.backend.enums.PoliticaCampoFaltante;
import com.preventiva.backend.enums.TipoDatoExcel;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mapeos_campo_importacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MapeoCampoImportacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plantilla_id", nullable = false)
    private PlantillaImportacion plantilla;

    @Column(name = "nombre_columna_origen", nullable = false, length = 255)
    private String nombreColumnaOrigen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campo_clinico_id", nullable = false)
    private CampoClinico campoClinico;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_dato", nullable = false, length = 50)
    private TipoDatoExcel tipoDato;

    @Column(nullable = false)
    @Builder.Default
    private Boolean obligatorio = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_campo_faltante", length = 30)
    private PoliticaCampoFaltante politicaCampoFaltante;

    @Column(name = "valor_por_defecto", length = 255)
    private String valorPorDefecto;

    private Integer orden;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
