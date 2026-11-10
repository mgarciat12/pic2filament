package com.pic2filament.service;

import java.lang.ref.SoftReference;
import java.nio.file.Path;
import java.time.Instant;

import com.pic2filament.core.ReliefResult;
import com.pic2filament.core.ReliefSettings;
import com.pic2filament.core.image.GridSize;
import com.pic2filament.core.image.LabImage;
import com.pic2filament.core.image.RgbImage;

/**
 * Trabajo de conversión: la imagen subida (guardada en un fichero temporal y en memoria mientras
 * haya sitio) y el último resultado calculado.
 */
public final class Job {

    private final String id;
    private final String originalName;
    private final Path dir;
    private final int width;
    private final int height;
    private final Instant created = Instant.now();
    private volatile Instant lastAccess = Instant.now();

    private volatile SoftReference<RgbImage> image;
    private volatile SoftReference<Resized> resized = new SoftReference<>(null);
    private volatile SoftReference<ReliefResult> result = new SoftReference<>(null);
    private volatile ReliefSettings lastSettings;
    private volatile int version;

    /** Imagen redimensionada a una rejilla concreta, con sus planos Lab. */
    public record Resized(GridSize grid, RgbImage image, LabImage lab) {
    }

    Job(String id, String originalName, Path dir, RgbImage image) {
        this.id = id;
        this.originalName = originalName;
        this.dir = dir;
        this.width = image.width();
        this.height = image.height();
        this.image = new SoftReference<>(image);
    }

    public String id() {
        return id;
    }

    public String originalName() {
        return originalName;
    }

    public Path dir() {
        return dir;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Instant created() {
        return created;
    }

    public Instant lastAccess() {
        return lastAccess;
    }

    void touch() {
        lastAccess = Instant.now();
    }

    RgbImage cachedImage() {
        return image.get();
    }

    void cacheImage(RgbImage img) {
        image = new SoftReference<>(img);
    }

    Resized cachedResized(GridSize grid) {
        Resized r = resized.get();
        return r != null && r.grid().equals(grid) ? r : null;
    }

    void cacheResized(Resized r) {
        resized = new SoftReference<>(r);
    }

    public ReliefResult cachedResult() {
        return result.get();
    }

    public ReliefSettings lastSettings() {
        return lastSettings;
    }

    public int version() {
        return version;
    }

    synchronized void storeResult(ReliefResult r) {
        result = new SoftReference<>(r);
        lastSettings = r.settings();
        version++;
    }
}
