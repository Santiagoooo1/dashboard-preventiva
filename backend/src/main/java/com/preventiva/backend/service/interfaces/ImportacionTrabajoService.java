package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.ColumnasReanudacionResponseDto;
import com.preventiva.backend.dto.CrearImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.ErrorImportacionTrabajoDto;
import com.preventiva.backend.dto.ImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.ImportarDesdeTrabajoResponseDto;
import com.preventiva.backend.dto.PaginaFilasImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.RevalidarImportacionTrabajoResponseDto;
import com.preventiva.backend.entity.ImportacionTrabajo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface ImportacionTrabajoService {

    CrearImportacionTrabajoResponseDto crear(
            MultipartFile archivo, Long plantillaId, Integer indiceHoja, Integer filaCabecera);

    ImportacionTrabajoResponseDto obtenerPorId(Long id);

    PaginaFilasImportacionTrabajoResponseDto listarFilas(
            Long id, int page, int size, boolean soloConErrores);

    List<ErrorImportacionTrabajoDto> listarErrores(Long id);

    RevalidarImportacionTrabajoResponseDto revalidar(Long id);

    /**
     * Descarta la copia de trabajo por petición explícita del usuario.
     *
     * <p>Deja constancia de que el descarte fue querido, para que la
     * reanudación no la resucite después: ver
     * {@link #buscarTrabajoHistoricoRecuperable(Long)} y
     * {@link #recuperarTrabajoAnterior(Long)}.
     */
    void descartar(Long id);

    /**
     * Busca, SIN modificar nada, una copia descartada que el usuario podría
     * querer recuperar.
     *
     * <p>Sirve para avisar al reanudar un borrador. Una copia con
     * {@code descarteExplicito == null} es ambigua —puede venir del defecto que
     * descartaba al salir de la pantalla de corrección, o de un descarte
     * anterior a que existiera la marca—, así que se ofrece, no se reactiva.
     *
     * <p>Exige que el dataset siga en borrador, que la copia conserve el
     * archivo y sus filas, que no haya ninguna copia viva, y que el descarte no
     * fuera explícito.
     *
     * @return la candidata, o vacío si no hay ninguna que cumpla las condiciones
     */
    Optional<ImportacionTrabajo> buscarTrabajoHistoricoRecuperable(Long datasetId);

    /**
     * Recupera la copia anterior porque el usuario lo ha pedido: la revalida y
     * la devuelve a EN_EDICION o LISTA_PARA_IMPORTAR según los errores que
     * tenga ahora, dejando constancia con un evento COPIA_RECUPERADA.
     *
     * <p>Es la única operación que resucita una copia descartada. Si ya se
     * recuperó antes, devuelve la copia viva sin volver a registrar nada.
     *
     * @throws IllegalArgumentException si no hay nada recuperable ni vigente
     */
    ImportacionTrabajoResponseDto recuperarTrabajoAnterior(Long datasetId);

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
