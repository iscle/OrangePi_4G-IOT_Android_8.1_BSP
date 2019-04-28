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

package com.android.tv.menu;

import com.android.tv.menu.Menu.MenuShowReason;

import java.util.List;

/**
 * An base interface for menu view.
 */
public interface IMenuView {
    /**
     * Sets menu rows.
     */
    void setMenuRows(List<MenuRow> menuRows);

    /**
     * Shows the main menu.
     *
     * <p> The inherited class should show the menu and select the row corresponding to
     * {@code rowIdToSelect}. If the menu is already visible, change the current selection to the
     * given row.
     *
     * @param reason A reason why this is called. See {@link MenuShowReason}.
     * @param rowIdToSelect An ID of the row which corresponds to the {@code reason}.
     */
    void onShow(@MenuShowReason int reason, String rowIdToSelect, Runnable runnableAfterShow);

    /**
     * Hides the main menu
     */
    void onHide();

    /**
     * Updates the menu contents.
     *
     * <p>Returns <@code true> if the contents have been changed, otherwise {@code false}.
     */
    boolean update(boolean menuActive);

    /**
     * Updates the menu row.
     *
     * <p>Returns <@code true> if the contents have been changed, otherwise {@code false}.
     */
    boolean update(String rowId, boolean menuActive);

    /**
     * Checks if the menu view is visible or not.
     */
    boolean isVisible();
}
