/*
 * Copyright (C) 2016 The Android Open Source Project
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

import java.math.BigInteger;
import com.hp.creals.CR;
import com.hp.creals.UnaryCRFunction;

/**
 * Computable real numbers, represented so that we can get exact decidable comparisons
 * for a number of interesting special cases, including rational computations.
 *
 * A real number is represented as the product of two numbers with different representations:
 * A) A BoundedRational that can only represent a subset of the rationals, but supports
 *    exact computable comparisons.
 * B) A lazily evaluated "constructive real number" that provides operations to evaluate
 *    itself to any requested number of digits.
 * Whenever possible, we choose (B) to be one of a small set of known constants about which we
 * know more.  For example, whenever we can, we represent rationals such that (B) is 1.
 * This scheme allows us to do some very limited symbolic computation on numbers when both
 * have the same (B) value, as well as in some other situations.  We try to maximize that
 * possibility.
 *
 * Arithmetic operations and operations that produce finite approximations may throw unchecked
 * exceptions produced by the underlying CR and BoundedRational packages, including
 * CR.PrecisionOverflowException and CR.AbortedException.
 */
public class UnifiedReal {

    private final BoundedRational mRatFactor;
    private final CR mCrFactor;
    // TODO: It would be helpful to add flags to indicate whether the result is known
    // irrational, etc.  This sometimes happens even if mCrFactor is not one of the known ones.
    // And exact comparisons between rationals and known irrationals are decidable.

    /**
     * Perform some nontrivial consistency checks.
     * @hide
     */
    public static boolean enableChecks = true;

    private static void check(boolean b) {
        if (!b) {
            throw new AssertionError();
        }
    }

    private UnifiedReal(BoundedRational rat, CR cr) {
        if (rat == null) {
            throw new ArithmeticException("Building UnifiedReal from null");
        }
        // We don't normally traffic in null CRs, and hence don't test explicitly.
        mCrFactor = cr;
        mRatFactor = rat;
    }

    public UnifiedReal(CR cr) {
        this(BoundedRational.ONE, cr);
    }

    public UnifiedReal(BoundedRational rat) {
        this(rat, CR_ONE);
    }

    public UnifiedReal(BigInteger n) {
        this(new BoundedRational(n));
    }

    public UnifiedReal(long n) {
        this(new BoundedRational(n));
    }

    // Various helpful constants
    private final static BigInteger BIG_24 = BigInteger.valueOf(24);
    private final static int DEFAULT_COMPARE_TOLERANCE = -1000;

    // Well-known CR constants we try to use in the mCrFactor position:
    private final static CR CR_ONE = CR.ONE;
    private final static CR CR_PI = CR.PI;
    private final static CR CR_E = CR.ONE.exp();
    private final static CR CR_SQRT2 = CR.valueOf(2).sqrt();
    private final static CR CR_SQRT3 = CR.valueOf(3).sqrt();
    private final static CR CR_LN2 = CR.valueOf(2).ln();
    private final static CR CR_LN3 = CR.valueOf(3).ln();
    private final static CR CR_LN5 = CR.valueOf(5).ln();
    private final static CR CR_LN6 = CR.valueOf(6).ln();
    private final static CR CR_LN7 = CR.valueOf(7).ln();
    private final static CR CR_LN10 = CR.valueOf(10).ln();

    // Square roots that we try to recognize.
    // We currently recognize only a small fixed collection, since the sqrt() function needs to
    // identify numbers of the form <SQRT[i]>*n^2, and we don't otherwise know of a good
    // algorithm for that.
    private final static CR sSqrts[] = {
            null,
            CR.ONE,
            CR_SQRT2,
            CR_SQRT3,
            null,
            CR.valueOf(5).sqrt(),
            CR.valueOf(6).sqrt(),
            CR.valueOf(7).sqrt(),
            null,
            null,
            CR.valueOf(10).sqrt() };

    // Natural logs of small integers that we try to recognize.
    private final static CR sLogs[] = {
            null,
            null,
            CR_LN2,
            CR_LN3,
            null,
            CR_LN5,
            CR_LN6,
            CR_LN7,
            null,
            null,
            CR_LN10 };


