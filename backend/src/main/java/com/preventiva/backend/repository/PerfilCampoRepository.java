package com.preventiva.backend.repository;

import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.util.FiltroSqlBuilder;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Estadísticas por columna, resueltas EN BASE DE DATOS (Fase 6.9I.2).
 *
 * <p>El constructor de métricas necesita saber, de cada campo, cuántos valores
 * tiene informados y cuántos distintos. Traerse los registros para contarlos en
 * Java sería el mismo antipatrón que se corrigió en la Fase 6.9H.3: aquí todo
 * es {@code count}, {@code min} y {@code max} sobre un único recorrido, y los
 * valores de ejemplo salen siempre con {@code LIMIT}.
 *
 * <p>Lo que se concatena al SQL procede de {@link CampoClinico} ya validado o de
 * constantes internas; los valores viajan como parámetros.
 */
@Repository
@RequiredArgsConstructor
public class PerfilCampoRepository {

    private static final String TABLA = "registros_clinicos_genericos r";

    private final EntityManager entityManager;

    /**
     * Total de registros, informados, distintos, mínimo y máximo de un campo,
     * en una sola pasada.
     *
     * <p>Devuelve {@code [total, informados, distintos, min, max]}. Mínimo y
     * máximo se piden como texto: sirven para orientar al usuario en el
     * formulario, no para calcular nada.
     */
    public Object[] estadisticas(Long datasetId, CampoClinico campo) {
        String valor = FiltroSqlBuilder.expresionValor(campo);
        String texto = "CAST(" + valor + " AS text)";

        // `btrim(...) <> ''` iguala el criterio de "informado" al del motor en
        // memoria, que también descarta las cadenas en blanco.
        String informado = valor + " IS NOT NULL AND btrim(" + texto + ") <> ''";

        Query q = entityManager.createNativeQuery(
                "SELECT count(*),"
                        + " count(*) FILTER (WHERE " + informado + "),"
                        + " count(DISTINCT " + texto + ") FILTER (WHERE " + informado + "),"
                        + " min(" + texto + ") FILTER (WHERE " + informado + "),"
                        + " max(" + texto + ") FILTER (WHERE " + informado + ")"
                        + " FROM " + TABLA + " WHERE r.dataset_id = :datasetId");

        q.setParameter("datasetId", datasetId);
        parametroCampo(q, campo);

        return (Object[]) q.getSingleResult();
    }

    /**
     * Unos pocos valores de ejemplo, los más frecuentes primero.
     *
     * <p>Siempre con {@code LIMIT}: el constructor nunca debe descargar el
     * contenido íntegro de una columna, y menos aún de una de texto clínico.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> valoresFrecuentes(Long datasetId, CampoClinico campo, int limite) {
        String valor = FiltroSqlBuilder.expresionValor(campo);
        String texto = "CAST(" + valor + " AS text)";

        Query q = entityManager.createNativeQuery(
                "SELECT " + texto + " AS v, count(*) FROM " + TABLA
                        + " WHERE r.dataset_id = :datasetId"
                        + " AND " + valor + " IS NOT NULL AND btrim(" + texto + ") <> ''"
                        + " GROUP BY v ORDER BY count(*) DESC, v ASC LIMIT :limite");

        q.setParameter("datasetId", datasetId);
        parametroCampo(q, campo);
        q.setParameter("limite", limite);

        return q.getResultList();
    }

    /** Un campo dinámico necesita su clave como parámetro; uno con columna física, no. */
    private void parametroCampo(Query q, CampoClinico campo) {
        if (FiltroSqlBuilder.esColumnaComun(campo)) return;
        String nombre = "campo_" + campo.getCodigo().toLowerCase().replaceAll("[^a-z0-9]", "_");
        q.setParameter(nombre, campo.getCodigo());
    }
}
