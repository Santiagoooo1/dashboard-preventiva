package com.preventiva.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Map;

@Getter
@Setter
public class RegistroClinicoGenericoRequestDto {

    @NotNull
    private Long datasetId;

    private String pacienteCodigo;
    private LocalDate fechaEvento;
    private String servicio;
    private String tipoEvento;
    private String procedimiento;
    private String diagnostico;
    private Integer edad;
    private String sexo;
    private Long hospitalId;
    private Map<String, Object> datosDinamicos;
}
