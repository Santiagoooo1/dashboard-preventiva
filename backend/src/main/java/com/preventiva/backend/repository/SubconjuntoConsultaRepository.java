package com.preventiva.backend.repository;

import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.util.FiltroSqlBuilder;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Consultas del subconjunto resueltas EN BASE DE DATOS (Fase 6.9H.3).
 *
 * <p>El WHERE lo produce {@link FiltroSqlBuilder} con la misma semántica que el
 * evaluador en memoria. Aquí se añaden COUNT, COUNT DISTINCT, ORDER BY, LIMIT y
 * OFFSET reales, de modo que una tabla paginada nunca materialice el
 * subconjunto entero.
 *
 * <p>Todo lo que se concatena al SQL procede de {@code CampoClinico} validado o
 * de constantes internas; los valores viajan siempre como parámetros.
 */
@Repository
@RequiredArgsConstructor
public class SubconjuntoConsultaRepository {

    private static final String TABLA = "registros_clinicos_genericos r";

    private final EntityManager entityManager;

    /** COUNT(*) del subconjunto completo: independiente de la paginación. */
    public long contarRegistros(Long datasetId, FiltroSqlBuilder.Where where) {
        Query q = entityManager.createNativeQuery(
                "SELECT count(*) FROM " + TABLA + " WHERE r.dataset_id = :datasetId AND " + where.sql());
        aplicar(q, datasetId, where);
        return ((Number) q.getSingleResult()).longValue();
    }

    /**
     * COUNT DISTINCT real del identificador individual sobre TODO el
     * subconjunto. Nunca se deduce del tamaño de una página.
     */
    public long contarIndividuosDistintos(Long datasetId, FiltroSqlBuilder.Where where, CampoClinico campoIndividuo) {
        String valor = FiltroSqlBuilder.expresionValor(campoIndividuo);
        Query q = entityManager.createNativeQuery(
                "SELECT count(DISTINCT " + valor + ") FROM " + TABLA
                        + " WHERE r.dataset_id = :datasetId AND " + where.sql()
                        + " AND " + valor + " IS NOT NULL AND btrim(" + valor + ") <> ''");
        aplicar(q, datasetId, where);
        parametroCampo(q, where, campoIndividuo);
        return ((Number) q.getSingleResult()).longValue();
    }

    /** IDs de una página de registros: ORDER BY + LIMIT/OFFSET en la base de datos. */
    @SuppressWarnings("unchecked")
    public List<Long> idsPaginaRegistros(
            Long datasetId, FiltroSqlBuilder.Where where, CampoClinico campoOrden, boolean descendente,
            CampoClinico campoIndividuo, String busqueda, int offset, int limite) {

        StringBuilder sql = new StringBuilder("SELECT r.id FROM ").append(TABLA)
                .append(" WHERE r.dataset_id = :datasetId AND ").append(where.sql());

        if (busqueda != null && campoIndividuo != null) {
            sql.append(" AND ").append(FiltroSqlBuilder.expresionTextoNormalizado(campoIndividuo))
                    .append(" LIKE '%' || CAST(:busqueda AS text) || '%'");
        }

        // Sin orden explícito: lo más reciente primero, con los nulos al final.
        String orden = campoOrden != null
                ? FiltroSqlBuilder.expresionValor(campoOrden) + (descendente ? " DESC NULLS LAST" : " ASC NULLS LAST")
                : "r.fecha_evento DESC NULLS LAST";
        // `r.id` desempata: sin él, dos filas con el mismo valor podrían
        // repetirse o perderse entre páginas.
        sql.append(" ORDER BY ").append(orden).append(", r.id ASC LIMIT :limite OFFSET :offset");

        Query q = entityManager.createNativeQuery(sql.toString());
        aplicar(q, datasetId, where);
        if (campoOrden != null) parametroCampo(q, where, campoOrden);
        if (campoIndividuo != null) {
            parametroCampo(q, where, campoIndividuo);
            if (busqueda != null) q.setParameter("busqueda", busqueda);
        }
        q.setParameter("limite", limite);
        q.setParameter("offset", offset);

        return ((List<Number>) q.getResultList()).stream().map(Number::longValue).toList();
    }

