package com.pic2filament.core.stack;

import java.util.List;

import com.pic2filament.core.color.ColorMath;
import com.pic2filament.core.model.Filament;
import com.pic2filament.core.model.LayerSettings;

/**
 * Simula el color visible de la pila de capas.
 * <p>
 * Empieza con el color del filamento 1 y, para cada capa n de grosor t con el filamento f activo:
 * <pre>
 *   opacidad = 1 − exp(−3·t / TD_f)
 *   color    = color + (color_f − color) · opacidad      (RGB lineal)
 * </pre>
 */
public final class StackSimulator {

    private StackSimulator() {
    }

    /**
     * @param filaments   filamentos de abajo a arriba
     * @param startLayers capa de inicio (desde 1) de cada filamento; la primera debe ser 1 y deben ser crecientes
     */
    public static StackColors simulate(List<Filament> filaments, int[] startLayers, LayerSettings layers) {
        int f = filaments.size();
        double[][] colors = new double[f][];
        double[] td = new double[f];
        for (int i = 0; i < f; i++) {
            colors[i] = filaments.get(i).linear();
            td[i] = filaments.get(i).td();
        }
        return simulate(colors, td, startLayers, layers);
    }

    /** Versión de bajo nivel (colores en RGB lineal), usada en bucles de optimización. */
    public static StackColors simulate(double[][] filamentLinear, double[] td, int[] startLayers, LayerSettings layers) {
        validateStarts(startLayers, filamentLinear.length, layers.totalLayers());
        int n = layers.totalLayers();
        double[][] linear = new double[n][];
        double[][] lab = new double[n][];
        int[] filamentOfLayer = new int[n];

        double r = filamentLinear[0][0], g = filamentLinear[0][1], b = filamentLinear[0][2];
        int active = 0;
        for (int layer = 1; layer <= n; layer++) {
            while (active + 1 < startLayers.length && startLayers[active + 1] <= layer) {
                active++;
            }
            double t = layers.thickness(layer);
            double opacity = 1.0 - Math.exp(-3.0 * t / td[active]);
            double[] c = filamentLinear[active];
            r += (c[0] - r) * opacity;
            g += (c[1] - g) * opacity;
            b += (c[2] - b) * opacity;
            double[] lin = { r, g, b };
            linear[layer - 1] = lin;
            lab[layer - 1] = ColorMath.linearToLab(lin);
            filamentOfLayer[layer - 1] = active;
        }
        return new StackColors(linear, lab, filamentOfLayer);
    }

    public static void validateStarts(int[] startLayers, int filamentCount, int totalLayers) {
        if (startLayers.length != filamentCount) {
            throw new IllegalArgumentException("Debe indicarse una capa de inicio por filamento");
        }
        if (startLayers[0] != 1) {
            throw new IllegalArgumentException("El primer filamento debe empezar en la capa 1");
        }
        for (int i = 1; i < startLayers.length; i++) {
            if (startLayers[i] <= startLayers[i - 1]) {
                throw new IllegalArgumentException("Las capas de cambio deben ser estrictamente crecientes");
            }
            if (startLayers[i] > totalLayers) {
                throw new IllegalArgumentException(
                        "La capa de inicio del filamento " + (i + 1) + " supera las capas totales (" + totalLayers + ")");
            }
        }
    }
}