    // Some convenient UnifiedReal constants.
    public static final UnifiedReal PI = new UnifiedReal(CR_PI);
    public static final UnifiedReal E = new UnifiedReal(CR_E);
    public static final UnifiedReal ZERO = new UnifiedReal(BoundedRational.ZERO);
    public static final UnifiedReal ONE = new UnifiedReal(BoundedRational.ONE);
    public static final UnifiedReal MINUS_ONE = new UnifiedReal(BoundedRational.MINUS_ONE);
    public static final UnifiedReal TWO = new UnifiedReal(BoundedRational.TWO);
    public static final UnifiedReal MINUS_TWO = new UnifiedReal(BoundedRational.MINUS_TWO);
    public static final UnifiedReal HALF = new UnifiedReal(BoundedRational.HALF);
    public static final UnifiedReal MINUS_HALF = new UnifiedReal(BoundedRational.MINUS_HALF);
    public static final UnifiedReal TEN = new UnifiedReal(BoundedRational.TEN);
    public static final UnifiedReal RADIANS_PER_DEGREE
            = new UnifiedReal(new BoundedRational(1, 180), CR_PI);
    private static final UnifiedReal SIX = new UnifiedReal(6);
    private static final UnifiedReal HALF_SQRT2 = new UnifiedReal(BoundedRational.HALF, CR_SQRT2);
    private static final UnifiedReal SQRT3 = new UnifiedReal(CR_SQRT3);
    private static final UnifiedReal HALF_SQRT3 = new UnifiedReal(BoundedRational.HALF, CR_SQRT3);
    private static final UnifiedReal THIRD_SQRT3 = new UnifiedReal(BoundedRational.THIRD, CR_SQRT3);
    private static final UnifiedReal PI_OVER_2 = new UnifiedReal(BoundedRational.HALF, CR_PI);
    private static final UnifiedReal PI_OVER_3 = new UnifiedReal(BoundedRational.THIRD, CR_PI);
    private static final UnifiedReal PI_OVER_4 = new UnifiedReal(BoundedRational.QUARTER, CR_PI);
    private static final UnifiedReal PI_OVER_6 = new UnifiedReal(BoundedRational.SIXTH, CR_PI);


    /**
     * Given a constructive real cr, try to determine whether cr is the square root of
     * a small integer.  If so, return its square as a BoundedRational.  Otherwise return null.
     * We make this determination by simple table lookup, so spurious null returns are
     * entirely possible, or even likely.
     */
    private static BoundedRational getSquare(CR cr) {
        for (int i = 0; i < sSqrts.length; ++i) {
             if (sSqrts[i] == cr) {
                return new BoundedRational(i);
             }
        }
        return null;
    }

    /**
     * Given a constructive real cr, try to determine whether cr is the square root of
     * a small integer.  If so, return its square as a BoundedRational.  Otherwise return null.
     * We make this determination by simple table lookup, so spurious null returns are
     * entirely possible, or even likely.
     */
    private BoundedRational getExp(CR cr) {
        for (int i = 0; i < sLogs.length; ++i) {
             if (sLogs[i] == cr) {
                return new BoundedRational(i);
             }
        }
        return null;
    }

    /**
     * If the argument is a well-known constructive real, return its name.
     * The name of "CR_ONE" is the empty string.
     * No named constructive reals are rational multiples of each other.
     * Thus two UnifiedReals with different named mCrFactors can be equal only if both
     * mRatFactors are zero or possibly if one is CR_PI and the other is CR_E.
     * (The latter is apparently an open problem.)
     */
    private static String crName(CR cr) {
        if (cr == CR_ONE) {
            return "";
        }
        if (cr == CR_PI) {
            return "\u03C0";   // GREEK SMALL LETTER PI
        }
        if (cr == CR_E) {
            return "e";
        }
        for (int i = 0; i < sSqrts.length; ++i) {
            if (cr == sSqrts[i]) {
                return "\u221A" /* SQUARE ROOT */ + i;
            }
        }
        for (int i = 0; i < sLogs.length; ++i) {
            if (cr == sLogs[i]) {
                return "ln(" + i + ")";
            }
        }
        return null;
    }

    /**
     * Would crName() return non-Null?
     */
    private static boolean isNamed(CR cr) {
        if (cr == CR_ONE || cr == CR_PI || cr == CR_E) {
            return true;
        }
        for (CR r: sSqrts) {
            if (cr == r) {
                return true;
            }
        }
        for (CR r: sLogs) {
            if (cr == r) {
                return true;
            }
        }
        return false;
    }

    /**
     * Is cr known to be algebraic (as opposed to transcendental)?
     * Currently only produces meaningful results for the above known special
     * constructive reals.
     */
    private static boolean definitelyAlgebraic(CR cr) {
        return cr == CR_ONE || getSquare(cr) != null;
    }

    /**
     * Is this number known to be rational?
     */
    public boolean definitelyRational() {
        return mCrFactor == CR_ONE || mRatFactor.signum() == 0;
    }

    /**
     * Is this number known to be irrational?
     * TODO: We could track the fact that something is irrational with an explicit flag, which
     * could cover many more cases.  Whether that matters in practice is TBD.
     */
    public boolean definitelyIrrational() {
        return !definitelyRational() && isNamed(mCrFactor);
    }

    /**
     * Is this number known to be algebraic?
     */
    public boolean definitelyAlgebraic() {
        return definitelyAlgebraic(mCrFactor) || mRatFactor.signum() == 0;
    }

    /**
     * Is this number known to be transcendental?
     */
    public boolean definitelyTranscendental() {
        return !definitelyAlgebraic() && isNamed(mCrFactor);
    }


