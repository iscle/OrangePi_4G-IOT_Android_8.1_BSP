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

package android.sustainedPerformance.cts;

import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.ddmlib.Log;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.*;
/**
 * Test to check if device implements Sustained Performance Mode
 */
public class SustainedPerformanceHostTest extends DeviceTestCase {

    ITestDevice device;
    private static final String PACKAGE = "com.android.gputest";
    private static final String CLASS = "GPUStressTestActivity";
    private static final String START_COMMAND = String.format(
            "am start -W -a android.intent.action.MAIN -n %s/%s.%s",
            PACKAGE, PACKAGE, CLASS);
    private static final String START_COMMAND_MODE = String.format(
            "am start -W -a android.intent.action.MAIN -n %s/%s.%s --ez SustainedPerformanceMode true",
            PACKAGE, PACKAGE, CLASS);
    private static final String STOP_COMMAND = String.format(
            "am force-stop %s", PACKAGE);
    private static final String TEST_PACKAGE = "android.test.app";
    private static final String TEST_CLASS = "DeviceTestActivity";
    private static final String START_TEST_COMMAND = String.format(
            "am start -W -a android.intent.action.MAIN -n %s/%s.%s",
            TEST_PACKAGE, TEST_PACKAGE, TEST_CLASS);
    private static final String DHRYSTONE = "/data/local/tmp/";
    private static final String LOG_TAG = "sustainedPerfTest";

    private static ArrayList<Double> appResultsWithMode = new ArrayList<Double>();
    private static ArrayList<Double> appResultsWithoutMode = new ArrayList<Double>();
    private static ArrayList<Double> dhrystoneResultsWithMode = new ArrayList<Double>();
    private static ArrayList<Double> dhrystoneResultsWithoutMode = new ArrayList<Double>();
    private double dhryMin = Double.MAX_VALUE, dhryMax = Double.MIN_VALUE;
    private static long testDuration = 1800000; //30 minutes

    public class Dhrystone implements Runnable {
        private boolean modeEnabled;
        private long startTime;
        private long loopCount = 300000000;
        private long cpumask = 1;

        public Dhrystone(boolean enabled, long cm) {
            cpumask = cm;
            modeEnabled = enabled;
            startTime = System.currentTimeMillis();
        }

        public void run() {
            double[] testSet = new double[3];
            int index = 0;
            try {
                device.executeShellCommand("cd " + DHRYSTONE + " ; chmod 777 dhry");
                while (true) {
                    String result = device.executeShellCommand("echo " + loopCount
                          + " | taskset -a " + cpumask + " " + DHRYSTONE + "dhry");
                    if (Math.abs(System.currentTimeMillis() - startTime) >= testDuration) {
                        break;
                    } else if (result.contains("Measured time too small")) {
                         loopCount = loopCount*10;
                    } else if (!result.isEmpty()){
                         double dmips = Double.parseDouble(result);
                         testSet[index++] = dmips;
                         if (index == 3) {
                             synchronized(this) {
                                 if (modeEnabled) {
                                     dhrystoneResultsWithMode.add(testSet[1]);
                                 } else {
                                     dhrystoneResultsWithoutMode.add(testSet[1]);
                                 }
                                 if (testSet[1] > dhryMax) {
                                     dhryMax = testSet[1];
                                 }
                                 if (testSet[1] < dhryMin) {
                                     dhryMin = testSet[1];
                                 }
                                 index = 0;
                             }
                        }
                    }
               }
           } catch (Exception e) {
               Log.e(LOG_TAG, e.toString());

           }
        }
    }

