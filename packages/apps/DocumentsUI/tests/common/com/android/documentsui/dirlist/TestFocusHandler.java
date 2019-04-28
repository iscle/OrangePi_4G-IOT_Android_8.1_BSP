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

import android.view.KeyEvent;
import android.view.View;

/**
 * A purely dummy instance of FocusHandler.
 */
public final class TestFocusHandler implements FocusHandler {

    public boolean handleKey;
    public int focusPos = 0;
    public String focusModelId;
    public boolean advanceFocusAreaCalled;
    public boolean focusDirectoryCalled;

    @Override
    public boolean handleKey(DocumentHolder doc, int keyCode, KeyEvent event) {
        return handleKey;
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
    }

    @Override
    public boolean advanceFocusArea() {
        advanceFocusAreaCalled = true;
        return true;
    }

    @Override
    public boolean focusDirectoryList() {
        focusDirectoryCalled = true;
        return true;
    }

    @Override
    public boolean hasFocusedItem() {
        return focusModelId != null;
    }

    @Override
    public int getFocusPosition() {
        return focusPos;
    }

    @Override
    public String getFocusModelId() {
        return focusModelId;
    }

    @Override
    public void focusDocument(String modelId) {
        focusModelId = modelId;
    }

    @Override
    public void onLayoutCompleted() {
    }

    @Override
    public void clearFocus() {
        focusModelId = null;
        focusPos = 0;
    }
}