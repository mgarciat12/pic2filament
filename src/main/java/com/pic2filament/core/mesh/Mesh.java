package com.pic2filament.core.mesh;

/**
 * Malla triangular indexada. Coordenadas en mm; triángulos en sentido antihorario vistos desde
 * fuera (normales hacia fuera).
 *
 * @param vertices  x0,y0,z0, x1,y1,z1, ...
 * @param triangles i0,j0,k0, i1,j1,k1, ... (índices de vértice)
 */
public record Mesh(float[] vertices, int[] triangles) {

    public int vertexCount() {
        return vertices.length / 3;
    }

    public int triangleCount() {
        return triangles.length / 3;
    }

    /** Caja envolvente: {minX, minY, minZ, maxX, maxY, maxZ}. */
    public double[] bounds() {
        double[] b = { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE };
        for (int i = 0; i < vertices.length; i += 3) {
            for (int c = 0; c < 3; c++) {
                b[c] = Math.min(b[c], vertices[i + c]);
                b[c + 3] = Math.max(b[c + 3], vertices[i + c]);
            }
        }
        return b;
    }

    /** Volumen con signo (positivo si las normales apuntan hacia fuera). */
    public double signedVolume() {
        double v = 0;
        for (int t = 0; t < triangles.length; t += 3) {
            int a = triangles[t] * 3, b = triangles[t + 1] * 3, c = triangles[t + 2] * 3;
            double ax = vertices[a], ay = vertices[a + 1], az = vertices[a + 2];
            double bx = vertices[b], by = vertices[b + 1], bz = vertices[b + 2];
            double cx = vertices[c], cy = vertices[c + 1], cz = vertices[c + 2];
            v += ax * (by * cz - bz * cy) - ay * (bx * cz - bz * cx) + az * (bx * cy - by * cx);
        }
        return v / 6.0;
    }
}
