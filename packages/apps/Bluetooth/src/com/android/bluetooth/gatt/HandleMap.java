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

import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class HandleMap {
    private static final boolean DBG = GattServiceConfig.DBG;
    private static final String TAG = GattServiceConfig.TAG_PREFIX + "HandleMap";

    public static final int TYPE_UNDEFINED = 0;
    public static final int TYPE_SERVICE = 1;
    public static final int TYPE_CHARACTERISTIC = 2;
    public static final int TYPE_DESCRIPTOR = 3;

    class Entry {
        int serverIf = 0;
        int type = TYPE_UNDEFINED;
        int handle = 0;
        UUID uuid = null;
        int instance = 0;
        int serviceType = 0;
        int serviceHandle = 0;
        int charHandle = 0;
        boolean started = false;
        boolean advertisePreferred = false;

        Entry(int serverIf, int handle, UUID uuid, int serviceType, int instance) {
            this.serverIf = serverIf;
            this.type = TYPE_SERVICE;
            this.handle = handle;
            this.uuid = uuid;
            this.instance = instance;
            this.serviceType = serviceType;
        }

        Entry(int serverIf, int handle, UUID uuid, int serviceType, int instance,
            boolean advertisePreferred) {
            this.serverIf = serverIf;
            this.type = TYPE_SERVICE;
            this.handle = handle;
            this.uuid = uuid;
            this.instance = instance;
            this.serviceType = serviceType;
            this.advertisePreferred = advertisePreferred;
        }

        Entry(int serverIf, int type, int handle, UUID uuid, int serviceHandle) {
            this.serverIf = serverIf;
            this.type = type;
            this.handle = handle;
            this.uuid = uuid;
            this.serviceHandle = serviceHandle;
        }

        Entry(int serverIf, int type, int handle, UUID uuid, int serviceHandle, int charHandle) {
            this.serverIf = serverIf;
            this.type = type;
            this.handle = handle;
            this.uuid = uuid;
            this.serviceHandle = serviceHandle;
            this.charHandle = charHandle;
        }
    }

    List<Entry> mEntries = null;
    Map<Integer, Integer> mRequestMap = null;
    int mLastCharacteristic = 0;

    HandleMap() {
        mEntries = new ArrayList<Entry>();
        mRequestMap = new HashMap<Integer, Integer>();
    }

    void clear() {
        mEntries.clear();
        mRequestMap.clear();
    }

    void addService(int serverIf, int handle, UUID uuid, int serviceType, int instance,
        boolean advertisePreferred) {
        mEntries.add(new Entry(serverIf, handle, uuid, serviceType, instance, advertisePreferred));
    }

    void addCharacteristic(int serverIf, int handle, UUID uuid, int serviceHandle) {
        mLastCharacteristic = handle;
        mEntries.add(new Entry(serverIf, TYPE_CHARACTERISTIC, handle, uuid, serviceHandle));
    }

    void addDescriptor(int serverIf, int handle, UUID uuid, int serviceHandle) {
        mEntries.add(new Entry(serverIf, TYPE_DESCRIPTOR, handle, uuid, serviceHandle, mLastCharacteristic));
    }

    void setStarted(int serverIf, int handle, boolean started) {
        for(Entry entry : mEntries) {
            if (entry.type != TYPE_SERVICE ||
                entry.serverIf != serverIf ||
                entry.handle != handle)
                continue;

            entry.started = started;
            return;
        }
    }

    Entry getByHandle(int handle) {
        for(Entry entry : mEntries) {
            if (entry.handle == handle)
                return entry;
        }
        Log.e(TAG, "getByHandle() - Handle " + handle + " not found!");
        return null;
    }

    boolean checkServiceExists(UUID uuid, int handle) {
        for(Entry entry : mEntries) {
            if (entry.type == TYPE_SERVICE && entry.handle == handle && entry.uuid.equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    void deleteService(int serverIf, int serviceHandle) {
        for(Iterator <Entry> it = mEntries.iterator(); it.hasNext();) {
            Entry entry = it.next();
            if (entry.serverIf != serverIf) continue;

            if (entry.handle == serviceHandle ||
                entry.serviceHandle == serviceHandle)
                it.remove();
        }
    }

    List<Entry> getEntries() {
        return mEntries;
    }

    void addRequest(int requestId, int handle) {
        mRequestMap.put(requestId, handle);
    }

    void deleteRequest(int requestId) {
        mRequestMap.remove(requestId);
    }

    Entry getByRequestId(int requestId) {
        Integer handle = mRequestMap.get(requestId);
        if (handle == null) {
            Log.e(TAG, "getByRequestId() - Request ID " + requestId + " not found!");
            return null;
        }
        return getByHandle(handle);
    }


    /**
     * Logs debug information.
     */
    void dump(StringBuilder sb) {
        sb.append("  Entries: " + mEntries.size() + "\n");
        sb.append("  Requests: " + mRequestMap.size() + "\n");

        for (Entry entry : mEntries) {
            sb.append("  " + entry.serverIf + ": [" + entry.handle + "] ");
            switch(entry.type) {
                case TYPE_SERVICE:
                    sb.append("Service " + entry.uuid);
                    sb.append(", started " + entry.started);
                    break;

                case TYPE_CHARACTERISTIC:
                    sb.append("  Characteristic " + entry.uuid);
                    break;

                case TYPE_DESCRIPTOR:
                    sb.append("    Descriptor " + entry.uuid);
                    break;
            }

            sb.append("\n");
        }
    }
}
