/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.car.internal;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Process;

/**
 * Represent an Android permission.
 *
 * @hide
 */
public class CarPermission {
    private final Context mContext;
    private final String mName;

    /** @hide */
    public CarPermission(Context context, String name) {
        mContext = context;
        mName = name;
    }

    /** @hide */
    public boolean checkGranted() {
        if (mName != null) {
            if (Binder.getCallingUid() != Process.myUid()) {
                return PackageManager.PERMISSION_GRANTED ==
                        mContext.checkCallingOrSelfPermission(mName);
            }
        }
        return true;
    }

    /** @hide */
    public void assertGranted() {
        if (checkGranted()) return;
        throw new SecurityException(
                "client does not have permission:"
                        + mName
                        + " pid:"
                        + Binder.getCallingPid()
                        + " uid:"
                        + Binder.getCallingUid());
    }
}
