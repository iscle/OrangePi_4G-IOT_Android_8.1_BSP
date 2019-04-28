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

import android.net.Uri;
import android.util.Log;

import com.android.bips.BuiltInPrintService;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Parent class for all printer discovery mechanisms. Subclasses must implement onStart and onStop.
 * While started, discovery mechanisms deliver DiscoveredPrinter objects via
 * {@link #printerFound(DiscoveredPrinter)} when they appear, and {@link #printerLost(Uri)} when
 * they become unavailable.
 */
public abstract class Discovery {
    private static final String TAG = Discovery.class.getSimpleName();
    private static final boolean DEBUG = false;

    private final BuiltInPrintService mPrintService;
    private final List<Listener> mListeners = new CopyOnWriteArrayList<>();
    private final Map<Uri, DiscoveredPrinter> mPrinters = new HashMap<>();

    private boolean mStarted = false;

    Discovery(BuiltInPrintService printService) {
        mPrintService = printService;
    }

    /**
     * Add a listener and begin receiving notifications from the Discovery object of any
     * printers it finds.
     */
    public void start(Listener listener) {
        mListeners.add(listener);
        mPrinters.values().forEach(listener::onPrinterFound);
        start();
    }

    /**
     * Remove a listener so that it no longer receives notifications of found printers.
     * Discovery will continue for other listeners until the last one is removed.
     */
    public void stop(Listener listener) {
        mListeners.remove(listener);
        if (mListeners.isEmpty()) {
            stop();
        }
    }

    /**
     * Return true if this object is in a started state
     */
    boolean isStarted() {
        return mStarted;
    }

    /**
     * Return the current print service instance
     */
    BuiltInPrintService getPrintService() {
        return mPrintService;
    }

    /**
     * Start if not already started
     */
    private void start() {
        if (!mStarted) {
            mStarted = true;
            onStart();
        }
    }

    /**
     * Stop if not already stopped
     */
    private void stop() {
        if (mStarted) {
            mStarted = false;
            onStop();
            mPrinters.clear();
        }
    }

    /**
     * Start searching for printers
     */
    abstract void onStart();

    /**
     * Stop searching for printers, freeing any search-reated resources.
     */
    abstract void onStop();

    /**
     * Signal that a printer appeared or possibly changed state.
     */
    void printerFound(DiscoveredPrinter printer) {
        DiscoveredPrinter current = mPrinters.get(printer.getUri());
        if (Objects.equals(current, printer)) {
            if (DEBUG) Log.d(TAG, "Already have the reported printer, ignoring");
            return;
        }
        mPrinters.put(printer.getUri(), printer);
        for (Listener listener : mListeners) {
            listener.onPrinterFound(printer);
        }
    }

    /**
     * Signal that a printer is no longer visible
     */
    void printerLost(Uri printerUri) {
        DiscoveredPrinter printer = mPrinters.remove(printerUri);
        if (printer == null) return;
        for (Listener listener : mListeners) {
            listener.onPrinterLost(printer);
        }
    }

    /** Signal loss of all printers */
    void allPrintersLost() {
        for (DiscoveredPrinter printer : mPrinters.values()) {
            for (Listener listener : mListeners) {
                listener.onPrinterLost(printer);
            }
        }
        mPrinters.clear();
    }

    /**
     * Return the working collection of currently-found printers
     */
    public Collection<DiscoveredPrinter> getPrinters() {
        return mPrinters.values();
    }

    /**
     * Return printer matching the uri, or null if none
     */
    public DiscoveredPrinter getPrinter(Uri uri) {
        return mPrinters.get(uri);
    }

    public interface Listener {
        void onPrinterFound(DiscoveredPrinter printer);

        void onPrinterLost(DiscoveredPrinter printer);
    }
}