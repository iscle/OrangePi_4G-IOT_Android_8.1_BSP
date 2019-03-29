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
import com.android.compatibility.common.util.MetricsStore;
import com.android.compatibility.common.util.ReportLog;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.AbiUtils;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Measure time to taskswitching between two Apps: A & B
 * Actual test is done in device, but this host side code installs all necessary APKs
 * and starts device test which is in CtsDeviceTaskswitchingControl.
 */
public class TaskSwitchingTest extends DeviceTestCase implements IAbiReceiver, IBuildReceiver {
    private static final String TAG = "TaskSwitchingTest";
    private final static String RUNNER = "android.support.test.runner.AndroidJUnitRunner";
    private static final String RESULT_KEY = "COMPATIBILITY_TEST_RESULT";
    private IBuildInfo mBuild;
    private ITestDevice mDevice;
    private ReportLog mReport = null;
    private IAbi mAbi;

    static final String[] PACKAGES = {
        "android.taskswitching.control.cts",
        "android.taskswitching.appa",
        "android.taskswitching.appb"
    };
    static final String[] APKS = {
        "CtsDeviceTaskSwitchingControl.apk",
        "CtsDeviceTaskSwitchingAppA.apk",
        "CtsDeviceTaskSwitchingAppB.apk"
    };

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
        String[] options = {AbiUtils.createAbiFlag(mAbi.getName())};
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuild);
        for (int i = 0; i < PACKAGES.length; i++) {
            mDevice.uninstallPackage(PACKAGES[i]);
            File app = buildHelper.getTestFile(APKS[i]);
            mDevice.installPackage(app, false, options);
        }
    }


    @Override
    protected void tearDown() throws Exception {
        for (int i = 0; i < PACKAGES.length; i++) {
            mDevice.uninstallPackage(PACKAGES[i]);
        }
        super.tearDown();
    }

    public void testTaskSwitching() throws Exception {
        RemoteAndroidTestRunner testRunner = new RemoteAndroidTestRunner(PACKAGES[0], RUNNER,
                mDevice.getIDevice());
        LocalListener listener = new LocalListener();
        mDevice.runInstrumentationTests(testRunner, listener);
        TestRunResult result = listener.getCurrentRunResults();
        if (result.isRunFailure()) {
            fail(result.getRunFailureMessage());
        }
        assertNotNull("no performance data", mReport);
        MetricsStore.storeResult(mBuild, mAbi.getName(),
                String.format("%s#%s", getClass().getName(), "testTaskSwitching"), mReport);

    }

    public class LocalListener extends CollectingTestListener {
        @Override
        public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
            // necessary as testMetrics passed from CollectingTestListerner is empty
            if (testMetrics.containsKey(RESULT_KEY)) {
                try {
                    mReport = ReportLog.parse(testMetrics.get(RESULT_KEY));
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                }
            }
            super.testEnded(test, testMetrics);
        }
    }
}
