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

package com.android.tv.settings.accessories;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.IBluetoothA2dp;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;

import com.android.tv.settings.util.bluetooth.BluetoothDeviceCriteria;
import com.android.tv.settings.util.bluetooth.BluetoothScanner;

import java.util.ArrayList;
import java.util.List;

/**
 * Monitors available Bluetooth devices and manages process of pairing
 * and connecting to the device.
 */
public class BluetoothDevicePairer {

    /**
     * This class operates in two modes, automatic and manual.
     *
     * AUTO MODE
     * In auto mode we listen for an input device that looks like it can
     * generate DPAD events. When one is found we wait
     * {@link #DELAY_AUTO_PAIRING} milliseconds before starting the process of
     * connecting to the device. The idea is that a UI making use of this class
     * would give the user a chance to cancel pairing during this window. Once
     * the connection process starts, it is considered uninterruptible.
     *
     * Connection is accomplished in two phases, bonding and socket connection.
     * First we try to create a bond to the device and listen for bond status
     * change broadcasts. Once the bond is made, we connect to the device.
     * Connecting to the device actually opens a socket and hooks the device up
     * to the input system.
     *
     * In auto mode if we see more than one compatible input device before
     * bonding with a candidate device, we stop the process. We don't want to
     * connect to the wrong device and it is up to the user of this class to
     * tell us what to connect to.
     *
     * MANUAL MODE
     * Manual mode is where a user of this class explicitly tells us which
     * device to connect to. To switch to manual mode you can call
     * {@link #cancelPairing()}. It is safe to call this method even if no
     * device connection process is underway. You would then call
     * {@link #start()} to resume scanning for devices. Once one is found
     * that you want to connect to, call {@link #startPairing(BluetoothDevice)}
     * to start the connection process. At this point the same process is
     * followed as when we start connection in auto mode.
     *
     * Even in manual mode there is a timeout before we actually start
     * connecting, but it is {@link #DELAY_MANUAL_PAIRING}.
     */

    public static final String TAG = "BluetoothDevicePairer";
    public static final int STATUS_ERROR = -1;
    public static final int STATUS_NONE = 0;
    public static final int STATUS_SCANNING = 1;
    /**
     * A device to pair with has been identified, we're currently in the
     * timeout period where the process can be cancelled.
     */
    public static final int STATUS_WAITING_TO_PAIR = 2;
    /**
     * Pairing is in progress.
     */
    public static final int STATUS_PAIRING = 3;
    /**
     * Device has been paired with, we are opening a connection to the device.
     */
    public static final int STATUS_CONNECTING = 4;


    public interface EventListener {
        /**
         * The status of the {@link BluetoothDevicePairer} changed.
         */
        void statusChanged();
    }

    public interface BluetoothConnector {
        void openConnection(BluetoothAdapter adapter);
    }

    public interface OpenConnectionCallback {
        /**
         * Call back when BT device connection is completed.
         */
        void succeeded();
        void failed();
    }

    /**
     * Time between when a single input device is found and pairing begins. If
     * one or more other input devices are found before this timeout or
     * {@link #cancelPairing()} is called then pairing will not proceed.
     */
    public static final int DELAY_AUTO_PAIRING = 15 * 1000;
    /**
     * Time between when the call to {@link #startPairing(BluetoothDevice)} is
     * called and when we actually start pairing. This gives the caller a
     * chance to change their mind.
     */
    public static final int DELAY_MANUAL_PAIRING = 5 * 1000;
    /**
     * If there was an error in pairing, we will wait this long before trying
     * again.
     */
    public static final int DELAY_RETRY = 5 * 1000;

    private static final int MSG_PAIR = 1;
    private static final int MSG_START = 2;

    private static final boolean DEBUG = true;

    private static final String[] INVALID_INPUT_KEYBOARD_DEVICE_NAMES = {
        "gpio-keypad", "cec_keyboard", "Virtual", "athome_remote"
    };

    private final BluetoothScanner.Listener mBtListener = new BluetoothScanner.Listener() {
        @Override
        public void onDeviceAdded(BluetoothScanner.Device device) {
            if (DEBUG) {
                Log.d(TAG, "Adding device: " + device.btDevice.getAddress());
            }
            onDeviceFound(device.btDevice);
        }

        @Override
        public void onDeviceRemoved(BluetoothScanner.Device device) {
            if (DEBUG) {
                Log.d(TAG, "Device lost: " + device.btDevice.getAddress());
            }
            onDeviceLost(device.btDevice);
        }
    };

