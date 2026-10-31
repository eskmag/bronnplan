package no.eskmag.bronnplan.core.sizing;

import java.util.List;

/**
 * Result of sizing one borehole.
 *
 * @param requiredDepthM smallest depth H that keeps T_f ≥ T_min over the design period [m]
 * @param minFluidTempC  lowest mean fluid temperature at that depth [°C]; equals T_min by construction
 * @param monthOfMinimum month in which the minimum occurs, 0 = first month of operation [–]
 * @param series         T_f at the end of every load step, at the required depth
 * @param warnings       messages for the user (Norwegian), empty if none
 * @param modelVersion   identifies the model, so a result can be reproduced
 */
public record SizingResult(double requiredDepthM, double minFluidTempC, int monthOfMinimum,
                           List<TemperaturePoint> series, List<String> warnings, String modelVersion) {

    public SizingResult {
        series = List.copyOf(series);
        warnings = List.copyOf(warnings);
    }
}
