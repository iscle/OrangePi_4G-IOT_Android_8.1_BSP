/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.ui.cts;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.MeasureRun;
import com.android.compatibility.common.util.MeasureTime;
import com.android.compatibility.common.util.MetricsReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.compatibility.common.util.Stat;
import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.AbiUtils;

import java.io.File;

/**
 * Test to measure installation time of a APK.
 */
public class InstallTimeTest extends DeviceTestCase implements IAbiReceiver, IBuildReceiver {
    private IBuildInfo mBuild;
    private ITestDevice mDevice;
    private IAbi mAbi;

    private static final String TAG = "InstallTimeTest";
    private static final String REPORT_LOG_NAME = "CtsUiHostTestCases";
    static final String PACKAGE = "com.replica.replicaisland";
    static final String APK = "com.replica.replicaisland.apk";
    private static final double OUTLIER_THRESHOLD = 0.1;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = getDevice();
    }


    @Override
    protected void tearDown() throws Exception {
        mDevice.uninstallPackage(PACKAGE);
        super.tearDown();
    }

    public void testInstallTime() throws Exception {
        String streamName = "test_install_time";
        MetricsReportLog report = new MetricsReportLog(mBuild, mAbi.getName(),
                String.format("%s#%s", getClass().getName(), "testInstallTime"), REPORT_LOG_NAME,
                streamName);
        final int NUMBER_REPEAT = 10;
        final CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuild);
        final ITestDevice device = mDevice;
        double[] result = MeasureTime.measure(NUMBER_REPEAT, new MeasureRun() {
            @Override
            public void prepare(int i) throws Exception {
                device.uninstallPackage(PACKAGE);
            }
            @Override
            public void run(int i) throws Exception {
                File app = buildHelper.getTestFile(APK);
                String[] options = {AbiUtils.createAbiFlag(mAbi.getName())};
                device.installPackage(app, false, options);
            }
        });
        report.addValues("install_time", result, ResultType.LOWER_BETTER, ResultUnit.MS);
        Stat.StatResult stat = Stat.getStatWithOutlierRejection(result, OUTLIER_THRESHOLD);
        if (stat.mDataCount != result.length) {
            Log.w(TAG, "rejecting " + (result.length - stat.mDataCount) + " outliers");
        }
        report.setSummary("install_time_average", stat.mAverage, ResultType.LOWER_BETTER,
                ResultUnit.MS);
        report.submit();
    }

}
