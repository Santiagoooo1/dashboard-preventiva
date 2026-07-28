package com.preventiva.backend.controller;

import com.preventiva.backend.dto.ActualizarExclusionFilaTrabajoRequestDto;
import com.preventiva.backend.dto.CorregirCeldaTrabajoRequestDto;
import com.preventiva.backend.dto.CrearImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.DeshacerCorreccionCeldaTrabajoRequestDto;
import com.preventiva.backend.dto.ErrorImportacionTrabajoDto;
import com.preventiva.backend.dto.ExcluirSimilaresRequestDto;
import com.preventiva.backend.dto.ImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.ImportarDesdeTrabajoResponseDto;
import com.preventiva.backend.dto.NormalizarColumnaTrabajoRequestDto;
import com.preventiva.backend.dto.PaginaFilasImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.RellenarColumnaTrabajoRequestDto;
import com.preventiva.backend.dto.RevalidarImportacionTrabajoResponseDto;
import com.preventiva.backend.service.interfaces.ImportacionTrabajoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/importaciones-trabajo")
@RequiredArgsConstructor
public class ImportacionTrabajoController {

    private final ImportacionTrabajoService importacionTrabajoService;

    @PostMapping
    public CrearImportacionTrabajoResponseDto crear(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam("plantillaId") Long plantillaId,
            @RequestParam(value = "indiceHoja", defaultValue = "0") Integer indiceHoja,
            @RequestParam(value = "filaCabecera", required = false) Integer filaCabecera) {
        return importacionTrabajoService.crear(archivo, plantillaId, indiceHoja, filaCabecera);
    }

    @GetMapping("/{id}")
    public ImportacionTrabajoResponseDto obtenerPorId(@PathVariable("id") Long id) {
        return importacionTrabajoService.obtenerPorId(id);
    }

