package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.ErrorImportacionGenericaResponseDto;
import com.preventiva.backend.dto.ImportacionGenericaResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ImportacionGenericaService {

    ImportacionGenericaResponseDto importar(
            MultipartFile archivo,
            Long plantillaId,
            Integer indiceHoja,
            Integer filaCabecera);

    ImportacionGenericaResponseDto obtenerPorId(Long id);

    List<ErrorImportacionGenericaResponseDto> listarErrores(Long importacionId);
}
