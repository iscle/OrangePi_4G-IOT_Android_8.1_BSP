/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.pbap;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothPbap;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetoothPbap;
import android.database.sqlite.SQLiteException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.bluetooth.BluetoothObexTransport;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ProfileService.IProfileServiceBinder;
import com.android.bluetooth.IObexConnectionHandler;
import com.android.bluetooth.ObexServerSockets;
import com.android.bluetooth.R;
import com.android.bluetooth.sdp.SdpManager;
import com.android.bluetooth.Utils;
import com.android.bluetooth.util.DevicePolicyUtils;

import java.io.IOException;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicLong;
import java.util.HashMap;

import javax.obex.ServerSession;

public class BluetoothPbapService extends ProfileService implements IObexConnectionHandler {
    private static final String TAG = "BluetoothPbapService";

    /**
     * To enable PBAP DEBUG/VERBOSE logging - run below cmd in adb shell, and
     * restart com.android.bluetooth process. only enable DEBUG log:
     * "setprop log.tag.BluetoothPbapService DEBUG"; enable both VERBOSE and
     * DEBUG log: "setprop log.tag.BluetoothPbapService VERBOSE"
     */

    public static final boolean DEBUG = true;

    public static final boolean VERBOSE = false;

    /**
     * Intent indicating incoming obex authentication request which is from
     * PCE(Carkit)
     */
    public static final String AUTH_CHALL_ACTION = "com.android.bluetooth.pbap.authchall";

    /**
     * Intent indicating obex session key input complete by user which is sent
     * from BluetoothPbapActivity
     */
    public static final String AUTH_RESPONSE_ACTION = "com.android.bluetooth.pbap.authresponse";

    /**
     * Intent indicating user canceled obex authentication session key input
     * which is sent from BluetoothPbapActivity
     */
    public static final String AUTH_CANCELLED_ACTION = "com.android.bluetooth.pbap.authcancelled";

    /**
     * Intent indicating timeout for user confirmation, which is sent to
     * BluetoothPbapActivity
     */
    public static final String USER_CONFIRM_TIMEOUT_ACTION =
            "com.android.bluetooth.pbap.userconfirmtimeout";

    /**
     * Intent Extra name indicating session key which is sent from
     * BluetoothPbapActivity
     */
    public static final String EXTRA_SESSION_KEY = "com.android.bluetooth.pbap.sessionkey";

    public static final String THIS_PACKAGE_NAME = "com.android.bluetooth";

    public static final int MSG_SERVERSESSION_CLOSE = 5000;

    public static final int MSG_SESSION_ESTABLISHED = 5001;

    public static final int MSG_SESSION_DISCONNECTED = 5002;

    public static final int MSG_OBEX_AUTH_CHALL = 5003;

    public static final int MSG_ACQUIRE_WAKE_LOCK = 5004;

    public static final int MSG_RELEASE_WAKE_LOCK = 5005;

    private static final String BLUETOOTH_PERM = android.Manifest.permission.BLUETOOTH;

    private static final String BLUETOOTH_ADMIN_PERM = android.Manifest.permission.BLUETOOTH_ADMIN;

    private static final int START_LISTENER = 1;

    private static final int USER_TIMEOUT = 2;

    private static final int AUTH_TIMEOUT = 3;

    private static final int SHUTDOWN = 4;

    protected static final int LOAD_CONTACTS = 5;

    private static final int CHECK_SECONDARY_VERSION_COUNTER = 6;

    protected static final int ROLLOVER_COUNTERS = 7;

    private static final int USER_CONFIRM_TIMEOUT_VALUE = 30000;

    private static final int RELEASE_WAKE_LOCK_DELAY = 10000;

    // Ensure not conflict with Opp notification ID
    private static final int NOTIFICATION_ID_ACCESS = -1000001;

    private static final int NOTIFICATION_ID_AUTH = -1000002;

    private static final String PBAP_NOTIFICATION_CHANNEL = "pbap_notification_channel";

    private PowerManager.WakeLock mWakeLock = null;

    private BluetoothPbapAuthenticator mAuth = null;

    private BluetoothPbapObexServer mPbapServer;

    private ServerSession mServerSession = null;

    private BluetoothServerSocket mServerSocket = null;

    private BluetoothSocket mConnSocket = null;

    private BluetoothDevice mRemoteDevice = null;

    private static String sLocalPhoneNum = null;

    private static String sLocalPhoneName = null;

    private static String sRemoteDeviceName = null;

    private volatile boolean mInterrupted;

    private int mState;

    private boolean mIsWaitingAuthorization = false;

    private ObexServerSockets mServerSockets = null;

    private static final int SDP_PBAP_SERVER_VERSION = 0x0102;

