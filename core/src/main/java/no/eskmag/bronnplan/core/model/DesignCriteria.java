package no.eskmag.bronnplan.core.model;

/**
 * What the borehole must satisfy. T_min &lt; T₀ is checked by the sizer, which has both values.
 *
 * @param minFluidTempC lowest allowed mean fluid temperature, T_min [°C]
 * @param years         design period [years], &ge; 1
 */
public record DesignCriteria(double minFluidTempC, int years) {

    public static final double DEFAULT_MIN_FLUID_TEMP_C = -3.0;
    public static final int DEFAULT_YEARS = 25;

    public DesignCriteria {
        if (!Double.isFinite(minFluidTempC)) {
            throw new IllegalArgumentException("minFluidTempC [°C] must be finite, got " + minFluidTempC);
        }
        if (years < 1) {
            throw new IllegalArgumentException("years [years] must be at least 1, got " + years);
        }
    }

    /** −3 °C over 25 years, see CLAUDE.md §3.5. */
    public static DesignCriteria defaults() {
        return new DesignCriteria(DEFAULT_MIN_FLUID_TEMP_C, DEFAULT_YEARS);
    }
}
