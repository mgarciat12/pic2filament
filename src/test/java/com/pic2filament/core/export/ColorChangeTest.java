package com.pic2filament.core.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.pic2filament.core.model.Filament;
import com.pic2filament.core.model.LayerSettings;

class ColorChangeTest {

    @Test
    void numeraLasCapasDesdeUnoYCalculaLaAlturaZ() {
        LayerSettings layers = new LayerSettings(0.16, 0.08, 4, 28);
        List<Filament> f = List.of(Filament.of("Negro", "#000000", 0.6), Filament.of("Gris", "#808080", 2),
                Filament.of("Blanco", "#FFFFFF", 4.5));
        List<ColorChange> c = ColorChange.build(f, new int[] { 1, 5, 12 }, layers);
        assertThat(c).extracting(ColorChange::filament).containsExactly(1, 2, 3);
        assertThat(c).extracting(ColorChange::startLayer).containsExactly(1, 5, 12);
        assertThat(c.get(0).z()).isCloseTo(0.16, within(1e-9));
        assertThat(c.get(1).z()).isCloseTo(0.48, within(1e-9));
        assertThat(c.get(2).z()).isCloseTo(1.04, within(1e-9));
        assertThat(c.get(2).hex()).isEqualTo("#ffffff");
        assertThat(c.get(0).isInitial()).isTrue();
    }

    @Test
    void alturaDeCapas() {
        LayerSettings layers = new LayerSettings(0.2, 0.1, 3, 10);
        assertThat(layers.zTop(0)).isCloseTo(0.2, within(1e-12));
        assertThat(layers.zTop(9)).isCloseTo(1.1, within(1e-12));
        assertThat(layers.heightOf(10)).isCloseTo(1.1, within(1e-12));
        assertThat(layers.thickness(1)).isEqualTo(0.2);
        assertThat(layers.thickness(2)).isEqualTo(0.1);
    }

    @Test
    void nombreBaseSeguro() {
        assertThat(ZipExporter.baseName("Mi foto ñandú.JPG")).isEqualTo("Mi-foto-nandu");
        assertThat(ZipExporter.baseName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(ZipExporter.baseName(null)).isEqualTo("pic2filament");
        assertThat(ZipExporter.baseName("???.png")).isEqualTo("pic2filament");
    }
}
