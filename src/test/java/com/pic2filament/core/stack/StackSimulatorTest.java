package com.pic2filament.core.stack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.pic2filament.core.model.Filament;
import com.pic2filament.core.model.LayerSettings;

class StackSimulatorTest {

    private static final LayerSettings LAYERS = new LayerSettings(0.16, 0.08, 4, 28);

    @Test
    void unaCapaOpacaTapaLoDeAbajo() {
        List<Filament> f = List.of(Filament.of("negro", "#000000", 0.6), Filament.of("blanco", "#ffffff", 0.001));
        StackColors s = StackSimulator.simulate(f, new int[] { 1, 10 }, LAYERS);
        assertThat(s.rgbAt(9)).isEqualTo(0x000000);
        assertThat(s.rgbAt(10)).isEqualTo(0xffffff);
        assertThat(s.filamentAt(9)).isZero();
        assertThat(s.filamentAt(10)).isEqualTo(1);
    }

    @Test
    void unTdAltoDejaVerLaCapaInferior() {
        List<Filament> f = List.of(Filament.of("negro", "#000000", 0.6), Filament.of("blanco", "#ffffff", 1000));
        StackColors s = StackSimulator.simulate(f, new int[] { 1, 5 }, LAYERS);
        // 24 capas de 0,08 mm con TD 1000: opacidad acumulada ≈ 1 − exp(−3·1,92/1000) ≈ 0,6 %
        assertThat(s.labAt(28)[0]).isLessThan(10);
        double expectedLin = 1 - Math.exp(-3 * 24 * 0.08 / 1000);
        assertThat(s.linear()[27][0]).isCloseTo(expectedLin, within(1e-9));
    }

    @Test
    void laMezclaSigueLaFormulaDeOpacidad() {
        List<Filament> f = List.of(Filament.of("negro", "#000000", 0.6), Filament.of("blanco", "#ffffff", 0.4));
        StackColors s = StackSimulator.simulate(f, new int[] { 1, 2 }, LAYERS);
        double op = 1 - Math.exp(-3 * 0.08 / 0.4);
        assertThat(s.linear()[1][1]).isCloseTo(op, within(1e-12));
        assertThat(s.linear()[2][1]).isCloseTo(op + (1 - op) * op, within(1e-12));
    }

    @Test
    void laPrimeraCapaUsaSuPropioGrosor() {
        List<Filament> f = List.of(Filament.of("blanco", "#ffffff", 1.0), Filament.of("negro", "#000000", 1.0));
        StackColors uno = StackSimulator.simulate(f, new int[] { 1, 2 }, new LayerSettings(0.16, 0.08, 1, 5));
        // La primera capa es del propio filamento 1: el color no cambia.
        assertThat(uno.rgbAt(1)).isEqualTo(0xffffff);
    }

    @Test
    void negroDebajoYBlancoEncimaDaUnResultadoMonotono() {
        List<Filament> f = List.of(Filament.of("negro", "#000000", 0.6), Filament.of("gris", "#808080", 2.0),
                Filament.of("blanco", "#ffffff", 4.5));
        StackColors s = StackSimulator.simulate(f, new int[] { 1, 6, 14 }, LAYERS);
        for (int k = 2; k <= LAYERS.totalLayers(); k++) {
            assertThat(s.labAt(k)[0]).isGreaterThanOrEqualTo(s.labAt(k - 1)[0] - 1e-9);
        }
        assertThat(s.labAt(28)[0]).isGreaterThan(80);
    }

    @Test
    void validaLasCapasDeInicio() {
        List<Filament> f = List.of(Filament.of("a", "#000000", 1), Filament.of("b", "#ffffff", 1));
        assertThatThrownBy(() -> StackSimulator.simulate(f, new int[] { 2, 5 }, LAYERS))
                .hasMessageContaining("capa 1");
        assertThatThrownBy(() -> StackSimulator.simulate(f, new int[] { 1, 1 }, LAYERS))
                .hasMessageContaining("crecientes");
        assertThatThrownBy(() -> StackSimulator.simulate(f, new int[] { 1, 29 }, LAYERS))
                .hasMessageContaining("supera");
    }
}
