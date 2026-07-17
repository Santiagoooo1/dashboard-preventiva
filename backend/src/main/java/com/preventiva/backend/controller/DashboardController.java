package com.preventiva.backend.controller;

import com.preventiva.backend.dto.DashboardResumenDto;
import com.preventiva.backend.dto.DashboardSerieDto;
import com.preventiva.backend.service.interfaces.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/resumen")
    public DashboardResumenDto obtenerResumen() {
        return dashboardService.obtenerResumen();
    }

    @GetMapping("/cirugias-por-mes")
    public List<DashboardSerieDto> obtenerCirugiasPorMes() {
        return dashboardService.obtenerCirugiasPorMes();
    }

    @GetMapping("/profilaxis")
    public List<DashboardSerieDto> obtenerProfilaxis() {
        return dashboardService.obtenerProfilaxis();
    }

    @GetMapping("/ilq-por-servicio")
    public List<DashboardSerieDto> obtenerIlqPorServicio() {
        return dashboardService.obtenerIlqPorServicio();
    }

    @GetMapping("/microorganismos")
    public List<DashboardSerieDto> obtenerMicroorganismos() {
        return dashboardService.obtenerMicroorganismos();
    }

    @GetMapping("/ilq-por-localizacion")
    public List<DashboardSerieDto> obtenerIlqPorLocalizacion() {
        return dashboardService.obtenerIlqPorLocalizacion();
    }
}
