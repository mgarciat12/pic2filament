package com.pic2filament.core.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.pic2filament.core.ReliefResult;
import com.pic2filament.core.mesh.Mesh;
import com.pic2filament.core.mesh.ReliefMeshBuilder;
import com.pic2filament.core.model.Filament;
import com.pic2filament.core.model.LayerSettings;

/**
 * Proyecto 3MF en el formato de Bambu Studio, que también leen Anycubic Slicer Next y OrcaSlicer.
 * Incluye la malla, el perfil completo de la impresora con los ajustes de pic2filament (altura de
 * capa, primera capa, relleno al 100 % y colores de los filamentos) y los cambios de filamento por capa
 * ({@code Metadata/custom_gcode_per_layer.xml}):
 * <ul>
 * <li>{@link ChangeMode#AUTOMATIC}: cambio de herramienta (tipo 2) a la ranura del filamento;</li>
 * <li>{@link ChangeMode#MANUAL}: pausa (tipo 1) para cambiarlo a mano.</li>
 * </ul>
 * El cambio se declara en la Z de la cara superior de la capa donde empieza el nuevo filamento, que
 * es como lo guardan los propios laminadores.
 */
public final class ProjectThreeMfWriter {

    private static final String NS = "xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\" "
            + "xmlns:BambuStudio=\"http://schemas.bambulab.com/package/2021\" "
            + "xmlns:p=\"http://schemas.microsoft.com/3dmanufacturing/production/2015/06\" requiredextensions=\"p\"";

    private ProjectThreeMfWriter() {
    }

