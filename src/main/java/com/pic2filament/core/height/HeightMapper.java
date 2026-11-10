package com.pic2filament.core.height;

import com.pic2filament.core.color.ColorDistance;
import com.pic2filament.core.image.LabImage;
import com.pic2filament.core.stack.StackColors;

/**
 * Asigna a cada píxel la altura (en capas, entre la última de base y la última) cuyo color
 * simulado está más cerca en Lab.
 */
public final class HeightMapper {

    private HeightMapper() {
    }

    public static HeightMap assign(LabImage image, StackColors stack, int baseLayers,
            ColorDistance metric, double contrast, double brightness) {
        int total = stack.layers();
        int from = Math.max(1, baseLayers);
        int count = total - from + 1;
        double[] sl = new double[count], sa = new double[count], sb = new double[count];
        for (int i = 0; i < count; i++) {
            double[] lab = stack.labAt(from + i);
            sl[i] = lab[0];
            sa[i] = lab[1];
            sb[i] = lab[2];
        }

        int n = image.size();
        int[] out = new int[n];
        float[] pl = image.l(), pa = image.a(), pb = image.b();
        for (int p = 0; p < n; p++) {
            double l = ColorDistance.adjustL(pl[p], contrast, brightness);
            out[p] = from + nearest(l, pa[p], pb[p], sl, sa, sb, metric);
        }
        return new HeightMap(image.width(), image.height(), out);
    }

    static int nearest(double l, double a, double b, double[] sl, double[] sa, double[] sb, ColorDistance metric) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < sl.length; i++) {
            double d = metric.distance(l, a, b, sl[i], sa[i], sb[i]);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }
}
