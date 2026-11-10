package com.pic2filament.core.image;

import java.awt.image.BufferedImage;

import com.pic2filament.core.color.ColorMath;

/**
 * Imagen opaca en memoria: píxeles 0xRRGGBB por filas, de arriba a abajo.
 */
public record RgbImage(int width, int height, int[] pixels) {

    public RgbImage {
        if (width <= 0 || height <= 0 || pixels.length != width * height) {
            throw new IllegalArgumentException("Dimensiones de imagen no válidas");
        }
    }

    public int get(int x, int y) {
        return pixels[y * width + x];
    }

    /**
     * Convierte una imagen cualquiera a RGB opaco, aplanando la transparencia sobre blanco.
     */
    public static RgbImage fromBufferedImage(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
        boolean alpha = img.getColorModel().hasAlpha();
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            if (alpha) {
                int a = (p >>> 24) & 0xFF;
                if (a < 255) {
                    int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                    r = (r * a + 255 * (255 - a) + 127) / 255;
                    g = (g * a + 255 * (255 - a) + 127) / 255;
                    b = (b * a + 255 * (255 - a) + 127) / 255;
                    p = (r << 16) | (g << 8) | b;
                }
            }
            argb[i] = p & 0xFFFFFF;
        }
        return new RgbImage(w, h, argb);
    }

    public BufferedImage toBufferedImage() {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, width, height, pixels, 0, width);
        return out;
    }

    /**
     * Redimensiona la imagen. Al reducir promedia el área cubierta por cada píxel destino
     * (en RGB lineal, para no oscurecer los bordes); al ampliar interpola bilinealmente.
     */
    public RgbImage resize(int newWidth, int newHeight) {
        if (newWidth == width && newHeight == height) {
            return this;
        }
        int[] out = new int[newWidth * newHeight];
        double sx = (double) width / newWidth;
        double sy = (double) height / newHeight;
        if (sx >= 1 && sy >= 1) {
            areaAverage(out, newWidth, newHeight, sx, sy);
        } else {
            bilinear(out, newWidth, newHeight, sx, sy);
        }
        return new RgbImage(newWidth, newHeight, out);
    }

    private void areaAverage(int[] out, int nw, int nh, double sx, double sy) {
        for (int y = 0; y < nh; y++) {
            double y0 = y * sy, y1 = y0 + sy;
            int iy0 = (int) Math.floor(y0), iy1 = Math.min(height, (int) Math.ceil(y1));
            for (int x = 0; x < nw; x++) {
                double x0 = x * sx, x1 = x0 + sx;
                int ix0 = (int) Math.floor(x0), ix1 = Math.min(width, (int) Math.ceil(x1));
                double r = 0, g = 0, b = 0, wsum = 0;
                for (int yy = iy0; yy < iy1; yy++) {
                    double wy = Math.min(yy + 1, y1) - Math.max(yy, y0);
                    for (int xx = ix0; xx < ix1; xx++) {
                        double wx = Math.min(xx + 1, x1) - Math.max(xx, x0);
                        double wgt = wx * wy;
                        int p = pixels[yy * width + xx];
                        r += ColorMath.srgb8ToLinear(p >> 16) * wgt;
                        g += ColorMath.srgb8ToLinear(p >> 8) * wgt;
                        b += ColorMath.srgb8ToLinear(p) * wgt;
                        wsum += wgt;
                    }
                }
                out[y * nw + x] = ColorMath.linearToRgbInt(new double[] { r / wsum, g / wsum, b / wsum });
            }
        }
    }

    private void bilinear(int[] out, int nw, int nh, double sx, double sy) {
        for (int y = 0; y < nh; y++) {
            double fy = Math.max(0, Math.min(height - 1, (y + 0.5) * sy - 0.5));
            int y0 = (int) fy, y1 = Math.min(height - 1, y0 + 1);
            double ty = fy - y0;
            for (int x = 0; x < nw; x++) {
                double fx = Math.max(0, Math.min(width - 1, (x + 0.5) * sx - 0.5));
                int x0 = (int) fx, x1 = Math.min(width - 1, x0 + 1);
                double tx = fx - x0;
                int p00 = pixels[y0 * width + x0], p10 = pixels[y0 * width + x1];
                int p01 = pixels[y1 * width + x0], p11 = pixels[y1 * width + x1];
                int rgb = 0;
                for (int shift = 16; shift >= 0; shift -= 8) {
                    double top = ((p00 >> shift) & 0xFF) * (1 - tx) + ((p10 >> shift) & 0xFF) * tx;
                    double bot = ((p01 >> shift) & 0xFF) * (1 - tx) + ((p11 >> shift) & 0xFF) * tx;
                    int v = (int) Math.round(top * (1 - ty) + bot * ty);
                    rgb |= Math.max(0, Math.min(255, v)) << shift;
                }
                out[y * nw + x] = rgb;
            }
        }
    }

    /** Planos L, a, b (CIELAB D65) de todos los píxeles. */
    public LabImage toLab() {
        int n = pixels.length;
        float[] l = new float[n], a = new float[n], b = new float[n];
        java.util.Map<Integer, double[]> cache = new java.util.HashMap<>();
        for (int i = 0; i < n; i++) {
            double[] lab = cache.computeIfAbsent(pixels[i], ColorMath::rgbIntToLab);
            l[i] = (float) lab[0];
            a[i] = (float) lab[1];
            b[i] = (float) lab[2];
        }
        return new LabImage(width, height, l, a, b);
    }
}
