/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.os.Build;

public class CpuFeatures {

    public static final String ARMEABI_V7 = "armeabi-v7a";

    public static final String ARMEABI = "armeabi";

    public static final String MIPSABI = "mips";

    public static final  String X86ABI = "x86";

    public static final int HWCAP_VFP = (1 << 6);

    public static final int HWCAP_NEON = (1 << 12);

    public static final int HWCAP_VFPv3 = (1 << 13);

    public static final int HWCAP_VFPv4 = (1 << 16);

    public static final int HWCAP_IDIVA = (1 << 17);

    public static final int HWCAP_IDIVT = (1 << 18);

    static {
        System.loadLibrary("cts_jni");
    }

    public static native boolean isArmCpu();

    public static native boolean isArm7Compatible();

    public static native boolean isMipsCpu();

    public static native boolean isX86Cpu();

    public static native boolean isArm64Cpu();

    public static native boolean isMips64Cpu();

    public static native boolean isX86_64Cpu();

    public static native int getHwCaps();

    public static boolean isArm64CpuIn32BitMode() {
        if (!isArmCpu()) {
            return false;
        }

        for (String abi : Build.SUPPORTED_64_BIT_ABIS) {
            if (abi.equals("arm64-v8a")) {
                return true;
            }
        }

        return false;
    }
}
