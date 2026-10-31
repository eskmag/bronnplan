package no.eskmag.bronnplan.core.sizing;

import java.util.List;

import no.eskmag.bronnplan.core.model.GroundProperties;
import no.eskmag.bronnplan.core.model.LineSource;
import no.eskmag.bronnplan.core.model.LoadSchedule;
import no.eskmag.bronnplan.core.model.LoadStep;
import no.eskmag.bronnplan.core.units.Units;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

class SuperpositionTest {

    private static final GroundProperties GRANITE = new GroundProperties(3.0, 2.2e6, 7.5);
    private static final double R = 0.0575;
    private static final double MONTH = Units.SECONDS_PER_MONTH;
    private static final double END = 12 * MONTH;

    private static LoadSchedule twoSteps(double first, double second) {
        return new LoadSchedule(List.of(new LoadStep(0.0, first), new LoadStep(MONTH, second)), END);
    }

    @Test
    void singleStepIsTheLineSourceResponse() {
        var constant = new LoadSchedule(List.of(new LoadStep(0.0, 5000.0)), END);
        double t = 6 * MONTH;

        assertThat(Superposition.responseAt(t, constant, GRANITE, R))
                .isCloseTo(5000.0 * LineSource.responseFactor(GRANITE, R, t), within(1e-9));
    }

    @Test
    void twoStepsSumIncrementsByHand() {
        double t = 3 * MONTH;
        double expected = 4000.0 * LineSource.responseFactor(GRANITE, R, t)
                + (1000.0 - 4000.0) * LineSource.responseFactor(GRANITE, R, t - MONTH);

        assertThat(Superposition.responseAt(t, twoSteps(4000.0, 1000.0), GRANITE, R)).isCloseTo(expected, within(1e-9));
    }

    @Test
    void stepIsInForceUpToAndIncludingItsEnd() {
        var schedule = twoSteps(4000.0, 1000.0);

        assertThat(Superposition.powerAt(MONTH, schedule)).isEqualTo(4000.0);
        assertThat(Superposition.powerAt(MONTH + 1.0, schedule)).isEqualTo(1000.0);
        // At t = t_1 the second step has not started, so only the first contributes.
        assertThat(Superposition.responseAt(MONTH, schedule, GRANITE, R))
                .isCloseTo(4000.0 * LineSource.responseFactor(GRANITE, R, MONTH), within(1e-9));
    }

    @Test
    void doublingTheLoadDoublesTheResponse() {
        double t = 7 * MONTH;
        double single = Superposition.responseAt(t, twoSteps(3000.0, 1500.0), GRANITE, R);
        double doubled = Superposition.responseAt(t, twoSteps(6000.0, 3000.0), GRANITE, R);

        assertThat(doubled).isCloseTo(2.0 * single, within(1e-9));
    }

    @Test
    void zeroLoadGivesZeroResponse() {
        assertThat(Superposition.responseAt(5 * MONTH, twoSteps(0.0, 0.0), GRANITE, R)).isZero();
    }

    @Test
    void groundRecoversAfterLoadIsSwitchedOff() {
        var offAfterOneMonth = twoSteps(4000.0, 0.0);
        double justAfter = Superposition.responseAt(MONTH + Units.SECONDS_PER_HOUR, offAfterOneMonth, GRANITE, R);
        double muchLater = Superposition.responseAt(END, offAfterOneMonth, GRANITE, R);

        assertThat(muchLater).isPositive().isLessThan(justAfter);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, 13 * 730 * 3600.0})
    void rejectsTimeOutsideTheSchedule(double t) {
        assertThatIllegalArgumentException().isThrownBy(() -> Superposition.powerAt(t, twoSteps(1.0, 1.0)))
                .withMessageContaining("t [s]");
    }
}
