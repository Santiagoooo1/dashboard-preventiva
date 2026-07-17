package com.preventiva.backend.repository;

import com.preventiva.backend.entity.Microbiologia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MicrobiologiaRepository extends JpaRepository<Microbiologia, Long> {

    Optional<Microbiologia> findByInfeccionId(Long infeccionId);

    List<Microbiologia> findByMicroorganismoContainingIgnoreCase(String microorganismo);
}