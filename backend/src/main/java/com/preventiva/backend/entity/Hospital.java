package com.preventiva.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "hospitales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hospital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 255)
    private String direccion;

    @Column(name = "codigo_hospital", unique = true, length = 50)
    private String codigoHospital;
}