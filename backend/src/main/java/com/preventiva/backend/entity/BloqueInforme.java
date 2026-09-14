package com.preventiva.backend.entity;

import com.preventiva.backend.dto.ComparacionInteranualRequestDto;
import com.preventiva.backend.dto.ConfiguracionWidgetDto;
import com.preventiva.backend.enums.TipoBloqueInforme;
import com.preventiva.backend.enums.TipoResultadoWidget;
import com.preventiva.backend.enums.TipoVisualizacion;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Un elemento colocado en una página del informe (Fase 6.9Q).
 *
 * <p>Los bloques analíticos <b>referencian</b> su origen; no copian el
 * resultado. Guardar «tasa = 3,23» dentro del informe lo convertiría en una foto
 * que envejece sin avisar: el informe volvería a abrirse con la cifra del día en
 * que se montó, no con la de hoy.
 *
 * <p>La forma es a propósito la misma que {@link PanelMetrica} —métrica,
 * visualización, tipo de resultado y una configuración en JSON— para que el
 * cálculo pueda reutilizar el motor del dashboard tal cual, sin una segunda ruta
 * analítica que pueda dar otros números.
 *
 * <p>Sobre el equilibrio de columnas: lo que es común a todos los bloques
 * (orden, ancho, referencia) va en columnas propias, y solo lo específico de un
 * tipo viaja en JSON. Ni una tabla por tipo de bloque ni un JSON para todo.
 */
@Entity
@Table(name = "bloques_informe")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BloqueInforme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pagina_id", nullable = false)
    private PaginaInforme pagina;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_bloque", nullable = false, length = 40)
    private TipoBloqueInforme tipoBloque;

    @Builder.Default
    @Column(nullable = false)
    private Integer orden = 0;

    /**
     * Columnas que ocupa sobre una rejilla de 12.
     *
     * <p>Se guarda el número de columnas y no una posición en píxeles: un
     * informe con {@code x = 137px} se descuadra al cambiar la fuente, el
     * navegador o la longitud de un título, y al pasarlo a PDF se descuadra
     * seguro.
     */
    @Builder.Default
    @Column(nullable = false)
    private Integer ancho = 12;

    /**
     * Métrica de la que sale el dato. Nula en los bloques de contenido (título,
     * texto, separador) y en la comparación interanual, que lleva la suya.
     *
     * <p>Sin {@code cascade}: borrar un informe nunca debe llevarse por delante
     * una métrica que otros dashboards usan.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "metrica_id")
    private MetricaClinica metrica;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_visualizacion", length = 30)
    private TipoVisualizacion tipoVisualizacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_resultado_widget", length = 30)
    private TipoResultadoWidget tipoResultadoWidget;

    /** Granularidad, campo de fecha, segmentación… lo mismo que en un widget. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuracion_widget", columnDefinition = "jsonb")
    private ConfiguracionWidgetDto configuracionWidget;

    /**
     * Qué comparar y entre qué datasets. Solo en COMPARACION_INTERANUAL.
     *
     * <p>Es la misma petición que acepta el endpoint de comparación, así que el
     * informe no necesita traducir nada ni conocer su interior.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuracion_comparacion", columnDefinition = "jsonb")
    private ComparacionInteranualRequestDto configuracionComparacion;

    /** Texto plano de los bloques de contenido. Nunca HTML: se escapa al pintar. */
    @Column(name = "contenido_texto", length = 4000)
    private String contenidoTexto;

    /** Título propio del bloque; si falta, se usa el nombre de la métrica. */
    @Column(name = "titulo_personalizado", length = 200)
    private String tituloPersonalizado;
}
