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

package android.widget.cts;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PointerIconTest {

    @Rule
    public final ActivityTestRule<PointerIconCtsActivity> mActivityRule =
            new ActivityTestRule<>(PointerIconCtsActivity.class);

    private Activity mActivity;
    private View mTopView;
    private PointerIcon mHandIcon;
    private PointerIcon mHelpIcon;

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mTopView = mActivity.findViewById(R.id.top);
        mHandIcon = PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_HAND);
        mHelpIcon = PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_HELP);
    }

    private void assertPointerIcon(String message, PointerIcon expectedIcon, View target) {
        final int[] topPos = mTopView.getLocationOnScreen();
        final int[] targetPos = target.getLocationOnScreen();
        final int x = targetPos[0] + target.getWidth() / 2 - topPos[0];
        final int y = targetPos[1] + target.getHeight() / 2 - topPos[1];
        final MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_HOVER_MOVE, x, y, 0);
        assertEquals(message, expectedIcon, mTopView.onResolvePointerIcon(event, 0));
    }

    private void assertDefaultWidgetPointerIconBehavior(View view) {
        assertPointerIcon("Default pointer icon", mHandIcon, view);

        view.setEnabled(false);
        assertPointerIcon("Disabled view has no pointer icon", null, view);

        view.setEnabled(true);
        assertPointerIcon("Enabled view has default pointer icon", mHandIcon, view);

        view.setPointerIcon(mHelpIcon);
        assertPointerIcon("Override pointer icon", mHelpIcon, view);

        view.setPointerIcon(null);
        assertPointerIcon("Revert to default pointer icon", mHandIcon, view);
    }

    private TabHost.TabSpec createTabSpec(TabHost tabHost, String label, PointerIcon pointerIcon) {
        final TextView tabIndicator = new TextView(mActivity);
        tabIndicator.setText(label);
        tabIndicator.setPointerIcon(pointerIcon);
        return tabHost.newTabSpec(label)
                .setIndicator(tabIndicator)
                .setContent(tag -> new View(mActivity));
    }

    @UiThreadTest
    @Test
    public void testButton() {
        assertDefaultWidgetPointerIconBehavior(mActivity.findViewById(R.id.button));
    }

    @UiThreadTest
    @Test
    public void testImageButton() {
        assertDefaultWidgetPointerIconBehavior(mActivity.findViewById(R.id.image_button));
    }

    @UiThreadTest
    @Test
    public void testSpinnerButton() {
        assertDefaultWidgetPointerIconBehavior(mActivity.findViewById(R.id.spinner));
    }

    @Test
    public void testTabWidget() throws Throwable {
        final TabHost tabHost = (TabHost) mActivity.findViewById(android.R.id.tabhost);

        WidgetTestUtils.runOnMainAndLayoutSync(
                mActivityRule,
                () -> {
                    tabHost.setup();
                    tabHost.addTab(createTabSpec(tabHost, "Tab 0", null));
                    tabHost.addTab(createTabSpec(tabHost, "Tab 1", mHandIcon));
                    tabHost.addTab(createTabSpec(tabHost, "Tab 2", mHelpIcon));
                },
                false /* force layout */);

        mActivityRule.runOnUiThread(() -> {
            final TabWidget tabWidget = tabHost.getTabWidget();

            tabWidget.setEnabled(false);
            assertPointerIcon("Disabled Tab 0", null, tabWidget.getChildTabViewAt(0));
            assertPointerIcon("Disabled Tab 1", null, tabWidget.getChildTabViewAt(1));
            assertPointerIcon("Disabled Tab 2", null, tabWidget.getChildTabViewAt(2));

            tabWidget.setEnabled(true);
            assertPointerIcon("Tab 0", mHandIcon, tabWidget.getChildTabViewAt(0));
            assertPointerIcon("Tab 1", mHandIcon, tabWidget.getChildTabViewAt(1));
            assertPointerIcon("Tab 2", mHelpIcon, tabWidget.getChildTabViewAt(2));
        });
    }
}