    /** Identificadores de una página de pacientes, ordenados y paginados en BD. */
    @SuppressWarnings("unchecked")
    public List<String> paginaIndividuos(
            Long datasetId, FiltroSqlBuilder.Where where, CampoClinico campoIndividuo,
            String busqueda, int offset, int limite) {

        String valor = FiltroSqlBuilder.expresionValor(campoIndividuo);
        StringBuilder sql = new StringBuilder("SELECT ").append(valor).append(" AS ident FROM ").append(TABLA)
                .append(" WHERE r.dataset_id = :datasetId AND ").append(where.sql())
                .append(" AND ").append(valor).append(" IS NOT NULL AND btrim(").append(valor).append(") <> ''");

        if (busqueda != null) {
            sql.append(" AND ").append(FiltroSqlBuilder.expresionTextoNormalizado(campoIndividuo))
                    .append(" LIKE '%' || CAST(:busqueda AS text) || '%'");
        }

        sql.append(" GROUP BY ident ORDER BY ident ASC LIMIT :limite OFFSET :offset");

        Query q = entityManager.createNativeQuery(sql.toString());
        aplicar(q, datasetId, where);
        parametroCampo(q, where, campoIndividuo);
        if (busqueda != null) q.setParameter("busqueda", busqueda);
        q.setParameter("limite", limite);
        q.setParameter("offset", offset);

        return ((List<Object>) q.getResultList()).stream().map(String::valueOf).toList();
    }

    /** Cuántos individuos distintos hay tras aplicar la búsqueda (total de la tabla Pacientes). */
    public long contarIndividuosConBusqueda(
            Long datasetId, FiltroSqlBuilder.Where where, CampoClinico campoIndividuo, String busqueda) {

        if (busqueda == null) {
            return contarIndividuosDistintos(datasetId, where, campoIndividuo);
        }

        String valor = FiltroSqlBuilder.expresionValor(campoIndividuo);
        Query q = entityManager.createNativeQuery(
                "SELECT count(DISTINCT " + valor + ") FROM " + TABLA
                        + " WHERE r.dataset_id = :datasetId AND " + where.sql()
                        + " AND " + valor + " IS NOT NULL AND btrim(" + valor + ") <> ''"
                        + " AND " + FiltroSqlBuilder.expresionTextoNormalizado(campoIndividuo)
                        + " LIKE '%' || CAST(:busqueda AS text) || '%'");
        aplicar(q, datasetId, where);
        parametroCampo(q, where, campoIndividuo);
        q.setParameter("busqueda", busqueda);
        return ((Number) q.getSingleResult()).longValue();
    }

    /** Cuántos registros quedan tras la búsqueda (total de la tabla Registros). */
    public long contarRegistrosConBusqueda(
            Long datasetId, FiltroSqlBuilder.Where where, CampoClinico campoIndividuo, String busqueda) {

        if (busqueda == null || campoIndividuo == null) {
            return contarRegistros(datasetId, where);
        }

        Query q = entityManager.createNativeQuery(
                "SELECT count(*) FROM " + TABLA + " WHERE r.dataset_id = :datasetId AND " + where.sql()
                        + " AND " + FiltroSqlBuilder.expresionTextoNormalizado(campoIndividuo)
                        + " LIKE '%' || CAST(:busqueda AS text) || '%'");
        aplicar(q, datasetId, where);
        parametroCampo(q, where, campoIndividuo);
        q.setParameter("busqueda", busqueda);
        return ((Number) q.getSingleResult()).longValue();
    }

    /** Rango de fechas del subconjunto, sin traerse las filas. */
    public Object[] rangoFechaEvento(Long datasetId, FiltroSqlBuilder.Where where) {
        Query q = entityManager.createNativeQuery(
                "SELECT min(r.fecha_evento), max(r.fecha_evento) FROM " + TABLA
                        + " WHERE r.dataset_id = :datasetId AND " + where.sql());
        aplicar(q, datasetId, where);
        return (Object[]) q.getSingleResult();
    }

    /** Estadísticos numéricos, mediana incluida (percentile_cont), calculados en BD. */
    public Object[] estadisticosNumericos(Long datasetId, FiltroSqlBuilder.Where where, CampoClinico campo) {
        String valor = FiltroSqlBuilder.expresionValor(campo);
        Query q = entityManager.createNativeQuery(
                "SELECT count(" + valor + "), avg(" + valor + "),"
                        + " percentile_cont(0.5) WITHIN GROUP (ORDER BY " + valor + "),"
                        + " min(" + valor + "), max(" + valor + "), count(*)"
                        + " FROM " + TABLA + " WHERE r.dataset_id = :datasetId AND " + where.sql());
        aplicar(q, datasetId, where);
        parametroCampo(q, where, campo);
        return (Object[]) q.getSingleResult();
    }

