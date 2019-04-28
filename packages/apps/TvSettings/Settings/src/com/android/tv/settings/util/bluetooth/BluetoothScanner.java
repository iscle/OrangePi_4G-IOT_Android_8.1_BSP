/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.util.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Listens for unconfigured or problematic devices to show up on
 * bluetooth and returns lists of them.  Also manages their colors.
 */
public class BluetoothScanner {
    private static final String TAG = "BluetoothScanner";
    private static final boolean DEBUG = false;

    private static final int FOUND_ON_SCAN = -1;
    private static final int CONSECUTIVE_MISS_THRESHOLD = 4;
    private static final int FAILED_SETTING_NAME = CONSECUTIVE_MISS_THRESHOLD + 1;
    private static final int SCAN_DELAY = 4000;

    private static Receiver sReceiver;

    public static class Device {
        public BluetoothDevice btDevice;
        public String address;
        public String btName;
        public String name = "";
        public LedConfiguration leds;
        public int consecutiveMisses;
        // the type of configuration this device needs, or -1 if the device does not
        // specify a configuration type
        public int configurationType = 0;

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("Device(addr=");
            str.append(address);
            str.append(" name=\"");
            str.append(name);
            str.append("\" leds=");
            str.append(leds);
            str.append("\" configuration_type=");
            str.append(configurationType);
            str.append(")");
            return str.toString();
        }

        public String getNameString() {
            return String.format("\"%s\" (%s)", this.name,
                    this.leds == null ? "" : this.leds.getNameString());
        }

        public boolean setNameString(String str) {
            this.btName = str;
            if (str == null || !BluetoothNameUtils.isValidName(str)) {
                this.name = "";
                this.leds = null;
                return false;
            }

            this.leds = BluetoothNameUtils.getColorConfiguration(str);
            this.configurationType = BluetoothNameUtils.getSetupType(str);
            return true;
        }

