/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.appwidget.cts.provider;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

public abstract class StubbableAppWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        AppWidgetProviderCallbacks callbacks = getCallbacks();
        if (callbacks != null) {
            callbacks.onUpdate(context, appWidgetManager, appWidgetIds);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
            int appWidgetId, Bundle newOptions) {
        AppWidgetProviderCallbacks callbacks = getCallbacks();
        if (callbacks != null) {
            callbacks.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        AppWidgetProviderCallbacks callbacks = getCallbacks();
        if (callbacks != null) {
            callbacks.onDeleted(context, appWidgetIds);
        }
    }

    @Override
    public void onEnabled(Context context) {
        AppWidgetProviderCallbacks callbacks = getCallbacks();
        if (callbacks != null) {
            callbacks.onEnabled(context);
        }
    }

    @Override
    public void onDisabled(Context context) {
        AppWidgetProviderCallbacks callbacks = getCallbacks();
        if (callbacks != null) {
            callbacks.onDisabled(context);
        }
    }

    @Override
    public void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds) {
        AppWidgetProviderCallbacks callbacks = getCallbacks();
        if (callbacks != null) {
            super.onRestored(context, oldWidgetIds, newWidgetIds);
        }
    }

    protected abstract AppWidgetProviderCallbacks getCallbacks();
}
