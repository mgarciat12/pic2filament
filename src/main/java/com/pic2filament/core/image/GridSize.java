package com.pic2filament.core.image;

/**
 * Tamaño de la rejilla del relieve. Cada píxel de la rejilla es un vértice de la malla, separados
 * {@code step} mm, de modo que la pieza mide exactamente {@code (columns-1)·step = ancho} mm.
 *
 * @param columns vértices en X
 * @param rows    vértices en Y
 * @param step    separación entre vértices (mm)
 */
public record GridSize(int columns, int rows, double step) {

    public double widthMm() {
        return (columns - 1) * step;
    }

    public double depthMm() {
        return (rows - 1) * step;
    }

    /**
     * Calcula la rejilla para una imagen.
     *
     * @param imageWidth  ancho de la imagen en píxeles
     * @param imageHeight alto de la imagen en píxeles
     * @param widthMm     ancho deseado de la pieza
     * @param mmPerPixel  detalle deseado
     * @param maxGrid     límite de vértices por lado
     */
    public static GridSize compute(int imageWidth, int imageHeight, double widthMm, double mmPerPixel, int maxGrid) {
        if (!(widthMm > 0) || !(mmPerPixel > 0) || maxGrid < 2) {
            throw new IllegalArgumentException("Parámetros de rejilla no válidos");
        }
        double aspect = (double) imageHeight / imageWidth;
        int cols = (int) Math.round(widthMm / mmPerPixel) + 1;
        cols = Math.max(2, Math.min(maxGrid, cols));
        int rows = (int) Math.round((cols - 1) * aspect) + 1;
        if (rows > maxGrid) {
            rows = maxGrid;
            cols = Math.max(2, (int) Math.round((rows - 1) / aspect) + 1);
        }
        rows = Math.max(2, rows);
        return new GridSize(cols, rows, widthMm / (cols - 1));
    }
}
