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
import android.appwidget.cts.provider.FirstAppWidgetProvider;
import android.appwidget.cts.provider.SecondAppWidgetProvider;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.test.InstrumentationTestCase;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public abstract class AppWidgetTestCase extends InstrumentationTestCase {
    private static final String FIRST_APP_WIDGET_CONFIGURE_ACTIVITY =
            "android.appwidget.cts.provider.FirstAppWidgetConfigureActivity";

    private static final String SECOND_APP_WIDGET_CONFIGURE_ACTIVITY =
            "android.appwidget.cts.provider.SecondAppWidgetConfigureActivity";

    public boolean hasAppWidgets() {
        return getInstrumentation().getTargetContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS);
    }

    public boolean[] verifyInstalledProviders(List<AppWidgetProviderInfo> providers) {
        boolean firstProviderVerified = false;
        boolean secondProviderVerified = false;

        ComponentName firstComponentName = getFirstWidgetComponent();
        ComponentName secondComponentName = getSecondWidgetComponent();

        final int providerCount = providers.size();
        for (int i = 0; i < providerCount; i++) {
            AppWidgetProviderInfo provider = providers.get(i);

            if (firstComponentName.equals(provider.provider)
                    && android.os.Process.myUserHandle().equals(provider.getProfile())) {
                assertEquals(getNormalizedDimensionResource(android.appwidget.cts.R.dimen.first_min_appwidget_size),
                        provider.minWidth);
                assertEquals(getNormalizedDimensionResource(android.appwidget.cts.R.dimen.first_min_appwidget_size),
                        provider.minHeight);
                assertEquals(getNormalizedDimensionResource(
                        android.appwidget.cts.R.dimen.first_min_resize_appwidget_size), provider.minResizeWidth);
                assertEquals(getNormalizedDimensionResource(
                        android.appwidget.cts.R.dimen.first_min_resize_appwidget_size), provider.minResizeHeight);
                assertEquals(getIntResource(android.appwidget.cts.R.integer.first_update_period_millis),
                        provider.updatePeriodMillis);
                assertEquals(getInstrumentation().getTargetContext().getPackageName(),
                        provider.configure.getPackageName());
                assertEquals(FIRST_APP_WIDGET_CONFIGURE_ACTIVITY,
                        provider.configure.getClassName());
                assertEquals(getIntResource(android.appwidget.cts.R.integer.first_resize_mode),
                        provider.resizeMode);
                assertEquals(getIntResource(android.appwidget.cts.R.integer.first_widget_category),
                        provider.widgetCategory);
                assertEquals(android.appwidget.cts.R.layout.first_initial_layout,
                        provider.initialLayout);
                assertEquals(android.appwidget.cts.R.layout.first_initial_keyguard_layout,
                        provider.initialKeyguardLayout);
                assertEquals(android.appwidget.cts.R.drawable.first_android_icon,
                        provider.previewImage);
                assertEquals(android.appwidget.cts.R.id.first_auto_advance_view_id,
                        provider.autoAdvanceViewId);
                firstProviderVerified = true;
            } else if (secondComponentName.equals(provider.provider)
                    && android.os.Process.myUserHandle().equals(provider.getProfile())) {
                assertEquals(getNormalizedDimensionResource(android.appwidget.cts.R.dimen.second_min_appwidget_size),
                        provider.minWidth);
                assertEquals(getNormalizedDimensionResource(android.appwidget.cts.R.dimen.second_min_appwidget_size),
                        provider.minHeight);
                assertEquals(getNormalizedDimensionResource(
                        android.appwidget.cts.R.dimen.second_min_resize_appwidget_size), provider.minResizeWidth);
                assertEquals(getNormalizedDimensionResource(
                        android.appwidget.cts.R.dimen.second_min_resize_appwidget_size), provider.minResizeHeight);
                assertEquals(getIntResource(android.appwidget.cts.R.integer.second_update_period_millis),
                        provider.updatePeriodMillis);
                assertEquals(getInstrumentation().getTargetContext().getPackageName(),
                        provider.configure.getPackageName());
                assertEquals(SECOND_APP_WIDGET_CONFIGURE_ACTIVITY,
                        provider.configure.getClassName());
                assertEquals(getIntResource(android.appwidget.cts.R.integer.second_resize_mode),
                        provider.resizeMode);
                assertEquals(getIntResource(android.appwidget.cts.R.integer.second_widget_category),
                        provider.widgetCategory);
                assertEquals(android.appwidget.cts.R.layout.second_initial_layout,
                        provider.initialLayout);
                assertEquals(android.appwidget.cts.R.layout.second_initial_keyguard_layout,
                        provider.initialKeyguardLayout);
                assertEquals(android.appwidget.cts.R.drawable.second_android_icon,
                        provider.previewImage);
                assertEquals(android.appwidget.cts.R.id.second_auto_advance_view_id,
                        provider.autoAdvanceViewId);
                secondProviderVerified = true;
            }
        }

        return new boolean[]{firstProviderVerified, secondProviderVerified};
    }

    private int getNormalizedDimensionResource(int resId) {
        return getInstrumentation().getTargetContext().getResources()
                .getDimensionPixelSize(resId);
    }

    private int getIntResource(int resId) {
        return getInstrumentation().getTargetContext().getResources().getInteger(resId);
    }

    public ComponentName getFirstWidgetComponent() {
        return new ComponentName(
                getInstrumentation().getTargetContext().getPackageName(),
                FirstAppWidgetProvider.class.getName());
    }

    public ComponentName getSecondWidgetComponent() {
        return new ComponentName(
                getInstrumentation().getTargetContext().getPackageName(),
                SecondAppWidgetProvider.class.getName());
    }

    public ArrayList<String> runShellCommand(String command) throws Exception {
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
}
