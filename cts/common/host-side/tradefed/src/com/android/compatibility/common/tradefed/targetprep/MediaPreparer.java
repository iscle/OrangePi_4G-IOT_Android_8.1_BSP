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
import com.android.compatibility.common.tradefed.targetprep.PreconditionPreparer;
import com.android.compatibility.common.tradefed.util.DynamicConfigFileReader;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.AndroidJUnitTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Ensures that the appropriate media files exist on the device
 */
@OptionClass(alias="media-preparer")
public class MediaPreparer extends PreconditionPreparer {

    @Option(name = "local-media-path",
            description = "Absolute path of the media files directory, containing" +
            "'bbb_short' and 'bbb_full' directories")
    protected String mLocalMediaPath = null;

    @Option(name = "skip-media-download",
            description = "Whether to skip the media files precondition")
    protected boolean mSkipMediaDownload = false;

    @Option(name = "media-download-only",
            description = "Only download media files; do not run instrumentation or copy files")
    protected boolean mMediaDownloadOnly = false;

    /*
     * The pathnames of the device's directories that hold media files for the tests.
     * These depend on the device's mount point, which is retrieved in the MediaPreparer's run
     * method.
     *
     * These fields are exposed for unit testing
     */
    protected String mBaseDeviceShortDir;
    protected String mBaseDeviceFullDir;

    /*
     * Variables set by the MediaPreparerListener during retrieval of maximum media file
     * resolution. After the MediaPreparerApp has been instrumented on the device:
     *
     * testMetrics contains the string representation of the resolution
     * testFailures contains a stacktrace if retrieval of the resolution was unsuccessful
     */
    protected Resolution mMaxRes = null;
    protected String mFailureStackTrace = null;

    /*
     * The default name of local directory into which media files will be downloaded, if option
     * "local-media-path" is not provided. This directory will live inside the temp directory.
     */
    protected static final String MEDIA_FOLDER_NAME = "android-cts-media";

    /* The key used to retrieve the media files URL from the dynamic configuration */
    private static final String MEDIA_FILES_URL_KEY = "media_files_url";

    /* For a target preparer, the "module" of the configuration is the test suite */
    private static final String DYNAMIC_CONFIG_MODULE = "cts";

    /*
     * Info used to install and uninstall the MediaPreparerApp
     */
    private static final String APP_APK = "CtsMediaPreparerApp.apk";
    private static final String APP_PKG_NAME = "android.mediastress.cts.preconditions.app";

    /* Key to retrieve resolution string in metrics upon MediaPreparerListener.testEnded() */
    private static final String RESOLUTION_STRING_KEY = "resolution";

    /*
     * In the case of MediaPreparer error, the default maximum resolution to push to the device.
     * Pushing higher resolutions may lead to insufficient storage for installing test APKs.
     * TODO(aaronholden): When the new detection of max resolution is proven stable, throw
     * a TargetSetupError when detection results in error
     */
    protected static final Resolution DEFAULT_MAX_RESOLUTION = new Resolution(480, 360);

    protected static final Resolution[] RESOLUTIONS = {
            new Resolution(176, 144),
            new Resolution(480, 360),
            new Resolution(720, 480),
            new Resolution(1280, 720),
            new Resolution(1920, 1080)
    };

    /** Helper class for generating and retrieving width-height pairs */
    protected static final class Resolution {
        // regex that matches a resolution string
        private static final String PATTERN = "(\\d+)x(\\d+)";
        // group indices for accessing resolution width and height from a PATTERN-based Matcher
        private static final int WIDTH_INDEX = 1;
        private static final int HEIGHT_INDEX = 2;

        private final int width;
        private final int height;

        private Resolution(int width, int height) {
            this.width = width;
            this.height = height;
        }

        private Resolution(String resolution) {
            Pattern pattern = Pattern.compile(PATTERN);
            Matcher matcher = pattern.matcher(resolution);
            matcher.find();
            this.width = Integer.parseInt(matcher.group(WIDTH_INDEX));
            this.height = Integer.parseInt(matcher.group(HEIGHT_INDEX));
        }

        @Override
        public String toString() {
            return String.format("%dx%d", width, height);
        }
    }

    public static File getDefaultMediaDir() {
        return new File(System.getProperty("java.io.tmpdir"), MEDIA_FOLDER_NAME);
    }

