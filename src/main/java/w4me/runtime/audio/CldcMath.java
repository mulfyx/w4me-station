package w4me.runtime.audio;

/**
 * CLDC 1.1 replacements for Math.pow and Math.log, which do not exist on CLDC. Both are deterministic: they use only
 * Math.sqrt, Double bit access, and double arithmetic.
 */
final class CldcMath {
    /** ROOT_CHAIN[k] is 2 raised to the power 2^-(k+1). */
    private static final double[] ROOT_CHAIN = buildRootChain();

    private CldcMath() {}

    private static double[] buildRootChain() {
        double[] chain = new double[60];
        double root = 2.0;
        int index;
        for (index = 0; index < chain.length; index++) {
            root = Math.sqrt(root);
            chain[index] = root;
        }
        return chain;
    }

    /** Returns 2 raised to the given power for exponents inside [-1022, 1023]. */
    static double powerOfTwo(double exponent) {
        int integerPart = (int) Math.floor(exponent);
        double fraction = exponent - integerPart;
        double product = 1.0;
        int index;
        for (index = 0; index < ROOT_CHAIN.length; index++) {
            fraction += fraction;
            if (fraction >= 1.0) {
                fraction -= 1.0;
                product *= ROOT_CHAIN[index];
            }
        }
        return product * exactPowerOfTwo(integerPart);
    }

    /**
     * Returns the base-2 logarithm of a positive value. Non-positive values return a large negative number so integer
     * conversions clamp low, which matches how the former Math.log expressions collapsed under clamping.
     */
    static double logBase2(double value) {
        if (value <= 0.0) {
            return -1.0e9;
        }
        long bits = Double.doubleToLongBits(value);
        int exponent = (int) ((bits >>> 52) & 0x7ff) - 1023;
        double mantissa = Double.longBitsToDouble((bits & 0x000fffffffffffffL) | 0x3ff0000000000000L);
        double result = exponent;
        double bit = 0.5;
        int index;
        for (index = 0; index < 48; index++) {
            mantissa *= mantissa;
            if (mantissa >= 2.0) {
                mantissa *= 0.5;
                result += bit;
            }
            bit *= 0.5;
        }
        return result;
    }

    private static double exactPowerOfTwo(int exponent) {
        return Double.longBitsToDouble((long) (exponent + 1023) << 52);
    }
}
