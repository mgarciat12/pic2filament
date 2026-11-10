package com.pic2filament.core.optimize;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import com.pic2filament.core.color.ColorMath;
import com.pic2filament.core.image.RgbImage;

/**
 * Detecta los colores dominantes de una imagen con k-means en CIELAB (inicialización k-means++
 * determinista) y los devuelve ordenados de oscuro a claro.
 */
public final class ColorExtractor {

    private static final int MAX_SAMPLES = 20_000;
    private static final int ITERATIONS = 30;

    private ColorExtractor() {
    }

    /** Colores detectados en 0xRRGGBB, de oscuro a claro. */
    public static List<Integer> dominantColors(RgbImage image, int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k debe ser al menos 1");
        }
        double[][] samples = sample(image);
        Random rnd = new Random(42);
        double[][] centers = initPlusPlus(samples, k, rnd);
        int[] assign = new int[samples.length];
        for (int it = 0; it < ITERATIONS; it++) {
            boolean changed = false;
            for (int i = 0; i < samples.length; i++) {
                int c = nearest(samples[i], centers);
                if (c != assign[i] || it == 0) {
                    changed |= c != assign[i];
                    assign[i] = c;
                }
            }
            double[][] sum = new double[centers.length][3];
            int[] cnt = new int[centers.length];
            for (int i = 0; i < samples.length; i++) {
                int c = assign[i];
                sum[c][0] += samples[i][0];
                sum[c][1] += samples[i][1];
                sum[c][2] += samples[i][2];
                cnt[c]++;
            }
            for (int c = 0; c < centers.length; c++) {
                if (cnt[c] > 0) {
                    centers[c] = new double[] { sum[c][0] / cnt[c], sum[c][1] / cnt[c], sum[c][2] / cnt[c] };
                }
            }
            if (!changed && it > 0) {
                break;
            }
        }
        List<double[]> sorted = new ArrayList<>(List.of(centers));
        sorted.sort(Comparator.comparingDouble(c -> c[0]));
        List<Integer> out = new ArrayList<>();
        for (double[] c : sorted) {
            out.add(ColorMath.labToRgbInt(c));
        }
        return out;
    }

    private static double[][] sample(RgbImage image) {
        int[] px = image.pixels();
        int stride = Math.max(1, px.length / MAX_SAMPLES);
        int n = (px.length + stride - 1) / stride;
        double[][] s = new double[n][];
        java.util.HashMap<Integer, double[]> cache = new java.util.HashMap<>();
        for (int i = 0, j = 0; i < px.length && j < n; i += stride, j++) {
            s[j] = cache.computeIfAbsent(px[i], ColorMath::rgbIntToLab);
        }
        return s;
    }

    private static double[][] initPlusPlus(double[][] samples, int k, Random rnd) {
        double[][] centers = new double[k][];
        centers[0] = samples[rnd.nextInt(samples.length)].clone();
        double[] d2 = new double[samples.length];
        for (int c = 1; c < k; c++) {
            double sum = 0;
            for (int i = 0; i < samples.length; i++) {
                double best = Double.MAX_VALUE;
                for (int j = 0; j < c; j++) {
                    best = Math.min(best, dist2(samples[i], centers[j]));
                }
                d2[i] = best;
                sum += best;
            }
            if (sum == 0) {
                // Imagen con menos colores distintos que k: repetimos el último.
                centers[c] = centers[c - 1].clone();
                continue;
            }
            double r = rnd.nextDouble() * sum;
            int pick = samples.length - 1;
            for (int i = 0; i < samples.length; i++) {
                r -= d2[i];
                if (r <= 0) {
                    pick = i;
                    break;
                }
            }
            centers[c] = samples[pick].clone();
        }
        return centers;
    }

    private static int nearest(double[] p, double[][] centers) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int c = 0; c < centers.length; c++) {
            double d = dist2(p, centers[c]);
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        return best;
    }

    private static double dist2(double[] p, double[] q) {
        double dl = p[0] - q[0], da = p[1] - q[1], db = p[2] - q[2];
        return dl * dl + da * da + db * db;
    }
}
