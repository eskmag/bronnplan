package no.eskmag.bronnplan.core.model;

import no.eskmag.bronnplan.core.math.ExponentialIntegral;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

class LineSourceTest {

    private static final double SECONDS_PER_YEAR = 365.0 * 86_400.0;
    private static final double MAX_RELATIVE_ERROR = 1e-10;

    private static final GroundProperties GRANITE = new GroundProperties(3.0, 2.2e6, 7.5);
    private static final Borehole BOREHOLE = new Borehole(0.0575, 0.10);

    /** Reference values computed with scipy, see tools/validation/generate_references.py. */
    @ParameterizedTest(name = "λ={0}, q={2}, r={3}, t={4}")
    @CsvFileSource(resources = "/reference/line_source_scipy.csv", useHeadersInDisplayName = true)
    void matchesScipyReference(double conductivity, double volumetricHeatCapacity, double q, double r,
                               double t, double expectedDeltaT, double undisturbedTemp,
                               double boreholeResistance, double expectedFluidTemp) {
        var ground = new GroundProperties(conductivity, volumetricHeatCapacity, undisturbedTemp);
        var borehole = new Borehole(r, boreholeResistance);

        assertThat(relativeError(LineSource.temperatureChange(ground, q, r, t), expectedDeltaT))
                .isLessThan(MAX_RELATIVE_ERROR);
        assertThat(relativeError(LineSource.fluidTemperature(ground, borehole, q, t), expectedFluidTemp))
                .isLessThan(MAX_RELATIVE_ERROR);
    }

    @Test
    void temperatureChangeIsLinearInHeatRate() {
        double t = SECONDS_PER_YEAR;
        double single = LineSource.temperatureChange(GRANITE, 20.0, BOREHOLE.radius(), t);
        double triple = LineSource.temperatureChange(GRANITE, 60.0, BOREHOLE.radius(), t);

        assertThat(triple).isCloseTo(3.0 * single, within(1e-12));
    }

    @Test
    void temperatureChangeGrowsWithTime() {
        double previous = 0.0;
        for (double t = 3600.0; t <= 25 * SECONDS_PER_YEAR; t *= 2) {
            double deltaT = LineSource.temperatureChange(GRANITE, 30.0, BOREHOLE.radius(), t);
            assertThat(deltaT).as("t = %s s", t).isGreaterThan(previous);
            previous = deltaT;
        }
    }

    @Test
    void temperatureChangeFallsWithDistance() {
        double t = SECONDS_PER_YEAR;
        double atWall = LineSource.temperatureChange(GRANITE, 30.0, 0.0575, t);
        double atOneMetre = LineSource.temperatureChange(GRANITE, 30.0, 1.0, t);
        double atTenMetres = LineSource.temperatureChange(GRANITE, 30.0, 10.0, t);

        assertThat(atWall).isGreaterThan(atOneMetre);
        assertThat(atOneMetre).isGreaterThan(atTenMetres);
    }

    @Test
    void higherConductivityGivesSmallerLongTermTemperatureChange() {
        double t = 25 * SECONDS_PER_YEAR;
        var poor = new GroundProperties(2.5, 2.2e6, 7.5);
        var good = new GroundProperties(4.0, 2.2e6, 7.5);

        assertThat(LineSource.temperatureChange(good, 30.0, BOREHOLE.radius(), t))
                .isLessThan(LineSource.temperatureChange(poor, 30.0, BOREHOLE.radius(), t));
    }

    @Test
    void matchesLogarithmicApproximationAtLongTimes() {
        // For large t: ΔT ≈ q/(4πλ) · (ln(4αt/r²) − γ). The neglected term is of order r²/(4αt) ≈ 1e-6.
        double q = 30.0;
        double r = BOREHOLE.radius();
        double t = 25 * SECONDS_PER_YEAR;
        double approx = q / (4 * Math.PI * GRANITE.conductivity())
                * (Math.log(4 * GRANITE.diffusivity() * t / (r * r)) - ExponentialIntegral.EULER_GAMMA);

        assertThat(LineSource.temperatureChange(GRANITE, q, r, t)).isCloseTo(approx, within(1e-5));
    }

