package com.pic2filament.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.pic2filament.config.Pic2filamentProperties;
import com.pic2filament.core.image.RgbImage;

import jakarta.annotation.PreDestroy;

/**
 * Almacén de trabajos. Cada trabajo guarda su imagen en {@code <temp-dir>/<id>/imagen.png};
 * los trabajos sin uso durante {@code job-ttl} se borran automáticamente, y también los más
 * antiguos si se supera {@code max-jobs}.
 */
@Service
public class JobStore {

    private static final Logger log = LoggerFactory.getLogger(JobStore.class);
    private static final Pattern ID = Pattern.compile("^[0-9a-f]{32}$");
    private static final String IMAGE_FILE = "imagen.png";

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Path root;
    private final Duration ttl;
    private final int maxJobs;

    public JobStore(Pic2filamentProperties props) throws IOException {
        this.root = props.effectiveTempDir().toAbsolutePath();
        this.ttl = props.jobTtl();
        this.maxJobs = Math.max(1, props.maxJobs());
        Files.createDirectories(root);
        // Restos de ejecuciones anteriores: ya no hay forma de acceder a ellos.
        try (Stream<Path> old = Files.list(root)) {
            old.filter(p -> ID.matcher(p.getFileName().toString()).matches()).forEach(JobStore::deleteTree);
        }
        log.info("Directorio temporal de trabajos: {} (caducidad {})", root, ttl);
    }

    public Job create(String originalName, RgbImage image) {
        evictIfFull();
        String id = UUID.randomUUID().toString().replace("-", "");
        Path dir = root.resolve(id);
        try {
            Files.createDirectories(dir);
            ImageIO.write(image.toBufferedImage(), "png", dir.resolve(IMAGE_FILE).toFile());
        } catch (IOException e) {
            deleteTree(dir);
            throw new UncheckedIOException("No se ha podido guardar la imagen temporal", e);
        }
        Job job = new Job(id, originalName, dir, image);
        jobs.put(id, job);
        return job;
    }

    public Optional<Job> find(String id) {
        if (id == null || !ID.matcher(id).matches()) {
            return Optional.empty();
        }
        Job job = jobs.get(id);
        if (job != null) {
            job.touch();
        }
        return Optional.ofNullable(job);
    }

    public Job require(String id) {
        return find(id).orElseThrow(Pic2filamentException::notFound);
    }

    /** Imagen del trabajo, desde memoria o, si se liberó, desde el fichero temporal. */
    public RgbImage image(Job job) {
        RgbImage img = job.cachedImage();
        if (img == null) {
            try {
                img = RgbImage.fromBufferedImage(ImageIO.read(job.dir().resolve(IMAGE_FILE).toFile()));
            } catch (IOException | RuntimeException e) {
                remove(job.id());
                throw Pic2filamentException.notFound();
            }
            job.cacheImage(img);
        }
        return img;
    }

    public int size() {
        return jobs.size();
    }

    public void remove(String id) {
        Job job = jobs.remove(id);
        if (job != null) {
            deleteTree(job.dir());
        }
    }

    /** Limpieza periódica de trabajos caducados. */
    @Scheduled(fixedDelayString = "${pic2filament.cleanup-interval:PT5M}", initialDelayString = "PT1M")
    public int cleanup() {
        Instant limit = Instant.now().minus(ttl);
        int removed = 0;
        for (Job job : jobs.values()) {
            if (job.lastAccess().isBefore(limit)) {
                remove(job.id());
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Limpieza: {} trabajos caducados borrados, quedan {}", removed, jobs.size());
        }
        return removed;
    }

    private void evictIfFull() {
        while (jobs.size() >= maxJobs) {
            jobs.values().stream().min(Comparator.comparing(Job::lastAccess)).ifPresent(j -> remove(j.id()));
        }
    }

    @PreDestroy
    void shutdown() {
        jobs.keySet().forEach(this::remove);
    }

    static void deleteTree(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("No se ha podido borrar {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("No se ha podido borrar {}: {}", dir, e.getMessage());
        }
    }
}
