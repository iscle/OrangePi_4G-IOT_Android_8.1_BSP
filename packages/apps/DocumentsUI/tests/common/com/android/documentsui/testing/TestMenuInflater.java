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

package com.android.documentsui.testing;

import android.annotation.MenuRes;
import android.support.test.InstrumentationRegistry;
import android.view.Menu;
import android.view.MenuInflater;

public class TestMenuInflater extends MenuInflater {

    public int lastInflatedMenuId;
    public Menu lastInflatedMenu;

    public TestMenuInflater() {
        super(InstrumentationRegistry.getContext());
    }

    @Override
    public void inflate(@MenuRes int menuId, Menu menu) {
        lastInflatedMenuId = menuId;
        lastInflatedMenu = menu;
    }
}
