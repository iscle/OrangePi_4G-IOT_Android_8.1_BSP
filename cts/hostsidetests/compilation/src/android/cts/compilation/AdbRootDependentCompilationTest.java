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

package android.cts.compilation;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Various integration tests for dex to oat compilation, with or without profiles.
 * When changing this test, make sure it still passes in each of the following
 * configurations:
 * <ul>
 *     <li>On a 'user' build</li>
 *     <li>On a 'userdebug' build with system property 'dalvik.vm.usejitprofiles' set to false</li>
 *     <li>On a 'userdebug' build with system property 'dalvik.vm.usejitprofiles' set to true</li>
 * </ul>
 */
public class AdbRootDependentCompilationTest extends DeviceTestCase {
    private static final String APPLICATION_PACKAGE = "android.cts.compilation";

    enum ProfileLocation {
        CUR("/data/misc/profiles/cur/0/" + APPLICATION_PACKAGE),
        REF("/data/misc/profiles/ref/" + APPLICATION_PACKAGE);

        private String directory;

        ProfileLocation(String directory) {
            this.directory = directory;
        }

        public String getDirectory() {
            return directory;
        }

        public String getPath() {
            return directory + "/primary.prof";
        }
    }

    private ITestDevice mDevice;
    private File textProfileFile;
    private byte[] initialOdexFileContents;
    private File apkFile;
    private boolean mCanEnableDeviceRootAccess;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = getDevice();

        String buildType = mDevice.getProperty("ro.build.type");
        assertTrue("Unknown build type: " + buildType,
                Arrays.asList("user", "userdebug", "eng").contains(buildType));
        boolean wasRoot = mDevice.isAdbRoot();
        // We can only enable root access on userdebug and eng builds.
        mCanEnableDeviceRootAccess = buildType.equals("userdebug") || buildType.equals("eng");

        apkFile = File.createTempFile("CtsCompilationApp", ".apk");
        try (OutputStream outputStream = new FileOutputStream(apkFile)) {
            InputStream inputStream = getClass().getResourceAsStream("/CtsCompilationApp.apk");
            ByteStreams.copy(inputStream, outputStream);
        }
        mDevice.uninstallPackage(APPLICATION_PACKAGE); // in case it's still installed
        mDevice.installPackage(apkFile, false);

