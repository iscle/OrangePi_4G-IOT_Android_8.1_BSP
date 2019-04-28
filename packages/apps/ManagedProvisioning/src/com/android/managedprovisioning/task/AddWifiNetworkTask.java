/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.task;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.wifi.NetworkMonitor;
import com.android.managedprovisioning.task.wifi.WifiConfigurationProvider;

/**
 * Adds a wifi network to the system and waits for it to successfully connect. If the system does
 * not support wifi, the adding or connection times out {@link #error(int)} will be called.
 */
public class AddWifiNetworkTask extends AbstractProvisioningTask
        implements NetworkMonitor.NetworkConnectedCallback {
    private static final int RETRY_SLEEP_DURATION_BASE_MS = 500;
    private static final int RETRY_SLEEP_MULTIPLIER = 2;
    private static final int MAX_RETRIES = 6;
    private static final int RECONNECT_TIMEOUT_MS = 60000;

    private final WifiConfigurationProvider mWifiConfigurationProvider;
    private final WifiManager mWifiManager;
    private final NetworkMonitor mNetworkMonitor;

    private Handler mHandler;
    private boolean mTaskDone = false;

    private final Utils mUtils = new Utils();
    private Runnable mTimeoutRunnable;

    public AddWifiNetworkTask(
            Context context,
            ProvisioningParams provisioningParams,
            Callback callback) {
        this(
                new NetworkMonitor(context),
                new WifiConfigurationProvider(),
                context, provisioningParams, callback);
    }

    @VisibleForTesting
    AddWifiNetworkTask(
            NetworkMonitor networkMonitor,
            WifiConfigurationProvider wifiConfigurationProvider,
            Context context,
            ProvisioningParams provisioningParams,
            Callback callback) {
        super(context, provisioningParams, callback);

        mNetworkMonitor = checkNotNull(networkMonitor);
        mWifiConfigurationProvider = checkNotNull(wifiConfigurationProvider);
        mWifiManager  = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    @Override
    public void run(int userId) {
        if (mProvisioningParams.wifiInfo == null) {
            success();
            return;
        }

        if (mWifiManager == null || !enableWifi()) {
            ProvisionLogger.loge("Failed to enable wifi");
            error(0);
            return;
        }

        if (isConnectedToSpecifiedWifi()) {
            success();
            return;
        }

        mTaskDone = false;
        mHandler = new Handler();
        mNetworkMonitor.startListening(this);
        connectToProvidedNetwork();
    }

    @Override
    public int getStatusMsgId() {
        return R.string.progress_connect_to_wifi;
    }

    private void connectToProvidedNetwork() {
        WifiConfiguration wifiConf =
                mWifiConfigurationProvider.generateWifiConfiguration(mProvisioningParams.wifiInfo);

        if (wifiConf == null) {
            ProvisionLogger.loge("WifiConfiguration is null");
            error(0);
            return;
        }

        int netId = tryAddingNetwork(wifiConf);
        if (netId == -1) {
            ProvisionLogger.loge("Unable to add network after trying " +  MAX_RETRIES + " times.");
            error(0);
            return;
        }

        // Setting disableOthers to 'true' should trigger a connection attempt.
        mWifiManager.enableNetwork(netId, true);
        mWifiManager.saveConfiguration();

        // Network was successfully saved, now connect to it.
        if (!mWifiManager.reconnect()) {
            ProvisionLogger.loge("Unable to connect to wifi");
            error(0);
            return;
        }

        // NetworkMonitor will call onNetworkConnected when in Wifi mode.
        // Post time out event in case the NetworkMonitor doesn't call back.
        mTimeoutRunnable = () -> finishTask(false);
        mHandler.postDelayed(mTimeoutRunnable, RECONNECT_TIMEOUT_MS);
    }

    private int tryAddingNetwork(WifiConfiguration wifiConf) {
        int netId = mWifiManager.addNetwork(wifiConf);
        int retriesLeft = MAX_RETRIES;
        int durationNextSleep = RETRY_SLEEP_DURATION_BASE_MS;

        while(netId == -1 && retriesLeft > 0) {
            ProvisionLogger.loge("Retrying in " + durationNextSleep + " ms.");
            try {
                Thread.sleep(durationNextSleep);
            } catch (InterruptedException e) {
                ProvisionLogger.loge("Retry interrupted.");
            }
            durationNextSleep *= RETRY_SLEEP_MULTIPLIER;
            retriesLeft--;
            netId = mWifiManager.addNetwork(wifiConf);
        }
        return netId;
    }

    private boolean enableWifi() {
        return mWifiManager.isWifiEnabled() || mWifiManager.setWifiEnabled(true);
    }

    @Override
    public void onNetworkConnected() {
        if (isConnectedToSpecifiedWifi()) {
            ProvisionLogger.logd("Connected to the correct network");
            finishTask(true);
            // Remove time out callback.
            mHandler.removeCallbacks(mTimeoutRunnable);
        }
    }

    private synchronized void finishTask(boolean isSuccess) {
        if (mTaskDone) {
            return;
        }

        mTaskDone = true;
        mNetworkMonitor.stopListening();
        if (isSuccess) {
            success();
        } else {
            error(0);
        }
    }

    private boolean isConnectedToSpecifiedWifi() {
        return mUtils.isConnectedToWifi(mContext)
                && mWifiManager.getConnectionInfo() != null
                && mProvisioningParams.wifiInfo.ssid.equals(
                        mWifiManager.getConnectionInfo().getSSID());
    }
}
