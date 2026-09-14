package com.preventiva.backend.repository;

import com.preventiva.backend.entity.BloqueInforme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BloqueInformeRepository extends JpaRepository<BloqueInforme, Long> {

    List<BloqueInforme> findByPaginaIdOrderByOrdenAscIdAsc(Long paginaId);

    /** Bloques que apuntan a una métrica; sirve para avisar antes de borrarla. */
    List<BloqueInforme> findByMetricaId(Long metricaId);
}
