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

package android.dumpsys.cts;

import com.android.tradefed.log.LogUtil.CLog;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test to check the format of the dumps of the processstats test.
 */
public class ProcessStatsDumpsysTest extends BaseDumpsysTest {
    private static final String DEVICE_SIDE_TEST_APK = "CtsProcStatsApp.apk";
    private static final String DEVICE_SIDE_TEST_PACKAGE = "com.android.server.cts.procstats";

    private static final String DEVICE_SIDE_HELPER_APK = "CtsProcStatsHelperApp.apk";
    private static final String DEVICE_SIDE_HELPER_PACKAGE = "com.android.server.cts.procstatshelper";

    /**
     * Maximum allowance scale factor when checking a duration time.
     *
     * If [actual value] > [expected value] * {@link #DURATION_TIME_MAX_FACTOR},
     * then the test fails.
     *
     * Because the run duration time may include the process startup time, we need a rather big
     * allowance.
     */
    private static final double DURATION_TIME_MAX_FACTOR = 2;

    /**
     * Tests the output of "dumpsys procstats -c". This is a proxy for testing "dumpsys procstats
     * --checkin", since the latter is not idempotent.
     */
    public void testProcstatsOutput() throws Exception {
        // First, run the helper app so that we have some interesting records in the output.
        checkWithProcStatsApp();

        String procstats = mDevice.executeShellCommand("dumpsys procstats -c");
        assertNotNull(procstats);
        assertTrue(procstats.length() > 0);

        final int sep24h = procstats.indexOf("AGGREGATED OVER LAST 24 HOURS:");
        final int sep3h = procstats.indexOf("AGGREGATED OVER LAST 3 HOURS:");

        assertTrue("24 hour stats not found.", sep24h > 1);
        assertTrue("3 hour stats not found.", sep3h > 1);

        // Current
        checkProcStateOutput(procstats.substring(0, sep24h), /*checkAvg=*/ true);

        // Last 24 hours
        checkProcStateOutput(procstats.substring(sep24h, sep3h), /*checkAvg=*/ false);

        // Last 3 hours
        checkProcStateOutput(procstats.substring(sep3h), /*checkAvg=*/ false);
    }

    private static String[] commaSplit(String line) {
        if (line.endsWith(",")) {
            line = line + " ";
        }
        final String[] values = line.split(",");
        if (" ".equals(values[values.length - 1])) {
            values[values.length - 1] = "";
        }
        return values;
    }

