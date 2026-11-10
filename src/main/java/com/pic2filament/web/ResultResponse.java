package com.pic2filament.web;

import java.util.List;

/** Resultado de una conversión para el navegador. */
public record ResultResponse(
        int version,
        double anchoMm,
        double fondoMm,
        double alturaMaximaMm,
        int columnas,
        int filas,
        double pasoMm,
        long triangulos,
        long tamanoStl,
        long tiempoMs,
        double error,
        int[] capasInicio,
        List<Change> cambios,
        List<Layer> capas) {

    /** Fila de la tabla de cambios. */
    public record Change(int filamento, String nombre, int capaInicio, double z, String color, double td) {
    }

    /** Capa de la pila: número (desde 1), Z superior, filamento (desde 1) y color visible. */
    public record Layer(int capa, double z, int filamento, String colorFilamento, String colorVisible) {
    }
}
