package com.pic2filament.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import com.pic2filament.config.Pic2filamentProperties;

/**
 * Convierte imágenes HEIC/HEIF (las fotos del iPhone) a PNG con una herramienta externa.
 * <p>
 * En Java no hay un decodificador HEVC fiable, así que se usa {@code heif-dec} o {@code heif-convert}
 * (libheif, incluido en la imagen Docker) o {@code sips} (macOS). Se puede fijar otro comando con
 * {@code pic2filament.heif-command}, donde {@code {entrada}} y {@code {salida}} se sustituyen por las rutas.
 */
@Component
public class HeifConverter {

    private static final Logger log = LoggerFactory.getLogger(HeifConverter.class);

    /** Marcas ISO-BMFF de HEIF/HEIC (no se incluye AVIF). */
    static final Set<String> BRANDS = Set.of("heic", "heix", "heim", "heis", "hevc", "hevx", "hevm", "hevs", "mif1",
            "msf1");

    static final long TIMEOUT_SECONDS = 60;

    private final List<String> command;
    private final Path tempDir;

    @Autowired
    public HeifConverter(Pic2filamentProperties props) {
        this(resolve(props.heifCommand()), props.effectiveTempDir());
    }

    HeifConverter(List<String> command, Path tempDir) {
        this.command = command;
        this.tempDir = tempDir;
        if (command.isEmpty()) {
            log.info("HEIC/HEIF desactivado: no se ha encontrado heif-dec, heif-convert ni sips.");
        } else {
            log.info("HEIC/HEIF activado con: {}", String.join(" ", command));
        }
    }

    public boolean available() {
        return !command.isEmpty();
    }

    /** ¿El contenido es un fichero HEIF (caja {@code ftyp} con una marca HEIF)? */
    public static boolean isHeif(byte[] data) {
        if (data == null || data.length < 16 || !"ftyp".equals(ascii(data, 4))) {
            return false;
        }
        long boxSize = ((data[0] & 0xffL) << 24) | ((data[1] & 0xff) << 16) | ((data[2] & 0xff) << 8) | (data[3] & 0xff);
        if (BRANDS.contains(ascii(data, 8))) {
            return true;
        }
        if (ascii(data, 8).startsWith("avi")) {
            return false;
        }
        // Marcas compatibles a partir del byte 16.
        int end = (int) Math.min(data.length, Math.max(16, boxSize));
        for (int i = 16; i + 4 <= end; i += 4) {
            if (BRANDS.contains(ascii(data, i))) {
                return true;
            }
        }
        return false;
    }

    /** Convierte la imagen HEIF a PNG. */
    public byte[] toPng(byte[] heif) {
        if (!available()) {
            throw new Pic2filamentException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Este servidor no puede leer fotos HEIC (falta libheif). Conviértela a JPG o PNG y vuelve a subirla.");
        }
        Path dir = null;
        try {
            Files.createDirectories(tempDir);
            dir = Files.createTempDirectory(tempDir, "heif-");
            Path in = dir.resolve("entrada.heic");
            Path out = dir.resolve("salida.png");
            Files.write(in, heif);
            List<String> cmd = new ArrayList<>();
            for (String part : command) {
                cmd.add(part.replace("{entrada}", in.toString()).replace("{salida}", out.toString()));
            }
            Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true)
                    .redirectOutput(dir.resolve("registro.txt").toFile()).start();
            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw failed("la conversión ha tardado demasiado", dir);
            }
            if (p.exitValue() != 0 || !Files.isRegularFile(out) || Files.size(out) == 0) {
                throw failed("código de salida " + p.exitValue(), dir);
            }
            return Files.readAllBytes(out);
        } catch (IOException e) {
            throw new Pic2filamentException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se ha podido convertir la foto HEIC.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Pic2filamentException(HttpStatus.SERVICE_UNAVAILABLE, "Conversión interrumpida.", e);
        } finally {
            if (dir != null) {
                try {
                    FileSystemUtils.deleteRecursively(dir);
                } catch (IOException e) {
                    log.warn("No se ha podido borrar {}", dir, e);
                }
            }
        }
    }

    private static Pic2filamentException failed(String reason, Path dir) {
        String detail = "";
        try {
            Path reg = dir.resolve("registro.txt");
            if (Files.exists(reg)) {
                detail = Files.readString(reg, StandardCharsets.UTF_8).strip();
                detail = detail.substring(Math.max(0, detail.length() - 500));
            }
        } catch (IOException ignored) {
            // sin registro
        }
        log.warn("Error al convertir HEIC ({}): {}", reason, detail);
        return new Pic2filamentException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "No se ha podido leer la foto HEIC: el fichero está dañado o usa una variante no admitida.");
    }

    /** Elige el comando: el configurado, o el primero disponible de heif-dec, heif-convert y sips. */
    static List<String> resolve(String configured) {
        if (configured != null && !configured.isBlank()) {
            if (configured.strip().equalsIgnoreCase("no")) {
                return List.of();
            }
            return List.of(configured.strip().split("\\s+"));
        }
        if (onPath("heif-dec")) {
            return List.of("heif-dec", "{entrada}", "{salida}");
        }
        if (onPath("heif-convert")) {
            return List.of("heif-convert", "{entrada}", "{salida}");
        }
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac") && onPath("sips")) {
            return List.of("sips", "-s", "format", "png", "{entrada}", "--out", "{salida}");
        }
        return List.of();
    }

    private static boolean onPath(String exe) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String d : path.split(java.io.File.pathSeparator)) {
            if (!d.isEmpty() && Files.isExecutable(Path.of(d, exe))) {
                return true;
            }
        }
        return false;
    }

    private static String ascii(byte[] data, int offset) {
        return new String(data, offset, 4, StandardCharsets.ISO_8859_1);
    }
}
