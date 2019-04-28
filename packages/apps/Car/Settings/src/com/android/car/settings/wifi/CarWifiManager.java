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
package com.android.car.settings.wifi;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.HandlerThread;
import android.os.Process;
import android.support.annotation.UiThread;

import com.android.settingslib.wifi.AccessPoint;
import com.android.settingslib.wifi.WifiTracker;
import com.android.settingslib.wifi.WifiTracker.WifiListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages Wifi configuration: e.g. monitors wifi states, change wifi setting etc.
 */
public class CarWifiManager implements WifiTracker.WifiListener {
    private static final String TAG = "CarWifiManager";
    private final Context mContext;
    private Listener mListener;
    private boolean mStarted;

    private WifiTracker mWifiTracker;
    private final HandlerThread mBgThread;
    private WifiManager mWifiManager;
    public interface Listener {
        /**
         * Something about wifi setting changed.
         */
        void onAccessPointsChanged();

        /**
         * Called when the state of Wifi has changed, the state will be one of
         * the following.
         *
         * <li>{@link WifiManager#WIFI_STATE_DISABLED}</li>
         * <li>{@link WifiManager#WIFI_STATE_ENABLED}</li>
         * <li>{@link WifiManager#WIFI_STATE_DISABLING}</li>
         * <li>{@link WifiManager#WIFI_STATE_ENABLING}</li>
         * <li>{@link WifiManager#WIFI_STATE_UNKNOWN}</li>
         * <p>
         *
         * @param state The new state of wifi.
         */
        void onWifiStateChanged(int state);
    }

    public CarWifiManager(Context context, Listener listener) {
        mContext = context;
        mListener = listener;
        mWifiManager = (WifiManager) mContext.getSystemService(WifiManager.class);
        mBgThread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        mBgThread.start();
        mWifiTracker = new WifiTracker(context, this, mBgThread.getLooper(), true, true);
    }

    /**
     * Starts {@link CarWifiManager}.
     * This should be called only from main thread.
     */
    @UiThread
    public void start() {
        if (!mStarted) {
            mStarted = true;
            mWifiTracker.startTracking();
        }
    }

    /**
     * Stops {@link CarWifiManager}.
     * This should be called only from main thread.
     */
    @UiThread
    public void stop() {
        if (mStarted) {
            mStarted = false;
            mWifiTracker.stopTracking();
        }
    }

    public List<AccessPoint> getAccessPoints() {
        List<AccessPoint> accessPoints = new ArrayList<AccessPoint>();
        if (mWifiManager.isWifiEnabled()) {
            for (AccessPoint accessPoint : mWifiTracker.getAccessPoints()) {
                // ignore out of reach access points.
                if (accessPoint.getLevel() != -1) {
                    accessPoints.add(accessPoint);
                }
            }
        }
        return accessPoints;
    }

    public boolean isWifiEnabled() {
        return mWifiManager.isWifiEnabled();
    }

    public int getWifiState() {
        return mWifiManager.getWifiState();
    }

    public boolean setWifiEnabled(boolean enabled) {
        return mWifiManager.setWifiEnabled(enabled);
    }

    public void connectToPublicWifi(AccessPoint accessPoint, WifiManager.ActionListener listener) {
        accessPoint.generateOpenNetworkConfig();
        mWifiManager.connect(accessPoint.getConfig(), listener);
    }

    @Override
    public void onWifiStateChanged(int state) {
        mListener.onWifiStateChanged(state);
    }

    @Override
    public void onConnectedChanged() {
    }

    @Override
    public void onAccessPointsChanged() {
        mListener.onAccessPointsChanged();
    }
}
