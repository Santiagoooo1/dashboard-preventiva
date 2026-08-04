package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class PerfilFechaDto {

    private String campo;
    private String etiqueta;
    private long valoresValidos;
    private long valoresAusentes;
    private LocalDate primera;
    private LocalDate ultima;
}
