package com.preventiva.backend.repository;

import com.preventiva.backend.entity.Cirugia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CirugiaRepository extends JpaRepository<Cirugia, Long> {

    List<Cirugia> findByServicioCodigo(String codigoServicio);

    List<Cirugia> findByFechaCirugiaBetween(LocalDate fechaInicio, LocalDate fechaFin);

    List<Cirugia> findByHc(String hc);

    boolean existsByHcAndFechaCirugiaAndCie10(
            String hc,
            java.time.LocalDate fechaCirugia,
            String cie10);
}