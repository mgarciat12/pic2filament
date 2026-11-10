package com.pic2filament.core.model;

import com.pic2filament.core.color.ColorMath;

/**
 * Filamento de la pila.
 *
 * @param name  nombre descriptivo
 * @param rgb   color sRGB empaquetado 0xRRGGBB
 * @param td    distancia de transmisión (mm): grosor a partir del cual el filamento es casi opaco
 */
public record Filament(String name, int rgb, double td) {

    public Filament {
        if (!(td > 0)) {
            throw new IllegalArgumentException("El TD debe ser positivo");
        }
        name = name == null || name.isBlank() ? ColorMath.toHex(rgb) : name;
    }

    public static Filament of(String name, String hex, double td) {
        return new Filament(name, ColorMath.parseHex(hex), td);
    }

    public String hex() {
        return ColorMath.toHex(rgb);
    }

    public double[] linear() {
        return ColorMath.rgbIntToLinear(rgb);
    }
}
