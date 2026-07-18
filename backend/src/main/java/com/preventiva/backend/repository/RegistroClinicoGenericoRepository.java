package com.preventiva.backend.repository;

import com.preventiva.backend.entity.RegistroClinicoGenerico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistroClinicoGenericoRepository extends JpaRepository<RegistroClinicoGenerico, Long> {

    List<RegistroClinicoGenerico> findByDatasetId(Long datasetId);
}
