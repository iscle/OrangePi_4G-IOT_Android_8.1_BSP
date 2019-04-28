/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calculator2;

import com.hp.creals.CR;

import java.math.BigInteger;
import java.util.Random;

/**
 * Rational numbers that may turn to null if they get too big.
 * For many operations, if the length of the nuumerator plus the length of the denominator exceeds
 * a maximum size, we simply return null, and rely on our caller do something else.
 * We currently never return null for a pure integer or for a BoundedRational that has just been
 * constructed.
 *
 * We also implement a number of irrational functions.  These return a non-null result only when
 * the result is known to be rational.
 */
public class BoundedRational {
    // TODO: Consider returning null for integers.  With some care, large factorials might become
    // much faster.
    // TODO: Maybe eventually make this extend Number?

    private static final int MAX_SIZE = 10000; // total, in bits

    private final BigInteger mNum;
    private final BigInteger mDen;

    public BoundedRational(BigInteger n, BigInteger d) {
        mNum = n;
        mDen = d;
    }

    public BoundedRational(BigInteger n) {
        mNum = n;
        mDen = BigInteger.ONE;
    }

    public BoundedRational(long n, long d) {
        mNum = BigInteger.valueOf(n);
        mDen = BigInteger.valueOf(d);
    }

    public BoundedRational(long n) {
        mNum = BigInteger.valueOf(n);
        mDen = BigInteger.valueOf(1);
    }

    /**
     * Convert to String reflecting raw representation.
     * Debug or log messages only, not pretty.
     */
    public String toString() {
        return mNum.toString() + "/" + mDen.toString();
    }

    /**
     * Convert to readable String.
     * Intended for output to user.  More expensive, less useful for debugging than
     * toString().  Not internationalized.
     */
    public String toNiceString() {
        final BoundedRational nicer = reduce().positiveDen();
        String result = nicer.mNum.toString();
        if (!nicer.mDen.equals(BigInteger.ONE)) {
            result += "/" + nicer.mDen;
        }
        return result;
    }

    public static String toString(BoundedRational r) {
        if (r == null) {
            return "not a small rational";
        }
        return r.toString();
    }

    /**
     * Returns a truncated (rounded towards 0) representation of the result.
     * Includes n digits to the right of the decimal point.
     * @param n result precision, >= 0
     */
    public String toStringTruncated(int n) {
        String digits = mNum.abs().multiply(BigInteger.TEN.pow(n)).divide(mDen.abs()).toString();
        int len = digits.length();
        if (len < n + 1) {
            digits = StringUtils.repeat('0', n + 1 - len) + digits;
            len = n + 1;
        }
        return (signum() < 0 ? "-" : "") + digits.substring(0, len - n) + "."
                + digits.substring(len - n);
    }

    /**
     * Return a double approximation.
     * The result is correctly rounded if numerator and denominator are
     * exactly representable as a double.
     * TODO: This should always be correctly rounded.
     */
    public double doubleValue() {
        return mNum.doubleValue() / mDen.doubleValue();
    }

    public CR crValue() {
        return CR.valueOf(mNum).divide(CR.valueOf(mDen));
    }

    public int intValue() {
        BoundedRational reduced = reduce();
        if (!reduced.mDen.equals(BigInteger.ONE)) {
            throw new ArithmeticException("intValue of non-int");
        }
        return reduced.mNum.intValue();
    }

    // Approximate number of bits to left of binary point.
    // Negative indicates leading zeroes to the right of binary point.
    public int wholeNumberBits() {
        if (mNum.signum() == 0) {
            return Integer.MIN_VALUE;
        } else {
            return mNum.bitLength() - mDen.bitLength();
        }
    }

    private boolean tooBig() {
        if (mDen.equals(BigInteger.ONE)) {
            return false;
        }
        return (mNum.bitLength() + mDen.bitLength() > MAX_SIZE);
    }

    /**
     * Return an equivalent fraction with a positive denominator.
     */
    private BoundedRational positiveDen() {
        if (mDen.signum() > 0) {
            return this;
        }
        return new BoundedRational(mNum.negate(), mDen.negate());
    }

    /**
     * Return an equivalent fraction in lowest terms.
     * Denominator sign may remain negative.
     */
    private BoundedRational reduce() {
        if (mDen.equals(BigInteger.ONE)) {
            return this;  // Optimization only
        }
        final BigInteger divisor = mNum.gcd(mDen);
        return new BoundedRational(mNum.divide(divisor), mDen.divide(divisor));
    }

    static Random sReduceRng = new Random();

