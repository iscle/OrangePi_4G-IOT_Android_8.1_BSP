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

package android.support.test.aupt;

import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.test.uiautomator.UiDevice;
import android.test.AndroidTestRunner;
import android.test.InstrumentationTestCase;
import android.test.InstrumentationTestRunner;
import android.util.Log;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestListener;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Ultra-fancy TestRunner to use when running AUPT: supports
 *
 * - Picking up tests from dexed JARs
 * - Running tests for multiple iterations or in a custom order
 * - Terminating tests after UI errors, timeouts, or when dependent processes die
 * - Injecting additional information into custom TestCase subclasses
 * - Passing through continuous metric-collection to a DataCollector instance
 * - Collecting bugreports and heapdumps
 *
 */
public class AuptTestRunner extends InstrumentationTestRunner {
    /* Constants */
    private static final String LOG_TAG = AuptTestRunner.class.getSimpleName();
    private static final Long ANR_DELAY = 30000L;
    private static final Long DEFAULT_SUITE_TIMEOUT = 0L;
    private static final Long DEFAULT_TEST_TIMEOUT = 10L;
    private static final SimpleDateFormat SCREENSHOT_DATE_FORMAT =
        new SimpleDateFormat("dd-mm-yy:HH:mm:ss:SSS");

    /* Keep a pointer to our argument bundle around for testing */
    private Bundle mParams;

    /* Primitive Parameters */
    private boolean mDeleteOldFiles;
    private long mFileRetainCount;
    private boolean mGenerateAnr;
    private boolean mRecordMeminfo;
    private long mIterations;
    private long mSeed;

    /* Dumpheap Parameters */
    private boolean mDumpheapEnabled;
    private long mDumpheapInterval;
    private long mDumpheapThreshold;
    private long mMaxDumpheaps;

    /* String Parameters */
    private List<String> mJars = new ArrayList<>();
    private List<String> mMemoryTrackedProcesses = new ArrayList<>();
    private List<String> mFinishCommands;

    /* Other Parameters */
    private File mResultsDirectory;

    /* Helpers */
    private Scheduler mScheduler;
    private DataCollector mDataCollector;
    private DexTestRunner mRunner;

    /* Logging */
    private ProcessStatusTracker mProcessTracker;
    private List<MemHealthRecord> mMemHealthRecords = new ArrayList<>();
    private Map<String, Long> mDumpheapCount = new HashMap<>();
    private Map<String, Long> mLastDumpheap = new HashMap<>();

