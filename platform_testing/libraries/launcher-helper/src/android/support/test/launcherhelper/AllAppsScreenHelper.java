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

package android.support.test.launcherhelper;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.pm.PackageManager;
import android.content.Context;
import android.os.RemoteException;
import android.support.test.InstrumentationRegistry;
import android.support.test.launcherhelper.ILauncherStrategy;
import android.support.test.launcherhelper.LauncherStrategyFactory;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.Until;
import android.system.helpers.ActivityHelper;
import android.test.InstrumentationTestCase;

import java.io.File;
import java.io.IOException;

import org.junit.Assert;

public class AllAppsScreenHelper {

    private static final int TIMEOUT = 3000;
    private static final int LONG_TIMEOUT = 10000;
    private UiDevice mDevice;
    private Instrumentation mInstrumentation;
    private ILauncherStrategy mLauncherStrategy = LauncherStrategyFactory
            .getInstance(mDevice).getLauncherStrategy();
    private String allApps = "apps_view";
    private String appsListView = "apps_list_view";
    private String searchBox = "search_box_input";
    private ActivityHelper mActivityHelper;

    public AllAppsScreenHelper() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivityHelper = ActivityHelper.getInstance();
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    public String getLauncherPackage() {
        return mDevice.getLauncherPackageName();
    }

    public void launchAllAppsScreen() {
        mDevice.pressHome();
        mDevice.findObject(mLauncherStrategy.getAllAppsButtonSelector()).click();
        mDevice.wait(Until.hasObject(By.res(getLauncherPackage(), allApps)), TIMEOUT);
    }

    public void searchAllAppsScreen(String searchString,
        String[] appNamesExpected) throws Exception {
        launchAllAppsScreen();
        UiObject2 searchBoxObject = mDevice.wait(Until.findObject
                (By.res(getLauncherPackage(), searchBox)), TIMEOUT);
        searchBoxObject.setText(searchString);
        for (String appName : appNamesExpected) {
            Assert.assertNotNull("The following app couldn't be found in the search results: "
                    + appName, mDevice.wait(Until.findObject
                    (By.text(appName)), TIMEOUT));
        }
    }
}