    /**
     * Return a possibly reduced version of r that's not tooBig().
     * Return null if none exists.
     */
    private static BoundedRational maybeReduce(BoundedRational r) {
        if (r == null) return null;
        // Reduce randomly, with 1/16 probability, or if the result is too big.
        if (!r.tooBig() && (sReduceRng.nextInt() & 0xf) != 0) {
            return r;
        }
        BoundedRational result = r.positiveDen();
        result = result.reduce();
        if (!result.tooBig()) {
            return result;
        }
        return null;
    }

    public int compareTo(BoundedRational r) {
        // Compare by multiplying both sides by denominators, invert result if denominator product
        // was negative.
        return mNum.multiply(r.mDen).compareTo(r.mNum.multiply(mDen)) * mDen.signum()
                * r.mDen.signum();
    }

    public int signum() {
        return mNum.signum() * mDen.signum();
    }

    public boolean equals(BoundedRational r) {
        return compareTo(r) == 0;
    }

    // We use static methods for arithmetic, so that we can easily handle the null case.  We try
    // to catch domain errors whenever possible, sometimes even when one of the arguments is null,
    // but not relevant.

    /**
     * Returns equivalent BigInteger result if it exists, null if not.
     */
    public static BigInteger asBigInteger(BoundedRational r) {
        if (r == null) {
            return null;
        }
        final BigInteger[] quotAndRem = r.mNum.divideAndRemainder(r.mDen);
        if (quotAndRem[1].signum() == 0) {
            return quotAndRem[0];
        } else {
            return null;
        }
    }
    public static BoundedRational add(BoundedRational r1, BoundedRational r2) {
        if (r1 == null || r2 == null) {
            return null;
        }
        final BigInteger den = r1.mDen.multiply(r2.mDen);
        final BigInteger num = r1.mNum.multiply(r2.mDen).add(r2.mNum.multiply(r1.mDen));
        return maybeReduce(new BoundedRational(num,den));
    }

    /**
     * Return the argument, but with the opposite sign.
     * Returns null only for a null argument.
     */
    public static BoundedRational negate(BoundedRational r) {
        if (r == null) {
            return null;
        }
        return new BoundedRational(r.mNum.negate(), r.mDen);
    }

    public static BoundedRational subtract(BoundedRational r1, BoundedRational r2) {
        return add(r1, negate(r2));
    }

    /**
     * Return product of r1 and r2 without reducing the result.
     */
    private static BoundedRational rawMultiply(BoundedRational r1, BoundedRational r2) {
        // It's tempting but marginally unsound to reduce 0 * null to 0.  The null could represent
        // an infinite value, for which we failed to throw an exception because it was too big.
        if (r1 == null || r2 == null) {
            return null;
        }
        // Optimize the case of our special ONE constant, since that's cheap and somewhat frequent.
        if (r1 == ONE) {
            return r2;
        }
        if (r2 == ONE) {
            return r1;
        }
        final BigInteger num = r1.mNum.multiply(r2.mNum);
        final BigInteger den = r1.mDen.multiply(r2.mDen);
        return new BoundedRational(num,den);
    }

    public static BoundedRational multiply(BoundedRational r1, BoundedRational r2) {
        return maybeReduce(rawMultiply(r1, r2));
    }

    public static class ZeroDivisionException extends ArithmeticException {
        public ZeroDivisionException() {
            super("Division by zero");
        }
    }

    /**
     * Return the reciprocal of r (or null if the argument was null).
     */
    public static BoundedRational inverse(BoundedRational r) {
        if (r == null) {
            return null;
        }
        if (r.mNum.signum() == 0) {
            throw new ZeroDivisionException();
        }
        return new BoundedRational(r.mDen, r.mNum);
    }

    public static BoundedRational divide(BoundedRational r1, BoundedRational r2) {
        return multiply(r1, inverse(r2));
    }

    public static BoundedRational sqrt(BoundedRational r) {
        // Return non-null if numerator and denominator are small perfect squares.
        if (r == null) {
            return null;
        }
        r = r.positiveDen().reduce();
        if (r.mNum.signum() < 0) {
            throw new ArithmeticException("sqrt(negative)");
        }
        final BigInteger num_sqrt = BigInteger.valueOf(Math.round(Math.sqrt(r.mNum.doubleValue())));
        if (!num_sqrt.multiply(num_sqrt).equals(r.mNum)) {
            return null;
        }
        final BigInteger den_sqrt = BigInteger.valueOf(Math.round(Math.sqrt(r.mDen.doubleValue())));
        if (!den_sqrt.multiply(den_sqrt).equals(r.mDen)) {
            return null;
        }
        return new BoundedRational(num_sqrt, den_sqrt);
    }

