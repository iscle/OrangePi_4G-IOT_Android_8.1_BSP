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
package com.google.android.car.kitchensink.setting.usb;

import android.content.ComponentName;
import android.hardware.usb.UsbDevice;

import com.android.internal.util.Preconditions;

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

    UsbDeviceSettings(String serialNumber, int vid, int pid) {
        Preconditions.checkNotNull(serialNumber);

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

    @Override
    public String toString() {
        return "UsbDeviceSettings{serial=" + getSerialNumber() + ", vid=" + mVid + "pid=" + mPid
                + "name=" + getDeviceName() + ", handler=" + getHandler().toString()
                + "aoap=" + getAoap() + "}";
    }

    /**
     * Checks if setting matches {@code UsbDevice}.
     */
    public boolean matchesDevice(UsbDevice device) {
        return (getSerialNumber().equals(device.getSerialNumber())
                && getVid() == device.getVendorId()
                && getPid() == device.getProductId());
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
