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
package com.android.compatibility.common.tradefed.targetprep;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.PushFilePreparer;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Pushes specified testing artifacts from Compatibility repository.
 */
@OptionClass(alias="file-pusher")
public class FilePusher extends PushFilePreparer implements IAbiReceiver {

    @Option(name = "append-bitness",
            description = "Append the ABI's bitness to the filename.")
    private boolean mAppendBitness = false;

    private CompatibilityBuildHelper mBuildHelper = null;

    private IAbi mAbi;

    private void setBuildHelper(IBuildInfo buildInfo) {
        if (mBuildHelper == null) {
            mBuildHelper = new CompatibilityBuildHelper(buildInfo);
        }
    }

    protected File getTestsDir(IBuildInfo buildInfo) throws FileNotFoundException {
        setBuildHelper(buildInfo);
        return mBuildHelper.getTestsDir();
    }

    protected File getTestFile(IBuildInfo buildInfo, String filename) throws FileNotFoundException {
        setBuildHelper(buildInfo);
        return mBuildHelper.getTestFile(filename);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IAbi getAbi() {
        return mAbi;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File resolveRelativeFilePath(IBuildInfo buildInfo, String fileName) {
        try {
            File f = getTestFile(buildInfo,
                    String.format("%s%s", fileName, mAppendBitness ? mAbi.getBitness() : ""));
            CLog.logAndDisplay(LogLevel.INFO, "Copying from %s", f.getAbsolutePath());
            return f;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}