        public boolean hasConfigurationType() {
            return configurationType != 0;
        }
    }

    public static class Listener {
        public void onScanningStarted() {
        }
        public void onScanningStopped(ArrayList<Device> devices) {
        }
        public void onDeviceAdded(Device device) {
        }
        public void onDeviceChanged(Device device) {
        }
        public void onDeviceRemoved(Device device) {
        }
    }

    private BluetoothScanner() {
        throw new RuntimeException("do not instantiate");
    }

    /**
     * Starts listening.  Will call onto listener with any devices we have
     * cached before this call returns.
     */
    public static void startListening(Context context, Listener listener,
            List<BluetoothDeviceCriteria> criteria) {
        if (sReceiver == null) {
            sReceiver = new Receiver(context.getApplicationContext());
        }
        sReceiver.startListening(listener, criteria);
        Log.d(TAG, "startListening");
    }

    /**
     * Removes the listener now, so there will be no more callbacks, but
     * leaves the scan running for 20 seconds to keep the cache warm just
     * in case it's needed again.
     */
    public static boolean stopListening(Listener listener) {
        Log.d(TAG, "stopListening sReceiver=" + sReceiver);
        if (sReceiver != null) {
            return sReceiver.stopListening(listener);
        }
        return false;
    }

    /**
     * Initiates a scan right now.
     */
    public static void scanNow() {
        if (sReceiver != null) {
            sReceiver.scanNow();
        }
    }

    public static void stopNow() {
        if (sReceiver != null) {
            sReceiver.stopNow();
        }
    }

    public static void removeDevice(Device device) {
        removeDevice(device.address);
    }

    public static void removeDevice(String btAddress) {
        if (sReceiver != null) {
            sReceiver.removeDevice(btAddress);
        }
    }

    private static class ClientRecord {
        public final Listener listener;
        public final ArrayList<Device> devices;
        public final List<BluetoothDeviceCriteria> matchers;

        public ClientRecord(Listener listener, List<BluetoothDeviceCriteria> matchers) {
            this.listener = listener;
            devices = new ArrayList<>();
            this.matchers = matchers;
        }
    }

    private static class Receiver extends BroadcastReceiver {
        private final Handler mHandler = new Handler();
        // TODO mListenerLock should probably now protect mClients
        private final ArrayList<ClientRecord> mClients = new ArrayList<>();
        private final ArrayList<Device> mPresentDevices = new ArrayList<>();
        private final Context mContext;
        private final BluetoothAdapter mBtAdapter;
        private static boolean mKeepScanning;
        private boolean mRegistered = false;
        private final Object mListenerLock = new Object();

        public Receiver(Context context) {
            mContext = context;

            // Bluetooth
            mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        /**
         * @param listener
         * @param matchers Pattern matchers to determine whether this listener
         * will be notified about changes in status of a discovered device. Note
         * that the matcher is only run against the device when the device is
         * first discovered, not each time it appears in scan results. Device
         * properties are assumed to be stable.
         */
        public void startListening(Listener listener, List<BluetoothDeviceCriteria> matchers) {
            int size = 0;
            ClientRecord newClient = new ClientRecord(listener, matchers);
            synchronized (mListenerLock) {
                for (int ptr = mClients.size() - 1; ptr > -1; ptr--) {
                    if (mClients.get(ptr).listener == listener) {
                        throw new RuntimeException("Listener already registered: " + listener);
                    }
                }

                // Save this listener in the list
                mClients.add(newClient);
                size = mClients.size();

            }
            // Register for broadcasts when a device is discovered
            // and broadcasts when discovery has finished
            if (size == 1) {
                mPresentDevices.clear();
                IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                mContext.registerReceiver(this, filter);
                mRegistered = true;
            }

            // Keep retrying until we say stop
            mKeepScanning = true;

            // Call back with the ones we have already
            final int N = mPresentDevices.size();
            for (int i=0; i<N; i++) {
                Device target = mPresentDevices.get(i);
                for (BluetoothDeviceCriteria matcher : newClient.matchers) {
                    if (matcher.isMatchingDevice(target.btDevice)) {
                        newClient.devices.add(target);
                        newClient.listener.onDeviceAdded(target);
                        break;
                    }
                }
            }

            // If we have a pending stop, cancel that.
            mHandler.removeCallbacks(mStopTask);

            // If there is a pending scan, we'll do one now, so we can scan any
            // pending ones.
            mHandler.removeCallbacks(mScanTask);

            scanNow();
        }

        public boolean stopListening(Listener listener) {
            final int size;
            boolean stopped = false;
            synchronized (mListenerLock) {
                for (int ptr = mClients.size() - 1; ptr > -1; ptr--) {
                    ClientRecord client = mClients.get(ptr);
                    if (client.listener == listener) {
                        mClients.remove(ptr);
                        stopped = true;
                        break;
                    }
                }
                size = mClients.size();
            }
            if (size == 0) {
                mHandler.removeCallbacks(mStopTask);
                mHandler.postDelayed(mStopTask, 20 * 1000 /* ms */);
            }
            return stopped;
        }

        public void scanNow() {
            // If we're already discovering, stop it.
            if (mBtAdapter.isDiscovering()) {
                mBtAdapter.cancelDiscovery();
            }

            sendScanningStarted();

            // Request discover from BluetoothAdapter
            mBtAdapter.startDiscovery();
        }

        public void stopNow() {
            final int size;
            synchronized (mListenerLock) {
                size = mClients.size();
            }
            if (size == 0) {
                Log.d(TAG, "mStopTask.run()");

                // cancel any pending scans
                mHandler.removeCallbacks(mScanTask);

                // If there is a pending stop, cancel it
                mHandler.removeCallbacks(mStopTask);

                // Make sure we're not doing discovery anymore
                if (mBtAdapter != null) {
                    mBtAdapter.cancelDiscovery();
                }

                // shut down discovery and prevent it from restarting
                mKeepScanning = false;

                // if the Bluetooth adapter is enabled, we're listening for discovery events and
                // should stop
                if (BluetoothAdapter.getDefaultAdapter().isEnabled() && mRegistered) {
                    mContext.unregisterReceiver(Receiver.this);
                    mRegistered = false;
                }
            }
        }

        public void removeDevice(String btAddress) {
            int count = mPresentDevices.size();
            for (int i = 0; i < count; i++) {
                Device d = mPresentDevices.get(i);
                if (btAddress.equals(d.address)) {
                    mPresentDevices.remove(d);
                    break;
                }
            }

            for (int ptr = mClients.size() - 1; ptr > -1; ptr--) {
                ClientRecord client = mClients.get(ptr);
                for (int devPtr = client.devices.size() - 1; devPtr > -1; devPtr--) {
                    Device d = client.devices.get(devPtr);
                    if (btAddress.equals(d.address)) {
                        client.devices.remove(devPtr);
                        break;
                    }
                }
            }
        }

        private final Runnable mStopTask = new Runnable() {
            @Override
            public void run() {
                synchronized (mListenerLock) {
                    if (mClients.size() != 0) {
                        throw new RuntimeException("mStopTask running with mListeners.size="
                                + mClients.size());
                    }
                }
                stopNow();
            }
        };

        private final Runnable mScanTask = new Runnable() {
            @Override
            public void run() {
                // If there is a pending scan request, cancel it
                mHandler.removeCallbacks(mScanTask);

                scanNow();
            }
        };

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                // When discovery finds a device

                // Get the BluetoothDevice object from the Intent
                BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                final String address = btDevice.getAddress();
                String name = btDevice.getName();

                if (DEBUG) {
                    Log.d(TAG, "Device found, address: " + address + " name: \"" + name + "\"");
                }

                if (address == null || name == null) {
                    return;
                }

                // Older Bluetooth stacks may append a null character to a device name
                if (name.endsWith("\0")) {
                    name = name.substring(0, name.length() - 1);
                }

                // See if this is a device we already know about
                Device device = null;
                final int N = mPresentDevices.size();
                for (int i=0; i<N; i++) {
                    final Device d = mPresentDevices.get(i);
                    if (address.equals(d.address)) {
                        device = d;
                        break;
                    }
                }

                if (device == null) {
                    if (DEBUG) {
                        Log.d(TAG, "Device is a new device.");
                    }
                    // New device.
                    device = new Device();
                    device.btDevice = btDevice;
                    device.address = address;
                    device.consecutiveMisses = -1;

                    device.setNameString(name);
                    // Save it
                    mPresentDevices.add(device);

                    // Tell the listeners
                    sendDeviceAdded(device);
                } else {
                    if (DEBUG) {
                        Log.d(TAG, "Device is an existing device.");
                    }
                    // Existing device: update miss count.
                    device.consecutiveMisses = FOUND_ON_SCAN;
                    if (device.btName == name
                            || (device.btName != null && device.btName.equals(name))) {
                        // Name hasn't changed
                        return;
                    } else {
                        device.setNameString(name);
                        sendDeviceChanged(device);
                        // If we can't parse it properly, treat it as a delete
                        // when we iterate through them again.
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                // Clear any devices that have disappeared since the last scan completed
                final int N = mPresentDevices.size();
                for (int i=N-1; i>=0; i--) {
                    Device device = mPresentDevices.get(i);
                    if (device.consecutiveMisses < 0) {
                        // -1 means found on this scan, raise to 0 for next time
                        if (DEBUG) Log.d(TAG, device.address + " -- Found");
                        device.consecutiveMisses = 0;

                    } else if (device.consecutiveMisses >= CONSECUTIVE_MISS_THRESHOLD) {
                        // Too many failures
                        if (DEBUG) Log.d(TAG, device.address + " -- Removing");
                        mPresentDevices.remove(i);
                        sendDeviceRemoved(device);

                    } else {
                        // Didn't see it this time, but not ready to delete it yet
                        device.consecutiveMisses++;
                        if (DEBUG) {
                            Log.d(TAG, device.address + " -- Missed consecutiveMisses="
                                    + device.consecutiveMisses);
                        }
                    }
                }

                // Show status when scanning is completed.
                sendScanningStopped();

                if (mKeepScanning) {
                    // Try again in SCAN_DELAY ms.
                    mHandler.postDelayed(mScanTask, SCAN_DELAY);
                }
            }
        }

        private void sendScanningStarted() {
            synchronized (mListenerLock) {
                final int N = mClients.size();
                for (int i = 0; i < N; i++) {
                    mClients.get(i).listener.onScanningStarted();
                }
            }
        }

        private void sendScanningStopped() {
            synchronized (mListenerLock) {
                final int N = mClients.size();
                // Loop backwards through the list in case a client wants to
                // remove its listener in this callback.
                for (int i = N - 1; i >= 0; --i) {
                    ClientRecord client = mClients.get(i);
                    client.listener.onScanningStopped(client.devices);
                }
            }
        }

        private void sendDeviceAdded(Device device) {
            synchronized (mListenerLock) {
                for (int ptr = mClients.size() - 1; ptr > -1; ptr--) {
                    ClientRecord client = mClients.get(ptr);
                    for (BluetoothDeviceCriteria matcher : client.matchers) {
                        if (matcher.isMatchingDevice(device.btDevice)) {
                            client.devices.add(device);
                            client.listener.onDeviceAdded(device);
                            break;
                        }
                    }
                }
            }
        }

        private void sendDeviceChanged(Device device) {
            synchronized (mListenerLock) {
                final int N = mClients.size();
                for (int i = 0; i < N; i++) {
                    ClientRecord client = mClients.get(i);
                    for (int ptr = client.devices.size() - 1; ptr > -1; ptr--) {
                        Device d = client.devices.get(ptr);
                        if (d.btDevice.getAddress().equals(device.btDevice.getAddress())) {
                            client.listener.onDeviceChanged(device);
                            break;
                        }
                    }
                }
            }
        }

        private void sendDeviceRemoved(Device device) {
            synchronized (mListenerLock) {
                for (int ptr = mClients.size() - 1; ptr > -1; ptr--) {
                    ClientRecord client = mClients.get(ptr);
                    for (int devPtr = client.devices.size() - 1; devPtr > -1; devPtr--) {
                        Device d = client.devices.get(devPtr);
                        if (d.btDevice.getAddress().equals(device.btDevice.getAddress())) {
                            client.devices.remove(devPtr);
                            client.listener.onDeviceRemoved(device);
                            break;
                        }
                    }
                }
            }
        }
    }
}
