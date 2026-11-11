package com.pic2filament;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.pic2filament.core.export.ZipExporterTest;

/**
 * Prueba de extremo a extremo con servidor real (HTTP): sube cada imagen de ejemplo de
 * {@code src/test/resources} (ficheros {@code ejemplo*}), la procesa con 4 filamentos, descarga el
 * ZIP, verifica el STL y guarda los resultados en {@code ejemplo/salida/} para revisarlos.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class EndToEndIT {

    static final String SETTINGS = """
            {"anchoMm":100,"mmPorPixel":0.25,"alturaCapa":0.08,"alturaPrimeraCapa":0.16,"capasBase":4,"capasTotales":28,
             "filamentos":[{"nombre":"Negro","color":"#111111","td":0.6},{"nombre":"Rojo","color":"#c0262b","td":2.5},
                           {"nombre":"Azul cielo","color":"#5fb5e8","td":3.5},{"nombre":"Blanco","color":"#f7f7f5","td":4.5}],
             "criterio":"color","contraste":1,"brillo":0,"reducirRuido":true,"optimizar":true}
            """;

    @LocalServerPort
    int port;

    final HttpClient http = HttpClient.newHttpClient();

    @TestFactory
    Stream<DynamicTest> procesaLasImagenesDeEjemplo() throws IOException {
        List<Path> images;
        try (Stream<Path> files = Files.list(Path.of("src/test/resources"))) {
            images = files.filter(p -> p.getFileName().toString().startsWith("ejemplo"))
                    .filter(p -> p.toString().toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|webp)$"))
                    .sorted().toList();
        }
        assertThat(images).as("imágenes de ejemplo en src/test/resources").isNotEmpty();
        return images.stream().map(p -> DynamicTest.dynamicTest(p.getFileName().toString(), () -> process(p)));
    }

    void process(Path image) throws Exception {
        String base = "http://localhost:" + port;

        // Subida (multipart)
        String boundary = "----pic2filament" + UUID.randomUUID();
        byte[] file = Files.readAllBytes(image);
        String name = image.getFileName().toString();
        String type = name.endsWith(".png") ? "image/png" : name.endsWith(".webp") ? "image/webp" : "image/jpeg";
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"imagen\"; filename=\"" + name
                + "\"\r\nContent-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> up = http.send(HttpRequest.newBuilder(URI.create(base + "/api/trabajos"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(head, file, tail))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(up.statusCode()).as(up.body()).isEqualTo(201);
        Matcher m = Pattern.compile("\"id\":\"([0-9a-f]{32})\"").matcher(up.body());
        assertThat(m.find()).isTrue();
        String api = base + "/api/trabajos/" + m.group(1);

        // Proceso con 4 filamentos
        long t0 = System.nanoTime();
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(api + "/procesar"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(SETTINGS)).build(),
                HttpResponse.BodyHandlers.ofString());
        double seconds = (System.nanoTime() - t0) / 1e9;
        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        assertThat(seconds).as("tiempo de proceso").isLessThan(5);
        assertThat(res.body()).contains("\"cambios\"").contains("\"capaInicio\":1");

        // Vista previa
        HttpResponse<byte[]> preview = http.send(HttpRequest.newBuilder(URI.create(api + "/vista/simulacion.png")).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(ImageIO.read(new ByteArrayInputStream(preview.body()))).isNotNull();

        // ZIP
        HttpResponse<byte[]> zip = http.send(HttpRequest.newBuilder(URI.create(api + "/descarga.zip")).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(zip.statusCode()).isEqualTo(200);
        assertThat(zip.headers().firstValue("Content-Type")).hasValue("application/zip");
        Map<String, byte[]> entries = ZipExporterTest.unzip(zip.body());
        String stem = name.substring(0, name.lastIndexOf('.'));
        assertThat(entries).containsOnlyKeys(stem + ".stl", stem + "-vista-previa.png", stem + "-cambios.txt");

        // STL: tamaño, estanqueidad, caja y altura máxima
        byte[] stlBytes = entries.get(stem + ".stl");
        StlChecks.Stl stl = StlChecks.parse(stlBytes);
        StlChecks.assertWatertight(stl);
        assertThat(StlChecks.signedVolume(stl)).isPositive();
        double[] b = stl.bounds();
        assertThat(b[0]).isCloseTo(0, within(1e-4));
        assertThat(b[1]).isCloseTo(0, within(1e-4));
        assertThat(b[2]).isCloseTo(0, within(1e-6));
        assertThat(b[3]).isCloseTo(100, within(1e-3));
        assertThat(b[5]).isLessThanOrEqualTo(0.16 + 27 * 0.08 + 1e-4).isGreaterThan(0.16 + 3 * 0.08);
        String txt = new String(entries.get(stem + "-cambios.txt"), StandardCharsets.UTF_8);
        assertThat(txt).contains("CAMBIOS DE FILAMENTO").contains("cambia al filamento 4");

        // Proyecto 3MF (Anycubic Kobra 3, cambio automático)
        HttpResponse<byte[]> mf = http.send(HttpRequest.newBuilder(
                URI.create(api + "/descarga.3mf?perfil=anycubic-kobra3-0.4&modo=automatico")).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(mf.statusCode()).isEqualTo(200);
        Map<String, byte[]> project = ZipExporterTest.unzip(mf.body());
        String changes = new String(project.get("Metadata/custom_gcode_per_layer.xml"), StandardCharsets.UTF_8);
        assertThat(changes.split("type=\"2\"", -1)).hasSize(4); // 3 cambios de herramienta

        // Resultados para revisar
        Path out = Path.of("ejemplo", "salida");
        Files.createDirectories(out);
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            Files.write(out.resolve(e.getKey()), e.getValue());
        }
        Files.write(out.resolve(stem + "-kobra3.3mf"), mf.body());
    }
}
