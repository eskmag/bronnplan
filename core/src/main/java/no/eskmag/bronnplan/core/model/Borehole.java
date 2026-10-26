package no.eskmag.bronnplan.core.model;

/**
 * A single borehole heat exchanger.
 *
 * @param radius            r_b, borehole radius [m]
 * @param thermalResistance R_b, effective borehole thermal resistance from fluid to borehole wall [m·K/W]
 */
public record Borehole(double radius, double thermalResistance) {

    public Borehole {
        if (!(radius > 0.0) || Double.isInfinite(radius)) {
            throw new IllegalArgumentException("radius must be positive and finite, got " + radius);
        }
        if (!(thermalResistance >= 0.0) || Double.isInfinite(thermalResistance)) {
            throw new IllegalArgumentException(
                    "thermalResistance must be non-negative and finite, got " + thermalResistance);
        }
    }
}
