/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.widget.cts;

import static com.android.compatibility.common.util.CtsMockitoUtils.within;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.Instrumentation;
import android.os.Looper;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.widget.Filter;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.TestThread;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class FilterTest {
    private static final long TIME_OUT = 10000;
    private static final long RUN_TIME = 1000;
    private static final String TEST_CONSTRAINT = "filter test";

    private Instrumentation mInstrumentation;
    private MockFilter mMockFilter;

    @Rule
    public ActivityTestRule<CtsActivity> mActivityRule =
            new ActivityTestRule<>(CtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    @Test
    public void testConstructor() throws Throwable {
        new TestThread(() -> {
            Looper.prepare();
            new MockFilter();
        }).runTest(RUN_TIME);
    }

    @Test
    public void testConvertResultToString() throws Throwable {
        final String testStr = "Test";
        new TestThread(() -> {
            Looper.prepare();
            MockFilter filter = new MockFilter();
            assertEquals("", filter.convertResultToString(null));
            assertEquals(testStr, filter.convertResultToString(testStr));
        }).runTest(RUN_TIME);
    }

    @Test
    public void testFilter1() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mMockFilter = new MockFilter();
            mMockFilter.filter(TEST_CONSTRAINT);
        });
        mInstrumentation.waitForIdleSync();

        PollingCheck.waitFor(TIME_OUT, mMockFilter::hadPerformedFiltering);
        assertEquals(TEST_CONSTRAINT, mMockFilter.getPerformFilteringConstraint());

        PollingCheck.waitFor(TIME_OUT, mMockFilter::hadPublishedResults);
        assertEquals(TEST_CONSTRAINT, mMockFilter.getPublishResultsConstraint());
        assertSame(mMockFilter.getExpectResults(), mMockFilter.getResults());
    }

    @Test
    public void testFilter2() throws Throwable {
        final Filter.FilterListener mockFilterListener = mock(Filter.FilterListener.class);

        mActivityRule.runOnUiThread(() -> {
            mMockFilter = new MockFilter();
            mMockFilter.filter(TEST_CONSTRAINT, mockFilterListener);
        });
        mInstrumentation.waitForIdleSync();

        PollingCheck.waitFor(TIME_OUT, mMockFilter::hadPerformedFiltering);
        assertEquals(TEST_CONSTRAINT, mMockFilter.getPerformFilteringConstraint());

        PollingCheck.waitFor(TIME_OUT, mMockFilter::hadPublishedResults);
        assertEquals(TEST_CONSTRAINT, mMockFilter.getPublishResultsConstraint());
        assertSame(mMockFilter.getExpectResults(), mMockFilter.getResults());

        verify(mockFilterListener, within(TIME_OUT)).onFilterComplete(anyInt());
    }

    private static class MockFilter extends Filter {
        private boolean mHadPublishedResults = false;
        private boolean mHadPerformedFiltering = false;
        private CharSequence mPerformFilteringConstraint;
        private CharSequence mPublishResultsConstraint;
        private FilterResults mResults;
        private FilterResults mExpectResults = new FilterResults();

        public MockFilter() {
            super();
        }

        public boolean hadPublishedResults() {
            synchronized (this) {
                return mHadPublishedResults;
            }
        }

        public boolean hadPerformedFiltering() {
            synchronized (this) {
                return mHadPerformedFiltering;
            }
        }

        public CharSequence getPerformFilteringConstraint() {
            synchronized (this) {
                return mPerformFilteringConstraint;
            }
        }

        public CharSequence getPublishResultsConstraint() {
            synchronized (this) {
                return mPublishResultsConstraint;
            }
        }

        public FilterResults getResults() {
            synchronized (this) {
                return mResults;
            }
        }

        public FilterResults getExpectResults() {
            synchronized (this) {
                return mExpectResults;
            }
        }

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            synchronized (this) {
                mHadPerformedFiltering = true;
                mPerformFilteringConstraint = constraint;
                return mExpectResults;
            }
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            synchronized (this) {
                mPublishResultsConstraint = constraint;
                mResults = results;
                mHadPublishedResults = true;
            }
        }
    }
}
