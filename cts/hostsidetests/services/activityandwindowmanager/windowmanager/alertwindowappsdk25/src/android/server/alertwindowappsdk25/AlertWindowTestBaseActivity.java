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

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Point;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import static android.view.Gravity.LEFT;
import static android.view.Gravity.TOP;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

public abstract class AlertWindowTestBaseActivity extends Activity {

    protected void createAllAlertWindows(String windowName) {
        final int[] alertWindowTypes = getAlertWindowTypes();
        for (int type : alertWindowTypes) {
            try {
                createAlertWindow(type, windowName);
            } catch (Exception e) {
                Log.e("AlertWindowTestBaseActivity", "Can't create type=" + type, e);
            }
        }
    }

    protected void createAlertWindow(int type) {
        createAlertWindow(type, getPackageName());
    }

    protected void createAlertWindow(int type, String windowName) {
        if (!isSystemAlertWindowType(type)) {
            throw new IllegalArgumentException("Well...you are not an alert window type=" + type);
        }

        final Point size = new Point();
        final WindowManager wm = getSystemService(WindowManager.class);
        wm.getDefaultDisplay().getSize(size);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                type, FLAG_NOT_FOCUSABLE | FLAG_WATCH_OUTSIDE_TOUCH | FLAG_NOT_TOUCHABLE);
        params.width = size.x / 3;
        params.height = size.y / 3;
        params.gravity = TOP | LEFT;
        params.setTitle(windowName);

        final TextView view = new TextView(this);
        view.setText(windowName + "   type=" + type);
        view.setBackgroundColor(Color.RED);
        wm.addView(view, params);
    }

    private boolean isSystemAlertWindowType(int type) {
        final int[] alertWindowTypes = getAlertWindowTypes();
        for (int current : alertWindowTypes) {
            if (current == type) {
                return true;
            }
        }
        return false;
    }

    protected abstract int[] getAlertWindowTypes();
}
