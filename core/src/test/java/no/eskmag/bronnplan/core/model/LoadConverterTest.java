package no.eskmag.bronnplan.core.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

class LoadConverterTest {

    @Test
    void groundEnergyIsHeatMinusCompressorWork() {
        // 20 000 kWh with SCOP 3.5: compressor supplies 5 714 kWh, ground 14 286 kWh
        assertThat(LoadConverter.groundEnergy(20_000.0, 3.5)).isCloseTo(20_000.0 * 2.5 / 3.5, within(1e-9));
        assertThat(LoadConverter.groundEnergy(20_000.0, 3.5)).isCloseTo(14_285.714, within(1e-3));
    }

    @Test
    void groundPeakPowerIsReturnedInWatts() {
        // 8 kW with COP 3: 5.333 kW from the ground
        assertThat(LoadConverter.groundPeakPower(8.0, 3.0)).isCloseTo(8000.0 * 2.0 / 3.0, within(1e-9));
    }

    @Test
    void higherCopTakesMoreFromTheGround() {
        assertThat(LoadConverter.groundEnergy(10_000.0, 4.0)).isGreaterThan(LoadConverter.groundEnergy(10_000.0, 3.0));
    }

    @Test
    void zeroDemandGivesZeroGroundLoad() {
        assertThat(LoadConverter.groundEnergy(0.0, 3.5)).isZero();
        assertThat(LoadConverter.groundPeakPower(0.0, 3.0)).isZero();
    }

    @ParameterizedTest
    @ValueSource(doubles = {1.0, 0.5, -2.0, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsCopAtOrBelowOne(double invalid) {
        assertThatIllegalArgumentException().isThrownBy(() -> LoadConverter.groundEnergy(10_000.0, invalid))
                .withMessageContaining("scop [–]");
        assertThatIllegalArgumentException().isThrownBy(() -> LoadConverter.groundPeakPower(8.0, invalid))
                .withMessageContaining("copPeak [–]");
    }

    @Test
    void rejectsNegativeDemand() {
        assertThatIllegalArgumentException().isThrownBy(() -> LoadConverter.groundEnergy(-1.0, 3.5))
                .withMessageContaining("annualHeatKWh [kWh]");
        assertThatIllegalArgumentException().isThrownBy(() -> LoadConverter.groundPeakPower(-1.0, 3.0))
                .withMessageContaining("peakHeatKW [kW]");
    }
}
