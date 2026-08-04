package com.preventiva.backend.util;

import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.enums.TipoDatoExcel;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Decide qué campo de un dataset identifica al INDIVIDUO (el paciente).
 *
 * <p>El backend es la autoridad. Antes bastaba con que el frontend enviara un
 * campo TEXTO cualquiera para que se usara como identificador, lo que permitía
 * pedir "pacientes únicos" agrupando por {@code sexo}, {@code procedimiento} o
 * {@code cie10} y obtener un COUNT DISTINCT sin ningún significado clínico
 * («2 pacientes» cuando en realidad son dos sexos distintos).
 *
 * <p>Ahora el valor que llega del frontend es solo una PISTA: se resuelve el
 * campo admisible a partir del propio dataset y únicamente se acepta si ambos
 * coinciden.
 *
 * <p>La lista es canónica y se compara sobre el CÓDIGO normalizado, nunca sobre
 * la etiqueta visible: una etiqueta puede decir "Paciente" en un campo que en
 * realidad guarde el servicio.
 */
public final class CampoIndividuoResolver {

    /**
     * Códigos admitidos, en orden de preferencia. El primero es el real del
     * proyecto ({@code pacienteCodigo}, campo común de
     * RegistroClinicoGenerico); el resto son variantes habituales en
     * exportaciones hospitalarias.
     */
    private static final List<String> CODIGOS_CANONICOS = List.of(
            "pacientecodigo",
            "codigopaciente",
            "historiaclinica",
            "numerohistoriaclinica",
            "numerohistoria",
            "nhc",
            "hc",
            "pacienteid",
            "idpaciente",
            "paciente");

    private CampoIndividuoResolver() {
    }

    /** Compara por código, ignorando mayúsculas, acentos y separadores. */
    private static String normalizar(String codigo) {
        return codigo == null ? "" : codigo.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * Campo que identifica al individuo en este dataset, si existe uno
     * inequívoco. Debe ser de tipo TEXTO: un identificador numérico o una fecha
     * no se tratan como código de paciente.
     *
     * @param campos campos ACTIVOS del dataset
     */
    public static Optional<CampoClinico> resolver(Collection<CampoClinico> campos) {
        if (campos == null) return Optional.empty();

        for (String canonico : CODIGOS_CANONICOS) {
            Optional<CampoClinico> encontrado = campos.stream()
                    .filter(c -> c.getTipoDato() == TipoDatoExcel.TEXTO)
                    .filter(c -> normalizar(c.getCodigo()).equals(canonico))
                    .findFirst();
            if (encontrado.isPresent()) return encontrado;
        }

        return Optional.empty();
    }

    /**
     * Resuelve el campo individuo teniendo en cuenta la pista del cliente.
     *
     * <ul>
     *   <li>Sin pista → se usa el que resuelve el backend (es la autoridad).</li>
     *   <li>Con pista que coincide → se acepta.</li>
     *   <li>Con pista que NO coincide (p. ej. {@code sexo}) → vacío, y el
     *       llamante degrada la vista de pacientes en vez de contar cualquier
     *       cosa como si fueran individuos.</li>
     * </ul>
     *
     * Nunca lanza: una pista incorrecta es una degradación controlada, no un
     * error técnico que deba romper el detalle del subconjunto.
     */
    public static Optional<CampoClinico> resolverConPista(Collection<CampoClinico> campos, String pista) {
        Optional<CampoClinico> resuelto = resolver(campos);

        if (pista == null || pista.isBlank()) {
            return resuelto;
        }

        return resuelto
                .filter(campo -> normalizar(campo.getCodigo()).equals(normalizar(pista)));
    }
}
