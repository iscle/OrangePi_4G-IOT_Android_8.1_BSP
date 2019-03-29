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
package com.android.compatibility.common.tradefed.result;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;


/**
 * A helper class for setting and checking whether an invocation has failed.
 */
public class InvocationFailureHandler {

    /**
     * Determine whether the invocation for this session has previously failed.
     *
     * @param buildHelper the {@link CompatibilityBuildHelper} from which to retrieve invocation
     * failure file
     * @return if invocation has previously failed
     */
    public static boolean hasFailed(final CompatibilityBuildHelper buildHelper) {
        try {
            File f = buildHelper.getInvocationFailureFile();
            return (f.exists() && f.length() != 0);
        } catch (FileNotFoundException e) {
            CLog.e("Could not find invocation failure file for session %s",
                CompatibilityBuildHelper.getDirSuffix(buildHelper.getStartTime()));
            CLog.e(e);
            return false;
        }
    }

    /**
     * Write the cause of invocation failure to the result's invocation failure file.
     *
     * @param buildHelper the {@link CompatibilityBuildHelper} from which to retrieve the
     * invocation failure file
     * @param cause the throwable responsible for invocation failure
     */
    public static void setFailed(final CompatibilityBuildHelper buildHelper, Throwable cause) {
        try {
            File f = buildHelper.getInvocationFailureFile();
            if (!f.exists()) {
                f.createNewFile();
                FileUtil.writeToFile(cause.toString(), f);
            }
        } catch (IOException e) {
            CLog.e("Exception while writing invocation failure file.");
            CLog.e(e);
        }
    }
}
