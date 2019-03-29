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

package android.widget.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.KeyEvent;
import android.widget.ExpandableListView;
import android.widget.cts.util.ListUtil;

import com.android.compatibility.common.util.CtsKeyEventUtil;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ExpandableListViewWithHeadersTest {
    private Instrumentation mInstrumentation;
    private ExpandableListWithHeaders mActivity;
    private ExpandableListView mExpandableListView;
    private ListUtil mListUtil;

    @Rule
    public ActivityTestRule<ExpandableListWithHeaders> mActivityRule =
            new ActivityTestRule<>(ExpandableListWithHeaders.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        PollingCheck.waitFor(mActivity::hasWindowFocus);
        mExpandableListView = mActivity.getExpandableListView();
        mListUtil = new ListUtil(mExpandableListView, mInstrumentation);
    }

    @Test
    public void testPreconditions() {
        assertNotNull(mExpandableListView);
    }

    @Test
    public void testExpandOnFirstPosition() {
        // Should be a header, and hence the first group should NOT have expanded
        mListUtil.arrowScrollToSelectedPosition(0);
        CtsKeyEventUtil.sendKeys(mInstrumentation, mExpandableListView,
                (KeyEvent.KEYCODE_DPAD_CENTER));
        mInstrumentation.waitForIdleSync();
        assertFalse(mExpandableListView.isGroupExpanded(0));
    }

    @LargeTest
    @Test
    public void testExpandOnFirstGroup() {
        mListUtil.arrowScrollToSelectedPosition(mActivity.getNumOfHeadersAndFooters());
        CtsKeyEventUtil.sendKeys(mInstrumentation, mExpandableListView,
                (KeyEvent.KEYCODE_DPAD_CENTER));
        mInstrumentation.waitForIdleSync();
        assertTrue(mExpandableListView.isGroupExpanded(0));
    }

    @Test
    public void testContextMenus() {
        ExpandableListTester tester = new ExpandableListTester(mExpandableListView);
        tester.testContextMenus();
    }

    @Test
    public void testConvertionBetweenFlatAndPacked() {
        ExpandableListTester tester = new ExpandableListTester(mExpandableListView);
        tester.testConversionBetweenFlatAndPackedOnGroups();
        tester.testConversionBetweenFlatAndPackedOnChildren();
    }

    @Test
    public void testSelectedPosition() {
        ExpandableListTester tester = new ExpandableListTester(mExpandableListView);
        tester.testSelectedPositionOnGroups();
        tester.testSelectedPositionOnChildren();
    }
}
