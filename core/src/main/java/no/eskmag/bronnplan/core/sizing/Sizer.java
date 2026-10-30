package no.eskmag.bronnplan.core.sizing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import no.eskmag.bronnplan.core.model.LineSource;
import no.eskmag.bronnplan.core.model.LoadSchedule;
import no.eskmag.bronnplan.core.units.Units;

/**
 * Sizes a single borehole with the infinite line source. See docs/model.md §4.3.
 *
 * <p>T_f(t) = T₀ − L(t)/H is linear in 1/H, so the smallest depth with T_f ≥ T_min everywhere is
 * <pre>
 * H = max_t L(t) / (T₀ − T_min)
 * </pre>
 * The maximum is taken over the end of every load step. Within a step Q is constant; after a load increase the
 * ground response grows towards the end, and after a decrease the ground recovers, so the step's coldest point
 * is at an end, and its start is never colder than the previous step's end. This is not proven for every load
 * shape; SizerTest checks it against a dense time grid.
 */
public final class Sizer {

    public static final String MODEL_VERSION = "infinite-line-source/monthly-steps/1";

    /** Above this depth one borehole is rarely practical; recommend several. */
    public static final double MAX_SINGLE_BOREHOLE_DEPTH_M = 300.0;

    /** Warnings are user-facing Norwegian text, so numbers use a decimal comma on every machine. */
    private static final Locale NORWEGIAN = Locale.forLanguageTag("nb-NO");

    private Sizer() {
    }

    public static SizingResult requiredDepth(SizingInputs in) {
        LoadSchedule schedule = LoadSchedule.build(in.load(), in.criteria().years());

        double maxLoadTerm = 0.0;
        for (int i = 0; i < schedule.steps().size(); i++) {
            double t = schedule.stepEndSeconds(i);
            maxLoadTerm = Math.max(maxLoadTerm, FluidTemperature.loadTerm(t, schedule, in.ground(), in.borehole()));
        }
        double allowedDrop = in.ground().undisturbedTemperature() - in.criteria().minFluidTempC();
        double depthM = maxLoadTerm / allowedDrop;

        List<TemperaturePoint> series = FluidTemperature.series(depthM, schedule, in.ground(), in.borehole());
        TemperaturePoint coldest = series.getFirst();
        for (TemperaturePoint p : series) {
            if (p.fluidTempC() < coldest.fluidTempC()) {
                coldest = p;
            }
        }
        return new SizingResult(depthM, coldest.fluidTempC(), monthOf(coldest.timeSeconds()), series,
                warnings(in, depthM, schedule), MODEL_VERSION);
    }

    /** Month that ends at or contains t, 0 = first month. Step ends fall on month ends or inside a month. */
    static int monthOf(double t) {
        return (int) Math.ceil(t / Units.SECONDS_PER_MONTH - 1e-9) - 1;
    }

    /**
     * Warnings for the user, in Norwegian. They do not stop the calculation.
     *
     * @param depthM   required depth from the closed form [m]
     * @param schedule the load schedule used for the sizing
     */
    static List<String> warnings(SizingInputs in, double depthM, LoadSchedule schedule) {
        List<String> warnings = new ArrayList<>();

        // The core models one borehole; deeper than this, several boreholes are usually the better solution.
        if (depthM > MAX_SINGLE_BOREHOLE_DEPTH_M) {
            warnings.add(String.format(NORWEGIAN, "Nødvendig dybde er %.0f m, over %.0f m for én brønn. Vurder flere brønner.",
                    depthM, MAX_SINGLE_BOREHOLE_DEPTH_M));
        }

        // The line source is inaccurate for pulses shorter than 5·r_b²/α. The shortest step is normally the peak.
        double shortestStepSeconds = Double.POSITIVE_INFINITY;
        for (int i = 0; i < schedule.steps().size(); i++) {
            double durationSeconds = schedule.stepEndSeconds(i) - schedule.steps().get(i).startTimeSeconds();
            shortestStepSeconds = Math.min(shortestStepSeconds, durationSeconds);
        }
        double validitySeconds = LineSource.validityTime(in.ground(), in.borehole().radius());
        if (shortestStepSeconds < validitySeconds) {
            warnings.add(String.format(NORWEGIAN, "Korteste laststeg varer %.1f t, men linjekildemodellen er bare gyldig etter "
                            + "%.1f t for dette borehullet og berget. Resultatet er usikkert for effekttoppen.",
                    shortestStepSeconds / Units.SECONDS_PER_HOUR, validitySeconds / Units.SECONDS_PER_HOUR));
        }

        return List.copyOf(warnings);
    }
}
