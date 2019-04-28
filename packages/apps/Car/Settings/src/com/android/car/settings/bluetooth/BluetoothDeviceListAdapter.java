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

import android.annotation.NonNull;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.car.settings.R;
import com.android.car.settings.common.BaseFragment;
import com.android.car.view.PagedListView;
import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.BluetoothDeviceFilter;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.LocalBluetoothAdapter;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfile;
import com.android.settingslib.bluetooth.HidProfile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders {@link android.bluetooth.BluetoothDevice} to a view to be displayed as a row in a list.
 */
public class BluetoothDeviceListAdapter
        extends RecyclerView.Adapter<BluetoothDeviceListAdapter.ViewHolder>
        implements PagedListView.ItemCap, BluetoothCallback {
    private static final String TAG = "BluetoothDeviceListAdapter";
    private static final int DEVICE_ROW_TYPE = 1;
    private static final int BONDED_DEVICE_HEADER_TYPE = 2;
    private static final int AVAILABLE_DEVICE_HEADER_TYPE = 3;
    private static final int NUM_OF_HEADERS = 2;
    public static final int DELAY_MILLIS = 1000;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final HashSet<CachedBluetoothDevice> mBondedDevices = new HashSet<>();
    private final HashSet<CachedBluetoothDevice> mAvailableDevices = new HashSet<>();
    private final LocalBluetoothAdapter mLocalAdapter;
    private final LocalBluetoothManager mLocalManager;
    private final CachedBluetoothDeviceManager mDeviceManager;
    private final Context mContext;
    private final BaseFragment.FragmentController mFragmentController;

    /* Talk-back descriptions for various BT icons */
    public final String mComputerDescription;
    public final String mInputPeripheralDescription;
    public final String mHeadsetDescription;
    public final String mPhoneDescription;
    public final String mImagingDescription;
    public final String mHeadphoneDescription;
    public final String mBluetoothDescription;

    private SortTask mSortTask;

    private ArrayList<CachedBluetoothDevice> mBondedDevicesSorted = new ArrayList<>();
    private ArrayList<CachedBluetoothDevice> mAvailableDevicesSorted = new ArrayList<>();

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mIcon;
        private final TextView mTitle;
        private final TextView mDesc;
        private final ImageButton mActionButton;
        private final DeviceAttributeChangeCallback mCallback =
                new DeviceAttributeChangeCallback(this);

        public ViewHolder(View view) {
            super(view);
            mTitle = (TextView) view.findViewById(R.id.title);
            mDesc = (TextView) view.findViewById(R.id.desc);
            mIcon = (ImageView) view.findViewById(R.id.icon);
            mActionButton = (ImageButton) view.findViewById(R.id.action);
            view.setOnClickListener(new BluetoothClickListener(this));
        }
    }

    public BluetoothDeviceListAdapter(
            Context context,
            LocalBluetoothManager localBluetoothManager,
            BaseFragment.FragmentController fragmentController) {
        mContext = context;
        mLocalManager = localBluetoothManager;
        mFragmentController = fragmentController;
        mLocalAdapter = mLocalManager.getBluetoothAdapter();
        mDeviceManager = mLocalManager.getCachedDeviceManager();

        Resources r = context.getResources();
        mComputerDescription = r.getString(R.string.bluetooth_talkback_computer);
        mInputPeripheralDescription = r.getString(
                R.string.bluetooth_talkback_input_peripheral);
        mHeadsetDescription = r.getString(R.string.bluetooth_talkback_headset);
        mPhoneDescription = r.getString(R.string.bluetooth_talkback_phone);
        mImagingDescription = r.getString(R.string.bluetooth_talkback_imaging);
        mHeadphoneDescription = r.getString(R.string.bluetooth_talkback_headphone);
        mBluetoothDescription = r.getString(R.string.bluetooth_talkback_bluetooth);
    }

    public void start() {
        mLocalManager.getEventManager().registerCallback(this);
        if (mLocalAdapter.isEnabled()) {
            mLocalAdapter.startScanning(true);
            addBondDevices();
            addCachedDevices();
        }
        // create task here to avoid re-executing existing tasks.
        mSortTask = new SortTask();
        mSortTask.execute();
    }

    public void stop() {
        mLocalAdapter.stopScanning();
        mDeviceManager.clearNonBondedDevices();
        mLocalManager.getEventManager().unregisterCallback(this);
        mBondedDevices.clear();
        mAvailableDevices.clear();
        mSortTask.cancel(true);
    }

    @Override
    public BluetoothDeviceListAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
            int viewType) {
        View v;
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case BONDED_DEVICE_HEADER_TYPE:
                v = layoutInflater.inflate(R.layout.single_text_line_item, parent, false);
                v.setEnabled(false);
                ((TextView) v.findViewById(R.id.title)).setText(
                        R.string.bluetooth_preference_paired_devices);
                break;
            case AVAILABLE_DEVICE_HEADER_TYPE:
                v = layoutInflater.inflate(R.layout.single_text_line_item, parent, false);
                v.setEnabled(false);
                ((TextView) v.findViewById(R.id.title)).setText(
                        R.string.bluetooth_preference_found_devices);
                break;
            default:
                v = layoutInflater.inflate(R.layout.icon_widget_line_item, parent, false);
        }
        return new ViewHolder(v);
    }

    @Override
    public int getItemCount() {
        return mAvailableDevicesSorted.size() + NUM_OF_HEADERS + mBondedDevicesSorted.size();
    }

    @Override
    public void setMaxItems(int maxItems) {
        // no limit in this list.
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final CachedBluetoothDevice bluetoothDevice = getItem(position);
        if (bluetoothDevice == null) {
            // this row is for in-list headers
            return;
        }
        if (holder.getOldPosition() != RecyclerView.NO_POSITION) {
            getItem(holder.getOldPosition()).unregisterCallback(holder.mCallback);
        }
        bluetoothDevice.registerCallback(holder.mCallback);
        holder.mTitle.setText(bluetoothDevice.getName());
        Pair<Integer, String> pair = getBtClassDrawableWithDescription(bluetoothDevice);
        holder.mIcon.setImageResource(pair.first);
        String summaryText = bluetoothDevice.getConnectionSummary();
        if (summaryText != null) {
            holder.mDesc.setText(summaryText);
            holder.mDesc.setVisibility(View.VISIBLE);
        } else {
            holder.mDesc.setVisibility(View.GONE);
        }
        if (BluetoothDeviceFilter.BONDED_DEVICE_FILTER.matches(bluetoothDevice.getDevice())) {
            holder.mActionButton.setVisibility(View.VISIBLE);
            holder.mActionButton.setOnClickListener(v -> {
                mFragmentController.launchFragment(
                        BluetoothDetailFragment.getInstance(bluetoothDevice.getDevice()));
                });
        } else {
            holder.mActionButton.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemViewType(int position) {
        // the first row is the header for the bonded device list;
        if (position == 0) {
            return BONDED_DEVICE_HEADER_TYPE;
        }
        // after the end of the bonded device list is the header of the available device list.
        if (position == mBondedDevicesSorted.size() + 1) {
            return AVAILABLE_DEVICE_HEADER_TYPE;
        }
        return DEVICE_ROW_TYPE;
    }

    private CachedBluetoothDevice getItem(int position) {
        if (position > 0 && position <= mBondedDevicesSorted.size()) {
            // off set the header row
            return mBondedDevicesSorted.get(position - 1);
        }
        if (position > mBondedDevicesSorted.size() + 1
                && position <= mBondedDevicesSorted.size() + 1 + mAvailableDevicesSorted.size()) {
            // off set two header row and the size of bonded device list.
            return mAvailableDevicesSorted.get(
                    position - NUM_OF_HEADERS - mBondedDevicesSorted.size());
        }
        // otherwise it's a in list header
        return null;
    }

    // callback functions
    @Override
    public void onDeviceAdded(CachedBluetoothDevice cachedDevice) {
        if (addDevice(cachedDevice)) {
            ArrayList<CachedBluetoothDevice> devices = new ArrayList<>(mBondedDevices);
            Collections.sort(devices);
            mBondedDevicesSorted = devices;
            notifyDataSetChanged();
        }
    }

    @Override
    public void onDeviceDeleted(CachedBluetoothDevice cachedDevice) {
        onDeviceDeleted(cachedDevice, true /* reset */);
    }

    @Override
    public void onBluetoothStateChanged(int bluetoothState) {
        switch (bluetoothState) {
            case BluetoothAdapter.STATE_OFF:
                mBondedDevices.clear();
                mBondedDevicesSorted.clear();
                mAvailableDevices.clear();
                mAvailableDevicesSorted.clear();
                notifyDataSetChanged();
                break;
            case BluetoothAdapter.STATE_ON:
                mLocalAdapter.startScanning(true);
                addBondDevices();
                addCachedDevices();
                break;
            default:
        }
    }

    public void reset() {
        mBondedDevices.clear();
        mBondedDevicesSorted.clear();
        mAvailableDevices.clear();
        mAvailableDevicesSorted.clear();
        mLocalAdapter.startScanning(true);
        addBondDevices();
        addCachedDevices();
    }

    @Override
    public void onScanningStateChanged(boolean started) {
        // don't care
    }

    @Override
    public void onDeviceBondStateChanged(CachedBluetoothDevice cachedDevice, int bondState) {
        onDeviceDeleted(cachedDevice, false /* reset */);
        onDeviceAdded(cachedDevice);
    }

    /**
     * Call back for the first connection or the last connection to ANY device/profile. Not
     * suitable for monitor per device level connection.
     */
    @Override
    public void onConnectionStateChanged(CachedBluetoothDevice cachedDevice, int state) {
        onDeviceDeleted(cachedDevice, false);
        onDeviceAdded(cachedDevice);
    }

    private void onDeviceDeleted(CachedBluetoothDevice cachedDevice, boolean refresh) {
        // the device might changed bonding state, so need to remove from both sets.
        if (mBondedDevices.remove(cachedDevice)) {
            mBondedDevicesSorted.remove(cachedDevice);
        }
        mAvailableDevices.remove(cachedDevice);
        if (refresh) {
            notifyDataSetChanged();
        }
    }

    private void addDevices(Collection<CachedBluetoothDevice> cachedDevices) {
        boolean needSort = false;
        for (CachedBluetoothDevice device : cachedDevices) {
            if (addDevice(device)) {
                needSort = true;
            }
        }
        if (needSort) {
            ArrayList<CachedBluetoothDevice> devices =
                    new ArrayList<CachedBluetoothDevice>(mBondedDevices);
            Collections.sort(devices);
            mBondedDevicesSorted = devices;
            notifyDataSetChanged();
        }
    }

    /**
     * @return {@code true} if list changed and needed sort again.
     */
    private boolean addDevice(CachedBluetoothDevice cachedDevice) {
        boolean needSort = false;
        if (BluetoothDeviceFilter.BONDED_DEVICE_FILTER.matches(cachedDevice.getDevice())) {
            if (mBondedDevices.add(cachedDevice)) {
                needSort = true;
            }
        }
        if (BluetoothDeviceFilter.UNBONDED_DEVICE_FILTER.matches(cachedDevice.getDevice())) {
            // reset is done at SortTask.
            mAvailableDevices.add(cachedDevice);
        }
        return needSort;
    }

    private void addBondDevices() {
        Set<BluetoothDevice> bondedDevices = mLocalAdapter.getBondedDevices();
        if (bondedDevices == null) {
            return;
        }
        ArrayList<CachedBluetoothDevice> cachedBluetoothDevices = new ArrayList<>();
        for (BluetoothDevice device : bondedDevices) {
            CachedBluetoothDevice cachedDevice = mDeviceManager.findDevice(device);
            if (cachedDevice == null) {
                cachedDevice = mDeviceManager.addDevice(
                        mLocalAdapter, mLocalManager.getProfileManager(), device);
            }
            cachedBluetoothDevices.add(cachedDevice);
        }
        addDevices(cachedBluetoothDevices);
    }

    private void addCachedDevices() {
        addDevices(mDeviceManager.getCachedDevicesCopy());
    }

    private Pair<Integer, String> getBtClassDrawableWithDescription(
            CachedBluetoothDevice bluetoothDevice) {
        BluetoothClass btClass = bluetoothDevice.getBtClass();
        if (btClass != null) {
            switch (btClass.getMajorDeviceClass()) {
                case BluetoothClass.Device.Major.COMPUTER:
                    return new Pair<>(R.drawable.ic_bt_laptop, mComputerDescription);

                case BluetoothClass.Device.Major.PHONE:
                    return new Pair<>(R.drawable.ic_bt_cellphone, mPhoneDescription);

                case BluetoothClass.Device.Major.PERIPHERAL:
                    return new Pair<>(HidProfile.getHidClassDrawable(btClass),
                            mInputPeripheralDescription);

                case BluetoothClass.Device.Major.IMAGING:
                    return new Pair<>(R.drawable.ic_bt_imaging, mImagingDescription);

                default:
                    // unrecognized device class; continue
            }
        } else {
            Log.w(TAG, "btClass is null");
        }

        List<LocalBluetoothProfile> profiles = bluetoothDevice.getProfiles();
        for (LocalBluetoothProfile profile : profiles) {
            int resId = profile.getDrawableResource(btClass);
            if (resId != 0) {
                return new Pair<Integer, String>(resId, null);
            }
        }
        if (btClass != null) {
            if (btClass.doesClassMatch(BluetoothClass.PROFILE_HEADSET)) {
                return new Pair<Integer, String>(R.drawable.ic_bt_headset_hfp, mHeadsetDescription);
            }
            if (btClass.doesClassMatch(BluetoothClass.PROFILE_A2DP)) {
                return new Pair<Integer, String>(R.drawable.ic_bt_headphones_a2dp,
                        mHeadphoneDescription);
            }
        }
        return new Pair<Integer, String>(R.drawable.ic_settings_bluetooth, mBluetoothDescription);
    }

    /**
     * Updates device render upon device attribute change.
     */
    // TODO: This is a walk around for handling attribute callback. Since the callback doesn't
    // contain the information about which device needs to be updated, we have to maintain a
    // local reference to the device. Fix the code in CachedBluetoothDevice.Callback to return
    // a reference of the device been updated.
    private class DeviceAttributeChangeCallback implements CachedBluetoothDevice.Callback {

        private final ViewHolder mViewHolder;

        DeviceAttributeChangeCallback(ViewHolder viewHolder) {
            mViewHolder = viewHolder;
        }

        @Override
        public void onDeviceAttributesChanged() {
            notifyItemChanged(mViewHolder.getAdapterPosition());
        }
    }

    private class BluetoothClickListener implements OnClickListener {
        private final ViewHolder mViewHolder;

        BluetoothClickListener(ViewHolder viewHolder) {
            mViewHolder = viewHolder;
        }

        @Override
        public void onClick(View v) {
            CachedBluetoothDevice device = getItem(mViewHolder.getAdapterPosition());
            int bondState = device.getBondState();

            if (device.isConnected()) {
                // TODO: ask user for confirmation
                device.disconnect();
            } else if (bondState == BluetoothDevice.BOND_BONDED) {
                device.connect(true);
            } else if (bondState == BluetoothDevice.BOND_NONE) {
                if (!device.startPairing()) {
                    showError(device.getName(),
                            R.string.bluetooth_pairing_error_message);
                    return;
                }
                // allow MAP and PBAP since this is client side, permission should be handled on
                // server side. i.e. the phone side.
                device.setPhonebookPermissionChoice(CachedBluetoothDevice.ACCESS_ALLOWED);
                device.setMessagePermissionChoice(CachedBluetoothDevice.ACCESS_ALLOWED);
            }
        }
    }

    private void showError(String name, int messageResId) {
        String message = mContext.getString(messageResId, name);
        Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Provides an ordered bt device list periodically.
     */
    // TODO: improve the way we sort BT devices. Ideally we should keep all devices in a TreeSet
    // and as devices are added the correct order is maintained, that requires a consistent
    // logic between equals and compareTo function, unfortunately it's not the case in
    // CachedBluetoothDevice class. Fix that and improve the way we order devices.
    private class SortTask extends AsyncTask<Void, Void, ArrayList<CachedBluetoothDevice>> {

        /**
         * Returns {code null} if no changed are made.
         */
        @Override
        protected ArrayList<CachedBluetoothDevice> doInBackground(Void... v) {
            if (mAvailableDevicesSorted != null
                    && mAvailableDevicesSorted.size() == mAvailableDevices.size()) {
                return null;
            }
            ArrayList<CachedBluetoothDevice> devices =
                    new ArrayList<CachedBluetoothDevice>(mAvailableDevices);
            Collections.sort(devices);
            return devices;
        }

        @Override
        protected void onPostExecute(ArrayList<CachedBluetoothDevice> devices) {
            // skip if no changes are made.
            if (devices != null) {
                mAvailableDevicesSorted = devices;
                notifyDataSetChanged();
            }
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    mSortTask = new SortTask();
                    mSortTask.execute();
                }
            }, DELAY_MILLIS);
        }
    }
}
