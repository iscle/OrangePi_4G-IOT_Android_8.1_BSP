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

public final class SecondAppWidgetProvider extends StubbableAppWidgetProvider {
    private static final Object sLock = new Object();

    private static AppWidgetProviderCallbacks sCallbacks;

    public static void setCallbacks(AppWidgetProviderCallbacks callbacks) {
        synchronized (sLock) {
            sCallbacks = callbacks;
        }
    }

    @Override
    protected AppWidgetProviderCallbacks getCallbacks() {
        synchronized (sLock) {
            if (sCallbacks != null) {
                sCallbacks.setProvider(this);
            }
            return sCallbacks;
        }
    }
}
