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

import android.annotation.DrawableRes;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import com.android.car.settings.R;
import com.android.car.settings.bluetooth.BluetoothSettingsFragment;
import com.android.car.settings.common.BaseFragment;
import com.android.car.settings.common.IconToggleLineItem;


/**
 * Represents the Bluetooth line item on settings home page.
 */
public class BluetoothLineItem extends IconToggleLineItem {
    private BluetoothAdapter mBluetoothAdapter;
    private BaseFragment.FragmentController mFragmentController;

    public BluetoothLineItem(Context context, BaseFragment.FragmentController fragmentController) {
        super(context.getText(R.string.bluetooth_settings), context);
        mFragmentController = fragmentController;
        mBluetoothAdapter =
                ((BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE))
                        .getAdapter();
    }

    @Override
    public void onToggleClicked(boolean isChecked) {
        if (isChecked) {
            mBluetoothAdapter.enable();
        } else {
            mBluetoothAdapter.disable();
        }
    }

    @Override
    public boolean isExpandable() {
        return false;
    }

    @Override
    public boolean isClickable() {
        return true;
    }

    @Override
    public void onClicked() {
        mFragmentController.launchFragment(BluetoothSettingsFragment.getInstance());
    }

    @Override
    public CharSequence getDesc() {
        return mContext.getText(R.string.bluetooth_settings_summary);
    }

    @Override
    public boolean isChecked() {
        return mBluetoothAdapter.isEnabled();
    }

    @Override
    public @DrawableRes int getIcon() {
        return getIconRes(mBluetoothAdapter.isEnabled());
    }

    public void onBluetoothStateChanged(boolean enabled) {
        if (mIconUpdateListener == null) {
            return;
        }
        mIconUpdateListener.onUpdateIcon(getIconRes(enabled));
    }

    private @DrawableRes int getIconRes(boolean enabled) {
        return enabled
                ? R.drawable.ic_settings_bluetooth : R.drawable.ic_settings_bluetooth_disabled;
    }
}
