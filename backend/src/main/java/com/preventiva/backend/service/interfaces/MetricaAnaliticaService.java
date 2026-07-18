package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.ComparativaRequestDto;
import com.preventiva.backend.dto.ComparativaResponseDto;
import com.preventiva.backend.dto.PanelSerieTemporalResponseDto;
import com.preventiva.backend.dto.SerieTemporalRequestDto;
import com.preventiva.backend.dto.SerieTemporalResponseDto;

public interface MetricaAnaliticaService {

    SerieTemporalResponseDto serieTemporal(Long metricaId, SerieTemporalRequestDto request);

    ComparativaResponseDto comparativa(Long metricaId, ComparativaRequestDto request);

    PanelSerieTemporalResponseDto serieTemporalPanel(Long panelId, SerieTemporalRequestDto request);
}
