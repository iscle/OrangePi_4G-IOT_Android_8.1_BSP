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

package android.util.cts;

import android.util.Half;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static android.util.Half.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class HalfTest {
    private static void assertShortEquals(short a, short b) {
        assertEquals((long) (a & 0xffff), (long) (b & 0xffff));
    }

    private static void assertShortEquals(int a, short b) {
        assertEquals((long) (a & 0xffff), (long) (b & 0xffff));
    }

    @Test
    public void singleToHalf() {
        // Zeroes, NaN and infinities
        assertShortEquals(POSITIVE_ZERO, toHalf(0.0f));
        assertShortEquals(NEGATIVE_ZERO, toHalf(-0.0f));
        assertShortEquals(NaN, toHalf(Float.NaN));
        assertShortEquals(POSITIVE_INFINITY, toHalf(Float.POSITIVE_INFINITY));
        assertShortEquals(NEGATIVE_INFINITY, toHalf(Float.NEGATIVE_INFINITY));
        // Known values
        assertShortEquals(0x3c01, toHalf(1.0009765625f));
        assertShortEquals(0xc000, toHalf(-2.0f));
        assertShortEquals(0x0400, toHalf(6.10352e-5f));
        assertShortEquals(0x7bff, toHalf(65504.0f));
        assertShortEquals(0x3555, toHalf(1.0f / 3.0f));
        // Denormals
        assertShortEquals(0x03ff, toHalf(6.09756e-5f));
        assertShortEquals(MIN_VALUE, toHalf(5.96046e-8f));
        assertShortEquals(0x83ff, toHalf(-6.09756e-5f));
        assertShortEquals(0x8001, toHalf(-5.96046e-8f));
        // Denormals (flushed to +/-0)
        assertShortEquals(POSITIVE_ZERO, toHalf(5.96046e-9f));
        assertShortEquals(NEGATIVE_ZERO, toHalf(-5.96046e-9f));
    }

    @Test
    public void halfToSingle() {
        // Zeroes, NaN and infinities
        assertEquals(0.0f, toFloat(toHalf(0.0f)), 1e-6f);
        assertEquals(-0.0f, toFloat(toHalf(-0.0f)), 1e-6f);
        assertEquals(Float.NaN, toFloat(toHalf(Float.NaN)), 1e-6f);
        assertEquals(Float.POSITIVE_INFINITY, toFloat(toHalf(Float.POSITIVE_INFINITY)), 1e-6f);
        assertEquals(Float.NEGATIVE_INFINITY, toFloat(toHalf(Float.NEGATIVE_INFINITY)), 1e-6f);
        // Known values
        assertEquals(1.0009765625f, toFloat(toHalf(1.0009765625f)), 1e-6f);
        assertEquals(-2.0f, toFloat(toHalf(-2.0f)), 1e-6f);
        assertEquals(6.1035156e-5f, toFloat(toHalf(6.10352e-5f)), 1e-6f); // Inexact
        assertEquals(65504.0f, toFloat(toHalf(65504.0f)), 1e-6f);
        assertEquals(0.33325195f, toFloat(toHalf(1.0f / 3.0f)), 1e-6f); // Inexact
        // Denormals (flushed to +/-0)
        assertEquals(6.097555e-5f, toFloat(toHalf(6.09756e-5f)), 1e-6f);
        assertEquals(5.9604645e-8f, toFloat(toHalf(5.96046e-8f)), 1e-9f);
        assertEquals(-6.097555e-5f, toFloat(toHalf(-6.09756e-5f)), 1e-6f);
        assertEquals(-5.9604645e-8f, toFloat(toHalf(-5.96046e-8f)), 1e-9f);
    }

    @Test
    public void hexString() {
        assertEquals("NaN", toHexString(NaN));
        assertEquals("Infinity", toHexString(POSITIVE_INFINITY));
        assertEquals("-Infinity", toHexString(NEGATIVE_INFINITY));
        assertEquals("0x0.0p0", toHexString(POSITIVE_ZERO));
        assertEquals("-0x0.0p0", toHexString(NEGATIVE_ZERO));
        assertEquals("0x1.0p0", toHexString(toHalf(1.0f)));
        assertEquals("-0x1.0p0", toHexString(toHalf(-1.0f)));
        assertEquals("0x1.0p1", toHexString(toHalf(2.0f)));
        assertEquals("0x1.0p8", toHexString(toHalf(256.0f)));
        assertEquals("0x1.0p-1", toHexString(toHalf(0.5f)));
        assertEquals("0x1.0p-2", toHexString(toHalf(0.25f)));
        assertEquals("0x1.3ffp15", toHexString(MAX_VALUE));
        assertEquals("0x0.1p-14", toHexString(MIN_VALUE));
        assertEquals("0x1.0p-14", toHexString(MIN_NORMAL));
        assertEquals("-0x1.3ffp15", toHexString(LOWEST_VALUE));
    }

    @Test
    public void string() {
        assertEquals("NaN", Half.toString(NaN));
        assertEquals("Infinity", Half.toString(POSITIVE_INFINITY));
        assertEquals("-Infinity", Half.toString(NEGATIVE_INFINITY));
        assertEquals("0.0", Half.toString(POSITIVE_ZERO));
        assertEquals("-0.0", Half.toString(NEGATIVE_ZERO));
        assertEquals("1.0", Half.toString(toHalf(1.0f)));
        assertEquals("-1.0", Half.toString(toHalf(-1.0f)));
        assertEquals("2.0", Half.toString(toHalf(2.0f)));
        assertEquals("256.0", Half.toString(toHalf(256.0f)));
        assertEquals("0.5", Half.toString(toHalf(0.5f)));
        assertEquals("0.25", Half.toString(toHalf(0.25f)));
        assertEquals("65504.0", Half.toString(MAX_VALUE));
        assertEquals("5.9604645E-8", Half.toString(MIN_VALUE));
        assertEquals("6.1035156E-5", Half.toString(MIN_NORMAL));
        assertEquals("-65504.0", Half.toString(LOWEST_VALUE));
    }

    @Test
    public void exponent() {
        assertEquals(16, getExponent(POSITIVE_INFINITY));
        assertEquals(16, getExponent(NEGATIVE_INFINITY));
        assertEquals(16, getExponent(NaN));
        assertEquals(-15, getExponent(POSITIVE_ZERO));
        assertEquals(-15, getExponent(NEGATIVE_ZERO));
        assertEquals(0, getExponent(toHalf(1.0f)));
        assertEquals(-4, getExponent(toHalf(0.1f)));
        assertEquals(-10, getExponent(toHalf(0.001f)));
        assertEquals(7, getExponent(toHalf(128.8f)));
    }

    @Test
    public void significand() {
        assertEquals(0, getSignificand(POSITIVE_INFINITY));
        assertEquals(0, getSignificand(NEGATIVE_INFINITY));
        assertEquals(512, getSignificand(NaN));
        assertEquals(0, getSignificand(POSITIVE_ZERO));
        assertEquals(0, getSignificand(NEGATIVE_ZERO));
        assertEquals(614, getSignificand(toHalf(0.1f)));
        assertEquals(25, getSignificand(toHalf(0.001f)));
        assertEquals(6, getSignificand(toHalf(128.8f)));
    }

    @Test
    public void sign() {
        assertEquals(1, getSign(POSITIVE_INFINITY));
        assertEquals(-1, getSign(NEGATIVE_INFINITY));
        assertEquals(1, getSign(POSITIVE_ZERO));
        assertEquals(-1, getSign(NEGATIVE_ZERO));
        assertEquals(1, getSign(NaN));
        assertEquals(1, getSign(toHalf(12.4f)));
        assertEquals(-1, getSign(toHalf(-12.4f)));
    }

    @Test
    public void isInfinite() {
        assertTrue(Half.isInfinite(POSITIVE_INFINITY));
        assertTrue(Half.isInfinite(NEGATIVE_INFINITY));
        assertFalse(Half.isInfinite(POSITIVE_ZERO));
        assertFalse(Half.isInfinite(NEGATIVE_ZERO));
        assertFalse(Half.isInfinite(NaN));
        assertFalse(Half.isInfinite(MAX_VALUE));
        assertFalse(Half.isInfinite(LOWEST_VALUE));
        assertFalse(Half.isInfinite(toHalf(-128.3f)));
        assertFalse(Half.isInfinite(toHalf(128.3f)));
    }

    @Test
    public void isNaN() {
        assertFalse(Half.isNaN(POSITIVE_INFINITY));
        assertFalse(Half.isNaN(NEGATIVE_INFINITY));
        assertFalse(Half.isNaN(POSITIVE_ZERO));
        assertFalse(Half.isNaN(NEGATIVE_ZERO));
        assertTrue(Half.isNaN(NaN));
        assertTrue(Half.isNaN((short) 0x7c01));
        assertTrue(Half.isNaN((short) 0x7c18));
        assertTrue(Half.isNaN((short) 0xfc01));
        assertTrue(Half.isNaN((short) 0xfc98));
        assertFalse(Half.isNaN(MAX_VALUE));
        assertFalse(Half.isNaN(LOWEST_VALUE));
        assertFalse(Half.isNaN(toHalf(-128.3f)));
        assertFalse(Half.isNaN(toHalf(128.3f)));
    }

    @Test
    public void isNormalized() {
        assertFalse(Half.isNormalized(POSITIVE_INFINITY));
        assertFalse(Half.isNormalized(NEGATIVE_INFINITY));
        assertFalse(Half.isNormalized(POSITIVE_ZERO));
        assertFalse(Half.isNormalized(NEGATIVE_ZERO));
        assertFalse(Half.isNormalized(NaN));
        assertTrue(Half.isNormalized(MAX_VALUE));
        assertTrue(Half.isNormalized(MIN_NORMAL));
        assertTrue(Half.isNormalized(LOWEST_VALUE));
        assertTrue(Half.isNormalized(toHalf(-128.3f)));
        assertTrue(Half.isNormalized(toHalf(128.3f)));
        assertTrue(Half.isNormalized(toHalf(0.3456f)));
        assertFalse(Half.isNormalized(MIN_VALUE));
        assertFalse(Half.isNormalized((short) 0x3ff));
        assertFalse(Half.isNormalized((short) 0x200));
        assertFalse(Half.isNormalized((short) 0x100));
    }

    @Test
    public void abs() {
        assertShortEquals(POSITIVE_INFINITY, Half.abs(POSITIVE_INFINITY));
        assertShortEquals(POSITIVE_INFINITY, Half.abs(NEGATIVE_INFINITY));
        assertShortEquals(POSITIVE_ZERO, Half.abs(POSITIVE_ZERO));
        assertShortEquals(POSITIVE_ZERO, Half.abs(NEGATIVE_ZERO));
        assertShortEquals(NaN, Half.abs(NaN));
        assertShortEquals(MAX_VALUE, Half.abs(LOWEST_VALUE));
        assertShortEquals(toHalf(12.12345f), Half.abs(toHalf(-12.12345f)));
        assertShortEquals(toHalf(12.12345f), Half.abs(toHalf( 12.12345f)));
    }

    @Test
    public void ceil() {
        assertShortEquals(POSITIVE_INFINITY, Half.ceil(POSITIVE_INFINITY));
        assertShortEquals(NEGATIVE_INFINITY, Half.ceil(NEGATIVE_INFINITY));
        assertShortEquals(POSITIVE_ZERO, Half.ceil(POSITIVE_ZERO));
        assertShortEquals(NEGATIVE_ZERO, Half.ceil(NEGATIVE_ZERO));
        assertShortEquals(NaN, Half.ceil(NaN));
        assertShortEquals(LOWEST_VALUE, Half.ceil(LOWEST_VALUE));
        assertEquals(1.0f, toFloat(Half.ceil(MIN_NORMAL)), 1e-6f);
        assertEquals(1.0f, toFloat(Half.ceil((short) 0x3ff)), 1e-6f);
        assertEquals(1.0f, toFloat(Half.ceil(toHalf(0.2f))), 1e-6f);
        assertShortEquals(NEGATIVE_ZERO, Half.ceil(toHalf(-0.2f)));
        assertEquals(1.0f, toFloat(Half.ceil(toHalf(0.7f))), 1e-6f);
        assertShortEquals(NEGATIVE_ZERO, Half.ceil(toHalf(-0.7f)));
        assertEquals(125.0f, toFloat(Half.ceil(toHalf(124.7f))), 1e-6f);
        assertEquals(-124.0f, toFloat(Half.ceil(toHalf(-124.7f))), 1e-6f);
        assertEquals(125.0f, toFloat(Half.ceil(toHalf(124.2f))), 1e-6f);
        assertEquals(-124.0f, toFloat(Half.ceil(toHalf(-124.2f))), 1e-6f);
    }

    @Test
    public void copySign() {
        assertShortEquals(toHalf(7.5f), Half.copySign(toHalf(-7.5f), POSITIVE_INFINITY));
        assertShortEquals(toHalf(7.5f), Half.copySign(toHalf(-7.5f), POSITIVE_ZERO));
        assertShortEquals(toHalf(-7.5f), Half.copySign(toHalf(7.5f), NEGATIVE_INFINITY));
        assertShortEquals(toHalf(-7.5f), Half.copySign(toHalf(7.5f), NEGATIVE_ZERO));
        assertShortEquals(toHalf(7.5f), Half.copySign(toHalf(7.5f), NaN));
        assertShortEquals(toHalf(7.5f), Half.copySign(toHalf(7.5f), toHalf(12.4f)));
        assertShortEquals(toHalf(-7.5f), Half.copySign(toHalf(7.5f), toHalf(-12.4f)));
    }

    @Test
    public void equals() {
        assertTrue(Half.equals(POSITIVE_INFINITY, POSITIVE_INFINITY));
        assertTrue(Half.equals(NEGATIVE_INFINITY, NEGATIVE_INFINITY));
        assertTrue(Half.equals(POSITIVE_ZERO, POSITIVE_ZERO));
        assertTrue(Half.equals(NEGATIVE_ZERO, NEGATIVE_ZERO));
        assertTrue(Half.equals(POSITIVE_ZERO, NEGATIVE_ZERO));
        assertFalse(Half.equals(NaN, toHalf(12.4f)));
        assertFalse(Half.equals(toHalf(12.4f), NaN));
        assertFalse(Half.equals(NaN, NaN));
        assertTrue(Half.equals(toHalf(12.4f), toHalf(12.4f)));
        assertTrue(Half.equals(toHalf(-12.4f), toHalf(-12.4f)));
        assertFalse(Half.equals(toHalf(12.4f), toHalf(0.7f)));

        //noinspection UnnecessaryBoxing
        assertNotEquals(Half.valueOf(0.0f), Float.valueOf(0.0f));
        assertEquals(Half.valueOf(NaN), Half.valueOf((short) 0x7c01)); // NaN, NaN
        assertEquals(Half.valueOf(NaN), Half.valueOf((short) 0xfc98)); // NaN, NaN

        assertEquals(Half.valueOf(POSITIVE_INFINITY), Half.valueOf(POSITIVE_INFINITY));
        assertEquals(Half.valueOf(NEGATIVE_INFINITY), Half.valueOf(NEGATIVE_INFINITY));
        assertEquals(Half.valueOf(POSITIVE_ZERO), Half.valueOf(POSITIVE_ZERO));
        assertEquals(Half.valueOf(NEGATIVE_ZERO), Half.valueOf(NEGATIVE_ZERO));
        assertNotEquals(Half.valueOf(POSITIVE_ZERO), Half.valueOf(NEGATIVE_ZERO));
        assertNotEquals(Half.valueOf(NaN), Half.valueOf(12.4f));
        assertNotEquals(Half.valueOf(12.4f), Half.valueOf(NaN));
        assertEquals(Half.valueOf(12.4f), Half.valueOf(12.4f));
        assertEquals(Half.valueOf(-12.4f), Half.valueOf(-12.4f));
        assertNotEquals(Half.valueOf(12.4f), Half.valueOf(0.7f));
    }

    @Test
    public void floor() {
        assertShortEquals(POSITIVE_INFINITY, Half.floor(POSITIVE_INFINITY));
        assertShortEquals(NEGATIVE_INFINITY, Half.floor(NEGATIVE_INFINITY));
        assertShortEquals(POSITIVE_ZERO, Half.floor(POSITIVE_ZERO));
        assertShortEquals(NEGATIVE_ZERO, Half.floor(NEGATIVE_ZERO));
        assertShortEquals(NaN, Half.floor(NaN));
        assertShortEquals(LOWEST_VALUE, Half.floor(LOWEST_VALUE));
        assertShortEquals(POSITIVE_ZERO, Half.floor(MIN_NORMAL));
        assertShortEquals(POSITIVE_ZERO, Half.floor((short) 0x3ff));
        assertShortEquals(POSITIVE_ZERO, Half.floor(toHalf(0.2f)));
        assertEquals(-1.0f, toFloat(Half.floor(toHalf(-0.2f))), 1e-6f);
        assertEquals(-1.0f, toFloat(Half.floor(toHalf(-0.7f))), 1e-6f);
        assertShortEquals(POSITIVE_ZERO, Half.floor(toHalf(0.7f)));
        assertEquals(124.0f, toFloat(Half.floor(toHalf(124.7f))), 1e-6f);
        assertEquals(-125.0f, toFloat(Half.floor(toHalf(-124.7f))), 1e-6f);
        assertEquals(124.0f, toFloat(Half.floor(toHalf(124.2f))), 1e-6f);
        assertEquals(-125.0f, toFloat(Half.floor(toHalf(-124.2f))), 1e-6f);
    }

    @Test
    public void round() {
        assertShortEquals(POSITIVE_INFINITY, Half.round(POSITIVE_INFINITY));
        assertShortEquals(NEGATIVE_INFINITY, Half.round(NEGATIVE_INFINITY));
        assertShortEquals(POSITIVE_ZERO, Half.round(POSITIVE_ZERO));
        assertShortEquals(NEGATIVE_ZERO, Half.round(NEGATIVE_ZERO));
        assertShortEquals(NaN, Half.round(NaN));
        assertShortEquals(LOWEST_VALUE, Half.round(LOWEST_VALUE));
        assertShortEquals(POSITIVE_ZERO, Half.round(MIN_VALUE));
        assertShortEquals(POSITIVE_ZERO, Half.round((short) 0x200));
        assertShortEquals(POSITIVE_ZERO, Half.round((short) 0x3ff));
        assertShortEquals(POSITIVE_ZERO, Half.round(toHalf(0.2f)));
        assertShortEquals(NEGATIVE_ZERO, Half.round(toHalf(-0.2f)));
        assertEquals(1.0f, toFloat(Half.round(toHalf(0.7f))), 1e-6f);
        assertEquals(-1.0f, toFloat(Half.round(toHalf(-0.7f))), 1e-6f);
        assertEquals(1.0f, toFloat(Half.round(toHalf(0.5f))), 1e-6f);
        assertEquals(-1.0f, toFloat(Half.round(toHalf(-0.5f))), 1e-6f);
        assertEquals(125.0f, toFloat(Half.round(toHalf(124.7f))), 1e-6f);
        assertEquals(-125.0f, toFloat(Half.round(toHalf(-124.7f))), 1e-6f);
        assertEquals(124.0f, toFloat(Half.round(toHalf(124.2f))), 1e-6f);
        assertEquals(-124.0f, toFloat(Half.round(toHalf(-124.2f))), 1e-6f);
    }

    @Test
    public void trunc() {
        assertShortEquals(POSITIVE_INFINITY, Half.trunc(POSITIVE_INFINITY));
        assertShortEquals(NEGATIVE_INFINITY, Half.trunc(NEGATIVE_INFINITY));
        assertShortEquals(POSITIVE_ZERO, Half.trunc(POSITIVE_ZERO));
        assertShortEquals(NEGATIVE_ZERO, Half.trunc(NEGATIVE_ZERO));
        assertShortEquals(NaN, Half.trunc(NaN));
        assertShortEquals(LOWEST_VALUE, Half.trunc(LOWEST_VALUE));
        assertShortEquals(POSITIVE_ZERO, Half.trunc(toHalf(0.2f)));
        assertShortEquals(NEGATIVE_ZERO, Half.trunc(toHalf(-0.2f)));
        assertEquals(0.0f, toFloat(Half.trunc(toHalf(0.7f))), 1e-6f);
        assertEquals(-0.0f, toFloat(Half.trunc(toHalf(-0.7f))), 1e-6f);
        assertEquals(124.0f, toFloat(Half.trunc(toHalf(124.7f))), 1e-6f);
        assertEquals(-124.0f, toFloat(Half.trunc(toHalf(-124.7f))), 1e-6f);
        assertEquals(124.0f, toFloat(Half.trunc(toHalf(124.2f))), 1e-6f);
        assertEquals(-124.0f, toFloat(Half.trunc(toHalf(-124.2f))), 1e-6f);
    }

    @Test
    public void less() {
        assertTrue(Half.less(NEGATIVE_INFINITY, POSITIVE_INFINITY));
        assertTrue(Half.less(MAX_VALUE, POSITIVE_INFINITY));
        assertFalse(Half.less(POSITIVE_INFINITY, MAX_VALUE));
        assertFalse(Half.less(LOWEST_VALUE, NEGATIVE_INFINITY));
        assertTrue(Half.less(NEGATIVE_INFINITY, LOWEST_VALUE));
        assertFalse(Half.less(POSITIVE_ZERO, NEGATIVE_ZERO));
        assertFalse(Half.less(NEGATIVE_ZERO, POSITIVE_ZERO));
        assertFalse(Half.less(NaN, toHalf(12.3f)));
        assertFalse(Half.less(toHalf(12.3f), NaN));
        assertTrue(Half.less(MIN_VALUE, MIN_NORMAL));
        assertFalse(Half.less(MIN_NORMAL, MIN_VALUE));
        assertTrue(Half.less(toHalf(12.3f), toHalf(12.4f)));
        assertFalse(Half.less(toHalf(12.4f), toHalf(12.3f)));
        assertFalse(Half.less(toHalf(-12.3f), toHalf(-12.4f)));
        assertTrue(Half.less(toHalf(-12.4f), toHalf(-12.3f)));
        assertTrue(Half.less(MIN_VALUE, (short) 0x3ff));
    }

    @Test
    public void lessEquals() {
        assertTrue(Half.less(NEGATIVE_INFINITY, POSITIVE_INFINITY));
        assertTrue(Half.lessEquals(MAX_VALUE, POSITIVE_INFINITY));
        assertFalse(Half.lessEquals(POSITIVE_INFINITY, MAX_VALUE));
        assertFalse(Half.lessEquals(LOWEST_VALUE, NEGATIVE_INFINITY));
        assertTrue(Half.lessEquals(NEGATIVE_INFINITY, LOWEST_VALUE));
        assertTrue(Half.lessEquals(POSITIVE_ZERO, NEGATIVE_ZERO));
        assertTrue(Half.lessEquals(NEGATIVE_ZERO, POSITIVE_ZERO));
        assertFalse(Half.lessEquals(NaN, toHalf(12.3f)));
        assertFalse(Half.lessEquals(toHalf(12.3f), NaN));
        assertTrue(Half.lessEquals(MIN_VALUE, MIN_NORMAL));
        assertFalse(Half.lessEquals(MIN_NORMAL, MIN_VALUE));
        assertTrue(Half.lessEquals(toHalf(12.3f), toHalf(12.4f)));
        assertFalse(Half.lessEquals(toHalf(12.4f), toHalf(12.3f)));
        assertFalse(Half.lessEquals(toHalf(-12.3f), toHalf(-12.4f)));
        assertTrue(Half.lessEquals(toHalf(-12.4f), toHalf(-12.3f)));
        assertTrue(Half.less(MIN_VALUE, (short) 0x3ff));
        assertTrue(Half.lessEquals(NEGATIVE_INFINITY, NEGATIVE_INFINITY));
        assertTrue(Half.lessEquals(POSITIVE_INFINITY, POSITIVE_INFINITY));
        assertTrue(Half.lessEquals(toHalf(12.12356f), toHalf(12.12356f)));
        assertTrue(Half.lessEquals(toHalf(-12.12356f), toHalf(-12.12356f)));
    }

    @Test
    public void greater() {
        assertTrue(Half.greater(POSITIVE_INFINITY, NEGATIVE_INFINITY));
        assertTrue(Half.greater(POSITIVE_INFINITY, MAX_VALUE));
        assertFalse(Half.greater(MAX_VALUE, POSITIVE_INFINITY));
        assertFalse(Half.greater(NEGATIVE_INFINITY, LOWEST_VALUE));
        assertTrue(Half.greater(LOWEST_VALUE, NEGATIVE_INFINITY));
        assertFalse(Half.greater(NEGATIVE_ZERO, POSITIVE_ZERO));
        assertFalse(Half.greater(POSITIVE_ZERO, NEGATIVE_ZERO));
        assertFalse(Half.greater(toHalf(12.3f), NaN));
        assertFalse(Half.greater(NaN, toHalf(12.3f)));
        assertTrue(Half.greater(MIN_NORMAL, MIN_VALUE));
        assertFalse(Half.greater(MIN_VALUE, MIN_NORMAL));
        assertTrue(Half.greater(toHalf(12.4f), toHalf(12.3f)));
        assertFalse(Half.greater(toHalf(12.3f), toHalf(12.4f)));
        assertFalse(Half.greater(toHalf(-12.4f), toHalf(-12.3f)));
        assertTrue(Half.greater(toHalf(-12.3f), toHalf(-12.4f)));
        assertTrue(Half.greater((short) 0x3ff, MIN_VALUE));
    }

    @Test
    public void greaterEquals() {
        assertTrue(Half.greaterEquals(POSITIVE_INFINITY, NEGATIVE_INFINITY));
        assertTrue(Half.greaterEquals(POSITIVE_INFINITY, MAX_VALUE));
        assertFalse(Half.greaterEquals(MAX_VALUE, POSITIVE_INFINITY));
        assertFalse(Half.greaterEquals(NEGATIVE_INFINITY, LOWEST_VALUE));
        assertTrue(Half.greaterEquals(LOWEST_VALUE, NEGATIVE_INFINITY));
        assertTrue(Half.greaterEquals(NEGATIVE_ZERO, POSITIVE_ZERO));
        assertTrue(Half.greaterEquals(POSITIVE_ZERO, NEGATIVE_ZERO));
        assertFalse(Half.greaterEquals(toHalf(12.3f), NaN));
        assertFalse(Half.greaterEquals(NaN, toHalf(12.3f)));
        assertTrue(Half.greaterEquals(MIN_NORMAL, MIN_VALUE));
        assertFalse(Half.greaterEquals(MIN_VALUE, MIN_NORMAL));
        assertTrue(Half.greaterEquals(toHalf(12.4f), toHalf(12.3f)));
        assertFalse(Half.greaterEquals(toHalf(12.3f), toHalf(12.4f)));
        assertFalse(Half.greaterEquals(toHalf(-12.4f), toHalf(-12.3f)));
        assertTrue(Half.greaterEquals(toHalf(-12.3f), toHalf(-12.4f)));
        assertTrue(Half.greater((short) 0x3ff, MIN_VALUE));
        assertTrue(Half.lessEquals(NEGATIVE_INFINITY, NEGATIVE_INFINITY));
        assertTrue(Half.lessEquals(POSITIVE_INFINITY, POSITIVE_INFINITY));
        assertTrue(Half.lessEquals(toHalf(12.12356f), toHalf(12.12356f)));
        assertTrue(Half.lessEquals(toHalf(-12.12356f), toHalf(-12.12356f)));
    }

    @Test
    public void min() {
        assertShortEquals(NEGATIVE_INFINITY, Half.min(POSITIVE_INFINITY, NEGATIVE_INFINITY));
        assertShortEquals(NEGATIVE_ZERO, Half.min(POSITIVE_ZERO, NEGATIVE_ZERO));
        assertShortEquals(NaN, Half.min(NaN, LOWEST_VALUE));
        assertShortEquals(NaN, Half.min(LOWEST_VALUE, NaN));
        assertShortEquals(NEGATIVE_INFINITY, Half.min(NEGATIVE_INFINITY, LOWEST_VALUE));
        assertShortEquals(MAX_VALUE, Half.min(POSITIVE_INFINITY, MAX_VALUE));
        assertShortEquals(MIN_VALUE, Half.min(MIN_VALUE, MIN_NORMAL));
        assertShortEquals(POSITIVE_ZERO, Half.min(MIN_VALUE, POSITIVE_ZERO));
        assertShortEquals(POSITIVE_ZERO, Half.min(MIN_NORMAL, POSITIVE_ZERO));
        assertShortEquals(toHalf(-3.456f), Half.min(toHalf(-3.456f), toHalf(-3.453f)));
        assertShortEquals(toHalf(3.453f), Half.min(toHalf(3.456f), toHalf(3.453f)));
    }

    @Test
    public void max() {
        assertShortEquals(POSITIVE_INFINITY, Half.max(POSITIVE_INFINITY, NEGATIVE_INFINITY));
        assertShortEquals(POSITIVE_ZERO, Half.max(POSITIVE_ZERO, NEGATIVE_ZERO));
        assertShortEquals(NaN, Half.max(NaN, MAX_VALUE));
        assertShortEquals(NaN, Half.max(MAX_VALUE, NaN));
        assertShortEquals(LOWEST_VALUE, Half.max(NEGATIVE_INFINITY, LOWEST_VALUE));
        assertShortEquals(POSITIVE_INFINITY, Half.max(POSITIVE_INFINITY, MAX_VALUE));
        assertShortEquals(MIN_NORMAL, Half.max(MIN_VALUE, MIN_NORMAL));
        assertShortEquals(MIN_VALUE, Half.max(MIN_VALUE, POSITIVE_ZERO));
        assertShortEquals(MIN_NORMAL, Half.max(MIN_NORMAL, POSITIVE_ZERO));
        assertShortEquals(toHalf(-3.453f), Half.max(toHalf(-3.456f), toHalf(-3.453f)));
        assertShortEquals(toHalf(3.456f), Half.max(toHalf(3.456f), toHalf(3.453f)));
    }

    @Test
    public void numberInterface() {
        assertEquals(12, Half.valueOf(12.57f).byteValue());
        assertEquals(12, Half.valueOf(12.57f).shortValue());
        assertEquals(12, Half.valueOf(12.57f).intValue());
        assertEquals(12, Half.valueOf(12.57f).longValue());
        assertEquals(12.57f, Half.valueOf(12.57f).floatValue(), 1e-3f);
        assertEquals(12.57, Half.valueOf(12.57f).doubleValue(), 1e-3);

        assertEquals(-12, Half.valueOf(-12.57f).byteValue());
        assertEquals(-12, Half.valueOf(-12.57f).shortValue());
        assertEquals(-12, Half.valueOf(-12.57f).intValue());
        assertEquals(-12, Half.valueOf(-12.57f).longValue());
        assertEquals(-12.57f, Half.valueOf(-12.57f).floatValue(), 1e-3f);
        assertEquals(-12.57, Half.valueOf(-12.57f).doubleValue(), 1e-3);

        assertEquals(0, Half.valueOf(POSITIVE_ZERO).byteValue());
        assertEquals(0, Half.valueOf(POSITIVE_ZERO).shortValue());
        assertEquals(0, Half.valueOf(POSITIVE_ZERO).intValue());
        assertEquals(0, Half.valueOf(POSITIVE_ZERO).longValue());
        assertTrue(+0.0f == Half.valueOf(POSITIVE_ZERO).floatValue());
        assertTrue(+0.0 == Half.valueOf(POSITIVE_ZERO).doubleValue());

        assertEquals(0, Half.valueOf(NEGATIVE_ZERO).byteValue());
        assertEquals(0, Half.valueOf(NEGATIVE_ZERO).shortValue());
        assertEquals(0, Half.valueOf(NEGATIVE_ZERO).intValue());
        assertEquals(0, Half.valueOf(NEGATIVE_ZERO).longValue());
        assertTrue(-0.0f == Half.valueOf(NEGATIVE_ZERO).floatValue());
        assertTrue(-0.0 == Half.valueOf(NEGATIVE_ZERO).doubleValue());

        assertEquals(-1, Half.valueOf(POSITIVE_INFINITY).byteValue());
        assertEquals(-1, Half.valueOf(POSITIVE_INFINITY).shortValue());
        assertEquals(Integer.MAX_VALUE, Half.valueOf(POSITIVE_INFINITY).intValue());
        assertEquals(Long.MAX_VALUE, Half.valueOf(POSITIVE_INFINITY).longValue());
        assertTrue(Float.POSITIVE_INFINITY == Half.valueOf(POSITIVE_INFINITY).floatValue());
        assertTrue(Double.POSITIVE_INFINITY == Half.valueOf(POSITIVE_INFINITY).doubleValue());

        assertEquals(0, Half.valueOf(NEGATIVE_INFINITY).byteValue());
        assertEquals(0, Half.valueOf(NEGATIVE_INFINITY).shortValue());
        assertEquals(Integer.MIN_VALUE, Half.valueOf(NEGATIVE_INFINITY).intValue());
        assertEquals(Long.MIN_VALUE, Half.valueOf(NEGATIVE_INFINITY).longValue());
        assertTrue(Float.NEGATIVE_INFINITY == Half.valueOf(NEGATIVE_INFINITY).floatValue());
        assertTrue(Double.NEGATIVE_INFINITY == Half.valueOf(NEGATIVE_INFINITY).doubleValue());

        assertEquals(0, Half.valueOf(NaN).byteValue());
        assertEquals(0, Half.valueOf(NaN).shortValue());
        assertEquals(0, Half.valueOf(NaN).intValue());
        assertEquals(0, Half.valueOf(NaN).longValue());
        assertEquals(Float.floatToRawIntBits(Float.NaN),
                Float.floatToRawIntBits(Half.valueOf(NaN).floatValue()));
        assertEquals(Double.doubleToRawLongBits(Double.NaN),
                Double.doubleToRawLongBits(Half.valueOf(NaN).doubleValue()));
    }

    @SuppressWarnings("PointlessBitwiseExpression")
    @Test
    public void bits() {
        assertEquals(POSITIVE_INFINITY & 0xffff, halfToRawIntBits(POSITIVE_INFINITY));
        assertEquals(NEGATIVE_INFINITY & 0xffff, halfToRawIntBits(NEGATIVE_INFINITY));
        assertEquals(POSITIVE_ZERO & 0xffff, halfToRawIntBits(POSITIVE_ZERO));
        assertEquals(NEGATIVE_ZERO & 0xffff, halfToRawIntBits(NEGATIVE_ZERO));
        assertEquals(NaN & 0xffff, halfToRawIntBits(NaN));
        assertEquals(0xfc98, halfToRawIntBits((short) 0xfc98)); // NaN
        assertEquals(toHalf(12.462f) & 0xffff, halfToRawIntBits(toHalf(12.462f)));
        assertEquals(toHalf(-12.462f) & 0xffff, halfToRawIntBits(toHalf(-12.462f)));

        assertEquals(POSITIVE_INFINITY & 0xffff, halfToIntBits(POSITIVE_INFINITY));
        assertEquals(NEGATIVE_INFINITY & 0xffff, halfToIntBits(NEGATIVE_INFINITY));
        assertEquals(POSITIVE_ZERO & 0xffff, halfToIntBits(POSITIVE_ZERO));
        assertEquals(NEGATIVE_ZERO & 0xffff, halfToIntBits(NEGATIVE_ZERO));
        assertEquals(NaN & 0xffff, halfToIntBits(NaN));
        assertEquals(NaN & 0xffff, halfToIntBits((short) 0xfc98)); // NaN
        assertEquals(toHalf(12.462f) & 0xffff, halfToIntBits(toHalf(12.462f)));
        assertEquals(toHalf(-12.462f) & 0xffff, halfToIntBits(toHalf(-12.462f)));

        assertShortEquals(POSITIVE_INFINITY, intBitsToHalf(halfToIntBits(POSITIVE_INFINITY)));
        assertShortEquals(NEGATIVE_INFINITY, intBitsToHalf(halfToIntBits(NEGATIVE_INFINITY)));
        assertShortEquals(POSITIVE_ZERO, intBitsToHalf(halfToIntBits(POSITIVE_ZERO)));
        assertShortEquals(NEGATIVE_ZERO, intBitsToHalf(halfToIntBits(NEGATIVE_ZERO)));
        assertShortEquals(NaN, intBitsToHalf(halfToIntBits(NaN)));
        assertShortEquals(NaN, intBitsToHalf(halfToIntBits((short) 0xfc98)));
        assertShortEquals(toHalf(12.462f), intBitsToHalf(halfToIntBits(toHalf(12.462f))));
        assertShortEquals(toHalf(-12.462f), intBitsToHalf(halfToIntBits(toHalf(-12.462f))));

        assertShortEquals(POSITIVE_INFINITY, halfToShortBits(POSITIVE_INFINITY));
        assertShortEquals(NEGATIVE_INFINITY, halfToShortBits(NEGATIVE_INFINITY));
        assertShortEquals(POSITIVE_ZERO, halfToShortBits(POSITIVE_ZERO));
        assertShortEquals(NEGATIVE_ZERO, halfToShortBits(NEGATIVE_ZERO));
        assertShortEquals(NaN, halfToShortBits(NaN));
        assertShortEquals(NaN, halfToShortBits((short) 0xfc98)); // NaN
        assertShortEquals(toHalf(12.462f), halfToShortBits(toHalf(12.462f)));
        assertShortEquals(toHalf(-12.462f), halfToShortBits(toHalf(-12.462f)));
    }

    @Test
    public void hashCodeGeneration() {
        assertNotEquals(Half.hashCode(POSITIVE_INFINITY), Half.hashCode(NEGATIVE_INFINITY));
        assertNotEquals(Half.hashCode(POSITIVE_ZERO), Half.hashCode(NEGATIVE_ZERO));
        assertNotEquals(Half.hashCode(toHalf(1.999f)), Half.hashCode(toHalf(1.998f)));
        assertEquals(Half.hashCode(NaN), Half.hashCode((short) 0x7c01));
        assertEquals(Half.hashCode(NaN), Half.hashCode((short) 0xfc98));

        assertEquals(Half.hashCode(POSITIVE_INFINITY), Half.valueOf(POSITIVE_INFINITY).hashCode());
        assertEquals(Half.hashCode(NEGATIVE_INFINITY), Half.valueOf(NEGATIVE_INFINITY).hashCode());
        assertEquals(Half.hashCode(POSITIVE_ZERO), Half.valueOf(POSITIVE_ZERO).hashCode());
        assertEquals(Half.hashCode(NEGATIVE_ZERO), Half.valueOf(NEGATIVE_ZERO).hashCode());
        assertEquals(Half.hashCode(NaN), Half.valueOf(NaN).hashCode());
        assertEquals(Half.hashCode((short) 0xfc98), Half.valueOf((short) 0xfc98).hashCode());
        assertEquals(Half.hashCode(toHalf(1.999f)), Half.valueOf(1.999f).hashCode());
    }

    @Test
    public void constructors() {
        assertEquals(POSITIVE_INFINITY, new Half(POSITIVE_INFINITY).halfValue());
        assertEquals(NEGATIVE_INFINITY, new Half(NEGATIVE_INFINITY).halfValue());
        assertEquals(POSITIVE_ZERO, new Half(POSITIVE_ZERO).halfValue());
        assertEquals(NEGATIVE_ZERO, new Half(NEGATIVE_ZERO).halfValue());
        assertEquals(NaN, new Half(NaN).halfValue());
        assertEquals(toHalf(12.57f), new Half(toHalf(12.57f)).halfValue());
        assertEquals(toHalf(-12.57f), new Half(toHalf(-12.57f)).halfValue());

        assertEquals(POSITIVE_INFINITY, new Half(Float.POSITIVE_INFINITY).halfValue());
        assertEquals(NEGATIVE_INFINITY, new Half(Float.NEGATIVE_INFINITY).halfValue());
        assertEquals(POSITIVE_ZERO, new Half(0.0f).halfValue());
        assertEquals(NEGATIVE_ZERO, new Half(-0.0f).halfValue());
        assertEquals(NaN, new Half(Float.NaN).halfValue());
        assertEquals(toHalf(12.57f), new Half(12.57f).halfValue());
        assertEquals(toHalf(-12.57f), new Half(-12.57f).halfValue());

        assertEquals(POSITIVE_INFINITY, new Half(Double.POSITIVE_INFINITY).halfValue());
        assertEquals(NEGATIVE_INFINITY, new Half(Double.NEGATIVE_INFINITY).halfValue());
        assertEquals(POSITIVE_ZERO, new Half(0.0).halfValue());
        assertEquals(NEGATIVE_ZERO, new Half(-0.0).halfValue());
        assertEquals(NaN, new Half(Double.NaN).halfValue());
        assertEquals(toHalf(12.57f), new Half(12.57).halfValue());
        assertEquals(toHalf(-12.57f), new Half(-12.57).halfValue());

        assertEquals(POSITIVE_INFINITY, new Half("+Infinity").halfValue());
        assertEquals(NEGATIVE_INFINITY, new Half("-Infinity").halfValue());
        assertEquals(POSITIVE_ZERO, new Half("0.0").halfValue());
        assertEquals(NEGATIVE_ZERO, new Half("-0.0").halfValue());
        assertEquals(NaN, new Half("NaN").halfValue());
        assertEquals(toHalf(12.57f), new Half("1257e-2").halfValue());
        assertEquals(toHalf(-12.57f), new Half("-1257e-2").halfValue());
    }

    @Test(expected = NumberFormatException.class)
    public void constructorFailure() {
        new Half("not a number");
    }

    @Test
    public void parse() {
        assertShortEquals(parseHalf("NaN"), NaN);
        assertShortEquals(parseHalf("Infinity"), POSITIVE_INFINITY);
        assertShortEquals(parseHalf("-Infinity"), NEGATIVE_INFINITY);
        assertShortEquals(parseHalf("0.0"), POSITIVE_ZERO);
        assertShortEquals(parseHalf("-0.0"), NEGATIVE_ZERO);
        assertShortEquals(parseHalf("1.0"), toHalf(1.0f));
        assertShortEquals(parseHalf("-1.0"), toHalf(-1.0f));
        assertShortEquals(parseHalf("2.0"), toHalf(2.0f));
        assertShortEquals(parseHalf("256.0"), toHalf(256.0f));
        assertShortEquals(parseHalf("0.5"), toHalf(0.5f));
        assertShortEquals(parseHalf("0.25"), toHalf(0.25f));
        assertShortEquals(parseHalf("65504.0"), MAX_VALUE);
        assertShortEquals(parseHalf("5.9604645E-8"), MIN_VALUE);
        assertShortEquals(parseHalf("6.1035156E-5"), MIN_NORMAL);
        assertShortEquals(parseHalf("-65504.0"), LOWEST_VALUE);
    }

    @Test(expected = NumberFormatException.class)
    public void parseFailure() {
        parseHalf("not a number");
    }

    @Test
    public void valueOf() {
        assertEquals(POSITIVE_INFINITY, Half.valueOf(POSITIVE_INFINITY).halfValue());
        assertEquals(NEGATIVE_INFINITY, Half.valueOf(NEGATIVE_INFINITY).halfValue());
        assertEquals(POSITIVE_ZERO, Half.valueOf(POSITIVE_ZERO).halfValue());
        assertEquals(NEGATIVE_ZERO, Half.valueOf(NEGATIVE_ZERO).halfValue());
        assertEquals(NaN, Half.valueOf(NaN).halfValue());
        assertEquals(toHalf(12.57f), Half.valueOf(toHalf(12.57f)).halfValue());
        assertEquals(toHalf(-12.57f), Half.valueOf(toHalf(-12.57f)).halfValue());

        assertEquals(POSITIVE_INFINITY, Half.valueOf(Float.POSITIVE_INFINITY).halfValue());
        assertEquals(NEGATIVE_INFINITY, Half.valueOf(Float.NEGATIVE_INFINITY).halfValue());
        assertEquals(POSITIVE_ZERO, Half.valueOf(0.0f).halfValue());
        assertEquals(NEGATIVE_ZERO, Half.valueOf(-0.0f).halfValue());
        assertEquals(NaN, Half.valueOf(Float.NaN).halfValue());
        assertEquals(toHalf(12.57f), Half.valueOf(12.57f).halfValue());
        assertEquals(toHalf(-12.57f), Half.valueOf(-12.57f).halfValue());

        assertEquals(POSITIVE_INFINITY, Half.valueOf("+Infinity").halfValue());
        assertEquals(NEGATIVE_INFINITY, Half.valueOf("-Infinity").halfValue());
        assertEquals(POSITIVE_ZERO, Half.valueOf("0.0").halfValue());
        assertEquals(NEGATIVE_ZERO, Half.valueOf("-0.0").halfValue());
        assertEquals(NaN, Half.valueOf("NaN").halfValue());
        assertEquals(toHalf(12.57f), Half.valueOf("1257e-2").halfValue());
        assertEquals(toHalf(-12.57f), Half.valueOf("-1257e-2").halfValue());
    }

    @Test
    public void compare() {
        assertEquals(0, Half.compare(NaN, NaN));
        assertEquals(0, Half.compare(NaN, (short) 0xfc98));
        assertEquals(1, Half.compare(NaN, POSITIVE_INFINITY));
        assertEquals(-1, Half.compare(POSITIVE_INFINITY, NaN));

        assertEquals(0, Half.compare(POSITIVE_INFINITY, POSITIVE_INFINITY));
        assertEquals(0, Half.compare(NEGATIVE_INFINITY, NEGATIVE_INFINITY));
        assertEquals(1, Half.compare(POSITIVE_INFINITY, NEGATIVE_INFINITY));
        assertEquals(-1, Half.compare(NEGATIVE_INFINITY, POSITIVE_INFINITY));

        assertEquals(0, Half.compare(POSITIVE_ZERO, POSITIVE_ZERO));
        assertEquals(0, Half.compare(NEGATIVE_ZERO, NEGATIVE_ZERO));
        assertEquals(1, Half.compare(POSITIVE_ZERO, NEGATIVE_ZERO));
        assertEquals(-1, Half.compare(NEGATIVE_ZERO, POSITIVE_ZERO));

        assertEquals(0, Half.compare(toHalf(12.462f), toHalf(12.462f)));
        assertEquals(0, Half.compare(toHalf(-12.462f), toHalf(-12.462f)));
        assertEquals(1, Half.compare(toHalf(12.462f), toHalf(-12.462f)));
        assertEquals(-1, Half.compare(toHalf(-12.462f), toHalf(12.462f)));
    }
}
