package com.pic2filament.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.pic2filament.StlChecks;
import com.pic2filament.TestImages;
import com.pic2filament.core.export.ZipExporterTest;

@SpringBootTest(properties = { "pic2filament.max-upload-size=400KB",
        "pic2filament.temp-dir=${java.io.tmpdir}/pic2filament-test" })
@AutoConfigureMockMvc
class ApiIntegrationTest {

    static final String SETTINGS = """
            {"anchoMm":60,"mmPorPixel":0.5,"alturaCapa":0.08,"alturaPrimeraCapa":0.16,"capasBase":4,"capasTotales":28,
             "filamentos":[{"nombre":"Negro","color":"#111111","td":0.6},{"nombre":"Rojo","color":"#c0262b","td":2.5},
                           {"nombre":"Azul","color":"#1e4fb4","td":1.5},{"nombre":"Blanco","color":"#f7f7f5","td":4.5}],
             "criterio":"color","contraste":1,"brillo":0,"reducirRuido":true,"optimizar":true}
            """;

    @Autowired
    MockMvc mvc;

    String upload(byte[] data, String name, String type) throws Exception {
        MvcResult r = mvc.perform(multipart("/api/trabajos").file(new MockMultipartFile("imagen", name, type, data)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nombre").value(name)).andReturn();
        Matcher m = Pattern.compile("\"id\":\"([0-9a-f]{32})\"").matcher(r.getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    String uploadAndProcess() throws Exception {
        String id = upload(TestImages.png(TestImages.gradient(160, 120)), "degradado.png", "image/png");
        mvc.perform(post("/api/trabajos/{id}/procesar", id).contentType(MediaType.APPLICATION_JSON).content(SETTINGS))
                .andExpect(status().isOk());
        return id;
    }

    @Test
    void laPaginaPrincipalCargaConBootstrapLocal() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("pic2filament")))
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern("(?s).*/webjars/bootstrap/[0-9.]+/css/bootstrap\\.min\\.css.*")))
                .andExpect(content().string(containsString("Detectar colores de la imagen")))
                .andExpect(content().string(containsString("lang=\"es\"")));
        mvc.perform(get("/webjars/bootstrap/css/bootstrap.min.css")).andExpect(status().isOk());
        mvc.perform(get("/webjars/bootstrap/js/bootstrap.bundle.min.js")).andExpect(status().isOk());
        mvc.perform(get("/js/app.js")).andExpect(status().isOk());
    }

    @Test
    void laBibliotecaDeFilamentosTieneLosPredefinidos() throws Exception {
        mvc.perform(get("/api/filamentos")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Negro')].td").value(0.6))
                .andExpect(jsonPath("$[?(@.nombre == 'Azul cielo')]").exists())
                .andExpect(jsonPath("$", hasSize(15)));
    }

    @Test
    void subirProcesarYVistaPrevia() throws Exception {
        String id = upload(TestImages.png(TestImages.gradient(160, 120)), "degradado.png", "image/png");
        mvc.perform(post("/api/trabajos/{id}/procesar", id).contentType(MediaType.APPLICATION_JSON).content(SETTINGS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anchoMm").value(60.0))
                .andExpect(jsonPath("$.columnas").value(121))
                .andExpect(jsonPath("$.filas").value(91))
                .andExpect(jsonPath("$.cambios", hasSize(4)))
                .andExpect(jsonPath("$.cambios[0].capaInicio").value(1))
                .andExpect(jsonPath("$.cambios[0].z").value(0.16))
                .andExpect(jsonPath("$.capas", hasSize(28)))
                .andExpect(jsonPath("$.capasInicio", hasSize(4)));

        for (String modo : new String[] { "simulacion", "relieve", "alturas", "original" }) {
            byte[] png = mvc.perform(get("/api/trabajos/{id}/vista/{modo}.png", id, modo)).andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.IMAGE_PNG)).andReturn().getResponse()
                    .getContentAsByteArray();
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            assertThat(img.getWidth()).isEqualTo(121);
            assertThat(img.getHeight()).isEqualTo(91);
        }
        mvc.perform(get("/api/trabajos/" + id + "/vista/otro.png")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("Modo de vista previa no válido")));

        mvc.perform(get("/trabajos/{id}/tabla-cambios", id)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Capa inicio")))
                .andExpect(content().string(containsString("Blanco")))
                .andExpect(content().string(containsString("0,16")));

        mvc.perform(post("/api/trabajos/{id}/colores", id).param("cantidad", "3")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3))).andExpect(jsonPath("$[0].color").exists())
                .andExpect(jsonPath("$[0].td").exists());
    }

    @Test
    void lasDescargasDevuelvenZipStlY3mf() throws Exception {
        String id = uploadAndProcess();

        MvcResult zipReq = mvc.perform(get("/api/trabajos/{id}/descarga.zip", id))
                .andExpect(request().asyncStarted()).andReturn();
        byte[] zip = mvc.perform(asyncDispatch(zipReq)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("degradado.zip")))
                .andReturn().getResponse().getContentAsByteArray();
        Map<String, byte[]> entries = ZipExporterTest.unzip(zip);
        assertThat(entries).containsOnlyKeys("degradado.stl", "degradado-vista-previa.png", "degradado-cambios.txt");
        StlChecks.assertWatertight(StlChecks.parse(entries.get("degradado.stl")));

        MvcResult stlReq = mvc.perform(get("/api/trabajos/{id}/descarga.stl", id))
                .andExpect(request().asyncStarted()).andReturn();
        byte[] stl = mvc.perform(asyncDispatch(stlReq)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("degradado.stl")))
                .andReturn().getResponse().getContentAsByteArray();
        StlChecks.Stl parsed = StlChecks.parse(stl);
        assertThat(parsed.bounds()[3]).isCloseTo(60, org.assertj.core.api.Assertions.within(1e-3));

        MvcResult mfReq = mvc.perform(get("/api/trabajos/{id}/descarga.3mf", id))
                .andExpect(request().asyncStarted()).andReturn();
        byte[] mf = mvc.perform(asyncDispatch(mfReq)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsByteArray();
        Map<String, byte[]> mfEntries = ZipExporterTest.unzip(mf);
        assertThat(mfEntries).containsKeys("3D/3dmodel.model", "[Content_Types].xml", "_rels/.rels",
                "Metadata/Prusa_Slicer_custom_gcode_per_print_z.xml", "Metadata/custom_gcode_per_layer.xml");
        assertThat(new String(mfEntries.get("Metadata/Prusa_Slicer_custom_gcode_per_print_z.xml")))
                .contains("type=\"0\"").contains("M600");
    }

    @Test
    void aceptaJpegYAplanaLaTransparencia() throws Exception {
        ByteArrayOutputStream jpg = new ByteArrayOutputStream();
        ImageIO.write(TestImages.gradient(80, 60), "jpg", jpg);
        upload(jpg.toByteArray(), "foto.jpg", "image/jpeg");

        BufferedImage transp = new BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB);
        String id = upload(TestImages.png(transp), "transparente.png", "image/png");
        mvc.perform(post("/api/trabajos/{id}/procesar", id).contentType(MediaType.APPLICATION_JSON).content(SETTINGS))
                .andExpect(status().isOk());
        byte[] png = mvc.perform(get("/api/trabajos/{id}/vista/original.png", id)).andReturn().getResponse()
                .getContentAsByteArray();
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(img.getRGB(3, 3) & 0xffffff).isEqualTo(0xffffff);
    }

    @Test
    void rechazaFicherosNoValidos() throws Exception {
        mvc.perform(multipart("/api/trabajos").file(
                new MockMultipartFile("imagen", "falsa.png", "image/png", "esto no es una imagen".getBytes())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.mensaje").value("Formato no admitido. Sube una imagen PNG, JPG o WebP."));

        ByteArrayOutputStream gif = new ByteArrayOutputStream();
        ImageIO.write(TestImages.gradient(10, 10), "gif", gif);
        mvc.perform(multipart("/api/trabajos").file(new MockMultipartFile("imagen", "a.gif", "image/gif",
                gif.toByteArray()))).andExpect(status().isUnsupportedMediaType());

        mvc.perform(multipart("/api/trabajos")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("No se ha recibido ninguna imagen."));
    }

    @Test
    void rechazaFicherosDemasiadoGrandes() throws Exception {
        byte[] big = new byte[500 * 1024];
        mvc.perform(multipart("/api/trabajos").file(new MockMultipartFile("imagen", "grande.png", "image/png", big)))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.estado").value(413))
                .andExpect(jsonPath("$.mensaje", containsString("tamaño máximo permitido (400 KB)")));
    }

    @Test
    void validaLosAjustesConMensajesEnEspanol() throws Exception {
        String id = upload(TestImages.png(TestImages.gradient(40, 30)), "a.png", "image/png");
        mvc.perform(post("/api/trabajos/{id}/procesar", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"filamentos\":[{\"color\":\"rojo\",\"td\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", org.hamcrest.Matchers.hasItems(
                        "El color debe tener el formato #RRGGBB", "Elige entre 2 y 4 filamentos")));

        mvc.perform(post("/api/trabajos/{id}/procesar", id).contentType(MediaType.APPLICATION_JSON)
                .content(SETTINGS.replace("\"capasBase\":4", "\"capasBase\":40")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Las capas totales deben ser más que las capas de base."));

        String manualMal = SETTINGS.replace("\"optimizar\":true", "\"optimizar\":false")
                .replace("\"td\":2.5}", "\"td\":2.5,\"capaInicio\":10}")
                .replace("\"td\":1.5}", "\"td\":1.5,\"capaInicio\":8}")
                .replace("\"td\":4.5}", "\"td\":4.5,\"capaInicio\":20}");
        mvc.perform(post("/api/trabajos/{id}/procesar", id).contentType(MediaType.APPLICATION_JSON).content(manualMal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("crecientes")));

        String manualBien = manualMal.replace("\"capaInicio\":8", "\"capaInicio\":14");
        mvc.perform(post("/api/trabajos/{id}/procesar", id).contentType(MediaType.APPLICATION_JSON).content(manualBien))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capasInicio[1]").value(10))
                .andExpect(jsonPath("$.capasInicio[2]").value(14))
                .andExpect(jsonPath("$.capasInicio[3]").value(20));

        mvc.perform(post("/api/trabajos/{id}/procesar", id).contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La petición no tiene un formato válido."));
    }

    @Test
    void trabajosInexistentesOSinResultado() throws Exception {
        mvc.perform(get("/api/trabajos/{id}/vista/simulacion.png", "0123456789abcdef0123456789abcdef"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje", containsString("no existe o ha caducado")));
        mvc.perform(get("/api/trabajos/{id}/descarga.zip", "../../etc")).andExpect(status().isNotFound());

        String id = upload(TestImages.png(TestImages.gradient(40, 30)), "b.png", "image/png");
        mvc.perform(get("/api/trabajos/{id}/descarga.stl", id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("Todavía no se ha generado")));
    }

    @Test
    void saludYPaginaDeError() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/no-existe")).andExpect(status().isNotFound()).andExpect(jsonPath("$.estado").value(404));
    }
}
