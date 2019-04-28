/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
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

package com.android.bips;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.printservice.PrinterDiscoverySession;
import android.util.Log;

import com.android.bips.discovery.Discovery;
import com.android.bips.discovery.ManualDiscovery;
import com.android.bips.discovery.MdnsDiscovery;
import com.android.bips.discovery.MultiDiscovery;
import com.android.bips.ipp.Backend;
import com.android.bips.ipp.CapabilitiesCache;
import com.android.bips.util.WifiMonitor;

import java.lang.ref.WeakReference;

public class BuiltInPrintService extends PrintService {
    private static final String TAG = BuiltInPrintService.class.getSimpleName();
    private static final boolean DEBUG = false;

    // Present because local activities can bind, but cannot access this object directly
    private static WeakReference<BuiltInPrintService> sInstance;

    private Discovery mDiscovery;
    private ManualDiscovery mManualDiscovery;
    private CapabilitiesCache mCapabilitiesCache;
    private JobQueue mJobQueue;
    private Handler mMainHandler;
    private Backend mBackend;
    private WifiManager.WifiLock mWifiLock;

    /**
     * Return the current print service instance, if running
     */
    public static BuiltInPrintService getInstance() {
        return sInstance == null ? null : sInstance.get();
    }

    @Override
    public void onCreate() {
        if (DEBUG) {
            try {
                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                String version = pInfo.versionName;
                Log.d(TAG, "onCreate() " + version);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        super.onCreate();

        sInstance = new WeakReference<>(this);
        mBackend = new Backend(this);
        mCapabilitiesCache = new CapabilitiesCache(this, mBackend,
                CapabilitiesCache.DEFAULT_MAX_CONCURRENT);
        mManualDiscovery = new ManualDiscovery(this);
        mDiscovery = new MultiDiscovery(this, new WifiMonitor.Factory(), new MdnsDiscovery(this),
                mManualDiscovery);
        mJobQueue = new JobQueue();
        mMainHandler = new Handler(getMainLooper());
        WifiManager wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        mWifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy()");
        mCapabilitiesCache.close();
        mManualDiscovery.close();
        mBackend.close();
        unlockWifi();
        sInstance = null;
        mMainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected PrinterDiscoverySession onCreatePrinterDiscoverySession() {
        if (DEBUG) Log.d(TAG, "onCreatePrinterDiscoverySession");
        return new LocalDiscoverySession(this);
    }

    @Override
    protected void onPrintJobQueued(PrintJob printJob) {
        if (DEBUG) Log.d(TAG, "onPrintJobQueued");
        if (WifiMonitor.isConnected(this)) {
            mJobQueue.print(new LocalPrintJob(this, mBackend, printJob));
        } else {
            printJob.fail(getString(R.string.wifi_not_connected));
        }
    }

    @Override
    protected void onRequestCancelPrintJob(PrintJob printJob) {
        if (DEBUG) Log.d(TAG, "onRequestCancelPrintJob");
        mJobQueue.cancel(printJob.getId());
    }

    /**
     * Return the global discovery object
     */
    Discovery getDiscovery() {
        return mDiscovery;
    }

    public ManualDiscovery getManualDiscovery() {
        return mManualDiscovery;
    }

    /**
     * Return the global Printer Capabilities cache
     */
    public CapabilitiesCache getCapabilitiesCache() {
        return mCapabilitiesCache;
    }

    /** Return the main handler for running on main UI */
    public Handler getMainHandler() {
        return mMainHandler;
    }

    /** Prevent Wi-Fi from going to sleep until {@link #unlockWifi} is called */
    public void lockWifi() {
        if (!mWifiLock.isHeld()) {
            mWifiLock.acquire();
        }
    }

    /** Allow Wi-Fi to be disabled during sleep modes. */
    public void unlockWifi() {
        if (mWifiLock.isHeld()) {
            mWifiLock.release();
        }
    }
}