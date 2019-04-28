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

package com.android.tv.settings.device.apps;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.storage.VolumeInfo;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.android.tv.settings.device.storage.MoveAppProgressFragment;
import com.android.tv.settings.device.storage.MoveAppStepFragment;

public class MoveAppActivity extends Activity implements MoveAppStepFragment.Callback {

    private static final String TAG = "MoveAppActivity";

    private static final String SAVE_STATE_MOVE_ID = "MoveAppActivity.moveId";

    private static final String ARG_PACKAGE_NAME = "packageName";
    private static final String ARG_PACKAGE_DESC = "packageDesc";

    private PackageManager mPackageManager;
    private int mAppMoveId = -1;
    private final PackageManager.MoveCallback mMoveCallback = new PackageManager.MoveCallback() {
        @Override
        public void onStatusChanged(int moveId, int status, long estMillis) {
            if (moveId != mAppMoveId || !PackageManager.isMoveStatusFinished(status)) {
                return;
            }

            finish();

            if (status != PackageManager.MOVE_SUCCEEDED) {
                Log.d(TAG, "Move failure status: " + status);
                Toast.makeText(MoveAppActivity.this,
                        MoveAppProgressFragment.moveStatusToMessage(MoveAppActivity.this, status),
                        Toast.LENGTH_LONG).show();
            }
        }
    };

    public static Intent getLaunchIntent(Context context, String packageName, String packageDesc) {
        final Intent i = new Intent(context, MoveAppActivity.class);
        i.putExtra(ARG_PACKAGE_NAME, packageName);
        i.putExtra(ARG_PACKAGE_DESC, packageDesc);
        return i;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPackageManager = getPackageManager();
        mPackageManager.registerMoveCallback(mMoveCallback, new Handler());

        if (savedInstanceState != null) {
            mAppMoveId = savedInstanceState.getInt(SAVE_STATE_MOVE_ID);
        } else {
            final String packageDesc = getIntent().getStringExtra(ARG_PACKAGE_DESC);
            final String packageName = getIntent().getStringExtra(ARG_PACKAGE_NAME);

            final Fragment fragment = MoveAppStepFragment.newInstance(packageName, packageDesc);
            getFragmentManager().beginTransaction()
                    .add(android.R.id.content, fragment)
                    .commit();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SAVE_STATE_MOVE_ID, mAppMoveId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPackageManager.unregisterMoveCallback(mMoveCallback);
    }

    @Override
    public void onRequestMovePackageToVolume(String packageName, VolumeInfo destination) {
        mAppMoveId = mPackageManager.movePackage(packageName, destination);
        final ApplicationInfo applicationInfo;
        try {
            applicationInfo = mPackageManager
                    .getApplicationInfo(packageName, 0);
        } catch (final PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(e);
        }

        final MoveAppProgressFragment fragment = MoveAppProgressFragment
                .newInstance(mPackageManager.getApplicationLabel(applicationInfo));

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment)
                .commit();
    }

}
