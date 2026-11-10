package com.pic2filament.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Ajustes de conversión tal como llegan del navegador (JSON). Los campos nulos toman el valor
 * por defecto.
 */
public record SettingsRequest(
        @DecimalMin(value = "10", message = "El ancho debe ser de al menos 10 mm")
        @DecimalMax(value = "500", message = "El ancho no puede superar 500 mm") Double anchoMm,
        @DecimalMin(value = "0.05", message = "El detalle debe ser de al menos 0,05 mm por píxel")
        @DecimalMax(value = "5", message = "El detalle no puede superar 5 mm por píxel") Double mmPorPixel,
        @DecimalMin(value = "0.04", message = "La altura de capa debe ser de al menos 0,04 mm")
        @DecimalMax(value = "0.5", message = "La altura de capa no puede superar 0,5 mm") Double alturaCapa,
        @DecimalMin(value = "0.04", message = "La altura de la primera capa debe ser de al menos 0,04 mm")
        @DecimalMax(value = "1", message = "La altura de la primera capa no puede superar 1 mm") Double alturaPrimeraCapa,
        @Min(value = 1, message = "Debe haber al menos 1 capa de base")
        @Max(value = 200, message = "No puede haber más de 200 capas de base") Integer capasBase,
        @Min(value = 2, message = "Debe haber al menos 2 capas totales")
        @Max(value = 1000, message = "Demasiadas capas totales") Integer capasTotales,
        @NotNull(message = "Indica los filamentos")
        @Size(min = 2, max = 4, message = "Elige entre 2 y 4 filamentos") List<@Valid FilamentRequest> filamentos,
        @Pattern(regexp = "^(color|luminosidad)$", message = "El criterio debe ser «color» o «luminosidad»") String criterio,
        @DecimalMin(value = "0.2", message = "El contraste debe estar entre 0,2 y 3")
        @DecimalMax(value = "3", message = "El contraste debe estar entre 0,2 y 3") Double contraste,
        @DecimalMin(value = "-50", message = "El brillo debe estar entre -50 y 50")
        @DecimalMax(value = "50", message = "El brillo debe estar entre -50 y 50") Double brillo,
        Boolean reducirRuido,
        Boolean optimizar,
        @DecimalMin(value = "0", message = "El marco no puede ser negativo")
        @DecimalMax(value = "20", message = "El marco no puede superar 20 mm") Double marcoMm) {

    public static final double DEFAULT_WIDTH = 100;
    public static final double DEFAULT_DETAIL = 0.25;
    public static final double DEFAULT_LAYER = 0.08;
    public static final double DEFAULT_FIRST_LAYER = 0.16;
    public static final int DEFAULT_BASE = 4;
    public static final int DEFAULT_TOTAL = 28;
}
