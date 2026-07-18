package com.preventiva.backend.enums;

public enum PoliticaCampoFaltante {
    ERROR,
    ADVERTENCIA,
    IGNORAR,
    NULO;

    public static PoliticaCampoFaltante porDefecto(boolean obligatoria) {
        return obligatoria ? ERROR : ADVERTENCIA;
    }
}
