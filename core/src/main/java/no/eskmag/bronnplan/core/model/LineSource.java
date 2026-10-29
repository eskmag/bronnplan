package no.eskmag.bronnplan.core.model;

import no.eskmag.bronnplan.core.math.ExponentialIntegral;

/**
 * Infinite line source (Kelvin) with constant heat extraction per metre, in homogeneous ground.
 * See docs/model.md §2 and Carslaw &amp; Jaeger, <i>Conduction of Heat in Solids</i>, 2nd ed., §10.4.
 *
 * <p>Sign convention: positive q means heat is <em>extracted</em> from the ground, so a positive
 * temperature change is a temperature drop.
 */
public final class LineSource {

    private LineSource() {
    }

    /**
     * Unit response G(r, t) = 1/(4πλ) · E1(r² / (4αt)): temperature drop per unit of heat extraction.
     *
     * @param r distance from the line source [m], &gt; 0
     * @param t time since the extraction started [s], &gt; 0
     * @return G [m·K/W]
     */
    public static double responseFactor(GroundProperties ground, double r, double t) {
        if (!(r > 0.0)) {
            throw new IllegalArgumentException("r [m] must be positive, got " + r);
        }
        if (!(t > 0.0)) {
            throw new IllegalArgumentException("t [s] must be positive, got " + t);
        }
        double x = r * r / (4.0 * ground.diffusivity() * t);
        return ExponentialIntegral.e1(x) / (4.0 * Math.PI * ground.conductivity());
    }

    /**
     * Earliest time at which the line source is a good approximation, t = 5·r²/α (Eskilson 1987).
     * Before this, the borehole's own radius and heat capacity matter.
     *
     * @param r distance from the line source, normally r_b [m]
     * @return validity limit [s]
     */
    public static double validityTime(GroundProperties ground, double r) {
        return 5.0 * r * r / ground.diffusivity();
    }

    /**
     * Whether t is past {@link #validityTime}. Used to raise warnings, not to stop a calculation.
     *
     * @param r distance from the line source, normally r_b [m]
     * @param t time since the extraction started [s]
     */
    public static boolean isValid(GroundProperties ground, double r, double t) {
        return t > validityTime(ground, r);
    }

    /**
     * ΔT(r, t) = q · G(r, t).
     *
     * @param q heat extraction per metre of borehole [W/m]
     * @param r distance from the line source [m]
     * @param t time since the extraction started [s]
     * @return temperature drop relative to T₀ [K]
     */
    public static double temperatureChange(GroundProperties ground, double q, double r, double t) {
        return q * responseFactor(ground, r, t);
    }

    /**
     * T_f(t) = T₀ − q · (R_b + G(r_b, t)), mean fluid temperature for constant extraction.
     *
     * @param q heat extraction per metre of borehole [W/m]
     * @param t time since the extraction started [s]
     * @return mean fluid temperature [°C]
     */
    public static double fluidTemperature(GroundProperties ground, Borehole borehole, double q, double t) {
        double g = responseFactor(ground, borehole.radius(), t);
        return ground.undisturbedTemperature() - q * (borehole.thermalResistance() + g);
    }
}
