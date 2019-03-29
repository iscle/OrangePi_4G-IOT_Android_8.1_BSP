/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.renderscriptlegacy.cts;

import android.content.res.Resources;
import android.renderscript.Allocation;
import android.renderscript.RSRuntimeException;

import java.util.Random;

/**
 * This class supplies some utils for renderscript tests
 */
public class RSUtils {
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
