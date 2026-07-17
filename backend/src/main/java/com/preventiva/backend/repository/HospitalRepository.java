package com.preventiva.backend.repository;

import com.preventiva.backend.entity.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HospitalRepository extends JpaRepository<Hospital, Long> {

    Optional<Hospital> findByCodigoHospital(String codigoHospital);
}