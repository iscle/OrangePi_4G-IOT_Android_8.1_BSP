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
import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;

import com.android.bips.BuiltInPrintService;
import com.android.bips.ipp.CapabilitiesCache;
import com.android.bips.jni.LocalPrinterCapabilities;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Manage a list of printers manually added by the user.
 */
public class ManualDiscovery extends Discovery implements AutoCloseable {
    private static final String TAG = ManualDiscovery.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final String CACHE_FILE = TAG + ".json";

    // Likely paths at which a print service may be found
    private static final Uri[] IPP_URIS = { Uri.parse("ipp://path:631/ipp/print"),
            Uri.parse("ipp://path:80/ipp/print"), Uri.parse("ipp://path:631/ipp/printer"),
            Uri.parse("ipp://path:631/ipp"), Uri.parse("ipp://path:631/")};

    private final List<DiscoveredPrinter> mManualPrinters = new ArrayList<>();

    public ManualDiscovery(BuiltInPrintService printService) {
        super(printService);
        load();
    }

    @Override
    void onStart() {
        if (DEBUG) Log.d(TAG, "onStart");
        for (DiscoveredPrinter printer : mManualPrinters) {
            if (DEBUG) Log.d(TAG, "reporting " + printer);
            getPrintService().getCapabilitiesCache().evictOnNetworkChange(printer.getUri());
            printerFound(printer);
        }
    }

    @Override
    void onStop() {
        if (DEBUG) Log.d(TAG, "onStop");
    }

    @Override
    public void close() {
        if (DEBUG) Log.d(TAG, "close");
        save();
    }

    /**
     * Asynchronously attempt to add a new manual printer, calling back with success
     */
    public void addManualPrinter(String hostname, PrinterAddCallback callback) {
        if (DEBUG) Log.d(TAG, "addManualPrinter " + hostname);
        new CapabilitiesFinder(hostname, callback);
    }

    private void addManualPrinter(DiscoveredPrinter printer) {
        // Remove any prior printer with the same uri or path
        for (DiscoveredPrinter other : new ArrayList<>(mManualPrinters)) {
            if (other.getUri().equals(printer.getUri())) {
                printerLost(other.getUri());
                mManualPrinters.remove(other);
            }
        }

        // Add new printer at top
        mManualPrinters.add(0, printer);

        // Notify if necessary
        if (isStarted()) printerFound(printer);
    }

    /**
     * Remove an existing manual printer
     */
    public void removeManualPrinter(DiscoveredPrinter toRemove) {
        for (DiscoveredPrinter printer : mManualPrinters) {
            if (printer.path.equals(toRemove.path)) {
                mManualPrinters.remove(printer);
                if (isStarted()) {
                    printerLost(printer.getUri());
                }
                break;
            }
        }
    }

    /**
     * Persist the current set of manual printers to storage
     */
    private void save() {
        File cachedPrintersFile = new File(getPrintService().getCacheDir(), CACHE_FILE);
        if (cachedPrintersFile.exists()) {
            cachedPrintersFile.delete();
        }

        try (JsonWriter writer = new JsonWriter(new BufferedWriter(
                new FileWriter(cachedPrintersFile)))) {
            writer.beginObject();
            writer.name("manualPrinters");
            writer.beginArray();
            for (DiscoveredPrinter printer : mManualPrinters) {
                if (DEBUG) Log.d(TAG, "Writing " + printer);
                printer.write(writer);
            }
            writer.endArray();
            writer.endObject();
        } catch (NullPointerException | IOException e) {
            Log.w(TAG, "Error while storing", e);
        }
    }

    /**
     * Load the current set of manual printers from storage
     */
    private void load() {
        File cachedPrintersFile = new File(getPrintService().getCacheDir(), CACHE_FILE);
        if (!cachedPrintersFile.exists()) return;

        try (JsonReader reader = new JsonReader(new BufferedReader(
                new FileReader(cachedPrintersFile)))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String itemName = reader.nextName();
                if (itemName.equals("manualPrinters")) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        addManualPrinter(new DiscoveredPrinter(reader));
                    }
                    reader.endArray();
                }
            }
            reader.endObject();
        } catch (IllegalStateException | IOException ignored) {
            Log.w(TAG, "Error while restoring", ignored);
        }
        if (DEBUG) Log.d(TAG, "After load we have " + mManualPrinters.size() + " manual printers");
    }

    /** Used to convey response to {@link #addManualPrinter} */
    public interface PrinterAddCallback {
        /**
         * The requested manual printer was found.
         *
         * @param printer   information about the discovered printer
         * @param supported true if the printer is supported (and was therefore added), or false
         *                  if the printer was found but is not supported (and was therefore not
         *                  added)
         */
        void onFound(DiscoveredPrinter printer, boolean supported);

        /**
         * The requested manual printer was not found.
         */
        void onNotFound();
    }

    /**
     * Search common printer paths for a successful response
     */
    private class CapabilitiesFinder implements CapabilitiesCache.OnLocalPrinterCapabilities {
        private final LinkedList<Uri> mUris = new LinkedList<>();
        private final PrinterAddCallback mFinalCallback;
        private final String mHostname;

        /**
         * Constructs a new finder
         *
         * @param hostname Hostname to crawl for IPP endpoints
         * @param callback Callback to issue when the first successful response arrives, or
         *                 when all responses have failed.
         */
        CapabilitiesFinder(String hostname, PrinterAddCallback callback) {
            mFinalCallback = callback;
            mHostname = hostname;

            for (Uri uri : IPP_URIS) {
                uri = uri.buildUpon().encodedAuthority(mHostname + ":" + uri.getPort()).build();
                mUris.add(uri);
                DiscoveredPrinter printer = new DiscoveredPrinter(null, "unknown", uri, null);
                getPrintService().getCapabilitiesCache().request(printer, true, this);
            }
        }

        @Override
        public void onCapabilities(DiscoveredPrinter printer, LocalPrinterCapabilities capabilities) {
            if (DEBUG) Log.d(TAG, "onCapabilities: " + capabilities);
            mUris.remove(printer.getUri());
            if (capabilities == null) {
                if (mUris.isEmpty()) {
                    mFinalCallback.onNotFound();
                }
                return;
            }

            // Success, so cancel all other requests
            getPrintService().getCapabilitiesCache().cancel(this);

            // Deliver a successful response
            Uri uuid = TextUtils.isEmpty(capabilities.uuid) ? null : Uri.parse(capabilities.uuid);
            String name = TextUtils.isEmpty(capabilities.name) ? printer.getUri().getHost() : capabilities.name;

            DiscoveredPrinter resolvedPrinter = new DiscoveredPrinter(uuid, name, printer.getUri(),
                    capabilities.location);

            // Only add supported printers
            if (capabilities.isSupported) {
                addManualPrinter(resolvedPrinter);
            }

            mFinalCallback.onFound(resolvedPrinter, capabilities.isSupported);
        }
    }
}