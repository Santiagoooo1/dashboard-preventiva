package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.ColumnasReanudacionResponseDto;
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

    // ---- Deshacer en bloque (Fase 6.8C.4) ----

    RevalidarImportacionTrabajoResponseDto deshacerTodasLasCorreccionesDeFila(Long id, Integer numeroFila);

    RevalidarImportacionTrabajoResponseDto deshacerTodasLasCorrecciones(Long id);

    RevalidarImportacionTrabajoResponseDto deshacerTodasLasExclusiones(Long id);

    /** Vacía correcciones y exclusiones a la vez; el archivo original y valoresOriginales no se tocan. */
    RevalidarImportacionTrabajoResponseDto restaurarOriginal(Long id);

    // ---- Corrección asistida (Fase 6.8C.4) ----

    RevalidarImportacionTrabajoResponseDto rellenarColumna(
            Long id, String nombreColumna, String tipoError, String valor, boolean soloFilasConEsteProblema);

    RevalidarImportacionTrabajoResponseDto normalizarColumna(Long id, String nombreColumna, String estrategia);

    ImportarDesdeTrabajoResponseDto importarDesdeTrabajo(Long id);

    /**
     * Reconstruye la revisión de columnas de una copia de trabajo a partir de
     * columnasPresentes/erroresGlobales (guardados al crearla) y los mapeos
     * activos de su plantilla, para reanudar un borrador sin el archivo
     * original (Fase 6.8E.2.1). Si no hay suficiente información, devuelve
     * una lista de columnas vacía y un mensaje explicando por qué.
     */
    ColumnasReanudacionResponseDto obtenerColumnasReanudacion(Long id);
}
