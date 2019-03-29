/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.renderscript.cts;

import android.renderscript.RSRuntimeException;
import android.util.Log;

import junit.framework.Assert;

/** This class contains utility functions needed by RenderScript CTS tests to handle Float16
 * operations.
 */
class Float16Utils {
    // 16-bit masks for extracting sign, exponent and mantissa bits
    private static short SIGN_MASK     = (short) 0x8000;
    private static short EXPONENT_MASK = (short) 0x7C00;
    private static short MANTISSA_MASK = (short) 0x03FF;

    private static long DOUBLE_SIGN_MASK = 0x8000000000000000L;
    private static long DOUBLE_EXPONENT_MASK = 0x7ff0000000000000L;
    private static long DOUBLE_MANTISSA_MASK = 0x000fffffffffffffL;

    static double MIN_NORMAL = Math.scalb(1.0, -14); // smallest Float16 normal is 2 ^ -14
    static double MIN_VALUE = Math.scalb(1.0, -24); // smallest Float16 value is 2 ^ -24
    static double MAX_VALUE = 65504; // largest Float16 value is 2^16 - 32

    // NaN has all exponent bits set to 1 and a non-zero mantissa
    static boolean isFloat16NaN(short val) {
        return (val & EXPONENT_MASK) == EXPONENT_MASK &&
               (val & MANTISSA_MASK) != 0;
    }

    // Infinity has all exponent bits set to 1 and zeroes in mantissa
    static boolean isFloat16Infinite(short val) {
        return (val & EXPONENT_MASK) == EXPONENT_MASK &&
               (val & MANTISSA_MASK) == 0;
    }

    // Subnormal numbers have exponent bits set to 0 and a non-zero mantissa
    static boolean isFloat16SubNormal(short val) {
        return (val & EXPONENT_MASK) == 0 && (val & MANTISSA_MASK) != 0;
    }

    // Zero has all but the sign bit set to zero
    static boolean isFloat16Zero(short val) {
        return (val & ~SIGN_MASK) == 0;
    }

    // Negativity test checks the sign bit
    static boolean isFloat16Negative(short val) {
        return (val & SIGN_MASK) != 0;
    }

    // Check if this is a finite, non-zero FP16 value
    static boolean isFloat16FiniteNonZero(short val) {
        return !isFloat16NaN(val) && !isFloat16Infinite(val) && !isFloat16Zero(val);
    }

    static float convertFloat16ToFloat(short val) {
        // Extract sign, exponent and mantissa
        int sign = val & SIGN_MASK;
        int exponent = (val & EXPONENT_MASK) >> 10;
        int mantissa = val & MANTISSA_MASK;

        // 0.<mantissa> = <mantissa> * 2^-10
        float mantissaAsFloat = Math.scalb(mantissa, -10);

        float result;
        if (isFloat16Zero(val))
            result = 0.0f;
        else if (isFloat16Infinite(val))
            result = java.lang.Float.POSITIVE_INFINITY;
        else if (isFloat16NaN(val))
            result = java.lang.Float.NaN;
        else if (isFloat16SubNormal(val)) {
            // value is 2^-14 * mantissaAsFloat
            result = Math.scalb(1, -14) * mantissaAsFloat;
        }
        else {
            // value is 2^(exponent - 15) * 1.<mantissa>
            result = Math.scalb(1, exponent - 15) * (1 + mantissaAsFloat);
        }

        if (sign != 0)
            result = -result;
        return result;
    }

    static double convertFloat16ToDouble(short val) {
        return (double) convertFloat16ToFloat(val);
    }

    /* This utility function accepts the mantissa, exponent and an isNegative flag and constructs a
     * double value.  The exponent should be biased, but not shifted left by 52-bits.
     */
    private static double constructDouble(long mantissa, long exponent, boolean isNegative) {
        exponent = exponent << 52;
        long bits = (exponent & DOUBLE_EXPONENT_MASK) | (mantissa & DOUBLE_MANTISSA_MASK);
        if (isNegative) bits |= DOUBLE_SIGN_MASK;
        return Double.longBitsToDouble(bits);
    }

