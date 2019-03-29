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
package com.android.compatibility.common.deviceinfo;

import android.os.UserManager;

import com.android.compatibility.common.util.DeviceInfoStore;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * User device info collector.
 */
public final class UserDeviceInfo extends DeviceInfo {

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        store.addResult("max_supported_users", getMaxSupportedUsers());
    }

    private int getMaxSupportedUsers() {
        try {
            Method method = UserManager.class.getMethod("getMaxSupportedUsers");
            return (Integer) method.invoke(null);
        } catch (ClassCastException |
                NoSuchMethodException |
                InvocationTargetException |
                IllegalAccessException e) {}
        return -1;
    }
}
