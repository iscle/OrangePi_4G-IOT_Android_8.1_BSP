/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.bots;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.hasFocus;
import static android.support.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static android.support.test.espresso.matcher.ViewMatchers.withClassName;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.endsWith;

import android.content.Context;
import android.support.test.espresso.Espresso;
import android.support.test.espresso.action.ViewActions;
import android.support.test.espresso.matcher.BoundedMatcher;
import android.support.test.espresso.matcher.ViewMatchers;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.util.TypedValue;
import android.view.View;
import android.widget.Toolbar;

import com.android.documentsui.R;

import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.util.Iterator;
import java.util.List;

/**
 * A test helper class that provides support for controlling DocumentsUI activities
 * programmatically, and making assertions against the state of the UI.
 * <p>
 * Support for working directly with Roots and Directory view can be found in the respective bots.
 */
public class UiBot extends Bots.BaseBot {

    public static final String TARGET_PKG = "com.android.documentsui";

    @SuppressWarnings("unchecked")
    private static final Matcher<View> TOOLBAR = allOf(
            isAssignableFrom(Toolbar.class),
            withId(R.id.toolbar));

    @SuppressWarnings("unchecked")
    private static final Matcher<View> ACTIONBAR = allOf(
            withClassName(endsWith("ActionBarContextView")));

    @SuppressWarnings("unchecked")
    private static final Matcher<View> TEXT_ENTRY = allOf(
            withClassName(endsWith("EditText")));

    @SuppressWarnings("unchecked")
    private static final Matcher<View> TOOLBAR_OVERFLOW = allOf(
            withClassName(endsWith("OverflowMenuButton")),
            ViewMatchers.isDescendantOfA(TOOLBAR));

    @SuppressWarnings("unchecked")
    private static final Matcher<View> ACTIONBAR_OVERFLOW = allOf(
            withClassName(endsWith("OverflowMenuButton")),
            ViewMatchers.isDescendantOfA(ACTIONBAR));

    public UiBot(UiDevice device, Context context, int timeout) {
        super(device, context, timeout);
    }

    public void assertWindowTitle(String expected) {
        onView(TOOLBAR)
                .check(matches(withToolbarTitle(is(expected))));
    }

    public void assertMenuEnabled(int id, boolean enabled) {
        UiObject2 menu = findMenuWithName(mContext.getString(id));
        assertNotNull(menu);
        assertEquals(enabled, menu.isEnabled());
    }

    public void assertInActionMode(boolean inActionMode) {
        assertEquals(inActionMode, waitForActionModeBarToAppear());
    }

    public UiObject openOverflowMenu() throws UiObjectNotFoundException {
        UiObject obj = findMenuMoreOptions();
        obj.click();
        mDevice.waitForIdle(mTimeout);
        return obj;
    }

    public void setDialogText(String text) throws UiObjectNotFoundException {
        onView(TEXT_ENTRY)
                .perform(ViewActions.replaceText(text));
    }

    public void assertDialogText(String expected) throws UiObjectNotFoundException {
        onView(TEXT_ENTRY)
                .check(matches(withText(is(expected))));
    }

    public boolean inFixedLayout() {
        TypedValue val = new TypedValue();
        // We alias files_activity to either fixed or drawer layouts based
        // on screen dimensions. In order to determine which layout
        // has been selected, we check the resolved value.
        mContext.getResources().getValue(R.layout.files_activity, val, true);
        return val.resourceId == R.layout.fixed_layout;
    }

    public boolean inDrawerLayout() {
        return !inFixedLayout();
    }

    public void switchToListMode() {
        final UiObject2 listMode = menuListMode();
        if (listMode != null) {
            listMode.click();
        }
    }

    public void clickActionItem(String label) throws UiObjectNotFoundException {
        if (!waitForActionModeBarToAppear()) {
            throw new UiObjectNotFoundException("ActionMode bar not found");
        }
        clickActionbarOverflowItem(label);
        mDevice.waitForIdle();
    }

    public void switchToGridMode() {
        final UiObject2 gridMode = menuGridMode();
        if (gridMode != null) {
            gridMode.click();
        }
    }

