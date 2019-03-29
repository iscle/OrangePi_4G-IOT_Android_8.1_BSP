/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.compatibility.common.util;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.Instrumentation;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;

import java.io.FileInputStream;
import java.io.IOException;

public class SystemUtil {
    public static long getFreeDiskSize(Context context) {
        final StatFs statFs = new StatFs(context.getFilesDir().getAbsolutePath());
        return (long)statFs.getAvailableBlocks() * statFs.getBlockSize();
    }

    public static long getFreeMemory(Context context) {
        final MemoryInfo info = new MemoryInfo();
        ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(info);
        return info.availMem;
    }

    public static long getTotalMemory(Context context) {
        final MemoryInfo info = new MemoryInfo();
        ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(info);
        return info.totalMem;
    }

    /**
     * Executes a shell command using shell user identity, and return the standard output in string
     * <p>Note: calling this function requires API level 21 or above
     * @param instrumentation {@link Instrumentation} instance, obtained from a test running in
     * instrumentation framework
     * @param cmd the command to run
     * @return the standard output of the command
     * @throws Exception
     */
    public static String runShellCommand(Instrumentation instrumentation, String cmd)
            throws IOException {
        ParcelFileDescriptor pfd = instrumentation.getUiAutomation().executeShellCommand(cmd);
        byte[] buf = new byte[512];
        int bytesRead;
        FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
        StringBuffer stdout = new StringBuffer();
        while ((bytesRead = fis.read(buf)) != -1) {
            stdout.append(new String(buf, 0, bytesRead));
        }
        fis.close();
        return stdout.toString();
    }
}
