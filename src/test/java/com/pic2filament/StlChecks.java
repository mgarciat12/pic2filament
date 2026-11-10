package com.pic2filament;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lectura y comprobaciones de un STL binario para las pruebas. */
public final class StlChecks {

    private StlChecks() {
    }

    public record Stl(int triangles, float[] normals, float[] vertices) {

        /** {minX, minY, minZ, maxX, maxY, maxZ} */
        public double[] bounds() {
            double[] b = { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE,
                    -Double.MAX_VALUE, -Double.MAX_VALUE };
            for (int i = 0; i < vertices.length; i += 3) {
                for (int c = 0; c < 3; c++) {
                    b[c] = Math.min(b[c], vertices[i + c]);
                    b[c + 3] = Math.max(b[c + 3], vertices[i + c]);
                }
            }
            return b;
        }
    }

    public static Stl parse(byte[] data) {
        assertThat(data.length).isGreaterThanOrEqualTo(84);
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(80);
        int n = buf.getInt();
        assertThat((long) data.length).as("tamaño = 84 + 50·n").isEqualTo(84L + 50L * n);
        float[] normals = new float[n * 3];
        float[] vertices = new float[n * 9];
        for (int t = 0; t < n; t++) {
            for (int c = 0; c < 3; c++) {
                normals[t * 3 + c] = buf.getFloat();
            }
            for (int c = 0; c < 9; c++) {
                vertices[t * 9 + c] = buf.getFloat();
            }
            buf.getShort();
        }
        return new Stl(n, normals, vertices);
    }

    /**
     * Comprueba que la malla es estanca y orientada: cada arista no dirigida aparece en exactamente
     * dos triángulos, y en sentidos opuestos (los vértices se identifican por sus coordenadas).
     */
    public static void assertWatertight(Stl stl) {
        Map<List<Float>, Integer> ids = new HashMap<>();
        float[] v = stl.vertices();
        int[] tri = new int[3];
        Map<Long, Integer> directed = new HashMap<>(stl.triangles() * 4);
        for (int t = 0; t < stl.triangles(); t++) {
            for (int k = 0; k < 3; k++) {
                int o = t * 9 + k * 3;
                tri[k] = ids.computeIfAbsent(List.of(v[o], v[o + 1], v[o + 2]), key -> ids.size());
            }
            assertThat(tri[0] != tri[1] && tri[1] != tri[2] && tri[0] != tri[2]).as("triángulo degenerado").isTrue();
            for (int k = 0; k < 3; k++) {
                directed.merge(((long) tri[k] << 32) | tri[(k + 1) % 3], 1, Integer::sum);
            }
        }
        for (Map.Entry<Long, Integer> e : directed.entrySet()) {
            long a = e.getKey() >>> 32, b = e.getKey() & 0xffffffffL;
            assertThat(e.getValue()).as("arista dirigida repetida %d>%d", a, b).isEqualTo(1);
            assertThat(directed.get((b << 32) | a)).as("arista %d>%d sin pareja", a, b).isEqualTo(1);
        }
    }

    /** Volumen con signo calculado a partir de los triángulos (positivo si las normales miran afuera). */
    public static double signedVolume(Stl stl) {
        double vol = 0;
        float[] v = stl.vertices();
        for (int t = 0; t < stl.triangles(); t++) {
            int o = t * 9;
            double ax = v[o], ay = v[o + 1], az = v[o + 2];
            double bx = v[o + 3], by = v[o + 4], bz = v[o + 5];
            double cx = v[o + 6], cy = v[o + 7], cz = v[o + 8];
            vol += ax * (by * cz - bz * cy) - ay * (bx * cz - bz * cx) + az * (bx * cy - by * cx);
        }
        return vol / 6;
    }
}
