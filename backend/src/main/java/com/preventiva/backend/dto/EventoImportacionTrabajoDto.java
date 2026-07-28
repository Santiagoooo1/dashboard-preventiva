package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class EventoImportacionTrabajoDto {

    private Long id;
    private String tipoEvento;
    private Integer numeroFilaOriginal;
    private String nombreColumna;
    private String valorAnterior;
    private String valorNuevo;
    private String detalle;
    private LocalDateTime fechaEvento;
    private String actor;
}
