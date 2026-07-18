package com.preventiva.backend.repository;

import com.preventiva.backend.entity.ErrorImportacionGenerica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ErrorImportacionGenericaRepository extends JpaRepository<ErrorImportacionGenerica, Long> {

    List<ErrorImportacionGenerica> findByImportacionGenericaId(Long importacionGenericaId);
}
