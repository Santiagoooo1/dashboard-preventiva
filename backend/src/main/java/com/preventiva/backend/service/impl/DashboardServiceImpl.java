package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.DashboardResumenDto;
import com.preventiva.backend.dto.DashboardSerieDto;
import com.preventiva.backend.entity.InfeccionQuirurgica;
import com.preventiva.backend.entity.Microbiologia;
import com.preventiva.backend.entity.Profilaxis;
import com.preventiva.backend.repository.CirugiaRepository;
import com.preventiva.backend.repository.InfeccionQuirurgicaRepository;
import com.preventiva.backend.repository.MicrobiologiaRepository;
import com.preventiva.backend.repository.ProfilaxisRepository;
import com.preventiva.backend.service.interfaces.DashboardService;
import com.preventiva.backend.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final CirugiaRepository cirugiaRepository;
    private final InfeccionQuirurgicaRepository infeccionQuirurgicaRepository;
    private final ProfilaxisRepository profilaxisRepository;
    private final MicrobiologiaRepository microbiologiaRepository;

    @Override
    public DashboardResumenDto obtenerResumen() {
        long totalCirugias = cirugiaRepository.count();

        List<InfeccionQuirurgica> infecciones = infeccionQuirurgicaRepository.findAll();

        long totalIlq = infecciones.stream()
                .filter(infeccion -> Boolean.TRUE.equals(infeccion.getTieneIlq()))
                .count();

        double tasaIlq = calcularPorcentaje(totalIlq, totalCirugias);

        List<Profilaxis> profilaxis = profilaxisRepository.findAll();

        long profilaxisAdecuadas = profilaxis.stream()
                .filter(p -> esProfilaxisAdecuada(p.getAdecuacionProfilaxis()))
                .count();

        long profilaxisInadecuadas = profilaxis.stream()
                .filter(p -> esProfilaxisInadecuada(p.getAdecuacionProfilaxis()))
                .count();

        long totalProfilaxisConDato = profilaxisAdecuadas + profilaxisInadecuadas;

        long profilaxisSinDato = profilaxis.stream()
                .filter(p -> !tieneTexto(p.getAdecuacionProfilaxis()))
                .count();

        double tasaAdecuacionProfilaxis = calcularPorcentaje(
                profilaxisAdecuadas,
                totalProfilaxisConDato);

        return DashboardResumenDto.builder()
                .totalCirugias(totalCirugias)
                .totalIlq(totalIlq)
                .tasaIlq(tasaIlq)
                .profilaxisAdecuadas(profilaxisAdecuadas)
                .profilaxisInadecuadas(profilaxisInadecuadas)
                .profilaxisSinDato(profilaxisSinDato)
                .tasaAdecuacionProfilaxis(tasaAdecuacionProfilaxis)
                .build();
    }

    @Override
    public List<DashboardSerieDto> obtenerCirugiasPorMes() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        Map<YearMonth, Long> agrupado = cirugiaRepository.findAll()
                .stream()
                .filter(cirugia -> cirugia.getFechaCirugia() != null)
                .collect(Collectors.groupingBy(
                        cirugia -> YearMonth.from(cirugia.getFechaCirugia()),
                        TreeMap::new,
                        Collectors.counting()));

        return convertirMapaYearMonthADto(agrupado, formatter);
    }

    @Override
    public List<DashboardSerieDto> obtenerProfilaxis() {
        Map<String, Long> resultado = new LinkedHashMap<>();
        resultado.put("Adecuada", 0L);
        resultado.put("Inadecuada", 0L);
        resultado.put("Sin dato", 0L);
        resultado.put("Otro valor", 0L);

        for (Profilaxis profilaxis : profilaxisRepository.findAll()) {
            String valor = profilaxis.getAdecuacionProfilaxis();

            if (!tieneTexto(valor)) {
                sumar(resultado, "Sin dato");
            } else if (esProfilaxisAdecuada(valor)) {
                sumar(resultado, "Adecuada");
            } else if (esProfilaxisInadecuada(valor)) {
                sumar(resultado, "Inadecuada");
            } else {
                sumar(resultado, "Otro valor");
            }
        }

        return convertirMapaStringADto(resultado, true);
    }

    @Override
    public List<DashboardSerieDto> obtenerIlqPorServicio() {
        Map<String, Long> resultado = new TreeMap<>();

        for (InfeccionQuirurgica infeccion : infeccionQuirurgicaRepository.findAll()) {
            if (!Boolean.TRUE.equals(infeccion.getTieneIlq())) {
                continue;
            }

            String servicio = "Sin servicio";

            if (infeccion.getCirugia() != null
                    && infeccion.getCirugia().getServicio() != null
                    && tieneTexto(infeccion.getCirugia().getServicio().getCodigo())) {
                servicio = infeccion.getCirugia().getServicio().getCodigo();
            }

            sumar(resultado, servicio);
        }

        return convertirMapaStringADto(resultado, false);
    }

    @Override
    public List<DashboardSerieDto> obtenerMicroorganismos() {
        Map<String, Long> resultado = new TreeMap<>();

        for (Microbiologia microbiologia : microbiologiaRepository.findAll()) {
            String microorganismo = microbiologia.getMicroorganismo();

            if (!tieneTexto(microorganismo)) {
                continue;
            }

            String etiqueta = microorganismo.trim();
            sumar(resultado, etiqueta);
        }

        return convertirMapaStringADto(resultado, false);
    }

    @Override
    public List<DashboardSerieDto> obtenerIlqPorLocalizacion() {
        Map<String, Long> resultado = new TreeMap<>();

        for (InfeccionQuirurgica infeccion : infeccionQuirurgicaRepository.findAll()) {
            if (!Boolean.TRUE.equals(infeccion.getTieneIlq())) {
                continue;
            }

            String localizacion = infeccion.getLocalizacionInfeccion();

            if (!tieneTexto(localizacion)) {
                localizacion = "Sin localización";
            }

            sumar(resultado, localizacion.trim());
        }

        return convertirMapaStringADto(resultado, false);
    }

    private boolean esProfilaxisAdecuada(String valor) {
        if (!tieneTexto(valor)) {
            return false;
        }

        String normalizado = TextNormalizer.normalize(valor);

        return normalizado.equals("ADECUADA")
                || normalizado.equals("SI")
                || normalizado.equals("S");
    }

    private boolean esProfilaxisInadecuada(String valor) {
        if (!tieneTexto(valor)) {
            return false;
        }

        String normalizado = TextNormalizer.normalize(valor);

        return normalizado.equals("INADECUADA")
                || normalizado.equals("NO ADECUADA")
                || normalizado.equals("NO")
                || normalizado.equals("N");
    }

    private boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private double calcularPorcentaje(long parte, long total) {
        if (total == 0) {
            return 0.0;
        }

        double porcentaje = (parte * 100.0) / total;

        return BigDecimal.valueOf(porcentaje)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private void sumar(Map<String, Long> mapa, String clave) {
        mapa.put(clave, mapa.getOrDefault(clave, 0L) + 1);
    }

    private List<DashboardSerieDto> convertirMapaStringADto(
            Map<String, Long> mapa,
            boolean incluirValoresCero) {
        return mapa.entrySet()
                .stream()
                .filter(entry -> incluirValoresCero || entry.getValue() > 0)
                .map(entry -> DashboardSerieDto.builder()
                        .etiqueta(entry.getKey())
                        .valor(entry.getValue())
                        .build())
                .toList();
    }

    private List<DashboardSerieDto> convertirMapaYearMonthADto(
            Map<YearMonth, Long> mapa,
            DateTimeFormatter formatter) {
        return mapa.entrySet()
                .stream()
                .map(entry -> DashboardSerieDto.builder()
                        .etiqueta(entry.getKey().format(formatter))
                        .valor(entry.getValue())
                        .build())
                .toList();
    }
}
