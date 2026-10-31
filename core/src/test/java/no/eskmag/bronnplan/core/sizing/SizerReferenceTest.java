package no.eskmag.bronnplan.core.sizing;

import no.eskmag.bronnplan.core.model.Borehole;
import no.eskmag.bronnplan.core.model.BuildingLoad;
import no.eskmag.bronnplan.core.model.DesignCriteria;
import no.eskmag.bronnplan.core.model.GroundProperties;
import no.eskmag.bronnplan.core.model.MonthlyProfile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Sizing against an independent NumPy/SciPy implementation, see tools/validation/generate_references.py. */
class SizerReferenceTest {

    private static final double MAX_RELATIVE_ERROR = 1e-8;

    @ParameterizedTest(name = "{0}")
    @CsvFileSource(resources = "/reference/sizing_scipy.csv", useHeadersInDisplayName = true)
    void matchesPythonReference(String name, double annualHeatKWh, double peakHeatKW, double scop, double copPeak,
                                double conductivity, double volumetricHeatCapacity, double undisturbedTemp,
                                double boreholeRadius, double boreholeResistance, double minFluidTemp, int years,
                                double expectedDepth, int expectedMonthOfMinimum) {
        var inputs = new SizingInputs(
                new GroundProperties(conductivity, volumetricHeatCapacity, undisturbedTemp),
                new Borehole(boreholeRadius, boreholeResistance),
                new BuildingLoad(annualHeatKWh, peakHeatKW, scop, copPeak, MonthlyProfile.bergenDegreeDays()),
                new DesignCriteria(minFluidTemp, years));

        var result = Sizer.requiredDepth(inputs);

        assertThat(Math.abs(result.requiredDepthM() - expectedDepth) / expectedDepth).isLessThan(MAX_RELATIVE_ERROR);
        assertThat(result.monthOfMinimum()).isEqualTo(expectedMonthOfMinimum);
    }
}
