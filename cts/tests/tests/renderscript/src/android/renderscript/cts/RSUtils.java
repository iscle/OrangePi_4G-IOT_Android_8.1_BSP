/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.res.Resources;
import android.renderscript.Allocation;
import android.renderscript.RSRuntimeException;

import java.util.Random;

/**
 * This class supplies some utils for renderscript tests
 */
public class RSUtils {
    public static final short FLOAT16_POSITIVE_INFINITY = (short) 0x7c00;
    public static final short FLOAT16_NEGATIVE_INFINITY = (short) 0xfc00;
    public static final short FLOAT16_MIN_NORMAL        = (short) 0x0400;  // 0.00006103516
    public static final short FLOAT16_MAX_VALUE         = (short) 0x7bff;  // 65504

    private static final double[] sInterestingDoubles = {
        0.0,
        1.0,
        Math.E,
        Math.PI,
        Math.PI / 2.0,
        Math.PI * 2.0,
        -0.0,
        -1.0,
        -Math.E,
        -Math.PI,
        -Math.PI / 2.0,
        -Math.PI * 2.0,
    };

    // Constants E, PI etc. are set to their nearest representations in Float16.
    private static final short[] sInterestingFloat16s = {
        (short) 0x0, // zero
        (short) 0x3c00, // one
        (short) 0x4170, // E, 2.71875000000
        (short) 0x4248, // PI, 3.14062500000
        (short) 0x3e48, // PI / 2, 1.57031250000
        (short) 0x4648, // PI * 2, 6.28125000000

        (short) 0x8000, // negative zero
        (short) 0xbc00, // negative one
        (short) 0xc170, // -E, -2.71875000000
        (short) 0xc248, // -PI, -3.14062500000
        (short) 0xbe48, // -PI / 2, -1.57031250000
        (short) 0xc648, // -PI * 2, -6.28125000000
    };

    /**
     * Fills the array with random doubles.  Values will be between min (inclusive) and
     * max (inclusive).
     */
    public static void genRandomDoubles(long seed, double min, double max, double array[],
            boolean includeExtremes) {
        Random r = new Random(seed);
        int minExponent = Math.min(Math.getExponent(min), 0);
        int maxExponent = Math.max(Math.getExponent(max), 0);
        if (minExponent < -6 || maxExponent > 6) {
            // Use an exponential distribution
            int exponentDiff = maxExponent - minExponent;
            for (int i = 0; i < array.length; i++) {
                double mantissa = r.nextDouble();
                int exponent = minExponent + r.nextInt(maxExponent - minExponent);
                int sign = (min >= 0) ? 1 : 1 - r.nextInt(2) * 2;  // -1 or 1
                double rand = sign * mantissa * Math.pow(2.0, exponent);
                if (rand < min || rand > max) {
                    continue;
                }
                array[i] = rand;
            }
        } else {
            // Use a linear distribution
            for (int i = 0; i < array.length; i++) {
                double rand = r.nextDouble();
                array[i] = min + rand * (max - min);
            }
        }
        // Seed a few special numbers we want to be sure to test.
        for (int i = 0; i < sInterestingDoubles.length; i++) {
            double d = sInterestingDoubles[i];
            if (min <= d && d <= max) {
                array[r.nextInt(array.length)] = d;
            }
        }
        array[r.nextInt(array.length)] = min;
        array[r.nextInt(array.length)] = max;
        if (includeExtremes) {
            array[r.nextInt(array.length)] = Double.NaN;
            array[r.nextInt(array.length)] = Double.POSITIVE_INFINITY;
            array[r.nextInt(array.length)] = Double.NEGATIVE_INFINITY;
            array[r.nextInt(array.length)] = Double.MIN_VALUE;
            array[r.nextInt(array.length)] = Double.MIN_NORMAL;
            array[r.nextInt(array.length)] = Double.MAX_VALUE;
            array[r.nextInt(array.length)] = -Double.MIN_VALUE;
            array[r.nextInt(array.length)] = -Double.MIN_NORMAL;
            array[r.nextInt(array.length)] = -Double.MAX_VALUE;
        }
    }