    /* Test Initialization */
    @Override
    public void onCreate(Bundle params) {
        mParams = params;

        // Parse out primitive parameters
        mIterations = parseLongParam("iterations", 1);
        mRecordMeminfo = parseBoolParam("record_meminfo", false);
        mDumpheapEnabled = parseBoolParam("enableDumpheap", false);
        mDumpheapThreshold = parseLongParam("dumpheapThreshold", 200 * 1024 * 1024);
        mDumpheapInterval = parseLongParam("dumpheapInterval", 60 * 60 * 1000);
        mMaxDumpheaps = parseLongParam("maxDumpheaps", 5);
        mSeed = parseLongParam("seed", new Random().nextLong());

        // Option: -e finishCommand 'a;b;c;d'
        String finishCommandArg = parseStringParam("finishCommand", null);
        mFinishCommands =
                finishCommandArg == null
                        ? Arrays.<String>asList()
                        : Arrays.asList(finishCommandArg.split("\\s*;\\s*"));

        // Option: -e shuffle true
        mScheduler = parseBoolParam("shuffle", false)
                ? Scheduler.shuffled(new Random(mSeed), mIterations)
                : Scheduler.sequential(mIterations);

        // Option: -e jars aupt-app-tests.jar:...
        mJars.addAll(DexTestRunner.parseDexedJarPaths(parseStringParam("jars", "")));

        // Option: -e trackMemory com.pkg1,com.pkg2,...
        String memoryTrackedProcesses = parseStringParam("trackMemory", null);

        if (memoryTrackedProcesses != null) {
            mMemoryTrackedProcesses = Arrays.asList(memoryTrackedProcesses.split(","));
        } else {
            try {
                // Deprecated approach: get tracked processes from a file.
                String trackMemoryFileName =
                        Environment.getExternalStorageDirectory() + "/track_memory.txt";

                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        new FileInputStream(new File(trackMemoryFileName))));

                mMemoryTrackedProcesses = Arrays.asList(reader.readLine().split(","));
                reader.close();
            } catch (NullPointerException | IOException ex) {
                mMemoryTrackedProcesses = Arrays.asList();
            }
        }

        // Option: -e detectKill com.pkg1,...,com.pkg8
        String processes = parseStringParam("detectKill", null);

        if (processes != null) {
            mProcessTracker = new ProcessStatusTracker(processes.split(","));
        } else {
            mProcessTracker = new ProcessStatusTracker(new String[] {});
        }

        // Option: -e outputLocation aupt_results
        mResultsDirectory = new File(Environment.getExternalStorageDirectory(),
                parseStringParam("outputLocation", "aupt_results"));
        if (!mResultsDirectory.exists() && !mResultsDirectory.mkdirs()) {
            Log.w(LOG_TAG, "Could not find or create output directory " + mResultsDirectory);
        }

        // Option: -e fileRetainCount 1
        mFileRetainCount = parseLongParam("fileRetainCount", -1);
        mDeleteOldFiles = (mFileRetainCount != -1);

        // Primary logging infrastructure
        mDataCollector = new DataCollector(
                TimeUnit.MINUTES.toMillis(parseLongParam("bugreportInterval", 0)),
                TimeUnit.MINUTES.toMillis(parseLongParam("jankInterval", 0)),
                TimeUnit.MINUTES.toMillis(parseLongParam("meminfoInterval", 0)),
                TimeUnit.MINUTES.toMillis(parseLongParam("cpuinfoInterval", 0)),
                TimeUnit.MINUTES.toMillis(parseLongParam("fragmentationInterval", 0)),
                TimeUnit.MINUTES.toMillis(parseLongParam("ionInterval", 0)),
                TimeUnit.MINUTES.toMillis(parseLongParam("pagetypeinfoInterval", 0)),
                TimeUnit.MINUTES.toMillis(parseLongParam("traceInterval", 0)),
                TimeUnit.MINUTES.toMillis(parseLongParam("bugreportzInterval", 0)),
                mResultsDirectory, this);

        // Make our TestRunner and make sure we injectInstrumentation.
        mRunner = new DexTestRunner(this, mScheduler, mJars,
                TimeUnit.MINUTES.toMillis(parseLongParam("testCaseTimeout", DEFAULT_TEST_TIMEOUT)),
                TimeUnit.MINUTES.toMillis(parseLongParam("suiteTimeout", DEFAULT_SUITE_TIMEOUT))) {
            @Override
            public void runTest(TestResult result) {
                for (TestCase test: mTestCases) {
                    injectInstrumentation(test);
                }

                try {
                    super.runTest(result);
                } finally {
                    mDataCollector.stop();
                }
            }
        };

        // Aupt's TestListeners
        mRunner.addTestListener(new PeriodicHeapDumper());
        mRunner.addTestListener(new MemHealthRecorder());
        mRunner.addTestListener(new DcimCleaner());
        mRunner.addTestListener(new PidChecker());
        mRunner.addTestListener(new TimeoutStackDumper());
        mRunner.addTestListener(new MemInfoDumper());
        mRunner.addTestListener(new FinishCommandRunner());
        mRunner.addTestListenerIf(parseBoolParam("generateANR", false), new ANRTrigger());
        mRunner.addTestListenerIf(parseBoolParam("quitOnError", false), new QuitOnErrorListener());
        mRunner.addTestListenerIf(parseBoolParam("checkBattery", false), new BatteryChecker());
        mRunner.addTestListenerIf(parseBoolParam("screenshots", false), new Screenshotter());

        // Start our loggers
        mDataCollector.start();

        // Start the test
        super.onCreate(params);
    }

    /* Option-parsing helpers */

    private long parseLongParam(String key, long alternative) throws NumberFormatException {
        if (mParams.containsKey(key)) {
            return Long.parseLong(mParams.getString(key));
        } else {
            return alternative;
        }
    }

    private boolean parseBoolParam(String key, boolean alternative)
            throws NumberFormatException {
        if (mParams.containsKey(key)) {
            return Boolean.parseBoolean(mParams.getString(key));
        } else {
            return alternative;
        }
    }

    private String parseStringParam(String key, String alternative) {
        if (mParams.containsKey(key)) {
            return mParams.getString(key);
        } else {
            return alternative;
        }
    }

    /* Utility methods */

    /**
     * Injects instrumentation into InstrumentationTestCase and AuptTestCase instances
     */
    private void injectInstrumentation(Test test) {
        if (InstrumentationTestCase.class.isAssignableFrom(test.getClass())) {
            InstrumentationTestCase instrTest = (InstrumentationTestCase) test;

            instrTest.injectInstrumentation(AuptTestRunner.this);
        }
    }

    /* Passthrough to our DexTestRunner */
    @Override
    protected AndroidTestRunner getAndroidTestRunner() {
        return mRunner;
    }

    @Override
    public Context getTargetContext() {
        return new ContextWrapper(super.getTargetContext()) {
            @Override
            public ClassLoader getClassLoader() {
                if(mRunner != null) {
                    return mRunner.getDexClassLoader();
                } else {
                    throw new RuntimeException("DexTestRunner not initialized!");
                }
            }
        };
    }

    /**
     * A simple abstract instantiation of TestListener
     *
     * Primarily meant to work around Java 7's lack of interface-default methods.
     */
    abstract static class AuptListener implements TestListener {
        /** Called when a test throws an exception. */
        public void addError(Test test, Throwable t) {}

        /** Called when a test fails. */
        public void addFailure(Test test, AssertionFailedError t) {}

        /** Called whenever a test ends. */
        public void endTest(Test test) {}

        /** Called whenever a test begins. */
        public void startTest(Test test) {}
    }

    /**
     * Periodically Heap-dump to assist with memory-leaks.
     */
    private class PeriodicHeapDumper extends AuptListener {
        private Thread mHeapDumpThread;

        private class InternalHeapDumper implements Runnable {
            private void recordDumpheap(String proc, long pss) throws IOException {
                if (!mDumpheapEnabled) {
                    return;
                }
                Long count = mDumpheapCount.get(proc);
                if (count == null) {
                    count = 0L;
                }
                Long lastDumpheap = mLastDumpheap.get(proc);
                if (lastDumpheap == null) {
                    lastDumpheap = 0L;
                }
                long currentTime = SystemClock.uptimeMillis();
                if (pss > mDumpheapThreshold && count < mMaxDumpheaps &&
                        currentTime - lastDumpheap > mDumpheapInterval) {
                    recordDumpheap(proc);
                    mDumpheapCount.put(proc, count + 1);
                    mLastDumpheap.put(proc, currentTime);
                }
            }

            private void recordDumpheap(String proc) throws IOException {
                long count = mDumpheapCount.get(proc);

                String filename = String.format("dumpheap-%s-%d", proc, count);
                String tempFilename = "/data/local/tmp/" + filename;
                String finalFilename = mResultsDirectory + "/" + filename;

                AuptTestRunner.this.getUiAutomation().executeShellCommand(
                        String.format("am dumpheap %s %s", proc, tempFilename));

                SystemClock.sleep(3000);

                AuptTestRunner.this.getUiAutomation().executeShellCommand(
                        String.format("cp %s %s", tempFilename, finalFilename));
            }

            public void run() {
                try {
                    while (true) {
                        Thread.sleep(mDumpheapInterval);

                        for(String proc : mMemoryTrackedProcesses) {
                            recordDumpheap(proc);
                        }
                    }
                } catch (InterruptedException iex) {
                } catch (IOException ioex) {
                    Log.e(LOG_TAG, "Failed to write heap dump!", ioex);
                }
            }
        }

        @Override
        public void startTest(Test test) {
            mHeapDumpThread = new Thread(new InternalHeapDumper());
            mHeapDumpThread.start();
        }

        @Override
        public void endTest(Test test) {
            try {
                mHeapDumpThread.interrupt();
                mHeapDumpThread.join();
            } catch (InterruptedException iex) { }
        }
    }

    /**
     * Dump memory info on test start/stop
     */
    private class MemInfoDumper extends AuptListener {
        private void dumpMemInfo() {
            if (mRecordMeminfo) {
                FilesystemUtil.dumpMeminfo(AuptTestRunner.this, "MemInfoDumper");
            }
        }

        @Override
        public void startTest(Test test) {
            dumpMemInfo();
        }

        @Override
        public void endTest(Test test) {
            dumpMemInfo();
        }
    }

    /**
     * Record all of our MemHealthRecords
     */
    private class MemHealthRecorder extends AuptListener {
        @Override
        public void startTest(Test test) {
            recordMemHealth();
        }

        @Override
        public void endTest(Test test) {
            recordMemHealth();

            try {
                MemHealthRecord.saveVerbose(mMemHealthRecords,
                        new File(mResultsDirectory, "memory-health.txt").getPath());
                MemHealthRecord.saveCsv(mMemHealthRecords,
                        new File(mResultsDirectory, "memory-health-details.txt").getPath());

                mMemHealthRecords.clear();
            } catch (IOException ioex) {
                Log.e(LOG_TAG, "Error writing MemHealthRecords", ioex);
            }
        }

        private void recordMemHealth() {
            try {
                mMemHealthRecords.addAll(MemHealthRecord.get(
                      AuptTestRunner.this,
                      mMemoryTrackedProcesses,
                      System.currentTimeMillis(),
                      getForegroundProcs()));
            } catch (IOException ioex) {
                Log.e(LOG_TAG, "Error collecting MemHealthRecords", ioex);
            }
        }

        private List<String> getForegroundProcs() {
            List<String> foregroundProcs = new ArrayList<String>();
            try {
                String compactMeminfo = MemHealthRecord.getProcessOutput(AuptTestRunner.this,
                        "dumpsys meminfo -c");

                for (String line : compactMeminfo.split("\\r?\\n")) {
                    if (line.contains("proc,fore")) {
                        String proc = line.split(",")[2];
                        foregroundProcs.add(proc);
                    }
                }
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error while getting foreground process", e);
            } finally {
                return foregroundProcs;
            }
        }
    }

    /**
     * Kills application and dumps UI Hierarchy on test error
     */
    private class QuitOnErrorListener extends AuptListener {
        @Override
        public void addError(Test test, Throwable t) {
            Log.e(LOG_TAG, "Caught exception from a test", t);

            if ((t instanceof AuptTerminator)) {
                throw (AuptTerminator)t;
            } else {

                // Check if our exception is caused by process dependency
                if (test instanceof AuptTestCase) {
                    mProcessTracker.setUiAutomation(getUiAutomation());
                    mProcessTracker.verifyRunningProcess();
                }

                // If that didn't throw, then dump our hierarchy
                Log.v(LOG_TAG, "Dumping UI hierarchy");
                try {
                    UiDevice.getInstance(AuptTestRunner.this).dumpWindowHierarchy(
                            new File("/data/local/tmp/error_dump.xml"));
                } catch (IOException e) {
                    Log.w(LOG_TAG, "Failed to create UI hierarchy dump for UI error", e);
                }
            }

            // Quit on an error
            throw new AuptTerminator(t.getMessage(), t);
        }

        @Override
        public void addFailure(Test test, AssertionFailedError t) {
            // Quit on an error
            throw new AuptTerminator(t.getMessage(), t);
        }
    }

    /**
     * Makes sure the processes this test requires are all alive
     */
    private class PidChecker extends AuptListener {
        @Override
        public void startTest(Test test) {
            mProcessTracker.setUiAutomation(getUiAutomation());
            mProcessTracker.verifyRunningProcess();
        }

        @Override
        public void endTest(Test test) {
            mProcessTracker.verifyRunningProcess();
        }
    }

    /**
     * Initialization for tests that touch the camera
     */
    private class DcimCleaner extends AuptListener {
        @Override
        public void startTest(Test test) {
            if (!mDeleteOldFiles) {
                return;
            }

            File dcimFolder = new File(Environment.getExternalStorageDirectory(), "DCIM");
            File cameraFolder = new File(dcimFolder, "Camera");

            if (dcimFolder.exists()) {
                if (cameraFolder.exists()) {
                    File[] allMediaFiles = cameraFolder.listFiles();
                    Arrays.sort(allMediaFiles, new Comparator<File>() {
                        public int compare(File f1, File f2) {
                            return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
                        }
                    });
                    for (int i = 0; i < allMediaFiles.length - mFileRetainCount; i++) {
                        allMediaFiles[i].delete();
                    }
                } else {
                    Log.w(LOG_TAG, "No Camera folder found to delete from.");
                }
            } else {
                Log.w(LOG_TAG, "No DCIM folder found to delete from.");
            }
        }
    }

    /**
     * Makes sure the battery hasn't died before and after each test.
     */
    private class BatteryChecker extends AuptListener {
        private static final double BATTERY_THRESHOLD = 0.05;

        private void checkBattery() {
            Intent batteryIntent = getContext().registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int rawLevel = batteryIntent.getIntExtra("level", -1);
            int scale = batteryIntent.getIntExtra("scale", -1);

            if (rawLevel < 0 || scale <= 0) {
                return;
            }

            double level = (double) rawLevel / (double) scale;
            if (level < BATTERY_THRESHOLD) {
                throw new AuptTerminator(String.format("Current battery level %f lower than %f",
                        level,
                        BATTERY_THRESHOLD));
            }
        }

        @Override
        public void startTest(Test test) {
            checkBattery();
        }
    }

    /**
     * Generates heap dumps when a test times out
     */
    private class TimeoutStackDumper extends AuptListener {
        private String getStackTraces() {
            StringBuilder sb = new StringBuilder();
            Map<Thread, StackTraceElement[]> stacks = Thread.getAllStackTraces();
            for (Thread t : stacks.keySet()) {
                sb.append(t.toString()).append('\n');
                for (StackTraceElement ste : t.getStackTrace()) {
                    sb.append("\tat ").append(ste.toString()).append('\n');
                }
                sb.append('\n');
            }
            return sb.toString();
        }

        @Override
        public void addError(Test test, Throwable t) {
            if (t instanceof TimeoutException) {
                Log.d("THREAD_DUMP", getStackTraces());
            }
        }
    }

    /** Generates ANRs when a test takes too long. */
    private class ANRTrigger extends AuptListener {
        @Override
        public void addError(Test test, Throwable t) {
            if (t instanceof TimeoutException) {
                Context ctx = getTargetContext();
                Log.d(LOG_TAG, "About to induce artificial ANR for debugging");
                ctx.startService(new Intent(ctx, AnrGenerator.class));

                try {
                    Thread.sleep(ANR_DELAY);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted while waiting for AnrGenerator...");
                }
            }
        }

        /** Service that hangs to trigger an ANR. */
        private class AnrGenerator extends Service {
            @Override
            public IBinder onBind(Intent intent) {
                return null;
            }

            @Override
            public int onStartCommand(Intent intent, int flags, int id) {
                Log.i(LOG_TAG, "in service start -- about to hang");
                try {
                    Thread.sleep(ANR_DELAY);
                } catch (InterruptedException e) {
                    Log.wtf(LOG_TAG, e);
                }
                Log.i(LOG_TAG, "service hang finished -- stopping and returning");
                stopSelf();
                return START_NOT_STICKY;
            }
        }
    }

    /**
     * Collect a screenshot on test failure.
     */
    private class Screenshotter extends AuptListener {
        private void collectScreenshot(Test test, String suffix) {
            UiDevice device = UiDevice.getInstance(AuptTestRunner.this);

            if (device == null) {
                Log.w(LOG_TAG, "Couldn't collect screenshot on test failure");
                return;
            }

            String testName =
                    test instanceof TestCase
                    ? ((TestCase) test).getName()
                    : (test instanceof TestSuite ? ((TestSuite) test).getName() : test.toString());

            String fileName =
                    mResultsDirectory.getPath()
                            + "/" + testName.replaceAll(".", "_")
                            + suffix + ".png";

            device.takeScreenshot(new File(fileName));
        }

        @Override
        public void addError(Test test, Throwable t) {
            collectScreenshot(test,
                    "_failure_screenshot_" + SCREENSHOT_DATE_FORMAT.format(new Date()));
        }

        @Override
        public void addFailure(Test test, AssertionFailedError t) {
            collectScreenshot(test,
                    "_failure_screenshot_" + SCREENSHOT_DATE_FORMAT.format(new Date()));
        }
    }

    /** Runs a command when a test finishes. */
    private class FinishCommandRunner extends AuptListener {
        @Override
        public void endTest(Test test) {
            for (String command : mFinishCommands) {
                AuptTestRunner.this.getUiAutomation().executeShellCommand(command);
            }
        }
    }
}
