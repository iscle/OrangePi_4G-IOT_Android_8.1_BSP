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

package com.google.android.car.kitchensink.cluster;

import android.app.Activity;
import android.car.cluster.CarInstrumentClusterManager;
import android.car.cluster.ClusterActivityState;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.car.Car;
import android.support.car.CarConnectionCallback;
import android.support.car.CarNotConnectedException;
import android.util.Log;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.google.android.car.kitchensink.R;

/**
 * Fake navigation activity for instrument cluster.
 */
public class FakeClusterNavigationActivity
        extends Activity
        implements CarInstrumentClusterManager.Callback {

    private final static String TAG = FakeClusterNavigationActivity.class.getSimpleName();

    private Car mCarApi;
    private CarInstrumentClusterManager mClusterManager;
    private ImageView mUnobscuredArea;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.fake_cluster_navigation_activity);
        mUnobscuredArea = findViewById(R.id.unobscuredArea);

        mCarApi = Car.createCar(this /* context */, new CarConnectionCallback() {

            @Override
            public void onConnected(Car car) {
                onCarConnected(car);
            }

            @Override
            public void onDisconnected(Car car) {
                onCarDisconnected(car);
            }
        });
        Log.i(TAG, "Connecting to car api...");
        mCarApi.connect();
    }


    @Override
    public void onClusterActivityStateChanged(String category, Bundle clusterActivityState) {
        ClusterActivityState state = ClusterActivityState.fromBundle(clusterActivityState);
        Log.i(TAG, "onClusterActivityStateChanged, category: " + category + ", state: " + state);

        Rect unobscured = state.getUnobscuredBounds();
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                unobscured.width(), unobscured.height());
        lp.setMargins(unobscured.left, unobscured.top, 0, 0);
        mUnobscuredArea.setLayoutParams(lp);
    }

    private void onCarConnected(Car car) {
        Log.i(TAG, "onCarConnected, car: " + car);
        try {
            mClusterManager = (CarInstrumentClusterManager) car.getCarManager(
                    android.car.Car.CAR_INSTRUMENT_CLUSTER_SERVICE);
        } catch (CarNotConnectedException e) {
            throw new IllegalStateException(e);
        }

        try {
            Log.i(TAG, "registering callback...");
            mClusterManager.registerCallback(CarInstrumentClusterManager.CATEGORY_NAVIGATION, this);
            Log.i(TAG, "callback registered");
        } catch (android.car.CarNotConnectedException e) {
            throw new IllegalStateException(e);
        }
    }

    private void onCarDisconnected(Car car) {

    }
}