    public static boolean hasValidInputDevice(Context context, int[] deviceIds) {
        InputManager inMan = (InputManager) context.getSystemService(Context.INPUT_SERVICE);

        for (int ptr = deviceIds.length - 1; ptr > -1; ptr--) {
            InputDevice device = inMan.getInputDevice(deviceIds[ptr]);
            int sources = device.getSources();

            boolean isCompatible = false;

            if ((sources & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD) {
                isCompatible = true;
            }

            if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
                isCompatible = true;
            }

            if ((sources & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD) {
                boolean isValidKeyboard = true;
                String keyboardName = device.getName();
                for (int index = 0; index < INVALID_INPUT_KEYBOARD_DEVICE_NAMES.length; ++index) {
                    if (keyboardName.equals(INVALID_INPUT_KEYBOARD_DEVICE_NAMES[index])) {
                        isValidKeyboard = false;
                        break;
                    }
                }

                if (isValidKeyboard) {
                    isCompatible = true;
                }
            }

            if (!device.isVirtual() && isCompatible) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasValidInputDevice(Context context) {
        InputManager inMan = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        int[] inputDevices = inMan.getInputDeviceIds();

        return hasValidInputDevice(context, inputDevices);
    }

    private final BroadcastReceiver mLinkStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (DEBUG) {
                Log.d(TAG, "There was a link status change for: " + device.getAddress());
            }

            if (device.equals(mTarget)) {
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.BOND_NONE);
                int previousBondState = intent.getIntExtra(
                        BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE);

                if (DEBUG) {
                    Log.d(TAG, "Bond states: old = " + previousBondState + ", new = " +
                        bondState);
                }

                if (bondState == BluetoothDevice.BOND_NONE &&
                        previousBondState == BluetoothDevice.BOND_BONDING) {
                    // we seem to have reverted, this is an error
                    // TODO inform user, start scanning again
                    unregisterLinkStatusReceiver();
                    onBondFailed();
                } else if (bondState == BluetoothDevice.BOND_BONDED) {
                    unregisterLinkStatusReceiver();
                    onBonded();
                }
            }
        }
    };

    private BroadcastReceiver mBluetoothStateReceiver;

    private final OpenConnectionCallback mOpenConnectionCallback = new OpenConnectionCallback() {
        public void succeeded() {
            setStatus(STATUS_NONE);
        }
        public void failed() {
            setStatus(STATUS_ERROR);
        }
    };

    private final Context mContext;
    private EventListener mListener;
    private int mStatus = STATUS_NONE;
    /**
     * Set to {@code false} when {@link #cancelPairing()} or
     * {@link #startPairing(BluetoothDevice)}. This instance
     * will now no longer automatically start pairing.
     */
    private boolean mAutoMode = true;
    private final ArrayList<BluetoothDevice> mVisibleDevices = new ArrayList<>();
    private BluetoothDevice mTarget;
    private final Handler mHandler;
    private long mNextStageTimestamp = -1;
    private boolean mLinkReceiverRegistered = false;
    private final ArrayList<BluetoothDeviceCriteria> mBluetoothDeviceCriteria = new ArrayList<>();
    private InputDeviceCriteria mInputDeviceCriteria;

