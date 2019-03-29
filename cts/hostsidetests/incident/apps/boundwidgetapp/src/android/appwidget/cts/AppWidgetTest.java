/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.appwidget.cts;

import android.appwidget.AppWidgetProviderInfo;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.cts.provider.FirstAppWidgetProvider;
import android.appwidget.cts.provider.SecondAppWidgetProvider;
import android.os.Bundle;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;
import android.content.ComponentName;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.test.InstrumentationTestCase;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@MediumTest
public class AppWidgetTest extends InstrumentationTestCase {

    private static final String TAG = "AppWidgetTest";
    private static final int HOST_ID = 1001;
    private static final String GRANT_BIND_APP_WIDGET_PERMISSION_COMMAND =
        "appwidget grantbind --package android.appwidget.cts --user 0";

    private Context mContext;

    @Override
    public void setUp() throws Exception {
        mContext = getInstrumentation().getTargetContext();
        // Workaround for dexmaker bug: https://code.google.com/p/dexmaker/issues/detail?id=2
        // Dexmaker is used by mockito.
        System.setProperty("dexmaker.dexcache", mContext.getCacheDir().getPath());
    }

    public void testBindWidget1() throws Exception {
        Log.d(TAG, "binding widget 1 ...");
        setupWidget(getFirstAppWidgetProviderInfo(), null, true);
        Log.d(TAG, "binding widget 1 done");
    }

    public void testBindWidget2() throws Exception {
        Log.d(TAG, "binding widget 2 ...");
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 1);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 2);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 3);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 4);
        setupWidget(getSecondAppWidgetProviderInfo(), options, true);
        Log.d(TAG, "binding widget 2 done");
    }

    public void testAllocateOnlyWidget1() throws Exception {
        Log.d(TAG, "allocating widget 1 ...");
        setupWidget(getFirstAppWidgetProviderInfo(), null, false);
        Log.d(TAG, "allocating widget 1 done");
    }

    public void testCleanup() throws Exception {
        Log.d(TAG, "deleting host");
        grantBindAppWidgetPermission();
        AppWidgetHost host = new AppWidgetHost(mContext, HOST_ID);
        host.deleteHost();
    }

    private void setupWidget(AppWidgetProviderInfo prov, Bundle options, boolean bindWidget) throws Exception {
        // We want to bind widgets.
        grantBindAppWidgetPermission();

        // Create a host and start listening.
        AppWidgetHost host = new AppWidgetHost(mContext, HOST_ID);
        host.deleteHost();
        host.startListening();

        AppWidgetManager mgr = getAppWidgetManager();

        // Initially we have no widgets.
        assertEquals(0, mgr.getAppWidgetIds(prov.provider).length);

        // Allocate the first widget id to bind.
        int appWidgetId = host.allocateAppWidgetId();

        if (bindWidget) {
            // Bind the widget.
            getAppWidgetManager().bindAppWidgetIdIfAllowed(appWidgetId,
                prov.getProfile(), prov.provider, options);

            assertEquals(1, mgr.getAppWidgetIds(prov.provider).length);
        }
    }

    private ComponentName getFirstWidgetComponent() {
        return new ComponentName(mContext.getPackageName(),
                FirstAppWidgetProvider.class.getName());
    }

    private ComponentName getSecondWidgetComponent() {
        return new ComponentName(mContext.getPackageName(),
                SecondAppWidgetProvider.class.getName());
    }

    private ArrayList<String> runShellCommand(String command) throws Exception {
        ParcelFileDescriptor pfd = getInstrumentation().getUiAutomation()
                .executeShellCommand(command);

        ArrayList<String> ret = new ArrayList<>();
        // Read the input stream fully.
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(pfd)))) {
            String line;
            while ((line = r.readLine()) != null) {
                ret.add(line);
            }
        }
        return ret;
    }

    private AppWidgetProviderInfo getFirstAppWidgetProviderInfo() {
        return getProviderInfo(getFirstWidgetComponent());
    }

    private AppWidgetProviderInfo getSecondAppWidgetProviderInfo() {
        return getProviderInfo(getSecondWidgetComponent());
    }

    private AppWidgetProviderInfo getProviderInfo(ComponentName componentName) {
        List<AppWidgetProviderInfo> providers = getAppWidgetManager().getInstalledProviders();

        final int providerCount = providers.size();
        for (int i = 0; i < providerCount; i++) {
            AppWidgetProviderInfo provider = providers.get(i);
            if (componentName.equals(provider.provider)
                && Process.myUserHandle().equals(provider.getProfile())) {
                return provider;

            }
        }

        return null;
    }

    private AppWidgetManager getAppWidgetManager() {
        return (AppWidgetManager) getInstrumentation().getTargetContext()
            .getSystemService(Context.APPWIDGET_SERVICE);
    }

    private void grantBindAppWidgetPermission() throws Exception {
        runShellCommand(GRANT_BIND_APP_WIDGET_PERMISSION_COMMAND);
    }

}
