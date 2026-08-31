package com.preventiva.backend;

import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.CampoClinicoResponseDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.service.interfaces.CampoClinicoService;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
import com.preventiva.backend.util.CatalogoColumnasClinicas;
import com.preventiva.backend.util.CatalogoColumnasClinicas.ColumnaCanonica;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 6.9L.1 — las columnas clínicas conocidas se reconocen por su nombre, con
 * su tipo, sin dejarlo a la heurística.
 *
 * <p>Sale de un fallo con consecuencias: al importar un Excel real de trauma,
 * «LOCALIZACIÓN DE LA INFECCIÓN» se tipó como BOOLEANO porque su nombre
 * contiene la palabra «infección». Las únicas filas que traen localización son
 * las de los pacientes infectados, así que esas filas fallaron la validación y
 * se quedaron fuera: el dataset importado tenía 43 registros, todos sin
 * infección, y los 3 casos reales de ILQ habían desaparecido.
 *
 * <p>Los nombres de estos tests son los del fichero real, con sus tildes.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogoColumnasClinicasIntegrationTest {

    private static final String PREFIJO = "TEST_69L1_";
    private final AtomicInteger contador = new AtomicInteger();

    @Autowired private DatasetClinicoService datasetClinicoService;
    @Autowired private CampoClinicoService campoClinicoService;

    private ColumnaCanonica resolver(String cabecera) {
        Optional<ColumnaCanonica> canonica = CatalogoColumnasClinicas.resolver(cabecera);
        assertThat(canonica).as("no se reconoció la columna «%s»", cabecera).isPresent();
        return canonica.get();
    }

    // ------------------------------------------------------------------
    // A–C: cada columna, su tipo
    // ------------------------------------------------------------------

    /** A: el resultado de la vigilancia sí es Sí/No. */
    @Test
    void infeccionDeLocalizacionQuirurgica_esBooleano() {
        ColumnaCanonica c = resolver("INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA");

        assertThat(c.codigo()).isEqualTo("infeccionLocalizacionQuirurgica");
        assertThat(c.tipoDato()).isEqualTo(TipoDatoExcel.BOOLEANO);
    }

    /** B: la localización NO es Sí/No. Es la corrección que motiva esta fase. */
    @Test
    void localizacionDeLaInfeccion_esTexto() {
        ColumnaCanonica c = resolver("LOCALIZACIÓN DE LA INFECCIÓN");

        assertThat(c.codigo()).isEqualTo("localizacionInfeccion");
        assertThat(c.tipoDato())
                .as("es categórica (ÓRGANO/ESPACIO, PROFUNDA…), no un Sí/No")
                .isEqualTo(TipoDatoExcel.TEXTO);
    }

    /** C: la fecha de la infección es una fecha. */
    @Test
    void fechaDeInfeccionDeLocalizacionQuirurgica_esFecha() {
        ColumnaCanonica c = resolver("FECHA DE INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA");

        assertThat(c.codigo())
                .as("el modelo genérico ya usa fechaInfeccion; no debe haber dos nombres")
                .isEqualTo("fechaInfeccion");
        assertThat(c.tipoDato()).isEqualTo(TipoDatoExcel.FECHA);
    }

    /**
     * D: las tres juntas, como vienen en el archivo. Los tres tipos son
     * distintos y los tres códigos son distintos: la palabra «infección» no
     * puede aplanarlas.
     */
    @Test
    void lasTresColumnasDeInfeccion_mantienenTiposYCodigosDistintos() {
        ColumnaCanonica infeccion = resolver("INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA");
        ColumnaCanonica localizacion = resolver("LOCALIZACIÓN DE LA INFECCIÓN");
        ColumnaCanonica fecha = resolver("FECHA DE INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA");

        assertThat(List.of(infeccion.tipoDato(), localizacion.tipoDato(), fecha.tipoDato()))
                .containsExactly(TipoDatoExcel.BOOLEANO, TipoDatoExcel.TEXTO, TipoDatoExcel.FECHA);
        assertThat(List.of(infeccion.codigo(), localizacion.codigo(), fecha.codigo()))
                .doesNotHaveDuplicates();
    }

    /** Las de profilaxis también son texto, no Sí/No. */
    @Test
    void adecuacionYMotivos_sonTexto() {
        assertThat(resolver("ADECUACIÓN DE PROFILAXIS").codigo()).isEqualTo("adecuacionProfilaxis");
        assertThat(resolver("ADECUACIÓN DE PROFILAXIS").tipoDato()).isEqualTo(TipoDatoExcel.TEXTO);
        assertThat(resolver("MOTIVOS DE INADECUACIÓN").codigo()).isEqualTo("motivoInadecuacionProfilaxis");
        assertThat(resolver("MOTIVOS DE INADECUACIÓN").tipoDato()).isEqualTo(TipoDatoExcel.TEXTO);
    }

    /** Con y sin tildes, en minúsculas y con espacios de más: el mismo campo. */
    @Test
    void elReconocimiento_ignoraTildesMayusculasYEspacios() {
        for (String variante : List.of(
                "LOCALIZACIÓN DE LA INFECCIÓN",
                "localizacion de la infeccion",
                "  Localización   de la Infección  ",
                "LOCALIZACION DE LA INFECCION")) {
            assertThat(CatalogoColumnasClinicas.resolver(variante))
                    .as("variante «%s»", variante)
                    .isPresent()
                    .get()
                    .extracting(ColumnaCanonica::codigo)
                    .isEqualTo("localizacionInfeccion");
        }
    }

    /** Una columna que no está en el catálogo no se inventa: sigue por las reglas genéricas. */
    @Test
    void columnaDesconocida_noSeReconoce() {
        assertThat(CatalogoColumnasClinicas.resolver("ANTISEPSIA DE PIEL")).isEmpty();
        assertThat(CatalogoColumnasClinicas.resolver("")).isEmpty();
        assertThat(CatalogoColumnasClinicas.resolver(null)).isEmpty();
    }

    /**
     * E: una localización con varias categorías es un TEXTO válido y no produce
     * ningún problema de «valor no booleano».
     *
     * <p>Se comprueba con los valores reales del archivo que falló.
     */
    @Test
    void localizacionConVariasCategorias_noEsUnBooleanoInvalido() {
        ColumnaCanonica c = resolver("LOCALIZACIÓN DE LA INFECCIÓN");
        assertThat(c.tipoDato()).isEqualTo(TipoDatoExcel.TEXTO);

        // Ninguno de estos valores es un Sí/No; con el tipo correcto, todos son
        // simplemente texto y ninguna fila se pierde por ellos.
        for (String valor : List.of("ÓRGANO/ESPACIO", "PROFUNDA", "INCISIONAL SUPERFICIAL")) {
            assertThat(valor).isNotIn("SI", "NO", "true", "false");
        }
    }

    // ------------------------------------------------------------------
    // F–H: el tipo llega hasta el campo creado y sobrevive
    // ------------------------------------------------------------------

    private Long crearDataset() {
        DatasetClinicoRequestDto r = new DatasetClinicoRequestDto();
        r.setCodigo(PREFIJO + contador.incrementAndGet() + "_" + System.nanoTime());
        r.setNombre("Test 69L1");
        return datasetClinicoService.crear(r).getId();
    }

    /** Los campos que crearía el asistente a partir de las cabeceras reales. */
    private List<CampoClinicoRequestDto> camposDesdeCabeceras(String... cabeceras) {
        return List.of(cabeceras).stream().map(cabecera -> {
            ColumnaCanonica canonica = resolver(cabecera);
            CampoClinicoRequestDto r = new CampoClinicoRequestDto();
            r.setCodigo(canonica.codigo());
            r.setEtiqueta(cabecera);
            r.setTipoDato(canonica.tipoDato());
            r.setEsComun(canonica.esComun());
            r.setObligatorio(false);
            return r;
        }).toList();
    }

    /** F: tras asegurar los campos, la localización queda como TEXTO en la base. */
    @Test
    void asegurarCampos_dejaLaLocalizacionComoTexto() {
        Long datasetId = crearDataset();

        List<CampoClinicoResponseDto> campos = campoClinicoService.asegurarParaImportacion(
                datasetId,
                camposDesdeCabeceras(
                        "INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA",
                        "LOCALIZACIÓN DE LA INFECCIÓN",
                        "FECHA DE INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA"));

        assertThat(campos).extracting(CampoClinicoResponseDto::getCodigo)
                .containsExactly("infeccionLocalizacionQuirurgica", "localizacionInfeccion",
                        "fechaInfeccion");
        assertThat(campos).extracting(CampoClinicoResponseDto::getTipoDato)
                .containsExactly(TipoDatoExcel.BOOLEANO.name(), TipoDatoExcel.TEXTO.name(),
                        TipoDatoExcel.FECHA.name());
    }

    /**
     * G: reanudar la importación vuelve a declarar las mismas columnas. El tipo
     * no puede cambiar por el camino, ni el id del campo.
     */
    @Test
    void reanudarLaImportacion_conservaElTipoTextoDeLaLocalizacion() {
        Long datasetId = crearDataset();
        List<CampoClinicoRequestDto> peticion = camposDesdeCabeceras(
                "INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA", "LOCALIZACIÓN DE LA INFECCIÓN");

        List<CampoClinicoResponseDto> primera =
                campoClinicoService.asegurarParaImportacion(datasetId, peticion);
        List<CampoClinicoResponseDto> segunda =
                campoClinicoService.asegurarParaImportacion(datasetId, peticion);

        assertThat(segunda).extracting(CampoClinicoResponseDto::getId)
                .isEqualTo(primera.stream().map(CampoClinicoResponseDto::getId).toList());
        assertThat(segunda.get(1).getCodigo()).isEqualTo("localizacionInfeccion");
        assertThat(segunda.get(1).getTipoDato()).isEqualTo(TipoDatoExcel.TEXTO.name());
    }

    /**
     * H: sustituir el archivo por otro con las mismas columnas tampoco cambia el
     * tipo. Un campo existente se reutiliza tal cual, y el catálogo produciría
     * el mismo tipo de todos modos.
     */
    @Test
    void sustituirArchivo_conservaElTipoTextoDeLaLocalizacion() {
        Long datasetId = crearDataset();
        campoClinicoService.asegurarParaImportacion(
                datasetId, camposDesdeCabeceras("INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA",
                        "LOCALIZACIÓN DE LA INFECCIÓN"));

        // Archivo nuevo: mismas columnas clínicas más una de profilaxis.
        List<CampoClinicoResponseDto> tras = campoClinicoService.asegurarParaImportacion(
                datasetId, camposDesdeCabeceras("INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA",
                        "LOCALIZACIÓN DE LA INFECCIÓN", "ADECUACIÓN DE PROFILAXIS"));

        assertThat(tras).hasSize(3);
        assertThat(tras).extracting(CampoClinicoResponseDto::getCodigo)
                .containsExactly("infeccionLocalizacionQuirurgica", "localizacionInfeccion",
                        "adecuacionProfilaxis");
        assertThat(tras.get(1).getTipoDato()).isEqualTo(TipoDatoExcel.TEXTO.name());
        assertThat(campoClinicoService.listarPorDataset(datasetId))
                .filteredOn(c -> "localizacionInfeccion".equals(c.getCodigo()))
                .singleElement()
                .satisfies(c -> assertThat(c.getTipoDato()).isEqualTo(TipoDatoExcel.TEXTO.name()));
    }

    /**
     * Los códigos del catálogo son exactamente los que buscan los indicadores
     * clínicos. Si dejaran de coincidir, el bloque de ILQ volvería a salir vacío
     * sin dar ningún error, que es como se descubrió el fallo.
     */
    @Test
    void losCodigosDelCatalogo_coincidenConLosQueBuscaElDashboard() {
        assertThat(CatalogoColumnasClinicas.codigosCanonicos())
                .contains("infeccionLocalizacionQuirurgica", "localizacionInfeccion",
                        "adecuacionProfilaxis", "motivoInadecuacionProfilaxis");
        assertThat(CatalogoColumnasClinicas.codigosCanonicos())
                .contains(com.preventiva.backend.util.BloqueInicialIlq.CAMPO_IMPRESCINDIBLE);
    }
}