    /**
     * Is it known that the two constructive reals differ by something other than a
     * a rational factor, i.e. is it known that two UnifiedReals
     * with those mCrFactors will compare unequal unless both mRatFactors are zero?
     * If this returns true, then a comparison of two UnifiedReals using those two
     * mCrFactors cannot diverge, though we don't know of a good runtime bound.
     */
    private static boolean definitelyIndependent(CR r1, CR r2) {
        // The question here is whether r1 = x*r2, where x is rational, where r1 and r2
        // are in our set of special known CRs, can have a solution.
        // This cannot happen if one is CR_ONE and the other is not.
        // (Since all others are irrational.)
        // This cannot happen for two named square roots, which have no repeated factors.
        // (To see this, square both sides of the equation and factor.  Each prime
        // factor in the numerator and denominator occurs twice.)
        // This cannot happen for e or pi on one side, and a square root on the other.
        // (One is transcendental, the other is algebraic.)
        // This cannot happen for two of our special natural logs.
        // (Otherwise ln(m) = (a/b)ln(n) ==> m = n^(a/b) ==> m^b = n^a, which is impossible
        // because either m or n includes a prime factor not shared by the other.)
        // This cannot happen for a log and a square root.
        // (The Lindemann-Weierstrass theorem tells us, among other things, that if
        // a is algebraic, then exp(a) is transcendental.  Thus if l in our finite
        // set of logs where algebraic, expl(l), must be transacendental.
        // But exp(l) is an integer.  Thus the logs are transcendental.  But of course the
        // square roots are algebraic.  Thus they can't be rational multiples.)
        // Unfortunately, we do not know whether e/pi is rational.
        if (r1 == r2) {
            return false;
        }
        CR other;
        if (r1 == CR_E || r1 == CR_PI) {
            return definitelyAlgebraic(r2);
        }
        if (r2 == CR_E || r2 == CR_PI) {
            return definitelyAlgebraic(r1);
        }
        return isNamed(r1) && isNamed(r2);
    }

    /**
     * Convert to String reflecting raw representation.
     * Debug or log messages only, not pretty.
     */
    public String toString() {
        return mRatFactor.toString() + "*" + mCrFactor.toString();
    }

    /**
     * Convert to readable String.
     * Intended for user output.  Produces exact expression when possible.
     */
    public String toNiceString() {
        if (mCrFactor == CR_ONE || mRatFactor.signum() == 0) {
            return mRatFactor.toNiceString();
        }
        String name = crName(mCrFactor);
        if (name != null) {
            BigInteger bi = BoundedRational.asBigInteger(mRatFactor);
            if (bi != null) {
                if (bi.equals(BigInteger.ONE)) {
                    return name;
                }
                return mRatFactor.toNiceString() + name;
            }
            return "(" + mRatFactor.toNiceString() + ")" + name;
        }
        if (mRatFactor.equals(BoundedRational.ONE)) {
            return mCrFactor.toString();
        }
        return crValue().toString();
    }

    /**
     * Will toNiceString() produce an exact representation?
     */
    public boolean exactlyDisplayable() {
        return crName(mCrFactor) != null;
    }

    // Number of extra bits used in evaluation below to prefer truncation to rounding.
    // Must be <= 30.
    private final static int EXTRA_PREC = 10;

    /*
     * Returns a truncated representation of the result.
     * If exactlyTruncatable(), we round correctly towards zero. Otherwise the resulting digit
     * string may occasionally be rounded up instead.
     * Always includes a decimal point in the result.
     * The result includes n digits to the right of the decimal point.
     * @param n result precision, >= 0
     */
    public String toStringTruncated(int n) {
        if (mCrFactor == CR_ONE || mRatFactor == BoundedRational.ZERO) {
            return mRatFactor.toStringTruncated(n);
        }
        final CR scaled = CR.valueOf(BigInteger.TEN.pow(n)).multiply(crValue());
        boolean negative = false;
        BigInteger intScaled;
        if (exactlyTruncatable()) {
            intScaled = scaled.get_appr(0);
            if (intScaled.signum() < 0) {
                negative = true;
                intScaled = intScaled.negate();
            }
            if (CR.valueOf(intScaled).compareTo(scaled.abs()) > 0) {
                intScaled = intScaled.subtract(BigInteger.ONE);
            }
            check(CR.valueOf(intScaled).compareTo(scaled.abs()) < 0);
        } else {
            // Approximate case.  Exact comparisons are impossible.
            intScaled = scaled.get_appr(-EXTRA_PREC);
            if (intScaled.signum() < 0) {
                negative = true;
                intScaled = intScaled.negate();
            }
            intScaled = intScaled.shiftRight(EXTRA_PREC);
        }
        String digits = intScaled.toString();
        int len = digits.length();
        if (len < n + 1) {
            digits = StringUtils.repeat('0', n + 1 - len) + digits;
            len = n + 1;
        }
        return (negative ? "-" : "") + digits.substring(0, len - n) + "."
                + digits.substring(len - n);
    }

    /*
     * Can we compute correctly truncated approximations of this number?
     */
    public boolean exactlyTruncatable() {
        // If the value is known rational, we can do exact comparisons.
        // If the value is known irrational, then we can safely compare to rational approximations;
        // equality is impossible; hence the comparison must converge.
        // The only problem cases are the ones in which we don't know.
        return mCrFactor == CR_ONE || mRatFactor == BoundedRational.ZERO || definitelyIrrational();
    }

    /**
     * Return a double approximation.
     * TODO: Result is correctly rounded if known to be rational.
     */
    public double doubleValue() {
        if (mCrFactor == CR_ONE) {
            return mRatFactor.doubleValue(); // Hopefully correctly rounded
        } else {
            return crValue().doubleValue(); // Approximately correctly rounded
        }
    }

