package no.eskmag.bronnplan.core.sizing;

import java.util.List;

import no.eskmag.bronnplan.core.model.Borehole;
import no.eskmag.bronnplan.core.model.BuildingLoad;
import no.eskmag.bronnplan.core.model.DesignCriteria;
import no.eskmag.bronnplan.core.model.GroundProperties;
import no.eskmag.bronnplan.core.model.LoadSchedule;
import no.eskmag.bronnplan.core.model.MonthlyProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

class SizerTest {

    private static final GroundProperties GRANITE = new GroundProperties(3.0, 2.2e6, 7.5);
    private static final Borehole BOREHOLE = new Borehole(0.0575, 0.10);
    private static final BuildingLoad HOUSE = new BuildingLoad(20_000.0, 8.0, 3.5, 3.0, MonthlyProfile.bergenDegreeDays());
    private static final SizingInputs INPUTS = new SizingInputs(GRANITE, BOREHOLE, HOUSE, DesignCriteria.defaults());

    @Test
    void requiredDepthBringsMinimumFluidTemperatureExactlyToTheLimit() {
        var result = Sizer.requiredDepth(INPUTS);

        double min = minOf(result.series());
        assertThat(min).isCloseTo(-3.0, within(1e-9));
        assertThat(result.minFluidTempC()).isCloseTo(-3.0, within(1e-9));
    }

    @Test
    void closedFormAgreesWithBisection() {
        var result = Sizer.requiredDepth(INPUTS);
        var schedule = LoadSchedule.build(HOUSE, 25);

        double low = 1.0;
        double high = 10_000.0;
        while (high - low > 1e-9) {
            double mid = 0.5 * (low + high);
            if (minOf(FluidTemperature.series(mid, schedule, GRANITE, BOREHOLE)) < -3.0) {
                low = mid;
            } else {
                high = mid;
            }
        }
        assertThat(result.requiredDepthM()).isCloseTo(high, within(1e-6));
    }

    @Test
    void noPointBetweenStepEndsIsColderThanTheLimit() {
        var result = Sizer.requiredDepth(INPUTS);
        var schedule = LoadSchedule.build(HOUSE, 25);
        int samplesPerStep = 20;

        double coldest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < schedule.steps().size(); i++) {
            double start = schedule.steps().get(i).startTimeSeconds();
            double end = schedule.stepEndSeconds(i);
            for (int k = 1; k <= samplesPerStep; k++) {
                double t = start + (end - start) * k / samplesPerStep;
                coldest = Math.min(coldest, FluidTemperature.at(result.requiredDepthM(), t, schedule, GRANITE, BOREHOLE));
            }
        }
        assertThat(coldest).isGreaterThanOrEqualTo(-3.0 - 1e-9);
    }

    @Test
    void minimumIsAtThePeakInJanuaryOfTheLastYear() {
        var result = Sizer.requiredDepth(INPUTS);

        assertThat(result.monthOfMinimum()).isEqualTo(24 * 12);
        assertThat(result.series()).hasSize(25 * 12 + 1);
        assertThat(result.modelVersion()).isEqualTo(Sizer.MODEL_VERSION);
    }

    @Test
    void deeperBoreholeGivesWarmerFluidAndShallowerGivesColder() {
        var result = Sizer.requiredDepth(INPUTS);
        var schedule = LoadSchedule.build(HOUSE, 25);

        assertThat(minOf(FluidTemperature.series(1.1 * result.requiredDepthM(), schedule, GRANITE, BOREHOLE)))
                .isGreaterThan(-3.0);
        assertThat(minOf(FluidTemperature.series(0.9 * result.requiredDepthM(), schedule, GRANITE, BOREHOLE)))
                .isLessThan(-3.0);
    }

    @Test
    void higherConductivityNeedsShallowerBorehole() {
        var poor = new SizingInputs(new GroundProperties(2.5, 2.2e6, 7.5), BOREHOLE, HOUSE, DesignCriteria.defaults());
        var good = new SizingInputs(new GroundProperties(4.0, 2.2e6, 7.5), BOREHOLE, HOUSE, DesignCriteria.defaults());

        assertThat(Sizer.requiredDepth(good).requiredDepthM()).isLessThan(Sizer.requiredDepth(poor).requiredDepthM());
    }

    @Test
    void longerDesignPeriodNeedsDeeperBorehole() {
        var tenYears = new SizingInputs(GRANITE, BOREHOLE, HOUSE, new DesignCriteria(-3.0, 10));

        assertThat(Sizer.requiredDepth(INPUTS).requiredDepthM())
                .isGreaterThan(Sizer.requiredDepth(tenYears).requiredDepthM());
    }

    @Test
    void largerPeakNeedsDeeperBorehole() {
        var smallPeak = new BuildingLoad(20_000.0, 1.0, 3.5, 3.0, MonthlyProfile.bergenDegreeDays());
        var withSmallPeak = new SizingInputs(GRANITE, BOREHOLE, smallPeak, DesignCriteria.defaults());

        assertThat(Sizer.requiredDepth(INPUTS).requiredDepthM())
                .isGreaterThan(Sizer.requiredDepth(withSmallPeak).requiredDepthM());
    }

    @Test
    void ordinaryHouseGivesNoWarnings() {
        assertThat(Sizer.requiredDepth(INPUTS).warnings()).isEmpty();
    }

    @Test
    void warnsWhenOneBoreholeWouldBeTooDeep() {
        var bigBuilding = new BuildingLoad(120_000.0, 40.0, 3.5, 3.0, MonthlyProfile.bergenDegreeDays());
        var result = Sizer.requiredDepth(new SizingInputs(GRANITE, BOREHOLE, bigBuilding, DesignCriteria.defaults()));

        assertThat(result.requiredDepthM()).isGreaterThan(Sizer.MAX_SINGLE_BOREHOLE_DEPTH_M);
        assertThat(result.warnings()).hasSize(1);
    }

    @Test
    void warnsWhenThePeakIsTooShortForTheLineSource() {
        // r_b = 0.15 m in poorly conducting ground: valid only after 5·0.15²·2.2e6/1.0 s ≈ 69 h > 6 h peak
        var wideHole = new Borehole(0.15, 0.10);
        var poorGround = new GroundProperties(1.0, 2.2e6, 7.5);
        var result = Sizer.requiredDepth(new SizingInputs(poorGround, wideHole, HOUSE, DesignCriteria.defaults()));

        assertThat(result.requiredDepthM()).isLessThan(Sizer.MAX_SINGLE_BOREHOLE_DEPTH_M);
        assertThat(result.warnings()).hasSize(1);
    }

    @Test
    void rejectsLimitAtOrAboveUndisturbedTemperature() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SizingInputs(GRANITE, BOREHOLE, HOUSE, new DesignCriteria(7.5, 25)))
                .withMessageContaining("minFluidTempC [°C]");
    }

    @Test
    void monthOfMapsStepEndsToTheirMonth() {
        double month = 730 * 3600.0;

        assertThat(Sizer.monthOf(month)).isZero();
        assertThat(Sizer.monthOf(month - 6 * 3600.0)).isZero();
        assertThat(Sizer.monthOf(289 * month)).isEqualTo(288);
    }

    private static double minOf(List<TemperaturePoint> series) {
        return series.stream().mapToDouble(TemperaturePoint::fluidTempC).min().orElseThrow();
    }
}
