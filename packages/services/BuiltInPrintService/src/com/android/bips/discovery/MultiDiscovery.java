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

package com.android.bips.discovery;

import android.util.Log;

import com.android.bips.BuiltInPrintService;
import com.android.bips.util.WifiMonitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Combines the behavior of multiple child {@link Discovery} objects to a single one */
public class MultiDiscovery extends Discovery {
    private static final String TAG = MultiDiscovery.class.getSimpleName();
    private static final boolean DEBUG = false;

    private final List<Discovery> mDiscoveries = new ArrayList<>();
    private final Listener mChildListener;
    private final WifiMonitor.Factory mWifiMonitorFactory;
    private WifiMonitor mWifiMonitor;

    public MultiDiscovery(BuiltInPrintService printService, WifiMonitor.Factory wifiMonitorFactory,
            Discovery... discoveries) {
        super(printService);
        mDiscoveries.addAll(Arrays.asList(discoveries));
        mWifiMonitorFactory = wifiMonitorFactory;
        mChildListener = new Listener() {
            @Override
            public void onPrinterFound(DiscoveredPrinter printer) {
                DiscoveredPrinter oldPrinter = getPrinter(printer.getUri());
                if (oldPrinter != null) {
                    printer = printer.bestOf(oldPrinter);
                }

                MultiDiscovery.this.printerFound(printer);
            }

            @Override
            public void onPrinterLost(DiscoveredPrinter printer) {
                MultiDiscovery.this.printerLost(printer.getUri());
            }
        };
    }

    @Override
    void onStart() {
        if (DEBUG) Log.d(TAG, "onStart()");
        mWifiMonitor = mWifiMonitorFactory.create(getPrintService(), connected -> {
            if (connected) {
                if (DEBUG) Log.d(TAG, "Connected, starting discovery");
                for (Discovery discovery : mDiscoveries) {
                    discovery.start(mChildListener);
                }
            } else {
                if (DEBUG) Log.d(TAG, "Disconnected, stopping discovery");
                for (Discovery discovery : mDiscoveries) {
                    discovery.stop(mChildListener);
                }
                allPrintersLost();
            }
        });
    }

    @Override
    void onStop() {
        if (DEBUG) Log.d(TAG, "onStop()");
        mWifiMonitor.close();
        for (Discovery discovery : mDiscoveries) {
            discovery.stop(mChildListener);
        }
        allPrintersLost();
    }
}