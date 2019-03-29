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
package com.android.compatibility.common.tradefed.build;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple helper that stores and retrieves information from a {@link IBuildInfo}.
 */
public class CompatibilityBuildHelper {

    public static final String MODULE_IDS = "MODULE_IDS";
    public static final String ROOT_DIR = "ROOT_DIR";
    public static final String SUITE_BUILD = "SUITE_BUILD";
    public static final String SUITE_NAME = "SUITE_NAME";
    public static final String SUITE_FULL_NAME = "SUITE_FULL_NAME";
    public static final String SUITE_VERSION = "SUITE_VERSION";
    public static final String SUITE_PLAN = "SUITE_PLAN";
    public static final String START_TIME_MS = "START_TIME_MS";
    public static final String COMMAND_LINE_ARGS = "command_line_args";

    private static final String ROOT_DIR2 = "ROOT_DIR2";
    private static final String DYNAMIC_CONFIG_OVERRIDE_URL = "DYNAMIC_CONFIG_OVERRIDE_URL";
    private static final String BUSINESS_LOGIC_HOST_FILE = "BUSINESS_LOGIC_HOST_FILE";
    private static final String RETRY_COMMAND_LINE_ARGS = "retry_command_line_args";
    private static final String ALT_HOST_TESTCASE_DIR = "ANDROID_HOST_OUT_TESTCASES";
    private static final String ALT_TARGET_TESTCASE_DIR = "ANDROID_TARGET_OUT_TESTCASES";

    private static final String CONFIG_PATH_PREFIX = "DYNAMIC_CONFIG_FILE:";

    private final IBuildInfo mBuildInfo;

