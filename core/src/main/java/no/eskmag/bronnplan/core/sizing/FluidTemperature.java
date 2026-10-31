package no.eskmag.bronnplan.core.sizing;

import java.util.ArrayList;
import java.util.List;

import no.eskmag.bronnplan.core.model.Borehole;
import no.eskmag.bronnplan.core.model.GroundProperties;
import no.eskmag.bronnplan.core.model.LoadSchedule;

/**
 * Mean fluid temperature for a varying load. See docs/model.md §4.2.
 *
 * <pre>
 * L(t)   = Q(t)·R_b + S(t)        [m·K]
 * T_f(t) = T₀ − L(t) / H          [°C]
 * </pre>
 */
public final class FluidTemperature {

    private FluidTemperature() {
    }

    /**
     * L(t) = Q(t)·R_b + S(t): the temperature drop from T₀ to the fluid, times the depth.
     *
     * @param t time from the start of operation [s], in (0, endTimeSeconds]
     * @return [m·K]
     */
    public static double loadTerm(double t, LoadSchedule schedule, GroundProperties ground, Borehole borehole) {
        return Superposition.powerAt(t, schedule) * borehole.thermalResistance()
                + Superposition.responseAt(t, schedule, ground, borehole.radius());
    }

    /**
     * @param depthM borehole depth H [m], &gt; 0
     * @param t      time from the start of operation [s], in (0, endTimeSeconds]
     * @return mean fluid temperature [°C]
     */
    public static double at(double depthM, double t, LoadSchedule schedule, GroundProperties ground,
                            Borehole borehole) {
        requirePositiveDepth(depthM);
        return ground.undisturbedTemperature() - loadTerm(t, schedule, ground, borehole) / depthM;
    }

    /**
     * T_f at the end of every load step: the end of each month, and the end of the peak.
     *
     * @param depthM borehole depth H [m], &gt; 0
     */
    public static List<TemperaturePoint> series(double depthM, LoadSchedule schedule, GroundProperties ground,
                                                Borehole borehole) {
        requirePositiveDepth(depthM);
        List<TemperaturePoint> points = new ArrayList<>(schedule.steps().size());
        for (int i = 0; i < schedule.steps().size(); i++) {
            double t = schedule.stepEndSeconds(i);
            points.add(new TemperaturePoint(t, at(depthM, t, schedule, ground, borehole)));
        }
        return List.copyOf(points);
    }

    private static void requirePositiveDepth(double depthM) {
        if (!(depthM > 0.0) || Double.isInfinite(depthM)) {
            throw new IllegalArgumentException("depthM [m] must be positive and finite, got " + depthM);
        }
    }
}