    /* This function takes a double value and returns an array with the double representations of
     * the Float16 values immediately smaller and larger than the input.  If the input value is
     * precisely representable in Float16, it is copied into both the entries of the array.
     *
     * The returned values can be subnormal Float16 numbers.  Handling subnormals is delegated to
     * the caller.
     *
     * TODO Extend this function to handle rounding for both float16 and float32.
     */
    static double[] roundToFloat16(double value) {
        long valueBits = Double.doubleToLongBits(value);
        long mantissa = valueBits & DOUBLE_MANTISSA_MASK; // 52-bit mantissa
        long exponent = valueBits & DOUBLE_EXPONENT_MASK; // 11-bit exponent
        long unbiasedExponent = (exponent >> 52) - 1023;
        boolean isNegative = (valueBits & DOUBLE_SIGN_MASK) != 0;

        double[] result = new double[2];
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            // Input is NaN or Infinity.  Return unchanged.
            result[0] = value;
            result[1] = value;
            return result; // Note that we skip the negation at the end of this function
        }

        if (unbiasedExponent == -1023 && mantissa == 0) {
            // Zero.  Assign 0 and adjust sign at the end of this function
            result[0] = 0.;
            result[1] = 0.;
        }
        else if (unbiasedExponent < -24) {
            // Absolute value is between 0 and MIN_VALUE.  Return 0 and MIN_VALUE
            result[0] = 0.;
            result[1] = MIN_VALUE;
        }
        else if (unbiasedExponent <= 15) {
            /*
             * Either subnormal or normal.  We compute a mask for the excess precision bits in the
             * mantissa.
             *
             * (a) If none of these bits are set, the current value's mantissa and exponent are used
             * for both the low and high values.
             * (b) If some of these bits are set, we zero-out the extra bits to get the mantissa and
             * exponent of the lower value.  For the higher value, we increment the masked mantissa
             * at the least-significant bit within the range of this Float16 value.  To handle
             * overflows during the the increment, we need to increment the exponent and round up to
             * infinity if needed.
             */

            // 'mask' is used to detect and zero-out excess bits set.  'mask + 1' is the value
            // added to zero-ed out mantissa to get the next higher Float16 value.
            long mask;
            long maxSigMantissaBits;

            if (unbiasedExponent < -14) {
                // Subnormal Float16.  For Float16's MIN_VALUE, mantissa can have no bits set (after
                // adjusting for the implied one bit.  For each higher exponent, an extra bit of
                // precision is allowed in the mantissa.  This computes to "24 + unbiasedExponent".
                maxSigMantissaBits = 24 + unbiasedExponent;
            } else {
                // For normal Float16 values have 10 bits of precision in the mantissa.
                maxSigMantissaBits = 10;
            }
            mask = DOUBLE_MANTISSA_MASK >> maxSigMantissaBits;

            // zero-out the excess precision bits for the mantissa for both low and high values.
            long lowFloat16Mantissa = mantissa & ~mask;
            long highFloat16Mantissa = mantissa & ~mask;

            long lowFloat16Exponent = unbiasedExponent;
            long highFloat16Exponent = unbiasedExponent;

            if ((mantissa & mask) != 0) {
                // If mantissa has extra bits set, increment the mantissa at the LSB (for this
                // Float16 value)
                highFloat16Mantissa += mask + 1;

                // If this overflows the mantissa into the exponent, set mantissa to zero and
                // increment the exponent.
                if ((highFloat16Mantissa & DOUBLE_EXPONENT_MASK) != 0) {
                    highFloat16Mantissa = 0;
                    highFloat16Exponent += 1;
                }

                // If the exponent exceeds the range of Float16 exponents, set it to 1024, so the
                // value gets rounded up to Double.POSITIVE_INFINITY.
                if (highFloat16Exponent == 16) {
                    highFloat16Exponent = 1024;
                }
            }

            result[0] = constructDouble(lowFloat16Mantissa, lowFloat16Exponent + 1023, false);
            result[1] = constructDouble(highFloat16Mantissa, highFloat16Exponent + 1023, false);
        } else {
            // Exponent is outside Float16's range.  Use POSITIVE_INFINITY for both bounds.
            result[0] = Double.POSITIVE_INFINITY;
            result[1] = Double.POSITIVE_INFINITY;
        }

        // Swap values in result and negate them if the input value is negative.
        if (isNegative) {
            double tmp = result[0];
            result[0] = -result[1];
            result[1] = -tmp;
        }