    public final static BoundedRational ZERO = new BoundedRational(0);
    public final static BoundedRational HALF = new BoundedRational(1,2);
    public final static BoundedRational MINUS_HALF = new BoundedRational(-1,2);
    public final static BoundedRational THIRD = new BoundedRational(1,3);
    public final static BoundedRational QUARTER = new BoundedRational(1,4);
    public final static BoundedRational SIXTH = new BoundedRational(1,6);
    public final static BoundedRational ONE = new BoundedRational(1);
    public final static BoundedRational MINUS_ONE = new BoundedRational(-1);
    public final static BoundedRational TWO = new BoundedRational(2);
    public final static BoundedRational MINUS_TWO = new BoundedRational(-2);
    public final static BoundedRational TEN = new BoundedRational(10);
    public final static BoundedRational TWELVE = new BoundedRational(12);
    public final static BoundedRational THIRTY = new BoundedRational(30);
    public final static BoundedRational MINUS_THIRTY = new BoundedRational(-30);
    public final static BoundedRational FORTY_FIVE = new BoundedRational(45);
    public final static BoundedRational MINUS_FORTY_FIVE = new BoundedRational(-45);
    public final static BoundedRational NINETY = new BoundedRational(90);
    public final static BoundedRational MINUS_NINETY = new BoundedRational(-90);

    private static final BigInteger BIG_TWO = BigInteger.valueOf(2);

    /**
     * Compute integral power of this, assuming this has been reduced and exp is >= 0.
     */
    private BoundedRational rawPow(BigInteger exp) {
        if (exp.equals(BigInteger.ONE)) {
            return this;
        }
        if (exp.and(BigInteger.ONE).intValue() == 1) {
            return rawMultiply(rawPow(exp.subtract(BigInteger.ONE)), this);
        }
        if (exp.signum() == 0) {
            return ONE;
        }
        BoundedRational tmp = rawPow(exp.shiftRight(1));
        if (Thread.interrupted()) {
            throw new CR.AbortedException();
        }
        return rawMultiply(tmp, tmp);
    }

    /**
     * Compute an integral power of this.
     */
    public BoundedRational pow(BigInteger exp) {
        if (exp.signum() < 0) {
            return inverse(pow(exp.negate()));
        }
        if (exp.equals(BigInteger.ONE)) {
            return this;
        }
        // Reducing once at the beginning means there's no point in reducing later.
        return reduce().rawPow(exp);
    }

    public static BoundedRational pow(BoundedRational base, BoundedRational exp) {
        if (exp == null) {
            return null;
        }
        if (exp.mNum.signum() == 0) {
            // Questionable if base has undefined value.  Java.lang.Math.pow() returns 1 anyway,
            // so we do the same.
            return new BoundedRational(1);
        }
        if (base == null) {
            return null;
        }
        exp = exp.reduce().positiveDen();
        if (!exp.mDen.equals(BigInteger.ONE)) {
            return null;
        }
        return base.pow(exp.mNum);
    }


    private static final BigInteger BIG_FIVE = BigInteger.valueOf(5);
    private static final BigInteger BIG_MINUS_ONE = BigInteger.valueOf(-1);

    /**
     * Return the number of decimal digits to the right of the decimal point required to represent
     * the argument exactly.
     * Return Integer.MAX_VALUE if that's not possible.  Never returns a value less than zero, even
     * if r is a power of ten.
     */
    public static int digitsRequired(BoundedRational r) {
        if (r == null) {
            return Integer.MAX_VALUE;
        }
        int powersOfTwo = 0;  // Max power of 2 that divides denominator
        int powersOfFive = 0;  // Max power of 5 that divides denominator
        // Try the easy case first to speed things up.
        if (r.mDen.equals(BigInteger.ONE)) {
            return 0;
        }
        r = r.reduce();
        BigInteger den = r.mDen;
        if (den.bitLength() > MAX_SIZE) {
            return Integer.MAX_VALUE;
        }
        while (!den.testBit(0)) {
            ++powersOfTwo;
            den = den.shiftRight(1);
        }
        while (den.mod(BIG_FIVE).signum() == 0) {
            ++powersOfFive;
            den = den.divide(BIG_FIVE);
        }
        // If the denominator has a factor of other than 2 or 5 (the divisors of 10), the decimal
        // expansion does not terminate.  Multiplying the fraction by any number of powers of 10
        // will not cancel the demoniator.  (Recall the fraction was in lowest terms to start
        // with.) Otherwise the powers of 10 we need to cancel the denominator is the larger of
        // powersOfTwo and powersOfFive.
        if (!den.equals(BigInteger.ONE) && !den.equals(BIG_MINUS_ONE)) {
            return Integer.MAX_VALUE;
        }
        return Math.max(powersOfTwo, powersOfFive);
    }
}
