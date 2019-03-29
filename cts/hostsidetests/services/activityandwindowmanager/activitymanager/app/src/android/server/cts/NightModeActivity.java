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

package android.server.cts;

import android.app.UiModeManager;
import android.content.Context;
import android.os.Bundle;

/** Activity that changes UI mode on creation and handles corresponding configuration change. */
public class NightModeActivity extends AbstractLifecycleLogActivity {

    private static final String TAG = NightModeActivity.class.getSimpleName();

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        UiModeManager uiManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        // Switch the mode two times to make sure it is independent of the current setting.
        uiManager.setNightMode(UiModeManager.MODE_NIGHT_YES);
        uiManager.setNightMode(UiModeManager.MODE_NIGHT_NO);
    }
}
