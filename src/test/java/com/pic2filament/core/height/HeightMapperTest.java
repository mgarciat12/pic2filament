package com.pic2filament.core.height;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.pic2filament.TestImages;
import com.pic2filament.core.color.ColorDistance;
import com.pic2filament.core.image.LabImage;
import com.pic2filament.core.image.RgbImage;
import com.pic2filament.core.model.Filament;
import com.pic2filament.core.model.LayerSettings;
import com.pic2filament.core.stack.StackColors;
import com.pic2filament.core.stack.StackSimulator;

class HeightMapperTest {

    private static final LayerSettings LAYERS = new LayerSettings(0.16, 0.08, 4, 28);
    private static final List<Filament> BW = List.of(Filament.of("negro", "#000000", 0.6),
            Filament.of("blanco", "#ffffff", 3.0));

    @Test
    void unPixelConElColorDeUnaAlturaRecibeEsaAltura() {
        StackColors stack = StackSimulator.simulate(BW, new int[] { 1, 5 }, LAYERS);
        int[] targets = { 4, 7, 12, 20, 28 };
        int[] px = new int[targets.length];
        for (int i = 0; i < targets.length; i++) {
            px[i] = stack.rgbAt(targets[i]);
        }
        LabImage lab = new RgbImage(targets.length, 1, px).toLab();
        for (ColorDistance metric : ColorDistance.values()) {
            HeightMap m = HeightMapper.assign(lab, stack, 4, metric, 1, 0);
            for (int i = 0; i < targets.length; i++) {
                // Colores casi iguales pueden coincidir al redondear a 8 bits: comprobamos el color.
                assertThat(stack.rgbAt(m.get(i, 0))).isEqualTo(px[i]);
            }
        }
    }

    @Test
    void lasAlturasQuedanEntreLaBaseYElTotal() {
        StackColors stack = StackSimulator.simulate(BW, new int[] { 1, 5 }, LAYERS);
        HeightMap m = HeightMapper.assign(TestImages.gradientRgb(120, 80).toLab(), stack, 4, ColorDistance.COLOR, 1, 0);
        assertThat(m.min()).isGreaterThanOrEqualTo(4);
        assertThat(m.max()).isLessThanOrEqualTo(28);
        assertThat(m.min()).isEqualTo(4);
        assertThat(m.max()).isGreaterThan(20);
    }

    @Test
    void elBrilloSubeLasAlturas() {
        StackColors stack = StackSimulator.simulate(BW, new int[] { 1, 5 }, LAYERS);
        LabImage lab = new RgbImage(1, 1, new int[] { 0x606060 }).toLab();
        int normal = HeightMapper.assign(lab, stack, 4, ColorDistance.LUMINANCE, 1, 0).get(0, 0);
        int brighter = HeightMapper.assign(lab, stack, 4, ColorDistance.LUMINANCE, 1, 20).get(0, 0);
        assertThat(brighter).isGreaterThan(normal);
    }

    @Test
    void laMedianaEliminaPicosAislados() {
        int[] l = new int[25];
        java.util.Arrays.fill(l, 5);
        l[12] = 28;
        HeightMap m = new HeightMap(5, 5, l).median3x3();
        assertThat(m.layers()).containsOnly(5);
    }

    @Test
    void laMedianaConservaBordesRectos() {
        int[] l = new int[36];
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 6; x++) {
                l[y * 6 + x] = x < 3 ? 4 : 20;
            }
        }
        HeightMap m = new HeightMap(6, 6, l).median3x3();
        assertThat(m.layers()).isEqualTo(l);
    }
}
