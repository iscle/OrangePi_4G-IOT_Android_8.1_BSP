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

package android.system.helpers;

import android.content.Context;
import android.content.pm.PackageManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.Until;

import junit.framework.Assert;

/**
 * Implement common helper methods for Hotseat.
 */
public class HotseatHelper {
    private static final int TIMEOUT = 3000;
    private UiDevice mDevice = null;
    private PackageManager mPkgManger = null;
    private Context mContext = null;
    public static HotseatHelper sInstance = null;

    private HotseatHelper() {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mContext = InstrumentationRegistry.getTargetContext();
    }

    public static HotseatHelper getInstance() {
        if (sInstance == null) {
            sInstance = new HotseatHelper();
        }
        return sInstance;
    }

    /**
     * Launch app from hotseat
     * @param textAppName
     * @param appPackage
     */
    public void launchAppFromHotseat(String textAppName, String appPackage) {
        mDevice.pressHome();
        UiObject2 appOnHotseat = mDevice.findObject(By.clazz("android.widget.TextView")
                .desc(textAppName));
        Assert.assertNotNull(textAppName + " app couldn't be found on hotseat", appOnHotseat);
        appOnHotseat.click();
        UiObject2 appLoaded = mDevice.wait(Until.findObject(By.pkg(appPackage)), TIMEOUT * 2);
        Assert.assertNotNull(textAppName + "app did not load on tapping from hotseat", appLoaded);
    }
}