package com.preventiva.backend.repository;

import com.preventiva.backend.entity.SeguimientoPostoperatorio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeguimientoPostoperatorioRepository extends JpaRepository<SeguimientoPostoperatorio, Long> {

    Optional<SeguimientoPostoperatorio> findByCirugiaId(Long cirugiaId);
}