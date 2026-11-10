package com.pic2filament.core;

import java.util.List;

import com.pic2filament.core.export.ColorChange;
import com.pic2filament.core.height.HeightMap;
import com.pic2filament.core.image.GridSize;
import com.pic2filament.core.image.RgbImage;
import com.pic2filament.core.stack.StackColors;

/**
 * Resultado de una conversión.
 *
 * @param settings     ajustes usados
 * @param grid         rejilla (sin marco)
 * @param original     imagen original redimensionada a la rejilla
 * @param heights      mapa de alturas en capas (incluye el marco, si lo hay)
 * @param step         separación entre vértices (mm)
 * @param stack        tabla altura → color
 * @param startLayers  capas de inicio usadas (desde 1)
 * @param changes      tabla de cambios de filamento
 * @param error        error medio ponderado de la configuración
 * @param elapsedMs    tiempo de cálculo
 */
public record ReliefResult(ReliefSettings settings, GridSize grid, RgbImage original, HeightMap heights, double step,
        StackColors stack, int[] startLayers, List<ColorChange> changes, double error, long elapsedMs) {

    public double widthMm() {
        return (heights.width() - 1) * step;
    }

    public double depthMm() {
        return (heights.height() - 1) * step;
    }

    public double maxHeightMm() {
        return settings.layers().heightOf(heights.max());
    }
}
