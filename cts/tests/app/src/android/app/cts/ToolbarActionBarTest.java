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
package android.app.cts;

import android.app.stubs.R;
import android.app.stubs.ToolbarActivity;
import android.test.ActivityInstrumentationTestCase2;
import android.view.KeyEvent;

import android.view.Window;

public class ToolbarActionBarTest extends ActivityInstrumentationTestCase2<ToolbarActivity> {

    private ToolbarActivity mActivity;

    public ToolbarActionBarTest() {
        super(ToolbarActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        getInstrumentation().runOnMainSync(
                () -> mActivity.getToolbar().inflateMenu(R.menu.flat_menu));
    }

    public void testOptionsMenuKey() {
        if (!mActivity.getWindow().hasFeature(Window.FEATURE_OPTIONS_PANEL)) {
            return;
        }
        final boolean menuIsVisible[] = {false};
        mActivity.getActionBar().addOnMenuVisibilityListener(
                isVisible -> menuIsVisible[0] = isVisible);
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);
        getInstrumentation().waitForIdleSync();
        assertTrue(menuIsVisible[0]);
        assertTrue(mActivity.getToolbar().isOverflowMenuShowing());
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);
        getInstrumentation().waitForIdleSync();
        assertFalse(menuIsVisible[0]);
        assertFalse(mActivity.getToolbar().isOverflowMenuShowing());
    }

    public void testOpenOptionsMenu() {
        if (!mActivity.getWindow().hasFeature(Window.FEATURE_OPTIONS_PANEL)) {
            return;
        }
        final boolean menuIsVisible[] = {false};
        mActivity.getActionBar().addOnMenuVisibilityListener(
                isVisible -> menuIsVisible[0] = isVisible);
        getInstrumentation().runOnMainSync(() -> mActivity.openOptionsMenu());
        getInstrumentation().waitForIdleSync();
        assertTrue(menuIsVisible[0]);
        assertTrue(mActivity.getToolbar().isOverflowMenuShowing());
        getInstrumentation().runOnMainSync(() -> mActivity.closeOptionsMenu());
        getInstrumentation().waitForIdleSync();
        assertFalse(menuIsVisible[0]);
        assertFalse(mActivity.getToolbar().isOverflowMenuShowing());
    }
}
