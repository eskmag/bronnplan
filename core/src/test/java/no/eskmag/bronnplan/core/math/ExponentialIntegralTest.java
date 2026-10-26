package no.eskmag.bronnplan.core.math;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

class ExponentialIntegralTest {

    private static final double MAX_RELATIVE_ERROR = 1e-8;

    /** Reference values from scipy.special.exp1, see tools/validation/generate_references.py. */
    @ParameterizedTest(name = "E1({0})")
    @CsvFileSource(resources = "/reference/e1_scipy.csv", useHeadersInDisplayName = true)
    void matchesScipyReference(double x, double expected) {
        double actual = ExponentialIntegral.e1(x);

        assertThat(relativeError(actual, expected)).isLessThan(MAX_RELATIVE_ERROR);
    }

    @Test
    void knownValueAtOne() {
        // E1(1) = 0.219383934395520... (Abramowitz & Stegun, table 5.1)
        assertThat(ExponentialIntegral.e1(1.0)).isCloseTo(0.21938393439552029, within(1e-15));
    }

    @Test
    void approachesLogarithmicAsymptoteForSmallX() {
        // E1(x) ≈ -γ - ln x for x → 0; the next term is +x, so the gap is ~1e-10 here.
        double x = 1e-10;
        double asymptote = -ExponentialIntegral.EULER_GAMMA - Math.log(x);

        assertThat(ExponentialIntegral.e1(x)).isCloseTo(asymptote, within(1e-9));
    }

    @Test
    void isStrictlyDecreasing() {
        double previous = Double.POSITIVE_INFINITY;
        for (double x = 1e-6; x < 60; x *= 1.05) {
            double value = ExponentialIntegral.e1(x);
            assertThat(value).as("E1(%s)", x).isLessThan(previous).isPositive();
            previous = value;
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN})
    void rejectsNonPositiveArgument(double x) {
        assertThatIllegalArgumentException().isThrownBy(() -> ExponentialIntegral.e1(x));
    }

    private static double relativeError(double actual, double expected) {
        return Math.abs(actual - expected) / Math.abs(expected);
    }
}
