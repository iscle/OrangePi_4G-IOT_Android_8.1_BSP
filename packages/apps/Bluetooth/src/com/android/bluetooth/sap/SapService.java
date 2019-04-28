package com.android.bluetooth.sap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSap;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetoothSap;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.bluetooth.R;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ProfileService.IProfileServiceBinder;
import com.android.bluetooth.sdp.SdpManager;

@TargetApi(Build.VERSION_CODES.ECLAIR)
public class SapService extends ProfileService {

    private static final String SDP_SAP_SERVICE_NAME = "SIM Access";
    private static final int SDP_SAP_VERSION = 0x0101;  // M: Current SAP version is v1.1
    private static final String TAG = "SapService";
    public static final boolean DEBUG = false;
    public static final boolean VERBOSE = false;

    /* Message ID's */
    private static final int START_LISTENER = 1;
    private static final int USER_TIMEOUT = 2;
    private static final int SHUTDOWN = 3;

    public static final int MSG_SERVERSESSION_CLOSE = 5000;
    public static final int MSG_SESSION_ESTABLISHED = 5001;
    public static final int MSG_SESSION_DISCONNECTED = 5002;

    public static final int MSG_ACQUIRE_WAKE_LOCK = 5005;
    public static final int MSG_RELEASE_WAKE_LOCK = 5006;

    public static final int MSG_CHANGE_STATE = 5007;

    /* Each time a transaction between the SIM and the BT Client is detected a wakelock is taken.
     * After an idle period of RELEASE_WAKE_LOCK_DELAY ms the wakelock is released.
     *
     * NOTE: While connected the the Nokia 616 car-kit it was noticed that the carkit do
     *       TRANSFER_APDU_REQ with 20-30 seconds interval, and it sends no requests less than 1 sec
     *       apart. Additionally the responses from the RIL seems to come within 100 ms, hence a
     *       one second timeout should be enough.
     */
    private static final int RELEASE_WAKE_LOCK_DELAY = 1000;

    /* Intent indicating timeout for user confirmation. */
    public static final String USER_CONFIRM_TIMEOUT_ACTION =
            "com.android.bluetooth.sap.USER_CONFIRM_TIMEOUT";
    private static final int USER_CONFIRM_TIMEOUT_VALUE = 25000;

    private PowerManager.WakeLock mWakeLock = null;
    private BluetoothAdapter mAdapter;
    private SocketAcceptThread mAcceptThread = null;
    private BluetoothServerSocket mServerSocket = null;
    private int mSdpHandle = -1;
    private BluetoothSocket mConnSocket = null;
    private BluetoothDevice mRemoteDevice = null;
    private static String sRemoteDeviceName = null;
    private volatile boolean mInterrupted;
    private int mState;
    private SapServer mSapServer = null;
    private AlarmManager mAlarmManager = null;
    private boolean mRemoveTimeoutMsg = false;

    private boolean mIsWaitingAuthorization = false;
    private boolean mIsRegistered = false;

    private static final ParcelUuid[] SAP_UUIDS = {
        BluetoothUuid.SAP,
    };


    public SapService() {
        mState = BluetoothSap.STATE_DISCONNECTED;
    }

    /***
     * Call this when ever an activity is detected to renew the wakelock
     *
     * @param messageHandler reference to the handler to notify
     *  - typically mSessionStatusHandler, but it cannot be accessed in a static manner.
     */
    public static void notifyUpdateWakeLock(Handler messageHandler) {
        if (messageHandler != null) {
            Message msg = Message.obtain(messageHandler);
            msg.what = MSG_ACQUIRE_WAKE_LOCK;
            msg.sendToTarget();
        }
    }

    private void removeSdpRecord() {
        if (mAdapter != null && mSdpHandle >= 0 &&
                                SdpManager.getDefaultManager() != null) {
            if(VERBOSE) Log.d(TAG, "Removing SDP record handle: " + mSdpHandle);
            boolean status = SdpManager.getDefaultManager().removeSdpRecord(mSdpHandle);
            mSdpHandle = -1;
        }
    }

    private void startRfcommSocketListener() {
        if (VERBOSE) Log.v(TAG, "Sap Service startRfcommSocketListener");

        if (mAcceptThread == null) {
            mAcceptThread = new SocketAcceptThread();
            mAcceptThread.setName("SapAcceptThread");
            mAcceptThread.start();
        }
    }

