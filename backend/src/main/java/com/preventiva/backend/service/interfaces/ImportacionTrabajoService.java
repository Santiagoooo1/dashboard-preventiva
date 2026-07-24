package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.CrearImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.ErrorImportacionTrabajoDto;
import com.preventiva.backend.dto.ImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.ImportarDesdeTrabajoResponseDto;
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

    RevalidarImportacionTrabajoResponseDto actualizarExclusion(Long id, Integer numeroFila, Boolean excluida);

    RevalidarImportacionTrabajoResponseDto excluirSimilares(Long id, String tipoError, String nombreColumna);

    RevalidarImportacionTrabajoResponseDto corregirCelda(Long id, Integer numeroFila, String columna, String valor);

    RevalidarImportacionTrabajoResponseDto deshacerCorreccionCelda(Long id, Integer numeroFila, String columna);

    ImportarDesdeTrabajoResponseDto importarDesdeTrabajo(Long id);
}