    UiObject2 menuGridMode() {
        // Note that we're using By.desc rather than By.res, because of b/25285770
        return find(By.desc("Grid view"));
    }

    UiObject2 menuListMode() {
        // Note that we're using By.desc rather than By.res, because of b/25285770
        return find(By.desc("List view"));
    }

    public void clickToolbarItem(int id) {
        onView(withId(id)).perform(click());
    }

    public void clickNewFolder() {
        onView(ACTIONBAR_OVERFLOW).perform(click());

        // Click the item by label, since Espresso doesn't support lookup by id on overflow.
        onView(withText("New folder")).perform(click());
    }

    public void clickActionbarOverflowItem(String label) {
        onView(ACTIONBAR_OVERFLOW).perform(click());
        // Click the item by label, since Espresso doesn't support lookup by id on overflow.
        onView(withText(label)).perform(click());
    }

    public void clickToolbarOverflowItem(String label) {
        onView(TOOLBAR_OVERFLOW).perform(click());
        // Click the item by label, since Espresso doesn't support lookup by id on overflow.
        onView(withText(label)).perform(click());
    }

    public boolean waitForActionModeBarToAppear() {
        UiObject2 bar =
                mDevice.wait(Until.findObject(By.res("android:id/action_mode_bar")), mTimeout);
        return (bar != null);
    }

    public UiObject findDownloadRetryDialog() {
        UiSelector selector = new UiSelector().text("Couldn't download");
        UiObject title = mDevice.findObject(selector);
        title.waitForExists(mTimeout);
        return title;
    }

    public UiObject findFileRenameDialog() {
        UiSelector selector = new UiSelector().text("Rename");
        UiObject title = mDevice.findObject(selector);
        title.waitForExists(mTimeout);
        return title;
    }

    public UiObject findRenameErrorMessage() {
        UiSelector selector = new UiSelector().text(mContext.getString(R.string.name_conflict));
        UiObject title = mDevice.findObject(selector);
        title.waitForExists(mTimeout);
        return title;
    }

    @SuppressWarnings("unchecked")
    public void assertDialogOkButtonFocused() {
        onView(withId(android.R.id.button1)).check(matches(hasFocus()));
    }

    public void clickDialogOkButton() {
        // Espresso has flaky results when keyboard shows up, so hiding it for now
        // before trying to click on any dialog button
        Espresso.closeSoftKeyboard();
        onView(withId(android.R.id.button1)).perform(click());
    }

    public void clickDialogCancelButton() throws UiObjectNotFoundException {
        // Espresso has flaky results when keyboard shows up, so hiding it for now
        // before trying to click on any dialog button
        Espresso.closeSoftKeyboard();
        onView(withId(android.R.id.button2)).perform(click());
    }

    UiObject findMenuLabelWithName(String label) {
        UiSelector selector = new UiSelector().text(label);
        return mDevice.findObject(selector);
    }

    UiObject2 findMenuWithName(String label) {
        List<UiObject2> menuItems = mDevice.findObjects(By.clazz("android.widget.LinearLayout"));
        Iterator<UiObject2> it = menuItems.iterator();

        UiObject2 menuItem = null;
        while (it.hasNext()) {
            menuItem = it.next();
            UiObject2 text = menuItem.findObject(By.text(label));
            if (text != null) {
                break;
            }
        }
        return menuItem;
    }

    boolean hasMenuWithName(String label) {
        return findMenuWithName(label) != null;
    }

    UiObject findMenuMoreOptions() {
        UiSelector selector = new UiSelector().className("android.widget.ImageButton")
                .descriptionContains("More options");
        // TODO: use the system string ? android.R.string.action_menu_overflow_description
        return mDevice.findObject(selector);
    }

    private static Matcher<Object> withToolbarTitle(
            final Matcher<CharSequence> textMatcher) {
        return new BoundedMatcher<Object, Toolbar>(Toolbar.class) {
            @Override
            public boolean matchesSafely(Toolbar toolbar) {
                return textMatcher.matches(toolbar.getTitle());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("with toolbar title: ");
                textMatcher.describeTo(description);
            }
        };
    }
}
