package no.eskmag.bronnplan.core.model;

import no.eskmag.bronnplan.core.units.Units;

/**
 * Converts the building's heat demand to heat extracted from the ground. The heat pump delivers
 * Q_heat; the compressor supplies Q_heat/COP of it, and the rest comes from the ground.
 * See docs/model.md §3.1.
 */
public final class LoadConverter {

    private LoadConverter() {
    }

    /**
     * Q_ground = Q_heat · (1 − 1/SCOP).
     *
     * @param annualHeatKWh heat delivered by the heat pump per year [kWh], &ge; 0
     * @param scop          seasonal coefficient of performance [–], &gt; 1
     * @return heat extracted from the ground per year [kWh]
     */
    public static double groundEnergy(double annualHeatKWh, double scop) {
        requireNonNegative(annualHeatKWh, "annualHeatKWh [kWh]");
        requireCopAboveOne(scop, "scop [–]");
        return annualHeatKWh * (1.0 - 1.0 / scop);
    }

    /**
     * P_ground = P_heat · (1 − 1/COP).
     *
     * @param peakHeatKW heat power delivered at the design point [kW], &ge; 0
     * @param copPeak    coefficient of performance at the design point [–], &gt; 1
     * @return power extracted from the ground [W]
     */
    public static double groundPeakPower(double peakHeatKW, double copPeak) {
        requireNonNegative(peakHeatKW, "peakHeatKW [kW]");
        requireCopAboveOne(copPeak, "copPeak [–]");
        return Units.kWToWatts(peakHeatKW) * (1.0 - 1.0 / copPeak);
    }

    private static void requireNonNegative(double value, String name) {
        if (!(value >= 0.0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be non-negative and finite, got " + value);
        }
    }

    private static void requireCopAboveOne(double value, String name) {
        if (!(value > 1.0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be greater than 1 and finite, got " + value);
        }
    }
}