    /** Distribución de un campo: GROUP BY en BD, ya ordenada por frecuencia. */
    @SuppressWarnings("unchecked")
    public List<Object[]> distribucion(Long datasetId, FiltroSqlBuilder.Where where, CampoClinico campo) {
        String valor = FiltroSqlBuilder.expresionValor(campo);
        Query q = entityManager.createNativeQuery(
                "SELECT CAST(" + valor + " AS text) AS v, count(*) FROM " + TABLA
                        + " WHERE r.dataset_id = :datasetId AND " + where.sql()
                        + " GROUP BY v ORDER BY count(*) DESC, v ASC");
        aplicar(q, datasetId, where);
        parametroCampo(q, where, campo);
        return q.getResultList();
    }

    /** Primera y última fecha de un campo FECHA cualquiera. */
    public Object[] rangoFechas(Long datasetId, FiltroSqlBuilder.Where where, CampoClinico campo) {
        String valor = FiltroSqlBuilder.expresionValor(campo);
        Query q = entityManager.createNativeQuery(
                "SELECT count(" + valor + "), min(" + valor + "), max(" + valor + "), count(*) FROM " + TABLA
                        + " WHERE r.dataset_id = :datasetId AND " + where.sql());
        aplicar(q, datasetId, where);
        parametroCampo(q, where, campo);
        return (Object[]) q.getSingleResult();
    }

    // ------------------------------------------------------------------

    private void aplicar(Query q, Long datasetId, FiltroSqlBuilder.Where where) {
        q.setParameter("datasetId", datasetId);
        where.parametros().forEach(q::setParameter);
    }

    /**
     * Un campo dinámico usado fuera del WHERE (orden, agregados) necesita su
     * clave como parámetro; si el WHERE ya la registró, no se duplica.
     */
    private void parametroCampo(Query q, FiltroSqlBuilder.Where where, CampoClinico campo) {
        // Mismo criterio que FiltroSqlBuilder: un campo marcado `esComun` pero
        // sin columna física sigue leyéndose del JSONB y necesita su clave.
        if (FiltroSqlBuilder.esColumnaComun(campo)) return;
        String nombre = "campo_" + campo.getCodigo().toLowerCase().replaceAll("[^a-z0-9]", "_");
        if (!where.parametros().containsKey(nombre)) {
            q.setParameter(nombre, campo.getCodigo());
        }
    }

    /** Carga solo las filas de la página, por sus IDs. */
    public Map<Long, com.preventiva.backend.entity.RegistroClinicoGenerico> cargarPorIds(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return entityManager
                .createQuery(
                        "SELECT r FROM RegistroClinicoGenerico r WHERE r.id IN :ids",
                        com.preventiva.backend.entity.RegistroClinicoGenerico.class)
                .setParameter("ids", ids)
                .getResultList()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.preventiva.backend.entity.RegistroClinicoGenerico::getId, r -> r));
    }

    /** Registros de un conjunto concreto de individuos (para agregar la vista Pacientes). */
    @SuppressWarnings("unchecked")
    public List<com.preventiva.backend.entity.RegistroClinicoGenerico> registrosDeIndividuos(
            Long datasetId, FiltroSqlBuilder.Where where, CampoClinico campoIndividuo, List<String> identificadores) {

        if (identificadores.isEmpty()) return List.of();

        String valor = FiltroSqlBuilder.expresionValor(campoIndividuo);
        // Un parámetro por identificador: el driver serializaría un String[]
        // como bytea y el cast a text[] fallaría.
        List<String> marcadores = new java.util.ArrayList<>();
        for (int i = 0; i < identificadores.size(); i++) marcadores.add("CAST(:ident" + i + " AS text)");

        Query q = entityManager.createNativeQuery(
                "SELECT r.id FROM " + TABLA + " WHERE r.dataset_id = :datasetId AND " + where.sql()
                        + " AND " + valor + " IN (" + String.join(", ", marcadores) + ")");
        aplicar(q, datasetId, where);
        parametroCampo(q, where, campoIndividuo);
        for (int i = 0; i < identificadores.size(); i++) q.setParameter("ident" + i, identificadores.get(i));

        List<Long> ids = ((List<Number>) q.getResultList()).stream().map(Number::longValue).toList();
        return List.copyOf(cargarPorIds(ids).values());
    }
}
