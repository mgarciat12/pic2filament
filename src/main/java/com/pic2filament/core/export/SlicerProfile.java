package com.pic2filament.core.export;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Perfil de laminador (formato de proyecto de Bambu Studio y derivados: Anycubic Slicer Next,
 * OrcaSlicer). {@code settings} es el contenido completo de {@code Metadata/project_settings.config}:
 * cada valor es un texto o una lista de textos.
 *
 * @param id       identificador (nombre del fichero sin extensión)
 * @param name     nombre visible
 * @param settings ajustes completos del proyecto
 */
public record SlicerProfile(String id, String name, Map<String, Object> settings) {

    public SlicerProfile {
        settings = new LinkedHashMap<>(settings);
        for (Map.Entry<String, Object> e : settings.entrySet()) {
            Object v = e.getValue();
            boolean ok = v instanceof String
                    || (v instanceof List<?> l && l.stream().allMatch(x -> x instanceof String));
            if (!ok) {
                throw new IllegalArgumentException("Valor no admitido en el perfil «" + id + "»: " + e.getKey());
            }
        }
        for (String required : List.of("printer_settings_id", "filament_settings_id", "layer_height")) {
            if (!settings.containsKey(required)) {
                throw new IllegalArgumentException("El perfil «" + id + "» no tiene la clave " + required);
            }
        }
    }

    /** Número de ranuras de filamento definidas en el perfil. */
    public int filamentSlots() {
        return settings.get("filament_settings_id") instanceof List<?> l ? l.size() : 1;
    }

    /** Centro de la cama (mm) según {@code printable_area}; 128×128 si no se puede leer. */
    public double[] bedCenter() {
        if (settings.get("printable_area") instanceof List<?> pts && !pts.isEmpty()) {
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            try {
                for (Object o : pts) {
                    String[] xy = ((String) o).split("x");
                    double x = Double.parseDouble(xy[0]), y = Double.parseDouble(xy[1]);
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
                return new double[] { (minX + maxX) / 2, (minY + maxY) / 2 };
            } catch (RuntimeException e) {
                // valor inesperado: usamos el centro por defecto
            }
        }
        return new double[] { 128, 128 };
    }

    /** Versión del laminador que generó el perfil (para la cabecera del 3MF). */
    public String version() {
        return settings.get("version") instanceof String s && !s.isBlank() ? s : "01.10.01.50";
    }
}
