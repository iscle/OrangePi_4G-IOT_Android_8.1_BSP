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

package com.android.tv.settings.connectivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.android.tv.settings.connectivity.setup.MessageWizardFragment;

import java.lang.ref.WeakReference;

/**
 * Connects to the wifi network specified by the given configuration.
 */
public class ConnectToWifiFragment extends MessageWizardFragment
        implements ConnectivityListener.Listener {

    public interface Listener {
        void onConnectToWifiCompleted(int reason);
    }

    private static final String TAG = "ConnectToWifiFragment";
    private static final boolean DEBUG = false;

    public static final int RESULT_SUCCESS = 0;
    public static final int RESULT_UNKNOWN_ERROR= 1;
    public static final int RESULT_TIMEOUT = 2;
    public static final int RESULT_BAD_AUTHENTICATION = 3;
    public static final int RESULT_REJECTED_BY_AP = 4;

    private static final String EXTRA_CONFIGURATION = "configuration";
    private static final int MSG_TIMEOUT = 1;
    private static final int CONNECTION_TIMEOUT = 15000;

    public static ConnectToWifiFragment newInstance(String title, boolean showProgressIndicator,
            WifiConfiguration configuration) {
        ConnectToWifiFragment fragment = new ConnectToWifiFragment();
        Bundle args = new Bundle();
        args.putParcelable(EXTRA_CONFIGURATION, configuration);
        addArguments(args, title, showProgressIndicator);
        fragment.setArguments(args);
        return fragment;
    }

    private Listener mListener;
    private ConnectivityListener mConnectivityListener;
    private WifiConfiguration mWifiConfiguration;
    private WifiManager mWifiManager;
    private Handler mHandler;
    private BroadcastReceiver mReceiver;
    private boolean mWasAssociating;
    private boolean mWasAssociated;
    private boolean mWasHandshaking;
    private boolean mConnected;

    @Override
    public void onAttach(Context activity) {
        if (activity instanceof Listener) {
            mListener = (Listener) activity;
        } else {
            throw new IllegalArgumentException("Activity must implement "
                    + "ConnectToWifiFragment.Listener to use this fragment.");
        }
        super.onAttach(activity);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private static class MessageHandler extends Handler {

        private final WeakReference<ConnectToWifiFragment> mFragmentRef;

        public MessageHandler(ConnectToWifiFragment fragment) {
            mFragmentRef = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            if (DEBUG) Log.d(TAG, "Timeout waiting on supplicant state change");

            final ConnectToWifiFragment fragment = mFragmentRef.get();
            if (fragment == null) {
                return;
            }

            if (fragment.isNetworkConnected()) {
                if (DEBUG) Log.d(TAG, "Fake timeout; we're actually connected");
                fragment.mConnected = true;
                fragment.notifyListener(RESULT_SUCCESS);
            } else {
                if (DEBUG) Log.d(TAG, "Timeout is real; telling the listener");
                fragment.notifyListener(RESULT_TIMEOUT);
            }
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mConnectivityListener = new ConnectivityListener(getActivity(), this);
        mWifiConfiguration = getArguments().getParcelable(EXTRA_CONFIGURATION);
        mWifiManager = ((WifiManager) getActivity().getSystemService(Context.WIFI_SERVICE));
        mHandler = new MessageHandler(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                    SupplicantState state = intent.getParcelableExtra(
                            WifiManager.EXTRA_NEW_STATE);
                    if (DEBUG) {
                        Log.d(TAG, "Got supplicant state: " + state.name());
                    }
                    switch (state) {
                        case ASSOCIATING:
                            mWasAssociating = true;
                            break;
                        case ASSOCIATED:
                            mWasAssociated = true;
                            break;
                        case COMPLETED:
                            // this just means the supplicant has connected, now
                            // we wait for the rest of the framework to catch up
                            break;
                        case DISCONNECTED:
                        case DORMANT:
                            if (mWasAssociated || mWasHandshaking) {
                                notifyListener(mWasHandshaking ? RESULT_BAD_AUTHENTICATION
                                        : RESULT_UNKNOWN_ERROR);
                            }
                            break;
                        case INTERFACE_DISABLED:
                        case UNINITIALIZED:
                            notifyListener(RESULT_UNKNOWN_ERROR);
                            break;
                        case FOUR_WAY_HANDSHAKE:
                        case GROUP_HANDSHAKE:
                            mWasHandshaking = true;
                            break;
                        case INACTIVE:
                            if (mWasAssociating && !mWasAssociated) {
                                // If we go inactive after 'associating' without ever having
                                // been 'associated', the AP(s) must have rejected us.
                                notifyListener(RESULT_REJECTED_BY_AP);
                                break;
                            }
                        case INVALID:
                        case SCANNING:
                        default:
                            return;
                    }
                    mHandler.removeMessages(MSG_TIMEOUT);
                    mHandler.sendEmptyMessageDelayed(MSG_TIMEOUT, CONNECTION_TIMEOUT);
                }
            }
        };
        getActivity().registerReceiver(mReceiver, filter);
        mConnectivityListener.start();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isNetworkConnected()) {
            mConnected = true;
            notifyListener(RESULT_SUCCESS);
        } else {
            int networkId = mWifiManager.addNetwork(mWifiConfiguration);
            if (networkId == -1) {
                if (DEBUG) {
                    Log.d(TAG, "Failed to add network!");
                }
                notifyListener(RESULT_UNKNOWN_ERROR);
            } else if (!mWifiManager.enableNetwork(networkId, true)) {
                if (DEBUG) {
                    Log.d(TAG, "Failed to enable network id " + networkId + "!");
                }
                notifyListener(RESULT_UNKNOWN_ERROR);
            } else if (!mWifiManager.reconnect()) {
                if (DEBUG) {
                    Log.d(TAG, "Failed to reconnect!");
                }
                notifyListener(RESULT_UNKNOWN_ERROR);
            } else {
                mHandler.sendEmptyMessageDelayed(MSG_TIMEOUT, CONNECTION_TIMEOUT);
            }
        }
    }

    @Override
    public void onDestroy() {
        if (!mConnected) {
            mWifiManager.disconnect();
        }
        getActivity().unregisterReceiver(mReceiver);
        mConnectivityListener.stop();
        mConnectivityListener.destroy();
        mHandler.removeMessages(MSG_TIMEOUT);
        super.onDestroy();
    }

    @Override
    public void onConnectivityChange() {
        if (DEBUG) Log.d(TAG, "Connectivity changed");
        if (!isResumed()) {
            return;
        }
        if (isNetworkConnected()) {
            mConnected = true;
            notifyListener(RESULT_SUCCESS);
        }
    }

    private void notifyListener(int result) {
        if (mListener != null && isResumed()) {
            mListener.onConnectToWifiCompleted(result);
            mListener = null;
        }
    }

    private boolean isNetworkConnected() {
        ConnectivityManager connMan =
                (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = connMan.getActiveNetworkInfo();
        if (netInfo == null) {
            if (DEBUG) Log.d(TAG, "NetworkInfo is null; network is not connected");
            return false;
        }

        if (DEBUG) Log.d(TAG, "NetworkInfo: " + netInfo.toString());
        if (netInfo.isConnected() && netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            WifiInfo currentConnection = mWifiManager.getConnectionInfo();
            if (DEBUG) {
                Log.d(TAG, "Connected to "
                        + ((currentConnection == null) ? "nothing" : currentConnection.getSSID()));
            }
            if (currentConnection != null
                    && currentConnection.getSSID().equals(mWifiConfiguration.SSID)) {
                return true;
            }
        } else {
            if (DEBUG) Log.d(TAG, "Network is not connected");
        }
        return false;
    }
}
