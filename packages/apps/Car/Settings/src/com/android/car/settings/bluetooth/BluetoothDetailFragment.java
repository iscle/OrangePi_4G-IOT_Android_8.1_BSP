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

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.android.car.settings.R;
import com.android.car.settings.common.EditTextLineItem;
import com.android.car.settings.common.ListSettingsFragment;
import com.android.car.settings.common.SingleTextLineItem;
import com.android.car.settings.common.TypedPagedListAdapter;
import com.android.car.view.PagedListView;

import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfile;
import com.android.settingslib.bluetooth.MapClientProfile;
import com.android.settingslib.bluetooth.PanProfile;
import com.android.settingslib.bluetooth.PbapClientProfile;

import java.util.ArrayList;

/**
 * Shows details about a bluetooth device, including actions related to the device,
 * e.g. forget etc. The intent should include information about the device, use that to
 * render UI, e.g. show name etc.
 */
public class BluetoothDetailFragment extends ListSettingsFragment implements
        BluetoothProfileLineItem.DataChangedListener {
    private static final String TAG = "BluetoothDetailFragment";

    public static final String EXTRA_BT_DEVICE = "extra_bt_device";

    private BluetoothDevice mDevice;
    private CachedBluetoothDevice mCachedDevice;

    private CachedBluetoothDeviceManager mDeviceManager;
    private LocalBluetoothManager mLocalManager;
    private EditTextLineItem mInputLineItem;
    private TextView mOkButton;

    public static BluetoothDetailFragment getInstance(BluetoothDevice btDevice) {
        BluetoothDetailFragment bluetoothDetailFragment = new BluetoothDetailFragment();
        Bundle bundle = ListSettingsFragment.getBundle();
        bundle.putParcelable(EXTRA_BT_DEVICE, btDevice);
        bundle.putInt(EXTRA_TITLE_ID, R.string.bluetooth_settings);
        bundle.putInt(EXTRA_ACTION_BAR_LAYOUT, R.layout.action_bar_with_button);
        bluetoothDetailFragment.setArguments(bundle);
        return bluetoothDetailFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDevice = getArguments().getParcelable(EXTRA_BT_DEVICE);
        mLocalManager = LocalBluetoothManager.getInstance(getContext(), null /* listener */);
        if (mLocalManager == null) {
            Log.e(TAG, "Bluetooth is not supported on this device");
            return;
        }
        mDeviceManager = mLocalManager.getCachedDeviceManager();
        mCachedDevice = mDeviceManager.findDevice(mDevice);
        if (mCachedDevice == null) {
            mCachedDevice = mDeviceManager.addDevice(
                    mLocalManager.getBluetoothAdapter(),
                    mLocalManager.getProfileManager(),
                    mDevice);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        if (mDevice == null) {
            Log.w(TAG, "No bluetooth device set.");
            return;
        }
        super.onActivityCreated(savedInstanceState);

        setupForgetButton();
        setupOkButton();
    }

    @Override
    public void onDataChanged() {
        mPagedListAdapter.notifyDataSetChanged();
    }

    @Override
    public ArrayList<TypedPagedListAdapter.LineItem> getLineItems() {
        ArrayList<TypedPagedListAdapter.LineItem> lineItems = new ArrayList<>();
        mInputLineItem = new EditTextLineItem(
                getContext().getText(R.string.bluetooth_preference_paired_dialog_name_label),
                mCachedDevice.getName());
        mInputLineItem.setTextType(EditTextLineItem.TextType.TEXT);
        lineItems.add(mInputLineItem);
        lineItems.add(new SingleTextLineItem(getContext().getText(
                R.string.bluetooth_device_advanced_profile_header_title)));
        addProfileLineItems(lineItems);
        return lineItems;
    }

    private void addProfileLineItems(ArrayList<TypedPagedListAdapter.LineItem> lineItems) {
        for (LocalBluetoothProfile profile : mCachedDevice.getConnectableProfiles()) {
            lineItems.add(new BluetoothProfileLineItem(
                    getContext(), profile, mCachedDevice, this));
        }
    }

    private void setupForgetButton() {
        TextView fortgetButton = getActivity().findViewById(R.id.action_button2);
        fortgetButton.setVisibility(View.VISIBLE);
        fortgetButton.setText(R.string.forget);
        fortgetButton.setOnClickListener(v -> {
            mCachedDevice.unpair();
            mFragmentController.goBack();
        });
    }

    private void setupOkButton() {
        mOkButton = getActivity().findViewById(R.id.action_button1);
        mOkButton.setText(R.string.okay);
        mOkButton.setOnClickListener(v -> {
            if (!mInputLineItem.getInput().equals(mCachedDevice.getName())) {
                mCachedDevice.setName(mInputLineItem.getInput());
            }
            mFragmentController.goBack();
        });
    }
}