    private void checkProcStateOutput(String text, boolean checkAvg) throws Exception {
        final Set<String> seenTags = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(
                new StringReader(text))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                CLog.d("Checking line: " + line);

                String[] parts = commaSplit(line);
                seenTags.add(parts[0]);

                switch (parts[0]) {
                    case "vers":
                        assertEquals(2, parts.length);
                        assertEquals(5, Integer.parseInt(parts[1]));
                        break;
                    case "period":
                        checkPeriod(parts);
                        break;
                    case "pkgproc":
                        checkPkgProc(parts);
                        break;
                    case "pkgpss":
                        checkPkgPss(parts, checkAvg);
                        break;
                    case "pkgsvc-bound":
                    case "pkgsvc-exec":
                    case "pkgsvc-run":
                    case "pkgsvc-start":
                        checkPkgSvc(parts);
                        break;
                    case "pkgkills":
                        checkPkgKills(parts, checkAvg);
                        break;
                    case "proc":
                        checkProc(parts);
                        break;
                    case "pss":
                        checkPss(parts, checkAvg);
                        break;
                    case "kills":
                        checkKills(parts, checkAvg);
                        break;
                    case "total":
                        checkTotal(parts);
                        break;
                    default:
                        break;
                }
            }
        }

        assertSeenTag(seenTags, "vers");
        assertSeenTag(seenTags, "period");
        assertSeenTag(seenTags, "pkgproc");
        assertSeenTag(seenTags, "proc");
        assertSeenTag(seenTags, "pss");
        assertSeenTag(seenTags, "total");
        assertSeenTag(seenTags, "weights");
        assertSeenTag(seenTags, "availablepages");
    }

    private void checkPeriod(String[] parts) {
        assertTrue("Length should be >= 5, found: " + parts.length,
                parts.length >= 5);
        assertNotNull(parts[1]); // date
        assertLesserOrEqual(parts[2], parts[3]); // start time and end time (msec)
        for (int i = 4; i < parts.length; i++) {
            switch (parts[i]) {
                case "shutdown":
                case "sysprops":
                case "complete":
                case "partial":
                case "swapped-out-pss":
                    continue;
            }
            fail("Invalid value '" + parts[i] + "' found.");
        }
    }

    private void checkPkgProc(String[] parts) {
        int statesStartIndex;

        assertTrue(parts.length >= 5);
        assertNotNull(parts[1]); // package name
        assertNonNegativeInteger(parts[2]); // uid
        assertNonNegativeInteger(parts[3]); // app version
        assertNotNull(parts[4]); // process
        statesStartIndex = 5;

        for (int i = statesStartIndex; i < parts.length; i++) {
            String[] subparts = parts[i].split(":");
            assertEquals(2, subparts.length);
            checkTag(subparts[0], true); // tag
            assertNonNegativeInteger(subparts[1]); // duration (msec)
        }
    }

    private void checkTag(String tag, boolean hasProcess) {
        assertEquals(hasProcess ? 3 : 2, tag.length());

        // screen: 0 = off, 1 = on
        char s = tag.charAt(0);
        if (s != '0' && s != '1') {
            fail("malformed tag: " + tag);
        }

        // memory: n = normal, m = moderate, l = low, c = critical
        char m = tag.charAt(1);
        if (m != 'n' && m != 'm' && m != 'l' && m != 'c') {
            fail("malformed tag: " + tag);
        }

        if (hasProcess) {
            char p = tag.charAt(2);
            assertTrue("malformed tag: " + tag, "ptfbuwsxrhlace".indexOf(p) >= 0);
        }
    }

    private void checkPkgPss(String[] parts, boolean checkAvg) {
        int statesStartIndex;

        assertTrue(parts.length >= 5);
        assertNotNull(parts[1]); // package name
        assertNonNegativeInteger(parts[2]); // uid
        assertNonNegativeInteger(parts[3]); // app version
        assertNotNull(parts[4]); // process
        statesStartIndex = 5;

        for (int i = statesStartIndex; i < parts.length; i++) {
            String[] subparts = parts[i].split(":");
            assertEquals(8, subparts.length);
            checkTag(subparts[0], true); // tag
            assertNonNegativeInteger(subparts[1]); // sample size
            assertMinAvgMax(subparts[2], subparts[3], subparts[4], checkAvg); // pss
            assertMinAvgMax(subparts[5], subparts[6], subparts[7], checkAvg); // uss
        }
    }

    private void checkPkgSvc(String[] parts) {
        int statesStartIndex;

        assertTrue(parts.length >= 6);
        assertNotNull(parts[1]); // package name
        assertNonNegativeInteger(parts[2]); // uid
        assertNonNegativeInteger(parts[3]); // app version
        assertNotNull(parts[4]); // service name
        assertNonNegativeInteger(parts[5]); // count
        statesStartIndex = 6;

        for (int i = statesStartIndex; i < parts.length; i++) {
            String[] subparts = parts[i].split(":");
            assertEquals(2, subparts.length);
            checkTag(subparts[0], false); // tag
            assertNonNegativeInteger(subparts[1]); // duration (msec)
        }
    }

    private void checkPkgKills(String[] parts, boolean checkAvg) {
        String pssStr;

        assertEquals(9, parts.length);
        assertNotNull(parts[1]); // package name
        assertNonNegativeInteger(parts[2]); // uid
        assertNonNegativeInteger(parts[3]); // app version
        assertNotNull(parts[4]); // process
        assertNonNegativeInteger(parts[5]); // wakes
        assertNonNegativeInteger(parts[6]); // cpu
        assertNonNegativeInteger(parts[7]); // cached
        pssStr = parts[8];

        String[] subparts = pssStr.split(":");
        assertEquals(3, subparts.length);
        assertMinAvgMax(subparts[0], subparts[1], subparts[2], checkAvg); // pss
    }

    private void checkProc(String[] parts) {
        assertTrue(parts.length >= 3);
        assertNotNull(parts[1]); // package name
        assertNonNegativeInteger(parts[2]); // uid

        for (int i = 3; i < parts.length; i++) {
            String[] subparts = parts[i].split(":");
            assertEquals(2, subparts.length);
            checkTag(subparts[0], true); // tag
            assertNonNegativeInteger(subparts[1]); // duration (msec)
        }
    }

    private void checkPss(String[] parts, boolean checkAvg) {
        assertTrue(parts.length >= 3);
        assertNotNull(parts[1]); // package name
        assertNonNegativeInteger(parts[2]); // uid

        for (int i = 3; i < parts.length; i++) {
            String[] subparts = parts[i].split(":");
            assertEquals(8, subparts.length);
            checkTag(subparts[0], true); // tag
            assertNonNegativeInteger(subparts[1]); // sample size
            assertMinAvgMax(subparts[2], subparts[3], subparts[4], checkAvg); // pss
            assertMinAvgMax(subparts[5], subparts[6], subparts[7], checkAvg); // uss
        }
    }

    private void checkKills(String[] parts, boolean checkAvg) {
        assertEquals(7, parts.length);
        assertNotNull(parts[1]); // package name
        assertNonNegativeInteger(parts[2]); // uid
        assertNonNegativeInteger(parts[3]); // wakes
        assertNonNegativeInteger(parts[4]); // cpu
        assertNonNegativeInteger(parts[5]); // cached
        String pssStr = parts[6];

        String[] subparts = pssStr.split(":");
        assertEquals(3, subparts.length);
        assertMinAvgMax(subparts[0], subparts[1], subparts[2], checkAvg); // pss
    }

    private void checkTotal(String[] parts) {
        assertTrue(parts.length >= 2);
        for (int i = 1; i < parts.length; i++) {
            String[] subparts = parts[i].split(":");
            checkTag(subparts[0], false); // tag

            assertNonNegativeInteger(subparts[1]); // duration (msec)
        }
    }

    /**
     * Find the first line with the prefix, and return the rest of the line.
     */
    private static String findLine(String prefix, String[] lines) {
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                CLog.d("Found line: " + line);
                return line.substring(prefix.length());
            }
        }
        fail("Line with prefix '" + prefix + "' not found.");
        return null;
    }

    private static long getTagValueSum(String[] parts, String tagRegex) {
        final Pattern tagPattern = Pattern.compile("^" + tagRegex + "\\:");

        boolean found = false;
        long sum = 0;
        for (int i = 0; i < parts.length; i++){
            final String part = parts[i];
            final Matcher m = tagPattern.matcher(part);
            if (!m.find()) {
                continue;
            }
            // Extract the rest of the part and parse as a long.
            sum += assertInteger(parts[i].substring(m.end(0)));
            found = true;
        }
        assertTrue("Tag '" + tagRegex + "' not found.", found);
        return sum;
    }

    private static void assertTagValueLessThan(String[] parts, String tagRegex,
            long expectedMax) {
        final long sum = getTagValueSum(parts, tagRegex);

        assertTrue("Total values for '" + tagRegex
                + "' expected to be <= (" + expectedMax + ") but was: "
                + sum, sum <= expectedMax);
    }

    private static void assertTagValueSumAbout(String[] parts, String tagRegex,
            long expectedValue) {
        final long sum = getTagValueSum(parts, tagRegex);

        assertTrue("Total values for '" + tagRegex
                + "' expected to be >= " + expectedValue + " but was: "
                + sum, sum >= expectedValue);
        assertTrue("Total values for '" + tagRegex
                + "' expected to be <= (" + expectedValue + ") * "
                + DURATION_TIME_MAX_FACTOR + " but was: "
                + sum, sum <= (expectedValue * DURATION_TIME_MAX_FACTOR));
    }

    private void checkWithProcStatsApp() throws Exception {
        getDevice().uninstallPackage(DEVICE_SIDE_TEST_PACKAGE);
        getDevice().uninstallPackage(DEVICE_SIDE_HELPER_PACKAGE);

        final long startNs = System.nanoTime();

        installPackage(DEVICE_SIDE_TEST_APK, /* grantPermissions= */ true);
        installPackage(DEVICE_SIDE_HELPER_APK, /* grantPermissions= */ true);

        final int helperAppUid = Integer.parseInt(execCommandAndGetFirstGroup(
                "dumpsys package " + DEVICE_SIDE_HELPER_PACKAGE, "userId=(\\d+)"));
        final String uid = String.valueOf(helperAppUid);

        CLog.i("Start: Helper app UID: " + helperAppUid);

        try {
            // Run the device side test which makes some network requests.
            runDeviceTests(DEVICE_SIDE_TEST_PACKAGE,
                    "com.android.server.cts.procstats.ProcStatsTest", "testLaunchApp");
        } finally {
            getDevice().uninstallPackage(DEVICE_SIDE_TEST_PACKAGE);
            getDevice().uninstallPackage(DEVICE_SIDE_HELPER_PACKAGE);
        }
        final long finishNs = System.nanoTime();
        CLog.i("Finish: Took " + ((finishNs - startNs) / 1000000) + " ms");

        // The total running duration should be less than this, since we've uninstalled the app.
        final long maxRunTime = (finishNs - startNs) / 1000000;

        // Get the current procstats.
        final String procstats = mDevice.executeShellCommand("dumpsys procstats -c --current");
        assertNotNull(procstats);
        assertTrue(procstats.length() > 0);

        final String[] lines = procstats.split("\n");

        // Start checking.
        String parts[] = commaSplit(findLine(
                "pkgproc,com.android.server.cts.procstatshelper,$U,32123,,".replace("$U", uid),
                lines));
        assertTagValueSumAbout(parts, "0.t", 2000); // Screen off, foreground activity.
        assertTagValueSumAbout(parts, "1.t", 2000); // Screen on, foreground activity.
        assertTagValueSumAbout(parts, "1.f", 1000); // Screen on, foreground service.
        assertTagValueSumAbout(parts, "1.s", 500); // Screen on, background service.
        assertTagValueLessThan(parts, "...", maxRunTime); // total time.

//      We can't really assert there's always "pss".  If there is, then we do check the format in
//      checkProcStateOutput().
//        parts = commaSplit(findLine(
//                "pkgpss,com.android.server.cts.procstatshelper,$U,32123,,".replace("$U", uid),
//                lines));

        parts = commaSplit(findLine(
                ("pkgproc,com.android.server.cts.procstatshelper,$U,32123,"
                + "com.android.server.cts.procstatshelper:proc2,").replace("$U", uid),
                lines));

        assertTagValueSumAbout(parts, "0.f", 1000); // Screen off, foreground service.
        assertTagValueSumAbout(parts, "0.s", 500); // Screen off, background service.

        assertTagValueLessThan(parts, "...", maxRunTime); // total time.

//      We can't really assert there's always "pss".  If there is, then we do check the format in
//      checkProcStateOutput().
//        parts = commaSplit(findLine(
//                ("pkgpss,com.android.server.cts.procstatshelper,$U,32123,"
//                + "com.android.server.cts.procstatshelper:proc2,").replace("$U", uid),
//                lines));

        parts = commaSplit(findLine(
                ("pkgsvc-run,com.android.server.cts.procstatshelper,$U,32123,"
                + ".ProcStatsHelperServiceMain,").replace("$U", uid),
                lines));

        assertTagValueSumAbout(parts, "1.", 1500); // Screen on, running.

        parts = commaSplit(findLine(
                ("pkgsvc-start,com.android.server.cts.procstatshelper,$U,32123,"
                + ".ProcStatsHelperServiceMain,").replace("$U", uid),
                lines));

        assertTagValueSumAbout(parts, "1.", 1500); // Screen on, running.

//      Dose it always exist?
//        parts = commaSplit(findLine(
//                ("pkgsvc-exec,com.android.server.cts.procstatshelper,$U,32123,"
//                + ".ProcStatsHelperServiceMain,").replace("$U", uid),
//                lines));

        parts = commaSplit(findLine(
                ("pkgsvc-run,com.android.server.cts.procstatshelper,$U,32123,"
                + ".ProcStatsHelperServiceSub,").replace("$U", uid),
                lines));

        assertTagValueSumAbout(parts, "0.", 1500); // Screen off, running.

        parts = commaSplit(findLine(
                ("pkgsvc-start,com.android.server.cts.procstatshelper,$U,32123,"
                + ".ProcStatsHelperServiceSub,").replace("$U", uid),
                lines));

        assertTagValueSumAbout(parts, "0.", 1500); // Screen off, running.

//      Dose it always exist?
//        parts = commaSplit(findLine(
//                ("pkgsvc-exec,com.android.server.cts.procstatshelper,$U,32123,"
//                + ".ProcStatsHelperServiceSub,").replace("$U", uid),
//                lines));

    }
}
