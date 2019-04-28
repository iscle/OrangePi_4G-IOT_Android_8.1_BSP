/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.support.test.metricshelper;

import android.metrics.LogMaker;
import android.metrics.MetricsReader;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import android.support.test.runner.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.junit.Assert.fail;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MetricsAssertsTest {
    @Mock MetricsReader mReader;

    private LogMaker a;
    private LogMaker b;
    private LogMaker c;
    private LogMaker d;

    private int mActionView = MetricsEvent.ACTION_WIFI_ON;
    private int mOpenView = MetricsEvent.MAIN_SETTINGS;
    private int mCloseView = MetricsEvent.NOTIFICATION_PANEL;
    private int mSubtype = 4;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        a = new LogMaker(MetricsEvent.SCREEN)
                .setType(MetricsEvent.TYPE_OPEN)
                .setTimestamp(1000);
        b = new LogMaker(mOpenView)
                .setType(MetricsEvent.TYPE_OPEN)
                .setTimestamp(2000);
        c = new LogMaker(mActionView)
                .setType(MetricsEvent.TYPE_ACTION)
                .setSubtype(mSubtype)
                .setTimestamp(3000);
        d = new LogMaker(mCloseView)
                .setType(MetricsEvent.TYPE_CLOSE)
                .setTimestamp(4000);

        when(mReader.hasNext())
            .thenReturn(true)
            .thenReturn(true)
            .thenReturn(true)
            .thenReturn(true)
            .thenReturn(false);
        when(mReader.next())
            .thenReturn(a)
            .thenReturn(b)
            .thenReturn(c)
            .thenReturn(d)
            .thenReturn(null);
    }

    @Test
    public void testHasActionLogTrue() {
        MetricsAsserts.assertHasActionLog("foo", mReader, mActionView);
    }

    @Test
    public void testHasActionLogFalse() {
        final String message = "foo";
        try {
            MetricsAsserts.assertHasActionLog(message, mReader, mOpenView);
        } catch (AssertionError e) {
            assertEquals(message, e.getMessage());
            return; // success!
        }
    }

    @Test
    public void testHasVisibileLogTrue() {
        MetricsAsserts.assertHasVisibilityLog("foo", mReader, mOpenView, true);
    }

    @Test
    public void testHasVisibleLogFalse() {
        final String message = "foo";
        try {
            MetricsAsserts.assertHasVisibilityLog(message, mReader, mActionView, true);
        } catch (AssertionError e) {
            assertEquals(message, e.getMessage());
            return; // success!
        }
    }

    @Test
    public void testHasHiddenLogTrue() {
        MetricsAsserts.assertHasVisibilityLog("foo", mReader, mCloseView, false);
    }

    @Test
    public void testHasHiddenLogFalse() {
        final String message = "foo";
        try {
            MetricsAsserts.assertHasVisibilityLog(message, mReader, mOpenView, false);
        } catch (AssertionError e) {
            assertEquals(message, e.getMessage());
            return; // success!
        }
    }

    @Test
    public void testHasTemplateLogCategoryOnly() {
        MetricsAsserts.assertHasLog("didn't find existing log", mReader,
                new LogMaker(mActionView));
    }

    @Test
    public void testHasTemplateLogCategoryAndType() {
        MetricsAsserts.assertHasLog("didn't find existing log", mReader,
                new LogMaker(mActionView)
                        .setType(MetricsEvent.TYPE_ACTION));
    }

    @Test
    public void testHasTemplateLogCategoryTypeAndSubtype() {
        MetricsAsserts.assertHasLog("didn't find existing log", mReader,
                new LogMaker(mActionView)
                        .setType(MetricsEvent.TYPE_ACTION)
                        .setSubtype(mSubtype));
    }

    @Test
    public void testDoesNotHaveTemplateLog() {
        final String message = "foo";
        try {
            MetricsAsserts.assertHasLog(message, mReader,
                    new LogMaker(mActionView)
                            .setType(MetricsEvent.TYPE_ACTION)
                            .setSubtype(mSubtype));
        } catch (AssertionError e) {
            assertEquals(message, e.getMessage());
            return; // success!
        }

    }
}
