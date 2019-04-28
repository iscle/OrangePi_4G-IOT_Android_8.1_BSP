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

import android.content.res.Resources;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;

import com.android.tv.R;
import com.android.tv.ui.sidepanel.SideFragment;

import junit.framework.Assert;

/**
 * Helper for testing {@link SideFragment}s.
 */
public class SidePanelHelper extends BaseUiDeviceHelper {

    public SidePanelHelper(UiDevice uiDevice, Resources targetResources) {
        super(uiDevice, targetResources);
    }

    public BySelector bySidePanelTitled(int titleResId) {
        return By.copy(Constants.SIDE_PANEL)
                .hasDescendant(ByResource.text(mTargetResources, titleResId));
    }

    public BySelector byViewText(int textResId) {
        return By.hasDescendant(ByResource.text(mTargetResources, textResId));
    }

    public UiObject2 assertNavigateToItem(int resId) {
        return assertNavigateToItem(resId, Direction.DOWN);
    }

    public UiObject2 assertNavigateToItem(int resId, Direction direction) {
        String title = mTargetResources.getString(resId);
        return assertNavigateToItem(title, direction);
    }
    public UiObject2 assertNavigateToItem(String title) {
        return assertNavigateToItem(title, Direction.DOWN);
    }

    public UiObject2 assertNavigateToItem(String title, Direction direction) {
        BySelector sidePanelSelector = ByResource.id(mTargetResources, R.id.side_panel_list);
        UiObject2 sidePanelList = mUiDevice.findObject(sidePanelSelector);
        Assert.assertNotNull(sidePanelSelector + " not found", sidePanelList);

        return UiDeviceAsserts.assertNavigateTo(mUiDevice, sidePanelList,
                By.hasDescendant(By.text(title)), direction);
    }
}
