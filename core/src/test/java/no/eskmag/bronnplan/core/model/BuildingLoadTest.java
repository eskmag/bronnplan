package no.eskmag.bronnplan.core.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

class BuildingLoadTest {

    private static final MonthlyProfile PROFILE = MonthlyProfile.bergenDegreeDays();

    @Test
    void derivesGroundLoadFromDemandAndCop() {
        var load = new BuildingLoad(20_000.0, 8.0, 3.5, 3.0, PROFILE);

        assertThat(load.annualGroundEnergyKWh()).isCloseTo(LoadConverter.groundEnergy(20_000.0, 3.5), within(1e-12));
        assertThat(load.peakGroundPowerW()).isCloseTo(LoadConverter.groundPeakPower(8.0, 3.0), within(1e-12));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsNonPositiveDemandAndPower(double invalid) {
        assertThatIllegalArgumentException().isThrownBy(() -> new BuildingLoad(invalid, 8.0, 3.5, 3.0, PROFILE))
                .withMessageContaining("annualHeatDemandKWh [kWh]");
        assertThatIllegalArgumentException().isThrownBy(() -> new BuildingLoad(20_000.0, invalid, 3.5, 3.0, PROFILE))
                .withMessageContaining("peakHeatPowerKW [kW]");
    }

    @ParameterizedTest
    @ValueSource(doubles = {1.0, 0.9, Double.NaN})
    void rejectsCopAtOrBelowOne(double invalid) {
        assertThatIllegalArgumentException().isThrownBy(() -> new BuildingLoad(20_000.0, 8.0, invalid, 3.0, PROFILE))
                .withMessageContaining("scop [–]");
        assertThatIllegalArgumentException().isThrownBy(() -> new BuildingLoad(20_000.0, 8.0, 3.5, invalid, PROFILE))
                .withMessageContaining("copPeak [–]");
    }

    @Test
    void rejectsMissingProfile() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BuildingLoad(20_000.0, 8.0, 3.5, 3.0, null))
                .withMessageContaining("monthlyProfile");
    }

    @Test
    void designCriteriaDefaultsAreMinusThreeDegreesOver25Years() {
        assertThat(DesignCriteria.defaults()).isEqualTo(new DesignCriteria(-3.0, 25));
    }

    @Test
    void designCriteriaRejectsInvalidValues() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DesignCriteria(-3.0, 0))
                .withMessageContaining("years [years]");
        assertThatIllegalArgumentException().isThrownBy(() -> new DesignCriteria(Double.NaN, 25))
                .withMessageContaining("minFluidTempC [°C]");
    }
}