    /**
     * Fills the array with random floats.  Values will be between min (inclusive) and
     * max (inclusive).
     */
    public static void genRandomFloats(long seed, float min, float max, float array[],
            boolean includeExtremes) {
        Random r = new Random(seed);
        int minExponent = Math.min(Math.getExponent(min), 0);
        int maxExponent = Math.max(Math.getExponent(max), 0);
        if (minExponent < -6 || maxExponent > 6) {
            // Use an exponential distribution
            int exponentDiff = maxExponent - minExponent;
            for (int i = 0; i < array.length; i++) {
                float mantissa = r.nextFloat();
                int exponent = minExponent + r.nextInt(maxExponent - minExponent);
                int sign = (min >= 0) ? 1 : 1 - r.nextInt(2) * 2;  // -1 or 1
                float rand = sign * mantissa * (float) Math.pow(2.0, exponent);
                if (rand < min || rand > max) {
                    continue;
                }
                array[i] = rand;
            }
        } else {
            // Use a linear distribution
            for (int i = 0; i < array.length; i++) {
                float rand = r.nextFloat();
                array[i] = min + rand * (max - min);
            }
        }
        // Seed a few special numbers we want to be sure to test.
        for (int i = 0; i < sInterestingDoubles.length; i++) {
            float f = (float) sInterestingDoubles[i];
            if (min <= f && f <= max) {
                array[r.nextInt(array.length)] = f;
            }
        }
        array[r.nextInt(array.length)] = min;
        array[r.nextInt(array.length)] = max;
        if (includeExtremes) {
            array[r.nextInt(array.length)] = Float.NaN;
            array[r.nextInt(array.length)] = Float.POSITIVE_INFINITY;
            array[r.nextInt(array.length)] = Float.NEGATIVE_INFINITY;
            array[r.nextInt(array.length)] = Float.MIN_VALUE;
            array[r.nextInt(array.length)] = Float.MIN_NORMAL;
            array[r.nextInt(array.length)] = Float.MAX_VALUE;
            array[r.nextInt(array.length)] = -Float.MIN_VALUE;
            array[r.nextInt(array.length)] = -Float.MIN_NORMAL;
            array[r.nextInt(array.length)] = -Float.MAX_VALUE;
        }
    }

    public static void genRandomFloat16s(long seed, double minDoubleValue, double maxDoubleValue,
            short array[], boolean includeExtremes) {

        // Ensure that requests for random Float16s span a reasnoable range.
        if (maxDoubleValue - minDoubleValue <= 1.) {
            throw new RSRuntimeException("Unexpected: Range is too small");
        }

        boolean includeNegatives = false;

        // Identify a range of 'short' values from the input range of 'double' If either
        // minValueInHalf or maxValueInHalf is +/- infinity, use MAX_VALUE with appropriate sign
        // instead.  The extreme values will get included if includeExtremes flag is set.
        double minValueInHalf = Float16Utils.roundToFloat16(minDoubleValue)[1];
        double maxValueInHalf = Float16Utils.roundToFloat16(maxDoubleValue)[0];

        if (Double.isInfinite(minValueInHalf)) {
            minValueInHalf = Math.copySign(Float16Utils.MAX_VALUE, minValueInHalf);
        }
        if (Double.isInfinite(maxValueInHalf)) {
            maxValueInHalf = Math.copySign(Float16Utils.MAX_VALUE, maxValueInHalf);
        }

        short min = Float16Utils.convertDoubleToFloat16(minValueInHalf);
        short max = Float16Utils.convertDoubleToFloat16(maxValueInHalf);

        // If range spans across zero, set the range to be entirely positive and set
        // includeNegatives to true.  In this scenario, the upper bound is set to the larger of
        // maxValue and abs(minValue).  The lower bound is FLOAT16_MIN_NORMAL.
        if (minDoubleValue < 0. && maxDoubleValue > 0.) {
            includeNegatives = true;
            min = FLOAT16_MIN_NORMAL;

            // If abs(minDoubleValue) is greater than maxDoubleValue, pick abs(minValue) as the
            // upper bound.
            // TODO Update this function to generate random float16s exactly between minDoubleValue
            // and maxDoubleValue.
            if (Math.abs(minDoubleValue) > maxDoubleValue) {
                max = (short) (0x7fff & min);
            }
        } else if (maxDoubleValue < 0.) {
            throw new RSRuntimeException("Unexpected: Range is entirely negative: " +
                Double.toString(minDoubleValue) + " to " + Double.toString(maxDoubleValue));
        }

        // If min is 0 or subnormal, set it to FLOAT16_MIN_NORMAL
        if (Float16Utils.isFloat16Zero(min) || Float16Utils.isFloat16SubNormal(min)) {
            min = FLOAT16_MIN_NORMAL;
        }

        Random r = new Random(seed);
        short range = (short) (max - min + 1);
        for (int i = 0; i < array.length; i ++) {
            array[i] = (short) (min + r.nextInt(range));
        }
        array[r.nextInt(array.length)] = min;
        array[r.nextInt(array.length)] = max;

        // Negate approximately half of the elements.
        if (includeNegatives) {
            for (int i = 0; i < array.length; i ++) {
                if (r.nextBoolean()) {
                    array[i] = (short) (0x8000 | array[i]);
                }
            }
        }

        for (short s : sInterestingFloat16s) {
            if (!includeNegatives && s < 0)
                continue;
            array[r.nextInt(array.length)] = s;
        }
        if (includeExtremes) {
            array[r.nextInt(array.length)] = (short) 0x7c01; // NaN
            array[r.nextInt(array.length)] = FLOAT16_POSITIVE_INFINITY;
            array[r.nextInt(array.length)] = FLOAT16_NEGATIVE_INFINITY;
            array[r.nextInt(array.length)] = FLOAT16_MIN_NORMAL;
            array[r.nextInt(array.length)] = FLOAT16_MAX_VALUE;
            array[r.nextInt(array.length)] = (short) 0x8400; // -MIN_NORMAL, -0.00006103516
            array[r.nextInt(array.length)] = (short) 0xfbff; // -MAX_VALUE, -65504
        }
    }

