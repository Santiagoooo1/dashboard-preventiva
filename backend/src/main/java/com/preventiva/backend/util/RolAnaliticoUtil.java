package com.preventiva.backend.util;

import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.enums.RolAnaliticoCampo;
import com.preventiva.backend.enums.TipoDatoExcel;

import java.util.Collection;
import java.util.Optional;

/**
 * Deduce cómo interpretar analíticamente una columna (Fase 6.9I.2).
 *
 * <p>El tipo de dato resuelve casi todo; la excepción es TEXTO, que tanto puede
 * ser una categoría con ocho valores como un identificador de paciente o una
 * observación clínica irrepetible. Para distinguirlos hace falta mirar la
 * cardinalidad real, y eso solo lo sabe quien ha consultado la base — de ahí
 * las dos variantes: {@link #rolBase} para validar sin estadísticas y
 * {@link #rol} para ofrecer operaciones con ellas.
 */
public class RolAnaliticoUtil {

    /**
     * A partir de tantos valores distintos, un TEXTO deja de comportarse como
     * categoría: la distribución se vuelve ilegible y la leyenda empieza a
     * volcar texto clínico. Coincide con el umbral del perfil de subconjunto.
     */
    public static final int UMBRAL_CARDINALIDAD_CATEGORICA = 25;

    private RolAnaliticoUtil() {
    }

    /**
     * Rol sin consultar datos. TEXTO cae en CATEGORICO por defecto: es el
     * criterio permisivo, y como las operaciones de TEXTO_LIBRE son un
     * subconjunto de las categóricas, validar así nunca deja pasar algo que el
     * rol real prohibiría.
     */
    public static RolAnaliticoCampo rolBase(CampoClinico campo, Collection<CampoClinico> todosLosCampos) {
        if (esIdentificador(campo, todosLosCampos)) {
            return RolAnaliticoCampo.IDENTIFICADOR;
        }

        return switch (campo.getTipoDato()) {
            case BOOLEANO -> RolAnaliticoCampo.BOOLEANO;
            case ENTERO, DECIMAL -> RolAnaliticoCampo.NUMERICO;
            case FECHA -> RolAnaliticoCampo.FECHA;
            case TEXTO -> RolAnaliticoCampo.CATEGORICO;
        };
    }

    /** Rol definitivo: igual que {@link #rolBase} pero separando categoría de texto libre. */
    public static RolAnaliticoCampo rol(
            CampoClinico campo, Collection<CampoClinico> todosLosCampos, long valoresDistintos) {
        RolAnaliticoCampo base = rolBase(campo, todosLosCampos);

        if (base == RolAnaliticoCampo.CATEGORICO && valoresDistintos > UMBRAL_CARDINALIDAD_CATEGORICA) {
            return RolAnaliticoCampo.TEXTO_LIBRE;
        }

        return base;
    }

    /**
     * ¿Es ESTE campo el identificador individual del dataset? Se delega en
     * {@link CampoIndividuoResolver}, que ya resuelve el mismo problema para el
     * detalle de subconjuntos: un solo criterio para toda la aplicación.
     */
    public static boolean esIdentificador(CampoClinico campo, Collection<CampoClinico> todosLosCampos) {
        if (campo.getTipoDato() != TipoDatoExcel.TEXTO) {
            return false;
        }

        Optional<CampoClinico> individuo = CampoIndividuoResolver.resolver(todosLosCampos);
        return individuo.isPresent() && individuo.get().getCodigo().equals(campo.getCodigo());
    }
}
