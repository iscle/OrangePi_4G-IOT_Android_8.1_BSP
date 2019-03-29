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

package android.util.cts;

import static org.junit.Assert.assertEquals;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Range;
import android.util.Rational;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RangeTest {

    @Test
    public void testConstructor() {
        // Trivial, same range
        Range<Integer> intRange = new Range<>(1, 1);

        verifyLower(intRange, 1);
        verifyUpper(intRange, 1);

        // Different values in range
        Range<Integer> intRange2 = new Range<>(100, 200);
        verifyLower(intRange2, 100);
        verifyUpper(intRange2, 200);

        Range<Float> floatRange = new Range<>(Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY);
        verifyLower(floatRange, Float.NEGATIVE_INFINITY);
        verifyUpper(floatRange, Float.POSITIVE_INFINITY);
    }

    @Test(expected=NullPointerException.class)
    public void testIntegerRangeNullBoth() {
        new Range<Integer>(null, null);
    }

    @Test(expected=NullPointerException.class)
    public void testIntegerRangeNullLower() {
        new Range<>(null, 0);
    }

    @Test(expected=NullPointerException.class)
    public void testIntegerRangeNullUpper() {
        new Range<>(0, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testIntegerRangeLowerMoreThanHigher() {
        new Range<>(50, -50);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testFloatRangeLowerMoreThanHigher() {
        new Range<>(0.0f, Float.NEGATIVE_INFINITY);
    }

    @Test
    public void testEquals() {
        Range<Float> oneHalf = Range.create(1.0f, 2.0f);
        Range<Float> oneHalf2 = new Range<>(1.0f, 2.0f);
        assertEquals(oneHalf, oneHalf2);
        verifyHashCodeEquals(oneHalf, oneHalf2);

        Range<Float> twoThirds = new Range<>(2.0f, 3.0f);
        Range<Float> twoThirds2 = Range.create(2.0f, 3.0f);
        assertEquals(twoThirds, twoThirds2);
        verifyHashCodeEquals(twoThirds, twoThirds2);

        Range<Rational> negativeOneTenthPositiveOneTenth =
                new Range<>(new Rational(-1, 10), new Rational(1, 10));
        Range<Rational> negativeOneTenthPositiveOneTenth2 =
                Range.create(new Rational(-1, 10), new Rational(1, 10));
        assertEquals(negativeOneTenthPositiveOneTenth, negativeOneTenthPositiveOneTenth2);
        verifyHashCodeEquals(negativeOneTenthPositiveOneTenth, negativeOneTenthPositiveOneTenth2);
    }

    @Test
    public void testInRange() {
        Range<Integer> hundredOneTwo = Range.create(100, 200);

        verifyInRange(hundredOneTwo, 100);
        verifyInRange(hundredOneTwo, 200);
        verifyInRange(hundredOneTwo, 150);
        verifyOutOfRange(hundredOneTwo, 99);
        verifyOutOfRange(hundredOneTwo, 201);
        verifyOutOfRange(hundredOneTwo, 100000);

        Range<Float> infinities = Range.create(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);

        verifyInRange(infinities, Float.NEGATIVE_INFINITY);
        verifyInRange(infinities, Float.POSITIVE_INFINITY);
        verifyInRange(infinities, 0.0f);
        verifyOutOfRange(infinities, Float.NaN);

        Range<Rational> negativeOneTenthPositiveOneTenth =
                new Range<>(new Rational(-1, 10), new Rational(1, 10));
        verifyInRange(negativeOneTenthPositiveOneTenth, new Rational(-1, 10));
        verifyInRange(negativeOneTenthPositiveOneTenth, new Rational(1, 10));
        verifyInRange(negativeOneTenthPositiveOneTenth, Rational.ZERO);
        verifyOutOfRange(negativeOneTenthPositiveOneTenth, new Rational(-100, 1));
        verifyOutOfRange(negativeOneTenthPositiveOneTenth, new Rational(100, 1));
    }

    private static <T extends Comparable<? super T>> void verifyInRange(Range<T> object, T needle) {
        verifyAction("in-range", object, needle, true, object.contains(needle));
    }

    private static <T extends Comparable<? super T>> void verifyOutOfRange(Range<T> object,
            T needle) {
        verifyAction("out-of-range", object, needle, false, object.contains(needle));
    }

    private static <T extends Comparable<? super T>> void verifyUpper(Range<T> object, T expected) {
        verifyAction("upper", object, expected, object.getUpper());
    }

    private static <T extends Comparable<? super T>> void verifyLower(Range<T> object, T expected) {
        verifyAction("lower", object, expected, object.getLower());
    }

    private static <T, T2> void verifyAction(String action, T object, T2 expected,
            T2 actual) {
        assertEquals("Expected " + object + " " + action + " to be ",
                expected, actual);
    }

    private static <T, T2> void verifyAction(String action, T object, T2 needle, boolean expected,
            boolean actual) {
        String expectedMessage = expected ? action : ("not " + action);
        assertEquals("Expected " + needle + " to be " + expectedMessage + " of " + object,
                expected, actual);
    }

    private static <T extends Comparable<? super T>> void verifyHashCodeEquals(
            Range<T> left, Range<T> right) {
        assertEquals("Left hash code for " + left +
                " expected to be equal to right hash code for " + right,
                left.hashCode(), right.hashCode());
    }
}
