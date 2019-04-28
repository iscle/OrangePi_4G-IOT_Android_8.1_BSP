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

import android.app.Activity;
import android.app.FragmentManager;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.android.tv.settings.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity for detecting and adding (pairing) new bluetooth devices.
 */
public class AddAccessoryActivity extends Activity implements BluetoothDevicePairer.EventListener {

    private static final boolean DEBUG = false;
    private static final String TAG = "AddAccessoryActivity";

    private static final String ACTION_CONNECT_INPUT =
            "com.google.android.intent.action.CONNECT_INPUT";

    private static final String INTENT_EXTRA_NO_INPUT_MODE = "no_input_mode";

    private static final String SAVED_STATE_PREFERENCE_FRAGMENT =
            "AddAccessoryActivity.PREFERENCE_FRAGMENT";
    private static final String SAVED_STATE_CONTENT_FRAGMENT =
            "AddAccessoryActivity.CONTENT_FRAGMENT";
    private static final String SAVED_STATE_BLUETOOTH_DEVICES =
            "AddAccessoryActivity.BLUETOOTH_DEVICES";

    private static final String ADDRESS_NONE = "NONE";

    private static final int AUTOPAIR_COUNT = 10;

    private static final int MSG_UPDATE_VIEW = 1;
    private static final int MSG_REMOVE_CANCELED = 2;
    private static final int MSG_PAIRING_COMPLETE = 3;
    private static final int MSG_OP_TIMEOUT = 4;
    private static final int MSG_RESTART = 5;
    private static final int MSG_TRIGGER_SELECT_DOWN = 6;
    private static final int MSG_TRIGGER_SELECT_UP = 7;
    private static final int MSG_AUTOPAIR_TICK = 8;
    private static final int MSG_START_AUTOPAIR_COUNTDOWN = 9;

    private static final int CANCEL_MESSAGE_TIMEOUT = 3000;
    private static final int DONE_MESSAGE_TIMEOUT = 3000;
    private static final int PAIR_OPERATION_TIMEOUT = 120000;
    private static final int CONNECT_OPERATION_TIMEOUT = 15000;
    private static final int RESTART_DELAY = 3000;
    private static final int LONG_PRESS_DURATION = 3000;
    private static final int KEY_DOWN_TIME = 150;
    private static final int TIME_TO_START_AUTOPAIR_COUNT = 5000;
    private static final int EXIT_TIMEOUT_MILLIS = 90 * 1000;

    private AddAccessoryPreferenceFragment mPreferenceFragment;
    private AddAccessoryContentFragment mContentFragment;

    // members related to Bluetooth pairing
    private BluetoothDevicePairer mBluetoothPairer;
    private int mPreviousStatus = BluetoothDevicePairer.STATUS_NONE;
    private boolean mPairingSuccess = false;
    private boolean mPairingBluetooth = false;
    private List<BluetoothDevice> mBluetoothDevices;
    private String mCancelledAddress = ADDRESS_NONE;
    private String mCurrentTargetAddress = ADDRESS_NONE;
    private String mCurrentTargetStatus = "";
    private boolean mPairingInBackground = false;

    private boolean mDone = false;

    private boolean mHwKeyDown;
    private boolean mHwKeyDidSelect;
    private boolean mNoInputMode;

    private static final String GAMEPAD_PAIRING_SERVICE =
            "com.google.android.settings.accessories.GamepadPairingService";
    private static final String GAMEPAD_PAIRING_PACKAGE =
            "com.google.android.tv.remotepairing";
    /* Keep in sync with GamepadPairingService */
    private static final int GAMEPAD_MESSAGE_START_PAIRING = 1;
    private static final int GAMEPAD_MESSAGE_STOP_PAIRING = 2;
    private static final int GAMEPAD_MESSAGE_PAUSE_PAIRING = 3;
    private static final int GAMEPAD_MESSAGE_RESUME_PAIRING = 4;
    private final List<Message> mGamepadMessageQueue = new ArrayList<>();
    private Messenger mGamepadService;
    private ServiceConnection mGamepadServiceConn;

