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

package com.android.cts.managedprofile;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * This class contains tests for cross profile widget providers that are run on the managed
 * profile. Policies are set using {@link SetPolicyActivity} and then verified in these tests.
 * The tests cannot be run independently, but are part of one hostside test.
 */
public class CrossProfileWidgetTest extends BaseManagedProfileTest {
    static final String WIDGET_PROVIDER_PKG = "com.android.cts.widgetprovider";

    private AppWidgetManager mAppWidgetManager;

    public void setUp() throws Exception {
        super.setUp();
        mAppWidgetManager = (AppWidgetManager) mContext.getSystemService(Context.APPWIDGET_SERVICE);
    }

    /**
     * This test checks that the widget provider was successfully whitelisted and verifies that
     * if was added successfully and can be found inside the profile.
     */
    public void testCrossProfileWidgetProviderAdded() {
        List<String> providers = mDevicePolicyManager.getCrossProfileWidgetProviders(
                ADMIN_RECEIVER_COMPONENT);
        assertEquals(1, providers.size());
        assertTrue(providers.contains(WIDGET_PROVIDER_PKG));
        // check that widget can be found inside the profile
        assertTrue(containsWidgetProviderPkg(mAppWidgetManager.getInstalledProviders()));
    }

    /**
     * This test verifies that the widget provider was successfully removed from the whitelist.
     */
    public void testCrossProfileWidgetProviderRemoved() {
        List<String> providers = mDevicePolicyManager.getCrossProfileWidgetProviders(
                ADMIN_RECEIVER_COMPONENT);
        assertTrue(providers.isEmpty());
        // Check that widget can still be found inside the profile
        assertTrue(containsWidgetProviderPkg(mAppWidgetManager.getInstalledProviders()));
    }

    private boolean containsWidgetProviderPkg(List<AppWidgetProviderInfo> widgets) {
        for (AppWidgetProviderInfo widget : widgets) {
            if (WIDGET_PROVIDER_PKG.equals(widget.provider.getPackageName())) {
                return true;
            }
        }
        return false;
    }
}
