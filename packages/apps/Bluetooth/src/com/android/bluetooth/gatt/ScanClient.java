/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.bluetooth.gatt;

import android.bluetooth.le.ResultStorageDescriptor;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Helper class identifying a client that has requested LE scan results.
 *
 * @hide
 */
/* package */class ScanClient {
    int scannerId;
    UUID[] uuids;
    ScanSettings settings;
    List<ScanFilter> filters;
    List<List<ResultStorageDescriptor>> storages;
    // App associated with the scan client died.
    boolean appDied;
    boolean hasLocationPermission;
    boolean hasPeersMacAddressPermission;
    // Pre-M apps are allowed to get scan results even if location is disabled
    boolean legacyForegroundApp;

    AppScanStats stats = null;

    private static final ScanSettings DEFAULT_SCAN_SETTINGS = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();

    ScanClient(int scannerId) {
        this(scannerId, new UUID[0], DEFAULT_SCAN_SETTINGS, null, null);
    }

    ScanClient(int scannerId, UUID[] uuids) {
        this(scannerId, uuids, DEFAULT_SCAN_SETTINGS, null, null);
    }

    ScanClient(int scannerId, ScanSettings settings,
            List<ScanFilter> filters) {
        this(scannerId, new UUID[0], settings, filters, null);
    }

    ScanClient(int scannerId, ScanSettings settings, List<ScanFilter> filters,
            List<List<ResultStorageDescriptor>> storages) {
        this(scannerId, new UUID[0], settings, filters, storages);
    }

    private ScanClient(int scannerId, UUID[] uuids, ScanSettings settings, List<ScanFilter> filters,
            List<List<ResultStorageDescriptor>> storages) {
        this.scannerId = scannerId;
        this.uuids = uuids;
        this.settings = settings;
        this.filters = filters;
        this.storages = storages;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ScanClient other = (ScanClient) obj;
        return scannerId == other.scannerId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(scannerId);
    }
}
