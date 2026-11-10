package com.pic2filament.core.image;

/**
 * Imagen en CIELAB, como tres planos de floats por filas.
 */
public record LabImage(int width, int height, float[] l, float[] a, float[] b) {

    public int size() {
        return width * height;
    }
}
