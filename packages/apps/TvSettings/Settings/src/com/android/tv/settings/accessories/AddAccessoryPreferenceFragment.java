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

package com.android.tv.settings.accessories;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v17.preference.BaseLeanbackPreferenceFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.util.SparseArray;

import com.android.tv.settings.R;

import java.util.List;

public class AddAccessoryPreferenceFragment extends BaseLeanbackPreferenceFragment {

    private SparseArray<Drawable> mResizedDrawables = new SparseArray<>();

    public static AddAccessoryPreferenceFragment newInstance() {
        return new AddAccessoryPreferenceFragment();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final AddAccessoryActivity activity = (AddAccessoryActivity) getActivity();
        updateList(activity.getBluetoothDevices(), activity.getCurrentTargetAddress(),
                activity.getCurrentTargetStatus(), activity.getCancelledAddress());
    }

    public void updateList(List<BluetoothDevice> devices, String currentTargetAddress,
            String currentTargetStatus, String cancelledAddress) {
        final Context themedContext = getPreferenceManager().getContext();

        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            screen = getPreferenceManager().createPreferenceScreen(themedContext);
            setPreferenceScreen(screen);
        } else {
            screen.removeAll();
        }

        if (devices == null) {
            return;
        }

        // Add entries for the discovered Bluetooth devices
        for (BluetoothDevice bt : devices) {
            final Preference preference = new Preference(themedContext);
            if (currentTargetAddress.equalsIgnoreCase(bt.getAddress()) &&
                    !currentTargetStatus.isEmpty()) {
                preference.setSummary(currentTargetStatus);
            } else if (cancelledAddress.equalsIgnoreCase(bt.getAddress())) {
                preference.setSummary(R.string.accessory_state_canceled);
            } else {
                preference.setSummary(bt.getAddress());
            }
            preference.setKey(bt.getAddress());
            preference.setTitle(bt.getName());
            preference.setIcon(getDeviceDrawable(bt));
            screen.addPreference(preference);
        }
    }

    private Drawable getDeviceDrawable(BluetoothDevice device) {
        final int resId = AccessoryUtils.getImageIdForDevice(device);
        Drawable drawable = mResizedDrawables.get(resId);
        if (drawable == null) {
            final Drawable tempDrawable = getActivity().getDrawable(resId);
            final int iconWidth =
                    getResources().getDimensionPixelSize(R.dimen.lb_dialog_list_item_icon_width);
            final int iconHeight =
                    getResources().getDimensionPixelSize(R.dimen.lb_dialog_list_item_icon_height);
            tempDrawable.setBounds(0, 0, iconWidth, iconHeight);
            final Bitmap bitmap =
                    Bitmap.createBitmap(iconWidth, iconHeight, Bitmap.Config.ARGB_8888);
            final Canvas canvas = new Canvas(bitmap);
            tempDrawable.draw(canvas);
            drawable = new BitmapDrawable(getResources(), bitmap);
            mResizedDrawables.put(resId, drawable);
        }
        return drawable;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final AddAccessoryActivity activity = (AddAccessoryActivity) getActivity();
        activity.onActionClicked(preference.getKey());
        return super.onPreferenceTreeClick(preference);
    }

    public void advanceSelection() {
        final int preferenceCount = getPreferenceScreen().getPreferenceCount();
        if (preferenceCount > 0) {
            final VerticalGridView vgv = (VerticalGridView) getListView();
            final int position = (vgv.getSelectedPosition() + 1) % preferenceCount;
            vgv.setSelectedPositionSmooth(position);
        }
    }
}
