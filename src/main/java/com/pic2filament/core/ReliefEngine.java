package com.pic2filament.core;

import java.util.List;

import com.pic2filament.core.export.ColorChange;
import com.pic2filament.core.height.HeightMap;
import com.pic2filament.core.height.HeightMapper;
import com.pic2filament.core.image.GridSize;
import com.pic2filament.core.image.LabImage;
import com.pic2filament.core.image.RgbImage;
import com.pic2filament.core.optimize.ChangeOptimizer;
import com.pic2filament.core.optimize.ColorBuckets;
import com.pic2filament.core.stack.StackColors;
import com.pic2filament.core.stack.StackSimulator;

/**
 * Orquesta la conversión completa: redimensionado, optimización de cambios, simulación de la pila,
 * asignación de alturas, reducción de ruido y marco. Sin dependencias de Spring.
 */
public final class ReliefEngine {

    private final int maxGrid;

    public ReliefEngine(int maxGrid) {
        this.maxGrid = maxGrid;
    }

    public GridSize gridFor(RgbImage source, ReliefSettings s) {
        return GridSize.compute(source.width(), source.height(), s.widthMm(), s.mmPerPixel(), maxGrid);
    }

    public ReliefResult process(RgbImage source, ReliefSettings s) {
        long t0 = System.nanoTime();
        GridSize grid = gridFor(source, s);
        RgbImage resized = source.resize(grid.columns(), grid.rows());
        return process(resized, resized.toLab(), grid, s, t0);
    }

    /** Variante que reutiliza una imagen ya redimensionada y su conversión a Lab (caché del servicio). */
    public ReliefResult process(RgbImage resized, LabImage lab, GridSize grid, ReliefSettings s, long startNanos) {
        int[] starts;
        double error;
        ColorBuckets buckets = ColorBuckets.of(resized);
        ChangeOptimizer optimizer = new ChangeOptimizer(s.filaments(), s.layers(), buckets, s.metric(),
                s.contrast(), s.brightness());
        if (s.autoOptimize() && s.filaments().size() > 1) {
            ChangeOptimizer.Result r = optimizer.optimize();
            starts = r.startLayers();
            error = r.error();
        } else {
            starts = s.startLayers().clone();
            StackSimulator.validateStarts(starts, s.filaments().size(), s.layers().totalLayers());
            error = optimizer.error(starts);
        }

        StackColors stack = StackSimulator.simulate(s.filaments(), starts, s.layers());
        HeightMap heights = HeightMapper.assign(lab, stack, s.layers().baseLayers(), s.metric(), s.contrast(),
                s.brightness());
        if (s.denoise()) {
            heights = heights.median3x3();
        }
        RgbImage original = resized;
        if (s.borderMm() > 0) {
            int cells = Math.max(1, (int) Math.round(s.borderMm() / grid.step()));
            heights = Border.add(heights, cells, s.layers().totalLayers());
            original = Border.add(resized, cells, stack.rgbAt(s.layers().totalLayers()));
        }
        List<ColorChange> changes = ColorChange.build(s.filaments(), starts, s.layers());
        long elapsed = (System.nanoTime() - startNanos) / 1_000_000;
        return new ReliefResult(s, grid, original, heights, grid.step(), stack, starts, changes, error, elapsed);
    }
}
