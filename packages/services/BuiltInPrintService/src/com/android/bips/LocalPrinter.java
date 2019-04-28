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

import android.net.Uri;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.util.Log;

import com.android.bips.discovery.DiscoveredPrinter;
import com.android.bips.ipp.CapabilitiesCache;
import com.android.bips.jni.LocalPrinterCapabilities;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;

/**
 * A session-specific printer record. Encapsulates logic for getting the latest printer
 * capabilities as necessary.
 */
class LocalPrinter implements CapabilitiesCache.OnLocalPrinterCapabilities {
    private static final String TAG = LocalPrinter.class.getSimpleName();
    private static final boolean DEBUG = false;

    private final BuiltInPrintService mPrintService;
    private final DiscoveredPrinter mDiscoveredPrinter;
    private final LocalDiscoverySession mSession;
    private final PrinterId mPrinterId;
    private long mLastSeenTime = System.currentTimeMillis();
    private boolean mFound = true;
    private LocalPrinterCapabilities mCapabilities;

    LocalPrinter(BuiltInPrintService printService, LocalDiscoverySession session,
            DiscoveredPrinter discoveredPrinter) {
        mPrintService = printService;
        mSession = session;
        mDiscoveredPrinter = discoveredPrinter;
        mPrinterId = discoveredPrinter.getId(printService);
    }

    /** Return the address of the printer or {@code null} if not known */
    public InetAddress getAddress() {
        if (mCapabilities != null) {
            return mCapabilities.inetAddress;
        }
        return null;
    }

    /** Return true if this printer should be aged out */
    boolean isExpired() {
        return !mFound && (System.currentTimeMillis() - mLastSeenTime) >
                LocalDiscoverySession.PRINTER_EXPIRATION_MILLIS;
    }

    /** Return capabilities or null if not present */
    LocalPrinterCapabilities getCapabilities() {
        return mCapabilities;
    }

    /** Create a PrinterInfo from this record or null if not possible */
    PrinterInfo createPrinterInfo() {
        if (mCapabilities != null && !mCapabilities.isSupported) {
            // Fail out if not supported.
            return null;
        }

        // Get the most recently discovered version of this printer
        DiscoveredPrinter printer = mPrintService.getDiscovery()
                .getPrinter(mDiscoveredPrinter.getUri());
        if (printer == null) return null;

        String description = printer.getDescription(mPrintService);
        boolean idle = mFound && mCapabilities != null;
        PrinterInfo.Builder builder = new PrinterInfo.Builder(
                mPrinterId, printer.name,
                idle ? PrinterInfo.STATUS_IDLE : PrinterInfo.STATUS_UNAVAILABLE)
                .setIconResourceId(R.drawable.ic_printer)
                .setDescription(description);

        if (mCapabilities != null) {
            // Add capabilities if we have them
            PrinterCapabilitiesInfo.Builder capabilitiesBuilder =
                    new PrinterCapabilitiesInfo.Builder(mPrinterId);
            mCapabilities.buildCapabilities(mPrintService, capabilitiesBuilder);
            builder.setCapabilities(capabilitiesBuilder.build());
        }

        return builder.build();
    }

    @Override
    public void onCapabilities(DiscoveredPrinter printer, LocalPrinterCapabilities capabilities) {
        if (mSession.isDestroyed() || !mSession.isKnown(mPrinterId)) return;

        if (capabilities == null) {
            if (DEBUG) Log.d(TAG, "No capabilities so removing printer " + this);
            mSession.removePrinters(Collections.singletonList(mPrinterId));
        } else {
            mCapabilities = capabilities;
            mSession.handlePrinter(this);
        }
    }

    PrinterId getPrinterId() {
        return mPrinterId;
    }

    /** Return true if the printer is in a "found" state according to discoveries */
    boolean isFound() {
        return mFound;
    }

    /** Start a fresh request for capabilities */
    void requestCapabilities() {
        mPrintService.getCapabilitiesCache().request(mDiscoveredPrinter,
                mSession.isPriority(mPrinterId), this);
    }

    /**
     * Indicate the printer was found and gather capabilities if we don't have them
     */
    void found() {
        mLastSeenTime = System.currentTimeMillis();
        mFound = true;

        // Check for cached capabilities
        Uri printerUri = mDiscoveredPrinter.getUri();
        LocalPrinterCapabilities capabilities = mPrintService.getCapabilitiesCache()
                .get(printerUri);
        if (DEBUG) Log.d(TAG, "Printer " + mDiscoveredPrinter + " has caps=" + capabilities);

        if (capabilities != null) {
            // Report current capabilities
            onCapabilities(mDiscoveredPrinter, capabilities);
        } else {
            // Announce printer and fetch capabilities
            mSession.handlePrinter(this);
            requestCapabilities();
        }
    }

    /**
     * Mark this printer as not found (will eventually expire)
     */
    void notFound() {
        mFound = false;
        mLastSeenTime = System.currentTimeMillis();
    }

    /** Return the UUID for this printer if it is known */
    public Uri getUuid() {
        return mDiscoveredPrinter.uuid;
    }

    @Override
    public String toString() {
        return mDiscoveredPrinter.toString();
    }
}