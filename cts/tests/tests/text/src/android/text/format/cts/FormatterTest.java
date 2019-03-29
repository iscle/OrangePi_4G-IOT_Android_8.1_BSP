/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.text.format.cts;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.format.Formatter;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class FormatterTest {
    @Test
    public void testFormatFileSize() {
        // test null Context
        assertEquals("", Formatter.formatFileSize(null, 0));

        final MathContext mc = MathContext.DECIMAL64;
        final BigDecimal bd = new BigDecimal((long) 1000, mc);
        final Configuration config = new Configuration();
        config.setLocales(new LocaleList(Locale.US));
        final Context context =
                InstrumentationRegistry.getTargetContext().createConfigurationContext(config);

        // test different long values with various length
        assertEquals("0 B", Formatter.formatFileSize(context, 0));
        assertEquals("1 B", Formatter.formatFileSize(context, 1));
        assertEquals("9 B", Formatter.formatFileSize(context, 9));
        assertEquals("10 B", Formatter.formatFileSize(context, 10));
        assertEquals("99 B", Formatter.formatFileSize(context, 99));
        assertEquals("100 B", Formatter.formatFileSize(context, 100));
        assertEquals("900 B", Formatter.formatFileSize(context, 900));
        assertEquals("0.90 kB", Formatter.formatFileSize(context, 901));

        assertEquals("1.00 kB", Formatter.formatFileSize(context, bd.pow(1).longValue()));
        assertEquals("1.50 kB", Formatter.formatFileSize(context, bd.pow(1).longValue() * 3 / 2));
        assertEquals("12.50 kB", Formatter.formatFileSize(context, bd.pow(1).longValue() * 25 / 2));

        assertEquals("1.00 MB", Formatter.formatFileSize(context, bd.pow(2).longValue()));

        assertEquals("1.00 GB", Formatter.formatFileSize(context, bd.pow(3).longValue()));

        assertEquals("1.00 TB", Formatter.formatFileSize(context, bd.pow(4).longValue()));

        assertEquals("1.00 PB", Formatter.formatFileSize(context, bd.pow(5).longValue()));

        assertEquals("1000 PB", Formatter.formatFileSize(context, bd.pow(6).longValue()));

        // test Negative value
        assertEquals("-1 B", Formatter.formatFileSize(context, -1));
    }

    @Test
    public void testFormatShortFileSize() {
        // test null Context
        assertEquals("", Formatter.formatFileSize(null, 0));

        final MathContext mc = MathContext.DECIMAL64;
        final BigDecimal bd = new BigDecimal((long) 1000, mc);
        final Configuration config = new Configuration();
        config.setLocales(new LocaleList(Locale.US));
        final Context context =
                InstrumentationRegistry.getTargetContext().createConfigurationContext(config);

        // test different long values with various length
        assertEquals("0 B", Formatter.formatShortFileSize(context, 0));
        assertEquals("1 B", Formatter.formatShortFileSize(context, 1));
        assertEquals("9 B", Formatter.formatShortFileSize(context, 9));
        assertEquals("10 B", Formatter.formatShortFileSize(context, 10));
        assertEquals("99 B", Formatter.formatShortFileSize(context, 99));
        assertEquals("100 B", Formatter.formatShortFileSize(context, 100));
        assertEquals("900 B", Formatter.formatShortFileSize(context, 900));
        assertEquals("0.90 kB", Formatter.formatShortFileSize(context, 901));

        assertEquals("1.0 kB", Formatter.formatShortFileSize(context, bd.pow(1).longValue()));
        assertEquals("1.5 kB", Formatter.formatShortFileSize(context,
                bd.pow(1).longValue() * 3 / 2));
        assertEquals("13 kB", Formatter.formatShortFileSize(context,
                bd.pow(1).longValue() * 25 / 2));

        assertEquals("1.0 MB", Formatter.formatShortFileSize(context, bd.pow(2).longValue()));

        assertEquals("1.0 GB", Formatter.formatShortFileSize(context, bd.pow(3).longValue()));

        assertEquals("1.0 TB", Formatter.formatShortFileSize(context, bd.pow(4).longValue()));

        assertEquals("1.0 PB", Formatter.formatShortFileSize(context, bd.pow(5).longValue()));

        assertEquals("1000 PB", Formatter.formatShortFileSize(context, bd.pow(6).longValue()));

        // test Negative value
        assertEquals("-1 B", Formatter.formatShortFileSize(context, -1));
    }

    @Test
    public void testFormatIpAddress() {
        assertEquals("1.0.168.192", Formatter.formatIpAddress(0xC0A80001));
        assertEquals("1.0.0.127", Formatter.formatIpAddress(0x7F000001));
        assertEquals("35.182.168.192", Formatter.formatIpAddress(0xC0A8B623));
        assertEquals("0.255.255.255", Formatter.formatIpAddress(0xFFFFFF00));
        assertEquals("222.5.15.10", Formatter.formatIpAddress(0x0A0F05DE));
    }
}
