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

package com.android.documentsui.dirlist;

import android.annotation.Nullable;
import android.view.KeyEvent;
import android.view.View;

/**
 * A class that manages focus and keyboard driven navigation in the activity.
 */
public interface FocusHandler extends View.OnFocusChangeListener {

    /**
     * Handles navigation (setting focus, adjusting selection if needed) arising from incoming key
     * events.
     *
     * @param doc The DocumentHolder receiving the key event.
     * @param keyCode
     * @param event
     * @return Whether the event was handled.
     */
    boolean handleKey(DocumentHolder doc, int keyCode, KeyEvent event);

    @Override
    void onFocusChange(View v, boolean hasFocus);

    void onLayoutCompleted();

    void focusDocument(String modelId);

    /**
     * Requests focus on the the directory list. Will specifically
     * attempt to focus the item in the directory list that last had focus.
     * Scrolls to that item if necessary.
     *
     * <p>If focus is unsuccessful, return false.
     */
    boolean focusDirectoryList();

    /**
     * Attempts to advance the focus to the next available focus area
     * in the app. As of this writing, known focus areas are the sidebar
     * and the directory list (specifically an item in the directory list).
     */
    boolean advanceFocusArea();

    /**
     * @return The adapter position of the last focused item.
     */
    int getFocusPosition();

    /**
     * @return True if there is currently an item in focus, false otherwise.
     */
    boolean hasFocusedItem();

    /**
     * If there is an item which has focus, the focus is removed.
     */
    void clearFocus();

    /**
     * @return The modelId of the last focused item. If no item is focused, this should return null.
     */
    @Nullable String getFocusModelId();
}
