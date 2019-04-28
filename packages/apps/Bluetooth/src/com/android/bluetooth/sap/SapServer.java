package com.android.bluetooth.sap;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothSap;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.radio.V1_0.ISap;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.bluetooth.R;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;


/**
 * The SapServer uses two threads, one for reading messages from the RFCOMM socket and
 * one for writing the responses.
 * Incoming requests are decoded in the "main" SapServer, by the decoder build into SapMEssage.
 * The relevant RIL calls are made from the message handler thread through the rild-bt socket.
 * The RIL replies are read in the SapRilReceiver, and passed to the SapServer message handler
 * to be written to the RFCOMM socket.
 * All writes to the RFCOMM and rild-bt socket must be synchronized, hence to write e.g. an error
 * response, send a message to the Sap Handler thread. (There are helper functions to do this)
 * Communication to the RIL is through an intent, and a BroadcastReceiver.
 */
public class SapServer extends Thread implements Callback {
    private static final String TAG = "SapServer";
    private static final String TAG_HANDLER = "SapServerHandler";
    public static final boolean DEBUG = SapService.DEBUG;
    public static final boolean VERBOSE = SapService.VERBOSE;

    private enum SAP_STATE    {
        DISCONNECTED, CONNECTING, CONNECTING_CALL_ONGOING, CONNECTED,
        CONNECTED_BUSY, DISCONNECTING;
    }

    private SAP_STATE mState = SAP_STATE.DISCONNECTED;

    private Context mContext = null;
    /* RFCOMM socket I/O streams */
    private BufferedOutputStream mRfcommOut = null;
    private BufferedInputStream mRfcommIn = null;
    /* References to the SapRilReceiver object */
    private SapRilReceiver mRilBtReceiver = null;
    /* The message handler members */
    private Handler mSapHandler = null;
    private HandlerThread mHandlerThread = null;
    /* Reference to the SAP service - which created this instance of the SAP server */
    private Handler mSapServiceHandler = null;

    /* flag for when user forces disconnect of rfcomm */
    private boolean mIsLocalInitDisconnect = false;
    private CountDownLatch mDeinitSignal = new CountDownLatch(1);

    /* Message ID's handled by the message handler */
    public static final int SAP_MSG_RFC_REPLY =   0x00;
    public static final int SAP_MSG_RIL_CONNECT = 0x01;
    public static final int SAP_MSG_RIL_REQ =     0x02;
    public static final int SAP_MSG_RIL_IND =     0x03;
    public static final int SAP_RIL_SOCK_CLOSED = 0x04;
    public static final int SAP_PROXY_DEAD = 0x05;

    public static final String SAP_DISCONNECT_ACTION =
            "com.android.bluetooth.sap.action.DISCONNECT_ACTION";
    public static final String SAP_DISCONNECT_TYPE_EXTRA =
            "com.android.bluetooth.sap.extra.DISCONNECT_TYPE";
    public static final int NOTIFICATION_ID = android.R.drawable.stat_sys_data_bluetooth;
    private static final String SAP_NOTIFICATION_CHANNEL = "sap_notification_channel";
    public static final int ISAP_GET_SERVICE_DELAY_MILLIS = 3 * 1000;
    private static final int DISCONNECT_TIMEOUT_IMMEDIATE = 5000; /* ms */
    private static final int DISCONNECT_TIMEOUT_RFCOMM = 2000; /* ms */
    private PendingIntent pDiscIntent = null; // Holds a reference to disconnect timeout intents

    /* We store the mMaxMessageSize, as we need a copy of it when the init. sequence completes */
    private int mMaxMsgSize = 0;
    /* keep track of the current RIL test mode */
    private int mTestMode = SapMessage.INVALID_VALUE; // used to set the RIL in test mode

    /**
     * SapServer constructor
     * @param serviceHandler The handler to send a SapService.MSG_SERVERSESSION_CLOSE when closing
     * @param inStream The socket input stream
     * @param outStream The socket output stream
     */
    public SapServer(Handler serviceHandler, Context context, InputStream inStream,
            OutputStream outStream) {
        mContext = context;
        mSapServiceHandler = serviceHandler;

        /* Open in- and output streams */
        mRfcommIn = new BufferedInputStream(inStream);
        mRfcommOut = new BufferedOutputStream(outStream);

        /* Register for phone state change and the RIL cfm message */
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction(SAP_DISCONNECT_ACTION);
        mIntentReceiver = new SapServerBroadcastReceiver();
        mContext.registerReceiver(mIntentReceiver, filter);
    }

