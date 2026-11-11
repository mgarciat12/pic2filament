package com.pic2filament.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.pic2filament.config.Pic2filamentProperties;
import com.pic2filament.core.export.SlicerProfile;

import tools.jackson.databind.json.JsonMapper;

/**
 * Perfiles de laminador para el proyecto 3MF: los incluidos ({@code classpath:perfiles/*.json}) y,
 * opcionalmente, los del directorio {@code pic2filament.profiles-dir}. Un perfil es el
 * {@code Metadata/project_settings.config} de un proyecto guardado con el laminador; la clave
 * opcional {@code pic2filament_nombre} da el nombre que se muestra.
 */
@Service
public class ProfileLibrary {

    static final String NAME_KEY = "pic2filament_nombre";
    private static final Logger log = LoggerFactory.getLogger(ProfileLibrary.class);

    private final Map<String, SlicerProfile> profiles = new LinkedHashMap<>();
    private final String defaultId;

    public ProfileLibrary(Pic2filamentProperties props) throws IOException {
        JsonMapper json = JsonMapper.builder().build();
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath:perfiles/*.json")) {
            try (InputStream in = r.getInputStream()) {
                add(json, stem(r.getFilename()), in);
            }
        }
        String dir = props.profilesDir();
        if (dir != null && !dir.isBlank()) {
            try (Stream<Path> files = Files.list(Path.of(dir))) {
                for (Path p : files.filter(f -> f.toString().matches("(?i).*\\.(json|config)$")).sorted().toList()) {
                    try (InputStream in = Files.newInputStream(p)) {
                        add(json, stem(p.getFileName().toString()), in);
                        log.info("Perfil de laminador cargado de {}", p);
                    } catch (RuntimeException e) {
                        log.warn("Perfil de laminador no válido {}: {}", p, e.getMessage());
                    }
                }
            }
        }
        if (profiles.isEmpty()) {
            throw new IllegalStateException("No hay ningún perfil de laminador");
        }
        String wanted = props.defaultProfile();
        defaultId = wanted != null && profiles.containsKey(wanted) ? wanted : profiles.keySet().iterator().next();
    }

    @SuppressWarnings("unchecked")
    private void add(JsonMapper json, String id, InputStream in) {
        Map<String, Object> raw = json.readValue(in, LinkedHashMap.class);
        Object name = raw.remove(NAME_KEY);
        String display = name instanceof String s && !s.isBlank() ? s : String.valueOf(raw.get("printer_settings_id"));
        profiles.put(id, new SlicerProfile(id, display, raw));
    }

    private static String stem(String filename) {
        String n = filename == null ? "perfil" : filename;
        int dot = n.lastIndexOf('.');
        return (dot > 0 ? n.substring(0, dot) : n).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
    }

    public List<SlicerProfile> all() {
        return new ArrayList<>(profiles.values());
    }

    public String defaultId() {
        return defaultId;
    }

    public Optional<SlicerProfile> find(String id) {
        return Optional.ofNullable(profiles.get(id == null || id.isBlank() ? defaultId : id));
    }
}
