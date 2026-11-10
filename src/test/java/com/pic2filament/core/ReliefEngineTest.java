package com.pic2filament.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import com.pic2filament.TestImages;
import com.pic2filament.core.export.ZipExporterTest;
import com.pic2filament.core.image.RgbImage;

class ReliefEngineTest {

    @Test
    void una400x400Con4FilamentosSeResuelveEnPocosSegundos() {
        RgbImage img = TestImages.gradientRgb(1200, 1200);
        ReliefSettings s = new ReliefSettings(100, 0.25, ZipExporterTest.settingsLayers(), ZipExporterTest
                .settings(0).filaments(), new int[] { 1, 6, 12, 18 }, com.pic2filament.core.color.ColorDistance.COLOR,
                1, 0, true, true, 0);
        long t0 = System.nanoTime();
        ReliefResult r = new ReliefEngine(600).process(img, s);
        double seconds = (System.nanoTime() - t0) / 1e9;
        assertThat(r.heights().width()).isEqualTo(401);
        assertThat(r.heights().height()).isEqualTo(401);
        assertThat(seconds).isLessThan(5);
        assertThat(r.widthMm()).isCloseTo(100, within(1e-9));
        assertThat(r.changes()).hasSize(4);
    }

    @Test
    void conCambiosManualesSeRespetan() {
        ReliefSettings auto = ZipExporterTest.settings(0);
        ReliefSettings manual = new ReliefSettings(auto.widthMm(), auto.mmPerPixel(), auto.layers(), auto.filaments(),
                new int[] { 1, 3, 9, 20 }, auto.metric(), 1, 0, false, false, 0);
        ReliefResult r = new ReliefEngine(600).process(TestImages.gradientRgb(80, 60), manual);
        assertThat(r.startLayers()).containsExactly(1, 3, 9, 20);
        assertThat(r.changes().get(3).z()).isCloseTo(0.16 + 19 * 0.08, within(1e-9));
    }
}
