/*
* Copyright (C) 2014 Samsung System LSI
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

package com.android.bluetooth.map;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothMap;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetoothMap;
import android.bluetooth.SdpMnsRecord;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.android.bluetooth.R;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ProfileService.IProfileServiceBinder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class BluetoothMapService extends ProfileService {
    private static final String TAG = "BluetoothMapService";

    /**
     * To enable MAP DEBUG/VERBOSE logging - run below cmd in adb shell, and
     * restart com.android.bluetooth process. only enable DEBUG log:
     * "setprop log.tag.BluetoothMapService DEBUG"; enable both VERBOSE and
     * DEBUG log: "setprop log.tag.BluetoothMapService VERBOSE"
     */

    public static final boolean DEBUG = true; //FIXME set to false;

    public static final boolean VERBOSE = false;

    /**
     * Intent indicating timeout for user confirmation, which is sent to
     * BluetoothMapActivity
     */
    public static final String USER_CONFIRM_TIMEOUT_ACTION =
            "com.android.bluetooth.map.USER_CONFIRM_TIMEOUT";
    private static final int USER_CONFIRM_TIMEOUT_VALUE = 25000;

    /** Intent indicating that the email settings activity should be opened*/
    public static final String ACTION_SHOW_MAPS_SETTINGS =
            "android.btmap.intent.action.SHOW_MAPS_SETTINGS";

    public static final int MSG_SERVERSESSION_CLOSE = 5000;

    public static final int MSG_SESSION_ESTABLISHED = 5001;

    public static final int MSG_SESSION_DISCONNECTED = 5002;

    public static final int MSG_MAS_CONNECT = 5003; // Send at MAS connect, including the MAS_ID
    public static final int MSG_MAS_CONNECT_CANCEL = 5004; // Send at auth. declined

    public static final int MSG_ACQUIRE_WAKE_LOCK = 5005;

    public static final int MSG_RELEASE_WAKE_LOCK = 5006;

    public static final int MSG_MNS_SDP_SEARCH = 5007;

    public static final int MSG_OBSERVER_REGISTRATION = 5008;

    private static final String BLUETOOTH_PERM = android.Manifest.permission.BLUETOOTH;

    private static final String BLUETOOTH_ADMIN_PERM = android.Manifest.permission.BLUETOOTH_ADMIN;

    private static final int START_LISTENER = 1;

    private static final int USER_TIMEOUT = 2;

    private static final int DISCONNECT_MAP = 3;

    private static final int SHUTDOWN = 4;

    private static final int RELEASE_WAKE_LOCK_DELAY = 10000;

    private PowerManager.WakeLock mWakeLock = null;

    private static final int UPDATE_MAS_INSTANCES = 5;

    public static final int UPDATE_MAS_INSTANCES_ACCOUNT_ADDED = 0;
    public static final int UPDATE_MAS_INSTANCES_ACCOUNT_REMOVED = 1;
    public static final int UPDATE_MAS_INSTANCES_ACCOUNT_RENAMED = 2;
    public static final int UPDATE_MAS_INSTANCES_ACCOUNT_DISCONNECT = 3;

    private static final int MAS_ID_SMS_MMS = 0;

    private BluetoothAdapter mAdapter;

    private BluetoothMnsObexClient mBluetoothMnsObexClient = null;

    /* mMasInstances: A list of the active MasInstances with the key being the MasId */
    private SparseArray<BluetoothMapMasInstance> mMasInstances =
            new SparseArray<BluetoothMapMasInstance>(1);
    /* mMasInstanceMap: A list of the active MasInstances with the key being the account */
    private HashMap<BluetoothMapAccountItem, BluetoothMapMasInstance> mMasInstanceMap =
            new HashMap<BluetoothMapAccountItem, BluetoothMapMasInstance>(1);

    private static BluetoothDevice mRemoteDevice = null; // The remote connected device - protect access

    private ArrayList<BluetoothMapAccountItem> mEnabledAccounts = null;
    private static String sRemoteDeviceName = null;

    private int mState;
    private BluetoothMapAppObserver mAppObserver = null;
    private AlarmManager mAlarmManager = null;

    private boolean mIsWaitingAuthorization = false;
    private boolean mRemoveTimeoutMsg = false;
    private boolean mRegisteredMapReceiver = false;
    private int mPermission = BluetoothDevice.ACCESS_UNKNOWN;
    private boolean mAccountChanged = false;
    private boolean mSdpSearchInitiated = false;
    SdpMnsRecord mMnsRecord = null;
    private MapServiceMessageHandler mSessionStatusHandler;
    private boolean mStartError = true;

    private boolean mSmsCapable = true;

    private static final ParcelUuid[] MAP_UUIDS = {
        BluetoothUuid.MAP,
        BluetoothUuid.MNS,
    };

    public BluetoothMapService() {
        mState = BluetoothMap.STATE_DISCONNECTED;
    }


    private synchronized void closeService() {
        if (DEBUG) Log.d(TAG, "MAP Service closeService in");

        if (mBluetoothMnsObexClient != null) {
            mBluetoothMnsObexClient.shutdown();
            mBluetoothMnsObexClient = null;
        }
        if (mMasInstances.size() > 0) {
            for (int i=0, c=mMasInstances.size(); i < c; i++) {
                mMasInstances.valueAt(i).shutdown();
            }
            mMasInstances.clear();
        }

        mIsWaitingAuthorization = false;
        mPermission = BluetoothDevice.ACCESS_UNKNOWN;
        setState(BluetoothMap.STATE_DISCONNECTED);

        if (mWakeLock != null) {
            mWakeLock.release();
            if (VERBOSE) Log.v(TAG, "CloseService(): Release Wake Lock");
            mWakeLock = null;
        }
        /* Only one SHUTDOWN message expected to closeService.
         * Hence, quit looper and Handler on first SHUTDOWN message*/
        if (mSessionStatusHandler != null) {
            //Perform cleanup in Handler running on worker Thread
            mSessionStatusHandler.removeCallbacksAndMessages(null);
            Looper looper = mSessionStatusHandler.getLooper();
            if (looper != null) {
                looper.quit();
                if(VERBOSE) Log.i(TAG, "Quit looper");
            }
            mSessionStatusHandler = null;
            if(VERBOSE) Log.i(TAG, "Remove Handler");
        }
        mRemoteDevice = null;

        if (VERBOSE) Log.v(TAG, "MAP Service closeService out");
    }

    /**
     * Starts the RFComm listener threads for each MAS
     * @throws IOException
     */
    private final void startRfcommSocketListeners(int masId) {
        if(masId == -1) {
            for(int i=0, c=mMasInstances.size(); i < c; i++) {
                mMasInstances.valueAt(i).startRfcommSocketListener();
            }
        } else {
            BluetoothMapMasInstance masInst = mMasInstances.get(masId); // returns null for -1
            if(masInst != null) {
                masInst.startRfcommSocketListener();
            } else {
                Log.w(TAG, "startRfcommSocketListeners(): Invalid MasId: " + masId);
            }
        }
    }

    /**
     * Start a MAS instance for SMS/MMS and each e-mail account.
     */
    private final void startObexServerSessions() {
        if (DEBUG) Log.d(TAG, "Map Service START ObexServerSessions()");

        // acquire the wakeLock before start Obex transaction thread
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "StartingObexMapTransaction");
            mWakeLock.setReferenceCounted(false);
            mWakeLock.acquire();
            if (VERBOSE) Log.v(TAG, "startObexSessions(): Acquire Wake Lock");
        }

        if(mBluetoothMnsObexClient == null) {
            mBluetoothMnsObexClient =
                    new BluetoothMnsObexClient(mRemoteDevice, mMnsRecord, mSessionStatusHandler);
        }

        boolean connected = false;
        for(int i=0, c=mMasInstances.size(); i < c; i++) {
            try {
                if(mMasInstances.valueAt(i)
                        .startObexServerSession(mBluetoothMnsObexClient) == true) {
                    connected = true;
                }
            } catch (IOException e) {
                Log.w(TAG,"IOException occured while starting an obexServerSession restarting" +
                        " the listener",e);
                mMasInstances.valueAt(i).restartObexServerSession();
            } catch (RemoteException e) {
                Log.w(TAG,"RemoteException occured while starting an obexServerSession restarting" +
                        " the listener",e);
                mMasInstances.valueAt(i).restartObexServerSession();
            }
        }
        if(connected) {
            setState(BluetoothMap.STATE_CONNECTED);
        }

        mSessionStatusHandler.removeMessages(MSG_RELEASE_WAKE_LOCK);
        mSessionStatusHandler.sendMessageDelayed(mSessionStatusHandler
                .obtainMessage(MSG_RELEASE_WAKE_LOCK), RELEASE_WAKE_LOCK_DELAY);

        if (VERBOSE) Log.v(TAG, "startObexServerSessions() success!");
    }

    public Handler getHandler() {
        return mSessionStatusHandler;
    }

    /**
     * Restart a MAS instances.
     * @param masId use -1 to stop all instances
     */
    private void stopObexServerSessions(int masId) {
        if (DEBUG) Log.d(TAG, "MAP Service STOP ObexServerSessions()");

        boolean lastMasInst = true;

        if(masId != -1) {
            for(int i=0, c=mMasInstances.size(); i < c; i++) {
                BluetoothMapMasInstance masInst = mMasInstances.valueAt(i);
                if(masInst.getMasId() != masId && masInst.isStarted()) {
                    lastMasInst = false;
                }
            }
        } // Else just close down it all

        /* Shutdown the MNS client - currently must happen before MAS close */
        if(mBluetoothMnsObexClient != null && lastMasInst) {
            mBluetoothMnsObexClient.shutdown();
            mBluetoothMnsObexClient = null;
        }

        BluetoothMapMasInstance masInst = mMasInstances.get(masId); // returns null for -1
        if(masInst != null) {
            masInst.restartObexServerSession();
        } else  if(masId == -1) {
            for(int i=0, c=mMasInstances.size(); i < c; i++) {
                mMasInstances.valueAt(i).restartObexServerSession();
            }
        }

        if(lastMasInst) {
            setState(BluetoothMap.STATE_DISCONNECTED);
            mPermission = BluetoothDevice.ACCESS_UNKNOWN;
            mRemoteDevice = null;
            if(mAccountChanged) {
                updateMasInstances(UPDATE_MAS_INSTANCES_ACCOUNT_DISCONNECT);
            }
        }

        // Release the wake lock at disconnect
        if (mWakeLock != null && lastMasInst) {
            mSessionStatusHandler.removeMessages(MSG_ACQUIRE_WAKE_LOCK);
            mSessionStatusHandler.removeMessages(MSG_RELEASE_WAKE_LOCK);
            mWakeLock.release();
            if (VERBOSE) Log.v(TAG, "stopObexServerSessions(): Release Wake Lock");
        }
    }

    private final class MapServiceMessageHandler extends Handler {
        private MapServiceMessageHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            if (VERBOSE) Log.v(TAG, "Handler(): got msg=" + msg.what);

            switch (msg.what) {
                case UPDATE_MAS_INSTANCES:
                    updateMasInstancesHandler();
                    break;
                case START_LISTENER:
                    if (mAdapter.isEnabled()) {
                        startRfcommSocketListeners(msg.arg1);
                    }
                    break;
                case MSG_MAS_CONNECT:
                    onConnectHandler(msg.arg1);
                    break;
                case MSG_MAS_CONNECT_CANCEL:
                    /* TODO: We need to handle this by accepting the connection and reject at
                     * OBEX level, by using ObexRejectServer - add timeout to handle clients not
                     * closing the transport channel.
                     */
                    stopObexServerSessions(-1);
                    break;
                case USER_TIMEOUT:
                    if (mIsWaitingAuthorization) {
                        Intent intent = new Intent(BluetoothDevice.ACTION_CONNECTION_ACCESS_CANCEL);
                        intent.setPackage(getString(R.string.pairing_ui_package));
                        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mRemoteDevice);
                        intent.putExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                                BluetoothDevice.REQUEST_TYPE_MESSAGE_ACCESS);
                        sendBroadcast(intent);
                        cancelUserTimeoutAlarm();
                        mIsWaitingAuthorization = false;
                        stopObexServerSessions(-1);
                    }
                    break;
                case MSG_SERVERSESSION_CLOSE:
                    stopObexServerSessions(msg.arg1);
                    break;
                case MSG_SESSION_ESTABLISHED:
                    break;
                case MSG_SESSION_DISCONNECTED:
                    // handled elsewhere
                    break;
                case DISCONNECT_MAP:
                    disconnectMap((BluetoothDevice)msg.obj);
                    break;
                case SHUTDOWN:
                    /* Ensure to call close from this handler to avoid starting new stuff
                       because of pending messages */
                    closeService();
                    break;
                case MSG_ACQUIRE_WAKE_LOCK:
                    if (VERBOSE) Log.v(TAG, "Acquire Wake Lock request message");
                    if (mWakeLock == null) {
                        PowerManager pm = (PowerManager)getSystemService(
                                          Context.POWER_SERVICE);
                        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                    "StartingObexMapTransaction");
                        mWakeLock.setReferenceCounted(false);
                    }
                    if(!mWakeLock.isHeld()) {
                        mWakeLock.acquire();
                        if (DEBUG) Log.d(TAG, "  Acquired Wake Lock by message");
                    }
                    mSessionStatusHandler.removeMessages(MSG_RELEASE_WAKE_LOCK);
                    mSessionStatusHandler.sendMessageDelayed(mSessionStatusHandler
                      .obtainMessage(MSG_RELEASE_WAKE_LOCK), RELEASE_WAKE_LOCK_DELAY);
                    break;
                case MSG_RELEASE_WAKE_LOCK:
                    if (VERBOSE) Log.v(TAG, "Release Wake Lock request message");
                    if (mWakeLock != null) {
                        mWakeLock.release();
                        if (DEBUG) Log.d(TAG, "  Released Wake Lock by message");
                    }
                    break;
                case MSG_MNS_SDP_SEARCH:
                    if (mRemoteDevice != null) {
                        if (DEBUG) Log.d(TAG,"MNS SDP Initiate Search ..");
                        mRemoteDevice.sdpSearch(BluetoothMnsObexClient.BLUETOOTH_UUID_OBEX_MNS);
                    } else {
                        Log.w(TAG, "remoteDevice info not available");
                    }
                    break;
                case MSG_OBSERVER_REGISTRATION:
                    if (DEBUG) Log.d(TAG,"ContentObserver Registration MASID: " + msg.arg1
                        + " Enable: " + msg.arg2);
                    BluetoothMapMasInstance masInst = mMasInstances.get(msg.arg1);
                    if (masInst != null && masInst.mObserver != null) {
                        try {
                            if (msg.arg2 == BluetoothMapAppParams.NOTIFICATION_STATUS_YES) {
                                masInst.mObserver.registerObserver();
                            } else {
                                masInst.mObserver.unregisterObserver();
                            }
                        } catch (RemoteException e) {
                            Log.e(TAG,"ContentObserverRegistarion Failed: "+ e);
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private void onConnectHandler(int masId) {
        if (mIsWaitingAuthorization == true || mRemoteDevice == null
                || mSdpSearchInitiated == true) {
            return;
        }
        BluetoothMapMasInstance masInst = mMasInstances.get(masId);
        // Need to ensure we are still allowed.
        if (DEBUG) Log.d(TAG, "mPermission = " + mPermission);
        if (mPermission == BluetoothDevice.ACCESS_ALLOWED) {
            try {
                if (VERBOSE) Log.v(TAG, "incoming connection accepted from: "
                        + sRemoteDeviceName + " automatically as trusted device");
                if (mBluetoothMnsObexClient != null && masInst != null) {
                    masInst.startObexServerSession(mBluetoothMnsObexClient);
                } else {
                    startObexServerSessions();
                }
            } catch (IOException ex) {
                Log.e(TAG, "catch IOException starting obex server session", ex);
            } catch (RemoteException ex) {
                Log.e(TAG, "catch RemoteException starting obex server session", ex);
            }
        }
    }

    public int getState() {
        return mState;
    }

    protected boolean isMapStarted() {
        return !mStartError;
    }
    public static BluetoothDevice getRemoteDevice() {
        return mRemoteDevice;
    }
    private void setState(int state) {
        setState(state, BluetoothMap.RESULT_SUCCESS);
    }

    private synchronized void setState(int state, int result) {
        if (state != mState) {
            if (DEBUG) Log.d(TAG, "Map state " + mState + " -> " + state + ", result = "
                    + result);
            int prevState = mState;
            mState = state;
            Intent intent = new Intent(BluetoothMap.ACTION_CONNECTION_STATE_CHANGED);
            intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
            intent.putExtra(BluetoothProfile.EXTRA_STATE, mState);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mRemoteDevice);
            sendBroadcast(intent, BLUETOOTH_PERM);
        }
    }

    public static String getRemoteDeviceName() {
        return sRemoteDeviceName;
    }

    public boolean disconnect(BluetoothDevice device) {
        mSessionStatusHandler.sendMessage(mSessionStatusHandler
                .obtainMessage(DISCONNECT_MAP, 0, 0, device));
        return true;
    }

    public boolean disconnectMap(BluetoothDevice device) {
        boolean result = false;
        if (DEBUG) Log.d(TAG, "disconnectMap");
        if (getRemoteDevice()!= null && getRemoteDevice().equals(device)) {
            switch (mState) {
                case BluetoothMap.STATE_CONNECTED:
                    /* Disconnect all connections and restart all MAS instances */
                    stopObexServerSessions(-1);
                    result = true;
                    break;
                default:
                    break;
                }
        }
        return result;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
        synchronized(this) {
            if (mState == BluetoothMap.STATE_CONNECTED && mRemoteDevice != null) {
                devices.add(mRemoteDevice);
            }
        }
        return devices;
    }

    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        List<BluetoothDevice> deviceList = new ArrayList<BluetoothDevice>();
        Set<BluetoothDevice> bondedDevices = mAdapter.getBondedDevices();
        int connectionState;
        synchronized (this) {
            for (BluetoothDevice device : bondedDevices) {
                ParcelUuid[] featureUuids = device.getUuids();
                if (!BluetoothUuid.containsAnyUuid(featureUuids, MAP_UUIDS)) {
                    continue;
                }
                connectionState = getConnectionState(device);
                for(int i = 0; i < states.length; i++) {
                    if (connectionState == states[i]) {
                        deviceList.add(device);
                    }
                }
            }
        }
        return deviceList;
    }

    public int getConnectionState(BluetoothDevice device) {
        synchronized(this) {
            if (getState() == BluetoothMap.STATE_CONNECTED && getRemoteDevice().equals(device)) {
                return BluetoothProfile.STATE_CONNECTED;
            } else {
                return BluetoothProfile.STATE_DISCONNECTED;
            }
        }
    }

    public boolean setPriority(BluetoothDevice device, int priority) {
        Settings.Global.putInt(getContentResolver(),
            Settings.Global.getBluetoothMapPriorityKey(device.getAddress()),
            priority);
        if (VERBOSE) Log.v(TAG, "Saved priority " + device + " = " + priority);
        return true;
    }

    public int getPriority(BluetoothDevice device) {
        int priority = Settings.Global.getInt(getContentResolver(),
            Settings.Global.getBluetoothMapPriorityKey(device.getAddress()),
            BluetoothProfile.PRIORITY_UNDEFINED);
        return priority;
    }

    @Override
    protected IProfileServiceBinder initBinder() {
        return new BluetoothMapBinder(this);
    }

    @Override
    protected boolean start() {
        if (DEBUG) Log.d(TAG, "start()");
        if (isMapStarted()) {
            Log.w(TAG, "start received for already started, ignoring");
            return false;
        }
        HandlerThread thread = new HandlerThread("BluetoothMapHandler");
        thread.start();
        Looper looper = thread.getLooper();
        mSessionStatusHandler = new MapServiceMessageHandler(looper);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_SDP_RECORD);
        filter.addAction(ACTION_SHOW_MAPS_SETTINGS);
        filter.addAction(USER_CONFIRM_TIMEOUT_ACTION);

        // We need two filters, since Type only applies to the ACTION_MESSAGE_SENT
        IntentFilter filterMessageSent = new IntentFilter();
        filterMessageSent.addAction(BluetoothMapContentObserver.ACTION_MESSAGE_SENT);
        try{
            filterMessageSent.addDataType("message/*");
        } catch (MalformedMimeTypeException e) {
            Log.e(TAG, "Wrong mime type!!!", e);
        }
        if (!mRegisteredMapReceiver) {
            try {
                registerReceiver(mMapReceiver, filter);
                registerReceiver(mMapReceiver, filterMessageSent);
                mRegisteredMapReceiver = true;
            } catch (Exception e) {
                Log.e(TAG,"Unable to register map receiver",e);
            }
        }
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mAppObserver = new BluetoothMapAppObserver(this, this);

        mEnabledAccounts = mAppObserver.getEnabledAccountItems();

        mSmsCapable = getResources().getBoolean(
                com.android.internal.R.bool.config_sms_capable);
        // Uses mEnabledAccounts, hence getEnabledAccountItems() must be called before this.
        createMasInstances();

        // start RFCOMM listener
        sendStartListenerMessage(-1);
        mStartError = false;
        return !mStartError;
    }

    /**
     * Call this to trigger an update of the MAS instance list.
     * No changes will be applied unless in disconnected state
     */
    public void updateMasInstances(int action) {
            mSessionStatusHandler.obtainMessage (UPDATE_MAS_INSTANCES,
                    action, 0).sendToTarget();
    }

    /**
     * Update the active MAS Instances according the difference between mEnabledDevices
     * and the current list of accounts.
     * Will only make changes if state is disconnected.
     *
     * How it works:
     * 1) Build lists of account changes from last update of mEnabledAccounts.
     *      newAccounts - accounts that have been enabled since mEnabledAccounts
     *                    was last updated.
     *      removedAccounts - Accounts that is on mEnabledAccounts, but no longer
     *                        enabled.
     *      enabledAccounts - A new list of all enabled accounts.
     * 2) Stop and remove all MasInstances on the remove list
     * 3) Add and start MAS instances for accounts on the new list.
     * Called at:
     *  - Each change in accounts
     *  - Each disconnect - before MasInstances restart.
     *
     * @return true is any changes are made, false otherwise.
     */
    private boolean updateMasInstancesHandler(){
        if (DEBUG) Log.d(TAG,"updateMasInstancesHandler() state = " + getState());
        boolean changed = false;

        if(getState() == BluetoothMap.STATE_DISCONNECTED) {
            ArrayList<BluetoothMapAccountItem> newAccountList =
                    mAppObserver.getEnabledAccountItems();
            ArrayList<BluetoothMapAccountItem> newAccounts = null;
            ArrayList<BluetoothMapAccountItem> removedAccounts = null;
            newAccounts = new ArrayList<BluetoothMapAccountItem>();
            removedAccounts = mEnabledAccounts; // reuse the current enabled list, to track removed
                                                // accounts
            for(BluetoothMapAccountItem account: newAccountList) {
                if(!removedAccounts.remove(account)) {
                    newAccounts.add(account);
                }
            }

            if(removedAccounts != null) {
                /* Remove all disabled/removed accounts */
                for(BluetoothMapAccountItem account : removedAccounts) {
                    BluetoothMapMasInstance masInst = mMasInstanceMap.remove(account);
                    if (VERBOSE) Log.v(TAG,"  Removing account: " + account + " masInst = " + masInst);
                    if(masInst != null) {
                        masInst.shutdown();
                        mMasInstances.remove(masInst.getMasId());
                        changed = true;
                    }
                }
            }

            if(newAccounts != null) {
                /* Add any newly created accounts */
                for(BluetoothMapAccountItem account : newAccounts) {
                    if (VERBOSE) Log.v(TAG,"  Adding account: " + account);
                    int masId = getNextMasId();
                    BluetoothMapMasInstance newInst =
                            new BluetoothMapMasInstance(this,
                                    this,
                                    account,
                                    masId,
                                    false);
                    mMasInstances.append(masId, newInst);
                    mMasInstanceMap.put(account, newInst);
                    changed = true;
                    /* Start the new instance */
                    if (mAdapter.isEnabled()) {
                        newInst.startRfcommSocketListener();
                    }
                }
            }
            mEnabledAccounts = newAccountList;
            if (VERBOSE) {
                Log.v(TAG,"  Enabled accounts:");
                for(BluetoothMapAccountItem account : mEnabledAccounts) {
                    Log.v(TAG, "   " + account);
                }
                Log.v(TAG,"  Active MAS instances:");
                for(int i=0, c=mMasInstances.size(); i < c; i++) {
                    BluetoothMapMasInstance masInst = mMasInstances.valueAt(i);
                    Log.v(TAG, "   " + masInst);
                }
            }
            mAccountChanged = false;
        } else {
            mAccountChanged = true;
        }
        return changed;
    }

    /**
     * Will return the next MasId to use.
     * Will ensure the key returned is greater than the largest key in use.
     * Unless the key 255 is in use, in which case the first free masId
     * will be returned.
     * @return
     */
    private int getNextMasId() {
        /* Find the largest masId in use */
        int largestMasId = 0;
        for(int i=0, c=mMasInstances.size(); i < c; i++) {
            int masId = mMasInstances.keyAt(i);
            if(masId > largestMasId) {
                largestMasId = masId;
            }
        }
        if(largestMasId < 0xff) {
            return largestMasId + 1;
        }
        /* If 0xff is already in use, wrap and choose the first free
         * MasId. */
        for(int i = 1; i <= 0xff; i++) {
            if(mMasInstances.get(i) == null) {
                return i;
            }
        }
        return 0xff; // This will never happen, as we only allow 10 e-mail accounts to be enabled
    }

    private void createMasInstances() {
        int masId = mSmsCapable ? MAS_ID_SMS_MMS : -1;

        if (mSmsCapable) {
            // Add the SMS/MMS instance
            BluetoothMapMasInstance smsMmsInst =
                    new BluetoothMapMasInstance(this,
                            this,
                            null,
                            masId,
                            true);
            mMasInstances.append(masId, smsMmsInst);
            mMasInstanceMap.put(null, smsMmsInst);
        }

        // get list of accounts already set to be visible through MAP
        for(BluetoothMapAccountItem account : mEnabledAccounts) {
            masId++;  // SMS/MMS is masId=0, increment before adding next
            BluetoothMapMasInstance newInst =
                    new BluetoothMapMasInstance(this,
                            this,
                            account,
                            masId,
                            false);
            mMasInstances.append(masId, newInst);
            mMasInstanceMap.put(account, newInst);
        }
    }

    @Override
    protected boolean stop() {
        if (DEBUG) Log.d(TAG, "stop()");
        if (mRegisteredMapReceiver) {
            try {
                mRegisteredMapReceiver = false;
                unregisterReceiver(mMapReceiver);
                mAppObserver.shutdown();
            } catch (Exception e) {
                Log.e(TAG,"Unable to unregister map receiver",e);
            }
        }
        //Stop MapProfile if already started.
        //TODO: Check if the profile state can be retreived from ProfileService or AdapterService.
        if (!isMapStarted()) {
            if (DEBUG) Log.d(TAG, "Service Not Available to STOP, ignoring");
            return true;
        } else {
            if (VERBOSE) Log.d(TAG, "Service Stoping()");
        }
        if (mSessionStatusHandler != null) {
            sendShutdownMessage();
        }
        mStartError = true;
        setState(BluetoothMap.STATE_DISCONNECTED, BluetoothMap.RESULT_CANCELED);
        return true;
    }

    public boolean cleanup()  {
        if (DEBUG) Log.d(TAG, "cleanup()");
        setState(BluetoothMap.STATE_DISCONNECTED, BluetoothMap.RESULT_CANCELED);
        //Cleanup already handled in Stop().
        //Move this  extra check to Handler.
        sendShutdownMessage();
        return true;
    }

    /**
     * Called from each MAS instance when a connection is received.
     * @param remoteDevice The device connecting
     * @param masInst a reference to the calling MAS instance.
     * @return
     */
    public boolean onConnect(BluetoothDevice remoteDevice, BluetoothMapMasInstance masInst) {
        boolean sendIntent = false;
        boolean cancelConnection = false;

        // As this can be called from each MasInstance, we need to lock access to member variables
        synchronized(this) {
            if (mRemoteDevice == null) {
                mRemoteDevice = remoteDevice;
                sRemoteDeviceName = mRemoteDevice.getName();
                // In case getRemoteName failed and return null
                if (TextUtils.isEmpty(sRemoteDeviceName)) {
                    sRemoteDeviceName = getString(R.string.defaultname);
                }

                mPermission = mRemoteDevice.getMessageAccessPermission();
                if (mPermission == BluetoothDevice.ACCESS_UNKNOWN) {
                    sendIntent = true;
                    mIsWaitingAuthorization = true;
                    setUserTimeoutAlarm();
                } else if (mPermission == BluetoothDevice.ACCESS_REJECTED) {
                    cancelConnection = true;
                } else if(mPermission == BluetoothDevice.ACCESS_ALLOWED) {
                    mRemoteDevice.sdpSearch(BluetoothMnsObexClient.BLUETOOTH_UUID_OBEX_MNS);
                    mSdpSearchInitiated = true;
                }
            } else if (!mRemoteDevice.equals(remoteDevice)) {
                Log.w(TAG, "Unexpected connection from a second Remote Device received. name: " +
                            ((remoteDevice==null)?"unknown":remoteDevice.getName()));
                return false; /* The connecting device is different from what is already
                                 connected, reject the connection. */
            } // Else second connection to same device, just continue
        }

        if (sendIntent) {
            // This will trigger Settings app's dialog.
            Intent intent = new Intent(BluetoothDevice.ACTION_CONNECTION_ACCESS_REQUEST);
            intent.setPackage(getString(R.string.pairing_ui_package));
            intent.putExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                            BluetoothDevice.REQUEST_TYPE_MESSAGE_ACCESS);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mRemoteDevice);
            sendOrderedBroadcast(intent, BLUETOOTH_ADMIN_PERM);

            if (VERBOSE) Log.v(TAG, "waiting for authorization for connection from: "
                    + sRemoteDeviceName);
            //Queue USER_TIMEOUT to disconnect MAP OBEX session. If user doesn't
            //accept or reject authorization request
        } else if (cancelConnection) {
            sendConnectCancelMessage();
        } else if (mPermission == BluetoothDevice.ACCESS_ALLOWED) {
            /* Signal to the service that we have a incoming connection. */
            sendConnectMessage(masInst.getMasId());
        }
        return true;
    };


    private void setUserTimeoutAlarm(){
        if (DEBUG) Log.d(TAG,"SetUserTimeOutAlarm()");
        if(mAlarmManager == null){
            mAlarmManager =(AlarmManager) this.getSystemService (Context.ALARM_SERVICE);
        }
        mRemoveTimeoutMsg = true;
        Intent timeoutIntent =
                new Intent(USER_CONFIRM_TIMEOUT_ACTION);
        PendingIntent pIntent = PendingIntent.getBroadcast(this, 0, timeoutIntent, 0);
        mAlarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() +
                USER_CONFIRM_TIMEOUT_VALUE,pIntent);
    }

    private void cancelUserTimeoutAlarm(){
        if (DEBUG) Log.d(TAG,"cancelUserTimeOutAlarm()");
        Intent timeoutIntent = new Intent(USER_CONFIRM_TIMEOUT_ACTION);
        PendingIntent pIntent = PendingIntent.getBroadcast(this, 0, timeoutIntent, 0);
        pIntent.cancel();

        AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pIntent);
        mRemoveTimeoutMsg = false;
    }

    /**
     * Start the incoming connection listeners for a MAS ID
     * @param masId the MasID to start. Use -1 to start all listeners.
     */
    public void sendStartListenerMessage(int masId) {
        if (mSessionStatusHandler != null && ! mSessionStatusHandler.hasMessages(START_LISTENER)) {
            Message msg = mSessionStatusHandler.obtainMessage(START_LISTENER, masId, 0);
            /* We add a small delay here to ensure the call returns true before this message is
             * handled. It seems wrong to add a delay, but the alternative is to build a lock
             * system to handle synchronization, which isn't nice either... */
            mSessionStatusHandler.sendMessageDelayed(msg, 20);
        } else if (mSessionStatusHandler != null) {
            if(DEBUG)
                Log.w(TAG, "mSessionStatusHandler START_LISTENER message already in Queue");
        }
    }

    private void sendConnectMessage(int masId) {
        if(mSessionStatusHandler != null) {
            Message msg = mSessionStatusHandler.obtainMessage(MSG_MAS_CONNECT, masId, 0);
            /* We add a small delay here to ensure onConnect returns true before this message is
             * handled. It seems wrong, but the alternative is to store a reference to the
             * connection in this message, which isn't nice either... */
            mSessionStatusHandler.sendMessageDelayed(msg, 20);
        } // Can only be null during shutdown
    }
    private void sendConnectTimeoutMessage() {
        if (DEBUG) Log.d(TAG, "sendConnectTimeoutMessage()");
        if(mSessionStatusHandler != null) {
            Message msg = mSessionStatusHandler.obtainMessage(USER_TIMEOUT);
            msg.sendToTarget();
        } // Can only be null during shutdown
    }
    private void sendConnectCancelMessage() {
        if(mSessionStatusHandler != null) {
            Message msg = mSessionStatusHandler.obtainMessage(MSG_MAS_CONNECT_CANCEL);
            msg.sendToTarget();
        } // Can only be null during shutdown
    }

    private void sendShutdownMessage() {
        /* Any pending messages are no longer valid.
        To speed up things, simply delete them. */
        if (mRemoveTimeoutMsg) {
            Intent timeoutIntent =
                    new Intent(USER_CONFIRM_TIMEOUT_ACTION);
            sendBroadcast(timeoutIntent, BLUETOOTH_PERM);
            mIsWaitingAuthorization = false;
            cancelUserTimeoutAlarm();
        }
        if (mSessionStatusHandler != null && !mSessionStatusHandler.hasMessages(SHUTDOWN)) {
            mSessionStatusHandler.removeCallbacksAndMessages(null);
            // Request release of all resources
            Message msg = mSessionStatusHandler.obtainMessage(SHUTDOWN);
            if( mSessionStatusHandler.sendMessage(msg) == false) {
                /* most likely caused by shutdown being called from multiple sources - e.g.BT off
                 * signaled through intent and a service shutdown simultaneously.
                 * Intended behavior not documented, hence we need to be able to handle all cases*/
            } else {
                if(DEBUG)
                    Log.e(TAG, "mSessionStatusHandler.sendMessage() dispatched shutdown message");
            }
        } else if (mSessionStatusHandler != null) {
                if(DEBUG)
                    Log.w(TAG, "mSessionStatusHandler shutdown message already in Queue");
        }
        if (VERBOSE) Log.d(TAG, "sendShutdownMessage() Out");
    }

    private MapBroadcastReceiver mMapReceiver = new MapBroadcastReceiver();

    private class MapBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "onReceive");
            String action = intent.getAction();
            if (DEBUG) Log.d(TAG, "onReceive: " + action);
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                                               BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_TURNING_OFF) {
                    if (DEBUG) Log.d(TAG, "STATE_TURNING_OFF");
                    sendShutdownMessage();
                } else if (state == BluetoothAdapter.STATE_ON) {
                    if (DEBUG) Log.d(TAG, "STATE_ON");
                    // start ServerSocket listener threads
                    sendStartListenerMessage(-1);
                }

            }else if (action.equals(USER_CONFIRM_TIMEOUT_ACTION)){
                if (DEBUG) Log.d(TAG, "USER_CONFIRM_TIMEOUT ACTION Received.");
                // send us self a message about the timeout.
                sendConnectTimeoutMessage();

            } else if (action.equals(BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY)) {

                int requestType = intent.getIntExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                                               BluetoothDevice.REQUEST_TYPE_PHONEBOOK_ACCESS);

                if (DEBUG) Log.d(TAG, "Received ACTION_CONNECTION_ACCESS_REPLY:" +
                           requestType + "isWaitingAuthorization:" + mIsWaitingAuthorization);
                if ((!mIsWaitingAuthorization)
                        || (requestType != BluetoothDevice.REQUEST_TYPE_MESSAGE_ACCESS)) {
                    // this reply is not for us
                    return;
                }

                mIsWaitingAuthorization = false;
                if (mRemoveTimeoutMsg) {
                    mSessionStatusHandler.removeMessages(USER_TIMEOUT);
                    cancelUserTimeoutAlarm();
                    setState(BluetoothMap.STATE_DISCONNECTED);
                }

                if (intent.getIntExtra(BluetoothDevice.EXTRA_CONNECTION_ACCESS_RESULT,
                                       BluetoothDevice.CONNECTION_ACCESS_NO)
                        == BluetoothDevice.CONNECTION_ACCESS_YES) {
                    // Bluetooth connection accepted by user
                    mPermission = BluetoothDevice.ACCESS_ALLOWED;
                    if (intent.getBooleanExtra(BluetoothDevice.EXTRA_ALWAYS_ALLOWED, false)) {
                        boolean result = mRemoteDevice.setMessageAccessPermission(
                                BluetoothDevice.ACCESS_ALLOWED);
                        if (DEBUG) {
                            Log.d(TAG, "setMessageAccessPermission(ACCESS_ALLOWED) result="
                                    + result);
                        }
                    }

                    mRemoteDevice.sdpSearch(BluetoothMnsObexClient.BLUETOOTH_UUID_OBEX_MNS);
                    mSdpSearchInitiated = true;
                } else {
                    // Auth. declined by user, serverSession should not be running, but
                    // call stop anyway to restart listener.
                    mPermission = BluetoothDevice.ACCESS_REJECTED;
                    if (intent.getBooleanExtra(BluetoothDevice.EXTRA_ALWAYS_ALLOWED, false)) {
                        boolean result = mRemoteDevice.setMessageAccessPermission(
                                BluetoothDevice.ACCESS_REJECTED);
                        if (DEBUG) {
                            Log.d(TAG, "setMessageAccessPermission(ACCESS_REJECTED) result="
                                    + result);
                        }
                    }
                    sendConnectCancelMessage();
                }
            } else if (action.equals(BluetoothDevice.ACTION_SDP_RECORD)) {
                if (DEBUG) Log.d(TAG, "Received ACTION_SDP_RECORD.");
                ParcelUuid uuid = intent.getParcelableExtra(BluetoothDevice.EXTRA_UUID);
                if (VERBOSE) {
                    Log.v(TAG, "Received UUID: " + uuid.toString());
                    Log.v(TAG, "expected UUID: " +
                          BluetoothMnsObexClient.BLUETOOTH_UUID_OBEX_MNS.toString());
                }
                if (uuid.equals(BluetoothMnsObexClient.BLUETOOTH_UUID_OBEX_MNS)) {
                    mMnsRecord = intent.getParcelableExtra(BluetoothDevice.EXTRA_SDP_RECORD);
                    int status = intent.getIntExtra(BluetoothDevice.EXTRA_SDP_SEARCH_STATUS, -1);
                    if (VERBOSE) {
                        Log.v(TAG, " -> MNS Record:" + mMnsRecord);
                        Log.v(TAG, " -> status: " + status);
                    }
                    if (mBluetoothMnsObexClient != null && !mSdpSearchInitiated) {
                        mBluetoothMnsObexClient.setMnsRecord(mMnsRecord);
                    }
                    if (status != -1 && mMnsRecord != null) {
                        for (int i = 0, c = mMasInstances.size(); i < c; i++) {
                                mMasInstances.valueAt(i).setRemoteFeatureMask(
                                        mMnsRecord.getSupportedFeatures());
                        }
                    }
                    if (mSdpSearchInitiated) {
                        mSdpSearchInitiated = false; // done searching
                        sendConnectMessage(-1); // -1 indicates all MAS instances
                    }
                }
            } else if (action.equals(ACTION_SHOW_MAPS_SETTINGS)) {
                if (VERBOSE) Log.v(TAG, "Received ACTION_SHOW_MAPS_SETTINGS.");

                Intent in = new Intent(context, BluetoothMapSettings.class);
                in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(in);
            } else if (action.equals(BluetoothMapContentObserver.ACTION_MESSAGE_SENT)) {
                BluetoothMapMasInstance masInst = null;
                int result = getResultCode();
                boolean handled = false;
                if(mSmsCapable && mMasInstances != null &&
                        (masInst = mMasInstances.get(MAS_ID_SMS_MMS)) != null) {
                    intent.putExtra(BluetoothMapContentObserver.EXTRA_MESSAGE_SENT_RESULT, result);
                    if(masInst.handleSmsSendIntent(context, intent)) {
                        // The intent was handled by the mas instance it self
                        handled = true;
                    }
                }
                if(handled == false)
                {
                    /* We do not have a connection to a device, hence we need to move
                       the SMS to the correct folder. */
                    BluetoothMapContentObserver
                            .actionMessageSentDisconnected(context, intent, result);
                }
            } else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED) &&
                    mIsWaitingAuthorization) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (mRemoteDevice == null || device == null) {
                    Log.e(TAG, "Unexpected error!");
                    return;
                }

                if (VERBOSE) Log.v(TAG,"ACL disconnected for " + device);

                if (mRemoteDevice.equals(device)) {
                    // Send any pending timeout now, since ACL got disconnected
                    mSessionStatusHandler.removeMessages(USER_TIMEOUT);
                    mSessionStatusHandler.obtainMessage(USER_TIMEOUT).sendToTarget();
                }
            }
        }
    };

    //Binder object: Must be static class or memory leak may occur
    /**
     * This class implements the IBluetoothMap interface - or actually it validates the
     * preconditions for calling the actual functionality in the MapService, and calls it.
     */
    private static class BluetoothMapBinder extends IBluetoothMap.Stub
        implements IProfileServiceBinder {
        private BluetoothMapService mService;

        private BluetoothMapService getService() {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"MAP call not allowed for non-active user");
                return null;
            }

            if (mService != null && mService.isAvailable()) {
                mService.enforceCallingOrSelfPermission(BLUETOOTH_PERM,"Need BLUETOOTH permission");
                return mService;
            }
            return null;
        }

        BluetoothMapBinder(BluetoothMapService service) {
            if (VERBOSE) Log.v(TAG, "BluetoothMapBinder()");
            mService = service;
        }

        public boolean cleanup()  {
            mService = null;
            return true;
        }

        public int getState() {
            if (VERBOSE) Log.v(TAG, "getState()");
            BluetoothMapService service = getService();
            if (service == null) return BluetoothMap.STATE_DISCONNECTED;
            return getService().getState();
        }

        public BluetoothDevice getClient() {
            if (VERBOSE) Log.v(TAG, "getClient()");
            BluetoothMapService service = getService();
            if (service == null) return null;
            if (VERBOSE) Log.v(TAG, "getClient() - returning " + service.getRemoteDevice());
            return service.getRemoteDevice();
        }

        public boolean isConnected(BluetoothDevice device) {
            if (VERBOSE) Log.v(TAG, "isConnected()");
            BluetoothMapService service = getService();
            if (service == null) return false;
            return service.getState() == BluetoothMap.STATE_CONNECTED
                    && service.getRemoteDevice().equals(device);
        }

        public boolean connect(BluetoothDevice device) {
            if (VERBOSE) Log.v(TAG, "connect()");
            BluetoothMapService service = getService();
            if (service == null) return false;
            return false;
        }

        public boolean disconnect(BluetoothDevice device) {
            if (VERBOSE) Log.v(TAG, "disconnect()");
            BluetoothMapService service = getService();
            if (service == null) return false;
            return service.disconnect(device);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            if (VERBOSE) Log.v(TAG, "getConnectedDevices()");
            BluetoothMapService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getConnectedDevices();
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            if (VERBOSE) Log.v(TAG, "getDevicesMatchingConnectionStates()");
            BluetoothMapService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getDevicesMatchingConnectionStates(states);
        }

        public int getConnectionState(BluetoothDevice device) {
            if (VERBOSE) Log.v(TAG, "getConnectionState()");
            BluetoothMapService service = getService();
            if (service == null) return BluetoothProfile.STATE_DISCONNECTED;
            return service.getConnectionState(device);
        }

        public boolean setPriority(BluetoothDevice device, int priority) {
            BluetoothMapService service = getService();
            if (service == null) return false;
            return service.setPriority(device, priority);
        }

        public int getPriority(BluetoothDevice device) {
            BluetoothMapService service = getService();
            if (service == null) return BluetoothProfile.PRIORITY_UNDEFINED;
            return service.getPriority(device);
        }
    }

    @Override
    public void dump(StringBuilder sb) {
        super.dump(sb);
        println(sb, "mRemoteDevice: " + mRemoteDevice);
        println(sb, "sRemoteDeviceName: " + sRemoteDeviceName);
        println(sb, "mState: " + mState);
        println(sb, "mAppObserver: " + mAppObserver);
        println(sb, "mIsWaitingAuthorization: " + mIsWaitingAuthorization);
        println(sb, "mRemoveTimeoutMsg: " + mRemoveTimeoutMsg);
        println(sb, "mPermission: " + mPermission);
        println(sb, "mAccountChanged: " + mAccountChanged);
        println(sb, "mBluetoothMnsObexClient: " + mBluetoothMnsObexClient);
        println(sb, "mMasInstanceMap:");
        for (BluetoothMapAccountItem key : mMasInstanceMap.keySet()) {
            println(sb, "  " + key + " : " + mMasInstanceMap.get(key));
        }
        println(sb, "mEnabledAccounts:");
        for (BluetoothMapAccountItem account : mEnabledAccounts) {
            println(sb, "  " + account);
        }
    }
}
