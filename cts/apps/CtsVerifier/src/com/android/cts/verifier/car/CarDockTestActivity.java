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
 * limitations under the License.
 */

package com.android.cts.verifier.car;

import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestResult;

/**
 * Tests that CAR_DOCK mode opens the app associated with car dock when going into
 * car mode.
 */
public class CarDockTestActivity extends PassFailButtons.Activity {

    private static final String CAR_DOCK1 =
            "com.android.cts.verifier.car.CarDockActivity1";
    private static final String CAR_DOCK2 =
            "com.android.cts.verifier.car.CarDockActivity2";

    private UiModeManager mManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = getLayoutInflater().inflate(R.layout.car_dock_test_main, null);
        setContentView(view);
        setInfoResources(R.string.car_dock_test, R.string.car_dock_test_desc, -1);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        mManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        CarDockActivity.sOnHomePressedRunnable = new Runnable() {
            @Override
            public void run() {
                TestResult.setPassedResult(CarDockTestActivity.this, getTestId(),
                        getTestDetails(), getReportLog());
                mManager.disableCarMode(0);
                finish();
            }
        };
        Button button = (Button) view.findViewById(R.id.car_mode);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mManager.enableCarMode(UiModeManager.ENABLE_CAR_MODE_GO_CAR_HOME);
            }
        });
        Intent i = new Intent(Intent.ACTION_MAIN, null);
        i.addCategory(Intent.CATEGORY_CAR_DOCK);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        ActivityInfo ai = null;
        ResolveInfo info = getPackageManager().resolveActivity(i,
                PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_META_DATA);
        if (info != null) {
            ai = info.activityInfo;
            // Check if we are the default CAR_DOCK handler
            if (!ai.packageName.equals(getPackageName())) {
                // Switch components to fake new CAR_DOCK install to force bringing up the
                // disambiguation dialog.
                PackageManager pm = getApplicationContext().getPackageManager();
                ComponentName component1 = new ComponentName(getPackageName(), CAR_DOCK1);
                ComponentName component2 = new ComponentName(getPackageName(), CAR_DOCK2);

                if (pm.getComponentEnabledSetting(component1) ==
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    swapCarDockHandler(component2, component1);
                } else {
                    swapCarDockHandler(component1, component2);
                }
            }
        }
    }

    private void swapCarDockHandler(
            ComponentName toBeDisabledComponent, ComponentName toBeEnabledComponent) {
        PackageManager pm = getApplicationContext().getPackageManager();

        pm.setComponentEnabledSetting(toBeDisabledComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);

        pm.setComponentEnabledSetting(toBeEnabledComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }
}
