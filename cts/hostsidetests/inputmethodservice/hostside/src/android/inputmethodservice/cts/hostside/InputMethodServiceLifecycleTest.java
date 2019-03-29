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

package android.inputmethodservice.cts.hostside;

import static android.inputmethodservice.cts.common.DeviceEventConstants.ACTION_DEVICE_EVENT;
import static android.inputmethodservice.cts.common.DeviceEventConstants.DeviceEventType.TEST_START;
import static android.inputmethodservice.cts.common.DeviceEventConstants.EXTRA_EVENT_SENDER;
import static android.inputmethodservice.cts.common.DeviceEventConstants.EXTRA_EVENT_TYPE;
import static android.inputmethodservice.cts.common.DeviceEventConstants.RECEIVER_COMPONENT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.inputmethodservice.cts.common.EventProviderConstants.EventTableConstants;
import android.inputmethodservice.cts.common.Ime1Constants;
import android.inputmethodservice.cts.common.Ime2Constants;
import android.inputmethodservice.cts.common.test.DeviceTestConstants;
import android.inputmethodservice.cts.common.test.ShellCommandUtils;
import android.inputmethodservice.cts.common.test.TestInfo;

import com.android.compatibility.common.tradefed.testtype.CompatibilityHostTestBase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class InputMethodServiceLifecycleTest extends CompatibilityHostTestBase {

    private String mDefaultImeId;

    @Before
    public void setUp() throws Exception {
        // Skip whole tests when DUT has no android.software.input_methods feature.
        assumeTrue(Boolean.parseBoolean(shell(
                ShellCommandUtils.hasFeature(ShellCommandUtils.FEATURE_INPUT_METHODS))));
        mDefaultImeId = shell(ShellCommandUtils.getCurrentIme());
        cleanUpTestImes();
        shell(ShellCommandUtils.deleteContent(EventTableConstants.CONTENT_URI));
    }

    @After
    public void tearDown() throws Exception {
        shell(ShellCommandUtils.setCurrentIme(mDefaultImeId));
        cleanUpTestImes();
    }

    @Test
    public void testSwitchIme() throws Exception {
        final TestInfo testSwitchIme1ToIme2 = new TestInfo(DeviceTestConstants.PACKAGE,
                DeviceTestConstants.TEST_CLASS, DeviceTestConstants.TEST_SWITCH_IME1_TO_IME2);
        sendTestStartEvent(testSwitchIme1ToIme2);
        installPackage(Ime1Constants.APK, "-r");
        installPackage(Ime2Constants.APK, "-r");
        shell(ShellCommandUtils.enableIme(Ime1Constants.IME_ID));
        shell(ShellCommandUtils.enableIme(Ime2Constants.IME_ID));
        shell(ShellCommandUtils.setCurrentIme(Ime1Constants.IME_ID));

        assertTrue(runDeviceTestMethod(testSwitchIme1ToIme2));
    }

    @Test
    public void testUninstallCurrentIme() throws Exception {
        installAndSetIme1();

        final TestInfo testIme1IsNotCurrentIme = new TestInfo(DeviceTestConstants.PACKAGE,
                DeviceTestConstants.TEST_CLASS, DeviceTestConstants.TEST_IME1_IS_NOT_CURRENT_IME);
        sendTestStartEvent(testIme1IsNotCurrentIme);
        uninstallPackageIfExists(Ime1Constants.PACKAGE);
        assertTrue(runDeviceTestMethod(testIme1IsNotCurrentIme));
        assertEquals(shell(ShellCommandUtils.getCurrentIme()), mDefaultImeId);
    }

    @Test
    public void testDisableCurrentIme() throws Exception {
        installAndSetIme1();

        final TestInfo testIme1IsNotCurrentIme = new TestInfo(DeviceTestConstants.PACKAGE,
                DeviceTestConstants.TEST_CLASS, DeviceTestConstants.TEST_IME1_IS_NOT_CURRENT_IME);
        sendTestStartEvent(testIme1IsNotCurrentIme);
        shell(ShellCommandUtils.disableIme(Ime1Constants.IME_ID));
        assertTrue(runDeviceTestMethod(testIme1IsNotCurrentIme));
        assertEquals(shell(ShellCommandUtils.getCurrentIme()), mDefaultImeId);
    }

    @Test
    public void testSearchView_giveFocusShowIme() throws Exception {
        installAndSetIme1();

        final TestInfo testGiveFocusShowIme1 = new TestInfo(DeviceTestConstants.PACKAGE,
                DeviceTestConstants.TEST_CLASS,
                DeviceTestConstants.TEST_SEARCH_VIEW_GIVE_FOCUS_SHOW_IME1);
        sendTestStartEvent(testGiveFocusShowIme1);
        assertTrue(runDeviceTestMethod(testGiveFocusShowIme1));
    }

    @Test
    public void testSearchView_setQueryHideIme() throws Exception {
        installAndSetIme1();

        final TestInfo testSetQueryHideIme1 = new TestInfo(DeviceTestConstants.PACKAGE,
                DeviceTestConstants.TEST_CLASS,
                DeviceTestConstants.TEST_SEARCH_VIEW_SET_QUERY_HIDE_IME1);
        sendTestStartEvent(testSetQueryHideIme1);
        assertTrue(runDeviceTestMethod(testSetQueryHideIme1));
    }

    @Test
    public void testOnStartInputCalledOnce() throws Exception {
        installAndSetIme1();

        final TestInfo testSetQueryHideIme1 = new TestInfo(DeviceTestConstants.PACKAGE,
                DeviceTestConstants.TEST_CLASS,
                DeviceTestConstants.TEST_ON_START_INPUT_CALLED_ONCE_IME1);
        sendTestStartEvent(testSetQueryHideIme1);
        assertTrue(runDeviceTestMethod(testSetQueryHideIme1));
    }

    private void installAndSetIme1() throws Exception {
        final TestInfo testCreateIme1 = new TestInfo(DeviceTestConstants.PACKAGE,
            DeviceTestConstants.TEST_CLASS, DeviceTestConstants.TEST_CREATE_IME1);
        sendTestStartEvent(testCreateIme1);
        installPackage(Ime1Constants.APK, "-r");
        shell(ShellCommandUtils.enableIme(Ime1Constants.IME_ID));
        shell(ShellCommandUtils.setCurrentIme(Ime1Constants.IME_ID));
        assertTrue(runDeviceTestMethod(testCreateIme1));
    }

    private void sendTestStartEvent(final TestInfo deviceTest) throws Exception {
        final String sender = deviceTest.getTestName();
        // {@link EventType#EXTRA_EVENT_TIME} will be recorded at device side.
        shell(ShellCommandUtils.broadcastIntent(
                ACTION_DEVICE_EVENT, RECEIVER_COMPONENT,
                "--es", EXTRA_EVENT_SENDER, sender,
                "--es", EXTRA_EVENT_TYPE, TEST_START.name()));
    }

    private boolean runDeviceTestMethod(final TestInfo deviceTest) throws Exception {
        return runDeviceTests(deviceTest.testPackage, deviceTest.testClass, deviceTest.testMethod);
    }

    private String shell(final String command) throws Exception {
        return getDevice().executeShellCommand(command).trim();
    }

    private void cleanUpTestImes() throws Exception {
        uninstallPackageIfExists(Ime1Constants.PACKAGE);
        uninstallPackageIfExists(Ime2Constants.PACKAGE);
    }

    private void uninstallPackageIfExists(final String packageName) throws Exception {
        if (isPackageInstalled(packageName)) {
            uninstallPackage(packageName);
        }
    }
}
