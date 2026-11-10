package com.pic2filament.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import com.pic2filament.TestImages;
import com.pic2filament.config.Pic2filamentProperties;

class JobStoreTest {

    @TempDir
    Path tmp;

    Pic2filamentProperties props(Duration ttl, int maxJobs) {
        return new Pic2filamentProperties(DataSize.ofMegabytes(15), 600, 60, 300, tmp, ttl, maxJobs, 0, null);
    }

    @Test
    void guardaLaImagenEnUnFicheroTemporalYLaBorraAlCaducar() throws Exception {
        JobStore store = new JobStore(props(Duration.ZERO, 10));
        Job job = store.create("a.png", TestImages.gradientRgb(20, 10));
        assertThat(Files.exists(job.dir().resolve("imagen.png"))).isTrue();
        assertThat(store.find(job.id())).isPresent();
        Thread.sleep(5);
        assertThat(store.cleanup()).isEqualTo(1);
        assertThat(store.find(job.id())).isEmpty();
        assertThat(Files.exists(job.dir())).isFalse();
        assertThatThrownBy(() -> store.require(job.id())).isInstanceOf(Pic2filamentException.class);
    }

    @Test
    void losTrabajosRecientesNoSeBorran() throws Exception {
        JobStore store = new JobStore(props(Duration.ofHours(1), 10));
        Job job = store.create("a.png", TestImages.gradientRgb(20, 10));
        assertThat(store.cleanup()).isZero();
        assertThat(store.find(job.id())).isPresent();
    }

    @Test
    void siSeSuperaElMaximoSeBorraElMasAntiguo() throws Exception {
        JobStore store = new JobStore(props(Duration.ofHours(1), 2));
        Job a = store.create("a.png", TestImages.gradientRgb(4, 4));
        Thread.sleep(5);
        Job b = store.create("b.png", TestImages.gradientRgb(4, 4));
        Thread.sleep(5);
        Job c = store.create("c.png", TestImages.gradientRgb(4, 4));
        assertThat(store.size()).isEqualTo(2);
        assertThat(store.find(a.id())).isEmpty();
        assertThat(store.find(b.id())).isPresent();
        assertThat(store.find(c.id())).isPresent();
    }

    @Test
    void rechazaIdentificadoresMalFormados() throws Exception {
        JobStore store = new JobStore(props(Duration.ofHours(1), 2));
        assertThat(store.find("../etc/passwd")).isEmpty();
        assertThat(store.find(null)).isEmpty();
    }

    @Test
    void laBibliotecaEncuentraElFilamentoMasParecido() throws Exception {
        FilamentLibrary lib = new FilamentLibrary(props(Duration.ofHours(1), 2));
        assertThat(lib.nearest(0x000000).nombre()).isEqualTo("Negro");
        assertThat(lib.nearest(0xffffff).nombre()).isEqualTo("Blanco");
        assertThat(lib.nearest(0xd02020).nombre()).isEqualTo("Rojo");
        assertThat(lib.all()).hasSize(15);
    }

    @Test
    void laBibliotecaSePuedeCargarDeUnFicheroExterno() throws Exception {
        Path f = tmp.resolve("mios.yml");
        Files.writeString(f, """
                filamentos:
                  - nombre: Mi verde
                    color: "#00aa00"
                    td: 1.8
                """);
        FilamentLibrary lib = new FilamentLibrary(new Pic2filamentProperties(DataSize.ofMegabytes(15), 600, 60, 300,
                tmp, Duration.ofHours(1), 2, 0, f.toString()));
        assertThat(lib.all()).containsExactly(new LibraryFilament("Mi verde", "#00aa00", 1.8));
    }

    @Test
    void imageIoTieneLectorDeWebp() {
        assertThat(javax.imageio.ImageIO.getImageReadersByFormatName("webp").hasNext()).isTrue();
    }
}
