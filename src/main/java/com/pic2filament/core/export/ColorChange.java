package com.pic2filament.core.export;

import java.util.ArrayList;
import java.util.List;

import com.pic2filament.core.model.Filament;
import com.pic2filament.core.model.LayerSettings;

/**
 * Entrada de la tabla de cambios de filamento.
 *
 * @param filament   número de filamento (desde 1, de abajo a arriba)
 * @param name       nombre del filamento
 * @param startLayer capa en la que empieza (desde 1, como en el laminador)
 * @param z          altura Z (mm) de la cara superior de esa capa
 * @param hex        color "#rrggbb"
 * @param td         distancia de transmisión (mm)
 */
public record ColorChange(int filament, String name, int startLayer, double z, String hex, double td) {

    public boolean isInitial() {
        return filament == 1;
    }

    public static List<ColorChange> build(List<Filament> filaments, int[] startLayers, LayerSettings layers) {
        List<ColorChange> out = new ArrayList<>();
        for (int i = 0; i < filaments.size(); i++) {
            Filament f = filaments.get(i);
            int layer = startLayers[i];
            out.add(new ColorChange(i + 1, f.name(), layer, layers.zTop(layer - 1), f.hex(), f.td()));
        }
        return out;
    }
}
