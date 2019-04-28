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

import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.assertFalse;

import android.content.Context;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObjectNotFoundException;

import java.util.Map;

/**
 * A test helper class that provides support for controlling menu items.
 */
public class MenuBot extends Bots.BaseBot {

    public MenuBot(
            UiDevice device, Context context, int timeout) {
        super(device, context, timeout);
    }

    public boolean hasMenuItem(String menuLabel) throws UiObjectNotFoundException {
        return mDevice.findObject(By.text(menuLabel)) != null;
    }

    public void assertPresentMenuItems(Map<String, Boolean> menuStates) throws Exception {
        for (String key : menuStates.keySet()) {
            if (menuStates.get(key)) {
                assertTrue(key + " expected to be shown", hasMenuItem(key));
            } else {
                assertFalse(key + " expected not to be shown", hasMenuItem(key));
            }
        }
    }
}
