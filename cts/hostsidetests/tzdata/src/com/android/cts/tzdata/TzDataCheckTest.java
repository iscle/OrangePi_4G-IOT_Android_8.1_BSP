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

package com.android.cts.tzdata;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.timezone.distro.DistroVersion;
import com.android.timezone.distro.TimeZoneDistro;
import com.android.timezone.distro.tools.TimeZoneDistroBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.StringJoiner;
import java.util.function.Consumer;
import libcore.tzdata.testing.ZoneInfoTestHelper;

import static org.junit.Assert.assertArrayEquals;

/**
 * Tests for the tzdatacheck binary.
 *
 * <p>The tzdatacheck binary operates over two directories: the "system directory" containing the
 * time zone rules in the system image, and a "data directory" in the data partition which can
 * optionally contain time zone rules data files for bionic/libcore and ICU.
 *
 * <p>This test executes the tzdatacheck binary to confirm it operates correctly in a number of
 * simulated situations; simulated system and data directories in various states are created in a
 * location the shell user has permission to access and the tzdatacheck binary is then executed.
 * The status code and directory state after execution is then used to determine if the tzdatacheck
 * binary operated correctly.
 *
 * <p>Most of the tests below prepare simulated directory structure for the system and data dirs
 * on the host before pushing them to the device. Device state is then checked rather than syncing
 * the files back.
 */
public class TzDataCheckTest extends DeviceTestCase {

    /**
     * The name of the directory containing the current time zone rules data beneath
     * {@link #mDataDir}.  Also known to {@link com.android.timezone.distro.installer.TimeZoneDistroInstaller} and
     * tzdatacheck.cpp.
     */
    private static final String CURRENT_DIR_NAME = "current";

    /**
     * The name of the directory containing the staged time zone rules data beneath
     * {@link #mDataDir}.  Also known to {@link com.android.timezone.distro.installer.TimeZoneDistroInstaller} and
     * tzdatacheck.cpp.
     */
    private static final String STAGED_DIR_NAME = "staged";

    /**
     * The name of the file inside the staged directory that indicates the staged operation is an
     * uninstall. Also known to {@link com.android.timezone.distro.installer.TimeZoneDistroInstaller} and
     * tzdatacheck.cpp.
     */
    private static final String UNINSTALL_TOMBSTONE_FILE_NAME = "STAGED_UNINSTALL_TOMBSTONE";

    /**
     * The name of the /system time zone data file. Also known to
     * {@link com.android.timezone.distro.installer.TimeZoneDistroInstaller} and tzdatacheck.cpp.
     */
    private static final String SYSTEM_TZDATA_FILE_NAME = "tzdata";

    /** A valid time zone rules version guaranteed to be older than {@link #RULES_VERSION_TWO} */
    private static final String RULES_VERSION_ONE = "2016g";
    /** A valid time zone rules version guaranteed to be newer than {@link #RULES_VERSION_ONE} */
    private static final String RULES_VERSION_TWO = "2016h";
    /**
     * An arbitrary, valid time zone rules version used when it doesn't matter what the rules
     * version is.
     */
    private static final String VALID_RULES_VERSION = RULES_VERSION_ONE;

    /** An arbitrary valid revision number. */
    private static final int VALID_REVISION = 1;

    private String mDeviceAndroidRootDir;
    private PathPair mTestRootDir;
    private PathPair mSystemDir;
    private PathPair mDataDir;

    public void setUp() throws Exception {
        super.setUp();

        // It's not clear how we would get this without invoking "/system/bin/sh", but we need the
        // value first to do so. It has been hardcoded instead.
        mDeviceAndroidRootDir = "/system";

        // Create a test root directory on host and device.
        Path hostTestRootDir = Files.createTempDirectory("tzdatacheck_test");
        mTestRootDir = new PathPair(
                hostTestRootDir,
                "/data/local/tmp/tzdatacheck_test");
        createDeviceDirectory(mTestRootDir);

        // tzdatacheck requires two directories: a "system" path and a "data" path.
        mSystemDir = mTestRootDir.createSubPath("system_dir");
        mDataDir = mTestRootDir.createSubPath("data_dir");

        // Create the host-side directory structure (for preparing files before pushing them to
        // device and looking at files retrieved from device).
        createHostDirectory(mSystemDir);
        createHostDirectory(mDataDir);

        // Create the equivalent device-side directory structure for receiving files.
        createDeviceDirectory(mSystemDir);
        createDeviceDirectory(mDataDir);
    }

