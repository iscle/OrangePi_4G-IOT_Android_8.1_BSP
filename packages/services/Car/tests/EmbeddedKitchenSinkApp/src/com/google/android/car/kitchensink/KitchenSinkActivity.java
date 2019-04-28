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

package com.google.android.car.kitchensink;

import android.car.hardware.hvac.CarHvacManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.car.Car;
import android.support.car.CarAppFocusManager;
import android.support.car.CarConnectionCallback;
import android.support.car.CarNotConnectedException;
import android.support.car.hardware.CarSensorManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.car.app.CarDrawerActivity;
import com.android.car.app.CarDrawerAdapter;
import com.android.car.app.DrawerItemViewHolder;
import com.google.android.car.kitchensink.assistant.CarAssistantFragment;
import com.google.android.car.kitchensink.audio.AudioTestFragment;
import com.google.android.car.kitchensink.bluetooth.BluetoothHeadsetFragment;
import com.google.android.car.kitchensink.bluetooth.MapMceTestFragment;
import com.google.android.car.kitchensink.cluster.InstrumentClusterFragment;
import com.google.android.car.kitchensink.cube.CubesTestFragment;
import com.google.android.car.kitchensink.diagnostic.DiagnosticTestFragment;
import com.google.android.car.kitchensink.hvac.HvacTestFragment;
import com.google.android.car.kitchensink.input.InputTestFragment;
import com.google.android.car.kitchensink.job.JobSchedulerFragment;
import com.google.android.car.kitchensink.orientation.OrientationTestFragment;
import com.google.android.car.kitchensink.radio.RadioTestFragment;
import com.google.android.car.kitchensink.sensor.SensorsTestFragment;
import com.google.android.car.kitchensink.setting.CarServiceSettingsActivity;
import com.google.android.car.kitchensink.touch.TouchTestFragment;
import com.google.android.car.kitchensink.volume.VolumeTestFragment;
import java.util.ArrayList;
import java.util.List;

public class KitchenSinkActivity extends CarDrawerActivity {
    private static final String TAG = "KitchenSinkActivity";

    private interface ClickHandler {
        void onClick();
    }

    private static abstract class MenuEntry implements ClickHandler {
        abstract String getText();
    }

    private final class OnClickMenuEntry extends MenuEntry {
        private final String mText;
        private final ClickHandler mClickHandler;

        OnClickMenuEntry(String text, ClickHandler clickHandler) {
            mText = text;
            mClickHandler = clickHandler;
        }

        @Override
        String getText() {
            return mText;
        }

        @Override
        public void onClick() {
            mClickHandler.onClick();
        }
    }

    private final class FragmentMenuEntry<T extends Fragment> extends MenuEntry {
        private final class FragmentClassOrInstance<T extends Fragment> {
            final Class<T> mClazz;
            T mFragment = null;

            FragmentClassOrInstance(Class<T> clazz) {
                mClazz = clazz;
            }

            T getFragment() {
                if (mFragment == null) {
                    try {
                        mFragment = mClazz.newInstance();
                    } catch (InstantiationException | IllegalAccessException e) {
                        Log.e(TAG, "unable to create fragment", e);
                    }
                }
                return mFragment;
            }
        }

        private final String mText;
        private final FragmentClassOrInstance<T> mFragment;

        FragmentMenuEntry(String text, Class<T> clazz) {
            mText = text;
            mFragment = new FragmentClassOrInstance<>(clazz);
        }

        @Override
        String getText() {
            return mText;
        }

        @Override
        public void onClick() {
            Fragment fragment = mFragment.getFragment();
            if (fragment != null) {
                KitchenSinkActivity.this.showFragment(fragment);
            } else {
                Log.e(TAG, "cannot show fragment for " + getText());
            }
        }
    }