    private class GamepadServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Gamepad Service Connected");
            mGamepadService = new Messenger(service);
            for (Message message : mGamepadMessageQueue) {
                try {
                    mGamepadService.send(message);
                } catch (RemoteException e) {
                    Log.e(TAG, "Remote exception sending message " + message + " " + e);
                }
            }
            mGamepadMessageQueue.clear();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Gamepad Service Disconnected");
            mGamepadService = null;
        }
    };

    // Internal message handler
    private final MessageHandler mMsgHandler = new MessageHandler();

    private static class MessageHandler extends Handler {

        private WeakReference<AddAccessoryActivity> mActivityRef = new WeakReference<>(null);

        public void setActivity(AddAccessoryActivity activity) {
            mActivityRef = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            final AddAccessoryActivity activity = mActivityRef.get();
            if (activity == null) {
                return;
            }
            switch (msg.what) {
                case MSG_UPDATE_VIEW:
                    activity.updateView();
                    break;
                case MSG_REMOVE_CANCELED:
                    activity.mCancelledAddress = ADDRESS_NONE;
                    activity.updateView();
                    break;
                case MSG_PAIRING_COMPLETE:
                    activity.finish();
                    break;
                case MSG_OP_TIMEOUT:
                    activity.handlePairingTimeout();
                    break;
                case MSG_RESTART:
                    if (activity.mBluetoothPairer != null) {
                        activity.mBluetoothPairer.start();
                        activity.mBluetoothPairer.cancelPairing();
                    }
                    break;
                case MSG_TRIGGER_SELECT_DOWN:
                    activity.sendKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER, true);
                    activity.mHwKeyDidSelect = true;
                    sendEmptyMessageDelayed(MSG_TRIGGER_SELECT_UP, KEY_DOWN_TIME);
                    activity.cancelPairingCountdown();
                    break;
                case MSG_TRIGGER_SELECT_UP:
                    activity.sendKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER, false);
                    break;
                case MSG_START_AUTOPAIR_COUNTDOWN:
                    activity.setPairingText(
                            activity.getString(R.string.accessories_autopair_msg, AUTOPAIR_COUNT));
                    sendMessageDelayed(obtainMessage(MSG_AUTOPAIR_TICK,
                            AUTOPAIR_COUNT, 0, null), 1000);
                    break;
                case MSG_AUTOPAIR_TICK:
                    int countToAutoPair = msg.arg1 - 1;
                    if (countToAutoPair <= 0) {
                        activity.setPairingText(null);
                        // AutoPair
                        activity.startAutoPairing();
                    } else {
                        activity.setPairingText(
                                activity.getString(R.string.accessories_autopair_msg,
                                        countToAutoPair));
                        sendMessageDelayed(obtainMessage(MSG_AUTOPAIR_TICK,
                                countToAutoPair, 0, null), 1000);
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private final Handler mAutoExitHandler = new Handler();

    private final Runnable mAutoExitRunnable = new Runnable() {
        @Override
        public void run() {
            finish();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.lb_dialog_fragment);

        Intent intent = new Intent();
        intent.setAction(GAMEPAD_PAIRING_SERVICE);
        intent.setPackage(GAMEPAD_PAIRING_PACKAGE);
        if (serviceIntentIsHandled(intent)) {
            // Don't auto-start the service. If it's not running we don't need to pause it.
            mGamepadServiceConn = new GamepadServiceConnection();
            bindService(intent, mGamepadServiceConn, BIND_ADJUST_WITH_ACTIVITY);
        }

        pauseGamepadPairingService();

        mMsgHandler.setActivity(this);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        mNoInputMode = getIntent().getBooleanExtra(INTENT_EXTRA_NO_INPUT_MODE, false);
        mHwKeyDown = false;

        if (savedInstanceState == null) {
            mBluetoothDevices = new ArrayList<>();
        } else {
            mBluetoothDevices =
                    savedInstanceState.getParcelableArrayList(SAVED_STATE_BLUETOOTH_DEVICES);
        }

        final FragmentManager fm = getFragmentManager();
        if (savedInstanceState == null) {
            mPreferenceFragment = AddAccessoryPreferenceFragment.newInstance();
            mContentFragment = AddAccessoryContentFragment.newInstance();
            fm.beginTransaction()
                    .add(R.id.action_fragment, mPreferenceFragment)
                    .add(R.id.content_fragment, mContentFragment)
                    .commit();
        } else {
            mPreferenceFragment = (AddAccessoryPreferenceFragment)
                    fm.getFragment(savedInstanceState,
                            SAVED_STATE_PREFERENCE_FRAGMENT);
            mContentFragment = (AddAccessoryContentFragment)
                    fm.getFragment(savedInstanceState,
                            SAVED_STATE_CONTENT_FRAGMENT);
        }

        rearrangeViews();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        getFragmentManager().putFragment(outState,
                SAVED_STATE_PREFERENCE_FRAGMENT, mPreferenceFragment);
        getFragmentManager().putFragment(outState,
                SAVED_STATE_CONTENT_FRAGMENT, mContentFragment);
        outState.putParcelableList(SAVED_STATE_BLUETOOTH_DEVICES, mBluetoothDevices);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (DEBUG) {
            Log.d(TAG, "onStart() mPairingInBackground = " + mPairingInBackground);
        }

        // Only do the following if we are not coming back to this activity from
        // the Secure Pairing activity.
        if (!mPairingInBackground) {
            startBluetoothPairer();
        }

        mPairingInBackground = false;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mNoInputMode) {
            // Start timer count down for exiting activity.
            if (DEBUG) Log.d(TAG, "starting auto-exit timer");
            mAutoExitHandler.postDelayed(mAutoExitRunnable, EXIT_TIMEOUT_MILLIS);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.d(TAG, "stopping auto-exit timer");
        mAutoExitHandler.removeCallbacks(mAutoExitRunnable);
    }


    @Override
    public void onStop() {
        if (DEBUG) {
            Log.d(TAG, "onStop()");
        }
        if (!mPairingBluetooth) {
            stopBluetoothPairer();
            mMsgHandler.removeCallbacksAndMessages(null);
        } else {
            // allow activity to remain in the background while we perform the
            // BT Secure pairing.
            mPairingInBackground = true;
        }

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        resumeGamepadPairingService();
        if (mGamepadServiceConn != null) {
            unbindService(mGamepadServiceConn);
            mGamepadServiceConn = null;
        }
        stopBluetoothPairer();
        mMsgHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {
            if (mPairingBluetooth && !mDone) {
                cancelBtPairing();
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (ACTION_CONNECT_INPUT.equals(intent.getAction()) &&
                (intent.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0) {

            KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_PAIRING) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    onHwKeyEvent(false);
                } else if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    onHwKeyEvent(true);
                }
            }
        } else {
            setIntent(intent);
        }
    }

    private boolean serviceIntentIsHandled(Intent intent) {
        List<ResolveInfo> matches = getPackageManager().queryIntentServices(intent, 0);
        if (matches != null) {
            for (ResolveInfo info : matches) {
                if (info.serviceInfo != null && info.serviceInfo.enabled) {
                    return true;
                }
            }
        }
        return false;
    }

    private void pauseGamepadPairingService() {
        final Message m = Message.obtain(null, GAMEPAD_MESSAGE_PAUSE_PAIRING);
        if (mGamepadService != null) {
            try {
                mGamepadService.send(m);
            } catch (RemoteException e) {
                Log.e(TAG, "Remote exception sending message " + m + " " + e);
            }
        } else {
            Log.d(TAG, "Queueing pause message");
            mGamepadMessageQueue.add(m);
        }
    }

    private void resumeGamepadPairingService() {
        final Message m = Message.obtain(null, GAMEPAD_MESSAGE_RESUME_PAIRING);
        if (mGamepadService != null) {
            try {
                mGamepadService.send(m);
            } catch (RemoteException e) {
                Log.e(TAG, "Remote exception sending message " + m + " " + e);
            }
        } else {
            Log.d(TAG, "Queueing resume message");
            mGamepadMessageQueue.add(m);
        }
    }


    public void onActionClicked(String address) {
        cancelPairingCountdown();
        if (!mDone) {
            btDeviceClicked(address);
        }
    }

    // Events related to a device HW key
    protected void onHwKeyEvent(boolean keyDown) {
        if (!mHwKeyDown) {
            // HW key was in UP state before
            if (keyDown) {
                // Back key pressed down
                mHwKeyDown = true;
                mHwKeyDidSelect = false;
                mMsgHandler.sendEmptyMessageDelayed(MSG_TRIGGER_SELECT_DOWN, LONG_PRESS_DURATION);
            }
        } else {
            // HW key was in DOWN state before
            if (!keyDown) {
                // HW key released
                mHwKeyDown = false;
                mMsgHandler.removeMessages(MSG_TRIGGER_SELECT_DOWN);
                if (!mHwKeyDidSelect) {
                    // key wasn't pressed long enough for selection, move selection
                    // to next item.
                    mPreferenceFragment.advanceSelection();
                }
                mHwKeyDidSelect = false;
            }
        }
    }

    private void sendKeyEvent(int keyCode, boolean down) {
        InputManager iMgr = (InputManager) getSystemService(INPUT_SERVICE);
        if (iMgr != null) {
            long time = SystemClock.uptimeMillis();
            KeyEvent evt = new KeyEvent(time, time,
                    down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
                    keyCode, 0);
            iMgr.injectInputEvent(evt, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    }

    protected void updateView() {
        if (mPreferenceFragment == null || isFinishing()) {
            // view not yet ready, update will happen on first layout event
            // or alternately we're done and don't need to do anything
            return;
        }

        int prevNumDevices = mPreferenceFragment.getPreferenceScreen().getPreferenceCount();

        mPreferenceFragment.updateList(mBluetoothDevices, mCurrentTargetAddress,
                mCurrentTargetStatus, mCancelledAddress);

        if (mNoInputMode) {
            if (DEBUG) Log.d(TAG, "stopping auto-exit timer");
            mAutoExitHandler.removeCallbacks(mAutoExitRunnable);
            if (mBluetoothDevices.size() == 1 && prevNumDevices == 0) {
                // first device added, start counter for autopair
                mMsgHandler.sendEmptyMessageDelayed(MSG_START_AUTOPAIR_COUNTDOWN,
                        TIME_TO_START_AUTOPAIR_COUNT);
            } else {

                // Start timer count down for exiting activity.
                if (DEBUG) Log.d(TAG, "starting auto-exit timer");
                mAutoExitHandler.postDelayed(mAutoExitRunnable, EXIT_TIMEOUT_MILLIS);

                if (mBluetoothDevices.size() > 1) {
                    // More than one device found, cancel auto pair
                    cancelPairingCountdown();
                }
           }
        }

        TransitionManager.beginDelayedTransition((ViewGroup) findViewById(R.id.content_frame));

        rearrangeViews();
    }

    private void rearrangeViews() {
        final boolean empty = mBluetoothDevices.isEmpty();

        final View contentView = findViewById(R.id.content_fragment);
        final ViewGroup.LayoutParams contentLayoutParams = contentView.getLayoutParams();
        contentLayoutParams.width = empty ? ViewGroup.LayoutParams.MATCH_PARENT :
                getResources().getDimensionPixelSize(R.dimen.lb_content_section_width);
        contentView.setLayoutParams(contentLayoutParams);

        mContentFragment.setContentWidth(empty
                ? getResources().getDimensionPixelSize(R.dimen.progress_fragment_content_width)
                : getResources().getDimensionPixelSize(R.dimen.bt_progress_width_narrow));
    }

    private void setPairingText(CharSequence text) {
        if (mContentFragment != null) {
            mContentFragment.setExtraText(text);
        }
    }

    private void cancelPairingCountdown() {
        // Cancel countdown
        mMsgHandler.removeMessages(MSG_AUTOPAIR_TICK);
        mMsgHandler.removeMessages(MSG_START_AUTOPAIR_COUNTDOWN);
        setPairingText(null);
    }

    private void setTimeout(int timeout) {
        cancelTimeout();
        mMsgHandler.sendEmptyMessageDelayed(MSG_OP_TIMEOUT, timeout);
    }

    private void cancelTimeout() {
        mMsgHandler.removeMessages(MSG_OP_TIMEOUT);
    }

    protected void startAutoPairing() {
        if (mBluetoothDevices.size() > 0) {
            onActionClicked(mBluetoothDevices.get(0).getAddress());
        }
    }

    private void btDeviceClicked(String clickedAddress) {
        if (mBluetoothPairer != null && !mBluetoothPairer.isInProgress()) {
            if (mBluetoothPairer.getStatus() == BluetoothDevicePairer.STATUS_WAITING_TO_PAIR &&
                    mBluetoothPairer.getTargetDevice() != null) {
                cancelBtPairing();
            } else {
                if (DEBUG) {
                    Log.d(TAG, "Looking for " + clickedAddress +
                            " in available devices to start pairing");
                }
                for (BluetoothDevice target : mBluetoothDevices) {
                    if (target.getAddress().equalsIgnoreCase(clickedAddress)) {
                        if (DEBUG) {
                            Log.d(TAG, "Found it!");
                        }
                        mCancelledAddress = ADDRESS_NONE;
                        setPairingBluetooth(true);
                        mBluetoothPairer.startPairing(target);
                        break;
                    }
                }
            }
        }
    }

    private void cancelBtPairing() {
        // cancel current request to pair
        if (mBluetoothPairer != null) {
            if (mBluetoothPairer.getTargetDevice() != null) {
                mCancelledAddress = mBluetoothPairer.getTargetDevice().getAddress();
            } else {
                mCancelledAddress = ADDRESS_NONE;
            }
            mBluetoothPairer.cancelPairing();
        }
        mPairingSuccess = false;
        setPairingBluetooth(false);
        mMsgHandler.sendEmptyMessageDelayed(MSG_REMOVE_CANCELED,
                CANCEL_MESSAGE_TIMEOUT);
    }

    private void setPairingBluetooth(boolean pairing) {
        if (mPairingBluetooth != pairing) {
            mPairingBluetooth = pairing;
        }
    }

    private void startBluetoothPairer() {
        stopBluetoothPairer();
        mBluetoothPairer = new BluetoothDevicePairer(this, this);
        mBluetoothPairer.start();

        mBluetoothPairer.disableAutoPairing();

        mPairingSuccess = false;
        statusChanged();
    }

    private void stopBluetoothPairer() {
        if (mBluetoothPairer != null) {
            mBluetoothPairer.setListener(null);
            mBluetoothPairer.dispose();
            mBluetoothPairer = null;
        }
    }

    private String getMessageForStatus(int status) {
        final int msgId;
        String msg;

        switch (status) {
            case BluetoothDevicePairer.STATUS_WAITING_TO_PAIR:
            case BluetoothDevicePairer.STATUS_PAIRING:
                msgId = R.string.accessory_state_pairing;
                break;
            case BluetoothDevicePairer.STATUS_CONNECTING:
                msgId = R.string.accessory_state_connecting;
                break;
            case BluetoothDevicePairer.STATUS_ERROR:
                msgId = R.string.accessory_state_error;
                break;
            default:
                return "";
        }

        msg = getString(msgId);

        return msg;
    }

    @Override
    public void statusChanged() {
        if (mBluetoothPairer == null) return;

        int numDevices = mBluetoothPairer.getAvailableDevices().size();
        int status = mBluetoothPairer.getStatus();
        int oldStatus = mPreviousStatus;
        mPreviousStatus = status;

        String address = mBluetoothPairer.getTargetDevice() == null ? ADDRESS_NONE :
                mBluetoothPairer.getTargetDevice().getAddress();

        if (DEBUG) {
            String state = "?";
            switch (status) {
                case BluetoothDevicePairer.STATUS_NONE:
                    state = "BluetoothDevicePairer.STATUS_NONE";
                    break;
                case BluetoothDevicePairer.STATUS_SCANNING:
                    state = "BluetoothDevicePairer.STATUS_SCANNING";
                    break;
                case BluetoothDevicePairer.STATUS_WAITING_TO_PAIR:
                    state = "BluetoothDevicePairer.STATUS_WAITING_TO_PAIR";
                    break;
                case BluetoothDevicePairer.STATUS_PAIRING:
                    state = "BluetoothDevicePairer.STATUS_PAIRING";
                    break;
                case BluetoothDevicePairer.STATUS_CONNECTING:
                    state = "BluetoothDevicePairer.STATUS_CONNECTING";
                    break;
                case BluetoothDevicePairer.STATUS_ERROR:
                    state = "BluetoothDevicePairer.STATUS_ERROR";
                    break;
            }
            long time = mBluetoothPairer.getNextStageTime() - SystemClock.elapsedRealtime();
            Log.d(TAG, "Update received, number of devices:" + numDevices + " state: " +
                    state + " target device: " + address + " time to next event: " + time);
        }

        mBluetoothDevices.clear();
        for (BluetoothDevice device : mBluetoothPairer.getAvailableDevices()) {
            mBluetoothDevices.add(device);
        }

        cancelTimeout();

        switch (status) {
            case BluetoothDevicePairer.STATUS_NONE:
                // if we just connected to something or just tried to connect
                // to something, restart scanning just in case the user wants
                // to pair another device.
                if (oldStatus == BluetoothDevicePairer.STATUS_CONNECTING) {
                    if (mPairingSuccess) {
                        // Pairing complete
                        mCurrentTargetStatus = getString(R.string.accessory_state_paired);
                        mMsgHandler.sendEmptyMessage(MSG_UPDATE_VIEW);
                        mMsgHandler.sendEmptyMessageDelayed(MSG_PAIRING_COMPLETE,
                                DONE_MESSAGE_TIMEOUT);
                        mDone = true;

                        // Done, return here and just wait for the message
                        // to close the activity
                        return;
                    }
                    if (DEBUG) {
                        Log.d(TAG, "Invalidating and restarting.");
                    }

                    mBluetoothPairer.invalidateDevice(mBluetoothPairer.getTargetDevice());
                    mBluetoothPairer.start();
                    mBluetoothPairer.cancelPairing();
                    setPairingBluetooth(false);

                    // if this looks like a successful connection run, reflect
                    // this in the UI, otherwise use the default message
                    if (!mPairingSuccess && BluetoothDevicePairer.hasValidInputDevice(this)) {
                        mPairingSuccess = true;
                    }
                }
                break;
            case BluetoothDevicePairer.STATUS_SCANNING:
                mPairingSuccess = false;
                break;
            case BluetoothDevicePairer.STATUS_WAITING_TO_PAIR:
                break;
            case BluetoothDevicePairer.STATUS_PAIRING:
                // reset the pairing success value since this is now a new
                // pairing run
                mPairingSuccess = true;
                setTimeout(PAIR_OPERATION_TIMEOUT);
                break;
            case BluetoothDevicePairer.STATUS_CONNECTING:
                setTimeout(CONNECT_OPERATION_TIMEOUT);
                break;
            case BluetoothDevicePairer.STATUS_ERROR:
                mPairingSuccess = false;
                setPairingBluetooth(false);
                if (mNoInputMode) {
                    clearDeviceList();
                }
                break;
        }

        mCurrentTargetAddress = address;
        mCurrentTargetStatus = getMessageForStatus(status);
        mMsgHandler.sendEmptyMessage(MSG_UPDATE_VIEW);
    }

    private void clearDeviceList() {
        mBluetoothDevices.clear();
        mBluetoothPairer.clearDeviceList();
    }

    private void handlePairingTimeout() {
        if (mPairingInBackground) {
            finish();
        } else {
            // Either Pairing or Connecting timeout out.
            // Display error message and post delayed message to the scanning process.
            mPairingSuccess = false;
            if (mBluetoothPairer != null) {
                mBluetoothPairer.cancelPairing();
            }
            mCurrentTargetStatus = getString(R.string.accessory_state_error);
            mMsgHandler.sendEmptyMessage(MSG_UPDATE_VIEW);
            mMsgHandler.sendEmptyMessageDelayed(MSG_RESTART, RESTART_DELAY);
        }
    }

    List<BluetoothDevice> getBluetoothDevices() {
        return mBluetoothDevices;
    }

    String getCurrentTargetAddress() {
        return mCurrentTargetAddress;
    }

    String getCurrentTargetStatus() {
        return mCurrentTargetStatus;
    }

    String getCancelledAddress() {
        return mCancelledAddress;
    }
}
