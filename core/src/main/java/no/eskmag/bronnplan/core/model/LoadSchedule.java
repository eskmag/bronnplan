package no.eskmag.bronnplan.core.model;

import java.util.ArrayList;
import java.util.List;

import no.eskmag.bronnplan.core.units.Units;

/**
 * Piecewise constant ground load over the design period: one step per 730-hour month, plus a short
 * peak in the coldest month of the last year. Input to temporal superposition. See docs/model.md §3.3.
 *
 * @param steps          load steps in strictly increasing start time, the first at t = 0
 * @param endTimeSeconds end of the design period [s], after the last step's start
 */
public record LoadSchedule(List<LoadStep> steps, double endTimeSeconds) {

    /** Duration of the design-power peak, CLAUDE.md §3.1. */
    public static final double PEAK_DURATION_SECONDS = 6.0 * Units.SECONDS_PER_HOUR;

    public LoadSchedule {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("steps must not be empty");
        }
        steps = List.copyOf(steps);
        if (steps.getFirst().startTimeSeconds() != 0.0) {
            throw new IllegalArgumentException(
                    "first step must start at 0 s, got " + steps.getFirst().startTimeSeconds());
        }
        for (int i = 1; i < steps.size(); i++) {
            if (!(steps.get(i).startTimeSeconds() > steps.get(i - 1).startTimeSeconds())) {
                throw new IllegalArgumentException("step start times [s] must be strictly increasing, step " + i
                        + " starts at " + steps.get(i).startTimeSeconds());
            }
        }
        if (!(endTimeSeconds > steps.getLast().startTimeSeconds()) || Double.isInfinite(endTimeSeconds)) {
            throw new IllegalArgumentException(
                    "endTimeSeconds [s] must be finite and after the last step, got " + endTimeSeconds);
        }
    }

    /**
     * @param load  the building's heat demand
     * @param years design period [years], &ge; 1
     */
    public static LoadSchedule build(BuildingLoad load, int years) {
        if (years < 1) {
            throw new IllegalArgumentException("years [years] must be at least 1, got " + years);
        }
        MonthlyProfile profile = load.monthlyProfile();
        double annualGroundKWh = load.annualGroundEnergyKWh();
        int peakMonth = profile.coldestMonth();

        List<LoadStep> steps = new ArrayList<>();
        for (int year = 0; year < years; year++) {
            for (int month = 0; month < Units.MONTHS_PER_YEAR; month++) {
                double monthStart = (year * Units.MONTHS_PER_YEAR + month) * Units.SECONDS_PER_MONTH;
                double monthlyPowerW = Units.monthlyEnergyToMeanPowerW(annualGroundKWh * profile.fraction(month));
                steps.add(new LoadStep(monthStart, monthlyPowerW));
                if (year == years - 1 && month == peakMonth) {
                    addPeak(steps, monthStart, monthlyPowerW, load.peakGroundPowerW());
                }
            }
        }
        return new LoadSchedule(steps, years * Units.SECONDS_PER_YEAR);
    }

    /**
     * Adds the 6-hour design-power peak at the end of the month that starts at monthStart. The month's own
     * step is already the last element of steps; the next month's step, added after this method returns,
     * ends the peak. See docs/model.md §3.3.
     *
     * @param monthStart    start of the peak month [s]
     * @param monthlyPowerW mean ground power in the peak month [W]
     * @param peakPowerW    ground power at the design point [W]
     */
    static void addPeak(List<LoadStep> steps, double monthStart, double monthlyPowerW, double peakPowerW) {
        // At the end of the month the ground has been cooled by the whole month's load, so this gives the lowest T_f.
        double peakStart = monthStart + Units.SECONDS_PER_MONTH - PEAK_DURATION_SECONDS;
        // The peak comes on top of the monthly mean (as in the ASHRAE method): conservative, and the extra
        // energy over 6 hours is negligible compared with the month.
        steps.add(new LoadStep(peakStart, monthlyPowerW + peakPowerW));
    }
}
