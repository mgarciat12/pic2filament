package com.pic2filament.core.render;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import com.pic2filament.core.height.HeightMap;
import com.pic2filament.core.model.LayerSettings;
import com.pic2filament.core.stack.StackColors;

/**
 * Genera las imágenes de vista previa a partir del mapa de alturas.
 */
public final class PreviewRenderer {

    /** Exageración vertical del sombreado (el relieve real mide pocos milímetros). */
    private static final double RELIEF_EXAGGERATION = 6.0;

    private PreviewRenderer() {
    }

    /** Simulación de la pieza impresa vista desde arriba: cada píxel toma el color de su altura. */
    public static BufferedImage simulation(HeightMap map, StackColors stack) {
        int[] table = stack.rgbTable();
        int[] px = new int[map.layers().length];
        int[] l = map.layers();
        for (int i = 0; i < px.length; i++) {
            px[i] = table[l[i] - 1];
        }
        return image(map.width(), map.height(), px);
    }

    /** Relieve sombreado en gris, con luz desde arriba a la izquierda. */
    public static BufferedImage shadedRelief(HeightMap map, LayerSettings layers, double step) {
        int w = map.width(), h = map.height();
        int[] px = new int[w * h];
        double lx = -1, ly = -1, lz = 1.2;
        double ln = Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= ln;
        ly /= ln;
        lz /= ln;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double zl = z(map, layers, Math.max(0, x - 1), y), zr = z(map, layers, Math.min(w - 1, x + 1), y);
                double zu = z(map, layers, x, Math.max(0, y - 1)), zd = z(map, layers, x, Math.min(h - 1, y + 1));
                double dzdx = (zr - zl) / (2 * step) * RELIEF_EXAGGERATION;
                double dzdy = (zd - zu) / (2 * step) * RELIEF_EXAGGERATION;
                double nx = -dzdx, ny = -dzdy, nz = 1;
                double nn = Math.sqrt(nx * nx + ny * ny + nz * nz);
                double shade = Math.max(0, (nx * lx + ny * ly + nz * lz) / nn);
                int g = (int) Math.round(255 * Math.min(1, 0.12 + 0.88 * shade));
                px[y * w + x] = (g << 16) | (g << 8) | g;
            }
        }
        return image(w, h, px);
    }

    /** Mapa de alturas en escala de grises (negro = base, blanco = altura máxima). */
    public static BufferedImage heightMap(HeightMap map, int baseLayers, int totalLayers) {
        int[] px = new int[map.layers().length];
        int[] l = map.layers();
        double range = Math.max(1, totalLayers - baseLayers);
        for (int i = 0; i < px.length; i++) {
            int g = (int) Math.round(255 * Math.max(0, Math.min(1, (l[i] - baseLayers) / range)));
            px[i] = (g << 16) | (g << 8) | g;
        }
        return image(map.width(), map.height(), px);
    }

    /** Amplía una imagen para que su lado mayor mida al menos {@code minSide} píxeles (suavizado). */
    public static BufferedImage upscale(BufferedImage src, int minSide) {
        int side = Math.max(src.getWidth(), src.getHeight());
        if (side >= minSide) {
            return src;
        }
        double f = (double) minSide / side;
        int w = (int) Math.round(src.getWidth() * f), h = (int) Math.round(src.getHeight() * f);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static double z(HeightMap map, LayerSettings layers, int x, int y) {
        return layers.heightOf(map.get(x, y));
    }

    private static BufferedImage image(int w, int h, int[] px) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, w, h, px, 0, w);
        return img;
    }
}
