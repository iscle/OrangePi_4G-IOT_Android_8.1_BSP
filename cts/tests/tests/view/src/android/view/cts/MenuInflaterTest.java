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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.PopupMenu;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link MenuInflater}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class MenuInflaterTest {
    private Activity mActivity;
    private MenuInflater mMenuInflater;
    private Menu mMenu;

    @Rule
    public ActivityTestRule<MockActivity> mActivityRule =
            new ActivityTestRule<>(MockActivity.class);

    @UiThreadTest
    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mMenuInflater = mActivity.getMenuInflater();
        mMenu = new PopupMenu(mActivity, null).getMenu();
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        new MenuInflater(mActivity);
    }

    @UiThreadTest
    @Test
    public void testInflate() {
        assertEquals(0, mMenu.size());

        mMenuInflater.inflate(R.menu.browser, mMenu);
        assertEquals(1, mMenu.size());
    }

    @UiThreadTest
    @Test(expected=Resources.NotFoundException.class)
    public void testInflateInvalidId() {
        mMenuInflater.inflate(0, mMenu);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testInflateNullMenu() {
        mMenuInflater.inflate(R.menu.browser, null);
    }

    // Check whether the objects are created correctly from xml files
    @UiThreadTest
    @Test
    public void testInflateAlphabeticShortcutFromXml() {
        // the visibility and shortcut
        mMenuInflater.inflate(R.menu.visible_shortcut, mMenu);

        assertTrue(mMenu.findItem(R.id.visible_item).isVisible());
        assertEquals('a', mMenu.findItem(R.id.visible_item).getAlphabeticShortcut());

        assertFalse(mMenu.findItem(R.id.hidden_item).isVisible());
        assertEquals('b', mMenu.findItem(R.id.hidden_item).getAlphabeticShortcut());

        assertEquals(R.id.hidden_group, mMenu.findItem(R.id.hidden_by_group).getGroupId());
        assertFalse(mMenu.findItem(R.id.hidden_by_group).isVisible());
        assertEquals('c', mMenu.findItem(R.id.hidden_by_group).getAlphabeticShortcut());
    }

    @UiThreadTest
    @Test
    public void testInflateShortcutModifiersFromXml() {
        mMenuInflater.inflate(R.menu.visible_shortcut, mMenu);
        MenuItem mMenuItem;

        mMenuItem = mMenu.findItem(R.id.no_modifiers);
        assertEquals('d', mMenuItem.getAlphabeticShortcut());
        assertEquals(KeyEvent.META_CTRL_ON, mMenuItem.getAlphabeticModifiers());

        mMenuItem = mMenu.findItem(R.id.default_modifiers);
        assertEquals('e', mMenuItem.getAlphabeticShortcut());
        assertEquals(KeyEvent.META_CTRL_ON, mMenuItem.getAlphabeticModifiers());

        mMenuItem = mMenu.findItem(R.id.single_modifier);
        assertEquals('f', mMenuItem.getAlphabeticShortcut());
        assertEquals(KeyEvent.META_SHIFT_ON, mMenuItem.getAlphabeticModifiers());

        mMenuItem = mMenu.findItem(R.id.multiple_modifiers);
        assertEquals('g', mMenuItem.getAlphabeticShortcut());
        assertEquals(KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON,
                mMenuItem.getAlphabeticModifiers());
    }

    @UiThreadTest
    @Test
    public void testInflateDrawableFromXml() {
        // the titles and icons
        mMenuInflater.inflate(R.menu.title_icon, mMenu);

        assertEquals("Start", mMenu.findItem(R.id.start).getTitle());
        verifyDrawableContent((BitmapDrawable) mMenu.findItem(R.id.start).getIcon(),
                R.drawable.start);

        assertEquals("Pass", mMenu.findItem(R.id.pass).getTitle());
        verifyDrawableContent((BitmapDrawable) mMenu.findItem(R.id.pass).getIcon(),
                R.drawable.pass);

        assertEquals("Failed", mMenu.findItem(R.id.failed).getTitle());
        verifyDrawableContent((BitmapDrawable) mMenu.findItem(R.id.failed).getIcon(),
                R.drawable.failed);
    }

    @UiThreadTest
    @Test
    public void testInflateOrderFromXml() {
        // the orders and categories
        mMenuInflater.inflate(R.menu.category_order, mMenu);
        // default category
        assertEquals(R.id.most_used_items, mMenu.findItem(R.id.first_most_item).getGroupId());
        assertEquals(1, mMenu.findItem(R.id.first_most_item).getOrder());

        assertEquals(R.id.most_used_items, mMenu.findItem(R.id.middle_most_item).getGroupId());
        assertEquals(3, mMenu.findItem(R.id.middle_most_item).getOrder());

        assertEquals(R.id.most_used_items, mMenu.findItem(R.id.last_most_item).getGroupId());
        assertEquals(5, mMenu.findItem(R.id.last_most_item).getOrder());

        // specific category (CATEGORY_SECONDARY)
        assertEquals(R.id.least_used_items, mMenu.findItem(R.id.first_least_item).getGroupId());
        assertEquals(Menu.CATEGORY_SECONDARY + 0, mMenu.findItem(R.id.first_least_item).getOrder());

        assertEquals(R.id.least_used_items, mMenu.findItem(R.id.middle_least_item).getGroupId());
        assertEquals(Menu.CATEGORY_SECONDARY + 2,
                mMenu.findItem(R.id.middle_least_item).getOrder());

        assertEquals(R.id.least_used_items, mMenu.findItem(R.id.last_least_item).getGroupId());
        assertEquals(Menu.CATEGORY_SECONDARY + 4, mMenu.findItem(R.id.last_least_item).getOrder());
    }

    @UiThreadTest
    @Test
    public void testInflateCheckableFromXmlNone() {
        mMenuInflater.inflate(R.menu.checkable, mMenu);

        // noncheckables
        assertEquals(R.id.noncheckable_group,
                mMenu.findItem(R.id.noncheckable_item_1).getGroupId());
        assertFalse(mMenu.findItem(R.id.noncheckable_item_1).isCheckable());

        assertEquals(R.id.noncheckable_group,
                mMenu.findItem(R.id.noncheckable_item_2).getGroupId());
        assertFalse(mMenu.findItem(R.id.noncheckable_item_2).isCheckable());

        assertEquals(R.id.noncheckable_group,
                mMenu.findItem(R.id.noncheckable_item_3).getGroupId());
        assertFalse(mMenu.findItem(R.id.noncheckable_item_3).isCheckable());
    }

    @UiThreadTest
    @Test
    public void testInflateCheckableFromXmlMultiples() {
        mMenuInflater.inflate(R.menu.checkable, mMenu);

        // multiple checkables (item 2 and item 3 are both checked)
        assertEquals(R.id.checkable_group, mMenu.findItem(R.id.checkable_item_1).getGroupId());
        assertTrue(mMenu.findItem(R.id.checkable_item_1).isCheckable());
        assertFalse(mMenu.findItem(R.id.checkable_item_1).isChecked());

        assertEquals(R.id.checkable_group, mMenu.findItem(R.id.checkable_item_3).getGroupId());
        assertTrue(mMenu.findItem(R.id.checkable_item_2).isCheckable());
        assertTrue(mMenu.findItem(R.id.checkable_item_2).isChecked());

        assertEquals(R.id.checkable_group, mMenu.findItem(R.id.checkable_item_2).getGroupId());
        assertTrue(mMenu.findItem(R.id.checkable_item_3).isCheckable());
        assertTrue(mMenu.findItem(R.id.checkable_item_3).isChecked());

        // make item 1 checked and item 2 and item 3 will remain checked
        mMenu.findItem(R.id.checkable_item_1).setChecked(true);
        assertTrue(mMenu.findItem(R.id.checkable_item_1).isChecked());
        assertTrue(mMenu.findItem(R.id.checkable_item_2).isChecked());
        assertTrue(mMenu.findItem(R.id.checkable_item_3).isChecked());
    }

    @UiThreadTest
    @Test
    public void testInflateCheckableFromXmlExclusive() {
        mMenuInflater.inflate(R.menu.checkable, mMenu);

        // exclusive checkables (only item 3 is checked)
        assertEquals(R.id.exclusive_checkable_group,
                mMenu.findItem(R.id.exclusive_checkable_item_1).getGroupId());
        assertTrue(mMenu.findItem(R.id.exclusive_checkable_item_1).isCheckable());
        assertFalse(mMenu.findItem(R.id.exclusive_checkable_item_1).isChecked());

        assertEquals(R.id.exclusive_checkable_group,
                mMenu.findItem(R.id.exclusive_checkable_item_3).getGroupId());
        assertTrue(mMenu.findItem(R.id.exclusive_checkable_item_2).isCheckable());
        assertFalse(mMenu.findItem(R.id.exclusive_checkable_item_2).isChecked());

        assertEquals(R.id.exclusive_checkable_group,
                mMenu.findItem(R.id.exclusive_checkable_item_2).getGroupId());
        assertTrue(mMenu.findItem(R.id.exclusive_checkable_item_3).isCheckable());
        assertTrue(mMenu.findItem(R.id.exclusive_checkable_item_3).isChecked());

        // make item 1 checked and item 3 will be unchecked
        mMenu.findItem(R.id.exclusive_checkable_item_1).setChecked(true);
        assertTrue(mMenu.findItem(R.id.exclusive_checkable_item_1).isChecked());
        assertFalse(mMenu.findItem(R.id.exclusive_checkable_item_2).isChecked());
        assertFalse(mMenu.findItem(R.id.exclusive_checkable_item_3).isChecked());
    }

    @UiThreadTest
    @Test
    public void testInflateCheckableFromXmlSubmenu() {
        mMenuInflater.inflate(R.menu.checkable, mMenu);

        // checkables without group (all in a sub menu)
        SubMenu subMenu = mMenu.findItem(R.id.submenu).getSubMenu();
        assertNotNull(subMenu);

        assertTrue(subMenu.findItem(R.id.nongroup_checkable_item_1).isCheckable());
        assertFalse(subMenu.findItem(R.id.nongroup_checkable_item_1).isChecked());

        assertTrue(subMenu.findItem(R.id.nongroup_checkable_item_2).isCheckable());
        assertTrue(subMenu.findItem(R.id.nongroup_checkable_item_2).isChecked());

        assertTrue(subMenu.findItem(R.id.nongroup_checkable_item_3).isCheckable());
        assertTrue(subMenu.findItem(R.id.nongroup_checkable_item_3).isChecked());

        // make item 1 checked and item 2 and item 3 will remain checked
        subMenu.findItem(R.id.nongroup_checkable_item_1).setChecked(true);
        assertTrue(mMenu.findItem(R.id.nongroup_checkable_item_1).isChecked());
        assertTrue(mMenu.findItem(R.id.nongroup_checkable_item_2).isChecked());
        assertTrue(mMenu.findItem(R.id.nongroup_checkable_item_3).isChecked());
    }

    @UiThreadTest
    @Test
    public void testInflateTooltipFromXml() {
        mMenuInflater.inflate(R.menu.tooltip, mMenu);

        MenuItem item1 = mMenu.findItem(R.id.item1);
        MenuItem item2 = mMenu.findItem(R.id.item2);
        MenuItem item3 = mMenu.findItem(R.id.item3);

        assertEquals("tooltip1", item1.getTooltipText());

        assertEquals("tooltip2", item2.getTooltipText());
        item2.setTooltipText(null);
        assertNull(item2.getTooltipText());
        item2.setTooltipText("tooltip2_new");
        assertEquals("tooltip2_new", item2.getTooltipText());

        assertNull(item3.getTooltipText());
        item3.setTooltipText("tooltip3");
        assertEquals("tooltip3", item3.getTooltipText());
    }

    @UiThreadTest
    @Test
    public void testInflateContentDescriptionFromXml() {
        mMenuInflater.inflate(R.menu.content_description, mMenu);

        MenuItem item1 = mMenu.findItem(R.id.item1);
        MenuItem item2 = mMenu.findItem(R.id.item2);
        MenuItem item3 = mMenu.findItem(R.id.item3);

        assertEquals("description1", item1.getContentDescription());

        assertEquals("description2", item2.getContentDescription());
        item2.setContentDescription(null);
        assertNull(item2.getContentDescription());
        item2.setContentDescription("description2_new");
        assertEquals("description2_new", item2.getContentDescription());

        assertNull(item3.getContentDescription());
        item3.setContentDescription("description3");
        assertEquals("description3", item3.getContentDescription());
    }

    private void verifyDrawableContent(BitmapDrawable b, int resId) {
        Bitmap expected = BitmapFactory.decodeResource(mActivity.getResources(), resId);
        WidgetTestUtils.assertEquals(expected, b.getBitmap());
    }
}
