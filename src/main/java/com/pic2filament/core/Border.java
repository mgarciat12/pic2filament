package com.pic2filament.core;

import com.pic2filament.core.height.HeightMap;
import com.pic2filament.core.image.RgbImage;

/**
 * Añade un marco alrededor de la pieza: una banda de {@code cells} vértices a la altura máxima.
 * La pieza crece en 2·cells vértices por lado.
 */
final class Border {

    private Border() {
    }

    static HeightMap add(HeightMap map, int cells, int layers) {
        int w = map.width() + 2 * cells, h = map.height() + 2 * cells;
        int[] out = new int[w * h];
        java.util.Arrays.fill(out, layers);
        for (int y = 0; y < map.height(); y++) {
            System.arraycopy(map.layers(), y * map.width(), out, (y + cells) * w + cells, map.width());
        }
        return new HeightMap(w, h, out);
    }

    static RgbImage add(RgbImage img, int cells, int rgb) {
        int w = img.width() + 2 * cells, h = img.height() + 2 * cells;
        int[] out = new int[w * h];
        java.util.Arrays.fill(out, rgb);
        for (int y = 0; y < img.height(); y++) {
            System.arraycopy(img.pixels(), y * img.width(), out, (y + cells) * w + cells, img.width());
        }
        return new RgbImage(w, h, out);
    }
}
