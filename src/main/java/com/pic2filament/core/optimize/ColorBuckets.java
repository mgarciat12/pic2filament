package com.pic2filament.core.optimize;

import com.pic2filament.core.color.ColorMath;
import com.pic2filament.core.image.RgbImage;

/**
 * Colores de una imagen agrupados en cubos (4 bits por canal sRGB), con su color medio en Lab
 * y su peso (número de píxeles). Sirve para evaluar rápidamente el error de una configuración.
 *
 * @param l       L medio de cada cubo
 * @param a       a medio de cada cubo
 * @param b       b medio de cada cubo
 * @param weights píxeles de cada cubo
 */
public record ColorBuckets(double[] l, double[] a, double[] b, double[] weights) {

    public int size() {
        return weights.length;
    }

    public static ColorBuckets of(RgbImage image) {
        final int cells = 4096;
        double[] sl = new double[cells], sa = new double[cells], sb = new double[cells];
        int[] count = new int[cells];
        java.util.HashMap<Integer, double[]> labCache = new java.util.HashMap<>();
        for (int p : image.pixels()) {
            int key = ((p >> 12) & 0xF00) | ((p >> 8) & 0xF0) | ((p >> 4) & 0xF);
            double[] lab = labCache.computeIfAbsent(p & 0xFFFFFF, ColorMath::rgbIntToLab);
            sl[key] += lab[0];
            sa[key] += lab[1];
            sb[key] += lab[2];
            count[key]++;
        }
        int used = 0;
        for (int c : count) {
            if (c > 0) {
                used++;
            }
        }
        double[] l = new double[used], a = new double[used], b = new double[used], w = new double[used];
        int i = 0;
        for (int k = 0; k < cells; k++) {
            if (count[k] > 0) {
                l[i] = sl[k] / count[k];
                a[i] = sa[k] / count[k];
                b[i] = sb[k] / count[k];
                w[i] = count[k];
                i++;
            }
        }
        return new ColorBuckets(l, a, b, w);
    }
}
