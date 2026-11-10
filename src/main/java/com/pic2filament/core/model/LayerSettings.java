package com.pic2filament.core.model;

/**
 * Geometría de capas de la impresión.
 *
 * @param firstLayerHeight altura de la primera capa (mm)
 * @param layerHeight      altura del resto de capas (mm)
 * @param baseLayers       capas de base (altura mínima de cualquier píxel)
 * @param totalLayers      capas totales (altura máxima)
 */
public record LayerSettings(double firstLayerHeight, double layerHeight, int baseLayers, int totalLayers) {

    public LayerSettings {
        if (!(firstLayerHeight > 0) || !(layerHeight > 0)) {
            throw new IllegalArgumentException("Las alturas de capa deben ser positivas");
        }
        if (baseLayers < 1 || totalLayers <= baseLayers) {
            throw new IllegalArgumentException(
                    "Debe haber al menos una capa de base y más capas totales que de base");
        }
    }

    /** Grosor de la capa n, numerada desde 1. */
    public double thickness(int layerNumber) {
        return layerNumber == 1 ? firstLayerHeight : layerHeight;
    }

    /** Altura Z de la cara superior de la capa con índice {@code index0} (desde 0). */
    public double zTop(int index0) {
        return firstLayerHeight + index0 * layerHeight;
    }

    /** Altura Z de una pieza de {@code layerCount} capas. */
    public double heightOf(int layerCount) {
        return zTop(layerCount - 1);
    }
}
