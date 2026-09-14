package com.preventiva.backend.repository;

import com.preventiva.backend.entity.InformeClinico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InformeClinicoRepository extends JpaRepository<InformeClinico, Long> {

    List<InformeClinico> findByActivoTrueOrderByActualizadoEnDesc();
}
