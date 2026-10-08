package com.preventiva.backend.controller;

import com.preventiva.backend.dto.BloqueInformeRequestDto;
import com.preventiva.backend.dto.CatalogoInformeResponseDto;
import com.preventiva.backend.dto.DocumentoPdfDto;
import com.preventiva.backend.dto.BloqueInformeResponseDto;
import com.preventiva.backend.dto.InformeClinicoRequestDto;
import com.preventiva.backend.dto.InformeClinicoResponseDto;
import com.preventiva.backend.dto.PaginaInformeResponseDto;
import com.preventiva.backend.service.interfaces.CatalogoInformeService;
import com.preventiva.backend.service.interfaces.InformeClinicoService;
import com.preventiva.backend.service.interfaces.InformePdfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/informes")
@RequiredArgsConstructor
public class InformeClinicoController {

    private final InformeClinicoService informeClinicoService;
    private final CatalogoInformeService catalogoInformeService;
    private final InformePdfService informePdfService;

    /**
     * Descarga el informe en PDF (Fase 6.9R.2).
     *
     * <p>El cliente solo manda el id. La URL que se renderiza la compone el
     * servidor con su propia configuración, así que no se puede usar este
     * endpoint para que el servidor visite una dirección elegida por el
     * usuario.
     *
     * <p>Se devuelven los bytes tal cual, no un JSON con base64: el navegador
     * necesita la cabecera {@code Content-Disposition} para lanzar la descarga,
     * y un base64 obligaría al frontend a reconstruir el archivo a mano.
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable("id") Long id) {
        DocumentoPdfDto documento = informePdfService.generar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, cabeceraDescarga(documento.getNombreArchivo()))
                .contentLength(documento.getContenido().length)
                .body(documento.getContenido());
    }

    /**
     * Cabecera de descarga con las dos formas del nombre.
     *
     * <p>{@code filename} en ASCII para clientes antiguos y {@code filename*}
     * codificado en UTF-8 (RFC 5987) para los actuales. El nombre ya viene
     * saneado del servicio; aquí solo se codifica.
     */
    private String cabeceraDescarga(String nombre) {
        String codificado = URLEncoder.encode(nombre, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + nombre + "\"; filename*=UTF-8''" + codificado;
    }

    /**
     * Qué se puede insertar en un informe: los widgets de los dashboards con su
     * configuración, y aparte las métricas que no están en ninguno.
     *
     * <p>Estructural: no ejecuta ningún cálculo clínico.
     */
    @GetMapping("/catalogo")
    public CatalogoInformeResponseDto catalogo() {
        return catalogoInformeService.obtener();
    }

    @GetMapping
    public List<InformeClinicoResponseDto> listar() {
        return informeClinicoService.listar();
    }

    /** Estructura del informe, sin calcular resultados: es lo que usa el editor. */
    @GetMapping("/{id}")
    public InformeClinicoResponseDto obtener(@PathVariable("id") Long id) {
        return informeClinicoService.obtenerEstructura(id);
    }

    /**
     * Informe con cada bloque analítico ya resuelto: una sola llamada para todo
     * el documento, para la vista previa y el futuro PDF.
     */
    @GetMapping("/{id}/resultados")
    public InformeClinicoResponseDto obtenerConResultados(@PathVariable("id") Long id) {
        return informeClinicoService.obtenerConResultados(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InformeClinicoResponseDto crear(@Valid @RequestBody InformeClinicoRequestDto request) {
        return informeClinicoService.crear(request);
    }

    @PutMapping("/{id}")
    public InformeClinicoResponseDto actualizar(
            @PathVariable("id") Long id, @Valid @RequestBody InformeClinicoRequestDto request) {
        return informeClinicoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable("id") Long id) {
        informeClinicoService.eliminar(id);
    }

    // --- Páginas ---

    @PostMapping("/{id}/paginas")
    @ResponseStatus(HttpStatus.CREATED)
    public PaginaInformeResponseDto anadirPagina(@PathVariable("id") Long id) {
        return informeClinicoService.anadirPagina(id);
    }

    @PostMapping("/{id}/paginas/{paginaId}/duplicar")
    @ResponseStatus(HttpStatus.CREATED)
    public PaginaInformeResponseDto duplicarPagina(
            @PathVariable("id") Long id, @PathVariable("paginaId") Long paginaId) {
        return informeClinicoService.duplicarPagina(id, paginaId);
    }

    @DeleteMapping("/{id}/paginas/{paginaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarPagina(@PathVariable("id") Long id, @PathVariable("paginaId") Long paginaId) {
        informeClinicoService.eliminarPagina(id, paginaId);
    }

    @PutMapping("/{id}/paginas/{paginaId}/posicion/{posicion}")
    public InformeClinicoResponseDto moverPagina(
            @PathVariable("id") Long id,
            @PathVariable("paginaId") Long paginaId,
            @PathVariable("posicion") int posicion) {
        return informeClinicoService.moverPagina(id, paginaId, posicion);
    }

    // --- Bloques ---

    @PostMapping("/{id}/paginas/{paginaId}/bloques")
    @ResponseStatus(HttpStatus.CREATED)
    public BloqueInformeResponseDto anadirBloque(
            @PathVariable("id") Long id,
            @PathVariable("paginaId") Long paginaId,
            @Valid @RequestBody BloqueInformeRequestDto request) {
        return informeClinicoService.anadirBloque(id, paginaId, request);
    }

    @PutMapping("/{id}/paginas/{paginaId}/bloques/{bloqueId}")
    public BloqueInformeResponseDto actualizarBloque(
            @PathVariable("id") Long id,
            @PathVariable("paginaId") Long paginaId,
            @PathVariable("bloqueId") Long bloqueId,
            @Valid @RequestBody BloqueInformeRequestDto request) {
        return informeClinicoService.actualizarBloque(id, paginaId, bloqueId, request);
    }

    @DeleteMapping("/{id}/paginas/{paginaId}/bloques/{bloqueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarBloque(
            @PathVariable("id") Long id,
            @PathVariable("paginaId") Long paginaId,
            @PathVariable("bloqueId") Long bloqueId) {
        informeClinicoService.eliminarBloque(id, paginaId, bloqueId);
    }

    @PutMapping("/{id}/paginas/{paginaId}/bloques/{bloqueId}/posicion/{posicion}")
    public PaginaInformeResponseDto moverBloque(
            @PathVariable("id") Long id,
            @PathVariable("paginaId") Long paginaId,
            @PathVariable("bloqueId") Long bloqueId,
            @PathVariable("posicion") int posicion) {
        return informeClinicoService.moverBloque(id, paginaId, bloqueId, posicion);
    }
}