    /*
     * Returns true if all necessary media files exist on the device, and false otherwise.
     *
     * This method is exposed for unit testing.
     */
    protected boolean mediaFilesExistOnDevice(ITestDevice device)
            throws DeviceNotAvailableException {
        for (Resolution resolution : RESOLUTIONS) {
            if (resolution.width > mMaxRes.width) {
                break; // no need to check for resolutions greater than this
            }
            String deviceShortFilePath = mBaseDeviceShortDir + resolution.toString();
            String deviceFullFilePath = mBaseDeviceFullDir + resolution.toString();
            if (!device.doesFileExist(deviceShortFilePath)
                    || !device.doesFileExist(deviceFullFilePath)) {
                return false;
            }
        }
        return true;
    }

    /*
     * After downloading and unzipping the media files, mLocalMediaPath must be the path to the
     * directory containing 'bbb_short' and 'bbb_full' directories, as it is defined in its
     * description as an option.
     * After extraction, this directory exists one level below the the directory 'mediaFolder'.
     * If the 'mediaFolder' contains anything other than exactly one subdirectory, a
     * TargetSetupError is thrown. Otherwise, the mLocalMediaPath variable is set to the path of
     * this subdirectory.
     */
    private void updateLocalMediaPath(ITestDevice device, File mediaFolder)
            throws TargetSetupError {
        String[] subDirs = mediaFolder.list();
        if (subDirs.length != 1) {
            throw new TargetSetupError(String.format(
                    "Unexpected contents in directory %s", mediaFolder.getAbsolutePath()),
                    device.getDeviceDescriptor());
        }
        mLocalMediaPath = new File(mediaFolder, subDirs[0]).getAbsolutePath();
    }

    /*
     * Copies the media files to the host from a predefined URL
     * Updates mLocalMediaPath to be the pathname of the directory containing bbb_short and
     * bbb_full media directories.
     */
    private void downloadMediaToHost(ITestDevice device, IBuildInfo buildInfo, File mediaFolder)
            throws TargetSetupError {
        URL url;
        try {
            // Get download URL from dynamic configuration service
            String mediaUrlString = DynamicConfigFileReader.getValueFromConfig(
                    buildInfo, DYNAMIC_CONFIG_MODULE, MEDIA_FILES_URL_KEY);
            url = new URL(mediaUrlString);
        } catch (IOException | XmlPullParserException e) {
            throw new TargetSetupError("Trouble finding media file download location with " +
                    "dynamic configuration", e, device.getDeviceDescriptor());
        }
        File mediaFolderZip = new File(mediaFolder.getAbsolutePath() + ".zip");
        try {
            logInfo("Downloading media files from %s", url.toString());
            URLConnection conn = url.openConnection();
            InputStream in = conn.getInputStream();
            mediaFolderZip.createNewFile();
            FileUtil.writeToFile(in, mediaFolderZip);
            logInfo("Unzipping media files");
            ZipUtil.extractZip(new ZipFile(mediaFolderZip), mediaFolder);
        } catch (IOException e) {
            FileUtil.recursiveDelete(mediaFolder);
            throw new TargetSetupError("Failed to download and open media files on host, the"
                    + " device requires these media files for CTS media tests", e,
                    device.getDeviceDescriptor());
        } finally {
            FileUtil.deleteFile(mediaFolderZip);
        }
    }

    /*
     * Pushes directories containing media files to the device for all directories that:
     * - are not already present on the device
     * - contain video files of a resolution less than or equal to the device's
     *       max video playback resolution
     *
     * This method is exposed for unit testing.
     */
    protected void copyMediaFiles(ITestDevice device)
            throws DeviceNotAvailableException {
        for (Resolution resolution : RESOLUTIONS) {
            if (resolution.width > mMaxRes.width) {
                logInfo("Media file copying complete");
                return;
            }
            String deviceShortFilePath = mBaseDeviceShortDir + resolution.toString();
            String deviceFullFilePath = mBaseDeviceFullDir + resolution.toString();
            if (!device.doesFileExist(deviceShortFilePath) ||
                    !device.doesFileExist(deviceFullFilePath)) {
                logInfo("Copying files of resolution %s to device", resolution.toString());
                String localShortDirName = "bbb_short/" + resolution.toString();
                String localFullDirName = "bbb_full/" + resolution.toString();
                File localShortDir = new File(mLocalMediaPath, localShortDirName);
                File localFullDir = new File(mLocalMediaPath, localFullDirName);
                // push short directory of given resolution, if not present on device
                if(!device.doesFileExist(deviceShortFilePath)) {
                    device.pushDir(localShortDir, deviceShortFilePath);
                }
                // push full directory of given resolution, if not present on device
                if(!device.doesFileExist(deviceFullFilePath)) {
                    device.pushDir(localFullDir, deviceFullFilePath);
                }
            }
        }
    }

