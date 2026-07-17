package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.ErrorImportacionExcelResponseDto;
import com.preventiva.backend.dto.ImportacionExcelResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ExcelImportService {

    ImportacionExcelResponseDto importarExcel(
            MultipartFile archivo,
            Long plantillaId,
            Integer indiceHoja,
            Integer filaCabecera);

    List<ErrorImportacionExcelResponseDto> listarErrores(Long importacionId);
}