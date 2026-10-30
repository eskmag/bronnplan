package no.eskmag.bronnplan.core.model;

import java.util.List;

import no.eskmag.bronnplan.core.units.Units;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

class LoadScheduleTest {

    private static final BuildingLoad LOAD = new BuildingLoad(20_000.0, 8.0, 3.5, 3.0, MonthlyProfile.bergenDegreeDays());

    @Test
    void startsAtZeroAndEndsAfterDesignPeriod() {
        var schedule = LoadSchedule.build(LOAD, 25);

        assertThat(schedule.steps().getFirst().startTimeSeconds()).isZero();
        assertThat(schedule.endTimeSeconds()).isEqualTo(25 * Units.SECONDS_PER_YEAR);
    }

    @Test
    void januaryStepIsMeanPowerOfJanuaryEnergy() {
        // 14 285.7 kWh · 0.139535 = 1 993.4 kWh over 730 h = 2 730.6 W
        double expected = 20_000.0 * (1 - 1 / 3.5) * 0.139534884 * 1000.0 / 730.0;

        assertThat(LoadSchedule.build(LOAD, 25).steps().getFirst().groundPowerW()).isCloseTo(expected, within(1e-6));
    }

    @Test
    void firstYearDeliversAnnualGroundEnergy() {
        var schedule = LoadSchedule.build(LOAD, 2);

        // The profile table is rounded to 9 decimals, so it sums to 1 within about 1e-9.
        assertThat(energyKWh(schedule, 0.0, Units.SECONDS_PER_YEAR))
                .isCloseTo(LOAD.annualGroundEnergyKWh(), within(1e-8 * LOAD.annualGroundEnergyKWh()));
    }

    @Test
    void hasOneStepPerMonthPlusThePeak() {
        var schedule = LoadSchedule.build(LOAD, 25);

        assertThat(schedule.steps().size()).isBetween(12 * 25 + 1, 12 * 25 + 2);
    }

    @Test
    void peakLastsSixHoursInColdestMonthOfLastYear() {
        int years = 25;
        var steps = LoadSchedule.build(LOAD, years).steps();
        double monthStart = ((years - 1) * 12 + LOAD.monthlyProfile().coldestMonth()) * Units.SECONDS_PER_MONTH;
        double monthEnd = monthStart + Units.SECONDS_PER_MONTH;

        int peak = indexOfMaxPower(steps);
        double peakStart = steps.get(peak).startTimeSeconds();
        double peakEnd = peak + 1 < steps.size() ? steps.get(peak + 1).startTimeSeconds() : years * Units.SECONDS_PER_YEAR;

        assertThat(steps.get(peak).groundPowerW()).isGreaterThanOrEqualTo(LOAD.peakGroundPowerW());
        assertThat(peakStart).isGreaterThan(monthStart);
        assertThat(peakEnd).isLessThanOrEqualTo(monthEnd);
        assertThat(peakEnd - peakStart).isCloseTo(LoadSchedule.PEAK_DURATION_SECONDS, within(1e-6));
    }

    @Test
    void peakReplacesTheMonthlyMeanInsteadOfAddingToIt() {
        int years = 25;
        var steps = LoadSchedule.build(LOAD, years).steps();
        int peak = (years - 1) * 12 + LOAD.monthlyProfile().coldestMonth() + 1;

        // January mean is 2 731 W, design power from the ground is 5 333 W: the peak is 5 333 W, not 8 064 W.
        assertThat(steps.get(peak).groundPowerW()).isCloseTo(LOAD.peakGroundPowerW(), within(1e-9));
    }

    @Test
    void peakBelowMonthlyMeanLeavesThePowerUnchanged() {
        var weakPeak = new BuildingLoad(20_000.0, 0.5, 3.5, 3.0, MonthlyProfile.bergenDegreeDays());
        int years = 2;
        var steps = LoadSchedule.build(weakPeak, years).steps();
        int peak = (years - 1) * 12 + weakPeak.monthlyProfile().coldestMonth() + 1;

        assertThat(weakPeak.peakGroundPowerW()).isLessThan(steps.get(peak - 1).groundPowerW());
        assertThat(steps.get(peak).groundPowerW()).isEqualTo(steps.get(peak - 1).groundPowerW());
    }

    @Test
    void peakIsAlsoPresentInOneYearSchedule() {
        var steps = LoadSchedule.build(LOAD, 1).steps();

        assertThat(steps.get(indexOfMaxPower(steps)).groundPowerW()).isGreaterThanOrEqualTo(LOAD.peakGroundPowerW());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsYearsBelowOne(int years) {
        assertThatIllegalArgumentException().isThrownBy(() -> LoadSchedule.build(LOAD, years))
                .withMessageContaining("years [years]");
    }

    @Test
    void rejectsUnorderedOrLateStartingSteps() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LoadSchedule(
                List.of(new LoadStep(0.0, 1.0), new LoadStep(10.0, 1.0), new LoadStep(10.0, 2.0)), 20.0))
                .withMessageContaining("strictly increasing");
        assertThatIllegalArgumentException().isThrownBy(() -> new LoadSchedule(List.of(new LoadStep(5.0, 1.0)), 20.0))
                .withMessageContaining("first step must start at 0");
        assertThatIllegalArgumentException().isThrownBy(() -> new LoadSchedule(List.of(new LoadStep(0.0, 1.0)), 0.0))
                .withMessageContaining("endTimeSeconds [s]");
    }

    /** Integral of power over [from, to), in kWh. */
    private static double energyKWh(LoadSchedule schedule, double from, double to) {
        var steps = schedule.steps();
        double joules = 0.0;
        for (int i = 0; i < steps.size(); i++) {
            double start = Math.max(from, steps.get(i).startTimeSeconds());
            double end = Math.min(to, i + 1 < steps.size() ? steps.get(i + 1).startTimeSeconds() : schedule.endTimeSeconds());
            if (end > start) {
                joules += steps.get(i).groundPowerW() * (end - start);
            }
        }
        return joules / 3.6e6;
    }

    private static int indexOfMaxPower(List<LoadStep> steps) {
        int max = 0;
        for (int i = 1; i < steps.size(); i++) {
            if (steps.get(i).groundPowerW() > steps.get(max).groundPowerW()) {
                max = i;
            }
        }
        return max;
    }
}
