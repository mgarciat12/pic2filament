package com.pic2filament.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Propiedades de la aplicación (prefijo {@code pic2filament}).
 */
@ConfigurationProperties("pic2filament")
public record Pic2filamentProperties(
        @DefaultValue("15MB") DataSize maxUploadSize,
        @DefaultValue("600") int maxGrid,
        @DefaultValue("60") int maxImageMegapixels,
        @DefaultValue("300") int maxTotalLayers,
        Path tempDir,
        @DefaultValue("2h") Duration jobTtl,
        @DefaultValue("200") int maxJobs,
        @DefaultValue("0") int maxConcurrent,
        String filamentsFile) {

    public Path effectiveTempDir() {
        return tempDir != null ? tempDir : Path.of(System.getProperty("java.io.tmpdir"), "pic2filament");
    }

    public int effectiveMaxConcurrent() {
        return maxConcurrent > 0 ? maxConcurrent : Math.max(1, Runtime.getRuntime().availableProcessors());
    }
}
