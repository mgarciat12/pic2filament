package com.pic2filament.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.pic2filament.config.Pic2filamentProperties;
import com.pic2filament.core.ReliefEngine;
import com.pic2filament.core.ReliefResult;
import com.pic2filament.core.ReliefSettings;
import com.pic2filament.core.color.ColorDistance;
import com.pic2filament.core.color.ColorMath;
import com.pic2filament.core.export.ColorChange;
import com.pic2filament.core.export.ThreeMfWriter;
import com.pic2filament.core.export.ZipExporter;
import com.pic2filament.core.image.GridSize;
import com.pic2filament.core.image.RgbImage;
import com.pic2filament.core.mesh.ReliefMeshBuilder;
import com.pic2filament.core.mesh.StlWriter;
import com.pic2filament.core.model.Filament;
import com.pic2filament.core.model.LayerSettings;
import com.pic2filament.core.optimize.ColorExtractor;
import com.pic2filament.core.render.PreviewRenderer;
import com.pic2filament.core.stack.StackColors;
import com.pic2filament.web.FilamentRequest;
import com.pic2filament.web.ResultResponse;
import com.pic2filament.web.SettingsRequest;

/**
 * Casos de uso de la aplicación: subir imagen, detectar colores, convertir, vista previa y descargas.
 */
@Service
public class ReliefService {

    private static final Logger log = LoggerFactory.getLogger(ReliefService.class);

    /** Modos de vista previa. */
    public enum PreviewMode {
        SIMULACION, RELIEVE, ALTURAS, ORIGINAL;

        public static PreviewMode parse(String s) {
            try {
                return valueOf(s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw Pic2filamentException
                        .invalid("Modo de vista previa no válido. Usa simulacion, relieve, alturas u original.");
            }
        }
    }

    /** Color detectado con la sugerencia de filamento más parecido de la biblioteca. */
    public record DetectedColor(String color, String nombre, double td) {
    }

    private final Pic2filamentProperties props;
    private final ImageLoader loader;
    private final JobStore store;
    private final FilamentLibrary library;
    private final ReliefEngine engine;
    private final Semaphore permits;

    public ReliefService(Pic2filamentProperties props, ImageLoader loader, JobStore store, FilamentLibrary library) {
        this.props = props;
        this.loader = loader;
        this.store = store;
        this.library = library;
        this.engine = new ReliefEngine(props.maxGrid());
        this.permits = new Semaphore(props.effectiveMaxConcurrent(), true);
    }

    public Job upload(String originalName, byte[] data) {
        RgbImage img = loader.load(data);
        Job job = store.create(originalName, img);
        log.info("Imagen subida: trabajo {} ({} × {} px, {} bytes)", job.id(), img.width(), img.height(),
                data.length);
        return job;
    }

    public List<DetectedColor> detectColors(String jobId, int count) {
        if (count < 2 || count > 4) {
            throw Pic2filamentException.invalid("Se pueden detectar entre 2 y 4 colores.");
        }
        Job job = store.require(jobId);
        RgbImage img = store.image(job);
        // Basta con una versión reducida para detectar los colores dominantes.
        int side = Math.max(img.width(), img.height());
        if (side > 300) {
            double f = 300.0 / side;
            img = img.resize(Math.max(1, (int) Math.round(img.width() * f)),
                    Math.max(1, (int) Math.round(img.height() * f)));
        }
        List<DetectedColor> out = new ArrayList<>();
        for (int rgb : ColorExtractor.dominantColors(img, count)) {
            LibraryFilament near = library.nearest(rgb);
            out.add(new DetectedColor(ColorMath.toHex(rgb), near.nombre(), near.td()));
        }
        return out;
    }

