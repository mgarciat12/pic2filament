package com.pic2filament.core.color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ColorMathTest {

    @ParameterizedTest(name = "#{0} -> Lab({1}, {2}, {3})")
    @CsvSource({
            // Valores de referencia sRGB D65 (Bruce Lindbloom / CIE)
            "ffffff, 100.000,   0.000,    0.000",
            "000000,   0.000,   0.000,    0.000",
            "ff0000,  53.241,  80.092,   67.203",
            "00ff00,  87.735, -86.183,   83.179",
            "0000ff,  32.297,  79.188, -107.860",
            "808080,  53.585,   0.000,    0.000",
            "ffff00,  97.139, -21.554,   94.478" })
    void srgbALabConValoresDeReferencia(String hex, double l, double a, double b) {
        double[] lab = ColorMath.rgbIntToLab(ColorMath.parseHex(hex));
        assertThat(lab[0]).isCloseTo(l, within(0.02));
        assertThat(lab[1]).isCloseTo(a, within(0.05));
        assertThat(lab[2]).isCloseTo(b, within(0.05));
    }

    @Test
    void srgbYLinealSonInversas() {
        for (int i = 0; i <= 255; i++) {
            double lin = ColorMath.srgb8ToLinear(i);
            assertThat(ColorMath.linearToSrgb(lin) * 255).isCloseTo(i, within(1e-6));
        }
        assertThat(ColorMath.srgbToLinear(0.5)).isCloseTo(0.214041, within(1e-6));
    }

    @Test
    void labAColorYVueltaConservaElColor() {
        for (int rgb : new int[] { 0x123456, 0xff8800, 0x00ffcc, 0x7f7f7f, 0xfafafa, 0x010203 }) {
            assertThat(ColorMath.labToRgbInt(ColorMath.rgbIntToLab(rgb))).isEqualTo(rgb);
        }
    }

    @Test
    void hexadecimal() {
        assertThat(ColorMath.parseHex("#FF8000")).isEqualTo(0xff8000);
        assertThat(ColorMath.parseHex("00ff00")).isEqualTo(0x00ff00);
        assertThat(ColorMath.toHex(0x0a0b0c)).isEqualTo("#0a0b0c");
        assertThat(ColorMath.isHex("#12345")).isFalse();
        assertThat(ColorMath.isHex("#gg0000")).isFalse();
        assertThatThrownBy(() -> ColorMath.parseHex("rojo")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void distanciasYAjusteDeLuminosidad() {
        assertThat(ColorDistance.COLOR.distance(50, 10, 0, 40, 0, 5)).isCloseTo(1.4 * 100 + 100 + 25, within(1e-9));
        assertThat(ColorDistance.LUMINANCE.distance(50, 10, 0, 40, 0, 5)).isCloseTo(100, within(1e-9));
        assertThat(ColorDistance.adjustL(60, 2, 0)).isCloseTo(70, within(1e-9));
        assertThat(ColorDistance.adjustL(60, 1, 10)).isCloseTo(70, within(1e-9));
        assertThat(ColorDistance.adjustL(90, 3, 0)).isEqualTo(100);
        assertThat(ColorDistance.adjustL(10, 3, 0)).isEqualTo(0);
    }
}
