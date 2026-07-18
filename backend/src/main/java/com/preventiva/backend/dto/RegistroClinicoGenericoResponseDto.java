package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Builder
public class RegistroClinicoGenericoResponseDto {

    private Long id;
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
    private String hospitalNombre;
    private Long importacionId;
    private Map<String, Object> datosDinamicos;
    private LocalDateTime fechaCreacion;
}
