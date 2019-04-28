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
package android.car.usb.handler;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.text.TextUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Util methods to work with USB devices.
 */
class UsbUtil {
    public static List<UsbDevice> findAllPossibleAndroidDevices(UsbManager usbManager) {
        HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
    ArrayList<UsbDevice> androidDevices = new ArrayList<>(devices.size());
        for (UsbDevice device : devices.values()) {
            UsbDeviceConnection connection = openConnection(usbManager, device);
            if (AoapInterface.isSupported(connection)) {
                androidDevices.add(device);
            }
            connection.close();
        }
        return androidDevices;
    }

    public static UsbDeviceConnection openConnection(UsbManager manager, UsbDevice device) {
    manager.grantPermission(device);
    return manager.openDevice(device);
    }
    
    public static void sendAoapAccessoryStart(UsbDeviceConnection connection, String manufacturer,
            String model, String description, String version, String uri, String serial)
                    throws IOException {
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_MANUFACTURER,
                                 manufacturer);
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_MODEL,
                                 model);
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_DESCRIPTION,
                                 description);
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_VERSION,
                                 version);
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_URI, uri);
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_SERIAL,serial);
        AoapInterface.sendAoapStart(connection);
    }

    public static boolean isTheSameDevice(UsbDevice l, UsbDevice r) {
        if (TextUtils.equals(l.getManufacturerName(), r.getManufacturerName())
                && TextUtils.equals(l.getProductName(), r.getProductName())
                && TextUtils.equals(l.getSerialNumber(), r.getSerialNumber())) {
            return true;
        }
        return false;
    }

    public static boolean isDevicesMatching(UsbDevice l, UsbDevice r) {
        if (l.getVendorId() == r.getVendorId() && l.getProductId() == r.getProductId()
                && TextUtils.equals(l.getSerialNumber(), r.getSerialNumber())) {
            return true;
        }
        return false;
    }

    public static boolean isDeviceConnected(UsbManager usbManager, UsbDevice device) {
        HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
        for (UsbDevice dev : devices.values()) {
            if (isDevicesMatching(dev, device)) {
                return true;
            }
        }
        return false;
    }
}
