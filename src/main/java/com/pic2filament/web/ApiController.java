package com.pic2filament.web;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.pic2filament.core.ReliefResult;
import com.pic2filament.service.FilamentLibrary;
import com.pic2filament.service.Job;
import com.pic2filament.service.LibraryFilament;
import com.pic2filament.service.Pic2filamentException;
import com.pic2filament.service.ReliefService;
import com.pic2filament.service.ReliefService.DetectedColor;
import com.pic2filament.service.ReliefService.PreviewMode;

import jakarta.validation.Valid;

/**
 * API REST usada por la página (y utilizable con curl).
 * <pre>
 * POST /api/trabajos                         (multipart "imagen")  → trabajo
 * POST /api/trabajos/{id}/colores?cantidad=4                        → colores detectados
 * POST /api/trabajos/{id}/procesar           (JSON de ajustes)       → resultado
 * GET  /api/trabajos/{id}/vista/{modo}.png   simulacion|relieve|alturas|original
 * GET  /api/trabajos/{id}/descarga.zip | .stl | .3mf
 * GET  /api/filamentos                                              → biblioteca
 * </pre>
 */
@RestController
@RequestMapping("/api")
@Validated
public class ApiController {

    private final ReliefService service;
    private final FilamentLibrary library;

    public ApiController(ReliefService service, FilamentLibrary library) {
        this.service = service;
        this.library = library;
    }

    /** Trabajo creado tras subir una imagen. */
    public record JobResponse(String id, String nombre, int ancho, int alto) {
    }

    @GetMapping("/filamentos")
    public List<LibraryFilament> filaments() {
        return library.all();
    }

    @PostMapping(path = "/trabajos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobResponse> upload(@RequestPart(name = "imagen", required = false) MultipartFile file)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw Pic2filamentException.invalid("No se ha recibido ninguna imagen.");
        }
        Job job = service.upload(file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.status(201)
                .body(new JobResponse(job.id(), job.originalName(), job.width(), job.height()));
    }

    @PostMapping("/trabajos/{id}/colores")
    public List<DetectedColor> colors(@PathVariable String id,
            @RequestParam(name = "cantidad", defaultValue = "4") int count) {
        return service.detectColors(id, count);
    }

    @PostMapping(path = "/trabajos/{id}/procesar", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResultResponse process(@PathVariable String id, @Valid @RequestBody SettingsRequest settings) {
        ReliefResult r = service.process(id, settings);
        return service.toResponse(service.job(id), r);
    }

    @GetMapping(path = "/trabajos/{id}/resultado")
    public ResultResponse result(@PathVariable String id) {
        ReliefResult r = service.lastResult(id);
        return service.toResponse(service.job(id), r);
    }

    @GetMapping(path = "/trabajos/{id}/vista/{modo}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> preview(@PathVariable String id, @PathVariable("modo") String mode)
            throws IOException {
        BufferedImage img = service.preview(id, PreviewMode.parse(mode));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .body(out.toByteArray());
    }

    @GetMapping("/trabajos/{id}/descarga.zip")
    public ResponseEntity<StreamingResponseBody> downloadZip(@PathVariable String id) {
        service.lastResult(id); // falla pronto (con JSON) si no hay resultado
        return download(service.baseName(id) + ".zip", "application/zip", out -> service.writeZip(id, out));
    }

    @GetMapping("/trabajos/{id}/descarga.stl")
    public ResponseEntity<StreamingResponseBody> downloadStl(@PathVariable String id) {
        service.lastResult(id);
        return download(service.baseName(id) + ".stl", "model/stl", out -> service.writeStl(id, out));
    }

    @GetMapping("/trabajos/{id}/descarga.3mf")
    public ResponseEntity<StreamingResponseBody> download3mf(@PathVariable String id) {
        service.lastResult(id);
        return download(service.baseName(id) + ".3mf", "model/3mf", out -> service.write3mf(id, out));
    }

    private static ResponseEntity<StreamingResponseBody> download(String filename, String type,
            StreamingResponseBody body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType(type)).cacheControl(CacheControl.noStore()).body(body);
    }

}
