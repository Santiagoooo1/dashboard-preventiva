package com.preventiva.backend.repository;

import com.preventiva.backend.entity.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServicioRepository extends JpaRepository<Servicio, Long> {

    Optional<Servicio> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);
}