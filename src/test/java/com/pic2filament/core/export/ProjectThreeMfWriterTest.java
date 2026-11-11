package com.pic2filament.core.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.pic2filament.TestImages;
import com.pic2filament.core.ReliefEngine;
import com.pic2filament.core.ReliefResult;
import com.pic2filament.core.ReliefSettings;

class ProjectThreeMfWriterTest {

    static SlicerProfile profile() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("version", "2.0.0.3");
        s.put("printer_settings_id", "Impresora de prueba");
        s.put("filament_settings_id", List.of("PLA", "PLA", "PLA", "PLA"));
        s.put("filament_colour", List.of("#FF0000"));
        s.put("layer_height", "0.2");
        s.put("initial_layer_print_height", "0.2");
        s.put("sparse_infill_density", "15%");
        s.put("printable_area", List.of("0x0", "250x0", "250x210", "0x210"));
        s.put("machine_start_gcode", "G28 ; \"inicio\"\nM104 S200");
        return new SlicerProfile("prueba", "Prueba", s);
    }

    static ReliefResult result() {
        ReliefSettings s = ZipExporterTest.settings(0);
        ReliefSettings manual = new ReliefSettings(s.widthMm(), s.mmPerPixel(), s.layers(), s.filaments(),
                new int[] { 1, 5, 7, 12 }, s.metric(), 1, 0, false, false, 0);
        return new ReliefEngine(600).process(TestImages.gradientRgb(80, 60), manual);
    }

    static Map<String, byte[]> write(ChangeMode mode) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProjectThreeMfWriter.write(result(), "prueba", profile(), mode, new byte[] { 1, 2, 3 }, out);
        return ZipExporterTest.unzip(out.toByteArray());
    }

    @Test
    void contieneLaEstructuraDeProyectoDeBambuStudio() throws Exception {
        Map<String, byte[]> e = write(ChangeMode.AUTOMATIC);
        assertThat(e).containsKeys("[Content_Types].xml", "_rels/.rels", "3D/3dmodel.model",
                "3D/_rels/3dmodel.model.rels", "3D/Objects/object_1.model", "Metadata/model_settings.config",
                "Metadata/project_settings.config", "Metadata/custom_gcode_per_layer.xml", "Metadata/plate_1.png");
        String model = text(e, "3D/3dmodel.model");
        assertThat(model).contains("<metadata name=\"Application\">BambuStudio-2.0.0.3</metadata>")
                .contains("p:path=\"/3D/Objects/object_1.model\"");
        // Centrado en la cama de 250×210: la pieza mide 60 × 45 mm.
        assertThat(model).contains("transform=\"1 0 0 0 1 0 0 0 1 95 82.5 0\"");
        assertThat(text(e, "Metadata/model_settings.config")).contains("<metadata key=\"extruder\" value=\"1\"/>");
    }

    @Test
    void losAjustesDelProyectoLlevanLosValoresDePic2filament() throws Exception {
        String json = text(write(ChangeMode.AUTOMATIC), "Metadata/project_settings.config");
        assertThat(json).contains("\"layer_height\": \"0.08\"").contains("\"initial_layer_print_height\": \"0.16\"")
                .contains("\"sparse_infill_density\": \"100%\"").contains("\"single_extruder_multi_material\": \"1\"")
                .contains("\"filament_colour\": [\"#000000\", \"#C0262B\", \"#1E4FB4\", \"#FFFFFF\"]")
                .contains("\"printer_settings_id\": \"Impresora de prueba\"")
                // los textos con comillas y saltos de línea se escapan
                .contains("\"machine_start_gcode\": \"G28 ; \\\"inicio\\\"\\nM104 S200\"");
    }

    @Test
    void modoAutomaticoCambiaDeHerramientaEnLaZDeCadaCambio() throws Exception {
        String xml = text(write(ChangeMode.AUTOMATIC), "Metadata/custom_gcode_per_layer.xml");
        List<String[]> layers = layers(xml);
        assertThat(layers).hasSize(3);
        assertThat(layers.get(0)).containsExactly("0.48", "2", "2");
        assertThat(layers.get(1)).containsExactly("0.64", "2", "3");
        assertThat(layers.get(2)).containsExactly("1.04", "2", "4");
        assertThat(xml).contains("<mode value=\"MultiAsSingle\"/>");
    }

    @Test
    void modoManualPausaEnLaZDeCadaCambio() throws Exception {
        String xml = text(write(ChangeMode.MANUAL), "Metadata/custom_gcode_per_layer.xml");
        List<String[]> layers = layers(xml);
        assertThat(layers).extracting(l -> l[0]).containsExactly("0.48", "0.64", "1.04");
        assertThat(layers).extracting(l -> l[1]).containsOnly("1");
        assertThat(xml).contains("gcode=\"M600\"").contains("<mode value=\"SingleExtruder\"/>");
    }

    @Test
    void laMallaDelProyectoTieneLosMismosTriangulosQueElStl() throws Exception {
        String mesh = text(write(ChangeMode.AUTOMATIC), "3D/Objects/object_1.model");
        ReliefResult r = result();
        long triangles = Pattern.compile("<triangle ").matcher(mesh).results().count();
        assertThat(triangles).isEqualTo(
                com.pic2filament.core.mesh.ReliefMeshBuilder.triangleCount(r.heights().width(), r.heights().height()));
    }

    @Test
    void rechazaMasFilamentosQueRanurasYCalculaElCentro() {
        Map<String, Object> s = new LinkedHashMap<>(profile().settings());
        s.put("filament_settings_id", List.of("PLA", "PLA"));
        SlicerProfile two = new SlicerProfile("dos", "Dos", s);
        assertThatThrownBy(() -> ProjectThreeMfWriter.write(result(), "x", two, ChangeMode.AUTOMATIC, null,
                new ByteArrayOutputStream())).hasMessageContaining("solo admite 2");
        assertThat(profile().bedCenter()[0]).isCloseTo(125, within(1e-9));
        assertThat(profile().bedCenter()[1]).isCloseTo(105, within(1e-9));
        assertThat(ProjectThreeMfWriter.num(0.08)).isEqualTo("0.08");
        assertThat(ProjectThreeMfWriter.num(25)).isEqualTo("25");
    }

    @Test
    void unPerfilConValoresNoAdmitidosSeRechaza() {
        Map<String, Object> s = new LinkedHashMap<>(profile().settings());
        s.put("raro", 3);
        assertThatThrownBy(() -> new SlicerProfile("x", "x", s)).hasMessageContaining("raro");
    }

    private static String text(Map<String, byte[]> e, String name) {
        return new String(e.get(name), StandardCharsets.UTF_8);
    }

    /** {top_z, type, extruder} de cada línea del XML de cambios. */
    private static List<String[]> layers(String xml) {
        Matcher m = Pattern.compile("<layer top_z=\"([^\"]+)\" type=\"(\\d)\" extruder=\"(\\d)\"").matcher(xml);
        List<String[]> out = new ArrayList<>();
        while (m.find()) {
            out.add(new String[] { m.group(1), m.group(2), m.group(3) });
        }
        return out;
    }
}