    public CR crValue() {
        return mRatFactor.crValue().multiply(mCrFactor);
    }

    /**
     * Are this and r exactly comparable?
     */
    public boolean isComparable(UnifiedReal u) {
        // We check for ONE only to speed up the common case.
        // The use of a tolerance here means we can spuriously return false, not true.
        return mCrFactor == u.mCrFactor
                && (isNamed(mCrFactor) || mCrFactor.signum(DEFAULT_COMPARE_TOLERANCE) != 0)
                || mRatFactor.signum() == 0 && u.mRatFactor.signum() == 0
                || definitelyIndependent(mCrFactor, u.mCrFactor)
                || crValue().compareTo(u.crValue(), DEFAULT_COMPARE_TOLERANCE) != 0;
    }

    /**
     * Return +1 if this is greater than r, -1 if this is less than r, or 0 of the two are
     * known to be equal.
     * May diverge if the two are equal and !isComparable(r).
     */
    public int compareTo(UnifiedReal u) {
        if (definitelyZero() && u.definitelyZero()) return 0;
        if (mCrFactor == u.mCrFactor) {
            int signum = mCrFactor.signum();  // Can diverge if mCRFactor == 0.
            return signum * mRatFactor.compareTo(u.mRatFactor);
        }
        return crValue().compareTo(u.crValue());  // Can also diverge.
    }

    /**
     * Return +1 if this is greater than r, -1 if this is less than r, or possibly 0 of the two are
     * within 2^a of each other.
     */
    public int compareTo(UnifiedReal u, int a) {
        if (isComparable(u)) {
            return compareTo(u);
        } else {
            return crValue().compareTo(u.crValue(), a);
        }
    }

    /**
     * Return compareTo(ZERO, a).
     */
    public int signum(int a) {
        return compareTo(ZERO, a);
    }

    /**
     * Return compareTo(ZERO).
     * May diverge for ZERO argument if !isComparable(ZERO).
     */
    public int signum() {
        return compareTo(ZERO);
    }

    /**
     * Equality comparison.  May erroneously return true if values differ by less than 2^a,
     * and !isComparable(u).
     */
    public boolean approxEquals(UnifiedReal u, int a) {
        if (isComparable(u)) {
            if (definitelyIndependent(mCrFactor, u.mCrFactor)
                    && (mRatFactor.signum() != 0 || u.mRatFactor.signum() != 0)) {
                // No need to actually evaluate, though we don't know which is larger.
                return false;
            } else {
                return compareTo(u) == 0;
            }
        }
        return crValue().compareTo(u.crValue(), a) == 0;
    }

    /**
     * Returns true if values are definitely known to be equal, false in all other cases.
     */
    public boolean definitelyEquals(UnifiedReal u) {
        return isComparable(u) && compareTo(u) == 0;
    }

    /**
     * Returns true if values are definitely known not to be equal, false in all other cases.
     * Performs no approximate evaluation.
     */
    public boolean definitelyNotEquals(UnifiedReal u) {
        boolean isNamed = isNamed(mCrFactor);
        boolean uIsNamed = isNamed(u.mCrFactor);
        if (isNamed && uIsNamed) {
            if (definitelyIndependent(mCrFactor, u.mCrFactor)) {
                return mRatFactor.signum() != 0 || u.mRatFactor.signum() != 0;
            } else if (mCrFactor == u.mCrFactor) {
                return !mRatFactor.equals(u.mRatFactor);
            }
            return !mRatFactor.equals(u.mRatFactor);
        }
        if (mRatFactor.signum() == 0) {
            return uIsNamed && u.mRatFactor.signum() != 0;
        }
        if (u.mRatFactor.signum() == 0) {
            return isNamed && mRatFactor.signum() != 0;
        }
        return false;
    }

    // And some slightly faster convenience functions for special cases:

    public boolean definitelyZero() {
        return mRatFactor.signum() == 0;
    }

    /**
     * Can this number be determined to be definitely nonzero without performing approximate
     * evaluation?
     */
    public boolean definitelyNonZero() {
        return isNamed(mCrFactor) && mRatFactor.signum() != 0;
    }

    public boolean definitelyOne() {
        return mCrFactor == CR_ONE && mRatFactor.equals(BoundedRational.ONE);
    }

    /**
     * Return equivalent BoundedRational, if known to exist, null otherwise
     */
    public BoundedRational boundedRationalValue() {
        if (mCrFactor == CR_ONE || mRatFactor.signum() == 0) {
            return mRatFactor;
        }
        return null;
    }

    /**
     * Returns equivalent BigInteger result if it exists, null if not.
     */
    public BigInteger bigIntegerValue() {
        final BoundedRational r = boundedRationalValue();
        return BoundedRational.asBigInteger(r);
    }

    public UnifiedReal add(UnifiedReal u) {
        if (mCrFactor == u.mCrFactor) {
            BoundedRational nRatFactor = BoundedRational.add(mRatFactor, u.mRatFactor);
            if (nRatFactor != null) {
                return new UnifiedReal(nRatFactor, mCrFactor);
            }
        }
        if (definitelyZero()) {
            // Avoid creating new mCrFactor, even if they don't currently match.
            return u;
        }
        if (u.definitelyZero()) {
            return this;
        }
        return new UnifiedReal(crValue().add(u.crValue()));
    }

