package com.pic2filament.core.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.pic2filament.StlChecks;
import com.pic2filament.TestImages;
import com.pic2filament.core.ReliefEngine;
import com.pic2filament.core.ReliefResult;
import com.pic2filament.core.ReliefSettings;
import com.pic2filament.core.color.ColorDistance;
import com.pic2filament.core.model.Filament;
import com.pic2filament.core.model.LayerSettings;

public class ZipExporterTest {

    public static ReliefSettings settings(double border) {
        return new ReliefSettings(60, 0.5, new LayerSettings(0.16, 0.08, 4, 28),
                List.of(Filament.of("Negro", "#000000", 0.6), Filament.of("Rojo", "#c0262b", 2.5),
                        Filament.of("Azul", "#1e4fb4", 1.5), Filament.of("Blanco", "#ffffff", 4.5)),
                new int[] { 1, 6, 12, 18 }, ColorDistance.COLOR, 1, 0, true, true, border);
    }

    public static LayerSettings settingsLayers() {
        return new LayerSettings(0.16, 0.08, 4, 28);
    }

    public static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                entries.put(e.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    @Test
    void elZipContieneLosTresFicherosYSeLeeConJavaUtilZip() throws IOException {
        ReliefResult r = new ReliefEngine(600).process(TestImages.gradientRgb(160, 120), settings(0));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZipExporter.write(r, "degradado.png", out);
        Map<String, byte[]> entries = unzip(out.toByteArray());
        assertThat(entries.keySet()).containsExactly("degradado.stl", "degradado-vista-previa.png",
                "degradado-cambios.txt");

        StlChecks.Stl stl = StlChecks.parse(entries.get("degradado.stl"));
        assertThat(stl.triangles()).isPositive();
        assertThat(ImageIO.read(new ByteArrayInputStream(entries.get("degradado-vista-previa.png")))).isNotNull();
        String txt = new String(entries.get("degradado-cambios.txt"), StandardCharsets.UTF_8);
        assertThat(txt).contains("Altura de la primera capa: 0,16 mm").contains("cambia al filamento 2")
                .contains("M600").contains("Bambu Studio").contains("PrusaSlicer");
        for (ColorChange c : r.changes()) {
            if (!c.isInitial()) {
                assertThat(txt).contains("Capa %3d".formatted(c.startLayer()));
            }
        }
    }

    @Test
    void elMarcoAgrandaLaPiezaYSigueSiendoEstanca() throws IOException {
        ReliefResult r = new ReliefEngine(600).process(TestImages.gradientRgb(40, 30), settings(2));
        assertThat(r.widthMm()).isGreaterThan(60);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZipExporter.writeStl(r, "x", out);
        StlChecks.Stl stl = StlChecks.parse(out.toByteArray());
        StlChecks.assertWatertight(stl);
        assertThat(stl.bounds()[3]).isCloseTo(r.widthMm(), org.assertj.core.api.Assertions.within(1e-3));
    }
}