    @GetMapping("/{id}/filas")
    public PaginaFilasImportacionTrabajoResponseDto listarFilas(
            @PathVariable("id") Long id,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "soloConErrores", defaultValue = "false") boolean soloConErrores) {
        return importacionTrabajoService.listarFilas(id, page, size, soloConErrores);
    }

    @GetMapping("/{id}/errores")
    public List<ErrorImportacionTrabajoDto> listarErrores(@PathVariable("id") Long id) {
        return importacionTrabajoService.listarErrores(id);
    }

    @PostMapping("/{id}/revalidar")
    public RevalidarImportacionTrabajoResponseDto revalidar(@PathVariable("id") Long id) {
        return importacionTrabajoService.revalidar(id);
    }

    @DeleteMapping("/{id}")
    public void descartar(@PathVariable("id") Long id) {
        importacionTrabajoService.descartar(id);
    }

    @PutMapping("/{id}/filas/{numeroFila}/exclusion")
    public RevalidarImportacionTrabajoResponseDto actualizarExclusion(
            @PathVariable("id") Long id,
            @PathVariable("numeroFila") Integer numeroFila,
            @RequestBody ActualizarExclusionFilaTrabajoRequestDto request) {
        return importacionTrabajoService.actualizarExclusion(id, numeroFila, request.getExcluida());
    }

    @PostMapping("/{id}/excluir-similares")
    public RevalidarImportacionTrabajoResponseDto excluirSimilares(
            @PathVariable("id") Long id,
            @RequestBody ExcluirSimilaresRequestDto request) {
        return importacionTrabajoService.excluirSimilares(id, request.getTipoError(), request.getNombreColumna());
    }

    /**
     * Endpoint recomendado: la columna viaja en el body para admitir nombres
     * con "/", espacios o acentos (p.ej. "Diagnóstico/CIE-10"), que no son
     * seguros como variable de ruta.
     */
    @PutMapping("/{id}/filas/{numeroFila}/celda")
    public RevalidarImportacionTrabajoResponseDto corregirCelda(
            @PathVariable("id") Long id,
            @PathVariable("numeroFila") Integer numeroFila,
            @RequestBody CorregirCeldaTrabajoRequestDto request) {
        return importacionTrabajoService.corregirCelda(id, numeroFila, request.getColumna(), request.getValor());
    }

    /**
     * Endpoint recomendado (POST en vez de DELETE para evitar problemas de
     * body en DELETE). La columna viaja en el body por el mismo motivo que
     * en {@link #corregirCelda}.
     */
    @PostMapping("/{id}/filas/{numeroFila}/celda/deshacer")
    public RevalidarImportacionTrabajoResponseDto deshacerCorreccionCelda(
            @PathVariable("id") Long id,
            @PathVariable("numeroFila") Integer numeroFila,
            @RequestBody DeshacerCorreccionCeldaTrabajoRequestDto request) {
        return importacionTrabajoService.deshacerCorreccionCelda(id, numeroFila, request.getColumna());
    }

    /**
     * @deprecated legacy: la columna en la ruta es frágil si contiene "/".
     * Usar {@link #corregirCelda} (columna en el body).
     */
    @Deprecated
    @PutMapping("/{id}/filas/{numeroFila}/celdas/{columna}")
    public RevalidarImportacionTrabajoResponseDto corregirCeldaPorRuta(
            @PathVariable("id") Long id,
            @PathVariable("numeroFila") Integer numeroFila,
            @PathVariable("columna") String columna,
            @RequestBody CorregirCeldaTrabajoRequestDto request) {
        return importacionTrabajoService.corregirCelda(id, numeroFila, columna, request.getValor());
    }

    /**
     * @deprecated legacy: la columna en la ruta es frágil si contiene "/".
     * Usar {@link #deshacerCorreccionCelda} (columna en el body).
     */
    @Deprecated
    @DeleteMapping("/{id}/filas/{numeroFila}/celdas/{columna}")
    public RevalidarImportacionTrabajoResponseDto deshacerCorreccionCeldaPorRuta(
            @PathVariable("id") Long id,
            @PathVariable("numeroFila") Integer numeroFila,
            @PathVariable("columna") String columna) {
        return importacionTrabajoService.deshacerCorreccionCelda(id, numeroFila, columna);
    }

    // ---- Deshacer en bloque (Fase 6.8C.4) ----

    @PostMapping("/{id}/filas/{numeroFila}/correcciones/deshacer-todas")
    public RevalidarImportacionTrabajoResponseDto deshacerTodasLasCorreccionesDeFila(
            @PathVariable("id") Long id,
            @PathVariable("numeroFila") Integer numeroFila) {
        return importacionTrabajoService.deshacerTodasLasCorreccionesDeFila(id, numeroFila);
    }

    @PostMapping("/{id}/correcciones/deshacer-todas")
    public RevalidarImportacionTrabajoResponseDto deshacerTodasLasCorrecciones(@PathVariable("id") Long id) {
        return importacionTrabajoService.deshacerTodasLasCorrecciones(id);
    }

    @PostMapping("/{id}/exclusiones/deshacer-todas")
    public RevalidarImportacionTrabajoResponseDto deshacerTodasLasExclusiones(@PathVariable("id") Long id) {
        return importacionTrabajoService.deshacerTodasLasExclusiones(id);
    }

    @PostMapping("/{id}/restaurar-original")
    public RevalidarImportacionTrabajoResponseDto restaurarOriginal(@PathVariable("id") Long id) {
        return importacionTrabajoService.restaurarOriginal(id);
    }

    // ---- Corrección asistida (Fase 6.8C.4) ----

    @PostMapping("/{id}/correcciones/rellenar-columna")
    public RevalidarImportacionTrabajoResponseDto rellenarColumna(
            @PathVariable("id") Long id,
            @RequestBody RellenarColumnaTrabajoRequestDto request) {
        boolean soloConEsteProblema = request.getSoloFilasConEsteProblema() == null
                || request.getSoloFilasConEsteProblema();
        return importacionTrabajoService.rellenarColumna(
                id, request.getNombreColumna(), request.getTipoError(), request.getValor(), soloConEsteProblema);
    }

    @PostMapping("/{id}/correcciones/normalizar-columna")
    public RevalidarImportacionTrabajoResponseDto normalizarColumna(
            @PathVariable("id") Long id,
            @RequestBody NormalizarColumnaTrabajoRequestDto request) {
        return importacionTrabajoService.normalizarColumna(id, request.getNombreColumna(), request.getEstrategia());
    }

    @PostMapping("/{id}/importar")
    public ImportarDesdeTrabajoResponseDto importarDesdeTrabajo(@PathVariable("id") Long id) {
        return importacionTrabajoService.importarDesdeTrabajo(id);
    }
}
