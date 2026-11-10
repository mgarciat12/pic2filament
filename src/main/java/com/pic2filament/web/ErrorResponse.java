package com.pic2filament.web;

import java.time.Instant;
import java.util.List;

/** Cuerpo JSON de los errores de la API. */
public record ErrorResponse(Instant fecha, int estado, String error, String mensaje, List<String> detalles,
        String ruta) {
}
