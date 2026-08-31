package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.BaseEvaluableDashboardDto;
import com.preventiva.backend.dto.FiltroMetricaDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.enums.OperadorFiltro;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.repository.CampoClinicoRepository;
import com.preventiva.backend.repository.DatasetClinicoRepository;
import com.preventiva.backend.service.interfaces.BaseEvaluableDashboardService;
import com.preventiva.backend.util.BloqueInicialIlq;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Decide sobre qué población cuentan los indicadores de actividad del dashboard
 * inicial (Fase 6.9L.2).
 *
 * <p>Sale de un caso real. Un Excel de vigilancia traía 55 filas con contenido:
 * 46 intervenciones y 9 fichas en las que alguien había escrito el número de
 * historia y nada más. La importación conserva las 55 —el dato está, y borrarlo
 * en la carga sería peor—, pero el dashboard abría diciendo «55 registros, 55
 * pacientes, 84 % de completitud». Ninguna de las tres cifras describía lo que
 * el servicio hace: hubo 46 intervenciones, de 46 pacientes, con el dato de
 * infección completo al 100 %.
 *
 * <p>La regla es una sola y se apoya en un hecho, no en una heurística: si el
 * dataset tiene {@code fechaEvento}, una fila sin fecha no es un evento. Los
 * indicadores que cuentan actividad se calculan entonces sobre las filas que sí
 * la tienen.
 *
 * <p>Deliberadamente acotado al dashboard inicial. El motor de métricas no
 * cambia, y una métrica escrita a mano sigue contando todo: quien escribe un
 * conteo sin filtros espera el número de filas, no una interpretación.
 */
@Service
@RequiredArgsConstructor
public class BaseEvaluableDashboardServiceImpl implements BaseEvaluableDashboardService {

    /** Campo canónico que marca cuándo ocurrió el evento principal del dataset. */
    private static final String CAMPO_FECHA_EVENTO = "fechaEvento";

    /**
     * Etiquetas del indicador de volumen.
     *
     * <p>«Intervenciones» solo cuando consta que el dataset es quirúrgico. La
     * aplicación es genérica y {@code fechaEvento} puede ser la fecha de una
     * consulta, un ingreso o un episodio: llamar «intervención» a cualquiera de
     * esas cosas sería una etiqueta clínicamente falsa. «Eventos» es el término
     * que ya usa el propio modelo ({@code fechaEvento}, {@code tipoEvento}).
     */
    private static final String ETIQUETA_QUIRURGICO = "Intervenciones";
    private static final String ETIQUETA_GENERICA = "Eventos";
    private static final String ETIQUETA_SIN_ACOTAR = "Total de registros";

    private static final String MOTIVO_QUIRURGICO =
            "El dataset registra intervenciones con su fecha, así que los indicadores de actividad "
                    + "cuentan las que la tienen: una ficha sin fecha no es una intervención.";
    private static final String MOTIVO_GENERICO =
            "El dataset registra la fecha del evento, así que los indicadores de actividad cuentan "
                    + "las filas que la tienen: una ficha sin fecha no es un evento.";
    private static final String MOTIVO_NO_APLICA =
            "El dataset no tiene fecha de evento, así que no hay forma de distinguir una fila "
                    + "incompleta de una completa: se cuenta todo.";

    private final DatasetClinicoRepository datasetClinicoRepository;
    private final CampoClinicoRepository campoClinicoRepository;

    @Override
    @Transactional(readOnly = true)
    public BaseEvaluableDashboardDto resolver(Long datasetId) {
        if (!datasetClinicoRepository.existsById(datasetId)) {
            throw new NoSuchElementException("No existe el dataset clínico con id: " + datasetId);
        }

        List<CampoClinico> campos = campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId);

        // Deliberadamente NO se mira prioridadDashboard. Esa marca dice qué
        // quiere ver el usuario en sus widgets, no qué es cada campo: excluir la
        // fecha de la propuesta de gráficos no la invalida para saber qué filas
        // son eventos reales. Mezclarlo convertiría una preferencia de
        // visualización en una regla semántica.
        boolean tieneFechaEvento = campos.stream()
                // Sí se exige el tipo: un campo llamado igual pero que no sea
                // una fecha no sirve para decidir si la fila es un evento.
                .filter(c -> c.getTipoDato() == TipoDatoExcel.FECHA)
                .map(CampoClinico::getCodigo)
                .anyMatch(CAMPO_FECHA_EVENTO::equalsIgnoreCase);

        if (!tieneFechaEvento) {
            return BaseEvaluableDashboardDto.builder()
                    .aplicaFechaEvento(false)
                    .filtros(List.of())
                    .etiquetaVolumen(ETIQUETA_SIN_ACOTAR)
                    .motivo(MOTIVO_NO_APLICA)
                    .build();
        }

        // Un dataset es quirúrgico si registra infección de localización
        // quirúrgica, que es el mismo hecho que activa el bloque clínico de ILQ.
        // Se comprueba por el campo canónico, nunca por el nombre del dataset.
        boolean esQuirurgico = campos.stream()
                .map(CampoClinico::getCodigo)
                .anyMatch(BloqueInicialIlq.CAMPO_IMPRESCINDIBLE::equalsIgnoreCase);

        FiltroMetricaDto conFecha = new FiltroMetricaDto();
        conFecha.setCampo(CAMPO_FECHA_EVENTO);
        conFecha.setOperador(OperadorFiltro.NOT_NULL);

        return BaseEvaluableDashboardDto.builder()
                .aplicaFechaEvento(true)
                .filtros(List.of(conFecha))
                .etiquetaVolumen(esQuirurgico ? ETIQUETA_QUIRURGICO : ETIQUETA_GENERICA)
                .motivo(esQuirurgico ? MOTIVO_QUIRURGICO : MOTIVO_GENERICO)
                .build();
    }
}
