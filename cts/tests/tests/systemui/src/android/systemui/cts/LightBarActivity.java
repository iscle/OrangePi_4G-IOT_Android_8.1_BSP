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
 * limitations under the License
 */
package android.systemui.cts;

import android.view.View;

/**
 * An activity that exercises SYSTEM_UI_FLAG_LIGHT_STATUS_BAR and
 * SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.
 */
public class LightBarActivity extends LightBarBaseActivity {

    public void setLightStatusBar(boolean lightStatusBar) {
        setLightBar(lightStatusBar, View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    public void setLightNavigationBar(boolean lightNavigationBar) {
        setLightBar(lightNavigationBar, View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private void setLightBar(boolean light, int systemUiFlag) {
        int vis = getWindow().getDecorView().getSystemUiVisibility();
        if (light) {
            vis |= systemUiFlag;
        } else {
            vis &= ~systemUiFlag;
        }
        getWindow().getDecorView().setSystemUiVisibility(vis);
    }
}
