package com.preventiva.backend.repository;

import com.preventiva.backend.entity.MedidasPreventivas;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MedidasPreventivasRepository extends JpaRepository<MedidasPreventivas, Long> {

    Optional<MedidasPreventivas> findByCirugiaId(Long cirugiaId);
}