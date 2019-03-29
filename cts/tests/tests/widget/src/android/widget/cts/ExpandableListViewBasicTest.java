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
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.KeyEvent;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.cts.util.ExpandableListScenario;
import android.widget.cts.util.ExpandableListScenario.MyGroup;
import android.widget.cts.util.ListUtil;

import com.android.compatibility.common.util.CtsKeyEventUtil;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ExpandableListViewBasicTest {
    private Instrumentation mInstrumentation;
    private ExpandableListScenario mActivity;
    private ExpandableListView mExpandableListView;
    private ExpandableListAdapter mAdapter;
    private ListUtil mListUtil;

    @Rule
    public ActivityTestRule<ExpandableListBasic> mActivityRule =
            new ActivityTestRule<>(ExpandableListBasic.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        PollingCheck.waitFor(mActivity::hasWindowFocus);
        mExpandableListView = mActivity.getExpandableListView();
        mAdapter = mExpandableListView.getExpandableListAdapter();
        mListUtil = new ListUtil(mExpandableListView, mInstrumentation);
    }

    @Test
    public void testPreconditions() {
        assertNotNull(mActivity);
        assertNotNull(mExpandableListView);
    }

    private int expandGroup(int numChildren, boolean atLeastOneChild) {
        final int groupPos = mActivity.findGroupWithNumChildren(numChildren, atLeastOneChild);

        assertTrue("Could not find group to expand", groupPos >= 0);
        assertFalse("Group is already expanded", mExpandableListView.isGroupExpanded(groupPos));
        mListUtil.arrowScrollToSelectedPosition(groupPos);
        mInstrumentation.waitForIdleSync();
        CtsKeyEventUtil.sendKeys(mInstrumentation, mExpandableListView,
                KeyEvent.KEYCODE_DPAD_CENTER);
        mInstrumentation.waitForIdleSync();
        assertTrue("Group did not expand", mExpandableListView.isGroupExpanded(groupPos));

        return groupPos;
    }

    @Test
    public void testExpandGroup() {
        expandGroup(-1, true);
    }

    @Test
    public void testCollapseGroup() {
        final int groupPos = expandGroup(-1, true);

        CtsKeyEventUtil.sendKeys(mInstrumentation, mExpandableListView,
                KeyEvent.KEYCODE_DPAD_CENTER);
        mInstrumentation.waitForIdleSync();
        assertFalse("Group did not collapse", mExpandableListView.isGroupExpanded(groupPos));
    }

    @Test
    public void testExpandedGroupMovement() throws Throwable {
        // Expand the first group
        mListUtil.arrowScrollToSelectedPosition(0);
        CtsKeyEventUtil.sendKeys(mInstrumentation, mExpandableListView,
                KeyEvent.KEYCODE_DPAD_CENTER);
        mInstrumentation.waitForIdleSync();

        // Ensure it expanded
        assertTrue("Group did not expand", mExpandableListView.isGroupExpanded(0));

        // Wait until that's all good
        mInstrumentation.waitForIdleSync();

        // Make sure it expanded
        assertTrue("Group did not expand", mExpandableListView.isGroupExpanded(0));

        // Insert a collapsed group in front of the one just expanded
        List<MyGroup> groups = mActivity.getGroups();
        MyGroup insertedGroup = new MyGroup(1);
        groups.add(0, insertedGroup);

        // Notify data change
        assertTrue("Adapter is not an instance of the base adapter",
                mAdapter instanceof BaseExpandableListAdapter);
        final BaseExpandableListAdapter adapter = (BaseExpandableListAdapter) mAdapter;

        mActivityRule.runOnUiThread(adapter::notifyDataSetChanged);
        mInstrumentation.waitForIdleSync();

        // Make sure the right group is expanded
        assertTrue("The expanded state didn't stay with the proper group",
                mExpandableListView.isGroupExpanded(1));
        assertFalse("The expanded state was given to the inserted group",
                mExpandableListView.isGroupExpanded(0));
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
