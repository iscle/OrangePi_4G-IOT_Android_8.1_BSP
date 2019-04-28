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
package com.android.car.settings.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v4.widget.SwipeRefreshLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.android.car.settings.common.BaseFragment;
import com.android.car.settings.R;
import com.android.car.view.PagedListView;

import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.BluetoothDeviceFilter;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothAdapter;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

/**
 * Hosts Bluetooth related preferences.
 */
public class BluetoothSettingsFragment extends BaseFragment implements BluetoothCallback {
    private static final String TAG = "BluetoothSettingsFragment";

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private Switch mBluetoothSwitch;
    private ProgressBar mProgressBar;
    private PagedListView mDeviceListView;
    private ViewSwitcher mViewSwitcher;
    private TextView mMessageView;
    private BluetoothDeviceListAdapter mDeviceAdapter;
    private LocalBluetoothAdapter mLocalAdapter;
    private LocalBluetoothManager mLocalManager;

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(intent.getAction())) {
                setProgressBarVisible(true);
                mBluetoothSwitch.setChecked(true);
                if (mViewSwitcher.getCurrentView() != mSwipeRefreshLayout) {
                    mViewSwitcher.showPrevious();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                setProgressBarVisible(false);
            }
        }
    };

    public static BluetoothSettingsFragment getInstance() {
        BluetoothSettingsFragment bluetoothSettingsFragment = new BluetoothSettingsFragment();
        Bundle bundle = BaseFragment.getBundle();
        bundle.putInt(EXTRA_TITLE_ID, R.string.bluetooth_settings);
        bundle.putInt(EXTRA_LAYOUT, R.layout.bluetooth_list);
        bundle.putInt(EXTRA_ACTION_BAR_LAYOUT, R.layout.action_bar_with_toggle);
        bluetoothSettingsFragment.setArguments(bundle);
        return bluetoothSettingsFragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mBluetoothSwitch = getActivity().findViewById(R.id.toggle_switch);
        mSwipeRefreshLayout = getActivity().findViewById(R.id.swiperefresh);
        mSwipeRefreshLayout.setSize(SwipeRefreshLayout.LARGE);
        mSwipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        mSwipeRefreshLayout.setRefreshing(false);
                        if (mLocalAdapter.isDiscovering()) {
                            mLocalAdapter.cancelDiscovery();
                        }
                        mDeviceAdapter.reset();
                    }
                }
        );

        mBluetoothSwitch.setOnCheckedChangeListener((v, isChecked) -> {
                if (mBluetoothSwitch.isChecked()) {
                    // bt scan was turned on at state listener, when state is on.
                    mLocalAdapter.setBluetoothEnabled(true);
                } else {
                    mLocalAdapter.stopScanning();
                    mLocalAdapter.setBluetoothEnabled(false);
                }
            });

        mProgressBar = getView().findViewById(R.id.bt_search_progress);
        mDeviceListView = getView().findViewById(R.id.list);
        mViewSwitcher = getView().findViewById(R.id.view_switcher);
        mMessageView = getView().findViewById(R.id.bt_message);

        mLocalManager = LocalBluetoothManager.getInstance(getContext(), null /* listener */);
        if (mLocalManager == null) {
            Log.e(TAG, "Bluetooth is not supported on this device");
            return;
        }
        mLocalAdapter = mLocalManager.getBluetoothAdapter();

        // Set this to light mode, since the scroll bar buttons always appear
        // on top of a dark scrim.
        mDeviceListView.setDarkMode();
        mDeviceAdapter = new BluetoothDeviceListAdapter(
                getContext() , mLocalManager, mFragmentController);
        mDeviceListView.setAdapter(mDeviceAdapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mLocalManager == null) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        getActivity().registerReceiver(mBroadcastReceiver, filter);

        mLocalManager.setForegroundActivity(getActivity());
        mLocalManager.getEventManager().registerCallback(this);
        mBluetoothSwitch.setChecked(mLocalAdapter.isEnabled());
        if (mLocalAdapter.isEnabled()) {
            setProgressBarVisible(true);
            mLocalAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
            mLocalAdapter.startScanning(true);
            if (mViewSwitcher.getCurrentView() != mSwipeRefreshLayout) {
                mViewSwitcher.showPrevious();
            }
        } else {
            setProgressBarVisible(false);
            if (mViewSwitcher.getCurrentView() != mMessageView) {
                mViewSwitcher.showNext();
            }
        }
        mDeviceAdapter.start();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mLocalManager == null) {
            return;
        }
        getActivity().unregisterReceiver(mBroadcastReceiver);
        mDeviceAdapter.stop();
        mLocalAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
        mLocalManager.setForegroundActivity(null);
        mLocalAdapter.stopScanning();
        mLocalManager.getEventManager().unregisterCallback(this);
    }

    @Override
    public void onBluetoothStateChanged(int bluetoothState) {
        switch (bluetoothState) {
            case BluetoothAdapter.STATE_OFF:
                setProgressBarVisible(false);
                mBluetoothSwitch.setChecked(false);
                if (mViewSwitcher.getCurrentView() != mMessageView) {
                    mViewSwitcher.showNext();
                }
                break;
            case BluetoothAdapter.STATE_ON:
            case BluetoothAdapter.STATE_TURNING_ON:
                setProgressBarVisible(true);
                mBluetoothSwitch.setChecked(true);
                if (mViewSwitcher.getCurrentView() != mSwipeRefreshLayout) {
                        mViewSwitcher.showPrevious();
                }
                break;
            case BluetoothAdapter.STATE_TURNING_OFF:
                setProgressBarVisible(true);
                break;
        }
    }

    @Override
    public void onScanningStateChanged(boolean started) {
        if (!started) {
            setProgressBarVisible(false);
        }
    }

    @Override
    public void onDeviceBondStateChanged(CachedBluetoothDevice cachedDevice, int bondState) {
        // no-op
    }

    @Override
    public void onDeviceAdded(CachedBluetoothDevice cachedDevice) {
        // no-op
    }

    @Override
    public void onDeviceDeleted(CachedBluetoothDevice cachedDevice) {
        // no-op
    }

    @Override
    public void onConnectionStateChanged(CachedBluetoothDevice cachedDevice, int state) {
        // no-op
    }

    private  void setProgressBarVisible(boolean visible) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }
}
