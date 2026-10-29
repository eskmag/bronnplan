package no.eskmag.bronnplan.core.model;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

class MonthlyProfileTest {

    private static double[] flat() {
        double[] f = new double[12];
        Arrays.fill(f, 1.0 / 12.0);
        return f;
    }

    @Test
    void bergenProfileSumsToOne() {
        double sum = Arrays.stream(MonthlyProfile.bergenDegreeDays().fractions()).sum();

        assertThat(sum).isCloseTo(1.0, within(1e-8));
    }

    @Test
    void bergenProfileIsWinterHeavy() {
        var profile = MonthlyProfile.bergenDegreeDays();

        assertThat(profile.coldestMonth()).isZero(); // January
        assertThat(profile.fraction(0)).isGreaterThan(profile.fraction(6)); // January > July
        assertThat(profile.fraction(6)).isPositive(); // degree days with base 17 °C also in July
    }

    @Test
    void coldestMonthIsLargestShareAndFirstOnTies() {
        double[] f = flat();
        f[1] += 0.01;
        f[7] -= 0.01;
        assertThat(new MonthlyProfile(f).coldestMonth()).isEqualTo(1);

        assertThat(new MonthlyProfile(flat()).coldestMonth()).isZero();
    }

    @Test
    void cannotBeChangedAfterConstruction() {
        double[] input = flat();
        var profile = new MonthlyProfile(input);

        input[0] = 99.0;
        profile.fractions()[1] = 99.0;

        assertThat(profile.fraction(0)).isEqualTo(1.0 / 12.0);
        assertThat(profile.fraction(1)).isEqualTo(1.0 / 12.0);
    }

    @Test
    void equalContentsMeansEqualProfiles() {
        assertThat(new MonthlyProfile(flat())).isEqualTo(new MonthlyProfile(flat()));
        assertThat(new MonthlyProfile(flat())).hasSameHashCodeAs(new MonthlyProfile(flat()));
        assertThat(new MonthlyProfile(flat())).isNotEqualTo(MonthlyProfile.bergenDegreeDays());
    }

    @Test
    void acceptsSumWithinTolerance() {
        double[] f = flat();
        f[0] += 0.9e-6;

        assertThat(new MonthlyProfile(f).fraction(0)).isEqualTo(f[0]);
    }

    @Test
    void rejectsSumOutsideTolerance() {
        double[] f = flat();
        f[0] += 2e-6;

        assertThatIllegalArgumentException().isThrownBy(() -> new MonthlyProfile(f))
                .withMessageContaining("must sum to 1");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 11, 13})
    void rejectsWrongLength(int length) {
        assertThatIllegalArgumentException().isThrownBy(() -> new MonthlyProfile(new double[length]))
                .withMessageContaining("12 values");
    }

    @Test
    void rejectsNull() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MonthlyProfile(null));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, Double.NaN})
    void rejectsNegativeOrNaNFraction(double invalid) {
        double[] f = flat();
        f[3] = invalid;

        assertThatIllegalArgumentException().isThrownBy(() -> new MonthlyProfile(f))
                .withMessageContaining("fractions[3] [–]");
    }
}
