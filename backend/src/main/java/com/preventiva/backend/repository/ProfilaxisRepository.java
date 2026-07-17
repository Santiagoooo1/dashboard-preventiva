package com.preventiva.backend.repository;

import com.preventiva.backend.entity.Profilaxis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfilaxisRepository extends JpaRepository<Profilaxis, Long> {

    Optional<Profilaxis> findByCirugiaId(Long cirugiaId);
}