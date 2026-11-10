package com.pic2filament.core.mesh;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Escritor de STL binario: cabecera de 80 bytes, número de triángulos (uint32 LE) y 50 bytes
 * por triángulo (normal, tres vértices y atributo de 2 bytes). Unidades en mm.
 */
public final class StlWriter {

    public static final int HEADER_SIZE = 80;
    public static final int TRIANGLE_SIZE = 50;

    private StlWriter() {
    }

    public static long fileSize(long triangles) {
        return HEADER_SIZE + 4 + TRIANGLE_SIZE * triangles;
    }

    public static void write(Mesh mesh, String header, OutputStream out) throws IOException {
        byte[] h = new byte[HEADER_SIZE];
        byte[] text = (header == null ? "pic2filament" : header).getBytes(StandardCharsets.US_ASCII);
        // Una cabecera que empiece por "solid" confunde a algunos lectores con el formato ASCII.
        if (text.length >= 5 && new String(text, 0, 5, StandardCharsets.US_ASCII).equalsIgnoreCase("solid")) {
            text[0] = '_';
        }
        System.arraycopy(text, 0, h, 0, Math.min(text.length, HEADER_SIZE));
        out.write(h);

        ByteBuffer buf = ByteBuffer.allocate(TRIANGLE_SIZE * 1024).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(mesh.triangleCount());
        float[] v = mesh.vertices();
        int[] t = mesh.triangles();
        for (int i = 0; i < t.length; i += 3) {
            if (buf.remaining() < TRIANGLE_SIZE) {
                out.write(buf.array(), 0, buf.position());
                buf.clear();
            }
            int a = t[i] * 3, b = t[i + 1] * 3, c = t[i + 2] * 3;
            float ux = v[b] - v[a], uy = v[b + 1] - v[a + 1], uz = v[b + 2] - v[a + 2];
            float wx = v[c] - v[a], wy = v[c + 1] - v[a + 1], wz = v[c + 2] - v[a + 2];
            float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 0) {
                nx /= len;
                ny /= len;
                nz /= len;
            }
            buf.putFloat(nx).putFloat(ny).putFloat(nz);
            buf.putFloat(v[a]).putFloat(v[a + 1]).putFloat(v[a + 2]);
            buf.putFloat(v[b]).putFloat(v[b + 1]).putFloat(v[b + 2]);
            buf.putFloat(v[c]).putFloat(v[c + 1]).putFloat(v[c + 2]);
            buf.putShort((short) 0);
        }
        out.write(buf.array(), 0, buf.position());
        out.flush();
    }
}