    public void analyzeResults(String logs, boolean mode) {
        Double[] testSet = new Double[10];
        int index = 0;
        double min = Double.MAX_VALUE, max = Double.MIN_VALUE;
        boolean first = true;

        Scanner in = new Scanner(logs);
        while (in.hasNextLine()) {
            String line = in.nextLine();
            if(line.startsWith("I/"+CLASS)) {
                Double time = Double.parseDouble(line.split(":")[1]);
                testSet[index++] = time;
                if (index == 10) {
                    if (first) {
                        first = false;
                        index = 0;
                        continue;
                    }
                    Arrays.sort(testSet);
                    if (mode) {
                        appResultsWithMode.add(testSet[5]);
                    } else {
                        appResultsWithoutMode.add(testSet[5]);
                    }
                    if (testSet[5] > max) {
                        max = testSet[5];
                    }
                    if (testSet[5] < min) {
                        min = testSet[5];
                    }
                    index = 0;
                }
            }
        }
        in.close();
        double diff = (max - min)*100/max;
        if (mode) {
            appResultsWithMode.add(0, min);
            appResultsWithMode.add(1, max);
            appResultsWithMode.add(2, diff);
        } else {
            appResultsWithoutMode.add(0, min);
            appResultsWithoutMode.add(1, max);
            appResultsWithoutMode.add(2, diff);
        }
    }

    private void setUpEnvironment() throws Exception {
        dhryMin = Double.MAX_VALUE;
        dhryMax = Double.MIN_VALUE;
        Thread.sleep(600000);
        device.executeAdbCommand("logcat", "-c");
        device.executeShellCommand("settings put global airplane_mode_on 1");
        device.executeShellCommand("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true");
    }

    public void testShader() throws Exception {
        device = getDevice();

        /**
         * Check if the device supports Sustained Performance Mode.
         * If not then assert true and return.
         **/
        device.executeAdbCommand("logcat", "-c");
        device.executeShellCommand(START_TEST_COMMAND);
        String logs = device.executeAdbCommand("logcat", "-v", "brief", "-d", TEST_CLASS + ":I", "*:S");
        String testString = "";
        Scanner in = new Scanner(logs);
        while (in.hasNextLine()) {
            String line = in.nextLine();
            if(line.startsWith("I/"+TEST_CLASS)) {
                testString = line.split(":")[1].trim();
            }
        }
        in.close();
        if (testString.isEmpty()) {
            assertTrue(true);
            return;
        }

        appResultsWithoutMode.clear();
        appResultsWithMode.clear();
        dhrystoneResultsWithoutMode.clear();
        dhrystoneResultsWithMode.clear();

        /*
         * Run the test with the mode.
         * Start the application and collect stats.
         * Run two threads of dhrystone and collect stats.
         */
        setUpEnvironment();
        device.executeShellCommand(START_COMMAND_MODE);
        Thread dhrystone = new Thread(new Dhrystone(true, 1));
        Thread dhrystone1 = new Thread(new Dhrystone(true, 2));
        dhrystone.start();
        dhrystone1.start();
        Thread.sleep(testDuration);
        device.executeShellCommand(STOP_COMMAND);
        dhrystone.join();
        dhrystone1.join();
        logs = device.executeAdbCommand("logcat", "-v", "brief", "-d", CLASS + ":I", "*:S");
        analyzeResults(logs, true);
        double diff = (dhryMax - dhryMin)*100/dhryMax;
        dhrystoneResultsWithMode.add(0, dhryMin);
        dhrystoneResultsWithMode.add(1, dhryMax);
        dhrystoneResultsWithMode.add(2, diff);

        device.executeShellCommand("settings put global airplane_mode_on 0");
        device.executeShellCommand("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false");

        double resDhry = dhrystoneResultsWithMode.get(2);
        double resApp = appResultsWithMode.get(2);

        /* Report if performance is below 5% margin for both dhrystone and shader */
        if ((resDhry > 5) || (resApp > 5)) {
            Log.w("SustainedPerformanceHostTests",
                  "Sustainable mode results, Dhrystone: " + resDhry + " App: " + resApp);
        }

        /*
         * Error if the performance in the mode is not consistent with
         * 5% error margin for shader and 10% error margin for dhrystone.
         */
        assertFalse("Results in the mode are not sustainable",
                    (resDhry > 15) ||
                    (resApp > 5));
    }
}
