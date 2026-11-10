package com.pic2filament.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.pic2filament.config.Pic2filamentProperties;
import com.pic2filament.core.color.ColorMath;

/**
 * Biblioteca de filamentos predefinidos, leída de {@code filamentos.yml} (incluido en la
 * aplicación) o de un fichero externo configurable.
 */
@Service
public class FilamentLibrary {

    private static final Logger log = LoggerFactory.getLogger(FilamentLibrary.class);

    private final List<LibraryFilament> filaments;

    public FilamentLibrary(Pic2filamentProperties props) throws IOException {
        String external = props.filamentsFile();
        if (external != null && !external.isBlank()) {
            Path p = Path.of(external);
            try (InputStream in = Files.newInputStream(p)) {
                filaments = parse(in);
            }
            log.info("Biblioteca de filamentos cargada de {} ({} filamentos)", p, filaments.size());
        } else {
            try (InputStream in = new ClassPathResource("filamentos.yml").getInputStream()) {
                filaments = parse(in);
            }
        }
    }

    public List<LibraryFilament> all() {
        return filaments;
    }

    /** Filamento de la biblioteca cuyo color está más cerca (en Lab) del indicado. */
    public LibraryFilament nearest(int rgb) {
        double[] lab = ColorMath.rgbIntToLab(rgb);
        LibraryFilament best = filaments.get(0);
        double bestD = Double.MAX_VALUE;
        for (LibraryFilament f : filaments) {
            double[] o = ColorMath.rgbIntToLab(ColorMath.parseHex(f.color()));
            double d = Math.pow(lab[0] - o[0], 2) + Math.pow(lab[1] - o[1], 2) + Math.pow(lab[2] - o[2], 2);
            if (d < bestD) {
                bestD = d;
                best = f;
            }
        }
        return best;
    }

    @SuppressWarnings("unchecked")
    static List<LibraryFilament> parse(InputStream in) {
        Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
        if (!(root instanceof Map<?, ?> map) || !(map.get("filamentos") instanceof List<?> list)) {
            throw new IllegalStateException("El fichero de filamentos debe tener una lista «filamentos»");
        }
        List<LibraryFilament> out = new ArrayList<>();
        for (Object o : list) {
            Map<String, Object> m = (Map<String, Object>) o;
            String name = String.valueOf(m.get("nombre"));
            String color = String.valueOf(m.get("color"));
            Object td = m.get("td");
            if (!ColorMath.isHex(color) || !(td instanceof Number n) || n.doubleValue() <= 0) {
                throw new IllegalStateException("Filamento no válido en la biblioteca: " + m);
            }
            out.add(new LibraryFilament(name, ColorMath.normalizeHex(color), n.doubleValue()));
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("La biblioteca de filamentos está vacía");
        }
        return List.copyOf(out);
    }
}