    // Initialize directory strings where media files live on device
    protected void setMountPoint(ITestDevice device) {
        String mountPoint = device.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        mBaseDeviceShortDir = String.format("%s/test/bbb_short/", mountPoint);
        mBaseDeviceFullDir = String.format("%s/test/bbb_full/", mountPoint);
    }

    @Override
    public void run(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {

        if (mSkipMediaDownload) {
            logInfo("Skipping media preparation");
            return; // skip this precondition
        }
        if (!mMediaDownloadOnly) {
            setMountPoint(device);
            setMaxRes(device, buildInfo);
            if (mediaFilesExistOnDevice(device)) {
                // if files already on device, do nothing
                logInfo("Media files found on the device");
                return;
            }
        }
        if (mLocalMediaPath == null) {
            // Option 'local-media-path' has not been defined
            // Get directory to store media files on this host
            File mediaFolder = getDefaultMediaDir();
            if(!mediaFolder.exists() || mediaFolder.list().length == 0){
                // If directory already exists and contains files, it has been created by previous
                // runs of MediaPreparer. Assume media files exist inside.
                // Else, create directory if needed and download/extract media files inside.
                mediaFolder.mkdirs();
                downloadMediaToHost(device, buildInfo, mediaFolder);
            }
            // set mLocalMediaPath to where the CTS media files have been extracted
            updateLocalMediaPath(device, mediaFolder);
        }
        logInfo("Media files located on host at: %s", mLocalMediaPath);
        if (!mMediaDownloadOnly) {
            copyMediaFiles(device);
        }
    }

    // Initialize maximum resolution of media files to copy
    private void setMaxRes(ITestDevice device, IBuildInfo buildInfo)
            throws DeviceNotAvailableException {
        ITestInvocationListener listener = new MediaPreparerListener();
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(buildInfo);
        File apkFile = null;
        try {
            apkFile = buildHelper.getTestFile(APP_APK);
            if (!apkFile.exists()) {
                // handle both missing tests dir and missing APK in catch block
                throw new FileNotFoundException();
            }
        } catch (FileNotFoundException e) {
            mMaxRes = DEFAULT_MAX_RESOLUTION;
            logWarning("Cound not find %s to determine maximum resolution, copying up to %s",
                    APP_APK, DEFAULT_MAX_RESOLUTION.toString());
            return;
        }
        if (device.getAppPackageInfo(APP_PKG_NAME) != null) {
            device.uninstallPackage(APP_PKG_NAME);
        }
        logInfo("Instrumenting package %s:", APP_PKG_NAME);
        AndroidJUnitTest instrTest = new AndroidJUnitTest();
        instrTest.setDevice(device);
        instrTest.setInstallFile(apkFile);
        instrTest.setPackageName(APP_PKG_NAME);
        instrTest.run(listener);
        if (mFailureStackTrace != null) {
            mMaxRes = DEFAULT_MAX_RESOLUTION;
            logWarning("Retrieving maximum resolution failed with trace:\n%s", mFailureStackTrace);
            logWarning("Copying up to %s", DEFAULT_MAX_RESOLUTION.toString());
        } else if (mMaxRes == null) {
            mMaxRes = DEFAULT_MAX_RESOLUTION;
            logWarning("Failed to pull resolution capabilities from device, copying up to %s",
                    DEFAULT_MAX_RESOLUTION.toString());
        }
    }

    /* Special listener for setting MediaPreparer instance variable values */
    private class MediaPreparerListener implements ITestInvocationListener {

        @Override
        public void testEnded(TestIdentifier test, Map<String, String> metrics) {
            String resString = metrics.get(RESOLUTION_STRING_KEY);
            if (resString != null) {
                mMaxRes = new Resolution(resString);
            }
        }

        @Override
        public void testFailed(TestIdentifier test, String trace) {
            mFailureStackTrace = trace;
        }
    }
}