    @Override
    public void tearDown() throws Exception {
        // Remove the test root directories that have been created by this test.
        deleteHostDirectory(mTestRootDir, true /* failOnError */);
        deleteDeviceDirectory(mTestRootDir, true /* failOnError */);
        super.tearDown();
    }

    public void testTooFewArgs() throws Exception {
        // No need to set up or push files to the device for this test.
        assertEquals(1, runTzDataCheckWithArgs(new String[0]));
        assertEquals(1, runTzDataCheckWithArgs(new String[] { "oneArg" }));
    }

    // {dataDir}/staged exists but it is a file.
    public void testStaging_stagingDirIsFile() throws Exception {
        // Set up the /system directory structure on host.
        createSystemTzDataFileOnHost(VALID_RULES_VERSION);

        // Set up the /data directory structure on host.
        PathPair dataStagedDir = mDataDir.createSubPath(STAGED_DIR_NAME);
        // Create a file with the same name as the directory that tzdatacheck expects.
        Files.write(dataStagedDir.hostPath, new byte[] { 'a' });

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code. Failures due to staging issues are
        // generally ignored providing the device is left in a reasonable state.
        assertEquals(0, runTzDataCheckOnDevice());

        // Assert the file was just ignored. This is a fairly arbitrary choice to leave it rather
        // than delete.
        assertDevicePathExists(dataStagedDir);
        assertDevicePathIsFile(dataStagedDir);
    }

    // {dataDir}/staged exists but /current dir is a file.
    public void testStaging_uninstall_currentDirIsFile() throws Exception {
        // Set up the /system directory structure on host.
        createSystemTzDataFileOnHost(VALID_RULES_VERSION);

        // Set up the /data directory structure on host.

        // Create a staged uninstall.
        PathPair dataStagedDir = mDataDir.createSubPath(STAGED_DIR_NAME);
        createStagedUninstallOnHost(dataStagedDir);

        // Create a file with the same name as the directory that tzdatacheck expects.
        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);
        Files.write(dataCurrentDir.hostPath, new byte[] { 'a' });

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code.
        assertEquals(0, runTzDataCheckOnDevice());

