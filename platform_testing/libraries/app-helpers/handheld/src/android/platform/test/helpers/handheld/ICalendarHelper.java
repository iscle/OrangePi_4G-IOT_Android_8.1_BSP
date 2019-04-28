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
 * limitations under the License
 */

package android.platform.test.helpers;

import android.support.test.uiautomator.Direction;

public interface ICalendarHelper extends IStandardAppHelper {
    // Enumeration of the available Calendar pages.
    public enum Page { DAY, MONTH, SCHEDULE, THREE_DAY, WEEK }

    /**
     * Setup expectations: Calendar is open with all menus closed.
     * <p>
     * Opens the navigation drawer.
     */
    public void openNavigationDrawer();

    /**
     * Setup expectations: Calendar is open and the navigation drawer is open.
     * <p>
     * Closes the navigation drawer.
     */
    public void closeNavigationDrawer();

    /**
     * Setup expectations: Calendar is open on a page other than {@link Page.MONTH} with all menus
     * closed.
     * <p>
     * Opens the month dropdown.
     */
    public void openMonthDropdown();

    /**
     * Setup expectations: Calendar is open on a page other than {@link Page.MONTH} and the month
     * dropdown is open.
     * <p>
     * Closes the month dropdown.
     */
    public void closeMonthDropdown();

    /**
     * Setup expectations: Calendar is open with all menus closed.
     * <p>
     * Opens the {@link FloatingActionButton} menu.
     */
    public void openActionMenu();

    /**
     * Setup expectations: Calendar is open and the action menu is open.
     * <p>
     * Closes the {@link FloatingActionButton} menu.
     */
    public void closeActionMenu();

    /**
     * Setup expectations: Calendar is open and the navigation drawer is open.
     * <p>
     * Flings the navigation drawer in the supplied {@link Direction}.
     * <p>
     * @param dir the {@link Direction} to fling the drawer.
     */
    public void flingNavigationDrawer(Direction dir);

    /**
     * Setup expectations: Calendar is open and the navigation drawer is open.
     * <p>
     * Selects the supplied {@link Page} from the navigation drawer.
     * <p>
     * @param page the {@link Page} or layout to select.
     */
    public void selectPage(Page page);

    /**
     * Setup expectations: Calendar is open on the supplied {@link Page} with all menus closed.
     * <p>
     * Flings the supplied {@link Page} in the supplied {@link Direction}.
     * <p>
     * @param page the {@link Page} or layout to select.
     * @param dir the {@link Direction} to fling in.
     */
    public void flingPage(Page page, Direction dir);
}
