package com.preventiva.backend;

import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.MapeoCampoImportacionRequestDto;
import com.preventiva.backend.dto.PlantillaImportacionRequestDto;
import com.preventiva.backend.enums.EstadoDatasetClinico;
import com.preventiva.backend.enums.OrigenImportacion;
import com.preventiva.backend.enums.PoliticaCampoFaltante;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.repository.CampoClinicoRepository;
import com.preventiva.backend.repository.DatasetClinicoRepository;
import com.preventiva.backend.repository.EventoImportacionTrabajoRepository;
import com.preventiva.backend.repository.FilaImportacionTrabajoRepository;
import com.preventiva.backend.repository.ImportacionGenericaRepository;
import com.preventiva.backend.repository.ImportacionTrabajoRepository;
import com.preventiva.backend.repository.MapeoCampoImportacionRepository;
import com.preventiva.backend.repository.PlantillaImportacionRepository;
import com.preventiva.backend.repository.RegistroClinicoGenericoRepository;
import com.preventiva.backend.service.interfaces.CampoClinicoService;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
import com.preventiva.backend.service.interfaces.ImportacionTrabajoService;
import com.preventiva.backend.service.interfaces.MapeoCampoImportacionService;
import com.preventiva.backend.service.interfaces.PlantillaImportacionService;
import com.preventiva.backend.service.interfaces.TrazabilidadImportacionTrabajoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base común para los tests de integración de la Fase 6.8F.1. Cada método de
 * test hereda {@code @Transactional}: todo lo que crea se revierte solo al
 * terminar el test (no hace falta borrar nada a mano ni dejar residuos
 * "TEST_68F_*" en la base de datos de desarrollo).
 *
 * <p>Se usan los servicios reales (no MockMvc) porque son más estables ante
 * cambios de rutas/DTOs y porque estos flujos ya están cubiertos manualmente
 * vía curl en fases anteriores; lo que falta es protegerlos de regresiones.
 */
@SpringBootTest
@Transactional
abstract class AbstractImportacionTrabajoFase68FTest {

    protected static final String PREFIJO_CODIGO = "TEST_68F_";

    @Autowired
    protected DatasetClinicoService datasetClinicoService;
    @Autowired
    protected CampoClinicoService campoClinicoService;
    @Autowired
    protected PlantillaImportacionService plantillaImportacionService;
    @Autowired
    protected MapeoCampoImportacionService mapeoCampoImportacionService;
    @Autowired
    protected ImportacionTrabajoService importacionTrabajoService;
    @Autowired
    protected TrazabilidadImportacionTrabajoService trazabilidadService;

    @Autowired
    protected DatasetClinicoRepository datasetClinicoRepository;
    @Autowired
    protected CampoClinicoRepository campoClinicoRepository;
    @Autowired
    protected PlantillaImportacionRepository plantillaImportacionRepository;
    @Autowired
    protected MapeoCampoImportacionRepository mapeoCampoImportacionRepository;
    @Autowired
    protected ImportacionTrabajoRepository importacionTrabajoRepository;
    @Autowired
    protected FilaImportacionTrabajoRepository filaImportacionTrabajoRepository;
    @Autowired
    protected EventoImportacionTrabajoRepository eventoImportacionTrabajoRepository;
    @Autowired
    protected ImportacionGenericaRepository importacionGenericaRepository;
    @Autowired
    protected RegistroClinicoGenericoRepository registroClinicoGenericoRepository;

    private final AtomicInteger contador = new AtomicInteger();

    /** Código único con el prefijo de la fase, para poder identificar/limpiar datos de test fácilmente. */
    protected String codigoUnico(String sufijo) {
        return PREFIJO_CODIGO + sufijo + "_" + contador.incrementAndGet() + "_" + System.nanoTime();
    }

    protected Long crearDatasetBorrador(String sufijo) {
        DatasetClinicoRequestDto request = new DatasetClinicoRequestDto();
        request.setCodigo(codigoUnico(sufijo));
        request.setNombre("Test 68F " + sufijo);
        request.setEstadoDataset(EstadoDatasetClinico.BORRADOR);
        return datasetClinicoService.crear(request).getId();
    }

    protected Long crearCampo(Long datasetId, String codigo, String etiqueta, TipoDatoExcel tipoDato, boolean obligatorio) {
        CampoClinicoRequestDto request = new CampoClinicoRequestDto();
        request.setCodigo(codigo);
        request.setEtiqueta(etiqueta);
        request.setTipoDato(tipoDato);
        request.setEsComun(false);
        request.setObligatorio(obligatorio);
        return campoClinicoService.crear(datasetId, request).getId();
    }

    protected Long crearPlantilla(Long datasetId, String nombre) {
        PlantillaImportacionRequestDto request = new PlantillaImportacionRequestDto();
        request.setNombre(nombre);
        request.setOrigen(OrigenImportacion.CSV);
        request.setFilaCabecera(0);
        return plantillaImportacionService.crear(datasetId, request).getId();
    }

    protected void crearMapeo(
            Long plantillaId, String nombreColumnaOrigen, Long campoClinicoId, TipoDatoExcel tipoDato, boolean obligatorio) {
        MapeoCampoImportacionRequestDto request = new MapeoCampoImportacionRequestDto();
        request.setNombreColumnaOrigen(nombreColumnaOrigen);
        request.setCampoClinicoId(campoClinicoId);
        request.setTipoDato(tipoDato);
        request.setObligatorio(obligatorio);
        request.setPoliticaCampoFaltante(
                obligatorio ? PoliticaCampoFaltante.ERROR : PoliticaCampoFaltante.ADVERTENCIA);
        mapeoCampoImportacionService.crear(plantillaId, request);
    }

    /** CSV con separador ';' y codificación UTF-8, tal como llegan las exportaciones reales del hospital. */
    protected MockMultipartFile archivoCsv(String contenido) {
        return new MockMultipartFile(
                "archivo", "test_68f.csv", "text/csv", contenido.getBytes(StandardCharsets.UTF_8));
    }
}