    @Test
    void fluidTemperatureSubtractsBoreholeResistanceDropAndGroundResponse() {
        double q = 30.0;
        double t = 10 * SECONDS_PER_YEAR;
        double groundResponse = LineSource.temperatureChange(GRANITE, q, BOREHOLE.radius(), t);
        double expected = GRANITE.undisturbedTemperature() - q * BOREHOLE.thermalResistance() - groundResponse;

        assertThat(LineSource.fluidTemperature(GRANITE, BOREHOLE, q, t)).isCloseTo(expected, within(1e-12));
    }

    @Test
    void zeroHeatRateLeavesGroundUndisturbed() {
        assertThat(LineSource.fluidTemperature(GRANITE, BOREHOLE, 0.0, SECONDS_PER_YEAR))
                .isEqualTo(GRANITE.undisturbedTemperature());
    }

    @Test
    void validityLimitIsAboutThreeAndAHalfHoursForTypicalBorehole() {
        // 5 · 0.0575² / (3.0 / 2.2e6) = 12 123 s ≈ 3.37 h, see docs/model.md §2
        double limit = LineSource.validityTime(GRANITE, BOREHOLE.radius());

        assertThat(limit).isCloseTo(5 * 0.0575 * 0.0575 * 2.2e6 / 3.0, within(1e-9));
        assertThat(limit / 3600.0).isBetween(3.3, 3.4);
    }

    @Test
    void isValidOnlyAfterValidityLimit() {
        double limit = LineSource.validityTime(GRANITE, BOREHOLE.radius());

        assertThat(LineSource.isValid(GRANITE, BOREHOLE.radius(), 0.99 * limit)).isFalse();
        assertThat(LineSource.isValid(GRANITE, BOREHOLE.radius(), limit)).isFalse();
        assertThat(LineSource.isValid(GRANITE, BOREHOLE.radius(), 1.01 * limit)).isTrue();
        assertThat(LineSource.isValid(GRANITE, BOREHOLE.radius(), 730 * 3600.0)).isTrue();
    }

    @Test
    void validationMessagesNameParameterAndUnit() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GroundProperties(-1.0, 2.2e6, 7.5))
                .withMessageContaining("conductivity [W/(m·K)]");
        assertThatIllegalArgumentException().isThrownBy(() -> new Borehole(0.0575, -0.1))
                .withMessageContaining("thermalResistance [m·K/W]");
    }

    @Test
    void diffusivityIsConductivityOverVolumetricHeatCapacity() {
        assertThat(GRANITE.diffusivity()).isCloseTo(3.0 / 2.2e6, within(1e-20));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN})
    void rejectsNonPositiveTimeAndRadius(double invalid) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LineSource.responseFactor(GRANITE, BOREHOLE.radius(), invalid));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LineSource.responseFactor(GRANITE, invalid, SECONDS_PER_YEAR));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN})
    void rejectsInvalidMaterialProperties(double invalid) {
        assertThatIllegalArgumentException().isThrownBy(() -> new GroundProperties(invalid, 2.2e6, 7.5));
        assertThatIllegalArgumentException().isThrownBy(() -> new GroundProperties(3.0, invalid, 7.5));
        assertThatIllegalArgumentException().isThrownBy(() -> new Borehole(invalid, 0.1));
    }

    @Test
    void rejectsNegativeBoreholeResistanceAndNonFiniteTemperature() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Borehole(0.0575, -0.01));
        assertThatIllegalArgumentException().isThrownBy(() -> new GroundProperties(3.0, 2.2e6, Double.NaN));
    }

    private static double relativeError(double actual, double expected) {
        return Math.abs(actual - expected) / Math.abs(expected);
    }
}
