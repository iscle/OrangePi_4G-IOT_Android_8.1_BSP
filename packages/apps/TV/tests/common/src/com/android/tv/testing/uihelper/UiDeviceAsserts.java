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
package com.android.tv.testing.uihelper;

import static com.android.tv.testing.uihelper.Constants.FOCUSED_VIEW;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.SearchCondition;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import junit.framework.Assert;

/**
 * Asserts for {@link UiDevice}s.
 */
public final class UiDeviceAsserts {

    public static void assertHas(UiDevice uiDevice, BySelector bySelector, boolean expected) {
        assertEquals("Has " + bySelector, expected, uiDevice.hasObject(bySelector));
    }

    public static void assertWaitUntilFocused(UiDevice uiDevice, BySelector bySelector) {
        UiObject2 uiObject = uiDevice.findObject(bySelector);
        assertNotNull(uiObject);
        assertTrue(uiObject.wait(Until.focused(true), Constants.MAX_FOCUSED_DELAY_MILLIS));
    }

    /**
     * Assert that {@code searchCondition} becomes true within
     * {@value Constants#MAX_SHOW_DELAY_MILLIS} milliseconds.
     *
     * @param uiDevice        the device under test.
     * @param searchCondition the condition to wait for.
     */
    public static void assertWaitForCondition(UiDevice uiDevice,
            SearchCondition<Boolean> searchCondition) {
        assertWaitForCondition(uiDevice, searchCondition, Constants.MAX_SHOW_DELAY_MILLIS);
    }

    /**
     * Assert that {@code searchCondition} becomes true within {@code timeout} milliseconds.
     *
     * @param uiDevice        the device under test.
     * @param searchCondition the condition to wait for.
     */
    public static void assertWaitForCondition(UiDevice uiDevice,
            SearchCondition<Boolean> searchCondition, long timeout) {
        boolean result = waitForCondition(uiDevice, searchCondition, timeout);
        assertTrue(searchCondition + " not true after " + timeout / 1000.0 + " seconds.", result);
    }

    /**
     * Wait until {@code searchCondition} becomes true.
     *
     * @param uiDevice        The device under test.
     * @param searchCondition The condition to wait for.
     * @return {@code true} if the condition is met, otherwise {@code false}.
     */
    public static boolean waitForCondition(UiDevice uiDevice,
            SearchCondition<Boolean> searchCondition) {
        return waitForCondition(uiDevice, searchCondition, Constants.MAX_SHOW_DELAY_MILLIS);
    }

    private static boolean waitForCondition(UiDevice uiDevice,
            SearchCondition<Boolean> searchCondition, long timeout) {
        long adjustedTimeout = timeout + Math.max(Constants.MIN_EXTRA_TIMEOUT,
                (long) (timeout * Constants.EXTRA_TIMEOUT_PERCENT));
        return uiDevice.wait(searchCondition, adjustedTimeout);
    }

    /**
     * Navigates through the focus items in a container returning the container child that has a
     * descendant matching the {@code selector}.
     * <p>
     * The navigation starts in the {@code direction} specified and
     * {@link Direction#reverse(Direction) reverses} once if needed. Fails if there is not a
     * focused
     * descendant, or if after completing both directions no focused child has a descendant
     * matching
     * {@code selector}.
     * <p>
     * Fails if the menu item can not be navigated to.
     *
     * @param uiDevice  the device under test.
     * @param container contains children to navigate over.
     * @param selector  the selector for the object to navigate to.
     * @param direction the direction to start navigating.
     * @return the object navigated to.
     */
    public static UiObject2 assertNavigateTo(UiDevice uiDevice, UiObject2 container,
            BySelector selector, Direction direction) {
        int count = 0;
        while (count < 2) {
            BySelector hasFocusedDescendant = By.hasDescendant(FOCUSED_VIEW);
            UiObject2 focusedChild = null;
            SearchCondition<Boolean> untilHasFocusedDescendant = Until
                    .hasObject(hasFocusedDescendant);

            boolean result = container.wait(untilHasFocusedDescendant,
                    UiObject2Asserts.getAdjustedTimeout(Constants.MAX_SHOW_DELAY_MILLIS));
            if (!result) {
                // HACK: Try direction anyways because play control does not always have a
                // focused item.
                UiDeviceUtils.pressDpad(uiDevice, direction);
                UiObject2Asserts.assertWaitForCondition(container, untilHasFocusedDescendant);
            }

            for (UiObject2 c : container.getChildren()) {
                if (c.isFocused() || c.hasObject(hasFocusedDescendant)) {
                    focusedChild = c;
                    break;
                }
            }
            if (focusedChild == null) {
                Assert.fail("No focused item found in container " + container);
            }
            if (focusedChild.hasObject(selector)) {
                return focusedChild;
            }
            if (!UiObject2Utils.hasSiblingInDirection(focusedChild, direction)) {
                direction = Direction.reverse(direction);
                count++;
            }
            UiDeviceUtils.pressDpad(uiDevice, direction);
        }
        Assert.fail("Could not find item with  " + selector);
        return null;
    }

    private UiDeviceAsserts() {
    }
}
