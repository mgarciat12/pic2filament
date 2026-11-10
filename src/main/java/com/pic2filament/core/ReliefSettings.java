package com.pic2filament.core;

import java.util.List;

import com.pic2filament.core.color.ColorDistance;
import com.pic2filament.core.model.Filament;
import com.pic2filament.core.model.LayerSettings;

/**
 * Ajustes completos de una conversión (ya validados).
 *
 * @param widthMm      ancho de la pieza (mm)
 * @param mmPerPixel   detalle deseado (mm por píxel)
 * @param layers       geometría de capas
 * @param filaments    de 2 a 4 filamentos, de abajo a arriba
 * @param startLayers  capas de inicio manuales (desde 1); se ignoran si {@code autoOptimize}
 * @param metric       criterio de ajuste
 * @param contrast     contraste aplicado a L (1 = sin cambio)
 * @param brightness   brillo aplicado a L (0 = sin cambio)
 * @param denoise      aplicar mediana 3×3 al mapa de alturas
 * @param autoOptimize optimizar automáticamente las capas de cambio
 * @param borderMm     ancho del marco alrededor de la pieza (0 = sin marco)
 */
public record ReliefSettings(double widthMm, double mmPerPixel, LayerSettings layers, List<Filament> filaments,
        int[] startLayers, ColorDistance metric, double contrast, double brightness, boolean denoise,
        boolean autoOptimize, double borderMm) {

    public ReliefSettings {
        filaments = List.copyOf(filaments);
        if (filaments.size() < 1) {
            throw new IllegalArgumentException("Se necesita al menos un filamento");
        }
    }
}