    private static final int SDP_PBAP_SUPPORTED_REPOSITORIES = 0x0003;

    private static final int SDP_PBAP_SUPPORTED_FEATURES = 0x021F;

    private AlarmManager mAlarmManager = null;

    private int mSdpHandle = -1;

    private boolean mRemoveTimeoutMsg = false;

    private int mPermission = BluetoothDevice.ACCESS_UNKNOWN;

    private boolean mSdpSearchInitiated = false;

    private boolean isRegisteredObserver = false;

    protected Context mContext;

    // package and class name to which we send intent to check phone book access permission
    private static final String ACCESS_AUTHORITY_PACKAGE = "com.android.settings";
    private static final String ACCESS_AUTHORITY_CLASS =
            "com.android.settings.bluetooth.BluetoothPermissionRequest";

    private class BluetoothPbapContentObserver extends ContentObserver {
        public BluetoothPbapContentObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            Log.d(TAG, " onChange on contact uri ");
            if (BluetoothPbapUtils.contactsLoaded) {
                if (!mSessionStatusHandler.hasMessages(CHECK_SECONDARY_VERSION_COUNTER)) {
                    mSessionStatusHandler.sendMessage(
                            mSessionStatusHandler.obtainMessage(CHECK_SECONDARY_VERSION_COUNTER));
                }
            }
        }
    }

    private BluetoothPbapContentObserver mContactChangeObserver;

    public BluetoothPbapService() {
        mState = BluetoothPbap.STATE_DISCONNECTED;
        mContext = this;
    }

    // process the intent from receiver
    private void parseIntent(final Intent intent) {
        String action = intent.getAction();
        if (DEBUG) Log.d(TAG, "action: " + action);
        if (action == null) return;             // Nothing to do
        int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
        if (DEBUG) Log.d(TAG, "state: " + state);
        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            if (state == BluetoothAdapter.STATE_TURNING_OFF) {
                // Send any pending timeout now, as this service will be destroyed.
                if (mSessionStatusHandler.hasMessages(USER_TIMEOUT)) {
                    mSessionStatusHandler.removeMessages(USER_TIMEOUT);
                    mSessionStatusHandler.obtainMessage(USER_TIMEOUT).sendToTarget();
                }
                // Release all resources
                closeService();
            } else if (state == BluetoothAdapter.STATE_ON) {
                // start RFCOMM listener
                mSessionStatusHandler.sendMessage(mSessionStatusHandler.obtainMessage(START_LISTENER));
            }
            return;
        }

        if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED) && mIsWaitingAuthorization) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (mRemoteDevice == null) return;
            if (DEBUG) Log.d(TAG,"ACL disconnected for "+ device);
            if (mRemoteDevice.equals(device)) {
                mSessionStatusHandler.removeMessages(USER_TIMEOUT);
                mSessionStatusHandler.obtainMessage(USER_TIMEOUT).sendToTarget();
            }
            return;
        }

        if (action.equals(BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY)) {
            int requestType = intent.getIntExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                                           BluetoothDevice.REQUEST_TYPE_PHONEBOOK_ACCESS);

            if ((!mIsWaitingAuthorization)
                    || (requestType != BluetoothDevice.REQUEST_TYPE_PHONEBOOK_ACCESS)) {
                // this reply is not for us
                return;
            }

            mSessionStatusHandler.removeMessages(USER_TIMEOUT);
            mIsWaitingAuthorization = false;

            if (intent.getIntExtra(BluetoothDevice.EXTRA_CONNECTION_ACCESS_RESULT,
                                   BluetoothDevice.CONNECTION_ACCESS_NO)
                    == BluetoothDevice.CONNECTION_ACCESS_YES) {
                if (intent.getBooleanExtra(BluetoothDevice.EXTRA_ALWAYS_ALLOWED, false)) {
                    boolean result = mRemoteDevice.setPhonebookAccessPermission(
                            BluetoothDevice.ACCESS_ALLOWED);
                    if (VERBOSE) {
                        Log.v(TAG, "setPhonebookAccessPermission(ACCESS_ALLOWED)=" + result);
                    }
                }
                try {
                    if (mConnSocket != null) {
                        startObexServerSession();
                    } else {
                        stopObexServerSession();
                    }
                } catch (IOException ex) {
                    Log.e(TAG, "Caught the error: " + ex.toString());
                }
            } else {
                if (intent.getBooleanExtra(BluetoothDevice.EXTRA_ALWAYS_ALLOWED, false)) {
                    boolean result = mRemoteDevice.setPhonebookAccessPermission(
                            BluetoothDevice.ACCESS_REJECTED);
                    if (VERBOSE) {
                        Log.v(TAG, "setPhonebookAccessPermission(ACCESS_REJECTED)=" + result);
                    }
                }
                stopObexServerSession();
            }
            return;
        }

        if (action.equals(AUTH_RESPONSE_ACTION)) {
            String sessionkey = intent.getStringExtra(EXTRA_SESSION_KEY);
            notifyAuthKeyInput(sessionkey);
        } else if (action.equals(AUTH_CANCELLED_ACTION)) {
            notifyAuthCancelled();
        } else {
            Log.w(TAG, "Unrecognized intent!");
            return;
        }

        mSessionStatusHandler.removeMessages(USER_TIMEOUT);
    }

    private BroadcastReceiver mPbapReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            parseIntent(intent);
        }
    };

    private final boolean initSocket() {
        if (VERBOSE) Log.v(TAG, "Pbap Service initSocket");

        boolean initSocketOK = false;
        final int CREATE_RETRY_TIME = 10;

        // It's possible that create will fail in some cases. retry for 10 times
        for (int i = 0; i < CREATE_RETRY_TIME && !mInterrupted; i++) {
            initSocketOK = true;
            try {
                // It is mandatory for PSE to support initiation of bonding and
                // encryption.
                mServerSocket = mAdapter.listenUsingEncryptedRfcommWithServiceRecord
                    ("OBEX Phonebook Access Server", BluetoothUuid.PBAP_PSE.getUuid());

            } catch (IOException e) {
                Log.e(TAG, "Error create RfcommServerSocket " + e.toString());
                initSocketOK = false;
            }
            if (!initSocketOK) {
                // Need to break out of this loop if BT is being turned off.
                if (mAdapter == null) break;
                int state = mAdapter.getState();
                if ((state != BluetoothAdapter.STATE_TURNING_ON) &&
                    (state != BluetoothAdapter.STATE_ON)) {
                    Log.w(TAG, "initServerSocket failed as BT is (being) turned off");
                    break;
                }
                try {
                    if (VERBOSE) Log.v(TAG, "wait 300 ms");
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Log.e(TAG, "socketAcceptThread thread was interrupted (3)");
                    break;
                }
            } else {
                break;
            }
        }

        if (mInterrupted) {
            initSocketOK = false;
            // close server socket to avoid resource leakage
            closeServerSocket();
        }

        if (initSocketOK) {
            if (VERBOSE) Log.v(TAG, "Succeed to create listening socket ");

        } else {
            Log.e(TAG, "Error to create listening socket after " + CREATE_RETRY_TIME + " try");
        }
        return initSocketOK;
    }

    private final synchronized void closeServerSocket() {
        // exit SocketAcceptThread early
        if (mServerSocket != null) {
            try {
                // this will cause mServerSocket.accept() return early with IOException
                mServerSocket.close();
                mServerSocket = null;
            } catch (IOException ex) {
                Log.e(TAG, "Close Server Socket error: " + ex);
            }
        }
    }

    private final synchronized void closeConnectionSocket() {
        if (mConnSocket != null) {
            try {
                mConnSocket.close();
                mConnSocket = null;
            } catch (IOException e) {
                Log.e(TAG, "Close Connection Socket error: " + e.toString());
            }
        }
    }

    private final void closeService() {
        if (VERBOSE) Log.v(TAG, "Pbap Service closeService in");

        BluetoothPbapUtils.savePbapParams(this, BluetoothPbapUtils.primaryVersionCounter,
                BluetoothPbapUtils.secondaryVersionCounter, BluetoothPbapUtils.mDbIdentifier.get(),
                BluetoothPbapUtils.contactsLastUpdated, BluetoothPbapUtils.totalFields,
                BluetoothPbapUtils.totalSvcFields, BluetoothPbapUtils.totalContacts);

        // exit initSocket early
        mInterrupted = true;
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }

        // Step 1: clean up active server session
        if (mServerSession != null) {
            mServerSession.close();
            mServerSession = null;
        }
        // Step 2: clean up existing connection socket
        closeConnectionSocket();
        // Step 3: clean up SDP record
        cleanUpSdpRecord();
        // Step 4: clean up existing server socket(s)
        closeServerSocket();
        if (mServerSockets != null) {
            mServerSockets.shutdown(false);
            mServerSockets = null;
        }
        if (mSessionStatusHandler != null) mSessionStatusHandler.removeCallbacksAndMessages(null);
        if (VERBOSE) Log.v(TAG, "Pbap Service closeService out");
    }

    private void cleanUpSdpRecord() {
        if (mSdpHandle < 0) {
            if (VERBOSE) Log.v(TAG, "cleanUpSdpRecord, SDP record never created");
            return;
        }
        int sdpHandle = mSdpHandle;
        mSdpHandle = -1;
        SdpManager sdpManager = SdpManager.getDefaultManager();
        if (sdpManager == null) {
            Log.e(TAG, "cleanUpSdpRecord failed, sdpManager is null, sdpHandle=" + sdpHandle);
            return;
        }
        Log.i(TAG, "cleanUpSdpRecord, mSdpHandle=" + sdpHandle);
        if (!sdpManager.removeSdpRecord(sdpHandle)) {
            Log.e(TAG, "cleanUpSdpRecord, removeSdpRecord failed, sdpHandle=" + sdpHandle);
        }
    }

    private final void startObexServerSession() throws IOException {
        if (VERBOSE) Log.v(TAG, "Pbap Service startObexServerSession");

        // acquire the wakeLock before start Obex transaction thread
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "StartingObexPbapTransaction");
            mWakeLock.setReferenceCounted(false);
            mWakeLock.acquire();
        }
        TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            sLocalPhoneNum = tm.getLine1Number();
            sLocalPhoneName = tm.getLine1AlphaTag();
            if (TextUtils.isEmpty(sLocalPhoneName)) {
                sLocalPhoneName = this.getString(R.string.localPhoneName);
            }
        }

        mPbapServer = new BluetoothPbapObexServer(mSessionStatusHandler, this);
        synchronized (this) {
            mAuth = new BluetoothPbapAuthenticator(mSessionStatusHandler);
            mAuth.setChallenged(false);
            mAuth.setCancelled(false);
        }
        BluetoothObexTransport transport = new BluetoothObexTransport(mConnSocket);
        mServerSession = new ServerSession(transport, mPbapServer, mAuth);
        setState(BluetoothPbap.STATE_CONNECTED);

        mSessionStatusHandler.removeMessages(MSG_RELEASE_WAKE_LOCK);
        mSessionStatusHandler.sendMessageDelayed(mSessionStatusHandler
            .obtainMessage(MSG_RELEASE_WAKE_LOCK), RELEASE_WAKE_LOCK_DELAY);

        if (VERBOSE) {
            Log.v(TAG, "startObexServerSession() success!");
        }
    }

    private void stopObexServerSession() {
        if (VERBOSE) Log.v(TAG, "Pbap Service stopObexServerSession");
        mSessionStatusHandler.removeMessages(MSG_ACQUIRE_WAKE_LOCK);
        mSessionStatusHandler.removeMessages(MSG_RELEASE_WAKE_LOCK);
        // Release the wake lock if obex transaction is over
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }

        if (mServerSession != null) {
            mServerSession.close();
            mServerSession = null;
        }
        closeConnectionSocket();

        // Last obex transaction is finished, we start to listen for incoming
        // connection again
        if (mAdapter != null && mAdapter.isEnabled()) {
            startSocketListeners();
        }
        setState(BluetoothPbap.STATE_DISCONNECTED);
    }

    private void notifyAuthKeyInput(final String key) {
        synchronized (mAuth) {
            if (key != null) {
                mAuth.setSessionKey(key);
            }
            mAuth.setChallenged(true);
            mAuth.notify();
        }
    }

    private void notifyAuthCancelled() {
        synchronized (mAuth) {
            mAuth.setCancelled(true);
            mAuth.notify();
        }
    }

    /**
     * A thread that runs in the background waiting for remote rfcomm
     * connect.Once a remote socket connected, this thread shall be
     * shutdown.When the remote disconnect,this thread shall run again waiting
     * for next request.
     */
    private class SocketAcceptThread extends Thread {

        private boolean stopped = false;

        @Override
        public void run() {
            BluetoothServerSocket serverSocket;
            if (mServerSocket == null) {
                if (!initSocket()) {
                    return;
                }
            }

            while (!stopped) {
                try {
                    if (VERBOSE) Log.v(TAG, "Accepting socket connection...");
                    serverSocket = mServerSocket;
                    if (serverSocket == null) {
                        Log.w(TAG, "mServerSocket is null");
                        break;
                    }
                    mConnSocket = serverSocket.accept();
                    if (VERBOSE) Log.v(TAG, "Accepted socket connection...");

                    synchronized (BluetoothPbapService.this) {
                        if (mConnSocket == null) {
                            Log.w(TAG, "mConnSocket is null");
                            break;
                        }
                        mRemoteDevice = mConnSocket.getRemoteDevice();
                    }
                    if (mRemoteDevice == null) {
                        Log.i(TAG, "getRemoteDevice() = null");
                        break;
                    }
                    sRemoteDeviceName = mRemoteDevice.getName();
                    // In case getRemoteName failed and return null
                    if (TextUtils.isEmpty(sRemoteDeviceName)) {
                        sRemoteDeviceName = getString(R.string.defaultname);
                    }
                    int permission = mRemoteDevice.getPhonebookAccessPermission();
                    if (VERBOSE) Log.v(TAG, "getPhonebookAccessPermission() = " + permission);

                    if (permission == BluetoothDevice.ACCESS_ALLOWED) {
                        try {
                            if (VERBOSE) {
                                Log.v(TAG, "incoming connection accepted from: " + sRemoteDeviceName
                                        + " automatically as already allowed device");
                            }
                            startObexServerSession();
                        } catch (IOException ex) {
                            Log.e(TAG, "Caught exception starting obex server session"
                                    + ex.toString());
                        }
                    } else if (permission == BluetoothDevice.ACCESS_REJECTED) {
                        if (VERBOSE) {
                            Log.v(TAG, "incoming connection rejected from: " + sRemoteDeviceName
                                    + " automatically as already rejected device");
                        }
                        stopObexServerSession();
                    } else {  // permission == BluetoothDevice.ACCESS_UNKNOWN
                        // Send an Intent to Settings app to ask user preference.
                        Intent intent =
                                new Intent(BluetoothDevice.ACTION_CONNECTION_ACCESS_REQUEST);
                        intent.setPackage(getString(R.string.pairing_ui_package));
                        intent.putExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                                        BluetoothDevice.REQUEST_TYPE_PHONEBOOK_ACCESS);
                        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mRemoteDevice);
                        intent.putExtra(BluetoothDevice.EXTRA_PACKAGE_NAME, getPackageName());
                        intent.putExtra(BluetoothDevice.EXTRA_CLASS_NAME, getName());

                        mIsWaitingAuthorization = true;
                        sendOrderedBroadcast(intent, BLUETOOTH_ADMIN_PERM);

                        if (VERBOSE) Log.v(TAG, "waiting for authorization for connection from: "
                                + sRemoteDeviceName);

                        // In case car kit time out and try to use HFP for
                        // phonebook
                        // access, while UI still there waiting for user to
                        // confirm
                        mSessionStatusHandler.sendMessageDelayed(mSessionStatusHandler
                                .obtainMessage(USER_TIMEOUT), USER_CONFIRM_TIMEOUT_VALUE);
                        // We will continue the process when we receive
                        // BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY from Settings app.
                    }
                    stopped = true; // job done ,close this thread;
                } catch (IOException ex) {
                    stopped=true;
                    /*
                    if (stopped) {
                        break;
                    }
                    */
                    if (VERBOSE) Log.v(TAG, "Accept exception: " + ex.toString());
                }
            }
        }

        void shutdown() {
            stopped = true;
            interrupt();
        }
    }

    protected final Handler mSessionStatusHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (VERBOSE) Log.v(TAG, "Handler(): got msg=" + msg.what);

            switch (msg.what) {
                case START_LISTENER:
                    if (mAdapter.isEnabled()) {
                        startSocketListeners();
                    }
                    break;
                case USER_TIMEOUT:
                    Intent intent = new Intent(BluetoothDevice.ACTION_CONNECTION_ACCESS_CANCEL);
                    intent.setPackage(getString(R.string.pairing_ui_package));
                    intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mRemoteDevice);
                    intent.putExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                                    BluetoothDevice.REQUEST_TYPE_PHONEBOOK_ACCESS);
                    sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);
                    mIsWaitingAuthorization = false;
                    stopObexServerSession();
                    break;
                case AUTH_TIMEOUT:
                    Intent i = new Intent(USER_CONFIRM_TIMEOUT_ACTION);
                    sendBroadcast(i);
                    removePbapNotification(NOTIFICATION_ID_AUTH);
                    notifyAuthCancelled();
                    break;
                case MSG_SERVERSESSION_CLOSE:
                    stopObexServerSession();
                    break;
                case MSG_SESSION_ESTABLISHED:
                    break;
                case MSG_SESSION_DISCONNECTED:
                    // case MSG_SERVERSESSION_CLOSE will handle ,so just skip
                    break;
                case MSG_OBEX_AUTH_CHALL:
                    createPbapNotification(AUTH_CHALL_ACTION);
                    mSessionStatusHandler.sendMessageDelayed(mSessionStatusHandler
                            .obtainMessage(AUTH_TIMEOUT), USER_CONFIRM_TIMEOUT_VALUE);
                    break;
                case MSG_ACQUIRE_WAKE_LOCK:
                    if (mWakeLock == null) {
                        PowerManager pm = (PowerManager)getSystemService(
                                          Context.POWER_SERVICE);
                        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                    "StartingObexPbapTransaction");
                        mWakeLock.setReferenceCounted(false);
                        mWakeLock.acquire();
                        Log.w(TAG, "Acquire Wake Lock");
                    }
                    mSessionStatusHandler.removeMessages(MSG_RELEASE_WAKE_LOCK);
                    mSessionStatusHandler.sendMessageDelayed(mSessionStatusHandler
                      .obtainMessage(MSG_RELEASE_WAKE_LOCK), RELEASE_WAKE_LOCK_DELAY);
                    break;
                case MSG_RELEASE_WAKE_LOCK:
                    if (mWakeLock != null) {
                        mWakeLock.release();
                        mWakeLock = null;
                        Log.w(TAG, "Release Wake Lock");
                    }
                    break;
                case SHUTDOWN:
                    closeService();
                    break;
                case LOAD_CONTACTS:
                    BluetoothPbapUtils.loadAllContacts(mContext, this);
                    break;
                case CHECK_SECONDARY_VERSION_COUNTER:
                    BluetoothPbapUtils.updateSecondaryVersionCounter(mContext, this);
                    break;
                case ROLLOVER_COUNTERS:
                    BluetoothPbapUtils.rolloverCounters();
                    break;
                default:
                    break;
            }
        }
    };

    private void setState(int state) {
        setState(state, BluetoothPbap.RESULT_SUCCESS);
    }

    private synchronized void setState(int state, int result) {
        if (state != mState) {
            if (DEBUG) Log.d(TAG, "Pbap state " + mState + " -> " + state + ", result = "
                    + result);
            int prevState = mState;
            mState = state;
            Intent intent = new Intent(BluetoothPbap.PBAP_STATE_CHANGED_ACTION);
            intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
            intent.putExtra(BluetoothProfile.EXTRA_STATE, mState);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mRemoteDevice);
            sendBroadcast(intent, BLUETOOTH_PERM);
        }
    }

    protected int getState() {
        return mState;
    }

    protected BluetoothDevice getRemoteDevice() {
        return mRemoteDevice;
    }

    private void createPbapNotification(String action) {

        NotificationManager nm = (NotificationManager)
            getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel notificationChannel = new NotificationChannel(PBAP_NOTIFICATION_CHANNEL,
                getString(R.string.pbap_notification_group), NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(notificationChannel);

        // Create an intent triggered by clicking on the status icon.
        Intent clickIntent = new Intent();
        clickIntent.setClass(this, BluetoothPbapActivity.class);
        clickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        clickIntent.setAction(action);

        // Create an intent triggered by clicking on the
        // "Clear All Notifications" button
        Intent deleteIntent = new Intent();
        deleteIntent.setClass(this, BluetoothPbapService.class);
        deleteIntent.setAction(AUTH_CANCELLED_ACTION);

        String name = getRemoteDeviceName();

        if (action.equals(AUTH_CHALL_ACTION)) {
            Notification notification =
                    new Notification.Builder(this, PBAP_NOTIFICATION_CHANNEL)
                            .setWhen(System.currentTimeMillis())
                            .setContentTitle(getString(R.string.auth_notif_title))
                            .setContentText(getString(R.string.auth_notif_message, name))
                            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                            .setTicker(getString(R.string.auth_notif_ticker))
                            .setColor(getResources().getColor(
                                    com.android.internal.R.color.system_notification_accent_color,
                                    this.getTheme()))
                            .setFlag(Notification.FLAG_AUTO_CANCEL, true)
                            .setFlag(Notification.FLAG_ONLY_ALERT_ONCE, true)
                            .setDefaults(Notification.DEFAULT_SOUND)
                            .setContentIntent(PendingIntent.getActivity(this, 0, clickIntent, 0))
                            .setDeleteIntent(PendingIntent.getBroadcast(this, 0, deleteIntent, 0))
                            .build();
            nm.notify(NOTIFICATION_ID_AUTH, notification);
        }
    }

    private void removePbapNotification(int id) {
        NotificationManager nm = (NotificationManager)
            getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(id);
    }

    public static String getLocalPhoneNum() {
        return sLocalPhoneNum;
    }

    public static String getLocalPhoneName() {
        return sLocalPhoneName;
    }

    public static String getRemoteDeviceName() {
        return sRemoteDeviceName;
    }

    @Override
    protected IProfileServiceBinder initBinder() {
        return new PbapBinder(this);
    }

    @Override
    protected boolean start() {
        Log.v(TAG, "start()");
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(AUTH_RESPONSE_ACTION);
        filter.addAction(AUTH_CANCELLED_ACTION);
        mInterrupted = false;
        BluetoothPbapConfig.init(this);
        mSessionStatusHandler.sendMessage(mSessionStatusHandler.obtainMessage(START_LISTENER));
        if (mContactChangeObserver == null) {
            registerReceiver(mPbapReceiver, filter);
            try {
                if (DEBUG) Log.d(TAG, "Registering observer");
                mContactChangeObserver = new BluetoothPbapContentObserver();
                getContentResolver().registerContentObserver(
                        DevicePolicyUtils.getEnterprisePhoneUri(this), false,
                        mContactChangeObserver);
            } catch (SQLiteException e) {
                Log.e(TAG, "SQLite exception: " + e);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Illegal state exception, content observer is already registered");
            }
        }
        return true;
    }

    @Override
    protected boolean stop() {
        Log.v(TAG, "stop()");
        if (mContactChangeObserver == null) {
            Log.i(TAG, "Avoid unregister when receiver it is not registered");
            return true;
        }
        try {
            unregisterReceiver(mPbapReceiver);
            getContentResolver().unregisterContentObserver(mContactChangeObserver);
            mContactChangeObserver = null;
        } catch (Exception e) {
            Log.w(TAG, "Unable to unregister pbap receiver", e);
        }
        mSessionStatusHandler.obtainMessage(SHUTDOWN).sendToTarget();
        setState(BluetoothPbap.STATE_DISCONNECTED, BluetoothPbap.RESULT_CANCELED);
        return true;
    }

    protected void disconnect() {
        synchronized (this) {
            if (mState == BluetoothPbap.STATE_CONNECTED) {
                if (mServerSession != null) {
                    mServerSession.close();
                    mServerSession = null;
                }

                closeConnectionSocket();

                setState(BluetoothPbap.STATE_DISCONNECTED, BluetoothPbap.RESULT_CANCELED);
            }
        }
    }

    // Has to be a static class or a memory leak can occur.
    private static class PbapBinder extends IBluetoothPbap.Stub implements IProfileServiceBinder {
        private BluetoothPbapService mService;

        private BluetoothPbapService getService(String perm) {
            if (!Utils.checkCaller()) {
                Log.w(TAG, "not allowed for non-active user");
                return null;
            }
            if (mService != null && mService.isAvailable()) {
                mService.enforceCallingOrSelfPermission(perm, "Need " + perm + " permission");
                return mService;
            }
            return null;
        }

        PbapBinder(BluetoothPbapService service) {
            Log.v(TAG, "PbapBinder()");
            mService = service;
        }

        public boolean cleanup() {
            mService = null;
            return true;
        }

        public int getState() {
            if (DEBUG) Log.d(TAG, "getState = " + mService.getState());
            BluetoothPbapService service = getService(BLUETOOTH_PERM);
            if (service == null) return BluetoothPbap.STATE_DISCONNECTED;

            return service.getState();
        }

        public BluetoothDevice getClient() {
            if (DEBUG) Log.d(TAG, "getClient = " + mService.getRemoteDevice());
            BluetoothPbapService service = getService(BLUETOOTH_PERM);
            if (service == null) return null;
            return service.getRemoteDevice();
        }

        public boolean isConnected(BluetoothDevice device) {
            if (DEBUG) Log.d(TAG, "isConnected " + device);
            BluetoothPbapService service = getService(BLUETOOTH_PERM);
            if (service == null) return false;
            return service.getState() == BluetoothPbap.STATE_CONNECTED
                    && service.getRemoteDevice().equals(device);
        }

        public boolean connect(BluetoothDevice device) {
            BluetoothPbapService service = getService(BLUETOOTH_ADMIN_PERM);
            return false;
        }

        public void disconnect() {
            if (DEBUG) Log.d(TAG, "disconnect");
            BluetoothPbapService service = getService(BLUETOOTH_ADMIN_PERM);
            if (service == null) return;
            service.disconnect();
        }
    }

    /**
     * Start server side socket listeners. Caller should make sure that adapter is in a ready state
     * and SDP record is cleaned up. Otherwise, this method will fail.
     */
    synchronized private void startSocketListeners() {
        if (DEBUG) Log.d(TAG, "startsocketListener");
        if (mServerSession != null) {
            if (DEBUG) Log.d(TAG, "mServerSession exists - shutting it down...");
            mServerSession.close();
            mServerSession = null;
        }
        closeConnectionSocket();
        if (mServerSockets != null) {
            mServerSockets.prepareForNewConnect();
        } else {
            mServerSockets = ObexServerSockets.create(this);
            if (mServerSockets == null) {
                // TODO: Handle - was not handled before
                Log.e(TAG, "Failed to start the listeners");
                return;
            }
            if (mSdpHandle >= 0) {
                Log.e(TAG, "SDP handle was not cleaned up, mSdpHandle=" + mSdpHandle);
                return;
            }
            mSdpHandle = SdpManager.getDefaultManager().createPbapPseRecord(
                    "OBEX Phonebook Access Server", mServerSockets.getRfcommChannel(),
                    mServerSockets.getL2capPsm(), SDP_PBAP_SERVER_VERSION,
                    SDP_PBAP_SUPPORTED_REPOSITORIES, SDP_PBAP_SUPPORTED_FEATURES);
            // fetch Pbap Params to check if significant change has happened to Database
            BluetoothPbapUtils.fetchPbapParams(mContext);

            if (DEBUG) Log.d(TAG, "PBAP server with handle:" + mSdpHandle);
        }
    }

    long getDbIdentifier() {
        return BluetoothPbapUtils.mDbIdentifier.get();
    }

    private void setUserTimeoutAlarm() {
        if (DEBUG) Log.d(TAG, "SetUserTimeOutAlarm()");
        if (mAlarmManager == null) {
            mAlarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        }
        mRemoveTimeoutMsg = true;
        Intent timeoutIntent = new Intent(USER_CONFIRM_TIMEOUT_ACTION);
        PendingIntent pIntent = PendingIntent.getBroadcast(this, 0, timeoutIntent, 0);
        mAlarmManager.set(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + USER_CONFIRM_TIMEOUT_VALUE, pIntent);
    }

    @Override
    public boolean onConnect(BluetoothDevice remoteDevice, BluetoothSocket socket) {
        mRemoteDevice = remoteDevice;
        if (mRemoteDevice == null || socket == null) {
            Log.i(TAG, "mRemoteDevice :" + mRemoteDevice + " socket :" + socket);
            return false;
        }
        mConnSocket = socket;
        sRemoteDeviceName = mRemoteDevice.getName();
        // In case getRemoteName failed and return null
        if (TextUtils.isEmpty(sRemoteDeviceName)) {
            sRemoteDeviceName = getString(R.string.defaultname);
        }
        int permission = mRemoteDevice.getPhonebookAccessPermission();
        if (DEBUG) Log.d(TAG, "getPhonebookAccessPermission() = " + permission);

        if (permission == BluetoothDevice.ACCESS_ALLOWED) {
            try {
                startObexServerSession();
            } catch (IOException ex) {
                Log.e(TAG, "Caught exception starting obex server session" + ex.toString());
            }

            if (!BluetoothPbapUtils.contactsLoaded) {
                mSessionStatusHandler.sendMessage(
                        mSessionStatusHandler.obtainMessage(LOAD_CONTACTS));
            }

        } else if (permission == BluetoothDevice.ACCESS_REJECTED) {
            if (DEBUG) {
                Log.d(TAG, "incoming connection rejected from: " + sRemoteDeviceName
                                + " automatically as already rejected device");
            }
            return false;
        } else { // permission == BluetoothDevice.ACCESS_UNKNOWN
            // Send an Intent to Settings app to ask user preference.
            Intent intent = new Intent(BluetoothDevice.ACTION_CONNECTION_ACCESS_REQUEST);
            intent.setClassName(ACCESS_AUTHORITY_PACKAGE, ACCESS_AUTHORITY_CLASS);
            intent.putExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                    BluetoothDevice.REQUEST_TYPE_PHONEBOOK_ACCESS);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mRemoteDevice);
            intent.putExtra(BluetoothDevice.EXTRA_PACKAGE_NAME, getPackageName());
            mIsWaitingAuthorization = true;
            sendOrderedBroadcast(intent, BLUETOOTH_ADMIN_PERM);
            if (VERBOSE)
                Log.v(TAG, "waiting for authorization for connection from: " + sRemoteDeviceName);
            /* In case car kit time out and try to use HFP for phonebook
             * access, while UI still there waiting for user to confirm */
            mSessionStatusHandler.sendMessageDelayed(
                    mSessionStatusHandler.obtainMessage(USER_TIMEOUT), USER_CONFIRM_TIMEOUT_VALUE);
            /* We will continue the process when we receive
             * BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY from Settings app. */
        }
        return true;
    };

    /**
     * Called when an unrecoverable error occurred in an accept thread.
     * Close down the server socket, and restart.
     * TODO: Change to message, to call start in correct context.
     */
    @Override
    public synchronized void onAcceptFailed() {
        // Clean up SDP record first
        cleanUpSdpRecord();
        // Force socket listener to restart
        if (mServerSockets != null) {
            mServerSockets.shutdown(false);
            mServerSockets = null;
        }
        if (!mInterrupted && mAdapter != null && mAdapter.isEnabled()) {
            startSocketListeners();
        }
    }
}
