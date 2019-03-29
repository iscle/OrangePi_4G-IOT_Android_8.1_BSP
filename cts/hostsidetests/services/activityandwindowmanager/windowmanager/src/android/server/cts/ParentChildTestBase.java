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

package android.server.cts;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;

import android.server.cts.WindowManagerState.WindowState;
import android.server.cts.ActivityManagerTestBase;

public abstract class ParentChildTestBase extends ActivityManagerTestBase {
    private static final String COMPONENT_NAME = "android.server.FrameTestApp";

    interface ParentChildTest {
        void doTest(WindowState parent, WindowState child);
    }

    public void startTestCase(String testCase) throws Exception {
        setComponentName(COMPONENT_NAME);
        String cmd = getAmStartCmd(activityName(), intentKey(), testCase);
        CLog.logAndDisplay(LogLevel.INFO, cmd);
        executeShellCommand(cmd);
    }

    public void startTestCaseDocked(String testCase) throws Exception {
        setComponentName(COMPONENT_NAME);
        String cmd = getAmStartCmd(activityName(), intentKey(), testCase);
        CLog.logAndDisplay(LogLevel.INFO, cmd);
        executeShellCommand(cmd);
        moveActivityToDockStack(activityName());
    }

    abstract String intentKey();
    abstract String activityName();

    abstract void doSingleTest(ParentChildTest t) throws Exception;

    void doFullscreenTest(String testCase, ParentChildTest t) throws Exception {
        CLog.logAndDisplay(LogLevel.INFO, "Running test fullscreen");
        startTestCase(testCase);
        doSingleTest(t);
        stopTestCase();
    }

    void doDockedTest(String testCase, ParentChildTest t) throws Exception {
        CLog.logAndDisplay(LogLevel.INFO, "Running test docked");
        startTestCaseDocked(testCase);
        doSingleTest(t);
        stopTestCase();
    }

    void doParentChildTest(String testCase, ParentChildTest t) throws Exception {
        doFullscreenTest(testCase, t);
        doDockedTest(testCase, t);
    }
}