    public UnifiedReal negate() {
        return new UnifiedReal(BoundedRational.negate(mRatFactor), mCrFactor);
    }

    public UnifiedReal subtract(UnifiedReal u) {
        return add(u.negate());
    }

    public UnifiedReal multiply(UnifiedReal u) {
        // Preserve a preexisting mCrFactor when we can.
        if (mCrFactor == CR_ONE) {
            BoundedRational nRatFactor = BoundedRational.multiply(mRatFactor, u.mRatFactor);
            if (nRatFactor != null) {
                return new UnifiedReal(nRatFactor, u.mCrFactor);
            }
        }
        if (u.mCrFactor == CR_ONE) {
            BoundedRational nRatFactor = BoundedRational.multiply(mRatFactor, u.mRatFactor);
            if (nRatFactor != null) {
                return new UnifiedReal(nRatFactor, mCrFactor);
            }
        }
        if (definitelyZero() || u.definitelyZero()) {
            return ZERO;
        }
        if (mCrFactor == u.mCrFactor) {
            BoundedRational square = getSquare(mCrFactor);
            if (square != null) {
                BoundedRational nRatFactor = BoundedRational.multiply(
                        BoundedRational.multiply(square, mRatFactor), u.mRatFactor);
                if (nRatFactor != null) {
                    return new UnifiedReal(nRatFactor);
                }
            }
        }
        // Probably a bit cheaper to multiply component-wise.
        BoundedRational nRatFactor = BoundedRational.multiply(mRatFactor, u.mRatFactor);
        if (nRatFactor != null) {
            return new UnifiedReal(nRatFactor, mCrFactor.multiply(u.mCrFactor));
        }
        return new UnifiedReal(crValue().multiply(u.crValue()));
    }

    public static class ZeroDivisionException extends ArithmeticException {
        public ZeroDivisionException() {
            super("Division by zero");
        }
    }

    /**
     * Return the reciprocal.
     */
    public UnifiedReal inverse() {
        if (definitelyZero()) {
            throw new ZeroDivisionException();
        }
        BoundedRational square = getSquare(mCrFactor);
        if (square != null) {
            // 1/sqrt(n) = sqrt(n)/n
            BoundedRational nRatFactor = BoundedRational.inverse(
                    BoundedRational.multiply(mRatFactor, square));
            if (nRatFactor != null) {
                return new UnifiedReal(nRatFactor, mCrFactor);
            }
        }
        return new UnifiedReal(BoundedRational.inverse(mRatFactor), mCrFactor.inverse());
    }

    public UnifiedReal divide(UnifiedReal u) {
        if (mCrFactor == u.mCrFactor) {
            if (u.definitelyZero()) {
                throw new ZeroDivisionException();
            }
            BoundedRational nRatFactor = BoundedRational.divide(mRatFactor, u.mRatFactor);
            if (nRatFactor != null) {
                return new UnifiedReal(nRatFactor, CR_ONE);
            }
        }
        return multiply(u.inverse());
    }

    public UnifiedReal sqrt() {
        if (mCrFactor == CR_ONE) {
            BoundedRational ratSqrt;
            // Check for all arguments of the form <perfect rational square> * small_int,
            // where small_int has a known sqrt.  This includes the small_int = 1 case.
            for (int divisor = 1; divisor < sSqrts.length; ++divisor) {
                if (sSqrts[divisor] != null) {
                    ratSqrt = BoundedRational.sqrt(
                            BoundedRational.divide(mRatFactor, new BoundedRational(divisor)));
                    if (ratSqrt != null) {
                        return new UnifiedReal(ratSqrt, sSqrts[divisor]);
                    }
                }
            }
        }
        return new UnifiedReal(crValue().sqrt());
    }

    /**
     * Return (this mod 2pi)/(pi/6) as a BigInteger, or null if that isn't easily possible.
     */
    private BigInteger getPiTwelfths() {
        if (definitelyZero()) return BigInteger.ZERO;
        if (mCrFactor == CR_PI) {
            BigInteger quotient = BoundedRational.asBigInteger(
                    BoundedRational.multiply(mRatFactor, BoundedRational.TWELVE));
            if (quotient == null) {
                return null;
            }
            return quotient.mod(BIG_24);
        }
        return null;
    }

    /**
     * Computer the sin() for an integer multiple n of pi/12, if easily representable.
     * @param n value between 0 and 23 inclusive.
     */
    private static UnifiedReal sinPiTwelfths(int n) {
        if (n >= 12) {
            UnifiedReal negResult = sinPiTwelfths(n - 12);
            return negResult == null ? null : negResult.negate();
        }
        switch (n) {
        case 0:
            return ZERO;
        case 2: // 30 degrees
            return HALF;
        case 3: // 45 degrees
            return HALF_SQRT2;
        case 4: // 60 degrees
            return HALF_SQRT3;
        case 6:
            return ONE;
        case 8:
            return HALF_SQRT3;
        case 9:
            return HALF_SQRT2;
        case 10:
            return HALF;
        default:
            return null;
        }
    }

