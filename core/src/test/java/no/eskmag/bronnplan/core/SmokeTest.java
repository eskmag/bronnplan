package no.eskmag.bronnplan.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SmokeTest {
    @Test
    void buildWorks() {
        assertThat(1 + 1).isEqualTo(2);
    }
}