        // Assert the device was left in a valid "uninstalled" state.
        assertDevicePathDoesNotExist(dataStagedDir);
        assertDevicePathDoesNotExist(dataCurrentDir);
    }

    // {dataDir}/staged contains an uninstall, but there is nothing to uninstall.
    public void testStaging_uninstall_noCurrent() throws Exception {
        // Set up the /system directory structure on host.
        createSystemTzDataFileOnHost(VALID_RULES_VERSION);

        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);

        // Set up the /data directory structure on host.

        // Create a staged uninstall.
        PathPair dataStagedDir = mDataDir.createSubPath(STAGED_DIR_NAME);
        createStagedUninstallOnHost(dataStagedDir);

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code. Failures due to staging issues are
        // generally ignored providing the device is left in a reasonable state.
        assertEquals(0, runTzDataCheckOnDevice());

        // Assert the device was left in a valid "uninstalled" state.
        assertDevicePathDoesNotExist(dataStagedDir);
        assertDevicePathDoesNotExist(dataCurrentDir);
    }

    // {dataDir}/staged contains an uninstall, and there is something to uninstall.
    public void testStaging_uninstall_withCurrent() throws Exception {
        // Set up the /system directory structure on host.
        createSystemTzDataFileOnHost(VALID_RULES_VERSION);

        // Set up the /data directory structure on host.

        // Create a staged uninstall.
        PathPair dataStagedDir = mDataDir.createSubPath(STAGED_DIR_NAME);
        createStagedUninstallOnHost(dataStagedDir);

        // Create a current installed distro.
        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);
        byte[] distroBytes = createValidDistroBuilder().buildBytes();
        unpackOnHost(dataCurrentDir, distroBytes);

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code. Failures due to staging issues are
        // generally ignored providing the device is left in a reasonable state.
        assertEquals(0, runTzDataCheckOnDevice());

        // Assert the device was left in a valid "uninstalled" state.
        assertDevicePathDoesNotExist(dataStagedDir);
        assertDevicePathDoesNotExist(dataCurrentDir);
    }

    // {dataDir}/staged exists but /current dir is a file.
    public void testStaging_install_currentDirIsFile() throws Exception {
        // Set up the /system directory structure on host.
        createSystemTzDataFileOnHost(VALID_RULES_VERSION);

        // Set up the /data directory structure on host.

        // Create a staged install.
        PathPair dataStagedDir = mDataDir.createSubPath(STAGED_DIR_NAME);
        byte[] distroBytes = createValidDistroBuilder().buildBytes();
        unpackOnHost(dataStagedDir, distroBytes);

        // Create a file with the same name as the directory that tzdatacheck expects.
        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);
        Files.write(dataCurrentDir.hostPath, new byte[] { 'a' });

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code. Failures due to staging issues are
        // generally ignored providing the device is left in a reasonable state.
        assertEquals(0, runTzDataCheckOnDevice());

        // Assert the device was left in a valid "installed" state.
        assertDevicePathDoesNotExist(dataStagedDir);
        assertDeviceDirContainsDistro(dataCurrentDir, distroBytes);
    }

    // {dataDir}/staged contains an install, but there is nothing to replace.
    public void testStaging_install_noCurrent() throws Exception {
        // Set up the /system directory structure on host.
        createSystemTzDataFileOnHost(VALID_RULES_VERSION);

        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);

        // Set up the /data directory structure on host.

        // Create a staged install.
        PathPair dataStagedDir = mDataDir.createSubPath(STAGED_DIR_NAME);
        byte[] stagedDistroBytes = createValidDistroBuilder().buildBytes();
        unpackOnHost(dataStagedDir, stagedDistroBytes);

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code. Failures due to staging issues are
        // generally ignored providing the device is left in a reasonable state.
        assertEquals(0, runTzDataCheckOnDevice());

        // Assert the device was left in a valid "installed" state.
        assertDevicePathDoesNotExist(dataStagedDir);
        assertDeviceDirContainsDistro(dataCurrentDir, stagedDistroBytes);
    }

    // {dataDir}/staged contains an install, and there is something to replace.
    public void testStaging_install_withCurrent() throws Exception {
        // Set up the /system directory structure on host.
        createSystemTzDataFileOnHost(VALID_RULES_VERSION);

        DistroVersion currentDistroVersion = new DistroVersion(
                DistroVersion.CURRENT_FORMAT_MAJOR_VERSION, 1, VALID_RULES_VERSION, 1);
        DistroVersion stagedDistroVersion = new DistroVersion(
                DistroVersion.CURRENT_FORMAT_MAJOR_VERSION, 1, VALID_RULES_VERSION, 2);

        // Set up the /data directory structure on host.

        // Create a staged uninstall.
        PathPair dataStagedDir = mDataDir.createSubPath(STAGED_DIR_NAME);
        byte[] stagedDistroBytes = createValidDistroBuilder()
                .setDistroVersion(stagedDistroVersion)
                .buildBytes();
        unpackOnHost(dataStagedDir, stagedDistroBytes);

        // Create a current installed distro.
        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);
        byte[] currentDistroBytes = createValidDistroBuilder()
                .setDistroVersion(currentDistroVersion)
                .buildBytes();
        unpackOnHost(dataCurrentDir, currentDistroBytes);

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code. Failures due to staging issues are
        // generally ignored providing the device is left in a reasonable state.
        assertEquals(0, runTzDataCheckOnDevice());

        // Assert the device was left in a valid "installed" state.
        // The stagedDistro should now be the one in the current dir.
        assertDevicePathDoesNotExist(dataStagedDir);
        assertDeviceDirContainsDistro(dataCurrentDir, stagedDistroBytes);
    }

    // {dataDir}/staged contains an invalid install, and there is something to replace.
    // Most of the invalid cases are tested without staging; this is just to prove that staging
    // an invalid distro is handled the same.
    public void testStaging_install_withCurrent_invalidStaged() throws Exception {
        // Set up the /system directory structure on host.
        createSystemTzDataFileOnHost(VALID_RULES_VERSION);

        // Set up the /data directory structure on host.

        // Create a staged uninstall which contains invalid.
        PathPair dataStagedDir = mDataDir.createSubPath(STAGED_DIR_NAME);
        byte[] stagedDistroBytes = createValidDistroBuilder()
                .clearVersionForTests()
                .buildUnvalidatedBytes();
        unpackOnHost(dataStagedDir, stagedDistroBytes);

        // Create a current installed distro.
        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);
        byte[] currentDistroBytes = createValidDistroBuilder().buildBytes();
        unpackOnHost(dataCurrentDir, currentDistroBytes);

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code. The staged directory will have become the
        // current one, but then it will be discovered to be invalid and will be removed.
        assertEquals(3, runTzDataCheckOnDevice());

        // Assert the device was left in a valid "uninstalled" state.
        assertDevicePathDoesNotExist(dataStagedDir);
        assertDevicePathDoesNotExist(dataCurrentDir);
    }

    // No {dataDir}/current exists.
    public void testNoCurrentDataDir() throws Exception {
        // Set up the /system directory structure on host.
        createSystemTzDataFileOnHost(VALID_RULES_VERSION);

        // Deliberately not creating anything on host in the data dir here, leaving the empty
        // structure.

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code.
        assertEquals(0, runTzDataCheckOnDevice());
    }

    // {dataDir}/current exists but it is a file.
    public void testCurrentDataDirIsFile() throws Exception {
        // Set up the /system directory structure on host.
        createSystemTzDataFileOnHost(VALID_RULES_VERSION);

        // Set up the /data directory structure on host.
        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);
        // Create a file with the same name as the directory that tzdatacheck expects.
        Files.write(dataCurrentDir.hostPath, new byte[] { 'a' });

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code.
        assertEquals(2, runTzDataCheckOnDevice());

        // Assert the file was just ignored. This is a fairly arbitrary choice to leave it rather
        // than delete.
        assertDevicePathExists(dataCurrentDir);
        assertDevicePathIsFile(dataCurrentDir);
    }

    // {dataDir}/current exists but is missing the distro version file.
    public void testMissingDataDirDistroVersionFile() throws Exception {
        // Set up the /system directory structure on host.
        createSystemTzDataFileOnHost(VALID_RULES_VERSION);

        // Set up the /data directory structure on host.
        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);
        byte[] distroWithoutAVersionFileBytes = createValidDistroBuilder()
                .clearVersionForTests()
                .buildUnvalidatedBytes();
        unpackOnHost(dataCurrentDir, distroWithoutAVersionFileBytes);

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code.
        assertEquals(3, runTzDataCheckOnDevice());

        // Assert the current data directory was deleted.
        assertDevicePathDoesNotExist(dataCurrentDir);
    }

    // {dataDir}/current exists but the distro version file is short.
    public void testShortDataDirDistroVersionFile() throws Exception {
        // Set up the /system directory structure on host.
        createSystemTzDataFileOnHost(VALID_RULES_VERSION);

        // Set up the /data directory structure on host.
        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);
        unpackOnHost(dataCurrentDir, createValidDistroBuilder().buildBytes());
        // Replace the distro version file with a short file.
        Path distroVersionFile =
                dataCurrentDir.hostPath.resolve(TimeZoneDistro.DISTRO_VERSION_FILE_NAME);
        assertHostFileExists(distroVersionFile);
        Files.write(distroVersionFile, new byte[3]);

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code.
        assertEquals(3, runTzDataCheckOnDevice());

        // Assert the current data directory was deleted.
        assertDevicePathDoesNotExist(dataCurrentDir);
    }

    // {dataDir}/current exists and the distro version file is long enough, but contains junk.
    public void testCorruptDistroVersionFile() throws Exception {
        // Set up the /system directory structure on host.
        createSystemTzDataFileOnHost(VALID_RULES_VERSION);

        // Set up the /data directory structure on host.
        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);
        unpackOnHost(dataCurrentDir, createValidDistroBuilder().buildBytes());

        // Replace the distro version file with junk.
        Path distroVersionFile =
                dataCurrentDir.hostPath.resolve(TimeZoneDistro.DISTRO_VERSION_FILE_NAME);
        assertHostFileExists(distroVersionFile);

        int fileLength = (int) Files.size(distroVersionFile);
        byte[] junkArray = new byte[fileLength]; // all zeros
        Files.write(distroVersionFile, junkArray);

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code.
        assertEquals(4, runTzDataCheckOnDevice());

        // Assert the current data directory was deleted.
        assertDevicePathDoesNotExist(dataCurrentDir);
    }

    // {dataDir}/current exists but the distro version is incorrect.
    public void testInvalidMajorDistroVersion_older() throws Exception {
        // Set up the /system directory structure on host.
        createSystemTzDataFileOnHost(VALID_RULES_VERSION);

        // Set up the /data directory structure on host.
        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);
        DistroVersion oldMajorDistroVersion = new DistroVersion(
                DistroVersion.CURRENT_FORMAT_MAJOR_VERSION - 1, 1, VALID_RULES_VERSION, 1);
        byte[] distroBytes = createValidDistroBuilder()
                .setDistroVersion(oldMajorDistroVersion)
                .buildBytes();
        unpackOnHost(dataCurrentDir, distroBytes);

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code.
        assertEquals(5, runTzDataCheckOnDevice());

        // Assert the current data directory was deleted.
        assertDevicePathDoesNotExist(dataCurrentDir);
    }

    // {dataDir}/current exists but the distro version is incorrect.
    public void testInvalidMajorDistroVersion_newer() throws Exception {
        // Set up the /system directory structure on host.
        createSystemTzDataFileOnHost(VALID_RULES_VERSION);

        // Set up the /data directory structure on host.
        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);
        DistroVersion newMajorDistroVersion = new DistroVersion(
                DistroVersion.CURRENT_FORMAT_MAJOR_VERSION + 1,
                DistroVersion.CURRENT_FORMAT_MINOR_VERSION,
                VALID_RULES_VERSION, VALID_REVISION);
        byte[] distroBytes = createValidDistroBuilder()
                .setDistroVersion(newMajorDistroVersion)
                .buildBytes();
        unpackOnHost(dataCurrentDir, distroBytes);

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code.
        assertEquals(5, runTzDataCheckOnDevice());

        // Assert the current data directory was deleted.
        assertDevicePathDoesNotExist(dataCurrentDir);
    }

    // {dataDir}/current exists but the distro version is incorrect.
    public void testInvalidMinorDistroVersion_older() throws Exception {
        // Set up the /system directory structure on host.
        createSystemTzDataFileOnHost(VALID_RULES_VERSION);

        // Set up the /data directory structure on host.
        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);
        DistroVersion oldMinorDistroVersion = new DistroVersion(
                DistroVersion.CURRENT_FORMAT_MAJOR_VERSION,
                DistroVersion.CURRENT_FORMAT_MINOR_VERSION - 1,
                VALID_RULES_VERSION, 1);
        byte[] distroBytes = createValidDistroBuilder()
                .setDistroVersion(oldMinorDistroVersion)
                .buildBytes();
        unpackOnHost(dataCurrentDir, distroBytes);

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code.
        assertEquals(5, runTzDataCheckOnDevice());

        // Assert the current data directory was deleted.
        assertDevicePathDoesNotExist(dataCurrentDir);
    }

    // {dataDir}/current exists but the distro version is newer (which is accepted because it should
    // be backwards compatible).
    public void testValidMinorDistroVersion_newer() throws Exception {
        // Set up the /system directory structure on host.
        createSystemTzDataFileOnHost(VALID_RULES_VERSION);

        // Set up the /data directory structure on host.
        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);
        DistroVersion newMajorDistroVersion = new DistroVersion(
                DistroVersion.CURRENT_FORMAT_MAJOR_VERSION,
                DistroVersion.CURRENT_FORMAT_MINOR_VERSION + 1,
                VALID_RULES_VERSION, VALID_REVISION);
        byte[] distroBytes = createValidDistroBuilder()
                .setDistroVersion(newMajorDistroVersion)
                .buildBytes();
        unpackOnHost(dataCurrentDir, distroBytes);

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code.
        assertEquals(0, runTzDataCheckOnDevice());

        // Assert the current data directory was not touched.
        assertDeviceDirContainsDistro(dataCurrentDir, distroBytes);
    }

    // {dataDir}/current is valid but the tzdata file in /system is missing.
    public void testSystemTzDataFileMissing() throws Exception {
        // Deliberately not writing anything in /system here.

        // Set up the /data directory structure on host.
        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);
        byte[] validDistroBytes = createValidDistroBuilder().buildBytes();
        unpackOnHost(dataCurrentDir, validDistroBytes);

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code.
        assertEquals(6, runTzDataCheckOnDevice());

        // Assert the current data directory was not touched.
        assertDeviceDirContainsDistro(dataCurrentDir, validDistroBytes);
    }

    // {dataDir}/current is valid but the tzdata file in /system has an invalid header.
    public void testSystemTzDataFileCorrupt() throws Exception {
        // Set up the /system directory structure on host.
        byte[] invalidTzDataBytes = new byte[20];
        Files.write(mSystemDir.hostPath.resolve(SYSTEM_TZDATA_FILE_NAME), invalidTzDataBytes);

        // Set up the /data directory structure on host.
        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);
        byte[] validDistroBytes = createValidDistroBuilder().buildBytes();
        unpackOnHost(dataCurrentDir, validDistroBytes);

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code.
        assertEquals(7, runTzDataCheckOnDevice());

        // Assert the current data directory was not touched.
        assertDeviceDirContainsDistro(dataCurrentDir, validDistroBytes);
    }

    // {dataDir}/current is valid and the tzdata file in /system is older.
    public void testSystemTzRulesOlder() throws Exception {
        // Set up the /system directory structure on host.
        createSystemTzDataFileOnHost(RULES_VERSION_ONE);

        // Set up the /data directory structure on host.
        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);
        // Newer than RULES_VERSION_ONE in /system
        final String distroRulesVersion = RULES_VERSION_TWO;
        DistroVersion distroVersion = new DistroVersion(
                DistroVersion.CURRENT_FORMAT_MAJOR_VERSION,
                DistroVersion.CURRENT_FORMAT_MINOR_VERSION, distroRulesVersion, VALID_REVISION);
        byte[] distroBytes = createValidDistroBuilder()
                .setDistroVersion(distroVersion)
                .setTzDataFile(createValidTzDataBytes(distroRulesVersion))
                .buildBytes();
        unpackOnHost(dataCurrentDir, distroBytes);

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code.
        assertEquals(0, runTzDataCheckOnDevice());

        // Assert the current data directory was not touched.
        assertDeviceDirContainsDistro(dataCurrentDir, distroBytes);
    }

    // {dataDir}/current is valid and the tzdata file in /system is the same (and should be kept).
    public void testSystemTzDataSame() throws Exception {
        // Set up the /system directory structure on host.
        final String systemRulesVersion = VALID_RULES_VERSION;
        createSystemTzDataFileOnHost(systemRulesVersion);

        // Set up the /data directory structure on host.
        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);
        DistroVersion distroVersion = new DistroVersion(
                DistroVersion.CURRENT_FORMAT_MAJOR_VERSION,
                DistroVersion.CURRENT_FORMAT_MINOR_VERSION, systemRulesVersion, VALID_REVISION);
        byte[] distroBytes = createValidDistroBuilder()
                .setDistroVersion(distroVersion)
                .setTzDataFile(createValidTzDataBytes(systemRulesVersion))
                .buildBytes();
        unpackOnHost(dataCurrentDir, distroBytes);

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code.
        assertEquals(0, runTzDataCheckOnDevice());

        // Assert the current data directory was not touched.
        assertDeviceDirContainsDistro(dataCurrentDir, distroBytes);
    }

    // {dataDir}/current is valid and the tzdata file in /system is the newer.
    public void testSystemTzDataNewer() throws Exception {
        // Set up the /system directory structure on host.
        String systemRulesVersion = RULES_VERSION_TWO;
        createSystemTzDataFileOnHost(systemRulesVersion);

        // Set up the /data directory structure on host.
        PathPair dataCurrentDir = mDataDir.createSubPath(CURRENT_DIR_NAME);
        String distroRulesVersion = RULES_VERSION_ONE; // Older than the system version.
        DistroVersion distroVersion = new DistroVersion(
                DistroVersion.CURRENT_FORMAT_MAJOR_VERSION,
                DistroVersion.CURRENT_FORMAT_MINOR_VERSION,
                distroRulesVersion,
                VALID_REVISION);
        byte[] distroBytes = createValidDistroBuilder()
                .setDistroVersion(distroVersion)
                .setTzDataFile(createValidTzDataBytes(distroRulesVersion))
                .buildBytes();
        unpackOnHost(dataCurrentDir, distroBytes);

        // Push the host test directory and contents to the device.
        pushHostTestDirToDevice();

        // Execute tzdatacheck and check the status code.
        assertEquals(0, runTzDataCheckOnDevice());

        // It is important the dataCurrentDir is deleted in this case - this test case is the main
        // reason tzdatacheck exists.
        assertDevicePathDoesNotExist(dataCurrentDir);
    }

    private void createSystemTzDataFileOnHost(String systemRulesVersion) throws IOException {
        byte[] systemTzData = createValidTzDataBytes(systemRulesVersion);
        Files.write(mSystemDir.hostPath.resolve(SYSTEM_TZDATA_FILE_NAME), systemTzData);
    }

    private static void createStagedUninstallOnHost(PathPair stagedDir) throws Exception {
        createHostDirectory(stagedDir);

        PathPair uninstallTombstoneFile = stagedDir.createSubPath(UNINSTALL_TOMBSTONE_FILE_NAME);
        // Create an empty file.
        new FileOutputStream(uninstallTombstoneFile.hostFile()).close();
    }

    private static void unpackOnHost(PathPair path, byte[] distroBytes) throws Exception {
        createHostDirectory(path);
        new TimeZoneDistro(distroBytes).extractTo(path.hostFile());
    }

    private static TimeZoneDistroBuilder createValidDistroBuilder() throws Exception {
        String distroRulesVersion = VALID_RULES_VERSION;
        DistroVersion validDistroVersion =
                new DistroVersion(
                        DistroVersion.CURRENT_FORMAT_MAJOR_VERSION,
                        DistroVersion.CURRENT_FORMAT_MINOR_VERSION,
                        distroRulesVersion, VALID_REVISION);
        return new TimeZoneDistroBuilder()
                .setDistroVersion(validDistroVersion)
                .setTzDataFile(createValidTzDataBytes(distroRulesVersion))
                .setIcuDataFile(new byte[10]);
    }

    private static byte[] createValidTzDataBytes(String rulesVersion) {
        return new ZoneInfoTestHelper.TzDataBuilder()
                .initializeToValid()
                .setHeaderMagic("tzdata" + rulesVersion)
                .build();
    }

    private int runTzDataCheckOnDevice() throws Exception {
        return runTzDataCheckWithArgs(new String[] { mSystemDir.devicePath, mDataDir.devicePath });
    }

    private int runTzDataCheckWithArgs(String[] args) throws Exception {
        String command = createTzDataCheckCommand(mDeviceAndroidRootDir, args);
        return executeCommandOnDeviceWithResultCode(command).statusCode;
    }

    private static String createTzDataCheckCommand(String rootDir, String[] args) {
        StringJoiner joiner = new StringJoiner(" ");
        String tzDataCheckCommand = rootDir + "/bin/tzdatacheck";
        joiner.add(tzDataCheckCommand);
        for (String arg : args) {
            joiner.add(arg);
        }
        return joiner.toString();
    }

    private static void assertHostFileExists(Path path) {
        assertTrue(Files.exists(path));
    }

    private String executeCommandOnDeviceRaw(String command) throws DeviceNotAvailableException {
        return getDevice().executeShellCommand(command);
    }

    private void createDeviceDirectory(PathPair dir) throws DeviceNotAvailableException {
        executeCommandOnDeviceRaw("mkdir -p " + dir.devicePath);
    }

    private static void createHostDirectory(PathPair dir) throws Exception {
        Files.createDirectory(dir.hostPath);
    }

    private static class ShellResult {
        final String output;
        final int statusCode;

        private ShellResult(String output, int statusCode) {
            this.output = output;
            this.statusCode = statusCode;
        }
    }

    private ShellResult executeCommandOnDeviceWithResultCode(String command) throws Exception {
        // A file to hold the script we're going to create.
        PathPair scriptFile = mTestRootDir.createSubPath("script.sh");
        // A file to hold the output of the script.
        PathPair scriptOut = mTestRootDir.createSubPath("script.out");

        // The content of the script. Runs the command, capturing stdout and stderr to scriptOut
        // and printing the result code.
        String hostScriptContent = command + " > " + scriptOut.devicePath + " 2>&1 ; echo -n $?";

        // Parse and return the result.
        try {
            Files.write(scriptFile.hostPath, hostScriptContent.getBytes(StandardCharsets.US_ASCII));

            // Push the script to the device.
            pushFile(scriptFile);

            // Execute the script using "sh".
            String execCommandUnderShell =
                    mDeviceAndroidRootDir + "/bin/sh " + scriptFile.devicePath;
            String resultCodeString = executeCommandOnDeviceRaw(execCommandUnderShell);

            // Pull back scriptOut to the host and read the content.
            pullFile(scriptOut);
            byte[] outputBytes = Files.readAllBytes(scriptOut.hostPath);
            String output = new String(outputBytes, StandardCharsets.US_ASCII);

            int resultCode;
            try {
                resultCode = Integer.parseInt(resultCodeString);
            } catch (NumberFormatException e) {
                fail("Command: " + command
                        + " returned a non-integer: \"" + resultCodeString + "\""
                        + ", output=\"" + output + "\"");
                return null;
            }
            return new ShellResult(output, resultCode);
        } finally {
            deleteDeviceFile(scriptFile, false /* failOnError */);
            deleteDeviceFile(scriptOut, false /* failOnError */);
            deleteHostFile(scriptFile, false /* failOnError */);
            deleteHostFile(scriptOut, false /* failOnError */);
        }
    }

    private void pushHostTestDirToDevice() throws Exception {
        assertTrue(getDevice().pushDir(mTestRootDir.hostFile(), mTestRootDir.devicePath));
    }

    private void pullFile(PathPair file) throws DeviceNotAvailableException {
        assertTrue("Could not pull file " + file.devicePath + " to " + file.hostFile(),
                getDevice().pullFile(file.devicePath, file.hostFile()));
    }

    private void pushFile(PathPair file) throws DeviceNotAvailableException {
        assertTrue("Could not push file " + file.hostFile() + " to " + file.devicePath,
                getDevice().pushFile(file.hostFile(), file.devicePath));
    }

    private void deleteHostFile(PathPair file, boolean failOnError) {
        try {
            Files.deleteIfExists(file.hostPath);
        } catch (IOException e) {
            if (failOnError) {
                fail(e);
            }
        }
    }

    private void deleteDeviceDirectory(PathPair dir, boolean failOnError)
            throws DeviceNotAvailableException {
        String deviceDir = dir.devicePath;
        try {
            executeCommandOnDeviceRaw("rm -r " + deviceDir);
        } catch (Exception e) {
            if (failOnError) {
                throw deviceFail(e);
            }
        }
    }

    private void deleteDeviceFile(PathPair file, boolean failOnError)
            throws DeviceNotAvailableException {
        try {
            assertDevicePathIsFile(file);
            executeCommandOnDeviceRaw("rm " + file.devicePath);
        } catch (Exception e) {
            if (failOnError) {
                throw deviceFail(e);
            }
        }
    }

    private static void deleteHostDirectory(PathPair dir, final boolean failOnError) {
        Path hostPath = dir.hostPath;
        if (Files.exists(hostPath)) {
            Consumer<Path> pathConsumer = file -> {
                try {
                    Files.delete(file);
                } catch (Exception e) {
                    if (failOnError) {
                        fail(e);
                    }
                }
            };

            try {
                Files.walk(hostPath).sorted(Comparator.reverseOrder()).forEach(pathConsumer);
            } catch (IOException e) {
                fail(e);
            }
        }
    }

    private void assertDevicePathExists(PathPair path) throws DeviceNotAvailableException {
        assertTrue(getDevice().doesFileExist(path.devicePath));
    }

    private void assertDeviceDirContainsDistro(PathPair distroPath, byte[] expectedDistroBytes)
            throws Exception {
        // Pull back just the version file and compare it.
        File localFile = mTestRootDir.createSubPath("temp.file").hostFile();
        try {
            String remoteVersionFile = distroPath.devicePath + "/"
                    + TimeZoneDistro.DISTRO_VERSION_FILE_NAME;
            assertTrue("Could not pull file " + remoteVersionFile + " to " + localFile,
                    getDevice().pullFile(remoteVersionFile, localFile));

            byte[] bytes = Files.readAllBytes(localFile.toPath());
            assertArrayEquals(bytes,
                    new TimeZoneDistro(expectedDistroBytes).getDistroVersion().toBytes());
        } finally {
            localFile.delete();
        }
    }

    private void assertDevicePathDoesNotExist(PathPair path) throws DeviceNotAvailableException {
        assertFalse(getDevice().doesFileExist(path.devicePath));
    }

    private void assertDevicePathIsFile(PathPair path) throws DeviceNotAvailableException {
        // This check cannot rely on getDevice().getFile(devicePath).isDirectory() here because that
        // requires that the user has rights to list all files beneath each and every directory in
        // the path. That is not the case for the shell user and the /data and /data/local
        // directories. http://b/35753041.
        String output = executeCommandOnDeviceRaw("stat -c %F " + path.devicePath);
        assertTrue(path.devicePath + " not a file. Received: " + output,
                output.startsWith("regular") && output.endsWith("file\n"));
    }

    private static DeviceNotAvailableException deviceFail(Exception e)
            throws DeviceNotAvailableException {
        if (e instanceof DeviceNotAvailableException) {
            throw (DeviceNotAvailableException) e;
        }
        fail(e);
        return null;
    }

    private static void fail(Exception e) {
        e.printStackTrace();
        fail(e.getMessage());
    }

    /** A path that has equivalents on both host and device. */
    private static class PathPair {
        private final Path hostPath;
        private final String devicePath;

        PathPair(Path hostPath, String devicePath) {
            this.hostPath = hostPath;
            this.devicePath = devicePath;
        }

        File hostFile() {
            return hostPath.toFile();
        }

        PathPair createSubPath(String s) {
            return new PathPair(hostPath.resolve(s), devicePath + "/" + s);
        }
    }
}
