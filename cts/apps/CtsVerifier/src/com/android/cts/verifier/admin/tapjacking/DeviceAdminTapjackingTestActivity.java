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
 * limitations under the License.
 */

package com.android.cts.verifier.admin.tapjacking;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Test that checks that device admin activate button does not allow taps when another window
 * is obscuring the device admin details
 */
public class DeviceAdminTapjackingTestActivity extends PassFailButtons.Activity implements
        View.OnClickListener {

    private static final String TAG = DeviceAdminTapjackingTestActivity.class.getSimpleName();
    private static final String ADMIN_ACTIVATED_BUNDLE_KEY = "admin_activated";
    private static final String ACTIVITIES_FINISHED_IN_ORDER_KEY = "activities_finished_in_order";
    private static final int REQUEST_ENABLE_ADMIN = 0;
    private static final int REQUEST_OVERLAY_ACTIVITY = 1;
    private static final long REMOVE_ADMIN_TIMEOUT = 5000;

    private DevicePolicyManager mDevicePolicyManager;
    private Button mAddDeviceAdminButton;
    private boolean mAdminActivated;
    private boolean mActivitiesFinishedInOrder;
    private boolean mOverlayFinished;
    private ComponentName mAdmin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.da_tapjacking_test_main);
        setInfoResources(R.string.da_tapjacking_test, R.string.da_tapjacking_test_info, -1);
        setPassFailButtonClickListeners();

        mAdmin = new ComponentName(this, EmptyDeviceAdminReceiver.class);
        mDevicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);

        if (savedInstanceState != null) {
            mAdminActivated = savedInstanceState.getBoolean(ADMIN_ACTIVATED_BUNDLE_KEY, false);
            mActivitiesFinishedInOrder = savedInstanceState.getBoolean(
                    ACTIVITIES_FINISHED_IN_ORDER_KEY, false);
        } else if (isActiveAdminAfterTimeout()) {
            Log.e(TAG, "Could not remove active admin. Cannot proceed with test");
            finish();
        }
        mAddDeviceAdminButton = findViewById(R.id.enable_admin_overlay_button);
        mAddDeviceAdminButton.setOnClickListener(this);
    }

    private boolean isActiveAdminAfterTimeout() {
        final long timeOut = SystemClock.uptimeMillis() + REMOVE_ADMIN_TIMEOUT;
        while (mDevicePolicyManager.isAdminActive(mAdmin)
                && SystemClock.uptimeMillis() < timeOut ) {
            try {
                Thread.sleep(1000);
            } catch(InterruptedException exc) {
            }
        }
        return mDevicePolicyManager.isAdminActive(mAdmin);
    }

    @Override
    public void onClick(View v) {
        if (v == mAddDeviceAdminButton) {
            Intent securitySettingsIntent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            securitySettingsIntent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mAdmin);
            startActivityForResult(securitySettingsIntent, REQUEST_ENABLE_ADMIN);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException exc) {
            }
            startActivityForResult(new Intent(this, OverlayingActivity.class),
                    REQUEST_OVERLAY_ACTIVITY);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_ADMIN) {
            mActivitiesFinishedInOrder = mOverlayFinished;
            if (resultCode == RESULT_OK) {
                mAdminActivated = true;
                Log.e(TAG, "Admin was activated. Restart the Test");
            }
        }
        else if (requestCode == REQUEST_OVERLAY_ACTIVITY) {
            mOverlayFinished = true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateWidgets();
    }

    @Override
    public void onSaveInstanceState(Bundle icicle) {
        icicle.putBoolean(ADMIN_ACTIVATED_BUNDLE_KEY, mAdminActivated);
        icicle.putBoolean(ACTIVITIES_FINISHED_IN_ORDER_KEY, mActivitiesFinishedInOrder);
    }

    private void updateWidgets() {
        mAddDeviceAdminButton.setEnabled(!mActivitiesFinishedInOrder && !mAdminActivated);
        getPassButton().setEnabled(!mAdminActivated && mActivitiesFinishedInOrder);
    }
}
