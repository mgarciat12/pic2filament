package com.pic2filament.core.height;

/**
 * Mapa de alturas: número de capas de cada vértice de la rejilla (por filas, de arriba a abajo
 * en la imagen).
 */
public record HeightMap(int width, int height, int[] layers) {

    public HeightMap {
        if (layers.length != width * height) {
            throw new IllegalArgumentException("Tamaño de mapa de alturas incoherente");
        }
    }

    public int get(int x, int y) {
        return layers[y * width + x];
    }

    public int max() {
        int m = 0;
        for (int v : layers) {
            m = Math.max(m, v);
        }
        return m;
    }

    public int min() {
        int m = Integer.MAX_VALUE;
        for (int v : layers) {
            m = Math.min(m, v);
        }
        return m;
    }

    /** Filtro de mediana 3×3 (los bordes replican el píxel más cercano). */
    public HeightMap median3x3() {
        int[] out = new int[layers.length];
        int[] win = new int[9];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int k = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = Math.max(0, Math.min(height - 1, y + dy));
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = Math.max(0, Math.min(width - 1, x + dx));
                        win[k++] = layers[yy * width + xx];
                    }
                }
                java.util.Arrays.sort(win);
                out[y * width + x] = win[4];
            }
        }
        return new HeightMap(width, height, out);
    }
}
