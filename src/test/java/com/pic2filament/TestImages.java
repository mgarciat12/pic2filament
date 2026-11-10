package com.pic2filament;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

import com.pic2filament.core.image.RgbImage;

/** Imágenes sintéticas para las pruebas. */
public final class TestImages {

    private TestImages() {
    }

    /** Degradado horizontal de negro a blanco con un círculo de color en el centro. */
    public static BufferedImage gradient(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, Color.BLACK, w, 0, Color.WHITE));
        g.fillRect(0, 0, w, h);
        g.setPaint(new GradientPaint(0, 0, new Color(200, 40, 40), w, h, new Color(40, 80, 220)));
        g.fillOval(w / 4, h / 4, w / 2, h / 2);
        g.dispose();
        return img;
    }

    public static RgbImage gradientRgb(int w, int h) {
        return RgbImage.fromBufferedImage(gradient(w, h));
    }

    public static byte[] png(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