    /** Convierte los ajustes del formulario en ajustes del núcleo, validando las reglas cruzadas. */
    public ReliefSettings toSettings(SettingsRequest r) {
        double width = or(r.anchoMm(), SettingsRequest.DEFAULT_WIDTH);
        double detail = or(r.mmPorPixel(), SettingsRequest.DEFAULT_DETAIL);
        double layer = or(r.alturaCapa(), SettingsRequest.DEFAULT_LAYER);
        double first = or(r.alturaPrimeraCapa(), SettingsRequest.DEFAULT_FIRST_LAYER);
        int base = r.capasBase() != null ? r.capasBase() : SettingsRequest.DEFAULT_BASE;
        int total = r.capasTotales() != null ? r.capasTotales() : SettingsRequest.DEFAULT_TOTAL;
        if (total > props.maxTotalLayers()) {
            throw Pic2filamentException.invalid("Las capas totales no pueden superar " + props.maxTotalLayers() + ".");
        }
        if (total <= base) {
            throw Pic2filamentException.invalid("Las capas totales deben ser más que las capas de base.");
        }
        List<FilamentRequest> fr = r.filamentos();
        if (fr == null || fr.size() < 2 || fr.size() > 4) {
            throw Pic2filamentException.invalid("Elige entre 2 y 4 filamentos.");
        }
        if (total < fr.size()) {
            throw Pic2filamentException.invalid("Hace falta al menos una capa por filamento.");
        }
        List<Filament> filaments = new ArrayList<>();
        int[] starts = new int[fr.size()];
        boolean optimize = r.optimizar() == null || r.optimizar();
        for (int i = 0; i < fr.size(); i++) {
            FilamentRequest f = fr.get(i);
            if (f == null || !ColorMath.isHex(f.color()) || f.td() == null || !(f.td() > 0)) {
                throw Pic2filamentException.invalid("El filamento " + (i + 1) + " no es válido.");
            }
            String name = f.nombre() == null || f.nombre().isBlank() ? "Filamento " + (i + 1) : f.nombre().strip();
            filaments.add(new Filament(name, ColorMath.parseHex(f.color()), f.td()));
            starts[i] = f.capaInicio() != null ? f.capaInicio() : 0;
        }
        if (!optimize) {
            starts[0] = 1;
            for (int i = 1; i < starts.length; i++) {
                if (starts[i] <= starts[i - 1]) {
                    throw Pic2filamentException.invalid("Las capas de inicio deben ser crecientes: el filamento "
                            + (i + 1) + " debe empezar después que el " + i + ".");
                }
                if (starts[i] > total) {
                    throw Pic2filamentException.invalid("La capa de inicio del filamento " + (i + 1)
                            + " (" + starts[i] + ") supera las capas totales (" + total + ").");
                }
            }
        }
        ColorDistance metric = "luminosidad".equals(r.criterio()) ? ColorDistance.LUMINANCE : ColorDistance.COLOR;
        return new ReliefSettings(width, detail, new LayerSettings(first, layer, base, total), filaments, starts,
                metric, or(r.contraste(), 1.0), or(r.brillo(), 0.0),
                r.reducirRuido() != null && r.reducirRuido(), optimize, or(r.marcoMm(), 0.0));
    }

    public ReliefResult process(String jobId, SettingsRequest request) {
        ReliefSettings settings = toSettings(request);
        Job job = store.require(jobId);
        ReliefResult result = compute(job, settings);
        job.storeResult(result);
        log.info("Trabajo {}: rejilla {}×{}, cambios {}, {} ms", job.id(), result.heights().width(),
                result.heights().height(), java.util.Arrays.toString(result.startLayers()), result.elapsedMs());
        return result;
    }

    private ReliefResult compute(Job job, ReliefSettings settings) {
        acquire();
        try {
            long t0 = System.nanoTime();
            RgbImage img = store.image(job);
            GridSize grid = engine.gridFor(img, settings);
            Job.Resized resized = job.cachedResized(grid);
            if (resized == null) {
                RgbImage r = img.resize(grid.columns(), grid.rows());
                resized = new Job.Resized(grid, r, r.toLab());
                job.cacheResized(resized);
            }
            return engine.process(resized.image(), resized.lab(), grid, settings, t0);
        } catch (IllegalArgumentException e) {
            throw Pic2filamentException.invalid(e.getMessage());
        } finally {
            permits.release();
        }
    }

