package no.eskmag.bronnplan.core.model;

/**
 * Thermal properties of homogeneous bedrock.
 *
 * @param conductivity           λ, thermal conductivity [W/(m·K)]
 * @param volumetricHeatCapacity ρc, volumetric heat capacity [J/(m³·K)]
 * @param undisturbedTemperature T₀, undisturbed ground temperature [°C]
 */
public record GroundProperties(double conductivity, double volumetricHeatCapacity, double undisturbedTemperature) {

    public GroundProperties {
        if (!(conductivity > 0.0) || Double.isInfinite(conductivity)) {
            throw new IllegalArgumentException("conductivity must be positive and finite, got " + conductivity);
        }
        if (!(volumetricHeatCapacity > 0.0) || Double.isInfinite(volumetricHeatCapacity)) {
            throw new IllegalArgumentException(
                    "volumetricHeatCapacity must be positive and finite, got " + volumetricHeatCapacity);
        }
        if (!Double.isFinite(undisturbedTemperature)) {
            throw new IllegalArgumentException("undisturbedTemperature must be finite, got " + undisturbedTemperature);
        }
    }

    /** α = λ / (ρc), thermal diffusivity [m²/s]. */
    public double diffusivity() {
        return conductivity / volumetricHeatCapacity;
    }
}