    /**
     * Fills the array with random ints.  Values will be between min (inclusive) and
     * max (inclusive).
     */
    public static void genRandomInts(long seed, int min, int max, int array[]) {
        Random r = new Random(seed);
        for (int i = 0; i < array.length; i++) {
            long range = max - min + 1;
            array[i] = (int) (min + r.nextLong() % range);
        }
        array[r.nextInt(array.length)] = min;
        array[r.nextInt(array.length)] = max;
    }

    /**
     * Fills the array with random longs.  If signed is true, negative values can be generated.
     * The values will fit within 'numberOfBits'.  This is useful for conversion tests.
     */
    public static void genRandomLongs(long seed, long array[], boolean signed, int numberOfBits) {
      long positiveMask = numberOfBits == 64 ? -1 : ((1l << numberOfBits) - 1);
      long negativeMask = ~positiveMask;
      Random r = new Random(seed);
      for (int i = 0; i < array.length; i++) {
          long l = r.nextLong();
          if (signed && l < 0) {
              l = l | negativeMask;
          } else {
              l = l & positiveMask;
          }
          array[i] = l;
      }
      // Seed a few special numbers we want to be sure to test.
      array[r.nextInt(array.length)] = 0l;
      array[r.nextInt(array.length)] = 1l;
      array[r.nextInt(array.length)] = positiveMask;
      if (signed) {
          array[r.nextInt(array.length)] = negativeMask;
          array[r.nextInt(array.length)] = -1;
      }
    }

    public static void genRandomInts(long seed, int array[], boolean signed, int numberOfBits) {
        long[] longs = new long[array.length];
        genRandomLongs(seed, longs, signed, numberOfBits);
        for (int i = 0; i < array.length; i++) {
            array[i] = (int) longs[i];
        }
    }

    public static void genRandomShorts(long seed, short array[], boolean signed, int numberOfBits) {
        long[] longs = new long[array.length];
        genRandomLongs(seed, longs, signed, numberOfBits);
        for (int i = 0; i < array.length; i++) {
            array[i] = (short) longs[i];
        }
    }

    public static void genRandomBytes(long seed, byte array[], boolean signed, int numberOfBits) {
        long[] longs = new long[array.length];
        genRandomLongs(seed, longs, signed, numberOfBits);
        for (int i = 0; i < array.length; i++) {
            array[i] = (byte) longs[i];
        }
    }

    // Compares two unsigned long.  Returns < 0 if a < b, 0 if a == b, > 0 if a > b.
    public static long compareUnsignedLong(long a, long b) {
        long aFirstFourBits = a >>> 60;
        long bFirstFourBits = b >>> 60;
        long firstFourBitsDiff = aFirstFourBits - bFirstFourBits;
        if (firstFourBitsDiff != 0) {
            return firstFourBitsDiff;
        }
        long aRest = a & 0x0fffffffffffffffl;
        long bRest = b & 0x0fffffffffffffffl;
        return aRest - bRest;
    }
}
