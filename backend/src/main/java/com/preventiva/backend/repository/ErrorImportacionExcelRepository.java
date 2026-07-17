package com.preventiva.backend.repository;

import com.preventiva.backend.entity.ErrorImportacionExcel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ErrorImportacionExcelRepository extends JpaRepository<ErrorImportacionExcel, Long> {

    List<ErrorImportacionExcel> findByImportacionId(Long importacionId);
}