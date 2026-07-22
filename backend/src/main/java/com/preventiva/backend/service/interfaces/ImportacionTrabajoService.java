package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.CrearImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.ErrorImportacionTrabajoDto;
import com.preventiva.backend.dto.ImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.PaginaFilasImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.RevalidarImportacionTrabajoResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ImportacionTrabajoService {

    CrearImportacionTrabajoResponseDto crear(
            MultipartFile archivo, Long plantillaId, Integer indiceHoja, Integer filaCabecera);

    ImportacionTrabajoResponseDto obtenerPorId(Long id);

    PaginaFilasImportacionTrabajoResponseDto listarFilas(
            Long id, int page, int size, boolean soloConErrores);

    List<ErrorImportacionTrabajoDto> listarErrores(Long id);

    RevalidarImportacionTrabajoResponseDto revalidar(Long id);

    void descartar(Long id);
}