        return result;
    }

    // This function takes a double value and returns 1 ulp, in Float16 precision, of that value.
    // Both the parameter and return value have 'double' type but they should be exactly
    // representable in Float16.  If the parameter exceeds the precision of Float16, an exception is
    // thrown.
    static double float16Ulp(double value) {
        long valueBits = Double.doubleToLongBits(value);
        long mantissa = valueBits & DOUBLE_MANTISSA_MASK; // 52-bit mantissa
        long exponent = valueBits & DOUBLE_EXPONENT_MASK; // 11-bit exponent
        long unbiasedExponent = (exponent >> 52) - 1023;

        if (unbiasedExponent == 1024) { // i.e. NaN or infinity
            if (mantissa == 0) {
                return Double.POSITIVE_INFINITY; // ulp of +/- infinity is +infinity
            } else {
                return Double.NaN; // ulp for NaN is NaN
            }
        }

        if (unbiasedExponent == -1023) {
            // assert that mantissa is zero, i.e. value is zero and not a subnormal value.
            if (mantissa != 0) {
                throw new RSRuntimeException("float16ulp: Double parameter is subnormal");
            }
            return MIN_VALUE;
        }

        if (unbiasedExponent < -24 || unbiasedExponent > 15) {
            throw new RSRuntimeException("float16Ulp: Double parameter's exponent out of range");
        }

        if (unbiasedExponent >= -24 && unbiasedExponent < -14) {
            // Exponent within the range of Float16 subnormals.

            // Ensure that mantissa doesn't have too much precision.  For example, the smallest
            // normal number has an unbiased exponent of -24 and has one bit in mantissa.  Each
            // higher exponent allows one extra bit of precision in the mantissa.  Combined with the
            // implied one bit, the mantissa can have "24 + unbiasedExponent" significant bits.  The
            // rest of the 52 bits in mantissa must be zero.

            long maxSigMantissaBits = 24 + unbiasedExponent;
            long mask = DOUBLE_MANTISSA_MASK >> maxSigMantissaBits;

            if((mask & mantissa) != 0) {
                throw new RSRuntimeException("float16ulp: Double parameter is too precise for subnormal Float16 values.");
            }
            return MIN_VALUE;
        }
        if (unbiasedExponent >= -14) {
            // Exponent within the range of Float16 normals.  Ensure that the mantissa has at most
            // 10 significant bits.
            long mask = DOUBLE_MANTISSA_MASK >> 10;
            if ((mantissa & mask) != 0) {
                throw new RSRuntimeException("float16ulp: Double parameter is too precise for normal Float16 values.");
            }
            return Math.scalb(1.0, (int) (unbiasedExponent - 10));
        }
        throw new RSRuntimeException("float16Ulp: unreachable line executed");
    }

    // This function converts its double input value to its Float16 representation (represented as a
    // short).  It assumes, but does not check, that the input is precisely representable in Float16
    // precision.  No rounding is performed either.
    static short convertDoubleToFloat16(double value) {
        if (value == 0.) {
            if (Double.doubleToLongBits(value) == 0)
                return (short) 0x0;
            else
                return (short) 0x8000;
        } else if (Double.isNaN(value)) {
            // return Quiet NaN irrespective of what kind of NaN 'value' is.
            return (short) 0x7e00;
        } else if (value == Double.POSITIVE_INFINITY) {
            return (short) 0x7c00;
        } else if (value == Double.NEGATIVE_INFINITY) {
            return (short) 0xfc00;
        }

        double positiveValue = Math.abs(value);
        boolean isNegative = (value < 0.);
        if (positiveValue < MIN_NORMAL) {
            short quotient = (short) (positiveValue / MIN_VALUE);
            return (isNegative) ? (short) (0x8000 | quotient) : quotient;
        } else {
            long valueBits = Double.doubleToLongBits(value);
            long mantissa = valueBits & DOUBLE_MANTISSA_MASK; // 52-bit mantissa
            long exponent = valueBits & DOUBLE_EXPONENT_MASK; // 11-bit exponent
            long unbiasedExponent = (exponent >> 52) - 1023;

            short halfExponent = (short) ((unbiasedExponent + 15) << 10);
            short halfMantissa = (short) (mantissa >> 42);
            short halfValue = (short) (halfExponent | halfMantissa);
            return (isNegative) ? (short) (0x8000 | halfValue) : halfValue;
        }
    }

}
