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

package android.system.helpers;

import android.app.Instrumentation;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.system.helpers.CommandsHelper;

/**
 * Implement common helper methods for package management.
 * eg. Delete package data.
 */
public class PackageHelper {
    public final int TIMEOUT = 500;
    public static PackageHelper sInstance = null;
    private Instrumentation mInstrumentation = null;
    private CommandsHelper cmdHelper = null;
    private PackageManager mPackageManager = null;

    public PackageHelper(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
        cmdHelper = CommandsHelper.getInstance(instrumentation);
        mPackageManager = instrumentation.getTargetContext().getPackageManager();
    }

    public static PackageHelper getInstance(Instrumentation instrumentation) {
        if (sInstance == null) {
            sInstance = new PackageHelper(instrumentation);
        }
        return sInstance;
    }

    /**
     * Deletes all data associated with the package.
     * @param packageName package name
     */
    public void cleanPackage(String packageName) {
        cmdHelper.executeShellCommand(String.format("pm clear %s", packageName));
        SystemClock.sleep(2 * TIMEOUT);
    }

    /**
     * Check if certain package is installed on the device.
     * @param packageName package name
     * @return true/false
     */
    public Boolean isPackageInstalled(String packageName) {
        try {
            mPackageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
