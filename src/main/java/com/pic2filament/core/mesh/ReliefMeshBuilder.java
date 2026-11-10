package com.pic2filament.core.mesh;

import com.pic2filament.core.height.HeightMap;
import com.pic2filament.core.model.LayerSettings;

/**
 * Construye una malla cerrada y estanca a partir del mapa de alturas:
 * <ul>
 * <li>superficie superior con un vértice por píxel de la rejilla y dos triángulos por celda;</li>
 * <li>paredes laterales desde el borde superior hasta Z = 0;</li>
 * <li>fondo plano en Z = 0, triangulado en abanico desde el centro usando los mismos vértices
 * del perímetro (así cada arista queda compartida por exactamente dos triángulos).</li>
 * </ul>
 * El eje Y se invierte respecto a la imagen (fila 0 = Y máxima) para que la pieza no salga en espejo.
 */
public final class ReliefMeshBuilder {

    private ReliefMeshBuilder() {
    }

    /** Número de triángulos que tendrá la malla de una rejilla w×h. */
    public static long triangleCount(int w, int h) {
        long perimeter = 2L * (w - 1) + 2L * (h - 1);
        return 2L * (w - 1) * (h - 1) + 3 * perimeter;
    }

    public static Mesh build(HeightMap map, LayerSettings layers, double step) {
        float[] z = new float[map.layers().length];
        int[] l = map.layers();
        for (int i = 0; i < l.length; i++) {
            z[i] = (float) layers.heightOf(l[i]);
        }
        return build(map.width(), map.height(), z, step);
    }

    /**
     * @param w     vértices en X
     * @param h     vértices en Y
     * @param z     altura de cada vértice (mm), por filas de la imagen
     * @param step  separación entre vértices (mm)
     */
    public static Mesh build(int w, int h, float[] z, double step) {
        if (w < 2 || h < 2) {
            throw new IllegalArgumentException("La rejilla debe tener al menos 2×2 vértices");
        }
        int top = w * h;
        int perimeter = 2 * (w - 1) + 2 * (h - 1);
        int vertexCount = top + perimeter + 1;
        float[] v = new float[vertexCount * 3];

        // Vértices superiores.
        for (int j = 0; j < h; j++) {
            float y = (float) ((h - 1 - j) * step);
            for (int i = 0; i < w; i++) {
                int idx = (j * w + i) * 3;
                v[idx] = (float) (i * step);
                v[idx + 1] = y;
                v[idx + 2] = z[j * w + i];
            }
        }

        // Perímetro en sentido antihorario visto desde arriba (X a la derecha, Y hacia arriba):
        // fila inferior de la imagen (Y=0) de izquierda a derecha, columna derecha hacia arriba,
        // fila superior de derecha a izquierda y columna izquierda hacia abajo.
        int[] ring = new int[perimeter];
        int k = 0;
        for (int i = 0; i < w - 1; i++) {
            ring[k++] = (h - 1) * w + i;
        }
        for (int j = h - 1; j > 0; j--) {
            ring[k++] = j * w + (w - 1);
        }
        for (int i = w - 1; i > 0; i--) {
            ring[k++] = i;
        }
        for (int j = 0; j < h - 1; j++) {
            ring[k++] = j * w;
        }

        // Vértices inferiores del perímetro y centro del fondo.
        for (int r = 0; r < perimeter; r++) {
            int src = ring[r] * 3, dst = (top + r) * 3;
            v[dst] = v[src];
            v[dst + 1] = v[src + 1];
            v[dst + 2] = 0f;
        }
        int center = top + perimeter;
        v[center * 3] = (float) ((w - 1) * step / 2.0);
        v[center * 3 + 1] = (float) ((h - 1) * step / 2.0);
        v[center * 3 + 2] = 0f;

        long triCount = triangleCount(w, h);
        int[] t = new int[Math.toIntExact(triCount * 3)];
        int p = 0;

        // Superficie superior: celda con esquinas (i,j) arriba-izquierda en la imagen.
        for (int j = 0; j < h - 1; j++) {
            for (int i = 0; i < w - 1; i++) {
                int a = j * w + i; // arriba-izquierda (Y mayor)
                int b = a + 1; // arriba-derecha
                int c = a + w; // abajo-izquierda (Y menor)
                int d = c + 1; // abajo-derecha
                // Antihorario visto desde +Z: c -> d -> b y c -> b -> a.
                t[p++] = c;
                t[p++] = d;
                t[p++] = b;
                t[p++] = c;
                t[p++] = b;
                t[p++] = a;
            }
        }

        // Paredes: para cada tramo a->b del perímetro (antihorario), el exterior queda a la derecha.
        for (int r = 0; r < perimeter; r++) {
            int aTop = ring[r], bTop = ring[(r + 1) % perimeter];
            int aBot = top + r, bBot = top + (r + 1) % perimeter;
            t[p++] = aBot;
            t[p++] = bBot;
            t[p++] = bTop;
            t[p++] = aBot;
            t[p++] = bTop;
            t[p++] = aTop;
        }

        // Fondo: abanico con normales hacia -Z.
        for (int r = 0; r < perimeter; r++) {
            int aBot = top + r, bBot = top + (r + 1) % perimeter;
            t[p++] = center;
            t[p++] = bBot;
            t[p++] = aBot;
        }
        return new Mesh(v, t);
    }
}
