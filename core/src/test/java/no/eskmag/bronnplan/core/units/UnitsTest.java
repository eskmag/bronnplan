package no.eskmag.bronnplan.core.units;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class UnitsTest {

    @Test
    void monthIsOneTwelfthOfA365DayYear() {
        assertThat(Units.HOURS_PER_MONTH).isEqualTo(730.0);
        assertThat(Units.SECONDS_PER_MONTH).isEqualTo(730.0 * 3600.0);
        assertThat(12 * Units.SECONDS_PER_MONTH).isEqualTo(Units.SECONDS_PER_YEAR);
    }

    @Test
    void oneKWhIs3_6MegaJoules() {
        assertThat(Units.kWhToJoules(1.0)).isEqualTo(3.6e6);
        assertThat(Units.kWToWatts(1.5)).isEqualTo(1500.0);
    }

    @Test
    void monthlyEnergySpreadOver730HoursGivesMeanPower() {
        // 730 kWh over 730 h = 1 kW
        assertThat(Units.monthlyEnergyToMeanPowerW(730.0)).isCloseTo(1000.0, within(1e-9));
    }
}
