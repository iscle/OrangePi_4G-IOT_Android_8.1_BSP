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

package android.os.cts;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.AbiUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OsHostTests extends DeviceTestCase implements IBuildReceiver, IAbiReceiver {
    private static final String TEST_APP_PACKAGE = "android.os.app";
    private static final String TEST_NON_EXPORTED_ACTIVITY_CLASS = "TestNonExported";

    private static final String START_NON_EXPORTED_ACTIVITY_COMMAND = String.format(
            "am start -n %s/%s.%s",
            TEST_APP_PACKAGE, TEST_APP_PACKAGE, TEST_NON_EXPORTED_ACTIVITY_CLASS);

    // Testing the intent filter verification mechanism
    private static final String HOST_VERIFICATION_APK = "CtsHostLinkVerificationApp.apk";
    private static final String HOST_VERIFICATION_PKG = "com.android.cts.openlinksskeleton";
    private static final String FILTER_VERIFIER_REGEXP =
            "Verifying IntentFilter\\..* package:\"" + HOST_VERIFICATION_PKG + "\"";
    private static final Pattern HOST_PATTERN = Pattern.compile(".*hosts:\"(.*?)\"");
    // domains that should be validated against given our test apk
    private static final String HOST_EXPLICIT = "explicit.example.com";
    private static final String HOST_WILDCARD = "wildcard.tld";

    /**
     * A reference to the device under test.
     */
    private ITestDevice mDevice;
    private IAbi mAbi;
    private IBuildInfo mCtsBuild;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Get the device, this gives a handle to run commands and install APKs.
        mDevice = getDevice();
    }

    /**
     * Test whether non-exported activities are properly not launchable.
     *
     * @throws Exception
     */
    public void testNonExportedActivities() throws Exception {
        // Attempt to launch the non-exported activity in the test app
        CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
        mDevice.executeShellCommand(START_NON_EXPORTED_ACTIVITY_COMMAND, outputReceiver);
        final String output = outputReceiver.getOutput();

        assertTrue(output.contains("Permission Denial") && output.contains(" not exported"));
    }

    public void testIntentFilterHostValidation() throws Exception {
        String line = null;
        try {
            // Clean slate in case of earlier aborted run
            mDevice.uninstallPackage(HOST_VERIFICATION_PKG);

            String[] options = { AbiUtils.createAbiFlag(mAbi.getName()) };

            mDevice.clearLogcat();

            String installResult = getDevice().installPackage(getTestAppFile(HOST_VERIFICATION_APK),
                    false /* = reinstall? */, options);

            assertNull("Couldn't install web intent filter sample apk", installResult);

            String logs = mDevice.executeAdbCommand("logcat", "-v", "brief", "-d");
            boolean foundVerifierOutput = false;
            Pattern verifierPattern = Pattern.compile(FILTER_VERIFIER_REGEXP);
            Scanner scanner = new Scanner(logs);
            while (scanner.hasNextLine()) {
                line = scanner.nextLine();
                Matcher verifierMatcher = verifierPattern.matcher(line);
                if (verifierMatcher.find()) {
                    Matcher m = HOST_PATTERN.matcher(line);
                    assertTrue(m.find());
                    final String hostgroup = m.group(1);
                    HashSet<String> allHosts = new HashSet<String>(
                            Arrays.asList(hostgroup.split(" ")));
                    assertEquals(2, allHosts.size());
                    assertTrue("AllHosts Contains: " + allHosts, allHosts.contains(HOST_EXPLICIT));
                    assertTrue("AllHosts Contains: " + allHosts, allHosts.contains(HOST_WILDCARD));
                    foundVerifierOutput = true;
                    break;
                }
            }

            assertTrue(foundVerifierOutput);
        } catch (Exception e) {
            fail("Unable to parse verification results: " + e.getMessage()
                    + " line=" + line);
        } finally {
            // Finally, uninstall the app
            mDevice.uninstallPackage(HOST_VERIFICATION_PKG);
        }
    }

    /*
     * Helper: find a test apk
     */
    private File getTestAppFile(String fileName) throws FileNotFoundException {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        return buildHelper.getTestFile(fileName);
    }
}
