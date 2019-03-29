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
 * limitations under the License
 */

package android.server.alertwindowappsdk25;

import android.os.Bundle;

import static android.view.WindowManager.LayoutParams.TYPE_PHONE;
import static android.view.WindowManager.LayoutParams.TYPE_PRIORITY_PHONE;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;

public class AlertWindowTestActivitySdk25 extends AlertWindowTestBaseActivity {
    private static final int[] ALERT_WINDOW_TYPES = {
            TYPE_PHONE,
            TYPE_PRIORITY_PHONE,
            TYPE_SYSTEM_ALERT,
            TYPE_SYSTEM_ERROR,
            TYPE_SYSTEM_OVERLAY
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        createAllAlertWindows(getPackageName());
    }

    @Override
    protected int[] getAlertWindowTypes() {
        return ALERT_WINDOW_TYPES;
    }
}
