package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.DashboardResumenDto;
import com.preventiva.backend.dto.DashboardSerieDto;

import java.util.List;

public interface DashboardService {

    DashboardResumenDto obtenerResumen();

    List<DashboardSerieDto> obtenerCirugiasPorMes();

    List<DashboardSerieDto> obtenerProfilaxis();

    List<DashboardSerieDto> obtenerIlqPorServicio();

    List<DashboardSerieDto> obtenerMicroorganismos();

    List<DashboardSerieDto> obtenerIlqPorLocalizacion();
}
