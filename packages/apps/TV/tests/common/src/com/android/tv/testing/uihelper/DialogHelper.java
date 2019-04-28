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

import static com.android.tv.testing.uihelper.UiDeviceAsserts.assertWaitForCondition;
import static com.android.tv.testing.uihelper.UiDeviceAsserts.waitForCondition;

import android.app.DialogFragment;
import android.content.res.Resources;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;

import com.android.tv.R;

/**
 * Helper for testing {@link DialogFragment}s.
 */
public class DialogHelper extends BaseUiDeviceHelper {
    private final BySelector byPinDialog;

    public DialogHelper(UiDevice uiDevice, Resources targetResources) {
        super(uiDevice, targetResources);
        byPinDialog = ByResource.id(mTargetResources, R.id.enter_pin);
    }

    public void assertWaitForPinDialogOpen() {
        assertWaitForCondition(mUiDevice, Until.hasObject(byPinDialog),
                Constants.MAX_SHOW_DELAY_MILLIS
                        + mTargetResources.getInteger(R.integer.pin_dialog_anim_duration));
    }

    public void assertWaitForPinDialogClose() {
        assertWaitForCondition(mUiDevice, Until.gone(byPinDialog));
    }

    public void enterPinCodes() {
        // Enter PIN code '0000' by pressing ENTER key four times.
        mUiDevice.pressEnter();
        mUiDevice.pressEnter();
        mUiDevice.pressEnter();
        mUiDevice.pressEnter();
        boolean result = waitForCondition(mUiDevice, Until.gone(byPinDialog));
        if (!result) {
            // It's the first time. Confirm the PIN code.
            mUiDevice.pressEnter();
            mUiDevice.pressEnter();
            mUiDevice.pressEnter();
            mUiDevice.pressEnter();
        }
    }
}
