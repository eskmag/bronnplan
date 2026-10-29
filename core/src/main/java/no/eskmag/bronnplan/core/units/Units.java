package no.eskmag.bronnplan.core.units;

/**
 * Unit conversions between the energy units users enter (kWh, kW) and SI units used internally (J, W, s).
 *
 * <p>All months have the same length, 8760 h / 12 = 730 h. This simplification keeps the monthly load
 * steps evenly spaced; the error in a month's mean power is at most about 6 % (February). See docs/model.md §3.
 */
public final class Units {

    public static final double SECONDS_PER_HOUR = 3600.0;
    public static final double HOURS_PER_YEAR = 8760.0;
    public static final int MONTHS_PER_YEAR = 12;
    public static final double HOURS_PER_MONTH = HOURS_PER_YEAR / MONTHS_PER_YEAR;
    public static final double SECONDS_PER_MONTH = HOURS_PER_MONTH * SECONDS_PER_HOUR;
    public static final double SECONDS_PER_YEAR = HOURS_PER_YEAR * SECONDS_PER_HOUR;

    private static final double JOULES_PER_KWH = 3.6e6;
    private static final double WATTS_PER_KW = 1000.0;

    private Units() {
    }

    /** @param kWh energy [kWh] @return energy [J] */
    public static double kWhToJoules(double kWh) {
        return kWh * JOULES_PER_KWH;
    }

    /** @param kW power [kW] @return power [W] */
    public static double kWToWatts(double kW) {
        return kW * WATTS_PER_KW;
    }

    /**
     * Mean power when an energy amount is spread evenly over one 730-hour month.
     *
     * @param kWh energy in the month [kWh]
     * @return mean power [W]
     */
    public static double monthlyEnergyToMeanPowerW(double kWh) {
        return kWhToJoules(kWh) / SECONDS_PER_MONTH;
    }
}