    /**
     * Creates a {@link CompatibilityBuildHelper} wrapping the given {@link IBuildInfo}.
     */
    public CompatibilityBuildHelper(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    public IBuildInfo getBuildInfo() {
        return mBuildInfo;
    }

    public void setRetryCommandLineArgs(String commandLineArgs) {
        mBuildInfo.addBuildAttribute(RETRY_COMMAND_LINE_ARGS, commandLineArgs);
    }

    public String getCommandLineArgs() {
        if (mBuildInfo.getBuildAttributes().containsKey(RETRY_COMMAND_LINE_ARGS)) {
            return mBuildInfo.getBuildAttributes().get(RETRY_COMMAND_LINE_ARGS);
        } else {
            // NOTE: this is a temporary workaround set in TestInvocation#invoke in tradefed.
            // This will be moved to a separate method in a new invocation metadata class.
            return mBuildInfo.getBuildAttributes().get(COMMAND_LINE_ARGS);
        }
    }

    public String getRecentCommandLineArgs() {
        return mBuildInfo.getBuildAttributes().get(COMMAND_LINE_ARGS);
    }

    public String getSuiteBuild() {
        return mBuildInfo.getBuildAttributes().get(SUITE_BUILD);
    }

    public String getSuiteName() {
        return mBuildInfo.getBuildAttributes().get(SUITE_NAME);
    }

    public String getSuiteFullName() {
        return mBuildInfo.getBuildAttributes().get(SUITE_FULL_NAME);
    }

    public String getSuiteVersion() {
        return mBuildInfo.getBuildAttributes().get(SUITE_VERSION);
    }

    public String getSuitePlan() {
        return mBuildInfo.getBuildAttributes().get(SUITE_PLAN);
    }

    public String getDynamicConfigUrl() {
        return mBuildInfo.getBuildAttributes().get(DYNAMIC_CONFIG_OVERRIDE_URL);
    }

    public long getStartTime() {
        return Long.parseLong(mBuildInfo.getBuildAttributes().get(START_TIME_MS));
    }

    public void addDynamicConfigFile(String moduleName, File configFile) {
        // If invocation fails and ResultReporter never moves this file into the result,
        // using setFile() ensures BuildInfo will delete upon cleanUp().
        mBuildInfo.setFile(configFile.getName(), configFile,
                CONFIG_PATH_PREFIX + moduleName /* version */);
    }

    public void setBusinessLogicHostFile(File hostFile) {
        mBuildInfo.addBuildAttribute(BUSINESS_LOGIC_HOST_FILE, hostFile.getAbsolutePath());
    }

    public void setModuleIds(String[] moduleIds) {
        mBuildInfo.addBuildAttribute(MODULE_IDS, String.join(",", moduleIds));
    }

    /**
     * Returns the map of the dynamic config files downloaded.
     */
    public Map<String, File> getDynamicConfigFiles() {
        Map<String, File> configMap = new HashMap<>();
        for (VersionedFile vFile : mBuildInfo.getFiles()) {
            if (vFile.getVersion().startsWith(CONFIG_PATH_PREFIX)) {
                configMap.put(
                        vFile.getVersion().substring(CONFIG_PATH_PREFIX.length()),
                        vFile.getFile());
            }
        }
        return configMap;
    }

    public File getBusinessLogicHostFile() {
        return new File(mBuildInfo.getBuildAttributes().get(BUSINESS_LOGIC_HOST_FILE));
    }

    /**
     * @return a {@link File} representing the directory holding the Compatibility installation
     * @throws FileNotFoundException if the directory does not exist
     */
    public File getRootDir() throws FileNotFoundException {
        File dir = null;
        if (mBuildInfo instanceof IFolderBuildInfo) {
            dir = ((IFolderBuildInfo) mBuildInfo).getRootDir();
        }
        if (dir == null || !dir.exists()) {
            dir = new File(mBuildInfo.getBuildAttributes().get(ROOT_DIR));
            if (!dir.exists()) {
                dir = new File(mBuildInfo.getBuildAttributes().get(ROOT_DIR2));
            }
        }
        if (!dir.exists()) {
            throw new FileNotFoundException(String.format(
                    "Compatibility root directory %s does not exist",
                    dir.getAbsolutePath()));
        }
        return dir;
    }

    /**
     * @return a {@link File} representing the "android-<suite>" folder of the Compatibility
     * installation
     * @throws FileNotFoundException if the directory does not exist
     */
    public File getDir() throws FileNotFoundException {
        File dir = new File(getRootDir(), String.format("android-%s", getSuiteName().toLowerCase()));
        if (!dir.exists()) {
            throw new FileNotFoundException(String.format(
                    "Compatibility install folder %s does not exist",
                    dir.getAbsolutePath()));
        }
        return dir;
    }

    /**
     * @return a {@link File} representing the results directory.
     * @throws FileNotFoundException if the directory structure is not valid.
     */
    public File getResultsDir() throws FileNotFoundException {
        return new File(getDir(), "results");
    }

    /**
     * @return a {@link File} representing the result directory of the current invocation.
     * @throws FileNotFoundException if the directory structure is not valid.
     */
    public File getResultDir() throws FileNotFoundException {
        return new File(getResultsDir(),
            getDirSuffix(Long.parseLong(mBuildInfo.getBuildAttributes().get(START_TIME_MS))));
    }

    /**
     * @return a {@link File} representing the directory to store result logs.
     * @throws FileNotFoundException if the directory structure is not valid.
     */
    public File getLogsDir() throws FileNotFoundException {
        return new File(getDir(), "logs");
    }

    /**
     * @return a {@link File} representing the directory to store derivedplan files.
     * @throws FileNotFoundException if the directory structure is not valid.
     */
    public File getSubPlansDir() throws FileNotFoundException {
        File subPlansDir = new File(getDir(), "subplans");
        if (!subPlansDir.exists()) {
            subPlansDir.mkdirs();
        }
        return subPlansDir;
    }

    /**
     * @return a {@link File} representing the directory to store screenshots taken while testing.
     * @throws FileNotFoundException if the directory structure is not valid.
     */
    public File getScreenshotsDir() throws FileNotFoundException {
        File screenshotsDir = new File(getResultDir(), "screenshots");
        if (!screenshotsDir.exists()) {
            screenshotsDir.mkdirs();
        }
        return screenshotsDir;
    }

    /**
     * @return a {@link File} representing the test modules directory.
     * @throws FileNotFoundException if the directory structure is not valid.
     */
    public File getTestsDir() throws FileNotFoundException {
        // We have 3 options that can be the test modules dir (and we're going
        // look for them in the following order):
        //   1. ../android-*ts/testcases/
        //   2. ALT_HOST_TESTCASE_DIR
        //   3. ALT_TARGET_TESTCASE_DIR (we'll skip this since if #2 fails, this
        //      will inevitably fail as well.)

        File testsDir = null;
        try {
            testsDir = new File(getDir(), "testcases");
        } catch (FileNotFoundException | NullPointerException e) {
            // Ok, no root dir for us to get, moving on to the next option.
            testsDir = null;
        }

        if (testsDir == null) {
            String altTestsDir = System.getenv().get(ALT_HOST_TESTCASE_DIR);
            if (altTestsDir != null) {
                testsDir = new File(altTestsDir);
            }
        }

        // This just means we have no signs of where to check for the test dir.
        if (testsDir == null) {
            throw new FileNotFoundException(
                String.format("No Compatibility tests folder set, did you run lunch?"));
        }

        if (!testsDir.exists()) {
            throw new FileNotFoundException(String.format(
                    "Compatibility tests folder %s does not exist",
                    testsDir.getAbsolutePath()));
        }

        return testsDir;
    }

    /**
     * @return a {@link File} representing the test file in the test modules directory.
     * @throws FileNotFoundException if the test file cannot be found
     */
    public File getTestFile(String filename) throws FileNotFoundException {
        // We have a lot of places to check for the test file.
        //   1. ../android-*ts/testcases/
        //   2. ALT_HOST_TESTCASE_DIR/
        //   3. ALT_TARGET_TESTCASE_DIR/

        // Our search depends on our run env, if we're in *ts, then we only want
        // to check #1.  If we're in gen tf, then we only want to check #2/3.
        // In *ts mode, getTestsDir will return #1, in gen tf mode, it'll return
        // #2.  In the event we're in *ts mode and the file isn't in #1, #2 or
        // #3, then the user probably needs to run lunch to setup the env.
        String altTargetTestDir = System.getenv().get(ALT_TARGET_TESTCASE_DIR);
        if (altTargetTestDir == null) {
            altTargetTestDir = "";
        }
        String[] testDirs = {getTestsDir().toString(), altTargetTestDir};

        File testFile;
        for (String testDir: testDirs) {
            testFile = new File(getTestsDir(), filename);
            if (testFile.exists()) {
                return testFile;
            }
            // The file may be in a subdirectory so do a more through search
            // if it did not exist.
            testFile = FileUtil.findFile(new File(testDir), filename);
            if (testFile != null) {
                return testFile;
            }
        }

        throw new FileNotFoundException(String.format(
                "Compatibility test file %s does not exist", filename));
    }

    /**
     * @return a {@link File} in the resultDir for logging invocation failures
     */
    public File getInvocationFailureFile() throws FileNotFoundException {
        return new File(getResultDir(), "invocation_failure.txt");
    }

    /**
     * @return a {@link File} in the resultDir for counting expected test runs
     */
    public File getTestRunsFile() throws FileNotFoundException {
        return new File(getResultDir(), "test_runs.txt");
    }

    /**
     * @return a {@link String} to use for directory suffixes created from the given time.
     */
    public static String getDirSuffix(long millis) {
        return new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss").format(new Date(millis));
    }
}
