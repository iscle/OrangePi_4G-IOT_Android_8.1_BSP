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

package com.google.android.car.kitchensink.diagnostic;

import android.annotation.Nullable;
import android.car.Car;
import android.car.diagnostic.CarDiagnosticEvent;
import android.car.diagnostic.CarDiagnosticManager;
import android.car.diagnostic.CarDiagnosticManager.OnDiagnosticEventListener;
import android.graphics.Color;
import android.os.Bundle;
import android.support.car.hardware.CarSensorManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.android.car.kitchensink.KitchenSinkActivity;
import com.google.android.car.kitchensink.R;
import java.util.Objects;


public class DiagnosticTestFragment extends Fragment {
    private static final String TAG = "CAR.DIAGNOSTIC.KS";

    private KitchenSinkActivity mActivity;
    private TextView mLiveDiagnosticInfo;
    private TextView mFreezeDiagnosticInfo;
    private CarDiagnosticManager mDiagnosticManager;

    private final class TestListener implements OnDiagnosticEventListener {
        private final TextView mTextView;

        TestListener(TextView view) {
            mTextView = Objects.requireNonNull(view);
        }

        @Override
        public void onDiagnosticEvent(CarDiagnosticEvent carDiagnosticEvent) {
            mTextView.post(() -> mTextView.setText(carDiagnosticEvent.toString()));
        }
    }

    private OnDiagnosticEventListener mLiveListener;
    private OnDiagnosticEventListener mFreezeListener;

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.diagnostic, container, false);
        mActivity = (KitchenSinkActivity) getHost();

        mLiveDiagnosticInfo = (TextView) view.findViewById(R.id.live_diagnostic_info);
        mLiveDiagnosticInfo.setTextColor(Color.RED);
        mLiveListener = new TestListener(mLiveDiagnosticInfo);

        mFreezeDiagnosticInfo = (TextView) view.findViewById(R.id.freeze_diagnostic_info);
        mFreezeDiagnosticInfo.setTextColor(Color.RED);
        mFreezeListener = new TestListener(mFreezeDiagnosticInfo);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        resumeDiagnosticManager();
    }

    @Override
    public void onPause() {
        super.onPause();
        pauseDiagnosticManager();
    }

    private void resumeDiagnosticManager() {
        try {
            mDiagnosticManager =
                    (CarDiagnosticManager)mActivity.getCar().getCarManager(Car.DIAGNOSTIC_SERVICE);
            if (mLiveListener != null) {
                mDiagnosticManager.registerListener(mLiveListener,
                    CarDiagnosticManager.FRAME_TYPE_LIVE,
                    CarSensorManager.SENSOR_RATE_NORMAL);
            }
            if (mFreezeListener != null) {
                mDiagnosticManager.registerListener(mFreezeListener,
                    CarDiagnosticManager.FRAME_TYPE_FREEZE,
                    CarSensorManager.SENSOR_RATE_NORMAL);
            }
        } catch (android.car.CarNotConnectedException|android.support.car.CarNotConnectedException e) {
            Log.e(TAG, "Car not connected or not supported", e);
        }
    }

    private void pauseDiagnosticManager() {
        if (mDiagnosticManager != null) {
            if (mLiveListener != null) {
                mDiagnosticManager.unregisterListener(mLiveListener);
            }
            if (mFreezeListener != null) {
                mDiagnosticManager.unregisterListener(mFreezeListener);
            }
        }
    }
}
