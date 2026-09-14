package com.preventiva.backend.entity;

import com.preventiva.backend.enums.OrientacionPagina;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/** Una hoja del informe. Por defecto A4 vertical. */
@Entity
@Table(name = "paginas_informe")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaginaInforme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "informe_id", nullable = false)
    private InformeClinico informe;

    @Builder.Default
    @Column(nullable = false)
    private Integer orden = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrientacionPagina orientacion = OrientacionPagina.VERTICAL;

    @Builder.Default
    @OneToMany(mappedBy = "pagina", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC, id ASC")
    private List<BloqueInforme> bloques = new ArrayList<>();
}
