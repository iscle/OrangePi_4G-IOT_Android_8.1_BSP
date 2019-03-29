/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.verifier.audio;

import android.content.Context;

import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.util.HashMap;
import java.util.Iterator;

public class UsbMicrophoneTester {

    public static String getUSBDeviceListString(Context context) {
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        StringBuilder sb = new StringBuilder();
        HashMap<String, UsbDevice> devicelist = manager.getDeviceList();
        for(UsbDevice usbDevice : devicelist.values()) {
            sb.append("Model    : " + usbDevice.getDeviceName() + "\n");
            sb.append(" Id      : " + usbDevice.getDeviceId() + "\n");
            sb.append(" Class   : " + usbDevice.getDeviceClass() + "\n");
            sb.append(" Prod.Id : " + usbDevice.getProductId() + "\n");
            sb.append(" Vendor.Id : " + usbDevice.getVendorId() + "\n");
            int iCount = usbDevice.getInterfaceCount();
            for (int i = 0; i < iCount; i++) {
                UsbInterface usbInterface = usbDevice.getInterface(i);
                sb.append("    Interface " + i + " :\n");
                sb.append("     Class: " + usbInterface.getInterfaceClass() + "\n");
            }
        }
        return sb.toString();
    }

    public static boolean getIsMicrophoneConnected(Context context) {
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        StringBuilder sb = new StringBuilder();
        HashMap<String, UsbDevice> devicelist = manager.getDeviceList();
        for(UsbDevice usbDevice : devicelist.values()) {
            int iCount = usbDevice.getInterfaceCount();
            for (int i = 0; i < iCount; i++) {
                UsbInterface usbInterface = usbDevice.getInterface(i);
                if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_AUDIO) {
                    return true;
                }
            }
        }
        return false;
    }
}