    public UnifiedReal sin() {
        BigInteger piTwelfths = getPiTwelfths();
        if (piTwelfths != null) {
            UnifiedReal result = sinPiTwelfths(piTwelfths.intValue());
            if (result != null) {
                return result;
            }
        }
        return new UnifiedReal(crValue().sin());
    }

    private static UnifiedReal cosPiTwelfths(int n) {
        int sinArg = n + 6;
        if (sinArg >= 24) {
            sinArg -= 24;
        }
        return sinPiTwelfths(sinArg);
    }

    public UnifiedReal cos() {
        BigInteger piTwelfths = getPiTwelfths();
        if (piTwelfths != null) {
            UnifiedReal result = cosPiTwelfths(piTwelfths.intValue());
            if (result != null) {
                return result;
            }
        }
        return new UnifiedReal(crValue().cos());
    }

    public UnifiedReal tan() {
        BigInteger piTwelfths = getPiTwelfths();
        if (piTwelfths != null) {
            int i = piTwelfths.intValue();
            if (i == 6 || i == 18) {
                throw new ArithmeticException("Tangent undefined");
            }
            UnifiedReal top = sinPiTwelfths(i);
            UnifiedReal bottom = cosPiTwelfths(i);
            if (top != null && bottom != null) {
                return top.divide(bottom);
            }
        }
        return sin().divide(cos());
    }

    // Throw an exception if the argument is definitely out of bounds for asin or acos.
    private void checkAsinDomain() {
        if (isComparable(ONE) && (compareTo(ONE) > 0 || compareTo(MINUS_ONE) < 0)) {
            throw new ArithmeticException("inverse trig argument out of range");
        }
    }

    /**
     * Return asin(n/2).  n is between -2 and 2.
     */
    public static UnifiedReal asinHalves(int n){
        if (n < 0) {
            return (asinHalves(-n).negate());
        }
        switch (n) {
        case 0:
            return ZERO;
        case 1:
            return new UnifiedReal(BoundedRational.SIXTH, CR.PI);
        case 2:
            return new UnifiedReal(BoundedRational.HALF, CR.PI);
        }
        throw new AssertionError("asinHalves: Bad argument");
    }

    /**
     * Return asin of this, assuming this is not an integral multiple of a half.
     */
    public UnifiedReal asinNonHalves()
    {
        if (compareTo(ZERO, -10) < 0) {
            return negate().asinNonHalves().negate();
        }
        if (definitelyEquals(HALF_SQRT2)) {
            return new UnifiedReal(BoundedRational.QUARTER, CR_PI);
        }
        if (definitelyEquals(HALF_SQRT3)) {
            return new UnifiedReal(BoundedRational.THIRD, CR_PI);
        }
        return new UnifiedReal(crValue().asin());
    }

    public UnifiedReal asin() {
        checkAsinDomain();
        final BigInteger halves = multiply(TWO).bigIntegerValue();
        if (halves != null) {
            return asinHalves(halves.intValue());
        }
        if (mCrFactor == CR.ONE || mCrFactor != CR_SQRT2 ||mCrFactor != CR_SQRT3) {
            return asinNonHalves();
        }
        return new UnifiedReal(crValue().asin());
    }

    public UnifiedReal acos() {
        return PI_OVER_2.subtract(asin());
    }

    public UnifiedReal atan() {
        if (compareTo(ZERO, -10) < 0) {
            return negate().atan().negate();
        }
        final BigInteger asBI = bigIntegerValue();
        if (asBI != null && asBI.compareTo(BigInteger.ONE) <= 0) {
            final int asInt = asBI.intValue();
            // These seem to be all rational cases:
            switch (asInt) {
            case 0:
                return ZERO;
            case 1:
                return PI_OVER_4;
            default:
                throw new AssertionError("Impossible r_int");
            }
        }
        if (definitelyEquals(THIRD_SQRT3)) {
            return PI_OVER_6;
        }
        if (definitelyEquals(SQRT3)) {
            return PI_OVER_3;
        }
        return new UnifiedReal(UnaryCRFunction.atanFunction.execute(crValue()));
    }

    private static final BigInteger BIG_TWO = BigInteger.valueOf(2);

    /**
     * Compute an integral power of a constrive real, using the standard recursive algorithm.
     * exp is known to be positive.
     */
    private static CR recursivePow(CR base, BigInteger exp) {
        if (exp.equals(BigInteger.ONE)) {
            return base;
        }
        if (exp.and(BigInteger.ONE).intValue() == 1) {
            return base.multiply(recursivePow(base, exp.subtract(BigInteger.ONE)));
        }
        CR tmp = recursivePow(base, exp.shiftRight(1));
        if (Thread.interrupted()) {
            throw new CR.AbortedException();
        }
        return tmp.multiply(tmp);
    }

