package no.eskmag.bronnplan.core.model;

/**
 * Constant heat extraction from the ground, starting at a given time and lasting until the next step.
 *
 * @param startTimeSeconds time from the start of operation [s], &ge; 0
 * @param groundPowerW     power extracted from the ground for the whole borehole [W]
 */
public record LoadStep(double startTimeSeconds, double groundPowerW) {

    public LoadStep {
        if (!(startTimeSeconds >= 0.0) || Double.isInfinite(startTimeSeconds)) {
            throw new IllegalArgumentException(
                    "startTimeSeconds [s] must be non-negative and finite, got " + startTimeSeconds);
        }
        if (!Double.isFinite(groundPowerW)) {
            throw new IllegalArgumentException("groundPowerW [W] must be finite, got " + groundPowerW);
        }
    }
}
