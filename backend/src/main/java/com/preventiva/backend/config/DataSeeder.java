package com.preventiva.backend.config;

import com.preventiva.backend.entity.*;
import com.preventiva.backend.enums.CampoDestino;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class DataSeeder {

    private final RolRepository rolRepository;
    private final HospitalRepository hospitalRepository;
    private final ServicioRepository servicioRepository;
    private final UsuarioRepository usuarioRepository;
    private final PlantillaExcelRepository plantillaExcelRepository;
    private final MapeoColumnaExcelRepository mapeoColumnaExcelRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner seedData() {
        return args -> {

            Rol adminRol = crearRolSiNoExiste(
                    "ADMIN",
                    "Administrador del sistema");

            Rol medicoRol = crearRolSiNoExiste(
                    "MEDICO",
                    "Usuario médico preventivista");

            Hospital hospital = crearHospitalSiNoExiste();

            crearUsuarioAdminSiNoExiste(adminRol);

            Servicio cpp = crearServicioSiNoExiste("CPP", "Cirugía CPP", hospital);
            Servicio trauma = crearServicioSiNoExiste("TRA", "Traumatología", hospital);
            Servicio neuro = crearServicioSiNoExiste("NEURO", "Neurocirugía", hospital);
            Servicio colorectal = crearServicioSiNoExiste("COLORECTAL", "Cirugía colorrectal", hospital);
            Servicio colecistectomia = crearServicioSiNoExiste("COLECISTECTOMIA", "Colecistectomía", hospital);
            Servicio cirRobotica = crearServicioSiNoExiste("CIR_ROBOTICA", "Cirugía robótica", hospital);
            Servicio gastroEsof = crearServicioSiNoExiste("GASTRO_ESOF", "Cirugía gastroesofágica", hospital);
            Servicio cesareas = crearServicioSiNoExiste("CESAREAS", "Cesáreas", hospital);

            crearPlantillaConMapeosSiNoExiste("CPP_2026", "Plantilla CPP 2026", cpp);
            crearPlantillaConMapeosSiNoExiste("TRA_2026", "Plantilla Trauma 2026", trauma);
            crearPlantillaConMapeosSiNoExiste("NEURO_2026", "Plantilla Neurocirugía 2026", neuro);
            crearPlantillaConMapeosSiNoExiste("COLORECTAL_2026", "Plantilla Colorrectal 2026", colorectal);
            crearPlantillaConMapeosSiNoExiste("COLECISTECTOMIA_2026", "Plantilla Colecistectomía 2026",
                    colecistectomia);
            crearPlantillaConMapeosSiNoExiste("CIR_ROBOTICA_2026", "Plantilla Cirugía Robótica 2026", cirRobotica);
            crearPlantillaConMapeosSiNoExiste("GASTRO_ESOF_2026", "Plantilla Gastroesofágica 2026", gastroEsof);
            crearPlantillaConMapeosSiNoExiste("CESAREAS_2026", "Plantilla Cesáreas 2026", cesareas);

            System.out.println("DataSeeder ejecutado correctamente.");
        };
    }

    private Rol crearRolSiNoExiste(String nombre, String descripcion) {
        return rolRepository.findByNombre(nombre)
                .orElseGet(() -> rolRepository.save(
                        Rol.builder()
                                .nombre(nombre)
                                .descripcion(descripcion)
                                .build()));
    }

    private Hospital crearHospitalSiNoExiste() {
        return hospitalRepository.findByCodigoHospital("HOSPITAL_DEMO")
                .orElseGet(() -> hospitalRepository.save(
                        Hospital.builder()
                                .nombre("Hospital Demo Medicina Preventiva")
                                .codigoHospital("HOSPITAL_DEMO")
                                .direccion("Pendiente de definir")
                                .build()));
    }

    private void crearUsuarioAdminSiNoExiste(Rol adminRol) {
        String email = "admin@preventiva.local";

        if (!usuarioRepository.existsByEmail(email)) {
            Usuario usuario = Usuario.builder()
                    .nombre("Administrador")
                    .email(email)
                    .password(passwordEncoder.encode("admin1234"))
                    .rol(adminRol)
                    .activo(true)
                    .build();

            usuarioRepository.save(usuario);
        }
    }

    private Servicio crearServicioSiNoExiste(String codigo, String nombre, Hospital hospital) {
        return servicioRepository.findByCodigo(codigo)
                .orElseGet(() -> servicioRepository.save(
                        Servicio.builder()
                                .codigo(codigo)
                                .nombre(nombre)
                                .descripcion("Servicio inicial cargado por DataSeeder")
                                .hospital(hospital)
                                .activo(true)
                                .build()));
    }

    private void crearPlantillaConMapeosSiNoExiste(String codigo, String nombre, Servicio servicio) {
        PlantillaExcel plantilla = plantillaExcelRepository.findByCodigo(codigo)
                .orElseGet(() -> plantillaExcelRepository.save(
                        PlantillaExcel.builder()
                                .codigo(codigo)
                                .nombre(nombre)
                                .descripcion("Plantilla inicial para importación de Excel")
                                .servicio(servicio)
                                .activa(true)
                                .build()));

        boolean yaTieneMapeos = !mapeoColumnaExcelRepository
                .findByPlantillaIdAndActivaTrue(plantilla.getId())
                .isEmpty();

        if (!yaTieneMapeos) {
            crearMapeo(plantilla, "HC", CampoDestino.CIRUGIA_HC, TipoDatoExcel.TEXTO, true);
            crearMapeo(plantilla, "SEXO", CampoDestino.CIRUGIA_SEXO, TipoDatoExcel.TEXTO, false);
            crearMapeo(plantilla, "EDAD", CampoDestino.CIRUGIA_EDAD, TipoDatoExcel.ENTERO, false);
            crearMapeo(plantilla, "SERVICIO", CampoDestino.CIRUGIA_SERVICIO, TipoDatoExcel.TEXTO, true);
            crearMapeo(plantilla, "PROCEDIMIENTO", CampoDestino.CIRUGIA_PROCEDIMIENTO, TipoDatoExcel.TEXTO, false);
            crearMapeo(plantilla, "CIE-10", CampoDestino.CIRUGIA_CIE10, TipoDatoExcel.TEXTO, false);
            crearMapeo(plantilla, "FECHA INGRESO", CampoDestino.CIRUGIA_FECHA_INGRESO, TipoDatoExcel.FECHA, false);
            crearMapeo(plantilla, "FECHA ALTA", CampoDestino.CIRUGIA_FECHA_ALTA, TipoDatoExcel.FECHA, false);
            crearMapeo(plantilla, "FECHA CIRUGÍA", CampoDestino.CIRUGIA_FECHA_CIRUGIA, TipoDatoExcel.FECHA, true);
            crearMapeo(plantilla, "MINUTOS DURACIÓN DE CIRUGÍA", CampoDestino.CIRUGIA_DURACION_MINUTOS,
                    TipoDatoExcel.ENTERO, false);
            crearMapeo(plantilla, "CIRUGÍA URGENTE", CampoDestino.CIRUGIA_URGENTE, TipoDatoExcel.BOOLEANO, false);
            crearMapeo(plantilla, "ASA", CampoDestino.CIRUGIA_ASA, TipoDatoExcel.TEXTO, false);
            crearMapeo(plantilla, "GRADO CONTAMINACIÓN DE CIRUGÍA INICIAL",
                    CampoDestino.CIRUGIA_GRADO_CONTAMINACION_INICIAL, TipoDatoExcel.TEXTO, false);
            crearMapeo(plantilla, "GRADO CONTAMINACIÓN DE CIRUGÍA FINAL",
                    CampoDestino.CIRUGIA_GRADO_CONTAMINACION_FINAL, TipoDatoExcel.TEXTO, false);
            crearMapeo(plantilla, "ABORDAJE QUIRÚRGICO", CampoDestino.CIRUGIA_ABORDAJE_QUIRURGICO, TipoDatoExcel.TEXTO,
                    false);

            crearMapeo(plantilla, "INDICACIÓN DE PROFILAXIS", CampoDestino.PROFILAXIS_INDICACION, TipoDatoExcel.TEXTO,
                    false);
            crearMapeo(plantilla, "ADMINISTRACIÓN DE PROFILAXIS", CampoDestino.PROFILAXIS_ADMINISTRACION,
                    TipoDatoExcel.TEXTO, false);
            crearMapeo(plantilla, "PROFILAXIS ANTIBIÓTICA PRESCRITA EN DRAGO", CampoDestino.PROFILAXIS_DRAGO,
                    TipoDatoExcel.TEXTO, true);
            crearMapeo(plantilla, "ADECUACIÓN DE PROFILAXIS", CampoDestino.PROFILAXIS_ADECUACION,
                    TipoDatoExcel.BOOLEANO, true);
            crearMapeo(plantilla, "MOTIVOS DE INADECUACIÓN", CampoDestino.PROFILAXIS_MOTIVO_INADECUACION,
                    TipoDatoExcel.TEXTO, false);
            crearMapeo(plantilla, "VALIDACIÓN DOSIS ADICIONALES", CampoDestino.PROFILAXIS_VALIDACION_DOSIS_ADICIONALES,
                    TipoDatoExcel.TEXTO, false);

            crearMapeo(plantilla, "INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA", CampoDestino.ILQ_TIENE,
                    TipoDatoExcel.BOOLEANO, true);
            crearMapeo(plantilla, "FECHA DE INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA", CampoDestino.ILQ_FECHA,
                    TipoDatoExcel.FECHA, false);
            crearMapeo(plantilla, "FECHA FIN DE VIGILANCIA", CampoDestino.ILQ_FECHA_FIN_VIGILANCIA, TipoDatoExcel.FECHA,
                    false);
            crearMapeo(plantilla, "LOCALIZACIÓN DE LA INFECCIÓN", CampoDestino.ILQ_LOCALIZACION, TipoDatoExcel.TEXTO,
                    false);
            crearMapeo(plantilla, "REINGRESO por ILQ", CampoDestino.ILQ_REINGRESO, TipoDatoExcel.BOOLEANO, false);
            crearMapeo(plantilla, "FECHA REINGRESO", CampoDestino.ILQ_FECHA_REINGRESO, TipoDatoExcel.FECHA, false);

            crearMapeo(plantilla, "CULTIVO DE ILQ", CampoDestino.MICRO_CULTIVO_ILQ, TipoDatoExcel.BOOLEANO, false);
            crearMapeo(plantilla, "TIPO DE MUESTRA", CampoDestino.MICRO_TIPO_MUESTRA, TipoDatoExcel.TEXTO, false);
            crearMapeo(plantilla, "RESULTADO CULTIVO_1", CampoDestino.MICRO_RESULTADO_CULTIVO, TipoDatoExcel.TEXTO,
                    false);
            crearMapeo(plantilla, "MICROOR.", CampoDestino.MICRO_MICROORGANISMO, TipoDatoExcel.TEXTO, false);
            crearMapeo(plantilla, "MICROORGANISMO", CampoDestino.MICRO_MICROORGANISMO, TipoDatoExcel.TEXTO, false);
            crearMapeo(plantilla, "RESISTENCIA 1", CampoDestino.MICRO_RESISTENCIA, TipoDatoExcel.TEXTO, false);
            crearMapeo(plantilla, "RESISTENCIA_1", CampoDestino.MICRO_RESISTENCIA, TipoDatoExcel.TEXTO, false);
            crearMapeo(plantilla, "OTRA RESISTENCIA_1", CampoDestino.MICRO_OTRA_RESISTENCIA, TipoDatoExcel.TEXTO,
                    false);
        }

        // Mapeos ampliados: privacidad, seguimiento, medidas preventivas y
        // microbiología ampliada
        crearMapeoSiNoExiste(plantilla, "NOMBRE", CampoDestino.IGNORAR, TipoDatoExcel.TEXTO, false);
        crearMapeoSiNoExiste(plantilla, "Hombre",
                CampoDestino.IGNORAR, TipoDatoExcel.TEXTO, false);
        crearMapeoSiNoExiste(plantilla, "MULTIRRESISTENTES",
                CampoDestino.CIRUGIA_MULTIRRESISTENTES, TipoDatoExcel.BOOLEANO, false);
        crearMapeoSiNoExiste(plantilla, "FALTA REGISTRO/VALIDACIÓN DOSIS ADICIONALES",
                CampoDestino.PROFILAXIS_VALIDACION_DOSIS_ADICIONALES, TipoDatoExcel.TEXTO, false);
        crearMapeoSiNoExiste(plantilla, "EDAD - en meses si< de 2 años",
                CampoDestino.SEGUIMIENTO_EDAD_MESES_MENOR_2, TipoDatoExcel.ENTERO, false);
        crearMapeoSiNoExiste(plantilla, "EXITUS - en los 60 días tras cirugía",
                CampoDestino.SEGUIMIENTO_EXITUS_POST_CIRUGIA, TipoDatoExcel.BOOLEANO, false);
        crearMapeoSiNoExiste(plantilla, "EXITUS - en los 30 días tras cirugía",
                CampoDestino.SEGUIMIENTO_EXITUS_POST_CIRUGIA, TipoDatoExcel.BOOLEANO, false);
        crearMapeoSiNoExiste(plantilla, "MOTIVO ALTA", CampoDestino.SEGUIMIENTO_MOTIVO_ALTA, TipoDatoExcel.TEXTO,
                false);
        crearMapeoSiNoExiste(plantilla, "COMENTARIOS", CampoDestino.SEGUIMIENTO_COMENTARIOS, TipoDatoExcel.TEXTO,
                false);

        crearMapeoSiNoExiste(plantilla, "MONITORIZACIÓN CENTRAL TEMPERATURA",
                CampoDestino.PREVENTIVA_MONITORIZACION_TEMPERATURA, TipoDatoExcel.TEXTO, false);
        crearMapeoSiNoExiste(plantilla, "GLUCEMIA INTRAOPERATORIA",
                CampoDestino.PREVENTIVA_GLUCEMIA_INTRAOPERATORIA, TipoDatoExcel.TEXTO, false);
        crearMapeoSiNoExiste(plantilla, "ELIMINACIÓN DEL VELLO", CampoDestino.PREVENTIVA_ELIMINACION_VELLO,
                TipoDatoExcel.TEXTO, false);
        crearMapeoSiNoExiste(plantilla, "MOMENTO DE ELIMINACIÓN DEL VELLO",
                CampoDestino.PREVENTIVA_MOMENTO_ELIMINACION_VELLO, TipoDatoExcel.TEXTO, false);
        crearMapeoSiNoExiste(plantilla, "TÉCNICA ELIMINACIÓN DEL VELLO",
                CampoDestino.PREVENTIVA_TECNICA_ELIMINACION_VELLO, TipoDatoExcel.TEXTO, false);
        crearMapeoSiNoExiste(plantilla, "OTRA TÉCNICA DE ELIMINACIÓN DEL VELLO",
                CampoDestino.PREVENTIVA_OTRA_TECNICA_ELIMINACION_VELLO, TipoDatoExcel.TEXTO, false);
        crearMapeoSiNoExiste(plantilla, "ANTISEPSIA DE PIEL", CampoDestino.PREVENTIVA_ANTISEPSIA_PIEL,
                TipoDatoExcel.TEXTO, false);

        crearMapeoSiNoExiste(plantilla, "OTRA MUESTRA", CampoDestino.MICRO_OTRA_MUESTRA, TipoDatoExcel.TEXTO,
                false);

        if ("GASTRO_ESOF_2026".equals(plantilla.getCodigo())) {
            marcarComoNoObligatoria(plantilla, "PROFILAXIS ANTIBIÓTICA PRESCRITA EN DRAGO");
            marcarComoNoObligatoria(plantilla, "ADECUACIÓN DE PROFILAXIS");
            marcarComoNoObligatoria(plantilla, "INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA");
        }
    }

    private void marcarComoNoObligatoria(PlantillaExcel plantilla, String nombreColumnaExcel) {
        mapeoColumnaExcelRepository
                .findByPlantillaIdAndNombreColumnaExcelIgnoreCase(
                        plantilla.getId(),
                        nombreColumnaExcel)
                .ifPresent(mapeo -> {
                    if (Boolean.TRUE.equals(mapeo.getObligatoria())) {
                        mapeo.setObligatoria(false);
                        mapeoColumnaExcelRepository.save(mapeo);
                    }
                });
    }

    private void crearMapeo(
            PlantillaExcel plantilla,
            String nombreColumnaExcel,
            CampoDestino campoDestino,
            TipoDatoExcel tipoDato,
            boolean obligatoria) {
        MapeoColumnaExcel mapeo = MapeoColumnaExcel.builder()
                .plantilla(plantilla)
                .nombreColumnaExcel(nombreColumnaExcel)
                .campoDestino(campoDestino)
                .tipoDato(tipoDato)
                .obligatoria(obligatoria)
                .activa(true)
                .build();

        mapeoColumnaExcelRepository.save(mapeo);
    }

    private void crearMapeoSiNoExiste(
            PlantillaExcel plantilla,
            String nombreColumnaExcel,
            CampoDestino campoDestino,
            TipoDatoExcel tipoDato,
            boolean obligatoria) {
        boolean existe = mapeoColumnaExcelRepository
                .existsByPlantillaIdAndNombreColumnaExcelIgnoreCase(
                        plantilla.getId(),
                        nombreColumnaExcel);

        if (!existe) {
            MapeoColumnaExcel mapeo = MapeoColumnaExcel.builder()
                    .plantilla(plantilla)
                    .nombreColumnaExcel(nombreColumnaExcel)
                    .campoDestino(campoDestino)
                    .tipoDato(tipoDato)
                    .obligatoria(obligatoria)
                    .activa(true)
                    .build();

            mapeoColumnaExcelRepository.save(mapeo);
        }
    }
}