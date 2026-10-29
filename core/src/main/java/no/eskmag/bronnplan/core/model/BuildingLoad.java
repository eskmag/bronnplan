package no.eskmag.bronnplan.core.model;

/**
 * Heat demand of the building, as covered by the heat pump. See docs/model.md §3.
 *
 * @param annualHeatDemandKWh heat delivered by the heat pump per year [kWh], &gt; 0
 * @param peakHeatPowerKW     heat power at the design point [kW], &gt; 0
 * @param scop                seasonal coefficient of performance [–], &gt; 1
 * @param copPeak             coefficient of performance at the design point [–], &gt; 1
 * @param monthlyProfile      distribution of the annual demand over the months
 */
public record BuildingLoad(double annualHeatDemandKWh, double peakHeatPowerKW, double scop, double copPeak,
                           MonthlyProfile monthlyProfile) {

    public BuildingLoad {
        requirePositive(annualHeatDemandKWh, "annualHeatDemandKWh [kWh]");
        requirePositive(peakHeatPowerKW, "peakHeatPowerKW [kW]");
        requireAboveOne(scop, "scop [–]");
        requireAboveOne(copPeak, "copPeak [–]");
        if (monthlyProfile == null) {
            throw new IllegalArgumentException("monthlyProfile must not be null");
        }
    }

    /** Heat extracted from the ground per year [kWh]. */
    public double annualGroundEnergyKWh() {
        return LoadConverter.groundEnergy(annualHeatDemandKWh, scop);
    }

    /** Power extracted from the ground at the design point [W]. */
    public double peakGroundPowerW() {
        return LoadConverter.groundPeakPower(peakHeatPowerKW, copPeak);
    }

    private static void requirePositive(double value, String name) {
        if (!(value > 0.0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be positive and finite, got " + value);
        }
    }

    private static void requireAboveOne(double value, String name) {
        if (!(value > 1.0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be greater than 1 and finite, got " + value);
        }
    }
}
