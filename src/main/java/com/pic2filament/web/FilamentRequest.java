package com.pic2filament.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Filamento tal como llega del formulario.
 *
 * @param nombre     nombre (opcional)
 * @param color      color hexadecimal #RRGGBB
 * @param td         distancia de transmisión en mm
 * @param capaInicio capa de inicio (desde 1); solo se usa con los cambios fijados a mano
 */
public record FilamentRequest(
        @Size(max = 40, message = "El nombre del filamento no puede superar 40 caracteres") String nombre,
        @NotBlank(message = "Indica el color del filamento")
        @Pattern(regexp = "^#?[0-9a-fA-F]{6}$", message = "El color debe tener el formato #RRGGBB") String color,
        @NotNull(message = "Indica el TD del filamento")
        @DecimalMin(value = "0.05", message = "El TD debe ser al menos 0,05 mm")
        @DecimalMax(value = "100", message = "El TD no puede superar 100 mm") Double td,
        @Min(value = 1, message = "La capa de inicio debe ser 1 o mayor")
        @Max(value = 10000, message = "La capa de inicio es demasiado alta") Integer capaInicio) {
}
