package com.preventiva.backend.repository;

import com.preventiva.backend.entity.ImportacionExcel;
import com.preventiva.backend.enums.EstadoImportacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportacionExcelRepository extends JpaRepository<ImportacionExcel, Long> {

    List<ImportacionExcel> findByEstado(EstadoImportacion estado);

    List<ImportacionExcel> findByNombreOriginalContainingIgnoreCase(String nombreOriginal);
}