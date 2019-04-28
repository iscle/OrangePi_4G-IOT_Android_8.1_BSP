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
package com.android.car.pm;

import android.app.Activity;
import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.content.pm.CarPackageManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.android.car.CarLog;
import com.android.car.R;

/**
 * Default activity that will be launched when the current foreground activity is not allowed.
 * Additional information on blocked Activity will be passed as extra in Intent
 * via {@link #INTENT_KEY_BLOCKED_ACTIVITY} key. *
 */
public class ActivityBlockingActivity extends Activity {

    public static final String INTENT_KEY_BLOCKED_ACTIVITY = "blocked_activity";

    private static final long AUTO_DISMISS_TIME_MS = 3000;

    private Handler mHandler;

    private Button mExitButton;

    private Car mCar;

    private boolean mExitRequested;

    private final Runnable mFinishRunnable = () -> handleFinish();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocking);
        mHandler = new Handler(Looper.getMainLooper());
        mExitButton = (Button) findViewById(R.id.botton_exit_now);
        mExitButton.setOnClickListener((View v) -> handleFinish());
        mCar = Car.createCar(this, new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                if (mExitRequested) {
                    handleFinish();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }

        });
        mCar.connect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.postDelayed(mFinishRunnable, AUTO_DISMISS_TIME_MS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mFinishRunnable);
        mCar.disconnect();
    }

    private void handleFinish() {
        if (!mCar.isConnected()) {
            mExitRequested = true;
            return;
        }
        if (isFinishing()) {
            return;
        }
        try {
            CarPackageManager carPm = (CarPackageManager) mCar.getCarManager(Car.PACKAGE_SERVICE);

            // finish itself only when it will not lead into another blocking
            if (carPm.isActivityBackedBySafeActivity(getComponentName())) {
                finish();
                return;
            }
            // back activity is not safe either. Now try home
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            PackageManager pm = getPackageManager();
            ComponentName homeComponent = homeIntent.resolveActivity(pm);
            if (carPm.isActivityAllowedWhileDriving(homeComponent.getPackageName(),
                    homeComponent.getClassName())) {
                startActivity(homeIntent);
                finish();
                return;
            } else {
                Log.w(CarLog.TAG_AM, "Home activity is not in white list. Keep blocking activity. "
                        + ", Home Activity:" + homeComponent);
            }
        } catch (CarNotConnectedException e) {
            Log.w(CarLog.TAG_AM, "Car service not avaiable, will finish", e);
            finish();
        }
    }
}