    private final boolean initSocket() {
        if (VERBOSE) Log.v(TAG, "Sap Service initSocket");

        boolean initSocketOK = false;
        final int CREATE_RETRY_TIME = 10;

        // It's possible that create will fail in some cases. retry for 10 times
        for (int i = 0; i < CREATE_RETRY_TIME && !mInterrupted; i++) {
            initSocketOK = true;
            try {
                // It is mandatory for MSE to support initiation of bonding and encryption.
                // TODO: Consider reusing the mServerSocket - it is indented to be reused
                //       for multiple connections.
                mServerSocket = mAdapter.listenUsingRfcommOn(
                        BluetoothAdapter.SOCKET_CHANNEL_AUTO_STATIC_NO_SDP, true, true);
                removeSdpRecord();
                mSdpHandle = SdpManager.getDefaultManager().createSapsRecord(SDP_SAP_SERVICE_NAME,
                        mServerSocket.getChannel(), SDP_SAP_VERSION);
            // Except IOException,NullPointException may popup while
            // mServerSocket set null by closeServerSocket function,
            // this may happend on multi thread access mServerSocket obj.
            } catch (Exception e) {
                Log.e(TAG, "Error create RfcommServerSocket ", e);
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
                    Log.e(TAG, "socketAcceptThread thread was interrupted (3)", e);
                }
            } else {
                break;
            }
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
                Log.e(TAG, "Close Server Socket error: ", ex);
            }
        }
    }
    private final synchronized void closeConnectionSocket() {
        if (mConnSocket != null) {
            try {
                mConnSocket.close();
                mConnSocket = null;
            } catch (IOException e) {
                Log.e(TAG, "Close Connection Socket error: ", e);
            }
        }
    }

    private final void closeService() {
        if (VERBOSE) Log.v(TAG, "SAP Service closeService in");

        // exit initSocket early
        mInterrupted = true;
        closeServerSocket();

        if (mAcceptThread != null) {
            try {
                mAcceptThread.shutdown();
                mAcceptThread.join();
                mAcceptThread = null;
            } catch (InterruptedException ex) {
                Log.w(TAG, "mAcceptThread close error", ex);
            }
        }

        if (mWakeLock != null) {
            mSessionStatusHandler.removeMessages(MSG_ACQUIRE_WAKE_LOCK);
            mSessionStatusHandler.removeMessages(MSG_RELEASE_WAKE_LOCK);
            mWakeLock.release();
            mWakeLock = null;
        }

        closeConnectionSocket();

        if (VERBOSE) Log.v(TAG, "SAP Service closeService out");
    }

    private final void startSapServerSession() throws IOException {
        if (VERBOSE) Log.v(TAG, "Sap Service startSapServerSession");

        // acquire the wakeLock before start SAP transaction thread
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "StartingSapTransaction");
            mWakeLock.setReferenceCounted(false);
            mWakeLock.acquire();
        }

        /* Start the SAP I/O thread and associate with message handler */
        mSapServer = new SapServer(mSessionStatusHandler, this, mConnSocket.getInputStream(), mConnSocket.getOutputStream());
        mSapServer.start();
        /* Warning: at this point we most likely have already handled the initial connect
         *          request from the SAP client, hence we need to be prepared to handle the
         *          response. (the SapHandler should have been started before this point)*/

        mSessionStatusHandler.removeMessages(MSG_RELEASE_WAKE_LOCK);
        mSessionStatusHandler.sendMessageDelayed(mSessionStatusHandler
                .obtainMessage(MSG_RELEASE_WAKE_LOCK), RELEASE_WAKE_LOCK_DELAY);

        if (VERBOSE) {
            Log.v(TAG, "startSapServerSession() success!");
        }
    }

    private void stopSapServerSession() {

        /* When we reach this point, the SapServer is closed down, and the client is
         * supposed to close the RFCOMM connection. */
        if (VERBOSE) Log.v(TAG, "SAP Service stopSapServerSession");

        mAcceptThread = null;
        closeConnectionSocket();
        closeServerSocket();

        setState(BluetoothSap.STATE_DISCONNECTED);

        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }

        // Last SAP transaction is finished, we start to listen for incoming
        // rfcomm connection again
        if (mAdapter.isEnabled()) {
            startRfcommSocketListener();
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
                    mConnSocket = mServerSocket.accept();
                    if (VERBOSE) Log.v(TAG, "Accepted socket connection...");
                    synchronized (SapService.this) {
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
                    int permission = mRemoteDevice.getSimAccessPermission();

                    if (VERBOSE) Log.v(TAG, "getSimAccessPermission() = " + permission);

                    if (permission == BluetoothDevice.ACCESS_ALLOWED) {
                        try {
                            if (VERBOSE) Log.v(TAG, "incoming connection accepted from: "
                                + sRemoteDeviceName + " automatically as trusted device");
                            startSapServerSession();
                        } catch (IOException ex) {
                            Log.e(TAG, "catch exception starting obex server session", ex);
                        }
                    } else if (permission != BluetoothDevice.ACCESS_REJECTED){
                        Intent intent = new
                                Intent(BluetoothDevice.ACTION_CONNECTION_ACCESS_REQUEST);
                        intent.setPackage(getString(R.string.pairing_ui_package));
                        intent.putExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                                        BluetoothDevice.REQUEST_TYPE_SIM_ACCESS);
                        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mRemoteDevice);
                        intent.putExtra(BluetoothDevice.EXTRA_PACKAGE_NAME, getPackageName());

                        mIsWaitingAuthorization = true;
                        setUserTimeoutAlarm();
                        sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);

                        if (VERBOSE) Log.v(TAG, "waiting for authorization for connection from: "
                                + sRemoteDeviceName);

                    } else {
                        // Close RFCOMM socket for current connection and start listening
                        // again for new connections.
                        Log.w(TAG, "Can't connect with " + sRemoteDeviceName +
                            " as access is rejected");
                        if (mSessionStatusHandler != null)
                            mSessionStatusHandler.sendEmptyMessage(MSG_SERVERSESSION_CLOSE);
                    }
                    stopped = true; // job done ,close this thread;
                } catch (IOException ex) {
                    stopped=true;
                    if (VERBOSE) Log.v(TAG, "Accept exception: ", ex);
                }
            }
        }

        void shutdown() {
            stopped = true;
            interrupt();
        }
    }

    private final Handler mSessionStatusHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (VERBOSE) Log.v(TAG, "Handler(): got msg=" + msg.what);

            switch (msg.what) {
                case START_LISTENER:
                    if (mAdapter.isEnabled()) {
                        startRfcommSocketListener();
                    }
                    break;
                case USER_TIMEOUT:
                    if (mIsWaitingAuthorization) {
                        sendCancelUserConfirmationIntent(mRemoteDevice);
                        cancelUserTimeoutAlarm();
                        mIsWaitingAuthorization = false;
                        stopSapServerSession(); // And restart RfcommListener if needed
                    }
                    break;
                case MSG_SERVERSESSION_CLOSE:
                    stopSapServerSession();
                    break;
                case MSG_SESSION_ESTABLISHED:
                    break;
                case MSG_SESSION_DISCONNECTED:
                    // handled elsewhere
                    break;
                case MSG_ACQUIRE_WAKE_LOCK:
                    if (VERBOSE)Log.i(TAG, "Acquire Wake Lock request message");
                    if (mWakeLock == null) {
                        PowerManager pm = (PowerManager)getSystemService(
                                          Context.POWER_SERVICE);
                        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                    "StartingObexMapTransaction");
                        mWakeLock.setReferenceCounted(false);
                    }
                    if (!mWakeLock.isHeld()) {
                        mWakeLock.acquire();
                        if (DEBUG)Log.i(TAG, "  Acquired Wake Lock by message");
                    }
                    mSessionStatusHandler.removeMessages(MSG_RELEASE_WAKE_LOCK);
                    mSessionStatusHandler.sendMessageDelayed(mSessionStatusHandler
                      .obtainMessage(MSG_RELEASE_WAKE_LOCK), RELEASE_WAKE_LOCK_DELAY);
                    break;
                case MSG_RELEASE_WAKE_LOCK:
                    if (VERBOSE)Log.i(TAG, "Release Wake Lock request message");
                    if (mWakeLock != null) {
                        mWakeLock.release();
                        if (DEBUG) Log.i(TAG, "  Released Wake Lock by message");
                    }
                    break;
                case MSG_CHANGE_STATE:
                    if (DEBUG) Log.d(TAG, "change state message: newState = " + msg.arg1);
                    setState(msg.arg1);
                    break;
                case SHUTDOWN:
                    /* Ensure to call close from this handler to avoid starting new stuff
                       because of pending messages */
                    closeService();
                    break;
                default:
                    break;
            }
        }
    };

    private void setState(int state) {
        setState(state, BluetoothSap.RESULT_SUCCESS);
    }

    private synchronized void setState(int state, int result) {
        if (state != mState) {
            if (DEBUG) Log.d(TAG, "Sap state " + mState + " -> " + state + ", result = "
                    + result);
            int prevState = mState;
            mState = state;
            Intent intent = new Intent(BluetoothSap.ACTION_CONNECTION_STATE_CHANGED);
            intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
            intent.putExtra(BluetoothProfile.EXTRA_STATE, mState);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mRemoteDevice);
            sendBroadcast(intent, BLUETOOTH_PERM);
        }
    }

    public int getState() {
        return mState;
    }

    public BluetoothDevice getRemoteDevice() {
        return mRemoteDevice;
    }

    public static String getRemoteDeviceName() {
        return sRemoteDeviceName;
    }

    public boolean disconnect(BluetoothDevice device) {
        boolean result = false;
        synchronized (SapService.this) {
            if (getRemoteDevice().equals(device)) {
                switch (mState) {
                    case BluetoothSap.STATE_CONNECTED:
                        closeConnectionSocket();
                        setState(BluetoothSap.STATE_DISCONNECTED, BluetoothSap.RESULT_CANCELED);
                        result = true;
                        break;
                    default:
                        break;
                }
            }
        }
        return result;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
        synchronized(this) {
            if (mState == BluetoothSap.STATE_CONNECTED && mRemoteDevice != null) {
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
                if (!BluetoothUuid.containsAnyUuid(featureUuids, SAP_UUIDS)) {
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
            if (getState() == BluetoothSap.STATE_CONNECTED && getRemoteDevice().equals(device)) {
                return BluetoothProfile.STATE_CONNECTED;
            } else {
                return BluetoothProfile.STATE_DISCONNECTED;
            }
        }
    }

    public boolean setPriority(BluetoothDevice device, int priority) {
        Settings.Global.putInt(getContentResolver(),
            Settings.Global.getBluetoothSapPriorityKey(device.getAddress()),
            priority);
        if (DEBUG) Log.d(TAG, "Saved priority " + device + " = " + priority);
        return true;
    }

    public int getPriority(BluetoothDevice device) {
        int priority = Settings.Global.getInt(getContentResolver(),
            Settings.Global.getBluetoothSapPriorityKey(device.getAddress()),
            BluetoothProfile.PRIORITY_UNDEFINED);
        return priority;
    }

    @Override
    protected IProfileServiceBinder initBinder() {
        return new SapBinder(this);
    }

    @Override
    protected boolean start() {
        Log.v(TAG, "start()");
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(USER_CONFIRM_TIMEOUT_ACTION);

        try {
            registerReceiver(mSapReceiver, filter);
            mIsRegistered = true;
        } catch (Exception e) {
            Log.w(TAG,"Unable to register sap receiver",e);
        }
        mInterrupted = false;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        // start RFCOMM listener
        mSessionStatusHandler.sendMessage(mSessionStatusHandler
                .obtainMessage(START_LISTENER));
        return true;
    }

    @Override
    protected boolean stop() {
        Log.v(TAG, "stop()");
        if (!mIsRegistered){
            Log.i(TAG, "Avoid unregister when receiver it is not registered");
            return true;
        }
        try {
            mIsRegistered = false;
            unregisterReceiver(mSapReceiver);
        } catch (Exception e) {
            Log.w(TAG,"Unable to unregister sap receiver",e);
        }
        setState(BluetoothSap.STATE_DISCONNECTED, BluetoothSap.RESULT_CANCELED);
        sendShutdownMessage();
        return true;
    }

    public boolean cleanup()  {
        setState(BluetoothSap.STATE_DISCONNECTED, BluetoothSap.RESULT_CANCELED);
        closeService();
        if (mSessionStatusHandler != null) {
            mSessionStatusHandler.removeCallbacksAndMessages(null);
        }
        return true;
    }

    private void setUserTimeoutAlarm(){
        if (DEBUG) Log.d(TAG, "setUserTimeOutAlarm()");
        cancelUserTimeoutAlarm();
        mRemoveTimeoutMsg = true;
        Intent timeoutIntent = new Intent(USER_CONFIRM_TIMEOUT_ACTION);
        PendingIntent pIntent = PendingIntent.getBroadcast(this, 0, timeoutIntent, 0);
        mAlarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()
                + USER_CONFIRM_TIMEOUT_VALUE,pIntent);
    }

    private void cancelUserTimeoutAlarm() {
        if (DEBUG) Log.d(TAG, "cancelUserTimeOutAlarm()");
        if (mAlarmManager == null) {
            mAlarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        }
        if (mRemoveTimeoutMsg) {
            Intent timeoutIntent = new Intent(USER_CONFIRM_TIMEOUT_ACTION);
            PendingIntent sender = PendingIntent.getBroadcast(this, 0, timeoutIntent, 0);
            mAlarmManager.cancel(sender);
            mRemoveTimeoutMsg = false;
        }
    }

    private void sendCancelUserConfirmationIntent(BluetoothDevice device) {
        Intent intent = new Intent(BluetoothDevice.ACTION_CONNECTION_ACCESS_CANCEL);
        intent.setPackage(getString(R.string.pairing_ui_package));
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                        BluetoothDevice.REQUEST_TYPE_SIM_ACCESS);
        sendBroadcast(intent, BLUETOOTH_PERM);
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
        removeSdpRecord();
        mSessionStatusHandler.removeCallbacksAndMessages(null);
        // Request release of all resources
        mSessionStatusHandler.obtainMessage(SHUTDOWN).sendToTarget();
    }

    private void sendConnectTimeoutMessage() {
        if (DEBUG) Log.d(TAG, "sendConnectTimeoutMessage()");
        if (mSessionStatusHandler != null) {
            Message msg = mSessionStatusHandler.obtainMessage(USER_TIMEOUT);
            msg.sendToTarget();
        } // Can only be null during shutdown
    }

    private SapBroadcastReceiver mSapReceiver = new SapBroadcastReceiver();

    private class SapBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (VERBOSE) Log.v(TAG, "onReceive");
            String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                                               BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_TURNING_OFF) {
                    if (DEBUG) Log.d(TAG, "STATE_TURNING_OFF");
                    sendShutdownMessage();
                } else if (state == BluetoothAdapter.STATE_ON) {
                    if (DEBUG) Log.d(TAG, "STATE_ON");
                    // start RFCOMM listener
                    mSessionStatusHandler.sendMessage(mSessionStatusHandler
                                  .obtainMessage(START_LISTENER));
                }
                return;
            }

            if (action.equals(BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY)) {
                Log.v(TAG, " - Received BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY");
                if (!mIsWaitingAuthorization) {
                    // this reply is not for us
                    return;
                }

                mIsWaitingAuthorization = false;

                if (intent.getIntExtra(BluetoothDevice.EXTRA_CONNECTION_ACCESS_RESULT,
                                       BluetoothDevice.CONNECTION_ACCESS_NO) ==
                    BluetoothDevice.CONNECTION_ACCESS_YES) {
                    //bluetooth connection accepted by user
                    if (intent.getBooleanExtra(BluetoothDevice.EXTRA_ALWAYS_ALLOWED, false)) {
                        boolean result = mRemoteDevice.setSimAccessPermission(
                                BluetoothDevice.ACCESS_ALLOWED);
                        if (VERBOSE) {
                            Log.v(TAG, "setSimAccessPermission(ACCESS_ALLOWED) result=" + result);
                        }
                    }
                    try {
                        if (mConnSocket != null) {
                            // start obex server and rfcomm connection
                            startSapServerSession();
                        } else {
                            stopSapServerSession();
                        }
                    } catch (IOException ex) {
                        Log.e(TAG, "Caught the error: ", ex);
                    }
                } else {
                    if (intent.getBooleanExtra(BluetoothDevice.EXTRA_ALWAYS_ALLOWED, false)) {
                        boolean result = mRemoteDevice.setSimAccessPermission(
                                BluetoothDevice.ACCESS_REJECTED);
                        if (VERBOSE) {
                            Log.v(TAG, "setSimAccessPermission(ACCESS_REJECTED) result="
                                    + result);
                        }
                    }
                    // Ensure proper cleanup, and prepare for new connect.
                    mSessionStatusHandler.sendEmptyMessage(MSG_SERVERSESSION_CLOSE);
                }
                return;
            }

            if (action.equals(USER_CONFIRM_TIMEOUT_ACTION)) {
                if (DEBUG) Log.d(TAG, "USER_CONFIRM_TIMEOUT_ACTION Received.");
                // send us self a message about the timeout.
                sendConnectTimeoutMessage();
                return;
            }

            if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED) && mIsWaitingAuthorization) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (mRemoteDevice == null || device == null) {
                    Log.i(TAG, "Unexpected error!");
                    return;
                }

                if (DEBUG) Log.d(TAG,"ACL disconnected for " + device);

                if (mRemoteDevice.equals(device)) {
                    if (mRemoveTimeoutMsg) {
                        // Send any pending timeout now, as ACL got disconnected.
                        mSessionStatusHandler.removeMessages(USER_TIMEOUT);
                        mSessionStatusHandler.obtainMessage(USER_TIMEOUT).sendToTarget();
                    }
                    setState(BluetoothSap.STATE_DISCONNECTED);
                    // Ensure proper cleanup, and prepare for new connect.
                    mSessionStatusHandler.sendEmptyMessage(MSG_SERVERSESSION_CLOSE);
                }
            }
        }
    };

    //Binder object: Must be static class or memory leak may occur
    /**
     * This class implements the IBluetoothSap interface - or actually it validates the
     * preconditions for calling the actual functionality in the SapService, and calls it.
     */
    private static class SapBinder extends IBluetoothSap.Stub
        implements IProfileServiceBinder {
        private SapService mService;

        private SapService getService() {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"call not allowed for non-active user");
                return null;
            }

            if (mService != null && mService.isAvailable() ) {
                mService.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
                return mService;
            }
            return null;
        }

        SapBinder(SapService service) {
            Log.v(TAG, "SapBinder()");
            mService = service;
        }

        public boolean cleanup()  {
            mService = null;
            return true;
        }

        public int getState() {
            Log.v(TAG, "getState()");
            SapService service = getService();
            if (service == null) return BluetoothSap.STATE_DISCONNECTED;
            return getService().getState();
        }

        public BluetoothDevice getClient() {
            Log.v(TAG, "getClient()");
            SapService service = getService();
            if (service == null) return null;
            Log.v(TAG, "getClient() - returning " + service.getRemoteDevice());
            return service.getRemoteDevice();
        }

        public boolean isConnected(BluetoothDevice device) {
            Log.v(TAG, "isConnected()");
            SapService service = getService();
            if (service == null) return false;
            return (service.getState() == BluetoothSap.STATE_CONNECTED
                    && service.getRemoteDevice().equals(device));
        }

        public boolean connect(BluetoothDevice device) {
            Log.v(TAG, "connect()");
            SapService service = getService();
            if (service == null) return false;
            return false;
        }

        public boolean disconnect(BluetoothDevice device) {
            Log.v(TAG, "disconnect()");
            SapService service = getService();
            if (service == null) return false;
            return service.disconnect(device);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            Log.v(TAG, "getConnectedDevices()");
            SapService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getConnectedDevices();
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            Log.v(TAG, "getDevicesMatchingConnectionStates()");
            SapService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getDevicesMatchingConnectionStates(states);
        }

        public int getConnectionState(BluetoothDevice device) {
            Log.v(TAG, "getConnectionState()");
            SapService service = getService();
            if (service == null) return BluetoothProfile.STATE_DISCONNECTED;
            return service.getConnectionState(device);
        }

        public boolean setPriority(BluetoothDevice device, int priority) {
            SapService service = getService();
            if (service == null) return false;
            return service.setPriority(device, priority);
        }

        public int getPriority(BluetoothDevice device) {
            SapService service = getService();
            if (service == null) return BluetoothProfile.PRIORITY_UNDEFINED;
            return service.getPriority(device);
        }
    }
}