    private void acquire() {
        try {
            if (!permits.tryAcquire(60, TimeUnit.SECONDS)) {
                throw new Pic2filamentException(HttpStatus.SERVICE_UNAVAILABLE,
                        "El servidor está ocupado. Inténtalo de nuevo en unos segundos.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Pic2filamentException(HttpStatus.SERVICE_UNAVAILABLE, "Operación interrumpida.");
        }
    }

    /** Último resultado del trabajo; si la memoria lo liberó, se recalcula con los últimos ajustes. */
    public ReliefResult lastResult(String jobId) {
        Job job = store.require(jobId);
        ReliefResult r = job.cachedResult();
        if (r != null) {
            return r;
        }
        ReliefSettings s = job.lastSettings();
        if (s == null) {
            throw new Pic2filamentException(HttpStatus.CONFLICT,
                    "Todavía no se ha generado el relieve: ajusta los parámetros y espera a la vista previa.");
        }
        r = compute(job, s);
        job.storeResult(r);
        return r;
    }

    public Job job(String jobId) {
        return store.require(jobId);
    }

    public BufferedImage preview(String jobId, PreviewMode mode) {
        ReliefResult r = lastResult(jobId);
        LayerSettings l = r.settings().layers();
        return switch (mode) {
            case SIMULACION -> PreviewRenderer.simulation(r.heights(), r.stack());
            case RELIEVE -> PreviewRenderer.shadedRelief(r.heights(), l, r.step());
            case ALTURAS -> PreviewRenderer.heightMap(r.heights(), l.baseLayers(), l.totalLayers());
            case ORIGINAL -> r.original().toBufferedImage();
        };
    }

    public String baseName(String jobId) {
        return ZipExporter.baseName(store.require(jobId).originalName());
    }

    public void writeZip(String jobId, OutputStream out) throws IOException {
        ReliefResult r = lastResult(jobId);
        ZipExporter.write(r, store.require(jobId).originalName(), out);
    }

    public void writeStl(String jobId, OutputStream out) throws IOException {
        ReliefResult r = lastResult(jobId);
        ZipExporter.writeStl(r, baseName(jobId), out);
    }

    public void write3mf(String jobId, OutputStream out) throws IOException {
        ReliefResult r = lastResult(jobId);
        ThreeMfWriter.write(r, baseName(jobId), out);
    }

    public ResultResponse toResponse(Job job, ReliefResult r) {
        List<ResultResponse.Change> changes = new ArrayList<>();
        for (ColorChange c : r.changes()) {
            changes.add(new ResultResponse.Change(c.filament(), c.name(), c.startLayer(), round(c.z()), c.hex(),
                    c.td()));
        }
        StackColors stack = r.stack();
        LayerSettings l = r.settings().layers();
        List<ResultResponse.Layer> layers = new ArrayList<>();
        for (int n = 1; n <= l.totalLayers(); n++) {
            int f = stack.filamentAt(n);
            layers.add(new ResultResponse.Layer(n, round(l.zTop(n - 1)), f + 1, r.settings().filaments().get(f).hex(),
                    ColorMath.toHex(stack.rgbAt(n))));
        }
        long tris = ReliefMeshBuilder.triangleCount(r.heights().width(), r.heights().height());
        return new ResultResponse(job.version(), round(r.widthMm()), round(r.depthMm()), round(r.maxHeightMm()),
                r.heights().width(), r.heights().height(), Math.round(r.step() * 10000) / 10000.0, tris,
                StlWriter.fileSize(tris), r.elapsedMs(), round(r.error()), r.startLayers(), changes, layers);
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private static double or(Double v, double def) {
        return v != null ? v : def;
    }
}
