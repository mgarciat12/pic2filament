package com.pic2filament.core.optimize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.pic2filament.core.color.ColorDistance;
import com.pic2filament.core.model.Filament;
import com.pic2filament.core.model.LayerSettings;
import com.pic2filament.core.stack.StackColors;
import com.pic2filament.core.stack.StackSimulator;

/**
 * Busca las capas de cambio de filamento (crecientes, la primera siempre en la capa 1) que
 * minimizan el error total entre los colores de la imagen y los colores alcanzables.
 * <p>
 * Estrategia: búsqueda exhaustiva sobre una rejilla gruesa de capas candidatas (limitada a unas
 * pocas miles de combinaciones), partiendo también del reparto uniforme, y después refinado
 * local por coordenadas con paso 1 hasta que no mejora.
 */
public final class ChangeOptimizer {

    /** Máximo de combinaciones a evaluar en la fase gruesa. */
    static final int MAX_COARSE_COMBINATIONS = 4000;

    private final double[][] filamentLinear;
    private final double[] td;
    private final LayerSettings layers;
    private final ColorBuckets buckets;
    private final ColorDistance metric;
    private final double[] adjustedL;
    private int evaluations;

    public ChangeOptimizer(List<Filament> filaments, LayerSettings layers, ColorBuckets buckets,
            ColorDistance metric, double contrast, double brightness) {
        this.filamentLinear = new double[filaments.size()][];
        this.td = new double[filaments.size()];
        for (int i = 0; i < filaments.size(); i++) {
            filamentLinear[i] = filaments.get(i).linear();
            td[i] = filaments.get(i).td();
        }
        this.layers = layers;
        this.buckets = buckets;
        this.metric = metric;
        this.adjustedL = new double[buckets.size()];
        for (int i = 0; i < adjustedL.length; i++) {
            adjustedL[i] = ColorDistance.adjustL(buckets.l()[i], contrast, brightness);
        }
    }

    /** Resultado de la optimización: capas de inicio (desde 1) y error ponderado medio. */
    public record Result(int[] startLayers, double error, int evaluations) {
    }

    public Result optimize() {
        int f = filamentLinear.length;
        int n = layers.totalLayers();
        if (f == 1) {
            return new Result(new int[] { 1 }, error(new int[] { 1 }), evaluations);
        }
        if (n < f) {
            throw new IllegalArgumentException("Hay más filamentos que capas");
        }

        int[] best = uniform(f, n);
        double bestErr = error(best);

        // Fase gruesa: combinaciones crecientes sobre valores candidatos.
        int[] candidates = coarseCandidates(n, f - 1);
        int[] combo = new int[f - 1];
        int[] trial = new int[f];
        trial[0] = 1;
        List<int[]> all = new ArrayList<>();
        enumerate(candidates, 0, 0, combo, all);
        for (int[] c : all) {
            System.arraycopy(c, 0, trial, 1, c.length);
            double e = error(trial);
            if (e < bestErr) {
                bestErr = e;
                best = trial.clone();
            }
        }

        // Refinado local por coordenadas.
        boolean improved = true;
        for (int round = 0; round < 20 && improved; round++) {
            improved = false;
            for (int k = 1; k < f; k++) {
                int lo = best[k - 1] + 1;
                int hi = k + 1 < f ? best[k + 1] - 1 : n;
                int[] t = best.clone();
                for (int v = lo; v <= hi; v++) {
                    if (v == best[k]) {
                        continue;
                    }
                    t[k] = v;
                    double e = error(t);
                    if (e < bestErr - 1e-9) {
                        bestErr = e;
                        best = t.clone();
                        improved = true;
                    }
                }
            }
        }
        return new Result(best, bestErr, evaluations);
    }

    /** Reparto uniforme de los cambios entre la capa 1 y la última. */
    public static int[] uniform(int filaments, int totalLayers) {
        int[] s = new int[filaments];
        s[0] = 1;
        for (int k = 1; k < filaments; k++) {
            int v = 1 + (int) Math.round((double) k * totalLayers / filaments);
            s[k] = Math.max(s[k - 1] + 1, Math.min(totalLayers - (filaments - 1 - k), v));
        }
        return s;
    }

    /** Error medio ponderado (por píxel) de una configuración de cambios. */
    public double error(int[] startLayers) {
        evaluations++;
        StackColors stack = StackSimulator.simulate(filamentLinear, td, startLayers, layers);
        int from = layers.baseLayers();
        int n = layers.totalLayers();
        int count = n - from + 1;
        double[] sl = new double[count], sa = new double[count], sb = new double[count];
        for (int i = 0; i < count; i++) {
            double[] lab = stack.labAt(from + i);
            sl[i] = lab[0];
            sa[i] = lab[1];
            sb[i] = lab[2];
        }
        double total = 0, wsum = 0;
        double[] bl = adjustedL, ba = buckets.a(), bb = buckets.b(), w = buckets.weights();
        for (int j = 0; j < bl.length; j++) {
            double best = Double.MAX_VALUE;
            for (int i = 0; i < count; i++) {
                double d = metric.distance(bl[j], ba[j], bb[j], sl[i], sa[i], sb[i]);
                if (d < best) {
                    best = d;
                }
            }
            total += best * w[j];
            wsum += w[j];
        }
        return wsum == 0 ? 0 : total / wsum;
    }

    /** Valores candidatos (2..n) con el paso más fino que mantenga las combinaciones acotadas. */
    static int[] coarseCandidates(int n, int choose) {
        int step = 1;
        while (true) {
            int[] vals = values(n, step);
            if (combinations(vals.length, choose) <= MAX_COARSE_COMBINATIONS || vals.length <= choose) {
                return vals;
            }
            step++;
        }
    }

    private static int[] values(int n, int step) {
        List<Integer> v = new ArrayList<>();
        for (int x = 2; x <= n; x += step) {
            v.add(x);
        }
        if (v.get(v.size() - 1) != n) {
            v.add(n);
        }
        return v.stream().mapToInt(Integer::intValue).toArray();
    }

    static double combinations(int n, int k) {
        double c = 1;
        for (int i = 0; i < k; i++) {
            c = c * (n - i) / (i + 1);
        }
        return c;
    }

    private static void enumerate(int[] vals, int from, int depth, int[] combo, List<int[]> out) {
        if (depth == combo.length) {
            out.add(Arrays.copyOf(combo, combo.length));
            return;
        }
        for (int i = from; i <= vals.length - (combo.length - depth); i++) {
            combo[depth] = vals[i];
            enumerate(vals, i + 1, depth + 1, combo, out);
        }
    }
}
