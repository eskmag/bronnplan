package no.eskmag.bronnplan.core.math;

/**
 * Exponential integral E1(x) = ∫_x^∞ e^(−u)/u du for x &gt; 0.
 *
 * <p>Power series for x ≤ 1, continued fraction (modified Lentz) for x &gt; 1, following
 * Press et al., <i>Numerical Recipes</i>, 3rd ed., §6.3. See docs/model.md.
 */
public final class ExponentialIntegral {

    /** Euler–Mascheroni constant γ. */
    public static final double EULER_GAMMA = 0.5772156649015329;

    private static final double SERIES_LIMIT = 1.0;
    private static final double EPS = 1e-16;
    private static final double TINY = 1e-300;
    private static final int MAX_ITERATIONS = 200;

    private ExponentialIntegral() {
    }

    /**
     * @param x argument, must be &gt; 0 [–]
     * @return E1(x) [–]
     * @throws IllegalArgumentException if x is not strictly positive (or NaN)
     */
    public static double e1(double x) {
        if (!(x > 0.0)) {
            throw new IllegalArgumentException("E1 is only defined for x > 0, got " + x);
        }
        return x <= SERIES_LIMIT ? series(x) : continuedFraction(x);
    }

    /** E1(x) = −γ − ln x − Σ_{k≥1} (−x)^k / (k·k!). */
    private static double series(double x) {
        double sum = 0.0;
        double term = 1.0; // (−x)^k / k!
        for (int k = 1; k <= MAX_ITERATIONS; k++) {
            term *= -x / k;
            double contribution = term / k;
            sum += contribution;
            if (Math.abs(contribution) < Math.abs(sum) * EPS) {
                return -EULER_GAMMA - Math.log(x) - sum;
            }
        }
        throw new ArithmeticException("E1 series did not converge for x = " + x);
    }

    /** E1(x) = e^(−x) · 1/(x+1 − 1²/(x+3 − 2²/(x+5 − …))), evaluated with modified Lentz. */
    private static double continuedFraction(double x) {
        double b = x + 1.0;
        double c = 1.0 / TINY;
        double d = 1.0 / b;
        double h = d;
        for (int i = 1; i <= MAX_ITERATIONS; i++) {
            double a = -(double) i * i;
            b += 2.0;
            d = 1.0 / (a * d + b);
            c = b + a / c;
            double delta = c * d;
            h *= delta;
            if (Math.abs(delta - 1.0) < EPS) {
                return h * Math.exp(-x);
            }
        }
        throw new ArithmeticException("E1 continued fraction did not converge for x = " + x);
    }
}
