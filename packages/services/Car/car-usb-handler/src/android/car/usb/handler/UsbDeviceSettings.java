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

import android.content.ComponentName;
import android.hardware.usb.UsbDevice;

/**
 * Settings for USB device.
 * @hide
 */
public final class UsbDeviceSettings {

    private final String mSerialNumber;
    private final int mVid;
    private final int mPid;
    private String mDeviceName;
    private ComponentName mHandler;
    private boolean mAoap;
    private boolean mDefaultHandler;

    UsbDeviceSettings(String serialNumber, int vid, int pid) {
        mSerialNumber = serialNumber;
        mVid = vid;
        mPid = pid;
    }

    public String getSerialNumber() {
        return mSerialNumber;
    }

    public int getVid() {
        return mVid;
    }

    public int getPid() {
        return mPid;
    }

    public void setDeviceName(String deviceName) {
        mDeviceName = deviceName;
    }

    public String getDeviceName() {
        return mDeviceName;
    }

    public void setHandler(ComponentName handler) {
        mHandler = handler;
    }

    public ComponentName getHandler() {
        return mHandler;
    }

    public void setAoap(boolean aoap) {
        mAoap = aoap;
    }

    public boolean getAoap() {
        return mAoap;
    }

    public void setDefaultHandler(boolean defaultHandler) {
        mDefaultHandler = defaultHandler;
    }

    public boolean isDefaultHandler() {
        return mDefaultHandler;
    }

    @Override
    public String toString() {
        return "UsbDeviceSettings{serial=" + mSerialNumber + ", vid=" + mVid + ", pid=" + mPid
                + ", name=" + mDeviceName + ", handler=" + mHandler.toString() + ", aoap=" + mAoap
                + ", default=" + mDefaultHandler + "}";
    }

    /**
     * Checks if setting matches {@code UsbDevice}.
     */
    public boolean matchesDevice(UsbDevice device) {
        String deviceSerial = device.getSerialNumber();
        if (AoapInterface.isDeviceInAoapMode(device)) {
            return mAoap && deviceSerial.equals(mSerialNumber);
        } else if (deviceSerial == null) {
            return mVid == device.getVendorId() && mPid == device.getProductId();
        } else {
            return mVid == device.getVendorId() && mPid == device.getProductId()
                    && deviceSerial.equals(mSerialNumber);
        }
    }

    /**
     * Creates settings from {@code UsbDevice}.
     */
    public static UsbDeviceSettings constructSettings(UsbDevice device) {
        UsbDeviceSettings settings = new UsbDeviceSettings(
                device.getSerialNumber(), device.getVendorId(), device.getProductId());
        settings.setDeviceName(device.getProductName());
        return settings;
    }

    /**
     * Creates settings from other settings.
     * <p>
     * Only basic properties are inherited.
     */
    public static UsbDeviceSettings constructSettings(UsbDeviceSettings origSettings) {
        UsbDeviceSettings settings = new UsbDeviceSettings(
                origSettings.getSerialNumber(), origSettings.getVid(), origSettings.getPid());
        settings.setDeviceName(origSettings.getDeviceName());
        return settings;
    }

    /**
     * Creates settings.
     */
    public static UsbDeviceSettings constructSettings(String serialNumber, int vid, int pid,
            String deviceName, ComponentName handler, boolean aoap) {
        UsbDeviceSettings settings = new UsbDeviceSettings(serialNumber, vid, pid);
        settings.setDeviceName(deviceName);
        settings.setHandler(handler);
        settings.setAoap(aoap);
        return settings;
    }
}
