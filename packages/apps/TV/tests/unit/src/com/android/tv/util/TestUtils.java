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

package com.android.tv.util;

import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.hardware.hdmi.HdmiDeviceInfo;
import android.media.tv.TvInputInfo;
import android.os.Build;
import android.os.Bundle;

import java.lang.reflect.Constructor;

/**
 * A class that includes convenience methods for testing.
 */
public class TestUtils {
    /**
     * Creates a {@link TvInputInfo}.
     */
    public static TvInputInfo createTvInputInfo(ResolveInfo service, String id, String parentId,
            int type, boolean isHardwareInput) throws Exception {
        return createTvInputInfo(service, id, parentId, type, isHardwareInput, false, 0);
    }

    /**
     * Creates a {@link TvInputInfo}.
     * <p>
     * If this is called on MNC, {@code canRecord} and {@code tunerCount} are ignored.
     */
    public static TvInputInfo createTvInputInfo(ResolveInfo service, String id, String parentId,
            int type, boolean isHardwareInput, boolean canRecord, int tunerCount) throws Exception {
        // Create a mock TvInputInfo by using private constructor
        // Note that mockito doesn't support mock/spy on final object.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return createTvInputInfoForO(service, id, parentId, type, isHardwareInput, canRecord,
                    tunerCount);

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return createTvInputInfoForNyc(service, id, parentId, type, isHardwareInput, canRecord,
                    tunerCount);
        }
        return createTvInputInfoForMnc(service, id, parentId, type, isHardwareInput);
    }

    /**
     * private TvInputInfo(ResolveInfo service, String id, int type, boolean isHardwareInput,
     *      CharSequence label, int labelResId, Icon icon, Icon iconStandby, Icon iconDisconnected,
     *      String setupActivity, boolean canRecord, int tunerCount, HdmiDeviceInfo hdmiDeviceInfo,
     *      boolean isConnectedToHdmiSwitch, String parentId, Bundle extras) {
     */
    private static TvInputInfo createTvInputInfoForO(ResolveInfo service, String id,
            String parentId, int type, boolean isHardwareInput, boolean canRecord, int tunerCount)
            throws Exception {
        Constructor<TvInputInfo> constructor = TvInputInfo.class.getDeclaredConstructor(
                ResolveInfo.class, String.class, int.class, boolean.class, CharSequence.class,
                int.class, Icon.class, Icon.class, Icon.class, String.class, boolean.class,
                int.class, HdmiDeviceInfo.class, boolean.class, String.class, Bundle.class);
        constructor.setAccessible(true);
        return constructor.newInstance(service, id, type, isHardwareInput, null, 0, null, null,
                null, null, canRecord, tunerCount, null, false, parentId, null);
    }

    /**
     * private TvInputInfo(ResolveInfo service, String id, int type, boolean isHardwareInput,
     *      CharSequence label, int labelResId, Icon icon, Icon iconStandby, Icon iconDisconnected,
     *      String setupActivity, String settingsActivity, boolean canRecord, int tunerCount,
     *      HdmiDeviceInfo hdmiDeviceInfo, boolean isConnectedToHdmiSwitch, String parentId,
     *      Bundle extras) {
     */
    private static TvInputInfo createTvInputInfoForNyc(ResolveInfo service, String id,
            String parentId, int type, boolean isHardwareInput, boolean canRecord, int tunerCount)
            throws Exception {
        Constructor<TvInputInfo> constructor = TvInputInfo.class.getDeclaredConstructor(
                ResolveInfo.class, String.class, int.class, boolean.class, CharSequence.class,
                int.class, Icon.class, Icon.class, Icon.class, String.class, String.class,
                boolean.class, int.class, HdmiDeviceInfo.class, boolean.class, String.class,
                Bundle.class);
        constructor.setAccessible(true);
        return constructor.newInstance(service, id, type, isHardwareInput, null, 0, null, null,
                null, null, null, canRecord, tunerCount, null, false, parentId, null);
    }

    private static TvInputInfo createTvInputInfoForMnc(ResolveInfo service, String id,
            String parentId, int type, boolean isHardwareInput) throws Exception {
        Constructor<TvInputInfo> constructor = TvInputInfo.class.getDeclaredConstructor(
                ResolveInfo.class, String.class, String.class, int.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(service, id, parentId, type, isHardwareInput);
    }

    public static ResolveInfo createResolveInfo(String packageName, String name) {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.packageName = packageName;
        resolveInfo.serviceInfo.name = name;
        resolveInfo.serviceInfo.metaData = new Bundle();
        return resolveInfo;
    }
}
