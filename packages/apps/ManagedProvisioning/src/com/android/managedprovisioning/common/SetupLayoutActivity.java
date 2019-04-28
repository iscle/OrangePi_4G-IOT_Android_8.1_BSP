/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.common;

import static android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.VIEW_UNKNOWN;

import android.app.Activity;
import android.app.ActivityManager.TaskDescription;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemProperties;
import android.support.annotation.VisibleForTesting;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.analytics.TimeLogger;
import com.android.setupwizardlib.util.WizardManagerHelper;

/**
 * Base class for setting up the layout.
 */
public abstract class SetupLayoutActivity extends Activity {
    protected final Utils mUtils;

    private TimeLogger mTimeLogger;

    public SetupLayoutActivity() {
        this(new Utils());
    }

    @VisibleForTesting
    protected SetupLayoutActivity(Utils utils) {
        mUtils = utils;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDefaultTheme();
        mTimeLogger = new TimeLogger(this, getMetricsCategory());
        mTimeLogger.start();

        // lock orientation to portrait on phones
        if (getResources().getBoolean(R.bool.lock_to_portrait)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    @Override
    public void onDestroy() {
        mTimeLogger.stop();
        super.onDestroy();
    }

    protected int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }

    protected Utils getUtils() {
        return mUtils;
    }

    /**
     * @param mainColor integer representing the color (i.e. not resource id)
     */
    protected void setMainColor(int mainColor) {
        mainColor = toSolidColor(mainColor);

        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(mainColor);

        // set status bar icon style
        View decorView = getWindow().getDecorView();
        int visibility = decorView.getSystemUiVisibility();
        decorView.setSystemUiVisibility(getUtils().isBrightColor(mainColor)
                ? (visibility | SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
                : (visibility & ~SYSTEM_UI_FLAG_LIGHT_STATUS_BAR));

        setTaskDescription(new TaskDescription(null /* label */, null /* icon */, mainColor));
    }

    /**
     * Removes transparency from the color
     *
     * <p>Needed for correct calculation of Status Bar icons (light / dark)
     */
    private Integer toSolidColor(Integer color) {
        return Color.argb(255, Color.red(color), Color.green(color), Color.blue(color));
    }

    /**
     * Constructs and shows a {@link DialogFragment} unless it is already displayed.
     * @param dialogBuilder Lightweight builder, that it is inexpensive to discard it if dialog
     * already shown.
     * @param tag The tag for this dialog, as per {@link FragmentTransaction#add(Fragment, String)}.
     */
    protected void showDialog(DialogBuilder dialogBuilder, String tag) {
        FragmentManager fragmentManager = getFragmentManager();
        if (!isDialogAdded(tag)) {
            dialogBuilder.build().show(fragmentManager, tag);
        }
    }

    /**
     * Checks whether the {@link DialogFragment} associated with the given tag is currently showing.
     * @param tag The tag for this dialog.
     */
    protected boolean isDialogAdded(String tag) {
        Fragment fragment = getFragmentManager().findFragmentByTag(tag);
        return (fragment != null) && (fragment.isAdded());
    }

    private void setDefaultTheme() {
        // Take Glif light as default theme like
        // com.google.android.setupwizard.util.ThemeHelper.getDefaultTheme
        setTheme(WizardManagerHelper.getThemeRes(SystemProperties.get("setupwizard.theme"),
                R.style.SuwThemeGlif_Light));
    }


}