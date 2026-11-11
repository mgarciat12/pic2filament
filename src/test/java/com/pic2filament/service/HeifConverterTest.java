package com.pic2filament.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HeifConverterTest {

    static final Path HEIC = Path.of("src/test/resources/ejemplo-sintetico.heic");

    @TempDir
    Path tmp;

    static byte[] header(String major, String... compatible) {
        StringBuilder s = new StringBuilder("ftyp").append(major).append("\0\0\0\0");
        for (String c : compatible) {
            s.append(c);
        }
        byte[] body = s.toString().getBytes(StandardCharsets.ISO_8859_1);
        byte[] out = new byte[body.length + 4 + 8];
        int size = body.length + 4;
        out[3] = (byte) size;
        System.arraycopy(body, 0, out, 4, body.length);
        return out;
    }

    @Test
    void reconoceHeicPorElContenido() throws Exception {
        assertThat(HeifConverter.isHeif(Files.readAllBytes(HEIC))).isTrue();
        assertThat(HeifConverter.isHeif(header("heic", "mif1"))).isTrue();
        assertThat(HeifConverter.isHeif(header("mif1", "heic"))).isTrue();
        // Una marca genérica con HEIF entre las compatibles (p. ej. algunas cámaras)
        assertThat(HeifConverter.isHeif(header("isom", "heix"))).isTrue();
        // AVIF, MP4 y otros formatos no son HEIC
        assertThat(HeifConverter.isHeif(header("avif", "mif1", "miaf"))).isFalse();
        assertThat(HeifConverter.isHeif(header("isom", "mp41"))).isFalse();
        assertThat(HeifConverter.isHeif(Files.readAllBytes(Path.of("src/test/resources/ejemplo-sintetico.png"))))
                .isFalse();
        assertThat(HeifConverter.isHeif(new byte[5])).isFalse();
        assertThat(HeifConverter.isHeif(null)).isFalse();
    }

    @Test
    void sinConversorDaUnErrorClaro() {
        HeifConverter none = new HeifConverter(HeifConverter.resolve("no"), tmp);
        assertThat(none.available()).isFalse();
        assertThatThrownBy(() -> none.toPng(new byte[] { 1 })).isInstanceOf(Pic2filamentException.class)
                .hasMessageContaining("falta libheif");
    }

    @Test
    void unComandoQueFallaSeConvierteEnErrorDeFormato() throws Exception {
        HeifConverter bad = new HeifConverter(List.of("false", "{entrada}", "{salida}"), tmp);
        assertThatThrownBy(() -> bad.toPng(Files.readAllBytes(HEIC))).isInstanceOf(Pic2filamentException.class)
                .hasMessageContaining("No se ha podido leer la foto HEIC");
        try (var files = Files.list(tmp)) {
            assertThat(files).as("los temporales se borran").isEmpty();
        }
    }

    @Test
    void convierteLaFotoDeEjemploAPng() throws Exception {
        HeifConverter conv = new HeifConverter(HeifConverter.resolve(null), tmp);
        assumeTrue(conv.available(), "no hay heif-dec, heif-convert ni sips");
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(conv.toPng(Files.readAllBytes(HEIC))));
        assertThat(img.getWidth()).isEqualTo(640);
        assertThat(img.getHeight()).isEqualTo(480);
        BufferedImage ref = ImageIO.read(Path.of("src/test/resources/ejemplo-sintetico.png").toFile());
        // Comparación tolerante (HEIC tiene pérdidas): diferencia media por canal pequeña.
        long diff = 0;
        for (int y = 0; y < 480; y += 8) {
            for (int x = 0; x < 640; x += 8) {
                int a = img.getRGB(x, y), b = ref.getRGB(x, y);
                for (int s = 0; s < 24; s += 8) {
                    diff += Math.abs(((a >> s) & 0xff) - ((b >> s) & 0xff));
                }
            }
        }
        assertThat(diff / (60.0 * 80 * 3)).isLessThan(8);
        try (var files = Files.list(tmp)) {
            assertThat(files).isEmpty();
        }
    }
}
