package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.ValidacionFilasImportacionGenericaResponseDto;
import com.preventiva.backend.dto.ValidacionImportacionGenericaResponseDto;
import org.springframework.web.multipart.MultipartFile;

public interface ImportacionGenericaValidationService {

    ValidacionImportacionGenericaResponseDto validarCabeceras(
            MultipartFile archivo,
            Long plantillaId,
            Integer indiceHoja,
            Integer filaCabecera);

    ValidacionFilasImportacionGenericaResponseDto validarFilas(
            MultipartFile archivo,
            Long plantillaId,
            Integer indiceHoja,
            Integer filaCabecera);
}