    /**
     * Compute an integral power of this.
     * This recurses roughly as deeply as the number of bits in the exponent, and can, in
     * ridiculous cases, result in a stack overflow.
     */
    private UnifiedReal pow(BigInteger exp) {
        if (exp.signum() < 0) {
            return pow(exp.negate()).inverse();
        }
        if (exp.equals(BigInteger.ONE)) {
            return this;
        }
        if (exp.signum() == 0) {
            // Questionable if base has undefined value.  Java.lang.Math.pow() returns 1 anyway,
            // so we do the same.
            return ONE;
        }
        if (mCrFactor == CR_ONE) {
            final BoundedRational ratPow = mRatFactor.pow(exp);
            if (ratPow != null) {
                return new UnifiedReal(mRatFactor.pow(exp));
            }
        }
        BoundedRational square = getSquare(mCrFactor);
        if (square != null) {
            final BoundedRational nRatFactor =
                    BoundedRational.multiply(mRatFactor.pow(exp), square.pow(exp.shiftRight(1)));
            if (nRatFactor != null) {
                if (exp.and(BigInteger.ONE).intValue() == 1) {
                    // Odd power: Multiply by remaining square root.
                    return new UnifiedReal(nRatFactor, mCrFactor);
                } else {
                    return new UnifiedReal(nRatFactor);
                }
            }
        }
        if (signum(DEFAULT_COMPARE_TOLERANCE) > 0) {
            // Safe to take the log. This avoids deep recursion for huge exponents, which
            // may actually make sense here.
            return new UnifiedReal(crValue().ln().multiply(CR.valueOf(exp)).exp());
        } else {
            // Possibly negative base with integer exponent. Use a recursive computation.
            // (Another possible option would be to use the absolute value of the base, and then
            // adjust the sign at the end.  But that would have to be done in the CR
            // implementation.)
            return new UnifiedReal(recursivePow(crValue(), exp));
        }
    }

    public UnifiedReal pow(UnifiedReal expon) {
        if (mCrFactor == CR_E) {
            if (mRatFactor.equals(BoundedRational.ONE)) {
                return expon.exp();
            } else {
                UnifiedReal ratPart = new UnifiedReal(mRatFactor).pow(expon);
                return expon.exp().multiply(ratPart);
            }
        }
        final BoundedRational expAsBR = expon.boundedRationalValue();
        if (expAsBR != null) {
            BigInteger expAsBI = BoundedRational.asBigInteger(expAsBR);
            if (expAsBI != null) {
                return pow(expAsBI);
            } else {
                // Check for exponent that is a multiple of a half.
                expAsBI = BoundedRational.asBigInteger(
                        BoundedRational.multiply(BoundedRational.TWO, expAsBR));
                if (expAsBI != null) {
                    return pow(expAsBI).sqrt();
                }
            }
        }
        return new UnifiedReal(crValue().ln().multiply(expon.crValue()).exp());
    }

    /**
     * Raise the argument to the 16th power.
     */
    private static long pow16(int n) {
        if (n > 10) {
            throw new AssertionError("Unexpexted pow16 argument");
        }
        long result = n*n;
        result *= result;
        result *= result;
        result *= result;
        return result;
    }

    /**
     * Return the integral log with respect to the given base if it exists, 0 otherwise.
     * n is presumed positive.
     */
    private static long getIntLog(BigInteger n, int base) {
        double nAsDouble = n.doubleValue();
        double approx = Math.log(nAsDouble)/Math.log(base);
        // A relatively quick test first.
        // Unfortunately, this doesn't help for values to big to fit in a Double.
        if (!Double.isInfinite(nAsDouble) && Math.abs(approx - Math.rint(approx)) > 1.0e-6) {
            return 0;
        }
        long result = 0;
        BigInteger remaining = n;
        BigInteger bigBase = BigInteger.valueOf(base);
        BigInteger base16th = null;  // base^16, computed lazily
        while (n.mod(bigBase).signum() == 0) {
            if (Thread.interrupted()) {
                throw new CR.AbortedException();
            }
            n = n.divide(bigBase);
            ++result;
            // And try a slightly faster computation for large n:
            if (base16th == null) {
                base16th = BigInteger.valueOf(pow16(base));
            }
            while (n.mod(base16th).signum() == 0) {
                n = n.divide(base16th);
                result += 16;
            }
        }
        if (n.equals(BigInteger.ONE)) {
            return result;
        }
        return 0;
    }

    public UnifiedReal ln() {
        if (isComparable(ZERO)) {
            if (signum() <= 0) {
                throw new ArithmeticException("log(non-positive)");
            }
            int compare1 = compareTo(ONE, DEFAULT_COMPARE_TOLERANCE);
            if (compare1 == 0) {
                if (definitelyEquals(ONE)) {
                    return ZERO;
                }
            } else if (compare1 < 0) {
                return inverse().ln().negate();
            }
            final BigInteger bi = BoundedRational.asBigInteger(mRatFactor);
            if (bi != null) {
                if (mCrFactor == CR_ONE) {
                    // Check for a power of a small integer.  We can use sLogs[] to return
                    // a more useful answer for those.
                    for (int i = 0; i < sLogs.length; ++i) {
                        if (sLogs[i] != null) {
                            long intLog = getIntLog(bi, i);
                            if (intLog != 0) {
                                return new UnifiedReal(new BoundedRational(intLog), sLogs[i]);
                            }
                        }
                    }
                } else {
                    // Check for n^k * sqrt(n), for which we can also return a more useful answer.
                    BoundedRational square = getSquare(mCrFactor);
                    if (square != null) {
                        int intSquare = square.intValue();
                        if (sLogs[intSquare] != null) {
                            long intLog = getIntLog(bi, intSquare);
                            if (intLog != 0) {
                                BoundedRational nRatFactor =
                                        BoundedRational.add(new BoundedRational(intLog),
                                        BoundedRational.HALF);
                                if (nRatFactor != null) {
                                    return new UnifiedReal(nRatFactor, sLogs[intSquare]);
                                }
                            }
                        }
                    }
                }
            }
        }
        return new UnifiedReal(crValue().ln());
    }

