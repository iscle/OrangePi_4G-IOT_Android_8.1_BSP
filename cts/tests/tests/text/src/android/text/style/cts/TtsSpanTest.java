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

package android.text.style.cts;

import static org.junit.Assert.assertEquals;

import android.os.Parcel;
import android.os.PersistableBundle;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.style.TtsSpan;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TtsSpanTest {
    private PersistableBundle mBundle;

    @Before
    public void setup() {
        mBundle = new PersistableBundle();
        mBundle.putString("argument.one", "value.one");
        mBundle.putString("argument.two", "value.two");
        mBundle.putLong("argument.three", 3);
        mBundle.putLong("argument.four", 4);
    }

    @Test
    public void testGetArgs() {
        TtsSpan t = new TtsSpan("test.type.one", mBundle);
        final PersistableBundle args = t.getArgs();
        assertEquals(4, args.size());
        assertEquals("value.one", args.getString("argument.one"));
        assertEquals("value.two", args.getString("argument.two"));
        assertEquals(3, args.getLong("argument.three"));
        assertEquals(4, args.getLong("argument.four"));
    }

    @Test
    public void testGetType() {
        TtsSpan t = new TtsSpan("test.type.two", mBundle);
        assertEquals("test.type.two", t.getType());
    }

    @Test
    public void testDescribeContents() {
        TtsSpan span = new TtsSpan("test.type.three", mBundle);
        span.describeContents();
    }

    @Test
    public void testGetSpanTypeId() {
        TtsSpan span = new TtsSpan("test.type.four", mBundle);
        span.getSpanTypeId();
    }

    @Test
    public void testWriteAndReadParcel() {
        Parcel p = Parcel.obtain();
        try {
            TtsSpan span = new TtsSpan("test.type.five", mBundle);
            span.writeToParcel(p, 0);
            p.setDataPosition(0);

            TtsSpan t = new TtsSpan(p);

            assertEquals("test.type.five", t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(4, args.size());
            assertEquals("value.one", args.getString("argument.one"));
            assertEquals("value.two", args.getString("argument.two"));
            assertEquals(3, args.getLong("argument.three"));
            assertEquals(4, args.getLong("argument.four"));
        } finally {
            p.recycle();
        }
    }

    @Test
    public void testBuilder() {
        final TtsSpan t = new TtsSpan.Builder<>("test.type.builder")
                .setStringArgument("argument.string", "value")
                .setIntArgument("argument.int", Integer.MAX_VALUE)
                .setLongArgument("argument.long", Long.MAX_VALUE)
                .build();
        assertEquals("test.type.builder", t.getType());
        final PersistableBundle args = t.getArgs();
        assertEquals(3, args.size());
        assertEquals("value", args.getString("argument.string"));
        assertEquals(Integer.MAX_VALUE, args.getInt("argument.int"));
        assertEquals(Long.MAX_VALUE, args.getLong("argument.long"));
    }

    @Test
    public void testSemioticClassBuilder() {
        final TtsSpan t = new TtsSpan.SemioticClassBuilder<>("test.type.semioticClassBuilder")
                .setGender(TtsSpan.GENDER_FEMALE)
                .setAnimacy(TtsSpan.ANIMACY_ANIMATE)
                .setMultiplicity(TtsSpan.MULTIPLICITY_SINGLE)
                .setCase(TtsSpan.CASE_NOMINATIVE)
                .build();
        assertEquals("test.type.semioticClassBuilder", t.getType());
        final PersistableBundle args = t.getArgs();
        assertEquals(4, args.size());
        assertEquals(TtsSpan.GENDER_FEMALE, args.getString(TtsSpan.ARG_GENDER));
        assertEquals(TtsSpan.ANIMACY_ANIMATE, args.getString(TtsSpan.ARG_ANIMACY));
        assertEquals(TtsSpan.MULTIPLICITY_SINGLE, args.getString(TtsSpan.ARG_MULTIPLICITY));
        assertEquals(TtsSpan.CASE_NOMINATIVE, args.getString(TtsSpan.ARG_CASE));
    }

    @Test
    public void testTextBuilder() {
        {
            final TtsSpan t = new TtsSpan.TextBuilder()
                    .setText("text")
                    .build();
            assertEquals(TtsSpan.TYPE_TEXT, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("text", args.getString(TtsSpan.ARG_TEXT));
        }
        {
            final TtsSpan t = new TtsSpan.TextBuilder("text").build();
            assertEquals(TtsSpan.TYPE_TEXT, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("text", args.getString(TtsSpan.ARG_TEXT));
        }
    }

    @Test
    public void testCardinalBuilder() {
        {
            final TtsSpan t = new TtsSpan.CardinalBuilder()
                    .setNumber(Long.MAX_VALUE)
                    .build();
            assertEquals(TtsSpan.TYPE_CARDINAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals(String.valueOf(Long.MAX_VALUE), args.getString(TtsSpan.ARG_NUMBER));
        }
        {
            final TtsSpan t = new TtsSpan.CardinalBuilder()
                    .setNumber("10")
                    .build();
            assertEquals(TtsSpan.TYPE_CARDINAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_NUMBER));
        }
        {
            final TtsSpan t = new TtsSpan.CardinalBuilder(Long.MAX_VALUE).build();
            assertEquals(TtsSpan.TYPE_CARDINAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals(String.valueOf(Long.MAX_VALUE), args.getString(TtsSpan.ARG_NUMBER));
        }
        {
            final TtsSpan t = new TtsSpan.CardinalBuilder("10").build();
            assertEquals(TtsSpan.TYPE_CARDINAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_NUMBER));
        }
    }

    @Test
    public void testOrdinalBuilder() {
        {
            final TtsSpan t = new TtsSpan.OrdinalBuilder()
                    .setNumber(Long.MAX_VALUE)
                    .build();
            assertEquals(TtsSpan.TYPE_ORDINAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals(String.valueOf(Long.MAX_VALUE), args.getString(TtsSpan.ARG_NUMBER));
        }
        {
            final TtsSpan t = new TtsSpan.OrdinalBuilder()
                    .setNumber("10")
                    .build();
            assertEquals(TtsSpan.TYPE_ORDINAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_NUMBER));
        }
        {
            final TtsSpan t = new TtsSpan.OrdinalBuilder(Long.MAX_VALUE).build();
            assertEquals(TtsSpan.TYPE_ORDINAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals(String.valueOf(Long.MAX_VALUE), args.getString(TtsSpan.ARG_NUMBER));
        }
        {
            final TtsSpan t = new TtsSpan.OrdinalBuilder("10").build();
            assertEquals(TtsSpan.TYPE_ORDINAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_NUMBER));
        }
    }

    @Test
    public void testDecimalBuilder() {
        {
            final TtsSpan t = new TtsSpan.DecimalBuilder()
                    .setArgumentsFromDouble(10.25, 1, 2)
                    .build();
            assertEquals(TtsSpan.TYPE_DECIMAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(2, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_INTEGER_PART));
            assertEquals("25", args.getString(TtsSpan.ARG_FRACTIONAL_PART));
        }
        {
            final TtsSpan t = new TtsSpan.DecimalBuilder(10.25, 1, 2).build();
            assertEquals(TtsSpan.TYPE_DECIMAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(2, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_INTEGER_PART));
            assertEquals("25", args.getString(TtsSpan.ARG_FRACTIONAL_PART));
        }
        {
            final TtsSpan t = new TtsSpan.DecimalBuilder()
                    .setArgumentsFromDouble(10, 0, 0)
                    .build();
            assertEquals(TtsSpan.TYPE_DECIMAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_INTEGER_PART));
        }
        {
            final TtsSpan t = new TtsSpan.DecimalBuilder(10, 0, 0).build();
            assertEquals(TtsSpan.TYPE_DECIMAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_INTEGER_PART));
        }
        {
            final TtsSpan t = new TtsSpan.DecimalBuilder()
                    .setArgumentsFromDouble(10.25, 10, 10)
                    .build();
            assertEquals(TtsSpan.TYPE_DECIMAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(2, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_INTEGER_PART));
            assertEquals("2500000000", args.getString(TtsSpan.ARG_FRACTIONAL_PART));
        }
        {
            final TtsSpan t = new TtsSpan.DecimalBuilder("10", "25").build();
            assertEquals(TtsSpan.TYPE_DECIMAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(2, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_INTEGER_PART));
            assertEquals("25", args.getString(TtsSpan.ARG_FRACTIONAL_PART));
        }
        {
            final TtsSpan t = new TtsSpan.DecimalBuilder(null, null).build();
            assertEquals(TtsSpan.TYPE_DECIMAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(2, args.size());
            assertEquals(null, args.getString(TtsSpan.ARG_INTEGER_PART));
            assertEquals(null, args.getString(TtsSpan.ARG_FRACTIONAL_PART));
        }
        {
            final TtsSpan t = new TtsSpan.DecimalBuilder().setIntegerPart(10).build();
            assertEquals(TtsSpan.TYPE_DECIMAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_INTEGER_PART));
        }
        {
            final TtsSpan t = new TtsSpan.DecimalBuilder().setIntegerPart("10").build();
            assertEquals(TtsSpan.TYPE_DECIMAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_INTEGER_PART));
        }
        {
            final TtsSpan t = new TtsSpan.DecimalBuilder().setIntegerPart(null).build();
            assertEquals(TtsSpan.TYPE_DECIMAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals(null, args.getString(TtsSpan.ARG_INTEGER_PART));
        }
        {
            final TtsSpan t = new TtsSpan.DecimalBuilder().setFractionalPart("25").build();
            assertEquals(TtsSpan.TYPE_DECIMAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("25", args.getString(TtsSpan.ARG_FRACTIONAL_PART));
        }
        {
            final TtsSpan t = new TtsSpan.DecimalBuilder().setFractionalPart(null).build();
            assertEquals(TtsSpan.TYPE_DECIMAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals(null, args.getString(TtsSpan.ARG_FRACTIONAL_PART));
        }
        {
            final TtsSpan t = new TtsSpan.DecimalBuilder().build();
            assertEquals(TtsSpan.TYPE_DECIMAL, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(0, args.size());
        }
    }

    @Test
    public void testFractionBuilder() {
        {
            final TtsSpan t = new TtsSpan.FractionBuilder()
                    .setIntegerPart(10)
                    .setNumerator(3)
                    .setDenominator(100)
                    .build();
            assertEquals(TtsSpan.TYPE_FRACTION, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(3, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_INTEGER_PART));
            assertEquals("3", args.getString(TtsSpan.ARG_NUMERATOR));
            assertEquals("100", args.getString(TtsSpan.ARG_DENOMINATOR));
        }
        {
            final TtsSpan t = new TtsSpan.FractionBuilder(10, 3, 100).build();
            assertEquals(TtsSpan.TYPE_FRACTION, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(3, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_INTEGER_PART));
            assertEquals("3", args.getString(TtsSpan.ARG_NUMERATOR));
            assertEquals("100", args.getString(TtsSpan.ARG_DENOMINATOR));
        }
        {
            final TtsSpan t = new TtsSpan.FractionBuilder().setIntegerPart("10").build();
            assertEquals(TtsSpan.TYPE_FRACTION, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_INTEGER_PART));
            assertEquals(null, args.getString(TtsSpan.ARG_NUMERATOR));
            assertEquals(null, args.getString(TtsSpan.ARG_DENOMINATOR));
        }
        {
            final TtsSpan t = new TtsSpan.FractionBuilder().setNumerator("3").build();
            assertEquals(TtsSpan.TYPE_FRACTION, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("3", args.getString(TtsSpan.ARG_NUMERATOR));
        }
        {
            final TtsSpan t = new TtsSpan.FractionBuilder().setDenominator("100").build();
            assertEquals(TtsSpan.TYPE_FRACTION, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("100", args.getString(TtsSpan.ARG_DENOMINATOR));
        }
        {
            final TtsSpan t = new TtsSpan.FractionBuilder().build();
            assertEquals(TtsSpan.TYPE_FRACTION, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(0, args.size());
        }
    }

    @Test
    public void testMeasureBuilder() {
        {
            final TtsSpan t = new TtsSpan.MeasureBuilder()
                    .setNumber(10)
                    .setUnit("unit")
                    .build();
            assertEquals(TtsSpan.TYPE_MEASURE, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(2, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_NUMBER));
            assertEquals("unit", args.getString(TtsSpan.ARG_UNIT));
        }
        {
            final TtsSpan t = new TtsSpan.MeasureBuilder()
                    .setIntegerPart(10)
                    .setFractionalPart("25")
                    .setUnit("unit")
                    .build();
            assertEquals(TtsSpan.TYPE_MEASURE, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(3, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_INTEGER_PART));
            assertEquals("25", args.getString(TtsSpan.ARG_FRACTIONAL_PART));
            assertEquals("unit", args.getString(TtsSpan.ARG_UNIT));
        }
        {
            final TtsSpan t = new TtsSpan.MeasureBuilder()
                    .setIntegerPart(10)
                    .setNumerator(3)
                    .setDenominator(100)
                    .setUnit("unit")
                    .build();
            assertEquals(TtsSpan.TYPE_MEASURE, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(4, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_INTEGER_PART));
            assertEquals("3", args.getString(TtsSpan.ARG_NUMERATOR));
            assertEquals("100", args.getString(TtsSpan.ARG_DENOMINATOR));
            assertEquals("unit", args.getString(TtsSpan.ARG_UNIT));
        }
        {
            final TtsSpan t = new TtsSpan.MeasureBuilder().setIntegerPart("10").build();
            assertEquals(TtsSpan.TYPE_MEASURE, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_INTEGER_PART));
        }
        {
            final TtsSpan t = new TtsSpan.MeasureBuilder().setNumerator("3").build();
            assertEquals(TtsSpan.TYPE_MEASURE, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("3", args.getString(TtsSpan.ARG_NUMERATOR));
        }
        {
            final TtsSpan t = new TtsSpan.MeasureBuilder().setDenominator("100").build();
            assertEquals(TtsSpan.TYPE_MEASURE, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("100", args.getString(TtsSpan.ARG_DENOMINATOR));
        }
        {
            final TtsSpan t = new TtsSpan.MeasureBuilder().build();
            assertEquals(TtsSpan.TYPE_MEASURE, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(0, args.size());
        }
    }

    @Test
    public void testTimeBuilder() {
        {
            final TtsSpan t = new TtsSpan.TimeBuilder()
                    .setHours(20)
                    .setMinutes(50)
                    .build();
            assertEquals(TtsSpan.TYPE_TIME, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(2, args.size());
            assertEquals(20, args.getInt(TtsSpan.ARG_HOURS));
            assertEquals(50, args.getInt(TtsSpan.ARG_MINUTES));
        }
        {
            final TtsSpan t = new TtsSpan.TimeBuilder(20, 50).build();
            assertEquals(TtsSpan.TYPE_TIME, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(2, args.size());
            assertEquals(20, args.getInt(TtsSpan.ARG_HOURS));
            assertEquals(50, args.getInt(TtsSpan.ARG_MINUTES));
        }
    }

    @Test
    public void testDateBuilder() {
        {
            final TtsSpan t = new TtsSpan.DateBuilder()
                    .setWeekday(3)
                    .setDay(16)
                    .setMonth(3)
                    .setYear(2016)
                    .build();
            assertEquals(TtsSpan.TYPE_DATE, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(4, args.size());
            assertEquals(3, args.getInt(TtsSpan.ARG_WEEKDAY));
            assertEquals(16, args.getInt(TtsSpan.ARG_DAY));
            assertEquals(3, args.getInt(TtsSpan.ARG_MONTH));
            assertEquals(2016, args.getInt(TtsSpan.ARG_YEAR));
        }
        {
            final TtsSpan t = new TtsSpan.DateBuilder(3, 16, 3, 2016).build();
            assertEquals(TtsSpan.TYPE_DATE, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(4, args.size());
            assertEquals(3, args.getInt(TtsSpan.ARG_WEEKDAY));
            assertEquals(16, args.getInt(TtsSpan.ARG_DAY));
            assertEquals(3, args.getInt(TtsSpan.ARG_MONTH));
            assertEquals(2016, args.getInt(TtsSpan.ARG_YEAR));
        }
        {
            final TtsSpan t = new TtsSpan.DateBuilder(3, 16, null, null).build();
            assertEquals(TtsSpan.TYPE_DATE, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(2, args.size());
            assertEquals(3, args.getInt(TtsSpan.ARG_WEEKDAY));
            assertEquals(16, args.getInt(TtsSpan.ARG_DAY));
        }
    }

    @Test
    public void testMoneyBuilder() {
        {
            final TtsSpan t = new TtsSpan.MoneyBuilder()
                    .setIntegerPart(10)
                    .setFractionalPart("25")
                    .setCurrency("USD")
                    .setQuantity("1000")
                    .build();
            assertEquals(TtsSpan.TYPE_MONEY, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(4, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_INTEGER_PART));
            assertEquals("25", args.getString(TtsSpan.ARG_FRACTIONAL_PART));
            assertEquals("USD", args.getString(TtsSpan.ARG_CURRENCY));
            assertEquals("1000", args.getString(TtsSpan.ARG_QUANTITY));
        }
        {
            final TtsSpan t = new TtsSpan.MoneyBuilder().setIntegerPart("10").build();
            assertEquals(TtsSpan.TYPE_MONEY, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("10", args.getString(TtsSpan.ARG_INTEGER_PART));
        }
        {
            final TtsSpan t = new TtsSpan.MoneyBuilder().build();
            assertEquals(TtsSpan.TYPE_MONEY, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(0, args.size());
        }

    }

    @Test
    public void testTelephoneBuilder() {
        {
            final TtsSpan t = new TtsSpan.TelephoneBuilder()
                    .setCountryCode("+01")
                    .setNumberParts("000-000-0000")
                    .setExtension("0000")
                    .build();
            assertEquals(TtsSpan.TYPE_TELEPHONE, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(3, args.size());
            assertEquals("+01", args.getString(TtsSpan.ARG_COUNTRY_CODE));
            assertEquals("000-000-0000", args.getString(TtsSpan.ARG_NUMBER_PARTS));
            assertEquals("0000", args.getString(TtsSpan.ARG_EXTENSION));
        }
        {
            final TtsSpan t = new TtsSpan.TelephoneBuilder("000-000-0000").build();
            assertEquals(TtsSpan.TYPE_TELEPHONE, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("000-000-0000", args.getString(TtsSpan.ARG_NUMBER_PARTS));
        }
    }

    @Test
    public void testElectronicBuilder() {
        {
            final TtsSpan t = new TtsSpan.ElectronicBuilder()
                    .setEmailArguments("example", "example.com")
                    .build();
            assertEquals(TtsSpan.TYPE_ELECTRONIC, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(2, args.size());
            assertEquals("example", args.getString(TtsSpan.ARG_USERNAME));
            assertEquals("example.com", args.getString(TtsSpan.ARG_DOMAIN));
        }
        {
            final TtsSpan t = new TtsSpan.ElectronicBuilder()
                    .setProtocol("http")
                    .setDomain("example.com")
                    .setPort(80)
                    .setPath("example/index.html")
                    .setQueryString("arg1=value1&arg2=value2")
                    .setFragmentId("fragment")
                    .setUsername("username")
                    .setPassword("password")
                    .build();
            assertEquals(TtsSpan.TYPE_ELECTRONIC, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(8, args.size());
            assertEquals("http", args.getString(TtsSpan.ARG_PROTOCOL));
            assertEquals("example.com", args.getString(TtsSpan.ARG_DOMAIN));
            assertEquals(80, args.getInt(TtsSpan.ARG_PORT));
            assertEquals("example/index.html", args.getString(TtsSpan.ARG_PATH));
            assertEquals("arg1=value1&arg2=value2", args.getString(TtsSpan.ARG_QUERY_STRING));
            assertEquals("fragment", args.getString(TtsSpan.ARG_FRAGMENT_ID));
            assertEquals("username", args.getString(TtsSpan.ARG_USERNAME));
            assertEquals("password", args.getString(TtsSpan.ARG_PASSWORD));
        }
    }

    @Test
    public void testDigitsBuilder() {
        {
            final TtsSpan t = new TtsSpan.DigitsBuilder()
                    .setDigits("12345")
                    .build();
            assertEquals(TtsSpan.TYPE_DIGITS, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("12345", args.getString(TtsSpan.ARG_DIGITS));
        }
        {
            final TtsSpan t = new TtsSpan.DigitsBuilder("12345").build();
            assertEquals(TtsSpan.TYPE_DIGITS, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("12345", args.getString(TtsSpan.ARG_DIGITS));
        }
    }

    @Test
    public void testVerbatimBuilder() {
        {
            final TtsSpan t = new TtsSpan.VerbatimBuilder()
                    .setVerbatim("abcdefg")
                    .build();
            assertEquals(TtsSpan.TYPE_VERBATIM, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("abcdefg", args.getString(TtsSpan.ARG_VERBATIM));
        }
        {
            final TtsSpan t = new TtsSpan.VerbatimBuilder("abcdefg").build();
            assertEquals(TtsSpan.TYPE_VERBATIM, t.getType());
            final PersistableBundle args = t.getArgs();
            assertEquals(1, args.size());
            assertEquals("abcdefg", args.getString(TtsSpan.ARG_VERBATIM));
        }
    }
}