    /**
     * Should be instantiated on a thread with a Looper, perhaps the main thread!
     */
    public BluetoothDevicePairer(Context context, EventListener listener) {
        mContext = context.getApplicationContext();
        mListener = listener;

        addBluetoothDeviceCriteria();

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_PAIR:
                        startBonding();
                        break;
                    case MSG_START:
                        start();
                        break;
                    default:
                        Log.d(TAG, "No handler case available for message: " + msg.what);
                }
            }
        };
    }

    private void addBluetoothDeviceCriteria() {
        // Input is supported by all devices.
        mInputDeviceCriteria = new InputDeviceCriteria();
        mBluetoothDeviceCriteria.add(mInputDeviceCriteria);

        // Add Bluetooth a2dp on if the service is running and the
        // setting profile_supported_a2dp is set to true.
        Intent intent = new Intent(IBluetoothA2dp.class.getName());
        ComponentName comp = intent.resolveSystemService(mContext.getPackageManager(), 0);
        if (comp != null) {
            int enabledState = mContext.getPackageManager().getComponentEnabledSetting(comp);
            if (enabledState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                Log.d(TAG, "Adding A2dp device criteria for pairing");
                mBluetoothDeviceCriteria.add(new A2dpDeviceCriteria());
            }
        }
    }

    /**
     * Start listening for devices and begin the pairing process when
     * criteria is met.
     */
    public void start() {
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth not enabled, delaying startup.");
            if (mBluetoothStateReceiver == null) {
                mBluetoothStateReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                                BluetoothAdapter.STATE_OFF) == BluetoothAdapter.STATE_ON) {
                            Log.d(TAG, "Bluetooth now enabled, starting.");
                            start();
                        } else {
                            Log.d(TAG, "Bluetooth not yet started, got broadcast: " + intent);
                        }
                    }
                };
                mContext.registerReceiver(mBluetoothStateReceiver,
                        new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            }

            bluetoothAdapter.enable();
            return;
        } else {
            if (mBluetoothStateReceiver != null) {
                mContext.unregisterReceiver(mBluetoothStateReceiver);
                mBluetoothStateReceiver = null;
            }
        }

        // set status to scanning before we start listening since
        // startListening may result in a transition to STATUS_WAITING_TO_PAIR
        // which might seem odd from a client perspective
        setStatus(STATUS_SCANNING);

        BluetoothScanner.startListening(mContext, mBtListener, mBluetoothDeviceCriteria);
    }

    public void clearDeviceList() {
        doCancel();
        mVisibleDevices.clear();
    }

    /**
     * Stop any pairing request that is in progress.
     */
    public void cancelPairing() {
        mAutoMode = false;
        doCancel();
    }


    /**
     * Switch to manual pairing mode.
     */
    public void disableAutoPairing() {
        mAutoMode = false;
    }

    /**
     * Stop doing anything we're doing, release any resources.
     */
    public void dispose() {
        mHandler.removeCallbacksAndMessages(null);
        if (mLinkReceiverRegistered) {
            unregisterLinkStatusReceiver();
        }
        if (mBluetoothStateReceiver != null) {
            mContext.unregisterReceiver(mBluetoothStateReceiver);
        }
        stopScanning();
    }

    /**
     * Start pairing and connection to the specified device.
     * @param device device
     */
    public void startPairing(BluetoothDevice device) {
        startPairing(device, true);
    }

    /**
     * Return our state
     * @return One of the STATE_ constants.
     */
    public int getStatus() {
        return mStatus;
    }

    /**
     * Get the device that we're currently targeting. This will be null if
     * there is no device that is in the process of being connected to.
     */
    public BluetoothDevice getTargetDevice() {
        return mTarget;
    }

    /**
     * When the timer to start the next stage will expire, in {@link SystemClock#elapsedRealtime()}.
     * Will only be valid while waiting to pair and after an error from which we are restarting.
     */
    public long getNextStageTime() {
        return mNextStageTimestamp;
    }

    public List<BluetoothDevice> getAvailableDevices() {
        ArrayList<BluetoothDevice> copy = new ArrayList<>(mVisibleDevices.size());
        copy.addAll(mVisibleDevices);
        return copy;
    }

    public void setListener(EventListener listener) {
        mListener = listener;
    }

    public void invalidateDevice(BluetoothDevice device) {
        onDeviceLost(device);
    }

    private void startPairing(BluetoothDevice device, boolean isManual) {
        // TODO check if we're already paired/bonded to this device

        // cancel auto-mode if applicable
        mAutoMode = !isManual;

        mTarget = device;

        if (isInProgress()) {
            throw new RuntimeException("Pairing already in progress, you must cancel the " +
                    "previous request first");
        }

        mHandler.removeCallbacksAndMessages(null);

        mNextStageTimestamp = SystemClock.elapsedRealtime() +
                (mAutoMode ? DELAY_AUTO_PAIRING : DELAY_MANUAL_PAIRING);
        mHandler.sendEmptyMessageDelayed(MSG_PAIR,
                mAutoMode ? DELAY_AUTO_PAIRING : DELAY_MANUAL_PAIRING);

        setStatus(STATUS_WAITING_TO_PAIR);
    }

    /**
     * Pairing is in progress and is no longer cancelable.
     */
    public boolean isInProgress() {
        return mStatus != STATUS_NONE && mStatus != STATUS_ERROR && mStatus != STATUS_SCANNING &&
                mStatus != STATUS_WAITING_TO_PAIR;
    }

    private void updateListener() {
        if (mListener != null) {
            mListener.statusChanged();
        }
    }

    private void onDeviceFound(BluetoothDevice device) {
        if (!mVisibleDevices.contains(device)) {
            mVisibleDevices.add(device);
            Log.d(TAG, "Added device to visible list. Name = " + device.getName() + " , class = " +
                    device.getBluetoothClass().getDeviceClass());
        } else {
            return;
        }

        updatePairingState();
        // update the listener because a new device is visible
        updateListener();
    }

    private void onDeviceLost(BluetoothDevice device) {
        // TODO validate removal works as expected
        if (mVisibleDevices.remove(device)) {
            updatePairingState();
            // update the listener because a device disappeared
            updateListener();
        }
    }

    private void updatePairingState() {
        if (mAutoMode) {
            BluetoothDevice candidate = getAutoPairDevice();
            if (null != candidate) {
                mTarget = candidate;
                startPairing(mTarget, false);
            } else {
                doCancel();
            }
        }
    }

    /**
     * @return returns the only visible input device if there is only one
     */
    private BluetoothDevice getAutoPairDevice() {
        List<BluetoothDevice> inputDevices = new ArrayList<>();
        for (BluetoothDevice device : mVisibleDevices) {
            if (mInputDeviceCriteria.isInputDevice(device.getBluetoothClass())) {
                inputDevices.add(device);
            }
        }
        if (inputDevices.size() == 1) {
            return inputDevices.get(0);
        }
        return null;
    }

    private void doCancel() {
        // TODO allow cancel to be called from any state
        if (isInProgress()) {
            Log.d(TAG, "Pairing process has already begun, it can not be canceled.");
            return;
        }

        // stop scanning, just in case we are
        final boolean wasListening = BluetoothScanner.stopListening(mBtListener);
        BluetoothScanner.stopNow();

        mHandler.removeCallbacksAndMessages(null);

        // remove bond, if existing
        unpairDevice(mTarget);

        mTarget = null;

        setStatus(STATUS_NONE);

        // resume scanning
        if (wasListening) {
            start();
        }
    }

    /**
     * Set the status and update any listener.
     */
    private void setStatus(int status) {
        mStatus = status;
        updateListener();
    }

    private void startBonding() {
        stopScanning();
        setStatus(STATUS_PAIRING);
        if (mTarget.getBondState() != BluetoothDevice.BOND_BONDED) {
            registerLinkStatusReceiver();

            // create bond (pair) to the device
            mTarget.createBond();
        } else {
            onBonded();
        }
    }

    private void onBonded() {
        openConnection();
    }

    private void openConnection() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothConnector btConnector = getBluetoothConnector();
        if (btConnector != null) {
            setStatus(STATUS_CONNECTING);
            btConnector.openConnection(adapter);
        } else {
            Log.w(TAG, "There was an error getting the BluetoothConnector.");
            setStatus(STATUS_ERROR);
            if (mLinkReceiverRegistered) {
                unregisterLinkStatusReceiver();
            }
            unpairDevice(mTarget);
        }
    }

    private void onBondFailed() {
        Log.w(TAG, "There was an error bonding with the device.");
        setStatus(STATUS_ERROR);

        // remove bond, if existing
        unpairDevice(mTarget);

        // TODO do we need to check Bluetooth for the device and possible delete it?
        mNextStageTimestamp = SystemClock.elapsedRealtime() + DELAY_RETRY;
        mHandler.sendEmptyMessageDelayed(MSG_START, DELAY_RETRY);
    }

    private void registerLinkStatusReceiver() {
        mLinkReceiverRegistered = true;
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        mContext.registerReceiver(mLinkStatusReceiver, filter);
    }

    private void unregisterLinkStatusReceiver() {
        mLinkReceiverRegistered = false;
        mContext.unregisterReceiver(mLinkStatusReceiver);
    }

    private void stopScanning() {
        BluetoothScanner.stopListening(mBtListener);
        BluetoothScanner.stopNow();
    }

    public boolean unpairDevice(BluetoothDevice device) {
        if (device != null) {
            int state = device.getBondState();

            if (state == BluetoothDevice.BOND_BONDING) {
                device.cancelBondProcess();
            }

            if (state != BluetoothDevice.BOND_NONE) {
                final boolean successful = device.removeBond();
                if (successful) {
                    if (DEBUG) {
                        Log.d(TAG, "Bluetooth device successfully unpaired: " + device.getName());
                    }
                    return true;
                } else {
                    Log.e(TAG, "Failed to unpair Bluetooth Device: " + device.getName());
                }
            }
        }
        return false;
    }

    private BluetoothConnector getBluetoothConnector() {
        int majorDeviceClass = mTarget.getBluetoothClass().getMajorDeviceClass();
        switch (majorDeviceClass) {
            case BluetoothClass.Device.Major.PERIPHERAL:
                return new BluetoothInputDeviceConnector(
                    mContext, mTarget, mHandler, mOpenConnectionCallback);
            case BluetoothClass.Device.Major.AUDIO_VIDEO:
                return new BluetoothA2dpConnector(mContext, mTarget, mOpenConnectionCallback);
            default:
                Log.d(TAG, "Unhandle device class: " + majorDeviceClass);
                break;
        }
        return null;
    }
}
