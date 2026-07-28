package com.preventiva.backend.repository;

import com.preventiva.backend.entity.EventoImportacionTrabajo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventoImportacionTrabajoRepository extends JpaRepository<EventoImportacionTrabajo, Long> {

    List<EventoImportacionTrabajo> findByImportacionTrabajoIdOrderByFechaEventoAsc(Long importacionTrabajoId);

    List<EventoImportacionTrabajo> findByImportacionTrabajoIdOrderByFechaEventoDesc(Long importacionTrabajoId);

    void deleteByImportacionTrabajoIdIn(List<Long> importacionTrabajoIds);
}
