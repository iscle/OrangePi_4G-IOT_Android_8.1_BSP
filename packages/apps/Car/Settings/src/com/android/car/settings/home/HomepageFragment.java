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
package com.android.car.settings.home;


import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.android.car.settings.R;
import com.android.car.settings.applications.ApplicationSettingsFragment;
import com.android.car.settings.common.ListSettingsFragment;
import com.android.car.settings.common.SimpleIconLineItem;
import com.android.car.settings.common.TypedPagedListAdapter;
import com.android.car.settings.datetime.DatetimeSettingsFragment;
import com.android.car.settings.display.DisplaySettingsFragment;
import com.android.car.settings.sound.SoundSettingsFragment;
import com.android.car.settings.system.SystemSettingsFragment;
import com.android.car.settings.users.UserSettingsFragment;
import com.android.car.settings.wifi.CarWifiManager;

import java.util.ArrayList;

/**
 * Homepage for settings for car.
 */
public class HomepageFragment extends ListSettingsFragment implements CarWifiManager.Listener {
    private CarWifiManager mCarWifiManager;
    private WifiLineItem mWifiLineItem;
    private BluetoothLineItem mBluetoothLineItem;

    private final BroadcastReceiver mBtStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        // TODO show a different status icon?
                    case BluetoothAdapter.STATE_OFF:
                        mBluetoothLineItem.onBluetoothStateChanged(false);
                        break;
                    default:
                        mBluetoothLineItem.onBluetoothStateChanged(true);
                }
            }
        }
    };
    private final IntentFilter mBtStateChangeFilter =
            new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);

    public static HomepageFragment getInstance() {
        HomepageFragment homepageFragment = new HomepageFragment();
        Bundle bundle = ListSettingsFragment.getBundle();
        bundle.putInt(EXTRA_TITLE_ID, R.string.settings_label);
        homepageFragment.setArguments(bundle);
        return homepageFragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        mCarWifiManager = new CarWifiManager(getContext(), this /* listener */);
        mWifiLineItem = new WifiLineItem(getContext(), mCarWifiManager, mFragmentController);
        mBluetoothLineItem = new BluetoothLineItem(getContext(), mFragmentController);

        // Call super after the wifiLineItem and BluetoothLineItem are setup, because
        // those are needed in super.onCreate().
        super.onActivityCreated(savedInstanceState);
        getActivity().findViewById(R.id.action_bar_icon_container).setVisibility(View.GONE);
    }

    @Override
    public void onAccessPointsChanged() {
        // don't care
    }

    @Override
    public void onWifiStateChanged(int state) {
        mWifiLineItem.onWifiStateChanged(state);
    }

    @Override
    public void onStart() {
        super.onStart();
        mCarWifiManager.start();
        getActivity().registerReceiver(mBtStateReceiver, mBtStateChangeFilter);
    }

    @Override
    public void onStop() {
        super.onStop();
        mCarWifiManager.stop();
        getActivity().unregisterReceiver(mBtStateReceiver);
    }

    @Override
    public ArrayList<TypedPagedListAdapter.LineItem> getLineItems() {
        ArrayList<TypedPagedListAdapter.LineItem> lineItems = new ArrayList<>();
        lineItems.add(new SimpleIconLineItem(
                R.string.display_settings,
                R.drawable.ic_settings_display,
                getContext(),
                null,
                DisplaySettingsFragment.getInstance(),
                mFragmentController));
        lineItems.add(new SimpleIconLineItem(
                R.string.sound_settings,
                R.drawable.ic_settings_sound,
                getContext(),
                null,
                SoundSettingsFragment.getInstance(),
                mFragmentController));
        lineItems.add(mWifiLineItem);
        lineItems.add(mBluetoothLineItem);
        lineItems.add(new SimpleIconLineItem(
                R.string.applications_settings,
                R.drawable.ic_settings_applications,
                getContext(),
                null,
                ApplicationSettingsFragment.getInstance(),
                mFragmentController));
        lineItems.add(new SimpleIconLineItem(
                R.string.date_and_time_settings_title,
                R.drawable.ic_settings_date_time,
                getContext(),
                null,
                DatetimeSettingsFragment.getInstance(),
                mFragmentController));
        lineItems.add(new SimpleIconLineItem(
                R.string.user_settings_title,
                R.drawable.ic_user,
                getContext(),
                null,
                UserSettingsFragment.getInstance(),
                mFragmentController));
        lineItems.add(new SimpleIconLineItem(
                R.string.system_setting_title,
                R.drawable.ic_settings_about,
                getContext(),
                null,
                SystemSettingsFragment.getInstance(),
                mFragmentController));
        return lineItems;
    }
}