    private final List<MenuEntry> mMenuEntries = new ArrayList<MenuEntry>() {
        {
            add("audio", AudioTestFragment.class);
            add("hvac", HvacTestFragment.class);
            add("job scheduler", JobSchedulerFragment.class);
            add("inst cluster", InstrumentClusterFragment.class);
            add("input test", InputTestFragment.class);
            add("radio", RadioTestFragment.class);
            add("assistant", CarAssistantFragment.class);
            add("sensors", SensorsTestFragment.class);
            add("diagnostic", DiagnosticTestFragment.class);
            add("volume test", VolumeTestFragment.class);
            add("touch test", TouchTestFragment.class);
            add("cubes test", CubesTestFragment.class);
            add("orientation test", OrientationTestFragment.class);
            add("bluetooth headset",BluetoothHeadsetFragment.class);
            add("bluetooth messaging test", MapMceTestFragment.class);
            add("car service settings", () -> {
                Intent intent = new Intent(KitchenSinkActivity.this,
                    CarServiceSettingsActivity.class);
                startActivity(intent);
            });
            add("quit", KitchenSinkActivity.this::finish);
        }

        <T extends Fragment> void add(String text, Class<T> clazz) {
            add(new FragmentMenuEntry(text, clazz));
        }
        void add(String text, ClickHandler onClick) {
            add(new OnClickMenuEntry(text, onClick));
        }
    };
    private Car mCarApi;
    private CarHvacManager mHvacManager;
    private CarSensorManager mCarSensorManager;
    private CarAppFocusManager mCarAppFocusManager;

    private final CarSensorManager.OnSensorChangedListener mListener = (manager, event) -> {
        switch (event.sensorType) {
            case CarSensorManager.SENSOR_TYPE_DRIVING_STATUS:
                Log.d(TAG, "driving status:" + event.intValues[0]);
                break;
        }
    };

    public CarHvacManager getHvacManager() {
        return mHvacManager;
    }

    @Override
    protected CarDrawerAdapter getRootAdapter() {
        return new DrawerAdapter();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setMainContent(R.layout.kitchen_content);
        // Connection to Car Service does not work for non-automotive yet.
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            mCarApi = Car.createCar(this, mCarConnectionCallback);
            mCarApi.connect();
        }
        Log.i(TAG, "onCreate");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i(TAG, "onRestart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCarSensorManager != null) {
            mCarSensorManager.removeListener(mListener);
        }
        if (mCarApi != null) {
            mCarApi.disconnect();
        }
        Log.i(TAG, "onDestroy");
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.kitchen_content, fragment)
                .commit();
    }

    private final CarConnectionCallback mCarConnectionCallback =
            new CarConnectionCallback() {
        @Override
        public void onConnected(Car car) {
            Log.d(TAG, "Connected to Car Service");
            try {
                mHvacManager = (CarHvacManager) mCarApi.getCarManager(android.car.Car.HVAC_SERVICE);
                mCarSensorManager = (CarSensorManager) mCarApi.getCarManager(Car.SENSOR_SERVICE);
                mCarSensorManager.addListener(mListener,
                        CarSensorManager.SENSOR_TYPE_DRIVING_STATUS,
                        CarSensorManager.SENSOR_RATE_NORMAL);
                mCarAppFocusManager =
                        (CarAppFocusManager) mCarApi.getCarManager(Car.APP_FOCUS_SERVICE);
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "Car is not connected!", e);
            }
        }

        @Override
        public void onDisconnected(Car car) {
            Log.d(TAG, "Disconnect from Car Service");
        }
    };

    public Car getCar() {
        return mCarApi;
    }

    private final class DrawerAdapter extends CarDrawerAdapter {

        public DrawerAdapter() {
            super(KitchenSinkActivity.this, true /* showDisabledOnListOnEmpty */);
            setTitle(getString(R.string.app_title));
        }

        @Override
        protected int getActualItemCount() {
            return mMenuEntries.size();
        }

        @Override
        protected void populateViewHolder(DrawerItemViewHolder holder, int position) {
            holder.getTitle().setText(mMenuEntries.get(position).getText());
        }

        @Override
        public void onItemClick(int position) {
            if ((position < 0) || (position >= mMenuEntries.size())) {
                Log.wtf(TAG, "Unknown menu item: " + position);
                return;
            }

            mMenuEntries.get(position).onClick();

            closeDrawer();
        }
    }
}
