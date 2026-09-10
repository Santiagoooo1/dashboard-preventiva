package com.preventiva.backend;

import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.CampoClinicoResponseDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.service.interfaces.CampoClinicoService;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
import com.preventiva.backend.util.CatalogoColumnasClinicas;
import com.preventiva.backend.util.CatalogoColumnasClinicas.ColumnaCanonica;
import com.preventiva.backend.util.CatalogoColumnasClinicas.OrigenReconocimiento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 6.9M — reconocimiento de columnas clínicas por concepto, no por
 * coincidencia exacta.
 *
 * <p>Cada hospital escribe las mismas columnas de forma distinta. Lo que no
 * puede pasar es que una variante razonable acabe en el campo equivocado o con
 * el tipo equivocado: de ahí salió la pérdida de los casos de ILQ al importar
 * «LOCALIZACIÓN DE LA INFECCIÓN» como Sí/No.
 *
 * <p>La otra mitad de estos tests es la contraria y pesa igual: comprobar que
 * NO reconocemos de más. Precisión antes que cobertura.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReconocimientoColumnasClinicasIntegrationTest {

    private static final String PREFIJO = "TEST_69M_";
    private final AtomicInteger contador = new AtomicInteger();

    @Autowired private DatasetClinicoService datasetClinicoService;
    @Autowired private CampoClinicoService campoClinicoService;

    private ColumnaCanonica resolver(String cabecera) {
        Optional<ColumnaCanonica> c = CatalogoColumnasClinicas.resolver(cabecera);
        assertThat(c).as("no se reconoció «%s»", cabecera).isPresent();
        return c.get();
    }

    // ------------------------------------------------------------------
    // A–F: los alias de cada concepto
    // ------------------------------------------------------------------

    /** A: infección de localización quirúrgica → BOOLEANO. */
    @ParameterizedTest
    @ValueSource(strings = {
            "INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA",
            "INFECCION DE LOCALIZACION QUIRURGICA",
            "INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA (ILQ)",
            "INFECCIÓN LOCALIZACIÓN QUIRÚRGICA",
            "INFECCIÓN QUIRÚRGICA",
            "INFECCION QUIRURGICA",
            "ILQ",
    })
    void aliasDeInfeccionQuirurgica(String cabecera) {
        ColumnaCanonica c = resolver(cabecera);
        assertThat(c.codigo()).isEqualTo("infeccionLocalizacionQuirurgica");
        assertThat(c.tipoDato()).isEqualTo(TipoDatoExcel.BOOLEANO);
    }

    /** B: localización de la infección → TEXTO. Nunca Sí/No. */
    @ParameterizedTest
    @ValueSource(strings = {
            "LOCALIZACIÓN DE LA INFECCIÓN",
            "LOCALIZACION DE LA INFECCION",
            "LOCALIZACIÓN INFECCIÓN",
            "LOCALIZACIÓN ILQ",
            "LOCALIZACION ILQ",
            "LOCALIZACIÓN DE ILQ",
            "LOCALIZACION DE ILQ",
            "SITIO DE LA INFECCIÓN",
            "SITIO DE LA INFECCION",
            "TIPO DE ILQ",
    })
    void aliasDeLocalizacion(String cabecera) {
        ColumnaCanonica c = resolver(cabecera);
        assertThat(c.codigo()).isEqualTo("localizacionInfeccion");
        assertThat(c.tipoDato()).isEqualTo(TipoDatoExcel.TEXTO);
    }

    /** C: fecha de la infección → FECHA, con el código del modelo genérico. */
    @ParameterizedTest
    @ValueSource(strings = {
            "FECHA DE INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA",
            "FECHA DE INFECCION DE LOCALIZACION QUIRURGICA",
            "FECHA DE INFECCIÓN",
            "FECHA DE INFECCION",
            "FECHA INFECCIÓN",
            "FECHA ILQ",
            "FECHA DE ILQ",
    })
    void aliasDeFechaInfeccion(String cabecera) {
        ColumnaCanonica c = resolver(cabecera);
        assertThat(c.codigo()).isEqualTo("fechaInfeccion");
        assertThat(c.tipoDato()).isEqualTo(TipoDatoExcel.FECHA);
    }

    /** D: adecuación de profilaxis → TEXTO, aunque venga en SI/NO. */
    @ParameterizedTest
    @ValueSource(strings = {
            "ADECUACIÓN DE PROFILAXIS",
            "ADECUACION DE PROFILAXIS",
            "ADECUACIÓN PROFILAXIS",
            "ADECUACION PROFILAXIS",
            "ADECUACIÓN DE LA PROFILAXIS",
            "ADECUACIÓN DE LA PROFILAXIS ANTIBIÓTICA",
            "ADECUACION DE LA PROFILAXIS ANTIBIOTICA",
            "PROFILAXIS ADECUADA",
    })
    void aliasDeAdecuacion(String cabecera) {
        ColumnaCanonica c = resolver(cabecera);
        assertThat(c.codigo()).isEqualTo("adecuacionProfilaxis");
        assertThat(c.tipoDato()).isEqualTo(TipoDatoExcel.TEXTO);
    }

    /** E: motivos de inadecuación → TEXTO. */
    @ParameterizedTest
    @ValueSource(strings = {
            "MOTIVOS DE INADECUACIÓN",
            "MOTIVOS DE INADECUACION",
            "MOTIVO DE INADECUACIÓN",
            "MOTIVO DE INADECUACION",
            "MOTIVOS INADECUACIÓN",
            "MOTIVOS DE INADECUACIÓN DE PROFILAXIS",
            "CAUSA DE INADECUACIÓN",
            "CAUSA DE INADECUACION",
            "MOTIVO PROFILAXIS INADECUADA",
    })
    void aliasDeMotivos(String cabecera) {
        ColumnaCanonica c = resolver(cabecera);
        assertThat(c.codigo()).isEqualTo("motivoInadecuacionProfilaxis");
        assertThat(c.tipoDato()).isEqualTo(TipoDatoExcel.TEXTO);
    }

    /** F: la fecha del evento principal en su versión quirúrgica. */
    @ParameterizedTest
    @ValueSource(strings = {
            "FECHA CIRUGÍA",
            "FECHA CIRUGIA",
            "FECHA DE CIRUGÍA",
            "FECHA DE LA CIRUGÍA",
            "FECHA INTERVENCIÓN",
            "FECHA DE INTERVENCIÓN",
            "FECHA DE LA INTERVENCIÓN",
            "FECHA PROCEDIMIENTO",
            "FECHA DEL PROCEDIMIENTO",
    })
    void aliasDeFechaEvento(String cabecera) {
        ColumnaCanonica c = resolver(cabecera);
        assertThat(c.codigo()).isEqualTo("fechaEvento");
        assertThat(c.tipoDato()).isEqualTo(TipoDatoExcel.FECHA);
        assertThat(c.esComun()).as("la fecha del evento es un campo común").isTrue();
    }

    // ------------------------------------------------------------------
    // G: cómo esté escrito da igual
    // ------------------------------------------------------------------

    /** G: tildes, mayúsculas, guiones bajos, guiones y espacios de más. */
    @ParameterizedTest
    @ValueSource(strings = {
            "LOCALIZACIÓN DE LA INFECCIÓN",
            "localización de la infección",
            "Localizacion De La Infeccion",
            "LOCALIZACION_DE_LA_INFECCION",
            "LOCALIZACIÓN-DE-LA-INFECCIÓN",
            "  LOCALIZACIÓN   DE  LA   INFECCIÓN  ",
            "Localización.de.la.infección",
            "LOCALIZACIÓN: DE LA INFECCIÓN",
            "(LOCALIZACIÓN DE LA INFECCIÓN)",
    })
    void mismaColumnaEscritaDeCualquierManera(String cabecera) {
        assertThat(resolver(cabecera).codigo()).isEqualTo("localizacionInfeccion");
    }

    /** El nombre que ve el usuario no se toca: la normalización solo compara. */
    @Test
    void laNormalizacionNoAlteraElNombreOriginal() {
        String original = "  Localización   de la Infección  ";
        assertThat(CatalogoColumnasClinicas.normalizar(original)).isEqualTo("LOCALIZACION DE LA INFECCION");
        assertThat(original).isEqualTo("  Localización   de la Infección  ");
    }

    // ------------------------------------------------------------------
    // H: lo que NO debe reconocerse
    // ------------------------------------------------------------------

    /**
     * H: columnas que se parecen pero son otra cosa. Ninguna debe resolverse:
     * caen en las reglas genéricas, que es lo correcto ante la duda.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "LOCALIZACIÓN DEL HOSPITAL",
            "LOCALIZACIÓN DEL PACIENTE",
            "FECHA DE NACIMIENTO",
            "FECHA INGRESO",
            "FECHA ALTA",
            "FECHA REINGRESO",
            "FECHA FIN DE VIGILANCIA",
            "INFECCIÓN URINARIA",
            "INFECCIÓN RESPIRATORIA",
            "TIPO DE INFECCIÓN RESPIRATORIA",
            "MOTIVO DE INGRESO",
            "MOTIVO ALTA",
            "ADECUACIÓN DEL TRATAMIENTO",
            "CULTIVO DE ILQ",
            "REINGRESO POR ILQ",
            "ANTISEPSIA DE PIEL",
            "FECHA",
    })
    void columnasQueNoDebenConfundirse(String cabecera) {
        assertThat(CatalogoColumnasClinicas.resolver(cabecera))
                .as("«%s» no debe resolverse a ningún concepto clínico", cabecera)
                .isEmpty();
    }

    /** «ILQ» solo se reconoce como cabecera completa, nunca como subcadena. */
    @Test
    void ilqSoloComoCabeceraCompleta() {
        assertThat(resolver("ILQ").codigo()).isEqualTo("infeccionLocalizacionQuirurgica");
        assertThat(CatalogoColumnasClinicas.resolver("CULTIVO DE ILQ")).isEmpty();
        assertThat(CatalogoColumnasClinicas.resolver("REINGRESO por ILQ")).isEmpty();
        assertThat(CatalogoColumnasClinicas.resolver("Nº ILQ ACUMULADAS")).isEmpty();
    }

    @Test
    void entradasVaciasONulas_noSeReconocen() {
        assertThat(CatalogoColumnasClinicas.resolver(null)).isEmpty();
        assertThat(CatalogoColumnasClinicas.resolver("")).isEmpty();
        assertThat(CatalogoColumnasClinicas.resolver("   ")).isEmpty();
    }

    // ------------------------------------------------------------------
    // I–J: el tipo lo fija el concepto, no los valores
    // ------------------------------------------------------------------

    /**
     * I y J: el tipo sale del significado de la columna. Los valores que traiga
     * hoy no cambian nada: «adecuación» con SI/NO sigue siendo categórica,
     * porque mañana traerá ADECUADA/INADECUADA/NO_APLICA.
     */
    @ParameterizedTest
    @CsvSource({
            "LOCALIZACIÓN DE LA INFECCIÓN, localizacionInfeccion, TEXTO",
            "ADECUACIÓN DE PROFILAXIS,     adecuacionProfilaxis,  TEXTO",
            "INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA, infeccionLocalizacionQuirurgica, BOOLEANO",
            "FECHA DE INFECCIÓN,           fechaInfeccion,        FECHA",
    })
    void elTipoLoFijaElConcepto(String cabecera, String codigo, TipoDatoExcel tipo) {
        ColumnaCanonica c = resolver(cabecera);
        assertThat(c.codigo()).isEqualTo(codigo);
        assertThat(c.tipoDato()).isEqualTo(tipo);
    }

    /** Las tres columnas con la palabra «infección» conservan tres tipos distintos. */
    @Test
    void lasTresColumnasDeInfeccion_noSeAplanan() {
        assertThat(List.of(
                resolver("INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA").tipoDato(),
                resolver("LOCALIZACIÓN DE LA INFECCIÓN").tipoDato(),
                resolver("FECHA DE INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA").tipoDato()))
                .containsExactly(TipoDatoExcel.BOOLEANO, TipoDatoExcel.TEXTO, TipoDatoExcel.FECHA);
    }

    // ------------------------------------------------------------------
    // K: dos columnas, un concepto
    // ------------------------------------------------------------------

    /**
     * K: dos cabeceras distintas del mismo archivo pueden resolver al mismo
     * código. Se detecta ANTES de persistir, porque crear los dos campos
     * repartiría el dato entre ambos y los gráficos saldrían incompletos.
     */
    @Test
    void dosCabecerasDelMismoConcepto_seDetectanAntesDePersistir() {
        String a = "LOCALIZACIÓN ILQ";
        String b = "LOCALIZACIÓN DE LA INFECCIÓN";

        assertThat(resolver(a).codigo()).isEqualTo(resolver(b).codigo());

        // Y el segundo intento de crearlo choca con la unicidad, que sigue viva:
        // por eso hay que avisar antes, no descubrirlo con un error.
        Long datasetId = crearDataset();
        campoClinicoService.crear(datasetId, peticion(resolver(a), a));
        assertThat(campoClinicoService.listarPorDataset(datasetId))
                .filteredOn(c -> "localizacionInfeccion".equals(c.getCodigo()))
                .hasSize(1);
    }

    // ------------------------------------------------------------------
    // L–M: lo que llega a la base y lo que no se toca al reanudar
    // ------------------------------------------------------------------

    /** L: los campos se persisten con el código y el tipo del catálogo. */
    @Test
    void unaImportacionNueva_persisteCodigosYTiposCorrectos() {
        Long datasetId = crearDataset();

        // Cabeceras de otro hospital: ninguna idéntica a las del archivo real.
        List<CampoClinicoResponseDto> campos = campoClinicoService.asegurarParaImportacion(datasetId, List.of(
                peticion(resolver("FECHA DE INTERVENCIÓN"), "FECHA DE INTERVENCIÓN"),
                peticion(resolver("ILQ"), "ILQ"),
                peticion(resolver("SITIO DE LA INFECCIÓN"), "SITIO DE LA INFECCIÓN"),
                peticion(resolver("ADECUACIÓN PROFILAXIS"), "ADECUACIÓN PROFILAXIS"),
                peticion(resolver("CAUSA DE INADECUACIÓN"), "CAUSA DE INADECUACIÓN")));

        assertThat(campos).extracting(CampoClinicoResponseDto::getCodigo)
                .containsExactly("fechaEvento", "infeccionLocalizacionQuirurgica",
                        "localizacionInfeccion", "adecuacionProfilaxis", "motivoInadecuacionProfilaxis");
        assertThat(campos).extracting(CampoClinicoResponseDto::getTipoDato)
                .containsExactly("FECHA", "BOOLEANO", "TEXTO", "TEXTO", "TEXTO");
    }

    /** M: reanudar vuelve a declarar lo mismo y no cambia ningún tipo ni id. */
    @Test
    void reanudar_noCambiaLosTiposYaPersistidos() {
        Long datasetId = crearDataset();
        List<CampoClinicoRequestDto> peticion = List.of(
                peticion(resolver("ILQ"), "ILQ"),
                peticion(resolver("SITIO DE LA INFECCIÓN"), "SITIO DE LA INFECCIÓN"));

        List<CampoClinicoResponseDto> primera = campoClinicoService.asegurarParaImportacion(datasetId, peticion);
        List<CampoClinicoResponseDto> segunda = campoClinicoService.asegurarParaImportacion(datasetId, peticion);

        assertThat(segunda).extracting(CampoClinicoResponseDto::getId)
                .isEqualTo(primera.stream().map(CampoClinicoResponseDto::getId).toList());
        assertThat(segunda).extracting(CampoClinicoResponseDto::getTipoDato)
                .containsExactly("BOOLEANO", "TEXTO");
    }

    /**
     * M (continuación): sustituir el archivo por otro que escribe las columnas
     * de otra forma reutiliza los campos existentes, no crea unos paralelos.
     */
    @Test
    void sustituirArchivoConOtrasCabeceras_reutilizaElMismoCampo() {
        Long datasetId = crearDataset();
        List<CampoClinicoResponseDto> antes = campoClinicoService.asegurarParaImportacion(
                datasetId, List.of(peticion(resolver("LOCALIZACIÓN DE LA INFECCIÓN"), "LOCALIZACIÓN DE LA INFECCIÓN")));

        // Archivo nuevo, otra redacción, mismo concepto.
        List<CampoClinicoResponseDto> despues = campoClinicoService.asegurarParaImportacion(
                datasetId, List.of(peticion(resolver("SITIO DE LA INFECCIÓN"), "SITIO DE LA INFECCIÓN")));

        assertThat(despues).singleElement()
                .satisfies(c -> assertThat(c.getId()).isEqualTo(antes.get(0).getId()));
        assertThat(campoClinicoService.listarPorDataset(datasetId)).hasSize(1);
    }

    // ------------------------------------------------------------------
    // N: lo desconocido sigue como siempre
    // ------------------------------------------------------------------

    /** N: una columna que el catálogo no conoce no recibe nada y usa el fallback. */
    @Test
    void columnaDesconocida_seDejaALasReglasGenericas() {
        assertThat(CatalogoColumnasClinicas.resolver("GLUCEMIA INTRAOPERATORIA")).isEmpty();
        assertThat(CatalogoColumnasClinicas.resolver("NNIS p75 (120 min o más)")).isEmpty();
        assertThat(CatalogoColumnasClinicas.resolver("OTRA TÉCNICA DE ELIMINACIÓN DEL VELLO")).isEmpty();
    }

    // ------------------------------------------------------------------
    // Trazabilidad del reconocimiento
    // ------------------------------------------------------------------

    /** Se distingue la denominación principal de sus variantes. */
    @Test
    void seSabePorQueSeReconocioLaColumna() {
        assertThat(resolver("INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA").origen())
                .isEqualTo(OrigenReconocimiento.CANONICA_EXACTA);
        assertThat(resolver("ILQ").origen()).isEqualTo(OrigenReconocimiento.ALIAS);
        assertThat(resolver("SITIO DE LA INFECCIÓN").origen()).isEqualTo(OrigenReconocimiento.ALIAS);
    }

    /** Una sigla ilegible trae una etiqueta recomendada; el resto también la tiene. */
    @Test
    void cadaConceptoTraeUnaEtiquetaLegible() {
        assertThat(resolver("ILQ").etiquetaRecomendada()).isEqualTo("Infección de localización quirúrgica");
        assertThat(resolver("SITIO DE LA INFECCIÓN").etiquetaRecomendada())
                .isEqualTo("Localización de la infección");
    }

    /** Ningún nombre está registrado en dos conceptos a la vez. */
    @Test
    void ningunNombreApuntaADosConceptos() {
        List<String> codigos = CatalogoColumnasClinicas.codigosCanonicos();
        assertThat(codigos).doesNotHaveDuplicates();

        long totalNombres = codigos.stream()
                .mapToLong(c -> CatalogoColumnasClinicas.nombresDe(c).size())
                .sum();
        long nombresDistintos = codigos.stream()
                .flatMap(c -> CatalogoColumnasClinicas.nombresDe(c).stream())
                .distinct()
                .count();
        assertThat(nombresDistintos).isEqualTo(totalNombres);
    }

    /** Los códigos siguen siendo los que buscan los indicadores clínicos. */
    @Test
    void losCodigos_siguenCoincidiendoConLosDelDashboard() {
        assertThat(CatalogoColumnasClinicas.codigosCanonicos())
                .contains("infeccionLocalizacionQuirurgica", "localizacionInfeccion",
                        "adecuacionProfilaxis", "motivoInadecuacionProfilaxis",
                        "fechaInfeccion", "fechaEvento")
                .contains(com.preventiva.backend.util.BloqueInicialIlq.CAMPO_IMPRESCINDIBLE);
    }

    // ------------------------------------------------------------------

    private Long crearDataset() {
        DatasetClinicoRequestDto r = new DatasetClinicoRequestDto();
        r.setCodigo(PREFIJO + contador.incrementAndGet() + "_" + System.nanoTime());
        r.setNombre("Test 69M");
        return datasetClinicoService.crear(r).getId();
    }

    private CampoClinicoRequestDto peticion(ColumnaCanonica canonica, String cabecera) {
        CampoClinicoRequestDto r = new CampoClinicoRequestDto();
        r.setCodigo(canonica.codigo());
        r.setEtiqueta(cabecera);
        r.setTipoDato(canonica.tipoDato());
        r.setEsComun(canonica.esComun());
        r.setObligatorio(false);
        return r;
    }
}
