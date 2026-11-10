package com.pic2filament.core.optimize;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.pic2filament.TestImages;
import com.pic2filament.core.color.ColorDistance;
import com.pic2filament.core.image.RgbImage;
import com.pic2filament.core.model.Filament;
import com.pic2filament.core.model.LayerSettings;

class ChangeOptimizerTest {

    private static final List<Filament> FOUR = List.of(Filament.of("negro", "#000000", 0.6),
            Filament.of("rojo", "#c0262b", 2.5), Filament.of("azul", "#1e4fb4", 1.5),
            Filament.of("blanco", "#ffffff", 4.5));

    @Test
    void losCambiosSalenCrecientesYDentroDeRango() {
        LayerSettings layers = new LayerSettings(0.16, 0.08, 4, 28);
        ColorBuckets buckets = ColorBuckets.of(TestImages.gradientRgb(200, 150));
        for (ColorDistance metric : ColorDistance.values()) {
            ChangeOptimizer.Result r = new ChangeOptimizer(FOUR, layers, buckets, metric, 1, 0).optimize();
            int[] s = r.startLayers();
            assertThat(s).hasSize(4);
            assertThat(s[0]).isEqualTo(1);
            for (int i = 1; i < s.length; i++) {
                assertThat(s[i]).isGreaterThan(s[i - 1]).isLessThanOrEqualTo(28);
            }
        }
    }

    @Test
    void noEmpeoraElErrorFrenteAlRepartoUniforme() {
        LayerSettings layers = new LayerSettings(0.16, 0.08, 4, 28);
        ColorBuckets buckets = ColorBuckets.of(TestImages.gradientRgb(200, 150));
        ChangeOptimizer opt = new ChangeOptimizer(FOUR, layers, buckets, ColorDistance.COLOR, 1, 0);
        double uniform = opt.error(ChangeOptimizer.uniform(4, 28));
        ChangeOptimizer.Result r = opt.optimize();
        assertThat(r.error()).isLessThanOrEqualTo(uniform);
        assertThat(r.error()).isCloseTo(opt.error(r.startLayers()), org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    void conUnaImagenDeDosColoresElegiblesElErrorEsCasiCero() {
        LayerSettings layers = new LayerSettings(0.16, 0.08, 2, 20);
        List<Filament> bw = List.of(Filament.of("negro", "#000000", 0.6), Filament.of("blanco", "#ffffff", 0.05));
        int[] px = new int[100];
        for (int i = 0; i < px.length; i++) {
            px[i] = i % 2 == 0 ? 0x000000 : 0xffffff;
        }
        ChangeOptimizer.Result r = new ChangeOptimizer(bw, layers, ColorBuckets.of(new RgbImage(10, 10, px)),
                ColorDistance.COLOR, 1, 0).optimize();
        assertThat(r.error()).isLessThan(0.5);
        assertThat(r.startLayers()[1]).isGreaterThan(2);
    }

    @Test
    void elRepartoUniformeEsValido() {
        assertThat(ChangeOptimizer.uniform(4, 28)).containsExactly(1, 8, 15, 22);
        assertThat(ChangeOptimizer.uniform(2, 2)).containsExactly(1, 2);
        assertThat(ChangeOptimizer.uniform(4, 4)).containsExactly(1, 2, 3, 4);
    }

    @Test
    void conMuchasCapasLaBusquedaGruesaSeAcota() {
        int[] vals = ChangeOptimizer.coarseCandidates(200, 3);
        assertThat(ChangeOptimizer.combinations(vals.length, 3)).isLessThanOrEqualTo(4000);
        assertThat(vals[0]).isEqualTo(2);
        assertThat(vals[vals.length - 1]).isEqualTo(200);

        LayerSettings layers = new LayerSettings(0.2, 0.1, 5, 200);
        long t0 = System.nanoTime();
        ChangeOptimizer.Result r = new ChangeOptimizer(FOUR, layers, ColorBuckets.of(TestImages.gradientRgb(100, 100)),
                ColorDistance.COLOR, 1, 0).optimize();
        assertThat((System.nanoTime() - t0) / 1e9).isLessThan(20);
        assertThat(r.startLayers()[3]).isLessThanOrEqualTo(200);
    }

    @Test
    void detectaLosColoresDominantesOrdenadosDeOscuroAClaro() {
        int[] px = new int[300];
        for (int i = 0; i < px.length; i++) {
            px[i] = i < 100 ? 0xf0f0f0 : (i < 200 ? 0x101010 : 0xd02020);
        }
        List<Integer> colors = ColorExtractor.dominantColors(new RgbImage(30, 10, px), 3);
        assertThat(colors).containsExactly(0x101010, 0xd02020, 0xf0f0f0);
    }
}
