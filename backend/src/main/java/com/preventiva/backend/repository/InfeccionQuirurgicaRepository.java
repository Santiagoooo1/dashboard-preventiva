package com.preventiva.backend.repository;

import com.preventiva.backend.entity.InfeccionQuirurgica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InfeccionQuirurgicaRepository extends JpaRepository<InfeccionQuirurgica, Long> {

    Optional<InfeccionQuirurgica> findByCirugiaId(Long cirugiaId);

    List<InfeccionQuirurgica> findByTieneIlq(Boolean tieneIlq);
}