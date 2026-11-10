package com.pic2filament.core.stack;

import com.pic2filament.core.color.ColorMath;

/**
 * Tabla "altura → color": para cada número de capas impresas (1..N), el color visible desde arriba.
 * Los índices de los arrays son {@code capas - 1}.
 */
public record StackColors(double[][] linear, double[][] lab, int[] filamentOfLayer) {

    public int layers() {
        return linear.length;
    }

    /** Color visible (0xRRGGBB) de una pieza de {@code layerCount} capas. */
    public int rgbAt(int layerCount) {
        return ColorMath.linearToRgbInt(linear[layerCount - 1]);
    }

    public double[] labAt(int layerCount) {
        return lab[layerCount - 1];
    }

    /** Índice (desde 0) del filamento usado en la capa {@code layerNumber} (desde 1). */
    public int filamentAt(int layerNumber) {
        return filamentOfLayer[layerNumber - 1];
    }

    /** Colores visibles empaquetados, índice = capas - 1. */
    public int[] rgbTable() {
        int[] t = new int[linear.length];
        for (int i = 0; i < t.length; i++) {
            t[i] = ColorMath.linearToRgbInt(linear[i]);
        }
        return t;
    }
}
