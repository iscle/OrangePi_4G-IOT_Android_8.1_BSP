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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Instrumentation;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.Menu;
import android.view.MenuItem;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class MenuItemTest {
    private Instrumentation mInstrumentation;
    private MenuItemCtsActivity mActivity;
    private Menu mMenu;

    @Rule
    public ActivityTestRule<MenuItemCtsActivity> mActivityRule =
            new ActivityTestRule<>(MenuItemCtsActivity.class);


    @UiThreadTest
    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();

        mActivity.getMainToolbar().inflateMenu(R.menu.menu_regular);
        mMenu = mActivity.getMainToolbar().getMenu();
    }

    @Test
    public void testAccessIconTint() {
        // Note that this test is not marked as @UiThreadTest. Updating MenuItem does not
        // immediately update the displayed content, and even though the getters are expected
        // to immediately return the just-set value, using instrumentation to wait for the
        // update to propagate makes this test more in line with the "real" application
        // experience.
        MenuItem firstItem = mMenu.getItem(0);
        MenuItem secondItem = mMenu.getItem(1);
        MenuItem thirdItem = mMenu.getItem(2);

        // These are the default set in layout XML
        assertEquals(Color.WHITE, firstItem.getIconTintList().getDefaultColor());
        assertNull(firstItem.getIconTintMode());
        assertNull(secondItem.getIconTintList());
        assertEquals(PorterDuff.Mode.SCREEN, secondItem.getIconTintMode());
        assertNull(thirdItem.getIconTintList());
        assertNull(thirdItem.getIconTintMode());

        // Change tint color list and mode and verify that they are returned by the getters
        ColorStateList colors = ColorStateList.valueOf(Color.RED);
        mInstrumentation.runOnMainSync(() -> {
            firstItem.setIconTintList(colors);
            firstItem.setIconTintMode(PorterDuff.Mode.XOR);
        });
        mInstrumentation.waitForIdleSync();
        assertSame(colors, firstItem.getIconTintList());
        assertEquals(PorterDuff.Mode.XOR, firstItem.getIconTintMode());

        // Ensure the tint is preserved across drawable changes.
        mInstrumentation.runOnMainSync(() -> firstItem.setIcon(R.drawable.icon_yellow));
        mInstrumentation.waitForIdleSync();
        assertSame(colors, firstItem.getIconTintList());
        assertEquals(PorterDuff.Mode.XOR, firstItem.getIconTintMode());

        // Change tint color list and mode again and verify that they are returned by the getters
        ColorStateList colorsNew = ColorStateList.valueOf(Color.MAGENTA);
        mInstrumentation.runOnMainSync(() -> {
            firstItem.setIconTintList(colorsNew);
            firstItem.setIconTintMode(PorterDuff.Mode.SRC_IN);
        });
        mInstrumentation.waitForIdleSync();
        assertSame(colorsNew, firstItem.getIconTintList());
        assertEquals(PorterDuff.Mode.SRC_IN, firstItem.getIconTintMode());
    }
}
