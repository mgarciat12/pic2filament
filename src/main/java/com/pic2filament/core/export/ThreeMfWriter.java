package com.pic2filament.core.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.pic2filament.core.ReliefResult;
import com.pic2filament.core.mesh.Mesh;
import com.pic2filament.core.mesh.ReliefMeshBuilder;

/**
 * Exportación a 3MF con los cambios de color integrados como metadatos:
 * <ul>
 * <li>{@code Metadata/Prusa_Slicer_custom_gcode_per_print_z.xml} (PrusaSlicer);</li>
 * <li>{@code Metadata/custom_gcode_per_layer.xml} (Bambu Studio y OrcaSlicer).</li>
 * </ul>
 * Cada cambio se declara como «cambio de color» (tipo 0) a la altura Z de la cara superior de la
 * capa en la que empieza el nuevo filamento. Los laminadores que no entiendan estos metadatos
 * cargan igualmente la malla.
 */
public final class ThreeMfWriter {

    private ThreeMfWriter() {
    }

    public static void write(ReliefResult r, String name, OutputStream out) throws IOException {
        Mesh mesh = ReliefMeshBuilder.build(r.heights(), r.settings().layers(), r.step());
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        Writer w = new BufferedWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8), 1 << 16);

        zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
        w.write("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                 <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                 <Default Extension="model" ContentType="application/vnd.ms-package.3dmanufacturing-3dmodel+xml"/>
                 <Default Extension="xml" ContentType="application/xml"/>
                </Types>
                """);
        w.flush();
        zip.closeEntry();

        zip.putNextEntry(new ZipEntry("_rels/.rels"));
        w.write("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                 <Relationship Target="/3D/3dmodel.model" Id="rel0" Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/>
                </Relationships>
                """);
        w.flush();
        zip.closeEntry();

        zip.putNextEntry(new ZipEntry("3D/3dmodel.model"));
        writeModel(mesh, name, w);
        w.flush();
        zip.closeEntry();

        zip.putNextEntry(new ZipEntry("Metadata/Prusa_Slicer_custom_gcode_per_print_z.xml"));
        w.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<custom_gcodes_per_print_z>\n");
        for (ColorChange c : r.changes()) {
            if (!c.isInitial()) {
                w.write(String.format(Locale.ROOT,
                        "<code print_z=\"%.3f\" type=\"0\" extruder=\"1\" color=\"%s\" extra=\"\" gcode=\"M600\"/>\n",
                        c.z(), c.hex()));
            }
        }
        w.write("<mode value=\"SingleExtruder\"/>\n</custom_gcodes_per_print_z>\n");
        w.flush();
        zip.closeEntry();

        zip.putNextEntry(new ZipEntry("Metadata/custom_gcode_per_layer.xml"));
        w.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<custom_gcodes_per_layer>\n<plate>\n"
                + "<plate_info id=\"1\"/>\n");
        for (ColorChange c : r.changes()) {
            if (!c.isInitial()) {
                w.write(String.format(Locale.ROOT,
                        "<layer top_z=\"%.3f\" type=\"0\" extruder=\"1\" color=\"%s\" extra=\"\" gcode=\"\"/>\n",
                        c.z(), c.hex().toUpperCase(Locale.ROOT)));
            }
        }
        w.write("<mode value=\"SingleExtruder\"/>\n</plate>\n</custom_gcodes_per_layer>\n");
        w.flush();
        zip.closeEntry();

        zip.finish();
        zip.flush();
    }

    private static void writeModel(Mesh mesh, String name, Writer w) throws IOException {
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        w.write("<model unit=\"millimeter\" xml:lang=\"es-ES\" "
                + "xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\">\n");
        w.write(" <metadata name=\"Application\">pic2filament</metadata>\n");
        w.write(" <metadata name=\"Title\">" + escape(name) + "</metadata>\n");
        w.write(" <resources>\n  <object id=\"1\" type=\"model\" name=\"" + escape(name) + "\">\n   <mesh>\n    <vertices>\n");
        float[] v = mesh.vertices();
        StringBuilder sb = new StringBuilder(64);
        for (int i = 0; i < v.length; i += 3) {
            sb.setLength(0);
            sb.append("     <vertex x=\"").append(v[i]).append("\" y=\"").append(v[i + 1]).append("\" z=\"")
                    .append(v[i + 2]).append("\"/>\n");
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
        w.write("    </triangles>\n   </mesh>\n  </object>\n </resources>\n");
        w.write(" <build>\n  <item objectid=\"1\"/>\n </build>\n</model>\n");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
