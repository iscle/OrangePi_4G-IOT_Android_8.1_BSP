/*
 * Copyright (C) 2016 The Android Open Source Project
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

import java.util.UUID;

/**
 * Helper class for passing gatt db elements between java and JNI, equal to
 * native btgatt_db_element_t.
 * @hide
 */
public class GattDbElement {

    public static final int TYPE_PRIMARY_SERVICE = 0;
    public static final int TYPE_SECONDARY_SERVICE = 1;
    public static final int TYPE_INCLUDED_SERVICE = 2;
    public static final int TYPE_CHARACTERISTIC = 3;
    public static final int TYPE_DESCRIPTOR = 4;

    public GattDbElement() {}

    public int id;
    public UUID uuid;
    public int type;
    public int attributeHandle;

    /*
     * If type is TYPE_PRIMARY_SERVICE, or TYPE_SECONDARY_SERVICE,
     * this contains the start and end attribute handles.
     */
    public int startHandle;
    public int endHandle;

    /*
     * If type is TYPE_CHARACTERISTIC, this contains the properties of
     * the characteristic.
     */
    public int properties;
    public int permissions;

    public static GattDbElement createPrimaryService(UUID uuid) {
        GattDbElement el = new GattDbElement();
        el.type = TYPE_PRIMARY_SERVICE;
        el.uuid = uuid;
        return el;
    }

    public static GattDbElement createSecondaryService(UUID uuid) {
        GattDbElement el = new GattDbElement();
        el.type = TYPE_SECONDARY_SERVICE;
        el.uuid = uuid;
        return el;
    }

    public static GattDbElement createCharacteristic(UUID uuid, int properties, int permissions) {
        GattDbElement el = new GattDbElement();
        el.type = TYPE_CHARACTERISTIC;
        el.uuid = uuid;
        el.properties = properties;
        el.permissions = permissions;
        return el;
    }

    public static GattDbElement createDescriptor(UUID uuid, int permissions) {
        GattDbElement el = new GattDbElement();
        el.type = TYPE_DESCRIPTOR;
        el.uuid = uuid;
        el.permissions = permissions;
        return el;
    }

    public static GattDbElement createIncludedService(int attributeHandle) {
        GattDbElement el = new GattDbElement();
        el.type = TYPE_INCLUDED_SERVICE;
        el.attributeHandle = attributeHandle;
        return el;
    }
}
