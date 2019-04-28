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
import android.media.tv.TvInputManager;
import android.os.ParcelFileDescriptor;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.util.Log;

import com.android.tv.common.recording.RecordingCapability;
import com.android.tv.tuner.tvinput.TunerTvInputService;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Provides with the file descriptors to access DVB device.
 */
public class DvbDeviceAccessor {
    private static final String TAG = "DvbDeviceAccessor";

    @IntDef({DVB_DEVICE_DEMUX, DVB_DEVICE_DVR, DVB_DEVICE_FRONTEND})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DvbDevice {}
    public static final int DVB_DEVICE_DEMUX = 0; // TvInputManager.DVB_DEVICE_DEMUX;
    public static final int DVB_DEVICE_DVR = 1; // TvInputManager.DVB_DEVICE_DVR;
    public static final int DVB_DEVICE_FRONTEND = 2; // TvInputManager.DVB_DEVICE_FRONTEND;

    private static Method sGetDvbDeviceListMethod;
    private static Method sOpenDvbDeviceMethod;

    private final TvInputManager mTvInputManager;

    static {
        try {
            Class tvInputManagerClass = Class.forName("android.media.tv.TvInputManager");
            Class dvbDeviceInfoClass = Class.forName("android.media.tv.DvbDeviceInfo");
            sGetDvbDeviceListMethod = tvInputManagerClass.getDeclaredMethod("getDvbDeviceList");
            sGetDvbDeviceListMethod.setAccessible(true);
            sOpenDvbDeviceMethod = tvInputManagerClass.getDeclaredMethod(
                    "openDvbDevice", dvbDeviceInfoClass, Integer.TYPE);
            sOpenDvbDeviceMethod.setAccessible(true);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Couldn't find class", e);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Couldn't find method", e);
        }
    }

    public DvbDeviceAccessor(Context context) {
        mTvInputManager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
    }

    public List<DvbDeviceInfoWrapper> getDvbDeviceList() {
        try {
            List<DvbDeviceInfoWrapper> wrapperList = new ArrayList<>();
            List dvbDeviceInfoList = (List) sGetDvbDeviceListMethod.invoke(mTvInputManager);
            for (Object dvbDeviceInfo : dvbDeviceInfoList) {
                wrapperList.add(new DvbDeviceInfoWrapper(dvbDeviceInfo));
            }
            Collections.sort(wrapperList);
            return wrapperList;
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Couldn't access", e);
        } catch (InvocationTargetException e) {
            Log.e(TAG, "Couldn't invoke", e);
        }
        return null;
    }

    /**
     * Returns the number of currently connected DVB devices.
     */
    public int getNumOfDvbDevices() {
        List<DvbDeviceInfoWrapper> dvbDeviceList = getDvbDeviceList();
        return dvbDeviceList == null ? 0 : dvbDeviceList.size();
    }

    public boolean isDvbDeviceAvailable() {
        try {
            List dvbDeviceInfoList = (List) sGetDvbDeviceListMethod.invoke(mTvInputManager);
            return (!dvbDeviceInfoList.isEmpty());
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Couldn't access", e);
        } catch (InvocationTargetException e) {
            Log.e(TAG, "Couldn't invoke", e);
        }
        return false;
    }

    public ParcelFileDescriptor openDvbDevice(DvbDeviceInfoWrapper deviceInfo,
            @DvbDevice int device) {
        try {
            return (ParcelFileDescriptor) sOpenDvbDeviceMethod.invoke(
                    mTvInputManager, deviceInfo.getDvbDeviceInfo(), device);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Couldn't access", e);
        } catch (InvocationTargetException e) {
            Log.e(TAG, "Couldn't invoke", e);
        }
        return null;
    }

    /**
     * Returns the current recording capability for USB tuner.
     * @param inputId the input id to use.
     */
    public RecordingCapability getRecordingCapability(String inputId) {
        List<DvbDeviceInfoWrapper> deviceList = getDvbDeviceList();
        // TODO(DVR) implement accurate capabilities and updating values when needed.
        return RecordingCapability.builder()
                .setInputId(inputId)
                .setMaxConcurrentPlayingSessions(1)
                .setMaxConcurrentTunedSessions(deviceList.size())
                .setMaxConcurrentSessionsOfAllTypes(deviceList.size() + 1)
                .build();
    }

    public static class DvbDeviceInfoWrapper implements Comparable<DvbDeviceInfoWrapper> {
        private static Method sGetAdapterIdMethod;
        private static Method sGetDeviceIdMethod;
        private final Object mDvbDeviceInfo;
        private final int mAdapterId;
        private final int mDeviceId;
        private final long mId;

        static {
            try {
                Class dvbDeviceInfoClass = Class.forName("android.media.tv.DvbDeviceInfo");
                sGetAdapterIdMethod = dvbDeviceInfoClass.getDeclaredMethod("getAdapterId");
                sGetAdapterIdMethod.setAccessible(true);
                sGetDeviceIdMethod = dvbDeviceInfoClass.getDeclaredMethod("getDeviceId");
                sGetDeviceIdMethod.setAccessible(true);
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Couldn't find class", e);
            } catch (NoSuchMethodException e) {
                Log.e(TAG, "Couldn't find method", e);
            }
        }

        public DvbDeviceInfoWrapper(Object dvbDeviceInfo) {
            mDvbDeviceInfo = dvbDeviceInfo;
            mAdapterId = initAdapterId();
            mDeviceId = initDeviceId();
            mId = (((long) getAdapterId()) << 32) | (getDeviceId() & 0xffffffffL);
        }

        public long getId() {
            return mId;
        }

        public int getAdapterId() {
            return mAdapterId;
        }

        private int initAdapterId() {
            try {
                return (int) sGetAdapterIdMethod.invoke(mDvbDeviceInfo);
            } catch (InvocationTargetException e) {
                Log.e(TAG, "Couldn't invoke", e);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "Couldn't access", e);
            }
            return -1;
        }

        public int getDeviceId() {
            return mDeviceId;
        }

        private int initDeviceId() {
            try {
                return (int) sGetDeviceIdMethod.invoke(mDvbDeviceInfo);
            } catch (InvocationTargetException e) {
                Log.e(TAG, "Couldn't invoke", e);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "Couldn't access", e);
            }
            return -1;
        }

        public Object getDvbDeviceInfo() {
            return mDvbDeviceInfo;
        }

        @Override
        public int compareTo(@NonNull DvbDeviceInfoWrapper another) {
            if (getAdapterId() != another.getAdapterId()) {
                return getAdapterId() - another.getAdapterId();
            }
            return getDeviceId() - another.getDeviceId();
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "DvbDeviceInfo {adapterId: %d, deviceId: %d}",
                    getAdapterId(),
                    getDeviceId());
        }
    }
}
