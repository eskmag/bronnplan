package no.eskmag.bronnplan.core.sizing;

import no.eskmag.bronnplan.core.model.Borehole;
import no.eskmag.bronnplan.core.model.BuildingLoad;
import no.eskmag.bronnplan.core.model.DesignCriteria;
import no.eskmag.bronnplan.core.model.GroundProperties;

/**
 * Everything the sizer needs. Checks the rule that needs two of the objects: T_min &lt; T₀.
 */
public record SizingInputs(GroundProperties ground, Borehole borehole, BuildingLoad load, DesignCriteria criteria) {

    public SizingInputs {
        if (ground == null || borehole == null || load == null || criteria == null) {
            throw new IllegalArgumentException("ground, borehole, load and criteria must not be null");
        }
        if (!(criteria.minFluidTempC() < ground.undisturbedTemperature())) {
            throw new IllegalArgumentException("minFluidTempC [°C] must be below undisturbedTemperature [°C], got "
                    + criteria.minFluidTempC() + " and " + ground.undisturbedTemperature());
        }
    }
}
