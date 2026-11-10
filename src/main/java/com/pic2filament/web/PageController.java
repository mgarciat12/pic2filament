package com.pic2filament.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.pic2filament.config.Pic2filamentProperties;
import com.pic2filament.core.ReliefResult;
import com.pic2filament.service.FilamentLibrary;
import com.pic2filament.service.ImageLoader;
import com.pic2filament.service.ReliefService;

/** Página principal y fragmentos HTML renderizados con Thymeleaf. */
@Controller
public class PageController {

    private final FilamentLibrary library;
    private final Pic2filamentProperties props;
    private final ReliefService service;

    public PageController(FilamentLibrary library, Pic2filamentProperties props, ReliefService service) {
        this.library = library;
        this.props = props;
        this.service = service;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("biblioteca", library.all());
        model.addAttribute("maxSubida", ImageLoader.human(props.maxUploadSize().toBytes()));
        model.addAttribute("maxSubidaBytes", props.maxUploadSize().toBytes());
        model.addAttribute("maxRejilla", props.maxGrid());
        model.addAttribute("maxCapas", props.maxTotalLayers());
        model.addAttribute("d", new Defaults());
        return "index";
    }

    /** Tabla de cambios de filamento del último resultado (fragmento HTML). */
    @GetMapping("/trabajos/{id}/tabla-cambios")
    public String changesTable(@PathVariable String id, Model model) {
        ReliefResult r = service.lastResult(id);
        model.addAttribute("cambios", service.toResponse(service.job(id), r).cambios());
        return "fragmentos :: tablaCambios";
    }

    /** Valores por defecto mostrados en el formulario. */
    public record Defaults(double ancho, double detalle, double capa, double primeraCapa, int base, int total) {
        Defaults() {
            this(SettingsRequest.DEFAULT_WIDTH, SettingsRequest.DEFAULT_DETAIL, SettingsRequest.DEFAULT_LAYER,
                    SettingsRequest.DEFAULT_FIRST_LAYER, SettingsRequest.DEFAULT_BASE, SettingsRequest.DEFAULT_TOTAL);
        }
    }
}
