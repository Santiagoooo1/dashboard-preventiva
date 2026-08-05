package com.preventiva.backend.enums;

/**
 * Qué hacer con los registros cuyo campo objetivo está sin informar.
 *
 * <p>En datos clínicos «sin documentar» no equivale a «no»: que no conste la
 * prescripción no significa que no se prescribiera. Por eso el tratamiento es
 * una decisión explícita de cada métrica y no un comportamiento implícito.
 */
public enum TratamientoNulos {

    /** Se descartan. Es el comportamiento por defecto de medias, medianas y conteos distintos. */
    EXCLUIR,

    /** Se agrupan bajo «Sin dato» como una categoría más (solo tiene sentido en distribuciones). */
    INCLUIR_COMO_CATEGORIA
}
