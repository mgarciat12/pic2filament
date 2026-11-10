package com.pic2filament.core.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

class ImageTest {

    @Test
    void laRejillaPorDefectoMideExactamenteElAnchoPedido() {
        GridSize g = GridSize.compute(800, 600, 100, 0.25, 600);
        assertThat(g.columns()).isEqualTo(401);
        assertThat(g.rows()).isEqualTo(301);
        assertThat(g.widthMm()).isCloseTo(100, within(1e-9));
        assertThat(g.depthMm()).isCloseTo(75, within(1e-9));
    }

    @Test
    void laRejillaSeLimitaA600() {
        GridSize g = GridSize.compute(1000, 1000, 200, 0.1, 600);
        assertThat(g.columns()).isEqualTo(600);
        assertThat(g.rows()).isEqualTo(600);
        assertThat(g.widthMm()).isCloseTo(200, within(1e-9));
        GridSize tall = GridSize.compute(500, 2000, 100, 0.1, 600);
        assertThat(tall.rows()).isEqualTo(600);
        assertThat(tall.columns()).isLessThan(600);
        assertThat(tall.widthMm()).isCloseTo(100, within(1e-9));
    }

    @Test
    void laTransparenciaSeAplanaSobreBlanco() {
        BufferedImage img = new BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0x00000000); // transparente
        img.setRGB(1, 0, 0xff000000); // negro opaco
        img.setRGB(2, 0, 0x80ff0000); // rojo semitransparente
        RgbImage r = RgbImage.fromBufferedImage(img);
        assertThat(r.get(0, 0)).isEqualTo(0xffffff);
        assertThat(r.get(1, 0)).isEqualTo(0x000000);
        assertThat(r.get(2, 0)).isEqualTo(0xff7f7f);
    }

    @Test
    void redimensionarPromediaYAmplia() {
        RgbImage img = new RgbImage(2, 1, new int[] { 0x000000, 0xffffff });
        RgbImage half = img.resize(1, 1);
        // Promedio en lineal de negro y blanco: 0,5 lineal = 188 en sRGB.
        assertThat(half.get(0, 0) & 0xff).isEqualTo(188);
        RgbImage big = img.resize(8, 4);
        assertThat(big.width()).isEqualTo(8);
        assertThat(big.get(0, 0)).isEqualTo(0x000000);
        assertThat(big.get(7, 3)).isEqualTo(0xffffff);
    }
}
