package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Informe completo, con sus páginas y bloques.
 *
 * <p>Cuando se pide con resultados, cada bloque analítico viene ya calculado:
 * una sola llamada para toda la página, no una por bloque.
 */
@Getter
@Setter
@Builder
public class InformeClinicoResponseDto {

    private Long id;

    /**
     * El único título que la interfaz enseña (Fase 6.9Q.2).
     *
     * <p>{@code nombre} y {@code titulo} significaban casi lo mismo y la UI
     * pedía los dos. Se conservan ambas columnas por compatibilidad, pero quien
     * pinta el informe usa solo este campo y no tiene que decidir nada.
     */
    private String tituloVisible;

    /** @deprecated se conserva por compatibilidad; usar {@link #tituloVisible}. */
    @Deprecated
    private String nombre;

    /** @deprecated se conserva por compatibilidad; usar {@link #tituloVisible}. */
    @Deprecated
    private String titulo;
    private String descripcion;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    private Integer totalPaginas;
    private List<PaginaInformeResponseDto> paginas;

    /**
     * Trazabilidad para la cabecera o el pie: cuándo se consultó y de qué
     * datasets sale. Sin identificadores técnicos.
     */
    private LocalDateTime generadoEn;
    private List<String> datasetsUtilizados;
}
