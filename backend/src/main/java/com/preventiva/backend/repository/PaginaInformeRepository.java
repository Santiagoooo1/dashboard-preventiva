package com.preventiva.backend.repository;

import com.preventiva.backend.entity.PaginaInforme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaginaInformeRepository extends JpaRepository<PaginaInforme, Long> {

    List<PaginaInforme> findByInformeIdOrderByOrdenAscIdAsc(Long informeId);
}
