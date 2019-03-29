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

package android.widget.cts.appwidget;

import android.content.Intent;
import android.widget.RemoteViewsService;

public class MyAppWidgetService extends RemoteViewsService {
    private static final Object sLock = new Object();

    private static RemoteViewsFactory sFactory;

    public static void setFactory(RemoteViewsFactory factory) {
        synchronized (sLock) {
            sFactory = factory;
        }
    }

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        synchronized (sLock) {
            return sFactory;
        }
    }
}
