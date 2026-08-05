package com.preventiva.backend.config;

import com.preventiva.backend.enums.TipoMetrica;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pone al día las restricciones CHECK generadas a partir de enums.
 *
 * <p>Hibernate crea un {@code CHECK (columna IN ('A','B'))} para cada columna
 * {@code @Enumerated(EnumType.STRING)}, pero solo al CREAR la tabla:
 * {@code ddl-auto=update} añade columnas e índices y nunca toca una restricción
 * existente. Al ampliar un enum, la tabla sigue con la lista antigua y cualquier
 * inserción con un valor nuevo falla con
 * {@code violates check constraint ..._check}.
 *
 * <p>El proyecto no tiene Flyway ni Liquibase (ver CLAUDE.md), así que esta
 * alineación ocupa su lugar para el único caso que se da: ampliar un enum ya
 * persistido. Es idempotente y conservadora — solo reescribe la restricción
 * cuando la lista de la base y la del enum difieren, y nunca borra datos.
 *
 * <p>Se registró al ampliar {@code TipoMetrica} en la Fase 6.9I.2. Si en el
 * futuro se amplía otro enum persistido, basta con añadirlo a la lista.
 */
@Component
@Slf4j
public class AlineadorRestriccionesEnum {

    /** Qué restricciones se vigilan: tabla, columna y enum del que salen los valores. */
    private static final List<RestriccionEnum> RESTRICCIONES = List.of(
            new RestriccionEnum("metricas_clinicas", "tipo_metrica", TipoMetrica.class));

    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    // La transacción se abre a mano: @Transactional no tiene efecto sobre un
    // @PostConstruct (el proxy todavía no existe cuando se invoca), y sin
    // transacción el executeUpdate falla en silencio.
    public AlineadorRestriccionesEnum(EntityManager entityManager, PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @PostConstruct
    public void alinear() {
        for (RestriccionEnum restriccion : RESTRICCIONES) {
            try {
                transactionTemplate.executeWithoutResult(estado -> alinearUna(restriccion));
            } catch (Exception e) {
                // Que no impida arrancar: si la restricción no puede reescribirse,
                // la aplicación sigue funcionando con los valores antiguos y el
                // fallo queda registrado con nombre y apellidos.
                log.warn("No se pudo alinear la restricción de {}.{}: {}",
                        restriccion.tabla(), restriccion.columna(), e.getMessage());
            }
        }
    }

    private void alinearUna(RestriccionEnum restriccion) {
        String nombre = restriccion.tabla() + "_" + restriccion.columna() + "_check";

        List<?> definiciones = entityManager
                .createNativeQuery("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = :nombre")
                .setParameter("nombre", nombre)
                .getResultList();

        String definicionActual = definiciones.isEmpty() ? null : String.valueOf(definiciones.get(0));

        List<String> valores = Arrays.stream(restriccion.tipoEnum().getEnumConstants())
                .map(v -> ((Enum<?>) v).name())
                .toList();

        // Si ya están todos, no se toca nada: el arranque normal no hace DDL.
        if (definicionActual != null && valores.stream().allMatch(v -> definicionActual.contains("'" + v + "'"))) {
            return;
        }

        String listaSql = valores.stream()
                .map(v -> "'" + v + "'")
                .collect(Collectors.joining(", "));

        if (definicionActual != null) {
            entityManager.createNativeQuery(
                    "ALTER TABLE " + restriccion.tabla() + " DROP CONSTRAINT " + nombre).executeUpdate();
        }

        entityManager.createNativeQuery(
                "ALTER TABLE " + restriccion.tabla() + " ADD CONSTRAINT " + nombre
                        + " CHECK (" + restriccion.columna() + " IN (" + listaSql + "))").executeUpdate();

        log.info("Restricción {} actualizada con {} valores del enum {}.",
                nombre, valores.size(), restriccion.tipoEnum().getSimpleName());
    }

    /** Los tres datos van fijos en el código: nada de aquí procede de una petición. */
    private record RestriccionEnum(String tabla, String columna, Class<?> tipoEnum) {
    }
}
