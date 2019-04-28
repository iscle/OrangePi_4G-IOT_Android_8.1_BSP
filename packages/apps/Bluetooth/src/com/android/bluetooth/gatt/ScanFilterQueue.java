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

import android.bluetooth.BluetoothUuid;
import android.bluetooth.le.ScanFilter;
import android.os.ParcelUuid;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Helper class used to manage advertisement package filters.
 *
 * @hide
 */
/* package */class ScanFilterQueue {
    public static final int TYPE_DEVICE_ADDRESS = 0;
    public static final int TYPE_SERVICE_DATA_CHANGED = 1;
    public static final int TYPE_SERVICE_UUID = 2;
    public static final int TYPE_SOLICIT_UUID = 3;
    public static final int TYPE_LOCAL_NAME = 4;
    public static final int TYPE_MANUFACTURER_DATA = 5;
    public static final int TYPE_SERVICE_DATA = 6;

    // Max length is 31 - 3(flags) - 2 (one byte for length and one byte for type).
    private static final int MAX_LEN_PER_FIELD = 26;

    // Values defined in bluedroid.
    private static final byte DEVICE_TYPE_ALL = 2;

    class Entry {
        public String address;
        public byte addr_type;
        public byte type;
        public UUID uuid;
        public UUID uuid_mask;
        public String name;
        public int company;
        public int company_mask;
        public byte[] data;
        public byte[] data_mask;

        @Override
        public int hashCode() {
            return Objects.hash(address, addr_type, type, uuid, uuid_mask,
                                name, company, company_mask,
                                Arrays.hashCode(data),
                                Arrays.hashCode(data_mask));
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            Entry other = (Entry) obj;
            return Objects.equals(address, other.address) &&
                    addr_type == other.addr_type && type == other.type &&
                    Objects.equals(uuid, other.uuid) &&
                    Objects.equals(uuid_mask, other.uuid_mask) &&
                    Objects.equals(name, other.name) &&
                            company == other.company && company_mask == other.company_mask &&
                    Objects.deepEquals(data, other.data) &&
                    Objects.deepEquals(data_mask, other.data_mask);
        }
    }

    private Set<Entry> mEntries = new HashSet<Entry>();

    void addDeviceAddress(String address, byte type) {
        Entry entry = new Entry();
        entry.type = TYPE_DEVICE_ADDRESS;
        entry.address = address;
        entry.addr_type = type;
        mEntries.add(entry);
    }

    void addServiceChanged() {
        Entry entry = new Entry();
        entry.type = TYPE_SERVICE_DATA_CHANGED;
        mEntries.add(entry);
    }

    void addUuid(UUID uuid) {
        Entry entry = new Entry();
        entry.type = TYPE_SERVICE_UUID;
        entry.uuid = uuid;
        entry.uuid_mask = new UUID(0, 0);
        mEntries.add(entry);
    }

    void addUuid(UUID uuid, UUID uuid_mask) {
        Entry entry = new Entry();
        entry.type = TYPE_SERVICE_UUID;
        entry.uuid = uuid;
        entry.uuid_mask = uuid_mask;
        mEntries.add(entry);
    }

    void addSolicitUuid(UUID uuid) {
        Entry entry = new Entry();
        entry.type = TYPE_SOLICIT_UUID;
        entry.uuid = uuid;
        mEntries.add(entry);
    }

    void addName(String name) {
        Entry entry = new Entry();
        entry.type = TYPE_LOCAL_NAME;
        entry.name = name;
        mEntries.add(entry);
    }

    void addManufacturerData(int company, byte[] data) {
        Entry entry = new Entry();
        entry.type = TYPE_MANUFACTURER_DATA;
        entry.company = company;
        entry.company_mask = 0xFFFF;
        entry.data = data;
        entry.data_mask = new byte[data.length];
        Arrays.fill(entry.data_mask, (byte) 0xFF);
        mEntries.add(entry);
    }

    void addManufacturerData(int company, int company_mask, byte[] data, byte[] data_mask) {
        Entry entry = new Entry();
        entry.type = TYPE_MANUFACTURER_DATA;
        entry.company = company;
        entry.company_mask = company_mask;
        entry.data = data;
        entry.data_mask = data_mask;
        mEntries.add(entry);
    }

    void addServiceData(byte[] data, byte[] dataMask) {
        Entry entry = new Entry();
        entry.type = TYPE_SERVICE_DATA;
        entry.data = data;
        entry.data_mask = dataMask;
        mEntries.add(entry);
    }

    Entry pop() {
        if (isEmpty()) {
            return null;
        }
        Iterator<Entry> iterator = mEntries.iterator();
        Entry entry = iterator.next();
        iterator.remove();
        return entry;
    }

    boolean isEmpty() {
        return mEntries.isEmpty();
    }

    void clearUuids() {
        for (Iterator<Entry> it = mEntries.iterator(); it.hasNext();) {
            Entry entry = it.next();
            if (entry.type == TYPE_SERVICE_UUID)
                it.remove();
        }
    }

    void clear() {
        mEntries.clear();
    }

    /**
     * Compute feature selection based on the filters presented.
     */
    int getFeatureSelection() {
        int selc = 0;
        for (Entry entry : mEntries) {
            selc |= (1 << entry.type);
        }
        return selc;
    }

    /**
     * Add ScanFilter to scan filter queue.
     */
    void addScanFilter(ScanFilter filter) {
        if (filter == null)
            return;
        if (filter.getDeviceName() != null) {
            addName(filter.getDeviceName());
        }
        if (filter.getDeviceAddress() != null) {
            addDeviceAddress(filter.getDeviceAddress(), DEVICE_TYPE_ALL);
        }
        if (filter.getServiceUuid() != null) {
            if (filter.getServiceUuidMask() == null) {
                addUuid(filter.getServiceUuid().getUuid());
            } else {
                addUuid(filter.getServiceUuid().getUuid(),
                        filter.getServiceUuidMask().getUuid());
            }
        }
        if (filter.getManufacturerData() != null) {
            if (filter.getManufacturerDataMask() == null) {
                addManufacturerData(filter.getManufacturerId(), filter.getManufacturerData());
            } else {
                addManufacturerData(filter.getManufacturerId(), 0xFFFF,
                        filter.getManufacturerData(), filter.getManufacturerDataMask());
            }
        }
        if (filter.getServiceDataUuid() != null && filter.getServiceData() != null) {
            ParcelUuid serviceDataUuid = filter.getServiceDataUuid();
            byte[] serviceData = filter.getServiceData();
            byte[] serviceDataMask = filter.getServiceDataMask();
            if (serviceDataMask == null) {
                serviceDataMask = new byte[serviceData.length];
                Arrays.fill(serviceDataMask, (byte) 0xFF);
            }
            serviceData = concate(serviceDataUuid, serviceData);
            serviceDataMask = concate(serviceDataUuid, serviceDataMask);
            if (serviceData != null && serviceDataMask != null) {
                addServiceData(serviceData, serviceDataMask);
            }
        }
    }

    private byte[] concate(ParcelUuid serviceDataUuid, byte[] serviceData) {
        int dataLen = 2 + (serviceData == null ? 0 : serviceData.length);
        // If data is too long, don't add it to hardware scan filter.
        if (dataLen > MAX_LEN_PER_FIELD) {
            return null;
        }
        byte[] concated = new byte[dataLen];
        // Extract 16 bit UUID value.
        int uuidValue = BluetoothUuid.getServiceIdentifierFromParcelUuid(
                serviceDataUuid);
        // First two bytes are service data UUID in little-endian.
        concated[0] = (byte) (uuidValue & 0xFF);
        concated[1] = (byte) ((uuidValue >> 8) & 0xFF);
        if (serviceData != null) {
            System.arraycopy(serviceData, 0, concated, 2, serviceData.length);
        }
        return concated;
    }
}
