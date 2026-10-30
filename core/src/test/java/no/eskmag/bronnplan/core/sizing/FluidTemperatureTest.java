package no.eskmag.bronnplan.core.sizing;

import java.util.List;

import no.eskmag.bronnplan.core.model.Borehole;
import no.eskmag.bronnplan.core.model.BuildingLoad;
import no.eskmag.bronnplan.core.model.GroundProperties;
import no.eskmag.bronnplan.core.model.LineSource;
import no.eskmag.bronnplan.core.model.LoadSchedule;
import no.eskmag.bronnplan.core.model.LoadStep;
import no.eskmag.bronnplan.core.model.MonthlyProfile;
import no.eskmag.bronnplan.core.units.Units;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

class FluidTemperatureTest {

    private static final GroundProperties GRANITE = new GroundProperties(3.0, 2.2e6, 7.5);
    private static final Borehole BOREHOLE = new Borehole(0.0575, 0.10);
    private static final double END = 25 * Units.SECONDS_PER_YEAR;
    private static final LoadSchedule HOUSE = LoadSchedule.build(
            new BuildingLoad(20_000.0, 8.0, 3.5, 3.0, MonthlyProfile.bergenDegreeDays()), 25);

    @Test
    void constantLoadMatchesSingleLineSource() {
        double power = 6000.0;
        double depth = 150.0;
        var constant = new LoadSchedule(List.of(new LoadStep(0.0, power)), END);

        assertThat(FluidTemperature.at(depth, END, constant, GRANITE, BOREHOLE))
                .isCloseTo(LineSource.fluidTemperature(GRANITE, BOREHOLE, power / depth, END), within(1e-12));
    }

    @Test
    void zeroLoadLeavesFluidAtUndisturbedTemperature() {
        var none = new LoadSchedule(List.of(new LoadStep(0.0, 0.0)), END);

        assertThat(FluidTemperature.at(100.0, END, none, GRANITE, BOREHOLE)).isEqualTo(7.5);
    }

    @Test
    void temperatureDropIsInverselyProportionalToDepth() {
        double t = HOUSE.stepEndSeconds(100);
        double dropAt100 = 7.5 - FluidTemperature.at(100.0, t, HOUSE, GRANITE, BOREHOLE);
        double dropAt200 = 7.5 - FluidTemperature.at(200.0, t, HOUSE, GRANITE, BOREHOLE);

        assertThat(dropAt200).isCloseTo(dropAt100 / 2.0, within(1e-12));
    }

    @Test
    void seriesHasOnePointAtTheEndOfEveryStep() {
        var series = FluidTemperature.series(150.0, HOUSE, GRANITE, BOREHOLE);

        assertThat(series).hasSize(HOUSE.steps().size());
        assertThat(series.getFirst().timeSeconds()).isEqualTo(Units.SECONDS_PER_MONTH);
        assertThat(series.getLast().timeSeconds()).isEqualTo(END);
        for (int i = 1; i < series.size(); i++) {
            assertThat(series.get(i).timeSeconds()).isGreaterThan(series.get(i - 1).timeSeconds());
        }
    }

    @Test
    void peakGivesTheColdestPointOfTheSeries() {
        var series = FluidTemperature.series(150.0, HOUSE, GRANITE, BOREHOLE);
        int peakEnd = 24 * 12 + 1; // the peak step comes right after January of the last year

        double coldest = series.stream().mapToDouble(TemperaturePoint::fluidTempC).min().orElseThrow();
        assertThat(series.get(peakEnd).fluidTempC()).isEqualTo(coldest);
    }

    @Test
    void rejectsNonPositiveDepth() {
        assertThatIllegalArgumentException().isThrownBy(() -> FluidTemperature.at(0.0, END, HOUSE, GRANITE, BOREHOLE))
                .withMessageContaining("depthM [m]");
    }
}