        // Write the text profile to a temporary file so that we can run profman on it to create a
        // real profile.
        byte[] profileBytes = ByteStreams.toByteArray(
                getClass().getResourceAsStream("/primary.prof.txt"));
        assertTrue("empty profile", profileBytes.length > 0); // sanity check
        textProfileFile = File.createTempFile("compilationtest", "prof.txt");
        Files.write(profileBytes, textProfileFile);
    }

    @Override
    protected void tearDown() throws Exception {
        FileUtil.deleteFile(apkFile);
        FileUtil.deleteFile(textProfileFile);
        mDevice.uninstallPackage(APPLICATION_PACKAGE);
        super.tearDown();
    }

    /**
     * Tests compilation using {@code -r bg-dexopt -f}.
     */
    public void testCompile_bgDexopt() throws Exception {
        if (!canRunTest(EnumSet.noneOf(ProfileLocation.class))) {
            return;
        }
        // Usually "interpret-only"
        String expectedInstallFilter = checkNotNull(mDevice.getProperty("pm.dexopt.install"));
        // Usually "speed-profile"
        String expectedBgDexoptFilter = checkNotNull(mDevice.getProperty("pm.dexopt.bg-dexopt"));

        String odexPath = getOdexFilePath();
        assertEquals(expectedInstallFilter, getCompilerFilter(odexPath));

        // Without -f, the compiler would only run if it judged the bg-dexopt filter to
        // be "better" than the install filter. However manufacturers can change those
        // values so we don't want to depend here on the resulting filter being better.
        executeCompile("-r", "bg-dexopt", "-f");

        assertEquals(expectedBgDexoptFilter, getCompilerFilter(odexPath));
    }

    /*
     The tests below test the remaining combinations of the "ref" (reference) and
     "cur" (current) profile being available. The "cur" profile gets moved/merged
     into the "ref" profile when it differs enough; as of 2016-05-10, "differs
     enough" is based on number of methods and classes in profile_assistant.cc.

     No nonempty profile exists right after an app is installed.
     Once the app runs, a profile will get collected in "cur" first but
     may make it to "ref" later. While the profile is being processed by
     profile_assistant, it may only be available in "ref".
     */

    public void testCompile_noProfile() throws Exception {
        compileWithProfilesAndCheckFilter(false /* expectOdexChange */,
                EnumSet.noneOf(ProfileLocation.class));
    }

    public void testCompile_curProfile() throws Exception {
        boolean didRun = compileWithProfilesAndCheckFilter(true  /* expectOdexChange */,
                 EnumSet.of(ProfileLocation.CUR));
        if (didRun) {
            assertTrue("ref profile should have been created by the compiler",
                    doesFileExist(ProfileLocation.REF.getPath()));
        }
    }

    public void testCompile_refProfile() throws Exception {
        compileWithProfilesAndCheckFilter(false /* expectOdexChange */,
                 EnumSet.of(ProfileLocation.REF));
        // We assume that the compiler isn't smart enough to realize that the
        // previous odex was compiled before the ref profile was in place, even
        // though theoretically it could be.
    }

    public void testCompile_curAndRefProfile() throws Exception {
        compileWithProfilesAndCheckFilter(false /* expectOdexChange */,
                EnumSet.of(ProfileLocation.CUR, ProfileLocation.REF));
    }

    private byte[] readFileOnClient(String clientPath) throws Exception {
        assertTrue("File not found on client: " + clientPath,
                doesFileExist(clientPath));
        File copyOnHost = File.createTempFile("host", "copy");
        try {
            executePull(clientPath, copyOnHost.getPath());
            return Files.toByteArray(copyOnHost);
        } finally {
            FileUtil.deleteFile(copyOnHost);
        }
    }

    /**
     * Places the profile in the specified locations, recompiles (without -f)
     * and checks the compiler-filter in the odex file.
     *
     * @return whether the test ran (as opposed to early exit)
     */
    private boolean compileWithProfilesAndCheckFilter(boolean expectOdexChange,
            Set<ProfileLocation> profileLocations)
            throws Exception {
        if (!canRunTest(profileLocations)) {
            return false;
        }
        // ensure no profiles initially present
        for (ProfileLocation profileLocation : ProfileLocation.values()) {
            String clientPath = profileLocation.getPath();
            if (doesFileExist(clientPath)) {
                executeSuShellAdbCommand(0, "rm", clientPath);
            }
        }
        executeCompile("-m", "speed-profile", "-f");
        String odexFilePath = getOdexFilePath();
        byte[] initialOdexFileContents = readFileOnClient(odexFilePath);
        assertTrue("empty odex file", initialOdexFileContents.length > 0); // sanity check

        for (ProfileLocation profileLocation : profileLocations) {
            writeProfile(profileLocation);
        }
        executeCompile("-m", "speed-profile");

        // Confirm the compiler-filter used in creating the odex file
        String compilerFilter = getCompilerFilter(odexFilePath);

        assertEquals("compiler-filter", "speed-profile", compilerFilter);

        byte[] odexFileContents = readFileOnClient(odexFilePath);
        boolean odexChanged = !(Arrays.equals(initialOdexFileContents, odexFileContents));
        if (odexChanged && !expectOdexChange) {
            String msg = String.format(Locale.US, "Odex file without filters (%d bytes) "
                    + "unexpectedly different from odex file (%d bytes) compiled with filters: %s",
                    initialOdexFileContents.length, odexFileContents.length, profileLocations);
            fail(msg);
        } else if (!odexChanged && expectOdexChange) {
            fail("odex file should have changed when recompiling with " + profileLocations);
        }
        return true;
    }

    /**
     * Invokes the dex2oat compiler on the client.
     *
     * @param compileOptions extra options to pass to the compiler on the command line
     */
    private void executeCompile(String... compileOptions) throws Exception {
        List<String> command = new ArrayList<>(Arrays.asList("cmd", "package", "compile"));
        command.addAll(Arrays.asList(compileOptions));
        command.add(APPLICATION_PACKAGE);
        String[] commandArray = command.toArray(new String[0]);
        assertEquals("Success", executeSuShellAdbCommand(1, commandArray)[0]);
    }

    /**
     * Copies {@link #textProfileFile} to the device and convert it to a binary profile on the
     * client device.
     */
    private void writeProfile(ProfileLocation location) throws Exception {
        String targetPath = location.getPath();
        // Get the owner of the parent directory so we can set it on the file
        String targetDir = location.getDirectory();
        if (!doesFileExist(targetDir)) {
            fail("Not found: " + targetPath);
        }
        // in format group:user so we can directly pass it to chown
        String owner = executeSuShellAdbCommand(1, "stat", "-c", "%U:%g", targetDir)[0];
        // for some reason, I've observed the output starting with a single space
        while (owner.startsWith(" ")) {
            owner = owner.substring(1);
        }

        String targetPathTemp = targetPath + ".tmp";
        executePush(textProfileFile.getAbsolutePath(), targetPathTemp, targetDir);
        assertTrue("Failed to push text profile", doesFileExist(targetPathTemp));

        String targetPathApk = targetPath + ".apk";
        executePush(apkFile.getAbsolutePath(), targetPathApk, targetDir);
        assertTrue("Failed to push APK from ", doesFileExist(targetPathApk));
        // Run profman to create the real profile on device.
        try {
            String pathSpec = executeSuShellAdbCommand(1, "pm", "path", APPLICATION_PACKAGE)[0];
            pathSpec = pathSpec.replace("package:", "");
            assertTrue("Failed find APK " + pathSpec, doesFileExist(pathSpec));
            executeSuShellAdbCommand(
                "profman",
                "--create-profile-from=" + targetPathTemp,
                "--apk=" + pathSpec,
                "--dex-location=" + pathSpec,
                "--reference-profile-file=" + targetPath);
        } catch (Exception e) {
            assertEquals("", e.toString());
        }
        executeSuShellAdbCommand(0, "chown", owner, targetPath);
        // Verify that the file was written successfully
        assertTrue("failed to create profile file", doesFileExist(targetPath));
        String[] result = executeSuShellAdbCommand(1, "stat", "-c", "%s", targetPath);
        assertTrue("profile " + targetPath + " is " + Integer.parseInt(result[0]) + " bytes",
                   Integer.parseInt(result[0]) > 0);
    }

    /**
     * Parses the value for the key "compiler-filter" out of the output from
     * {@code oatdump --header-only}.
     */
    private String getCompilerFilter(String odexFilePath) throws DeviceNotAvailableException {
        String[] response = executeSuShellAdbCommand(
                "oatdump", "--header-only", "--oat-file=" + odexFilePath);
        String prefix = "compiler-filter =";
        for (String line : response) {
            line = line.trim();
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        fail("No occurence of \"" + prefix + "\" in: " + Arrays.toString(response));
        return null;
    }

    /**
     * Returns the path to the application's base.odex file that should have
     * been created by the compiler.
     */
    private String getOdexFilePath() throws DeviceNotAvailableException {
        // Something like "package:/data/app/android.cts.compilation-1/base.apk"
        String pathSpec = executeSuShellAdbCommand(1, "pm", "path", APPLICATION_PACKAGE)[0];
        Matcher matcher = Pattern.compile("^package:(.+/)base\\.apk$").matcher(pathSpec);
        boolean found = matcher.find();
        assertTrue("Malformed spec: " + pathSpec, found);
        String apkDir = matcher.group(1);
        // E.g. /data/app/android.cts.compilation-1/oat/arm64/base.odex
        String result = executeSuShellAdbCommand(1, "find", apkDir, "-name", "base.odex")[0];
        assertTrue("odex file not found: " + result, doesFileExist(result));
        return result;
    }

    /**
     * Returns whether a test that uses the given profileLocations can run
     * in the current device configuration. This allows tests to exit early.
     *
     * <p>Ideally we'd like tests to be marked as skipped/ignored or similar
     * rather than passing if they can't run on the current device, but that
     * doesn't seem to be supported by CTS as of 2016-05-24.
     * TODO: Use Assume.assumeTrue() if this test gets converted to JUnit 4.
     */
    private boolean canRunTest(Set<ProfileLocation> profileLocations) throws Exception {
        boolean result = mCanEnableDeviceRootAccess &&
                (profileLocations.isEmpty() || isUseJitProfiles());
        if (!result) {
            System.err.printf("Skipping test [mCanEnableDeviceRootAccess=%s, %d profiles] on %s\n",
                    mCanEnableDeviceRootAccess, profileLocations.size(), mDevice);
        }
        return result;
    }

    private boolean isUseJitProfiles() throws Exception {
        boolean propUseJitProfiles = Boolean.parseBoolean(
                executeSuShellAdbCommand(1, "getprop", "dalvik.vm.usejitprofiles")[0]);
        return propUseJitProfiles;
    }

    private String[] executeSuShellAdbCommand(int numLinesOutputExpected, String... command)
            throws DeviceNotAvailableException {
        String[] lines = executeSuShellAdbCommand(command);
        assertEquals(
                String.format(Locale.US, "Expected %d lines output, got %d running %s: %s",
                        numLinesOutputExpected, lines.length, Arrays.toString(command),
                        Arrays.toString(lines)),
                numLinesOutputExpected, lines.length);
        return lines;
    }

    private String[] executeSuShellAdbCommand(String... command)
            throws DeviceNotAvailableException {
        // Add `shell su root` to the adb command.
        String cmdString = String.join(" ", command);
        String output = mDevice.executeShellCommand("su root " + cmdString);
        // "".split() returns { "" }, but we want an empty array
        String[] lines = output.equals("") ? new String[0] : output.split("\n");
        return lines;
    }

    private String getSelinuxLabel(String path) throws DeviceNotAvailableException {
        // ls -aZ (-a so it sees directories, -Z so it prints the label).
        String[] res = executeSuShellAdbCommand(String.format(
            "ls -aZ '%s'", path));

        if (res.length == 0) {
          return null;
        }

        // For directories, it will print many outputs. Filter to first line which contains '.'
        // The target line will look like
        //      "u:object_r:shell_data_file:s0 /data/local/tmp/android.cts.compilation.primary.prof"
        // Remove the second word to only return "u:object_r:shell_data_file:s0".

        return res[0].replaceAll("\\s+.*","");  // remove everything following the first whitespace
    }

    private void checkSelinuxLabelMatches(String a, String b) throws DeviceNotAvailableException {
      String labelA = getSelinuxLabel(a);
      String labelB = getSelinuxLabel(b);

      assertEquals("expected the selinux labels to match", labelA, labelB);
    }

    private void executePush(String hostPath, String targetPath, String targetDirectory)
            throws DeviceNotAvailableException {
        // Cannot push to a privileged directory with one command.
        // (i.e. there is no single-command equivalent of 'adb root; adb push src dst')
        //
        // Push to a tmp directory and then move it to the final destination
        // after updating the selinux label.
        String tmpPath = "/data/local/tmp/" + APPLICATION_PACKAGE + ".push.tmp";
        assertTrue(mDevice.pushFile(new File(hostPath), tmpPath));

        // Important: Use "cp" here because it newly copied files will inherit the security context
        // of the targetDirectory according to the default policy.
        //
        // (Other approaches, such as moving the file retain the invalid security context
        // of the tmp directory - b/37425296)
        //
        // This mimics the behavior of 'adb root; adb push $targetPath'.
        executeSuShellAdbCommand("mv", tmpPath, targetPath);

        // Important: Use "restorecon" here because the file in tmpPath retains the
        // incompatible security context of /data/local/tmp.
        //
        // This mimics the behavior of 'adb root; adb push $targetPath'.
        executeSuShellAdbCommand("restorecon", targetPath);

        // Validate that the security context of the file matches the security context
        // of the directory it was pushed to.
        //
        // This is a reasonable default behavior to check because most selinux policies
        // are configured to behave like this.
        checkSelinuxLabelMatches(targetDirectory, targetPath);
    }

    private void executePull(String targetPath, String hostPath)
            throws DeviceNotAvailableException {
        String tmpPath = "/data/local/tmp/" + APPLICATION_PACKAGE + ".pull.tmp";
        executeSuShellAdbCommand("cp", targetPath, tmpPath);
        try {
            executeSuShellAdbCommand("chmod", "606", tmpPath);
            assertTrue(mDevice.pullFile(tmpPath, new File(hostPath)));
        } finally {
            executeSuShellAdbCommand("rm", tmpPath);
        }
    }

    private boolean doesFileExist(String path) throws DeviceNotAvailableException {
        String[] result = executeSuShellAdbCommand("ls", path);
        // Testing for empty directories will return an empty array.
        return !(result.length > 0 && result[0].contains("No such file"));
    }
}
