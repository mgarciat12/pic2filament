package com.pic2filament.core.export;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import com.pic2filament.core.ReliefResult;
import com.pic2filament.core.mesh.Mesh;
import com.pic2filament.core.mesh.ReliefMeshBuilder;
import com.pic2filament.core.mesh.StlWriter;
import com.pic2filament.core.render.PreviewRenderer;

/**
 * Empaqueta el resultado en un ZIP: STL binario, PNG de la vista previa y TXT de instrucciones.
 */
public final class ZipExporter {

    private ZipExporter() {
    }

    public static void write(ReliefResult r, String sourceName, OutputStream out) throws IOException {
        String base = baseName(sourceName);
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);

        zip.putNextEntry(new ZipEntry(base + ".stl"));
        writeStl(r, base, zip);
        zip.closeEntry();

        zip.putNextEntry(new ZipEntry(base + "-vista-previa.png"));
        BufferedImage preview = PreviewRenderer.upscale(PreviewRenderer.simulation(r.heights(), r.stack()), 800);
        ImageIO.write(preview, "png", zip);
        zip.closeEntry();

        zip.putNextEntry(new ZipEntry(base + "-cambios.txt"));
        zip.write(SlicerInstructions.text(r, sourceName).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.finish();
        zip.flush();
    }

    public static void writeStl(ReliefResult r, String name, OutputStream out) throws IOException {
        Mesh mesh = ReliefMeshBuilder.build(r.heights(), r.settings().layers(), r.step());
        StlWriter.write(mesh, "pic2filament " + name, out);
    }

    /** Nombre base seguro (ASCII, sin extensión ni rutas) para los ficheros generados. */
    public static String baseName(String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            return "pic2filament";
        }
        String n = sourceName.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        int dot = n.lastIndexOf('.');
        if (dot > 0) {
            n = n.substring(0, dot);
        }
        n = Normalizer.normalize(n, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^[-.]+|[-.]+$", "");
        if (n.length() > 60) {
            n = n.substring(0, 60);
        }
        return n.isEmpty() ? "pic2filament" : n;
    }
}
