package com.pic2filament.core.color;

/**
 * Criterio de ajuste entre el color de un píxel y el color simulado de una altura.
 */
public enum ColorDistance {

    /** Color más parecido: 1,4·ΔL² + Δa² + Δb². */
    COLOR {
        @Override
        public double distance(double l1, double a1, double b1, double l2, double a2, double b2) {
            double dl = l1 - l2, da = a1 - a2, db = b1 - b2;
            return 1.4 * dl * dl + da * da + db * db;
        }
    },

    /** Solo luminosidad: ΔL². */
    LUMINANCE {
        @Override
        public double distance(double l1, double a1, double b1, double l2, double a2, double b2) {
            double dl = l1 - l2;
            return dl * dl;
        }
    };

    public abstract double distance(double l1, double a1, double b1, double l2, double a2, double b2);

    /**
     * Aplica contraste y brillo sobre L (alrededor de L=50) y recorta a [0,100].
     *
     * @param contrast   factor multiplicativo (1 = sin cambio)
     * @param brightness desplazamiento en unidades de L (0 = sin cambio)
     */
    public static double adjustL(double l, double contrast, double brightness) {
        double v = (l - 50.0) * contrast + 50.0 + brightness;
        return v < 0 ? 0 : (v > 100 ? 100 : v);
    }
}