    /**
     * @param thumbnailPng vista previa para la miniatura del proyecto (puede ser {@code null})
     */
    public static void write(ReliefResult r, String name, SlicerProfile profile, ChangeMode mode, byte[] thumbnailPng,
            OutputStream out) throws IOException {
        if (r.settings().filaments().size() > profile.filamentSlots()) {
            throw new IllegalArgumentException("El perfil «" + profile.name() + "» solo admite "
                    + profile.filamentSlots() + " filamentos");
        }
        Mesh mesh = ReliefMeshBuilder.build(r.heights(), r.settings().layers(), r.step());
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        Writer w = new BufferedWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8), 1 << 16);
        boolean thumb = thumbnailPng != null && thumbnailPng.length > 0;

        entry(zip, w, "[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                 <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                 <Default Extension="model" ContentType="application/vnd.ms-package.3dmanufacturing-3dmodel+xml"/>
                 <Default Extension="png" ContentType="image/png"/>
                 <Default Extension="gcode" ContentType="text/x.gcode"/>
                </Types>
                """);
        entry(zip, w, "_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                 <Relationship Target="/3D/3dmodel.model" Id="rel-1" Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/>
                """ + (thumb ? """
                 <Relationship Target="/Metadata/plate_1.png" Id="rel-2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/thumbnail"/>
                 <Relationship Target="/Metadata/plate_1.png" Id="rel-4" Type="http://schemas.bambulab.com/package/2021/cover-thumbnail-middle"/>
                 <Relationship Target="/Metadata/plate_1.png" Id="rel-5" Type="http://schemas.bambulab.com/package/2021/cover-thumbnail-small"/>
                """ : "") + "</Relationships>\n");

        double[] center = profile.bedCenter();
        double tx = center[0] - r.widthMm() / 2, ty = center[1] - r.depthMm() / 2;
        entry(zip, w, "3D/3dmodel.model", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<model unit=\"millimeter\" xml:lang=\"es-ES\" " + NS
                + ">\n <metadata name=\"Application\">BambuStudio-" + escape(profile.version()) + "</metadata>\n"
                + " <metadata name=\"BambuStudio:3mfVersion\">1</metadata>\n"
                + " <metadata name=\"Title\">" + escape(name) + "</metadata>\n"
                + " <metadata name=\"Designer\">pic2filament</metadata>\n"
                + " <resources>\n  <object id=\"2\" p:UUID=\"00000001-61cb-4c03-9d28-80fed5dfa1dc\" type=\"model\">\n"
                + "   <components>\n    <component p:path=\"/3D/Objects/object_1.model\" objectid=\"1\" "
                + "p:UUID=\"00010000-b206-40ff-9872-83e8017abed1\" transform=\"1 0 0 0 1 0 0 0 1 0 0 0\"/>\n"
                + "   </components>\n  </object>\n </resources>\n"
                + " <build p:UUID=\"2c7c17d8-22b5-4d84-8835-1976022ea369\">\n"
                + "  <item objectid=\"2\" p:UUID=\"00000002-b1ec-4553-aec9-835e5b724bb4\" transform=\"1 0 0 0 1 0 0 0 1 "
                + num(tx) + " " + num(ty) + " 0\" printable=\"1\"/>\n </build>\n</model>\n");
        entry(zip, w, "3D/_rels/3dmodel.model.rels", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                 <Relationship Target="/3D/Objects/object_1.model" Id="rel-1" Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/>
                </Relationships>
                """);

        zip.putNextEntry(new ZipEntry("3D/Objects/object_1.model"));
        writeMesh(mesh, w);
        w.flush();
        zip.closeEntry();

        entry(zip, w, "Metadata/model_settings.config", """
                <?xml version="1.0" encoding="UTF-8"?>
                <config>
                  <object id="2">
                    <metadata key="name" value="%1$s"/>
                    <metadata key="extruder" value="1"/>
                    <part id="1" subtype="normal_part">
                      <metadata key="name" value="%1$s"/>
                      <metadata key="matrix" value="1 0 0 0 0 1 0 0 0 0 1 0 0 0 0 1"/>
                    </part>
                  </object>
                  <plate>
                    <metadata key="plater_id" value="1"/>
                    <metadata key="plater_name" value=""/>
                    <metadata key="locked" value="false"/>
                    <model_instance>
                      <metadata key="object_id" value="2"/>
                      <metadata key="instance_id" value="0"/>
                    </model_instance>
                  </plate>
                </config>
                """.formatted(escape(name)));

        entry(zip, w, "Metadata/project_settings.config", Json.write(projectSettings(r, profile, mode)));
        entry(zip, w, "Metadata/custom_gcode_per_layer.xml", customGcode(r, mode));

        if (thumb) {
            zip.putNextEntry(new ZipEntry("Metadata/plate_1.png"));
            zip.write(thumbnailPng);
            zip.closeEntry();
        }
        zip.finish();
        zip.flush();
    }

    /** Ajustes del perfil con los valores de pic2filament encima. */
    static Map<String, Object> projectSettings(ReliefResult r, SlicerProfile profile, ChangeMode mode) {
        Map<String, Object> s = new LinkedHashMap<>(profile.settings());
        LayerSettings l = r.settings().layers();
        s.put("layer_height", num(l.layerHeight()));
        s.put("initial_layer_print_height", num(l.firstLayerHeight()));
        s.put("sparse_infill_density", "100%");
        s.put("sparse_infill_pattern", "zig-zag");
        s.put("adaptive_layer_height", "0");
        // Todas las ranuras del perfil reciben color: las que sobran repiten el último filamento.
        List<Filament> f = r.settings().filaments();
        List<String> colours = new ArrayList<>();
        for (int i = 0; i < profile.filamentSlots(); i++) {
            colours.add(f.get(Math.min(i, f.size() - 1)).hex().toUpperCase(Locale.ROOT));
        }
        s.put("filament_colour", colours);
        if (mode == ChangeMode.AUTOMATIC) {
            s.put("single_extruder_multi_material", "1");
            s.put("enable_prime_tower", "1");
        }
        return s;
    }

    static String customGcode(ReliefResult r, ChangeMode mode) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<custom_gcodes_per_layer>\n<plate>\n<plate_info id=\"1\"/>\n");
        for (ColorChange c : r.changes()) {
            if (c.isInitial()) {
                continue;
            }
            String colour = c.hex().toUpperCase(Locale.ROOT);
            if (mode == ChangeMode.AUTOMATIC) {
                sb.append("<layer top_z=\"").append(num(c.z())).append("\" type=\"2\" extruder=\"").append(c.filament())
                        .append("\" color=\"").append(colour).append("\" extra=\"\" gcode=\"tool_change\"/>\n");
            } else {
                sb.append("<layer top_z=\"").append(num(c.z())).append("\" type=\"1\" extruder=\"1\" color=\"")
                        .append(colour).append("\" extra=\"Cambia al filamento ").append(c.filament()).append(": ")
                        .append(escape(c.name())).append("\" gcode=\"M600\"/>\n");
            }
        }
        sb.append("<mode value=\"").append(mode == ChangeMode.AUTOMATIC ? "MultiAsSingle" : "SingleExtruder")
                .append("\"/>\n</plate>\n</custom_gcodes_per_layer>\n");
        return sb.toString();
    }

    private static void writeMesh(Mesh mesh, Writer w) throws IOException {
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<model unit=\"millimeter\" xml:lang=\"es-ES\" " + NS + ">\n");
        w.write(" <metadata name=\"BambuStudio:3mfVersion\">1</metadata>\n <resources>\n");
        w.write("  <object id=\"1\" p:UUID=\"00010000-81cb-4c03-9d28-80fed5dfa1dc\" type=\"model\">\n   <mesh>\n    <vertices>\n");
        float[] v = mesh.vertices();
        StringBuilder sb = new StringBuilder(64);
        for (int i = 0; i < v.length; i += 3) {
            sb.setLength(0);
            sb.append("     <vertex x=\"").append(v[i]).append("\" y=\"").append(v[i + 1]).append("\" z=\"").append(v[i + 2])
                    .append("\"/>\n");
            w.write(sb.toString());
        }
        w.write("    </vertices>\n    <triangles>\n");
        int[] t = mesh.triangles();
        for (int i = 0; i < t.length; i += 3) {
            sb.setLength(0);
            sb.append("     <triangle v1=\"").append(t[i]).append("\" v2=\"").append(t[i + 1]).append("\" v3=\"")
                    .append(t[i + 2]).append("\"/>\n");
            w.write(sb.toString());
        }
        w.write("    </triangles>\n   </mesh>\n  </object>\n </resources>\n <build/>\n</model>\n");
    }

    private static void entry(ZipOutputStream zip, Writer w, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        w.write(content);
        w.flush();
        zip.closeEntry();
    }

    /** Número con punto decimal y sin ceros sobrantes (0.08, 0.16, 25). */
    static String num(double v) {
        String s = String.format(Locale.ROOT, "%.4f", v);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Serializador JSON mínimo para los ajustes del proyecto (textos y listas de textos). */
    static final class Json {

        private Json() {
        }

        static String write(Map<String, Object> map) {
            StringBuilder sb = new StringBuilder("{\n");
            int i = 0;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                sb.append("    ").append(quote(e.getKey())).append(": ");
                if (e.getValue() instanceof List<?> list) {
                    sb.append('[');
                    for (int j = 0; j < list.size(); j++) {
                        if (j > 0) {
                            sb.append(", ");
                        }
                        sb.append(quote((String) list.get(j)));
                    }
                    sb.append(']');
                } else {
                    sb.append(quote((String) e.getValue()));
                }
                sb.append(++i < map.size() ? ",\n" : "\n");
            }
            return sb.append("}\n").toString();
        }

        static String quote(String s) {
            StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            return sb.append('"').toString();
        }
    }
}
