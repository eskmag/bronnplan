package no.eskmag.bronnplan.core.sizing;

/**
 * Mean fluid temperature at one point in time.
 *
 * @param timeSeconds time from the start of operation [s]
 * @param fluidTempC  mean fluid temperature T_f [°C]
 */
public record TemperaturePoint(double timeSeconds, double fluidTempC) {
}
