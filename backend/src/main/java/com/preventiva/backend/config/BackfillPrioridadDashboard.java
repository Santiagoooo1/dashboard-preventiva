package com.preventiva.backend.config;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Rellena {@code prioridad_dashboard} en las columnas que ya existían
 * (Fase 6.9J.1).
 *
 * <p>{@code ddl-auto=update} añade la columna nueva vacía. Sin este relleno,
 * todos los campos de los datasets ya importados quedarían sin prioridad y el
 * dashboard recomendado los trataría por igual.
 *
 * <p><b>La regla de relleno NO es la semántica permanente.</b> Se usa una sola
 * vez, para dar un punto de partida razonable a datos que nadie ha clasificado
 * todavía:
 *
 * <ul>
 *   <li>{@code esComun = true} → FUNDAMENTAL</li>
 *   <li>{@code obligatorio = true} y no común → IMPORTANTE</li>
 *   <li>el resto → NORMAL</li>
 * </ul>
 *
 * <p>EXCLUIR no se deduce nunca: apartar una columna del análisis es una
 * decisión clínica y adivinarla escondería datos sin que nadie lo pidiera.
 *
 * <p>A partir de aquí los tres atributos son independientes: cambiar la
 * prioridad no toca {@code esComun} ni {@code obligatorio}, y al revés tampoco.
 * Solo actúa sobre filas con la columna a NULL, así que no pisa nada de lo que
 * el usuario haya decidido y ejecutarlo de nuevo no cambia nada.
 */
@Component
@Slf4j
public class BackfillPrioridadDashboard {

    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    // La transacción se abre a mano: @Transactional no tiene efecto sobre un
    // @PostConstruct (el proxy todavía no existe), y sin transacción el
    // executeUpdate fallaría en silencio.
    public BackfillPrioridadDashboard(EntityManager entityManager, PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @PostConstruct
    public void rellenar() {
        try {
            transactionTemplate.executeWithoutResult(estado -> {
                int actualizadas = entityManager.createNativeQuery("""
                        UPDATE campos_clinicos
                        SET prioridad_dashboard = CASE
                            WHEN es_comun THEN 'FUNDAMENTAL'
                            WHEN obligatorio THEN 'IMPORTANTE'
                            ELSE 'NORMAL'
                        END
                        WHERE prioridad_dashboard IS NULL
                        """).executeUpdate();

                if (actualizadas > 0) {
                    log.info("Prioridad de dashboard inicializada en {} columnas sin clasificar.", actualizadas);
                }
            });
        } catch (Exception e) {
            // Que no impida arrancar: sin relleno, el getter de la entidad
            // devuelve NORMAL y la aplicación sigue funcionando.
            log.warn("No se pudo inicializar la prioridad de dashboard: {}", e.getMessage());
        }
    }
}
