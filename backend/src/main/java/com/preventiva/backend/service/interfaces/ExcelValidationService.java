package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.ValidacionExcelResponseDto;
import com.preventiva.backend.dto.ValidacionFilasExcelResponseDto;
import org.springframework.web.multipart.MultipartFile;

public interface ExcelValidationService {

    ValidacionExcelResponseDto validarExcel(
            MultipartFile archivo,
            Long plantillaId,
            Integer indiceHoja);

    ValidacionFilasExcelResponseDto validarFilasExcel(
            MultipartFile archivo,
            Long plantillaId,
            Integer indiceHoja,
            Integer filaCabecera);
}