    public UnifiedReal exp() {
        if (definitelyEquals(ZERO)) {
            return ONE;
        }
        if (definitelyEquals(ONE)) {
            // Avoid redundant computations, and ensure we recognize all instances as equal.
            return E;
        }
        final BoundedRational crExp = getExp(mCrFactor);
        if (crExp != null) {
            if (mRatFactor.signum() < 0) {
                return negate().exp().inverse();
            }
            boolean needSqrt = false;
            BoundedRational ratExponent = mRatFactor;
            BigInteger asBI = BoundedRational.asBigInteger(ratExponent);
            if (asBI == null) {
                // check for multiple of one half.
                needSqrt = true;
                ratExponent = BoundedRational.multiply(ratExponent, BoundedRational.TWO);
            }
            BoundedRational nRatFactor = BoundedRational.pow(crExp, ratExponent);
            if (nRatFactor != null) {
                UnifiedReal result = new UnifiedReal(nRatFactor);
                if (needSqrt) {
                    result = result.sqrt();
                }
                return result;
            }
        }
        return new UnifiedReal(crValue().exp());
    }


    /**
     * Generalized factorial.
     * Compute n * (n - step) * (n - 2 * step) * etc.  This can be used to compute factorial a bit
     * faster, especially if BigInteger uses sub-quadratic multiplication.
     */
    private static BigInteger genFactorial(long n, long step) {
        if (n > 4 * step) {
            BigInteger prod1 = genFactorial(n, 2 * step);
            if (Thread.interrupted()) {
                throw new CR.AbortedException();
            }
            BigInteger prod2 = genFactorial(n - step, 2 * step);
            if (Thread.interrupted()) {
                throw new CR.AbortedException();
            }
            return prod1.multiply(prod2);
        } else {
            if (n == 0) {
                return BigInteger.ONE;
            }
            BigInteger res = BigInteger.valueOf(n);
            for (long i = n - step; i > 1; i -= step) {
                res = res.multiply(BigInteger.valueOf(i));
            }
            return res;
        }
    }


    /**
     * Factorial function.
     * Fails if argument is clearly not an integer.
     * May round to nearest integer if value is close.
     */
    public UnifiedReal fact() {
        BigInteger asBI = bigIntegerValue();
        if (asBI == null) {
            asBI = crValue().get_appr(0);  // Correct if it was an integer.
            if (!approxEquals(new UnifiedReal(asBI), DEFAULT_COMPARE_TOLERANCE)) {
                throw new ArithmeticException("Non-integral factorial argument");
            }
        }
        if (asBI.signum() < 0) {
            throw new ArithmeticException("Negative factorial argument");
        }
        if (asBI.bitLength() > 20) {
            // Will fail.  LongValue() may not work. Punt now.
            throw new ArithmeticException("Factorial argument too big");
        }
        BigInteger biResult = genFactorial(asBI.longValue(), 1);
        BoundedRational nRatFactor = new BoundedRational(biResult);
        return new UnifiedReal(nRatFactor);
    }

    /**
     * Return the number of decimal digits to the right of the decimal point required to represent
     * the argument exactly.
     * Return Integer.MAX_VALUE if that's not possible.  Never returns a value less than zero, even
     * if r is a power of ten.
     */
    public int digitsRequired() {
        if (mCrFactor == CR_ONE || mRatFactor.signum() == 0) {
            return BoundedRational.digitsRequired(mRatFactor);
        } else {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Return an upper bound on the number of leading zero bits.
     * These are the number of 0 bits
     * to the right of the binary point and to the left of the most significant digit.
     * Return Integer.MAX_VALUE if we cannot bound it.
     */
    public int leadingBinaryZeroes() {
        if (isNamed(mCrFactor)) {
            // Only ln(2) is smaller than one, and could possibly add one zero bit.
            // Adding 3 gives us a somewhat sloppy upper bound.
            final int wholeBits = mRatFactor.wholeNumberBits();
            if (wholeBits == Integer.MIN_VALUE) {
                return Integer.MAX_VALUE;
            }
            if (wholeBits >= 3) {
                return 0;
            } else {
                return -wholeBits + 3;
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Is the number of bits to the left of the decimal point greater than bound?
     * The result is inexact: We roughly approximate the whole number bits.
     */
    public boolean approxWholeNumberBitsGreaterThan(int bound) {
        if (isNamed(mCrFactor)) {
            return mRatFactor.wholeNumberBits() > bound;
        } else {
            return crValue().get_appr(bound - 2).bitLength() > 2;
        }
    }
}