    /**
     * This handles the response from RIL.
     */
    private BroadcastReceiver mIntentReceiver;
    private class SapServerBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                if(VERBOSE) Log.i(TAG, "ACTION_PHONE_STATE_CHANGED intent received in state "
                                        + mState.name()
                                        + "PhoneState: "
                                        + intent.getStringExtra(TelephonyManager.EXTRA_STATE));
                if(mState == SAP_STATE.CONNECTING_CALL_ONGOING) {
                    String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                    if(state != null) {
                        if(state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                            if(DEBUG) Log.i(TAG, "sending RIL.ACTION_RIL_RECONNECT_OFF_REQ intent");
                            SapMessage fakeConReq = new SapMessage(SapMessage.ID_CONNECT_REQ);
                            fakeConReq.setMaxMsgSize(mMaxMsgSize);
                            onConnectRequest(fakeConReq);
                        }
                    }
                }
            } else if (intent.getAction().equals(SAP_DISCONNECT_ACTION)) {
                int disconnectType = intent.getIntExtra(SapServer.SAP_DISCONNECT_TYPE_EXTRA,
                        SapMessage.DISC_GRACEFULL);
                Log.v(TAG, " - Received SAP_DISCONNECT_ACTION type: " + disconnectType);

                if(disconnectType == SapMessage.DISC_RFCOMM) {
                    // At timeout we need to close the RFCOMM socket to complete shutdown
                    shutdown();
                } else if (mState != SAP_STATE.DISCONNECTED && mState != SAP_STATE.DISCONNECTING) {
                    // The user pressed disconnect - initiate disconnect sequence.
                    sendDisconnectInd(disconnectType);
                }
            } else {
                Log.w(TAG, "RIL-BT received unexpected Intent: " + intent.getAction());
            }
        }
    }

    /**
     * Set RIL driver in test mode - only possible if SapMessage is build with TEST == true
     * The value set by this function will take effect at the next connect request received
     * in DISCONNECTED state.
     * @param testMode Use SapMessage.TEST_MODE_XXX
     */
    public void setTestMode(int testMode) {
        if(SapMessage.TEST) {
            mTestMode = testMode;
        }
    }

    private void sendDisconnectInd(int discType) {
        if(VERBOSE) Log.v(TAG, "in sendDisconnectInd()");

        if(discType != SapMessage.DISC_FORCED){
            if(VERBOSE) Log.d(TAG, "Sending  disconnect ("+discType+") indication to client");
            /* Send disconnect to client */
            SapMessage discInd = new SapMessage(SapMessage.ID_DISCONNECT_IND);
            discInd.setDisconnectionType(discType);
            sendClientMessage(discInd);

            /* Handle local disconnect procedures */
            if (discType == SapMessage.DISC_GRACEFULL)
            {
                /* Update the notification to allow the user to initiate a force disconnect */
                setNotification(SapMessage.DISC_IMMEDIATE, PendingIntent.FLAG_CANCEL_CURRENT);

            } else if (discType == SapMessage.DISC_IMMEDIATE){
                /* Request an immediate disconnect, but start a timer to force disconnect if the
                 * client do not obey our request. */
                startDisconnectTimer(SapMessage.DISC_FORCED, DISCONNECT_TIMEOUT_IMMEDIATE);
            }

        } else {
            SapMessage msg = new SapMessage(SapMessage.ID_DISCONNECT_REQ);
            /* Force disconnect of RFCOMM - but first we need to clean up. */
            clearPendingRilResponses(msg);

            /* We simply need to forward to RIL, but not change state to busy - hence send and set
               message to null. */
            changeState(SAP_STATE.DISCONNECTING);
            sendRilThreadMessage(msg);
            mIsLocalInitDisconnect = true;
        }
    }

    void setNotification(int type, int flags)
    {
        String title, text, button, ticker;
        Notification notification;
        NotificationManager notificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel notificationChannel = new NotificationChannel(SAP_NOTIFICATION_CHANNEL,
                mContext.getString(R.string.bluetooth_sap_notif_title),
                NotificationManager.IMPORTANCE_HIGH);
        notificationManager.createNotificationChannel(notificationChannel);
        if(VERBOSE) Log.i(TAG, "setNotification type: " + type);
        /* For PTS TC_SERVER_DCN_BV_03_I we need to expose the option to send immediate disconnect
         * without first sending a graceful disconnect.
         * To enable this option set
         * bt.sap.pts="true" */
        String pts_enabled = SystemProperties.get("bt.sap.pts");
        Boolean pts_test = Boolean.parseBoolean(pts_enabled);

        /* put notification up for the user to be able to disconnect from the client*/
        Intent sapDisconnectIntent = new Intent(SapServer.SAP_DISCONNECT_ACTION);
        if (type == SapMessage.DISC_GRACEFULL) {
            title = mContext.getString(R.string.bluetooth_sap_notif_title);
            button = mContext.getString(R.string.bluetooth_sap_notif_disconnect_button);
            text = mContext.getString(R.string.bluetooth_sap_notif_message);
            ticker = mContext.getString(R.string.bluetooth_sap_notif_ticker);
        } else {
            title = mContext.getString(R.string.bluetooth_sap_notif_title);
            button = mContext.getString(R.string.bluetooth_sap_notif_force_disconnect_button);
            text = mContext.getString(R.string.bluetooth_sap_notif_disconnecting);
            ticker = mContext.getString(R.string.bluetooth_sap_notif_ticker);
        }
        if (!pts_test) {
            sapDisconnectIntent.putExtra(SapServer.SAP_DISCONNECT_TYPE_EXTRA, type);
            PendingIntent pIntentDisconnect = PendingIntent.getBroadcast(mContext, type,
                    sapDisconnectIntent,flags);
            notification = new Notification.Builder(mContext, SAP_NOTIFICATION_CHANNEL)
                                   .setOngoing(true)
                                   .addAction(android.R.drawable.stat_sys_data_bluetooth, button,
                                           pIntentDisconnect)
                                   .setContentTitle(title)
                                   .setTicker(ticker)
                                   .setContentText(text)
                                   .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                                   .setAutoCancel(false)
                                   .setPriority(Notification.PRIORITY_MAX)
                                   .setOnlyAlertOnce(true)
                                   .build();
        } else {
            sapDisconnectIntent.putExtra(SapServer.SAP_DISCONNECT_TYPE_EXTRA,
                    SapMessage.DISC_GRACEFULL);
            Intent sapForceDisconnectIntent = new Intent(SapServer.SAP_DISCONNECT_ACTION);
            sapForceDisconnectIntent.putExtra(SapServer.SAP_DISCONNECT_TYPE_EXTRA,
                    SapMessage.DISC_IMMEDIATE);
            PendingIntent pIntentDisconnect = PendingIntent.getBroadcast(mContext,
                    SapMessage.DISC_GRACEFULL, sapDisconnectIntent,flags);
            PendingIntent pIntentForceDisconnect = PendingIntent.getBroadcast(mContext,
                    SapMessage.DISC_IMMEDIATE, sapForceDisconnectIntent,flags);
            notification =
                    new Notification.Builder(mContext, SAP_NOTIFICATION_CHANNEL)
                            .setOngoing(true)
                            .addAction(android.R.drawable.stat_sys_data_bluetooth,
                                    mContext.getString(
                                            R.string.bluetooth_sap_notif_disconnect_button),
                                    pIntentDisconnect)
                            .addAction(android.R.drawable.stat_sys_data_bluetooth,
                                    mContext.getString(
                                            R.string.bluetooth_sap_notif_force_disconnect_button),
                                    pIntentForceDisconnect)
                            .setContentTitle(title)
                            .setTicker(ticker)
                            .setContentText(text)
                            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                            .setAutoCancel(false)
                            .setPriority(Notification.PRIORITY_MAX)
                            .setOnlyAlertOnce(true)
                            .build();
        }

        // cannot be set with the builder
        notification.flags |= Notification.FLAG_NO_CLEAR |Notification.FLAG_ONLY_ALERT_ONCE;

        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    void clearNotification() {
        NotificationManager notificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(SapServer.NOTIFICATION_ID);
    }

    /**
     * The SapServer RFCOMM reader thread. Sets up the handler thread and handle
     * all read from the RFCOMM in-socket. This thread also handle writes to the RIL socket.
     */
    @Override
    public void run() {
        try {
            /* SAP is not time critical, hence lowering priority to ensure critical tasks are
             * executed in a timely manner. */
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

            /* Start the SAP message handler thread */
            mHandlerThread = new HandlerThread("SapServerHandler",
                    android.os.Process.THREAD_PRIORITY_BACKGROUND);
            mHandlerThread.start();

            // This will return when the looper is ready
            Looper sapLooper = mHandlerThread.getLooper();
            mSapHandler = new Handler(sapLooper, this);

            mRilBtReceiver = new SapRilReceiver(mSapHandler, mSapServiceHandler);
            boolean done = false;
            while (!done) {
                if(VERBOSE) Log.i(TAG, "Waiting for incomming RFCOMM message...");
                int requestType = mRfcommIn.read();
                if (VERBOSE) Log.i(TAG, "RFCOMM message read...");
                if(requestType == -1) {
                    if (VERBOSE) Log.i(TAG, "requestType == -1");
                    done = true; // EOF reached
                } else {
                    if (VERBOSE) Log.i(TAG, "requestType != -1");
                    SapMessage msg = SapMessage.readMessage(requestType, mRfcommIn);
                    /* notify about an incoming message from the BT Client */
                    SapService.notifyUpdateWakeLock(mSapServiceHandler);
                    if(msg != null && mState != SAP_STATE.DISCONNECTING)
                    {
                        switch (requestType) {
                        case SapMessage.ID_CONNECT_REQ:
                            if(VERBOSE) Log.d(TAG, "CONNECT_REQ - MaxMsgSize: "
                                    + msg.getMaxMsgSize());
                            onConnectRequest(msg);
                            msg = null; /* don't send ril connect yet */
                            break;
                        case SapMessage.ID_DISCONNECT_REQ: /* No params */
                            /*
                             * 1) send RIL_REQUEST_SIM_SAP_DISCONNECT
                             *      (block for all incoming requests, as they are not
                             *       allowed, don't even send an error_resp)
                             * 2) on response disconnect ril socket.
                             * 3) when disconnected send RIL.ACTION_RIL_RECONNECT_OFF_REQ
                             * 4) on RIL.ACTION_RIL_RECONNECT_CFM
                             *       send SAP_DISCONNECT_RESP to client.
                             * 5) Start RFCOMM disconnect timer
                             * 6.a) on rfcomm disconnect:
                             *       cancel timer and initiate cleanup
                             * 6.b) on rfcomm disc. timeout:
                             *       close socket-streams and initiate cleanup */
                            if(VERBOSE) Log.d(TAG, "DISCONNECT_REQ");

                            if (mState ==  SAP_STATE.CONNECTING_CALL_ONGOING) {
                                Log.d(TAG, "disconnect received when call was ongoing, " +
                                     "send disconnect response");
                                changeState(SAP_STATE.DISCONNECTING);
                                SapMessage reply = new SapMessage(SapMessage.ID_DISCONNECT_RESP);
                                sendClientMessage(reply);
                            } else {
                                clearPendingRilResponses(msg);
                                changeState(SAP_STATE.DISCONNECTING);
                                sendRilThreadMessage(msg);
                                /*cancel the timer for the hard-disconnect intent*/
                                stopDisconnectTimer();
                            }
                            msg = null; // No message needs to be sent to RIL
                            break;
                        case SapMessage.ID_POWER_SIM_OFF_REQ: // Fall through
                        case SapMessage.ID_RESET_SIM_REQ:
                            /* Forward these to the RIL regardless of the state, and clear any
                             * pending resp */
                            clearPendingRilResponses(msg);
                            break;
                        case SapMessage.ID_SET_TRANSPORT_PROTOCOL_REQ:
                            /* The RIL might support more protocols that specified in the SAP,
                             * allow only the valid values. */
                            if(mState == SAP_STATE.CONNECTED
                                    && msg.getTransportProtocol() != 0
                                    && msg.getTransportProtocol() != 1) {
                                Log.w(TAG, "Invalid TransportProtocol received:"
                                        + msg.getTransportProtocol());
                                // We shall only handle one request at the time, hence return error
                                SapMessage errorReply = new SapMessage(SapMessage.ID_ERROR_RESP);
                                sendClientMessage(errorReply);
                                msg = null;
                            }
                            // Fall through
                        default:
                            /* Remaining cases just needs to be forwarded to the RIL unless we are
                             * in busy state. */
                            if(mState != SAP_STATE.CONNECTED) {
                                Log.w(TAG, "Message received in STATE != CONNECTED - state = "
                                        + mState.name());
                                // We shall only handle one request at the time, hence return error
                                SapMessage errorReply = new SapMessage(SapMessage.ID_ERROR_RESP);
                                sendClientMessage(errorReply);
                                msg = null;
                            }
                        }

                        if(msg != null && msg.getSendToRil() == true) {
                            changeState(SAP_STATE.CONNECTED_BUSY);
                            sendRilThreadMessage(msg);
                        }

                    } else {
                        //An unknown message or in disconnecting state - send error indication
                        Log.e(TAG, "Unable to parse message.");
                        SapMessage atrReply = new SapMessage(SapMessage.ID_ERROR_RESP);
                        sendClientMessage(atrReply);
                    }
                }
            } // end while
        } catch (NullPointerException e) {
            Log.w(TAG, e);
        } catch (IOException e) {
            /* This is expected during shutdown */
            Log.i(TAG, "IOException received, this is probably a shutdown signal, cleaning up...");
        } catch (Exception e) {
            /* TODO: Change to the needed Exception types when done testing */
            Log.w(TAG, e);
        } finally {
            // Do cleanup even if an exception occurs
            stopDisconnectTimer();
            /* In case of e.g. a RFCOMM close while connected:
             *        - Initiate a FORCED shutdown
             *        - Wait for RIL deinit to complete
             */
            if (mState == SAP_STATE.CONNECTING_CALL_ONGOING) {
                /* Most likely remote device closed rfcomm, update state */
                changeState(SAP_STATE.DISCONNECTED);
            } else if (mState != SAP_STATE.DISCONNECTED) {
                if(mState != SAP_STATE.DISCONNECTING &&
                        mIsLocalInitDisconnect != true) {
                    sendDisconnectInd(SapMessage.DISC_FORCED);
                }
                if(DEBUG) Log.i(TAG, "Waiting for deinit to complete");
                try {
                    mDeinitSignal.await();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupt received while waitinf for de-init to complete", e);
                }
            }

            if(mIntentReceiver != null) {
                try {
                    mContext.unregisterReceiver(mIntentReceiver);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Receiver not registered.");
                }
                mIntentReceiver = null;
            }
            stopDisconnectTimer();
            clearNotification();

            if(mHandlerThread != null) try {
                mHandlerThread.quit();
                mHandlerThread.join();
                mHandlerThread = null;
            } catch (InterruptedException e) {}
            if (mRilBtReceiver != null) {
                mRilBtReceiver.resetSapProxy();
                mRilBtReceiver = null;
            }

            if(mRfcommIn != null) try {
                if(VERBOSE) Log.i(TAG, "Closing mRfcommIn...");
                mRfcommIn.close();
                mRfcommIn = null;
            } catch (IOException e) {}

            if(mRfcommOut != null) try {
                if(VERBOSE) Log.i(TAG, "Closing mRfcommOut...");
                mRfcommOut.close();
                mRfcommOut = null;
            } catch (IOException e) {}

            if (mSapServiceHandler != null) {
                Message msg = Message.obtain(mSapServiceHandler);
                msg.what = SapService.MSG_SERVERSESSION_CLOSE;
                msg.sendToTarget();
                if (DEBUG) Log.d(TAG, "MSG_SERVERSESSION_CLOSE sent out.");
            }
            Log.i(TAG, "All done exiting thread...");
        }
    }


    /**
     * This function needs to determine:
     *  - if the maxMsgSize is acceptable - else reply CON_STATUS_ERROR_MAX_MSG_SIZE_UNSUPPORTED
     *      + new maxMsgSize if too big
     *  - connect to the RIL-BT socket
     *  - if a call is ongoing reply CON_STATUS_OK_ONGOING_CALL.
     *  - if all ok, just respond CON_STATUS_OK.
     *
     * @param msg the incoming SapMessage
     */
    private void onConnectRequest(SapMessage msg) {
        SapMessage reply = new SapMessage(SapMessage.ID_CONNECT_RESP);

        if(mState == SAP_STATE.CONNECTING) {
            /* A connect request might have been rejected because of maxMessageSize negotiation, and
             * this is a new connect request. Simply forward to RIL, and stay in connecting state.
             * */
            reply = null;
            sendRilMessage(msg);
            stopDisconnectTimer();

        } else if(mState != SAP_STATE.DISCONNECTED && mState != SAP_STATE.CONNECTING_CALL_ONGOING) {
            reply.setConnectionStatus(SapMessage.CON_STATUS_ERROR_CONNECTION);
        } else {
            // Store the MaxMsgSize for future use
            mMaxMsgSize = msg.getMaxMsgSize();
            // All parameters OK, examine if a call is ongoing and start the RIL-BT listener thread
            if (isCallOngoing() == true) {
                /* If a call is ongoing we set the state, inform the SAP client and wait for a state
                 * change intent from the TelephonyManager with state IDLE. */
                reply.setConnectionStatus(SapMessage.CON_STATUS_OK_ONGOING_CALL);
            } else {
                /* no call is ongoing, initiate the connect sequence:
                 *  1) Start the SapRilReceiver thread (open the rild-bt socket)
                 *  2) Send a RIL_SIM_SAP_CONNECT request to RILD
                 *  3) Send a RIL_SIM_RESET request and a connect confirm to the SAP client */
                changeState(SAP_STATE.CONNECTING);
                if (mRilBtReceiver != null) {
                    // Notify the SapServer that we have connected to the SAP service
                    mRilBtReceiver.sendRilConnectMessage();
                    // Don't send reply yet
                    reply = null;
                } else {
                    reply = new SapMessage(SapMessage.ID_CONNECT_RESP);
                    reply.setConnectionStatus(SapMessage.CON_STATUS_ERROR_CONNECTION);
                    sendClientMessage(reply);
                }
            }
        }
        if(reply != null)
            sendClientMessage(reply);
    }

    private void clearPendingRilResponses(SapMessage msg) {
        if(mState == SAP_STATE.CONNECTED_BUSY) {
            msg.setClearRilQueue(true);
        }
    }
    /**
     * Send RFCOMM message to the Sap Server Handler Thread
     * @param sapMsg The message to send
     */
    private void sendClientMessage(SapMessage sapMsg) {
        Message newMsg = mSapHandler.obtainMessage(SAP_MSG_RFC_REPLY, sapMsg);
        mSapHandler.sendMessage(newMsg);
    }

    /**
     * Send a RIL message to the SapServer message handler thread
     * @param sapMsg
     */
    private void sendRilThreadMessage(SapMessage sapMsg) {
        Message newMsg = mSapHandler.obtainMessage(SAP_MSG_RIL_REQ, sapMsg);
        mSapHandler.sendMessage(newMsg);
    }

    /**
     * Examine if a call is ongoing, by asking the telephony manager
     * @return false if the phone is IDLE (can be used for SAP), true otherwise.
     */
    private boolean isCallOngoing() {
        TelephonyManager tManager =
                (TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if(tManager.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
            return false;
        }
        return true;
    }

    /**
     * Change the SAP Server state.
     * We add thread protection, as we access the state from two threads.
     * @param newState
     */
    private void changeState(SAP_STATE newState) {
        if(DEBUG) Log.i(TAG_HANDLER,"Changing state from " + mState.name() +
                                        " to " + newState.name());
        synchronized (this) {
            mState = newState;
        }
    }

    /*************************************************************************
     * SAP Server Message Handler Thread Functions
     *************************************************************************/

    /**
     * The SapServer message handler thread implements the SAP state machine.
     *  - Handle all outgoing communication to the out-socket. Either replies from the RIL or direct
     *    messages send from the SapServe (e.g. connect_resp).
     *  - Handle all outgoing communication to the RIL-BT socket.
     *  - Handle all replies from the RIL
     */
    @Override
    public boolean handleMessage(Message msg) {
        if(VERBOSE) Log.i(TAG_HANDLER,"Handling message (ID: " + msg.what + "): "
                + getMessageName(msg.what));

        SapMessage sapMsg = null;

        switch(msg.what) {
        case SAP_MSG_RFC_REPLY:
            sapMsg = (SapMessage) msg.obj;
            handleRfcommReply(sapMsg);
            break;
        case SAP_MSG_RIL_CONNECT:
            /* The connection to rild-bt have been established. Store the outStream handle
             * and send the connect request. */
            if(mTestMode != SapMessage.INVALID_VALUE) {
                SapMessage rilTestModeReq = new SapMessage(SapMessage.ID_RIL_SIM_ACCESS_TEST_REQ);
                rilTestModeReq.setTestMode(mTestMode);
                sendRilMessage(rilTestModeReq);
                mTestMode = SapMessage.INVALID_VALUE;
            }
            SapMessage rilSapConnect = new SapMessage(SapMessage.ID_CONNECT_REQ);
            rilSapConnect.setMaxMsgSize(mMaxMsgSize);
            sendRilMessage(rilSapConnect);
            break;
        case SAP_MSG_RIL_REQ:
            sapMsg = (SapMessage) msg.obj;
            if(sapMsg != null) {
                sendRilMessage(sapMsg);
            }
            break;
        case SAP_MSG_RIL_IND:
            sapMsg = (SapMessage) msg.obj;
            handleRilInd(sapMsg);
            break;
        case SAP_RIL_SOCK_CLOSED:
            /* The RIL socket was closed unexpectedly, send immediate disconnect indication
               - close RFCOMM after timeout if no response. */
            sendDisconnectInd(SapMessage.DISC_IMMEDIATE);
            startDisconnectTimer(SapMessage.DISC_RFCOMM, DISCONNECT_TIMEOUT_RFCOMM);
            break;
        case SAP_PROXY_DEAD:
            if ((long) msg.obj == mRilBtReceiver.mSapProxyCookie.get()) {
                mRilBtReceiver.notifyShutdown(); /* Only needed in case of a connection error */
                mRilBtReceiver.resetSapProxy();

                // todo: rild should be back up since message was sent with a delay. this is a hack.
                mRilBtReceiver.getSapProxy();
            }
        default:
            /* Message not handled */
            return false;
        }
        return true; // Message handles
    }

    /**
     * Close the in/out rfcomm streams, to trigger a shutdown of the SapServer main thread.
     * Use this after completing the deinit sequence.
     */
    private void shutdown() {

        if(DEBUG) Log.i(TAG_HANDLER, "in Shutdown()");
        try {
            if (mRfcommOut != null)
                mRfcommOut.close();
        } catch (IOException e) {}
        try {
            if (mRfcommIn != null)
                mRfcommIn.close();
        } catch (IOException e) {}
        mRfcommIn = null;
        mRfcommOut = null;
        stopDisconnectTimer();
        clearNotification();
    }

    private void startDisconnectTimer(int discType, int timeMs) {

        stopDisconnectTimer();
        synchronized (this) {
            Intent sapDisconnectIntent = new Intent(SapServer.SAP_DISCONNECT_ACTION);
            sapDisconnectIntent.putExtra(SAP_DISCONNECT_TYPE_EXTRA, discType);
            AlarmManager alarmManager =
                    (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
            pDiscIntent = PendingIntent.getBroadcast(mContext,
                                                    discType,
                                                    sapDisconnectIntent,
                                                    PendingIntent.FLAG_CANCEL_CURRENT);
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + timeMs, pDiscIntent);

            if(VERBOSE) Log.d(TAG_HANDLER, "Setting alarm for " + timeMs +
                    " ms to activate disconnect type " + discType);
        }
    }

    private void stopDisconnectTimer() {
        synchronized (this) {
            if(pDiscIntent != null)
            {
                AlarmManager alarmManager =
                        (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
                alarmManager.cancel(pDiscIntent);
                pDiscIntent.cancel();
                if(VERBOSE) {
                    Log.d(TAG_HANDLER, "Canceling disconnect alarm");
                }
                pDiscIntent = null;
            }
        }
    }

    /**
     * Here we handle the replies to the SAP client, normally forwarded directly from the RIL.
     * We do need to handle some of the messages in the SAP profile, hence we look at the messages
     * here before they go to the client
     * @param sapMsg the message to send to the SAP client
     */
    private void handleRfcommReply(SapMessage sapMsg) {
        if(sapMsg != null) {

            if(DEBUG) Log.i(TAG_HANDLER, "handleRfcommReply() handling "
                    + SapMessage.getMsgTypeName(sapMsg.getMsgType()));

            switch(sapMsg.getMsgType()) {

                case SapMessage.ID_CONNECT_RESP:
                    if(mState == SAP_STATE.CONNECTING_CALL_ONGOING) {
                        /* Hold back the connect resp if a call was ongoing when the connect req
                         * was received.
                         * A response with status call-ongoing was sent, and the connect response
                         * received from the RIL when call ends must be discarded.
                         */
                        if (sapMsg.getConnectionStatus() == SapMessage.CON_STATUS_OK) {
                            // This is successful connect response from RIL/modem.
                            changeState(SAP_STATE.CONNECTED);
                        }
                        if(VERBOSE) Log.i(TAG, "Hold back the connect resp, as a call was ongoing" +
                                " when the initial response were sent.");
                        sapMsg = null;
                    } else if (sapMsg.getConnectionStatus() == SapMessage.CON_STATUS_OK) {
                        // This is successful connect response from RIL/modem.
                        changeState(SAP_STATE.CONNECTED);
                    } else if(sapMsg.getConnectionStatus() ==
                            SapMessage.CON_STATUS_OK_ONGOING_CALL) {
                        changeState(SAP_STATE.CONNECTING_CALL_ONGOING);
                    } else if(sapMsg.getConnectionStatus() != SapMessage.CON_STATUS_OK) {
                        /* Most likely the peer will try to connect again, hence we keep the
                         * connection to RIL open and stay in connecting state.
                         *
                         * Start timer to do shutdown if a new connect request is not received in
                         * time. */
                        startDisconnectTimer(SapMessage.DISC_FORCED, DISCONNECT_TIMEOUT_RFCOMM);
                    }
                    break;
                case SapMessage.ID_DISCONNECT_RESP:
                    if(mState == SAP_STATE.DISCONNECTING) {
                        /* Close the RIL-BT output Stream and signal to SapRilReceiver to close
                         * down the input stream. */
                        if(DEBUG) Log.i(TAG, "ID_DISCONNECT_RESP received in SAP_STATE." +
                                "DISCONNECTING.");

                        /* Send the disconnect resp, and wait for the client to close the Rfcomm,
                         * but start a timeout timer, just to be sure. Use alarm, to ensure we wake
                         * the host to close the connection to minimize power consumption. */
                        SapMessage disconnectResp = new SapMessage(SapMessage.ID_DISCONNECT_RESP);
                        changeState(SAP_STATE.DISCONNECTED);
                        sapMsg = disconnectResp;
                        startDisconnectTimer(SapMessage.DISC_RFCOMM, DISCONNECT_TIMEOUT_RFCOMM);
                        mDeinitSignal.countDown(); /* Signal deinit complete */
                    } else { /* DISCONNECTED */
                        mDeinitSignal.countDown(); /* Signal deinit complete */
                        if(mIsLocalInitDisconnect == true) {
                            if(VERBOSE) Log.i(TAG_HANDLER, "This is a FORCED disconnect.");
                            /* We needed to force the disconnect, hence no hope for the client to
                             * close the RFCOMM connection, hence we do it here. */
                            shutdown();
                            sapMsg = null;
                        } else {
                            /* The client must disconnect the RFCOMM, but in case it does not, we
                             * need to do it.
                             * We start an alarm, and if it triggers, we must send the
                             * MSG_SERVERSESSION_CLOSE */
                            if(VERBOSE) Log.i(TAG_HANDLER, "This is a NORMAL disconnect.");
                            startDisconnectTimer(SapMessage.DISC_RFCOMM, DISCONNECT_TIMEOUT_RFCOMM);
                        }
                    }
                    break;
                case SapMessage.ID_STATUS_IND:
                    /* Some car-kits only "likes" status indication when connected, hence discard
                     * any arriving outside this state */
                    if(mState == SAP_STATE.DISCONNECTED ||
                            mState == SAP_STATE.CONNECTING ||
                            mState == SAP_STATE.DISCONNECTING) {
                        sapMsg = null;
                    }
                    if (mSapServiceHandler != null && mState == SAP_STATE.CONNECTED) {
                        Message msg = Message.obtain(mSapServiceHandler);
                        msg.what = SapService.MSG_CHANGE_STATE;
                        msg.arg1 = BluetoothSap.STATE_CONNECTED;
                        msg.sendToTarget();
                        setNotification(SapMessage.DISC_GRACEFULL, 0);
                        if (DEBUG) Log.d(TAG, "MSG_CHANGE_STATE sent out.");
                    }
                    break;
                default:
                // Nothing special, just send the message
            }
        }

        /* Update state variable based on the number of pending commands. We are only able to
         * handle one request at the time, except from disconnect, sim off and sim reset.
         * Hence if one of these are received while in busy state, we might have a crossing
         * response, hence we must stay in BUSY state if we have an ongoing RIL request. */
        if(mState == SAP_STATE.CONNECTED_BUSY) {
            if(SapMessage.getNumPendingRilMessages() == 0) {
                changeState(SAP_STATE.CONNECTED);
            }
        }

        // This is the default case - just send the message to the SAP client.
        if(sapMsg != null)
            sendReply(sapMsg);
    }

    private void handleRilInd(SapMessage sapMsg) {
        if(sapMsg == null)
            return;

        switch(sapMsg.getMsgType()) {
        case SapMessage.ID_DISCONNECT_IND:
        {
            if(mState != SAP_STATE.DISCONNECTED && mState != SAP_STATE.DISCONNECTING){
                /* we only send disconnect indication to the client if we are actually connected*/
                SapMessage reply = new SapMessage(SapMessage.ID_DISCONNECT_IND);
                reply.setDisconnectionType(sapMsg.getDisconnectionType()) ;
                sendClientMessage(reply);
            } else {
                /* TODO: This was introduced to handle disconnect indication from RIL */
                sendDisconnectInd(sapMsg.getDisconnectionType());
            }
            break;
        }

        default:
            if(DEBUG) Log.w(TAG_HANDLER,"Unhandled message - type: "
                    + SapMessage.getMsgTypeName(sapMsg.getMsgType()));
        }
    }

    /**
     * This is only to be called from the handlerThread, else use sendRilThreadMessage();
     * @param sapMsg
     */
    private void sendRilMessage(SapMessage sapMsg) {
        if(VERBOSE) Log.i(TAG_HANDLER, "sendRilMessage() - "
                + SapMessage.getMsgTypeName(sapMsg.getMsgType()));

        Log.d(TAG_HANDLER, "sendRilMessage: calling getSapProxy");
        synchronized (mRilBtReceiver.getSapProxyLock()) {
            ISap sapProxy = mRilBtReceiver.getSapProxy();
            if (sapProxy == null) {
                Log.e(TAG_HANDLER,
                        "sendRilMessage: Unable to send message to RIL; sapProxy is null");
                sendClientMessage(new SapMessage(SapMessage.ID_ERROR_RESP));
                return;
            }

            try {
                sapMsg.send(sapProxy);
                if (VERBOSE) {
                    Log.d(TAG_HANDLER, "sendRilMessage: sapMsg.callISapReq called successfully");
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG_HANDLER, "sendRilMessage: IllegalArgumentException", e);
                sendClientMessage(new SapMessage(SapMessage.ID_ERROR_RESP));
            } catch (RemoteException | RuntimeException e) {
                Log.e(TAG_HANDLER, "sendRilMessage: Unable to send message to RIL: " + e);
                sendClientMessage(new SapMessage(SapMessage.ID_ERROR_RESP));
                mRilBtReceiver.notifyShutdown(); /* Only needed in case of a connection error */
                mRilBtReceiver.resetSapProxy();
            }
        }
    }

    /**
     * Only call this from the sapHandler thread.
     */
    private void sendReply(SapMessage msg) {
        if(VERBOSE) Log.i(TAG_HANDLER, "sendReply() RFCOMM - "
                + SapMessage.getMsgTypeName(msg.getMsgType()));
        if(mRfcommOut != null) { // Needed to handle brutal shutdown from car-kit and out of range
            try {
                msg.write(mRfcommOut);
                mRfcommOut.flush();
            } catch (IOException e) {
                Log.w(TAG_HANDLER, e);
                /* As we cannot write to the rfcomm channel we are disconnected.
                   Shutdown and prepare for a new connect. */
            }
        }
    }

    private static String getMessageName(int messageId) {
        switch (messageId) {
        case SAP_MSG_RFC_REPLY:
            return "SAP_MSG_REPLY";
        case SAP_MSG_RIL_CONNECT:
            return "SAP_MSG_RIL_CONNECT";
        case SAP_MSG_RIL_REQ:
            return "SAP_MSG_RIL_REQ";
        case SAP_MSG_RIL_IND:
            return "SAP_MSG_RIL_IND";
        default:
            return "Unknown message ID";
        }
    }

}
