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

public abstract class AppWidgetProviderCallbacks {

    private AppWidgetProvider mProvider;

    public final AppWidgetProvider getProvider() {
        return mProvider;
    }

    public final void setProvider(AppWidgetProvider provider) {
        mProvider = provider;
    }

    public abstract void onUpdate(Context context, AppWidgetManager appWidgetManager,
             int[] appWidgetIds);

    public abstract void onAppWidgetOptionsChanged(Context context,
            AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions);

    public abstract void onDeleted(Context context, int[] appWidgetIds);

    public abstract void onEnabled(Context context);

    public abstract void onDisabled(Context context);

    public abstract void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds);
}
