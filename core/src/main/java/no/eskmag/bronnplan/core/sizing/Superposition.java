package no.eskmag.bronnplan.core.sizing;

import java.util.List;

import no.eskmag.bronnplan.core.model.GroundProperties;
import no.eskmag.bronnplan.core.model.LineSource;
import no.eskmag.bronnplan.core.model.LoadSchedule;
import no.eskmag.bronnplan.core.model.LoadStep;

/**
 * Temporal superposition of line source responses for a piecewise constant load. See docs/model.md §4.1.
 *
 * <p>Convention: step i is in force on (t_i, t_{i+1}]. Evaluated at the end of a step, the step itself is
 * still in force and the next one has not started, so G is never evaluated at zero time.
 *
 * <p>Powers are for the whole borehole [W]. Dividing a response by the depth H gives a temperature [K];
 * this is what makes the fluid temperature linear in 1/H and the sizing a closed-form expression.
 */
public final class Superposition {

    private Superposition() {
    }

    /**
     * Q(t), the ground power of the step in force at t.
     *
     * @param t time from the start of operation [s], in (0, endTimeSeconds]
     * @return ground power [W]
     */
    public static double powerAt(double t, LoadSchedule schedule) {
        return schedule.steps().get(stepInForce(t, schedule)).groundPowerW();
    }

    /**
     * S(t) = Σ (Q_i − Q_{i−1}) · G(r, t − t_i) over the steps that started before t.
     *
     * @param t time from the start of operation [s], in (0, endTimeSeconds]
     * @param r distance from the borehole axis, normally r_b [m]
     * @return ground response for the whole borehole [m·K]; divide by depth for a temperature drop [K]
     */
    public static double responseAt(double t, LoadSchedule schedule, GroundProperties ground, double r) {
        List<LoadStep> steps = schedule.steps();
        int last = stepInForce(t, schedule);
        double previousPower = 0.0;
        double sum = 0.0;
        for (int i = 0; i <= last; i++) {
            LoadStep step = steps.get(i);
            double increment = step.groundPowerW() - previousPower;
            if (increment != 0.0) {
                sum += increment * LineSource.responseFactor(ground, r, t - step.startTimeSeconds());
            }
            previousPower = step.groundPowerW();
        }
        return sum;
    }

    /** Index of the last step that started strictly before t. */
    private static int stepInForce(double t, LoadSchedule schedule) {
        if (!(t > 0.0) || t > schedule.endTimeSeconds()) {
            throw new IllegalArgumentException("t [s] must be in (0, " + schedule.endTimeSeconds() + "], got " + t);
        }
        List<LoadStep> steps = schedule.steps();
        int i = 0;
        while (i + 1 < steps.size() && steps.get(i + 1).startTimeSeconds() < t) {
            i++;
        }
        return i;
    }
}
