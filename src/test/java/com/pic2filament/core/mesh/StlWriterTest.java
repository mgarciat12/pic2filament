package com.pic2filament.core.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.pic2filament.StlChecks;
import com.pic2filament.core.height.HeightMap;
import com.pic2filament.core.model.LayerSettings;

class StlWriterTest {

    private static final LayerSettings LAYERS = new LayerSettings(0.16, 0.08, 4, 28);

    private static HeightMap pyramid(int w, int h) {
        int[] l = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int d = Math.min(Math.min(x, w - 1 - x), Math.min(y, h - 1 - y));
                l[y * w + x] = Math.min(28, 4 + d * 3);
            }
        }
        return new HeightMap(w, h, l);
    }

    private static byte[] stl(Mesh mesh) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StlWriter.write(mesh, "pic2filament prueba", out);
        return out.toByteArray();
    }

    @Test
    void cabeceraNumeroDeTriangulosYTamano() throws IOException {
        int w = 21, h = 13;
        Mesh mesh = ReliefMeshBuilder.build(pyramid(w, h), LAYERS, 0.5);
        byte[] data = stl(mesh);
        String header = new String(data, 0, 19, StandardCharsets.US_ASCII);
        assertThat(header).isEqualTo("pic2filament prueba");
        StlChecks.Stl s = StlChecks.parse(data);
        long expected = 2L * (w - 1) * (h - 1) + 3L * (2 * (w - 1) + 2 * (h - 1));
        assertThat(s.triangles()).isEqualTo((int) expected).isEqualTo(mesh.triangleCount());
        assertThat((long) data.length).isEqualTo(StlWriter.fileSize(expected)).isEqualTo(84 + 50 * expected);
    }

    @Test
    void laMallaEsEstancaYConNormalesHaciaFuera() throws IOException {
        Mesh mesh = ReliefMeshBuilder.build(pyramid(17, 11), LAYERS, 0.4);
        StlChecks.Stl s = StlChecks.parse(stl(mesh));
        StlChecks.assertWatertight(s);
        assertThat(StlChecks.signedVolume(s)).isPositive().isCloseTo(mesh.signedVolume(), within(1e-3));
        // Todas las normales de la cara superior apuntan hacia arriba.
        int topTriangles = 2 * 16 * 10;
        for (int t = 0; t < topTriangles; t++) {
            assertThat(s.normals()[t * 3 + 2]).isPositive();
        }
    }

    @Test
    void elVolumenDeUnaLosaPlanaEsElEsperado() {
        int[] l = new int[11 * 6];
        Arrays.fill(l, 10);
        Mesh mesh = ReliefMeshBuilder.build(new HeightMap(11, 6, l), LAYERS, 1.0);
        double height = LAYERS.heightOf(10);
        assertThat(mesh.signedVolume()).isCloseTo(10 * 5 * height, within(1e-3));
    }

    @Test
    void laCajaCoincideConElTamanoPedidoYLaAlturaMaxima() throws IOException {
        int w = 41, h = 31;
        double step = 100.0 / (w - 1);
        Mesh mesh = ReliefMeshBuilder.build(pyramid(w, h), LAYERS, step);
        StlChecks.Stl s = StlChecks.parse(stl(mesh));
        double[] b = s.bounds();
        assertThat(b[0]).isCloseTo(0, within(1e-4));
        assertThat(b[1]).isCloseTo(0, within(1e-4));
        assertThat(b[2]).isCloseTo(0, within(1e-6));
        assertThat(b[3]).isCloseTo(100, within(1e-3));
        assertThat(b[4]).isCloseTo(75, within(1e-3));
        assertThat(b[5]).isCloseTo(LAYERS.heightOf(28), within(1e-5));
    }

    @Test
    void elEjeYEstaInvertidoRespectoALaImagen() {
        // Solo el píxel de arriba a la izquierda de la imagen es alto.
        int[] l = new int[4 * 3];
        Arrays.fill(l, 4);
        l[0] = 28;
        Mesh mesh = ReliefMeshBuilder.build(new HeightMap(4, 3, l), LAYERS, 1.0);
        float[] v = mesh.vertices();
        // El vértice 0 (fila 0 de la imagen) debe quedar en X=0 e Y máxima.
        assertThat(v[0]).isEqualTo(0f);
        assertThat(v[1]).isEqualTo(2f);
        assertThat((double) v[2]).isCloseTo(LAYERS.heightOf(28), within(1e-6));
    }

    @Test
    void unaCabeceraQueEmpiezaPorSolidSeModifica() throws IOException {
        Mesh mesh = ReliefMeshBuilder.build(pyramid(3, 3), LAYERS, 1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StlWriter.write(mesh, "solid prueba", out);
        assertThat(new String(out.toByteArray(), 0, 5, StandardCharsets.US_ASCII)).isNotEqualToIgnoringCase("solid");
    }
}
