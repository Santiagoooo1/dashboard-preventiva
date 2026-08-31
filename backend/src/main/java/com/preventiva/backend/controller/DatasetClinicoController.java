package com.preventiva.backend.controller;

import com.preventiva.backend.dto.BaseEvaluableDashboardDto;
import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.CampoClinicoResponseDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.DatasetClinicoResponseDto;
import com.preventiva.backend.dto.ImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.PropuestaWidgetDto;
import com.preventiva.backend.dto.ReanudarBorradorDatasetDto;
import com.preventiva.backend.service.interfaces.BaseEvaluableDashboardService;
import com.preventiva.backend.service.interfaces.BloqueInicialIlqService;
import com.preventiva.backend.service.interfaces.CampoClinicoService;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
import com.preventiva.backend.service.interfaces.ImportacionTrabajoService;
import com.preventiva.backend.service.interfaces.RegistroClinicoGenericoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/datasets-clinicos")
@RequiredArgsConstructor
public class DatasetClinicoController {

    private final DatasetClinicoService datasetClinicoService;
    private final CampoClinicoService campoClinicoService;
    private final RegistroClinicoGenericoService registroClinicoGenericoService;
    private final ImportacionTrabajoService importacionTrabajoService;
    private final BloqueInicialIlqService bloqueInicialIlqService;
    private final BaseEvaluableDashboardService baseEvaluableDashboardService;

    @GetMapping
    public List<DatasetClinicoResponseDto> listar(
            @RequestParam(value = "incluirBorradores", defaultValue = "false") boolean incluirBorradores) {
        return datasetClinicoService.listar(incluirBorradores);
    }

    @GetMapping("/{id}")
    public DatasetClinicoResponseDto obtenerPorId(@PathVariable("id") Long id) {
        return datasetClinicoService.obtenerPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetClinicoResponseDto crear(@Valid @RequestBody DatasetClinicoRequestDto request) {
        return datasetClinicoService.crear(request);
    }

    @PutMapping("/{id}")
    public DatasetClinicoResponseDto actualizar(
            @PathVariable("id") Long id,
            @Valid @RequestBody DatasetClinicoRequestDto request) {
        return datasetClinicoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable("id") Long id) {
        datasetClinicoService.desactivar(id);
    }

    @PostMapping("/{id}/activar")
    public DatasetClinicoResponseDto activar(@PathVariable("id") Long id) {
        return datasetClinicoService.activar(id);
    }

    @DeleteMapping("/{id}/descartar-borrador")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descartarBorrador(@PathVariable("id") Long id) {
        datasetClinicoService.descartarBorrador(id);
    }

    /**
     * Retoma un borrador: devuelve en qué paso continuar y con qué copia de
     * trabajo.
     *
     * <p>Es POST y no GET porque puede escribir: si la única copia quedó
     * DESCARTADA por el defecto que arreglaba la Fase 6.9K, la rescata. Un GET
     * que muta se dispararía con un refresco, un prefetch o con solo abrir la
     * pantalla; "Continuar creación" es una acción del usuario y se modela como
     * tal.
     *
     * <p>Idempotente: repetirlo devuelve el mismo dataset y la misma copia, y no
     * vuelve a registrar la recuperación (a la segunda llamada la copia ya no
     * está descartada, así que no hay nada que rescatar).
     */
    @PostMapping("/{id}/reanudar-borrador")
    public ReanudarBorradorDatasetDto reanudarBorrador(@PathVariable("id") Long id) {
        return datasetClinicoService.reanudarBorrador(id);
    }

    /**
     * Recupera la revisión anterior de un borrador, a petición del usuario.
     *
     * <p>Reanudar solo la anuncia; resucitarla es esta acción y ninguna otra.
     * Una copia descartada sin marca de intención es ambigua: pudo tirarla el
     * defecto del asistente o el propio usuario antes de que la marca
     * existiera, y esa duda la resuelve él pulsando aquí.
     */
    @PostMapping("/{id}/recuperar-trabajo-anterior")
    public ImportacionTrabajoResponseDto recuperarTrabajoAnterior(@PathVariable("id") Long id) {
        return importacionTrabajoService.recuperarTrabajoAnterior(id);
    }

    /**
     * Indicadores de infección quirúrgica que deben abrir el dashboard inicial
     * de este dataset, o lista vacía si el dataset no trata de eso.
     *
     * <p>GET porque solo consulta: no crea métricas ni paneles. Quien decide
     * crearlos es el generador del dashboard inicial.
     */
    @GetMapping("/{id}/dashboard-inicial/bloque-ilq")
    public List<PropuestaWidgetDto> bloqueInicialIlq(@PathVariable("id") Long id) {
        return bloqueInicialIlqService.proponerBloqueInicial(id);
    }

    /**
     * Sobre qué población deben calcularse los indicadores de actividad del
     * dashboard inicial. GET porque solo consulta.
     */
    @GetMapping("/{id}/dashboard-inicial/base-evaluable")
    public BaseEvaluableDashboardDto baseEvaluableDashboard(@PathVariable("id") Long id) {
        return baseEvaluableDashboardService.resolver(id);
    }

    @GetMapping("/{id}/campos")
    public List<CampoClinicoResponseDto> listarCampos(@PathVariable("id") Long id) {
        return campoClinicoService.listarPorDataset(id);
    }

    @PostMapping("/{id}/campos")
    @ResponseStatus(HttpStatus.CREATED)
    public CampoClinicoResponseDto crearCampo(
            @PathVariable("id") Long id,
            @Valid @RequestBody CampoClinicoRequestDto request) {
        return campoClinicoService.crear(id, request);
    }

    /**
     * Deja listos los campos de una importación: reutiliza los existentes y crea
     * los que falten. Es lo que usa el asistente, para que reanudar un borrador
     * no choque con los campos que creó el intento anterior.
     */
    @PostMapping("/{id}/campos/asegurar")
    public List<CampoClinicoResponseDto> asegurarCampos(
            @PathVariable("id") Long datasetId,
            @Valid @RequestBody List<CampoClinicoRequestDto> campos) {
        return campoClinicoService.asegurarParaImportacion(datasetId, campos);
    }

    @PutMapping("/{id}/campos/{campoId}")
    public CampoClinicoResponseDto actualizarCampo(
            @PathVariable("id") Long id,
            @PathVariable("campoId") Long campoId,
            @Valid @RequestBody CampoClinicoRequestDto request) {
        return campoClinicoService.actualizar(id, campoId, request);
    }

    @DeleteMapping("/{id}/campos/{campoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivarCampo(
            @PathVariable("id") Long id,
            @PathVariable("campoId") Long campoId) {
        campoClinicoService.desactivar(id, campoId);
    }

    @GetMapping("/{id}/campos/{codigo}/valores")
    public List<String> listarValoresUnicosDeCampo(
            @PathVariable("id") Long id,
            @PathVariable("codigo") String codigo) {
        return registroClinicoGenericoService.listarValoresUnicos(id, codigo);
    }
}
