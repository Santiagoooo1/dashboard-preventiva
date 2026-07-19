package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CampoRolesDto {

    private Boolean filtrable;
    private Boolean agrupable;
    private Boolean numerico;
    private Boolean fecha;
}
