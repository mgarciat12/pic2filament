package com.pic2filament.core.export;

import java.util.Locale;

import com.pic2filament.core.ReliefResult;
import com.pic2filament.core.ReliefSettings;
import com.pic2filament.core.color.ColorDistance;
import com.pic2filament.core.model.LayerSettings;

/**
 * Texto con los ajustes del laminador y la lista de cambios de filamento.
 */
public final class SlicerInstructions {

    private SlicerInstructions() {
    }

    public static String text(ReliefResult r, String sourceName) {
        ReliefSettings s = r.settings();
        LayerSettings l = s.layers();
        StringBuilder sb = new StringBuilder();
        String nl = "\r\n";
        sb.append("pic2filament - instrucciones de impresión").append(nl);
        sb.append("==========================================").append(nl).append(nl);
        if (sourceName != null && !sourceName.isBlank()) {
            sb.append("Imagen: ").append(sourceName).append(nl);
        }
        sb.append(f("Tamaño de la pieza: %.2f × %.2f mm, altura máxima %.2f mm", r.widthMm(), r.depthMm(),
                r.maxHeightMm())).append(nl);
        sb.append(f("Rejilla: %d × %d vértices (%.3f mm entre vértices)", r.heights().width(), r.heights().height(),
                r.step())).append(nl);
        sb.append("Criterio de ajuste: ")
                .append(s.metric() == ColorDistance.COLOR ? "color más parecido" : "solo luminosidad").append(nl);
        sb.append(f("Contraste %.2f, brillo %.1f, reducción de ruido: %s", s.contrast(), s.brightness(),
                s.denoise() ? "sí" : "no")).append(nl);
        if (s.borderMm() > 0) {
            sb.append(f("Marco: %.1f mm", s.borderMm())).append(nl);
        }
        sb.append(nl);

        sb.append("AJUSTES DEL LAMINADOR (obligatorios para que los colores coincidan)").append(nl);
        sb.append("--------------------------------------------------------------------").append(nl);
        sb.append(f("- Altura de la primera capa: %.2f mm", l.firstLayerHeight())).append(nl);
        sb.append(f("- Altura de capa: %.2f mm", l.layerHeight())).append(nl);
        sb.append("- Altura de capa variable/adaptativa: desactivada").append(nl);
        sb.append("- Relleno: 100 % (o suficientes perímetros/paredes para que la pieza sea maciza)").append(nl);
        sb.append("- Capas superiores e inferiores: las que quieras; con relleno al 100 % no influyen").append(nl);
        sb.append("- Imprime la pieza tumbada, con la imagen hacia arriba (tal cual sale el STL)").append(nl);
        sb.append(f("- Capas totales: %d (de ellas %d de base)", l.totalLayers(), l.baseLayers())).append(nl);
        sb.append(nl);

        sb.append("CAMBIOS DE FILAMENTO").append(nl);
        sb.append("--------------------").append(nl);
        sb.append("Capas numeradas desde 1, como en el laminador. Z = altura de la cara superior de la capa.").append(nl)
                .append(nl);
        for (ColorChange c : r.changes()) {
            if (c.isInitial()) {
                sb.append(f("Empieza con  filamento %d: %-16s %s  TD %.2f mm", c.filament(), c.name(), c.hex(), c.td()))
                        .append(nl);
            } else {
                sb.append(f("Capa %3d (Z = %.2f mm): cambia al filamento %d: %-16s %s  TD %.2f mm", c.startLayer(),
                        c.z(), c.filament(), c.name(), c.hex(), c.td())).append(nl);
            }
        }
        sb.append(nl);

        sb.append("CÓMO INTRODUCIR LOS CAMBIOS").append(nl);
        sb.append("---------------------------").append(nl);
        sb.append("El cambio se hace al EMPEZAR la capa indicada (pausa o M600 antes de imprimirla).").append(nl)
                .append(nl);
        sb.append("Bambu Studio / OrcaSlicer:").append(nl);
        sb.append("  1. Lamina la pieza y ve a la vista previa.").append(nl);
        sb.append("  2. Mueve el deslizador vertical de capas hasta la capa indicada.").append(nl);
        sb.append("  3. Pulsa con el botón derecho en el «+» y elige «Añadir pausa» (cambio manual)").append(nl);
        sb.append("     o «Cambiar filamento» si tienes AMS/varios extrusores.").append(nl).append(nl);
        sb.append("PrusaSlicer:").append(nl);
        sb.append("  1. Lamina y, en la vista previa, mueve el deslizador vertical hasta la capa indicada.").append(nl);
        sb.append("  2. Pulsa el «+» naranja para añadir un cambio de color (M600)").append(nl);
        sb.append("     o con el botón derecho elige «Añadir pausa».").append(nl).append(nl);
        sb.append("Comprueba que el número de capa y la altura Z que muestra el laminador coinciden con esta lista.")
                .append(nl);
        sb.append(nl).append(f("Calculado en %d ms, error medio %.2f.", r.elapsedMs(), r.error())).append(nl);
        return sb.toString();
    }

    private static String f(String fmt, Object... args) {
        return String.format(Locale.forLanguageTag("es-ES"), fmt, args);
    }
}
