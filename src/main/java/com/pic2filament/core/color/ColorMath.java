package com.pic2filament.core.color;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Conversiones de color: sRGB (gamma) &lt;-&gt; RGB lineal &lt;-&gt; XYZ &lt;-&gt; CIELAB (iluminante D65).
 * <p>
 * Los colores lineales se representan como {@code double[3]} en [0,1] y los Lab como
 * {@code double[3]} = {L, a, b}. Todos los métodos son estáticos y sin estado.
 */
public final class ColorMath {

    /** Blanco de referencia D65 (2°), normalizado con Y = 1. */
    public static final double XN = 0.95047;
    public static final double YN = 1.0;
    public static final double ZN = 1.08883;

    private static final double DELTA = 6.0 / 29.0;
    private static final double DELTA3 = DELTA * DELTA * DELTA;
    private static final Pattern HEX = Pattern.compile("^#?[0-9a-fA-F]{6}$");

    /** Tabla de sRGB de 8 bits a lineal, para acelerar las conversiones de imágenes. */
    private static final double[] SRGB8_TO_LINEAR = new double[256];

    static {
        for (int i = 0; i < 256; i++) {
            SRGB8_TO_LINEAR[i] = srgbToLinear(i / 255.0);
        }
    }

    private ColorMath() {
    }

    public static double srgbToLinear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public static double linearToSrgb(double c) {
        if (c <= 0) {
            return 0;
        }
        if (c >= 1) {
            return 1;
        }
        return c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
    }

    public static double srgb8ToLinear(int v) {
        return SRGB8_TO_LINEAR[v & 0xFF];
    }

    /** Color empaquetado 0xRRGGBB a RGB lineal. */
    public static double[] rgbIntToLinear(int rgb) {
        return new double[] {
                SRGB8_TO_LINEAR[(rgb >> 16) & 0xFF],
                SRGB8_TO_LINEAR[(rgb >> 8) & 0xFF],
                SRGB8_TO_LINEAR[rgb & 0xFF] };
    }

    /** RGB lineal a color empaquetado 0xRRGGBB (con redondeo y recorte). */
    public static int linearToRgbInt(double[] lin) {
        int r = to8(linearToSrgb(lin[0]));
        int g = to8(linearToSrgb(lin[1]));
        int b = to8(linearToSrgb(lin[2]));
        return (r << 16) | (g << 8) | b;
    }

    private static int to8(double v) {
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255.0);
    }

    public static double[] linearToXyz(double[] lin) {
        double r = lin[0], g = lin[1], b = lin[2];
        return new double[] {
                0.4124564 * r + 0.3575761 * g + 0.1804375 * b,
                0.2126729 * r + 0.7151522 * g + 0.0721750 * b,
                0.0193339 * r + 0.1191920 * g + 0.9503041 * b };
    }

    public static double[] xyzToLinear(double[] xyz) {
        double x = xyz[0], y = xyz[1], z = xyz[2];
        return new double[] {
                3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
                -0.9692660 * x + 1.8760108 * y + 0.0415560 * z,
                0.0556434 * x - 0.2040259 * y + 1.0572252 * z };
    }

    public static double[] xyzToLab(double[] xyz) {
        double fx = f(xyz[0] / XN);
        double fy = f(xyz[1] / YN);
        double fz = f(xyz[2] / ZN);
        return new double[] { 116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz) };
    }

    public static double[] labToXyz(double[] lab) {
        double fy = (lab[0] + 16) / 116.0;
        double fx = fy + lab[1] / 500.0;
        double fz = fy - lab[2] / 200.0;
        return new double[] { XN * fInv(fx), YN * fInv(fy), ZN * fInv(fz) };
    }

    public static double[] linearToLab(double[] lin) {
        return xyzToLab(linearToXyz(lin));
    }

    public static double[] labToLinear(double[] lab) {
        return xyzToLinear(labToXyz(lab));
    }

    public static double[] rgbIntToLab(int rgb) {
        return linearToLab(rgbIntToLinear(rgb));
    }

    public static int labToRgbInt(double[] lab) {
        return linearToRgbInt(labToLinear(lab));
    }

    private static double f(double t) {
        return t > DELTA3 ? Math.cbrt(t) : t / (3 * DELTA * DELTA) + 4.0 / 29.0;
    }

    private static double fInv(double t) {
        return t > DELTA ? t * t * t : 3 * DELTA * DELTA * (t - 4.0 / 29.0);
    }

    /** ¿Es una cadena hexadecimal válida (#RRGGBB o RRGGBB)? */
    public static boolean isHex(String s) {
        return s != null && HEX.matcher(s.trim()).matches();
    }

    /** "#RRGGBB" a 0xRRGGBB. */
    public static int parseHex(String hex) {
        if (!isHex(hex)) {
            throw new IllegalArgumentException("Color hexadecimal no válido: " + hex);
        }
        String h = hex.trim();
        if (h.startsWith("#")) {
            h = h.substring(1);
        }
        return Integer.parseInt(h, 16);
    }

    /** 0xRRGGBB a "#rrggbb". */
    public static String toHex(int rgb) {
        return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
    }

    /** Normaliza una cadena hexadecimal a "#rrggbb". */
    public static String normalizeHex(String hex) {
        return toHex(parseHex(hex));
    }
}
