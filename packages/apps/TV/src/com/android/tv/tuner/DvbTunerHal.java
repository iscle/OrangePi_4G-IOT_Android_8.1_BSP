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

package com.android.tv.tuner;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import com.android.tv.tuner.DvbDeviceAccessor.DvbDeviceInfoWrapper;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A class to handle a hardware Linux DVB API supported tuner device.
 */
public class DvbTunerHal extends TunerHal {

    private static final Object sLock = new Object();
    // @GuardedBy("sLock")
    private static final SortedSet<DvbDeviceInfoWrapper> sUsedDvbDevices = new TreeSet<>();

    private final DvbDeviceAccessor mDvbDeviceAccessor;
    private DvbDeviceInfoWrapper mDvbDeviceInfo;

    protected DvbTunerHal(Context context) {
        super(context);
        mDvbDeviceAccessor = new DvbDeviceAccessor(context);
    }

    @Override
    protected boolean openFirstAvailable() {
        List<DvbDeviceInfoWrapper> deviceInfoList = mDvbDeviceAccessor.getDvbDeviceList();
        if (deviceInfoList == null || deviceInfoList.isEmpty()) {
            Log.e(TAG, "There's no dvb device attached");
            return false;
        }
        synchronized (sLock) {
            for (DvbDeviceInfoWrapper deviceInfo : deviceInfoList) {
                if (!sUsedDvbDevices.contains(deviceInfo)) {
                    if (DEBUG) Log.d(TAG, "Available device info: " + deviceInfo);
                    mDvbDeviceInfo = deviceInfo;
                    sUsedDvbDevices.add(deviceInfo);
                    getDeliverySystemTypeFromDevice();
                    return true;
                }
            }
        }
        Log.e(TAG, "There's no available dvb devices");
        return false;
    }

    /**
     * Acquires the tuner device. The requested device will be locked to the current instance if
     * it's not acquired by others.
     *
     * @param deviceInfo a tuner device to open
     * @return {@code true} if the operation was successful, {@code false} otherwise
     */
    protected boolean open(DvbDeviceInfoWrapper deviceInfo) {
        if (deviceInfo == null) {
            Log.e(TAG, "Device info should not be null");
            return false;
        }
        if (mDvbDeviceInfo != null) {
            Log.e(TAG, "Already acquired");
            return false;
        }
        List<DvbDeviceInfoWrapper> deviceInfoList = mDvbDeviceAccessor.getDvbDeviceList();
        if (deviceInfoList == null || deviceInfoList.isEmpty()) {
            Log.e(TAG, "There's no dvb device attached");
            return false;
        }
        for (DvbDeviceInfoWrapper deviceInfoWrapper : deviceInfoList) {
            if (deviceInfoWrapper.compareTo(deviceInfo) == 0) {
                synchronized (sLock) {
                    if (sUsedDvbDevices.contains(deviceInfo)) {
                        Log.e(TAG, deviceInfo + " is already taken");
                        return false;
                    }
                    sUsedDvbDevices.add(deviceInfo);
                }
                if (DEBUG) Log.d(TAG, "Available device info: " + deviceInfo);
                mDvbDeviceInfo = deviceInfo;
                return true;
            }
        }
        Log.e(TAG, "There's no such dvb device attached");
        return false;
    }

    @Override
    public void close() {
        if (mDvbDeviceInfo != null) {
            if (isStreaming()) {
                stopTune();
            }
            nativeFinalize(mDvbDeviceInfo.getId());
            synchronized (sLock) {
                sUsedDvbDevices.remove(mDvbDeviceInfo);
            }
            mDvbDeviceInfo = null;
        }
    }

    @Override
    protected boolean isDeviceOpen() {
        return (mDvbDeviceInfo != null);
    }

    @Override
    protected long getDeviceId() {
        if (mDvbDeviceInfo != null) {
            return mDvbDeviceInfo.getId();
        }
        return -1;
    }

    @Override
    protected int openDvbFrontEndFd() {
        if (mDvbDeviceInfo != null) {
            ParcelFileDescriptor descriptor = mDvbDeviceAccessor.openDvbDevice(
                    mDvbDeviceInfo, DvbDeviceAccessor.DVB_DEVICE_FRONTEND);
            if (descriptor != null) {
                return descriptor.detachFd();
            }
        }
        return -1;
    }

    @Override
    protected int openDvbDemuxFd() {
        if (mDvbDeviceInfo != null) {
            ParcelFileDescriptor descriptor = mDvbDeviceAccessor.openDvbDevice(
                    mDvbDeviceInfo, DvbDeviceAccessor.DVB_DEVICE_DEMUX);
            if (descriptor != null) {
                return descriptor.detachFd();
            }
        }
        return -1;
    }

    @Override
    protected int openDvbDvrFd() {
        if (mDvbDeviceInfo != null) {
            ParcelFileDescriptor descriptor = mDvbDeviceAccessor.openDvbDevice(
                    mDvbDeviceInfo, DvbDeviceAccessor.DVB_DEVICE_DVR);
            if (descriptor != null) {
                return descriptor.detachFd();
            }
        }
        return -1;
    }

    /**
    * Gets the number of USB tuner devices currently present.
    */
    public static int getNumberOfDevices(Context context) {
        try {
            return (new DvbDeviceAccessor(context)).getNumOfDvbDevices();
        } catch (Exception e) {
            return 0;
        }
    }
}
