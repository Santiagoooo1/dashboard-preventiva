package com.preventiva.backend.repository;

import com.preventiva.backend.entity.DatasetClinico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DatasetClinicoRepository extends JpaRepository<DatasetClinico, Long> {

    Optional<DatasetClinico> findByCodigo(String codigo);

    List<DatasetClinico> findByActivoTrue();
}
