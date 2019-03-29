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

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

/**
 * Test to check the format of the dumps of the batterystats test.
 */
public class BatteryStatsDumpsysTest extends BaseDumpsysTest {
   private static final String TEST_APK = "CtsFramestatsTestApp.apk";
    private static final String TEST_PKG = "com.android.cts.framestatstestapp";

    /**
     * Tests the output of "dumpsys batterystats --checkin".
     *
     * @throws Exception
     */
    public void testBatterystatsOutput() throws Exception {
        String batterystats = mDevice.executeShellCommand("dumpsys batterystats --checkin");
        assertNotNull(batterystats);
        assertTrue(batterystats.length() > 0);

        Set<String> seenTags = new HashSet<>();
        int version = -1;

        try (BufferedReader reader = new BufferedReader(
                new StringReader(batterystats))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }


                // With a default limit of 0, empty strings at the end are discarded.
                // We still consider the empty string as a valid value in some cases.
                // Using any negative number for the limit will preserve a trailing empty string.
                // @see String#split(String, int)
                String[] parts = line.split(",", -1);
                assertInteger(parts[0]); // old version
                assertInteger(parts[1]); // UID
                switch (parts[2]) { // aggregation type
                    case "i":
                    case "l":
                    case "c":
                    case "u":
                        break;
                    default:
                        fail("malformed stat: " + parts[2]);
                }
                assertNotNull(parts[3]);
                seenTags.add(parts[3]);

                // Note the time fields are measured in milliseconds by default.
                switch (parts[3]) {
                    case "vers":
                        checkVersion(parts);
                        break;
                    case "uid":
                        checkUid(parts);
                        break;
                    case "apk":
                        checkApk(parts);
                        break;
                    case "pr":
                        checkProcess(parts);
                        break;
                    case "sr":
                        checkSensor(parts);
                        break;
                    case "vib":
                        checkVibrator(parts);
                        break;
                    case "fg":
                        checkForeground(parts);
                        break;
                    case "st":
                        checkStateTime(parts);
                        break;
                    case "wl":
                        checkWakelock(parts);
                        break;
                    case "awl":
                        checkAggregatedWakelock(parts);
                        break;
                    case "sy":
                        checkSync(parts);
                        break;
                    case "jb":
                        checkJob(parts);
                        break;
                    case "kwl":
                        checkKernelWakelock(parts);
                        break;
                    case "wr":
                        checkWakeupReason(parts);
                        break;
                    case "nt":
                        checkNetwork(parts);
                        break;
                    case "ua":
                        checkUserActivity(parts);
                        break;
                    case "bt":
                        checkBattery(parts);
                        break;
                    case "dc":
                        checkBatteryDischarge(parts);
                        break;
                    case "lv":
                        checkBatteryLevel(parts);
                        break;
                    case "wfl":
                        checkWifi(parts);
                        break;
                    case "m":
                        checkMisc(parts);
                        break;
                    case "gn":
                        checkGlobalNetwork(parts);
                        break;
                    case "br":
                        checkScreenBrightness(parts);
                        break;
                    case "sgt":
                    case "sgc":
                        checkSignalStrength(parts);
                        break;
                    case "sst":
                        checkSignalScanningTime(parts);
                        break;
                    case "dct":
                    case "dcc":
                        checkDataConnection(parts);
                        break;
                    case "wst":
                    case "wsc":
                        checkWifiState(parts);
                        break;
                    case "wsst":
                    case "wssc":
                        checkWifiSupplState(parts);
                        break;
                    case "wsgt":
                    case "wsgc":
                        checkWifiSignalStrength(parts);
                        break;
                    case "bst":
                    case "bsc":
                        checkBluetoothState(parts);
                        break;
                    case "blem":
                        checkBluetoothMisc(parts);
                        break;
                    case "pws":
                        checkPowerUseSummary(parts);
                        break;
                    case "pwi":
                        checkPowerUseItem(parts);
                        break;
                    case "dsd":
                    case "csd":
                        checkChargeDischargeStep(parts);
                        break;
                    case "dtr":
                        checkDischargeTimeRemain(parts);
                        break;
                    case "ctr":
                        checkChargeTimeRemain(parts);
                        break;
                    case "cpu":
                        checkUidCpuUsage(parts);
                    default:
                        break;
                }
            }
        }

        // spot check a few tags
        assertSeenTag(seenTags, "vers");
        assertSeenTag(seenTags, "bt");
        assertSeenTag(seenTags, "dc");
        assertSeenTag(seenTags, "m");
    }

    private void checkVersion(String[] parts) {
        assertEquals(8, parts.length);
        assertInteger(parts[4]); // checkinVersion
        assertInteger(parts[5]); // parcelVersion
        assertNotNull(parts[6]); // startPlatformVersion
        assertNotNull(parts[7]); // endPlatformVersion
    }

    private void checkUid(String[] parts) {
        assertEquals(6, parts.length);
        assertInteger(parts[4]); // uid
        assertNotNull(parts[5]); // pkgName
    }

    private void checkApk(String[] parts) {
        assertEquals(10, parts.length);
        long wakeup_count = assertInteger(parts[4]); // wakeups
        assertNotNull(parts[5]); // apk
        assertNotNull(parts[6]); // service
        assertInteger(parts[7]); // startTime
        assertInteger(parts[8]); // starts
        assertInteger(parts[9]); // launches

        // Sanity check.
        assertTrue("wakeup count must be >= 0", wakeup_count >= 0);
    }

    private void checkProcess(String[] parts) {
        assertTrue(parts.length >= 9);
        assertNotNull(parts[4]); // process
        assertInteger(parts[5]); // userMillis
        assertInteger(parts[6]); // systemMillis
        assertInteger(parts[7]); // foregroundMillis
        assertInteger(parts[8]); // starts
    }

    private void checkSensor(String[] parts) {
        assertEquals(10, parts.length);
        assertInteger(parts[4]); // sensorNumber
        assertInteger(parts[5]); // totalTime
        assertInteger(parts[6]); // count
        assertInteger(parts[7]); // backgroundCount
        assertInteger(parts[8]); // actualTime
        assertInteger(parts[9]); // backgroundActualTime
    }

    private void checkVibrator(String[] parts) {
        assertEquals(6, parts.length);
        assertInteger(parts[4]); // totalTime
        assertInteger(parts[5]); // count
    }

    private void checkForeground(String[] parts) {
        assertEquals(6, parts.length);
        assertInteger(parts[4]); // totalTime
        assertInteger(parts[5]); // count
    }

    private void checkStateTime(String[] parts) {
        assertEquals(10, parts.length);
        assertInteger(parts[4]); // top
        assertInteger(parts[5]); // foreground_service
        assertInteger(parts[6]); // top_sleeping
        assertInteger(parts[7]); // foreground
        assertInteger(parts[8]); // background
        assertInteger(parts[9]); // cached
    }

    private void checkWakelock(String[] parts) {
        assertEquals(29, parts.length);
        assertNotNull(parts[4]);      // wakelock

        assertInteger(parts[5]);      // full totalTime
        assertEquals("f", parts[6]);  // full
        long full_count = assertInteger(parts[7]);      // full count
        assertInteger(parts[8]);      // current duration
        assertInteger(parts[9]);      // max duration
        assertInteger(parts[10]);     // total duration

        assertInteger(parts[11]);      // partial totalTime
        assertEquals("p", parts[12]);  // partial
        long partial_count = assertInteger(parts[13]);     // partial count
        assertInteger(parts[14]);      // current duration
        assertInteger(parts[15]);      // max duration
        assertInteger(parts[16]);      // total duration

        assertInteger(parts[17]);      // background partial totalTime
        assertEquals("bp", parts[18]); // background partial
        long bg_partial_count = assertInteger(parts[19]);     // background partial count
        assertInteger(parts[20]);      // current duration
        assertInteger(parts[21]);      // max duration
        assertInteger(parts[22]);      // total duration

        assertInteger(parts[23]);      // window totalTime
        assertEquals("w", parts[24]);  // window
        long window_count = assertInteger(parts[25]);     // window count
        assertInteger(parts[26]);      // current duration
        assertInteger(parts[27]);      // max duration
        assertInteger(parts[28]);      // total duration

        // Sanity checks.
        assertTrue("full wakelock count must be >= 0", full_count >= 0);
        assertTrue("partial wakelock count must be >= 0", partial_count >= 0);
        assertTrue("background partial wakelock count must be >= 0", bg_partial_count >= 0);
        assertTrue("window wakelock count must be >= 0", window_count >= 0);
    }

    private void checkAggregatedWakelock(String[] parts) {
        assertEquals(6, parts.length);
        assertInteger(parts[4]); // total time
        assertInteger(parts[5]); // background time
    }

    private void checkSync(String[] parts) {
        assertEquals(9, parts.length);
        assertNotNull(parts[4]); // sync
        assertInteger(parts[5]); // totalTime
        assertInteger(parts[6]); // count
        assertInteger(parts[7]); // bgTime
        assertInteger(parts[8]); // bgCount
    }

    private void checkJob(String[] parts) {
        assertEquals(9, parts.length);
        assertNotNull(parts[4]); // job
        assertInteger(parts[5]); // totalTime
        assertInteger(parts[6]); // count
        assertInteger(parts[7]); // bgTime
        assertInteger(parts[8]); // bgCount
    }

    private void checkKernelWakelock(String[] parts) {
        assertTrue(parts.length >= 7);
	assertNotNull(parts[4]); // Kernel wakelock
	assertInteger(parts[parts.length-2]); // totalTime
        assertInteger(parts[parts.length-1]); // count
    }

    private void checkWakeupReason(String[] parts) {
        assertTrue(parts.length >= 7);
        for (int i = 4; i < parts.length-2; i++) {
            assertNotNull(parts[i]); // part of wakeup
        }
        assertInteger(parts[parts.length-2]); // totalTime
        assertInteger(parts[parts.length-1]); // count
    }

    private void checkNetwork(String[] parts) {
        assertEquals(26, parts.length);
        long mbRx = assertInteger(parts[4]);  // mobileBytesRx
        long mbTx = assertInteger(parts[5]);  // mobileBytesTx
        long wbRx = assertInteger(parts[6]);  // wifiBytesRx
        long wbTx = assertInteger(parts[7]);  // wifiBytesTx
        long mpRx = assertInteger(parts[8]);  // mobilePacketsRx
        long mpTx = assertInteger(parts[9]);  // mobilePacketsTx
        long wpRx = assertInteger(parts[10]); // wifiPacketsRx
        long wpTx = assertInteger(parts[11]); // wifiPacketsTx
        assertInteger(parts[12]); // mobileActiveTime (usec)
        assertInteger(parts[13]); // mobileActiveCount
        assertInteger(parts[14]); // btBytesRx
        assertInteger(parts[15]); // btBytesTx
        assertInteger(parts[16]); // mobileWakeup
        assertInteger(parts[17]); // wifiWakeup
        long mbBgRx = assertInteger(parts[18]);  // mobileBytesRx
        long mbBgTx = assertInteger(parts[19]);  // mobileBytesTx
        long wbBgRx = assertInteger(parts[20]);  // wifiBytesRx
        long wbBgTx = assertInteger(parts[21]);  // wifiBytesTx
        long mpBgRx = assertInteger(parts[22]);  // mobilePacketsRx
        long mpBgTx = assertInteger(parts[23]);  // mobilePacketsTx
        long wpBgRx = assertInteger(parts[24]); // wifiPacketsRx
        long wpBgTx = assertInteger(parts[25]); // wifiPacketsTx

        // Assuming each packet contains some bytes, bytes >= packets >= 0.
        assertTrue("mobileBytesRx must be >= mobilePacketsRx", mbRx >= mpRx);
        assertTrue("mobilePacketsRx must be >= 0", mpRx >= 0);
        assertTrue("mobileBytesTx must be >= mobilePacketsTx", mbTx >= mpTx);
        assertTrue("mobilePacketsTx must be >= 0", mpTx >= 0);
        assertTrue("wifiBytesRx must be >= wifiPacketsRx", wbRx >= wpRx);
        assertTrue("wifiPacketsRx must be >= 0", wpRx >= 0);
        assertTrue("wifiBytesTx must be >= wifiPacketsTx", wbTx >= wpTx);
        assertTrue("wifiPacketsTx must be >= 0", wpTx >= 0);
        // Totals should be greater than or equal to background data numbers
        assertTrue("mobileBytesRx must be >= mobileBytesBgRx", mbRx >= mbBgRx);
        assertTrue("mobilePacketsRx must be >= mobilePacketsBgRx", mpRx >= mpBgRx);
        assertTrue("mobileBytesTx must be >= mobileBytesBgTx", mbTx >= mbBgTx);
        assertTrue("mobilePacketsTx must be >= mobilePacketsBgTx", mpTx >= mpBgTx);
        assertTrue("wifiBytesRx must be >= wifiBytesBgRx", wbRx >= wbBgRx);
        assertTrue("wifiPacketsRx must be >= wifiPacketsBgRx", wpRx >= wpBgRx);
        assertTrue("wifiBytesTx must be >= wifiBytesBgTx", wbTx >= wbBgTx);
        assertTrue("wifiPacketsTx must be >= wifiPacketsBgTx", wpTx >= wpBgTx);
    }

    private void checkUserActivity(String[] parts) {
        assertEquals(8, parts.length);
        assertInteger(parts[4]); // other
        assertInteger(parts[5]); // button
        assertInteger(parts[6]); // touch
        assertInteger(parts[7]); // accessibility
    }

    private void checkBattery(String[] parts) {
        assertEquals(16, parts.length);
        if (!parts[4].equals("N/A")) {
            assertInteger(parts[4]);  // startCount
        }
        long bReal = assertInteger(parts[5]);  // batteryRealtime
        long bUp = assertInteger(parts[6]);  // batteryUptime
        long tReal = assertInteger(parts[7]);  // totalRealtime
        long tUp = assertInteger(parts[8]);  // totalUptime
        assertInteger(parts[9]);  // startClockTime
        long bOffReal = assertInteger(parts[10]); // batteryScreenOffRealtime
        long bOffUp = assertInteger(parts[11]); // batteryScreenOffUptime
        long bEstCap = assertInteger(parts[12]); // batteryEstimatedCapacity
        assertInteger(parts[13]); // minLearnedBatteryCapacity
        assertInteger(parts[14]); // maxLearnedBatteryCapacity
        long bDoze = assertInteger(parts[15]); // screenDozeTime

        // The device cannot be up more than there are real-world seconds.
        assertTrue("batteryRealtime must be >= batteryUptime", bReal >= bUp);
        assertTrue("totalRealtime must be >= totalUptime", tReal >= tUp);
        assertTrue("batteryScreenOffRealtime must be >= batteryScreenOffUptime",
                bOffReal >= bOffUp);

        // total >= battery >= battery screen-off >= 0
        assertTrue("totalRealtime must be >= batteryRealtime", tReal >= bReal);
        assertTrue("batteryRealtime must be >= batteryScreenOffRealtime", bReal >= bOffReal);
        assertTrue("batteryScreenOffRealtime must be >= 0", bOffReal >= 0);
        assertTrue("totalUptime must be >= batteryUptime", tUp >= bUp);
        assertTrue("batteryUptime must be >= batteryScreenOffUptime", bUp >= bOffUp);
        assertTrue("batteryScreenOffUptime must be >= 0", bOffUp >= 0);
        assertTrue("batteryEstimatedCapacity must be >= 0", bEstCap >= 0);
        assertTrue("screenDozeTime must be >= 0", bDoze >= 0);
        assertTrue("screenDozeTime must be <= batteryScreenOffRealtime", bDoze <= bOffReal);
    }

    private void checkBatteryDischarge(String[] parts) {
        assertEquals(12, parts.length);
        assertInteger(parts[4]); // low
        assertInteger(parts[5]); // high
        assertInteger(parts[6]); // screenOn
        assertInteger(parts[7]); // screenOff
        assertInteger(parts[8]); // dischargeMah
        assertInteger(parts[9]); // dischargeScreenOffMah
        assertInteger(parts[10]); // dischargeDozeCount
        assertInteger(parts[11]); // dischargeDozeMah
    }

    private void checkBatteryLevel(String[] parts) {
        assertEquals(6, parts.length);
        assertInteger(parts[4]); // startLevel
        assertInteger(parts[5]); // currentLevel
    }

    private void checkWifi(String[] parts) {
        assertEquals(14, parts.length);
        assertInteger(parts[4]); // fullWifiLockOnTime (usec)
        assertInteger(parts[5]); // wifiScanTime (usec)
        assertInteger(parts[6]); // uidWifiRunningTime (usec)
        assertInteger(parts[7]); // wifiScanCount
        // Fields for parts[8 and 9 and 10] are deprecated.
        assertInteger(parts[11]); // wifiScanCountBg
        assertInteger(parts[12]); // wifiScanActualTimeMs (msec)
        assertInteger(parts[13]); // wifiScanActualTimeMsBg (msec)
    }

    private void checkMisc(String[] parts) {
        assertTrue(parts.length >= 19);
        assertInteger(parts[4]);      // screenOnTime
        assertInteger(parts[5]);      // phoneOnTime
        assertInteger(parts[6]);      // fullWakeLockTimeTotal
        assertInteger(parts[7]);      // partialWakeLockTimeTotal
        assertInteger(parts[8]);      // mobileRadioActiveTime
        assertInteger(parts[9]);      // mobileRadioActiveAdjustedTime
        assertInteger(parts[10]);     // interactiveTime
        assertInteger(parts[11]);     // lowPowerModeEnabledTime
        assertInteger(parts[12]);     // connChanges
        assertInteger(parts[13]);     // deviceIdleModeEnabledTime
        assertInteger(parts[14]);     // deviceIdleModeEnabledCount
        assertInteger(parts[15]);     // deviceIdlingTime
        assertInteger(parts[16]);     // deviceIdlingCount
        assertInteger(parts[17]);     // mobileRadioActiveCount
        assertInteger(parts[18]);     // mobileRadioActiveUnknownTime
    }

    private void checkGlobalNetwork(String[] parts) {
        assertEquals(14, parts.length);
        assertInteger(parts[4]);  // mobileRxTotalBytes
        assertInteger(parts[5]);  // mobileTxTotalBytes
        assertInteger(parts[6]);  // wifiRxTotalBytes
        assertInteger(parts[7]);  // wifiTxTotalBytes
        assertInteger(parts[8]);  // mobileRxTotalPackets
        assertInteger(parts[9]);  // mobileTxTotalPackets
        assertInteger(parts[10]); // wifiRxTotalPackets
        assertInteger(parts[11]); // wifiTxTotalPackets
        assertInteger(parts[12]); // btRxTotalBytes
        assertInteger(parts[13]); // btTxTotalBytes
    }

    private void checkScreenBrightness(String[] parts) {
        assertEquals(9, parts.length);
        assertInteger(parts[4]); // dark
        assertInteger(parts[5]); // dim
        assertInteger(parts[6]); // medium
        assertInteger(parts[7]); // light
        assertInteger(parts[8]); // bright
    }

    private void checkSignalStrength(String[] parts) {
        assertTrue(parts.length >= 9);
        assertInteger(parts[4]); // none
        assertInteger(parts[5]); // poor
        assertInteger(parts[6]); // moderate
        assertInteger(parts[7]); // good
        assertInteger(parts[8]); // great
    }

    private void checkSignalScanningTime(String[] parts) {
        assertEquals(5, parts.length);
        assertInteger(parts[4]); // signalScanningTime
    }

    private void checkDataConnection(String[] parts) {
        assertEquals(21, parts.length);
        assertInteger(parts[4]);  // none
        assertInteger(parts[5]);  // gprs
        assertInteger(parts[6]);  // edge
        assertInteger(parts[7]);  // umts
        assertInteger(parts[8]);  // cdma
        assertInteger(parts[9]);  // evdo_0
        assertInteger(parts[10]); // evdo_A
        assertInteger(parts[11]); // 1xrtt
        assertInteger(parts[12]); // hsdpa
        assertInteger(parts[13]); // hsupa
        assertInteger(parts[14]); // hspa
        assertInteger(parts[15]); // iden
        assertInteger(parts[16]); // evdo_b
        assertInteger(parts[17]); // lte
        assertInteger(parts[18]); // ehrpd
        assertInteger(parts[19]); // hspap
        assertInteger(parts[20]); // other
    }

    private void checkWifiState(String[] parts) {
        assertEquals(12, parts.length);
        assertInteger(parts[4]);  // off
        assertInteger(parts[5]);  // scanning
        assertInteger(parts[6]);  // no_net
        assertInteger(parts[7]);  // disconn
        assertInteger(parts[8]);  // sta
        assertInteger(parts[9]);  // p2p
        assertInteger(parts[10]); // sta_p2p
        assertInteger(parts[11]); // soft_ap
    }

    private void checkWifiSupplState(String[] parts) {
        assertEquals(17, parts.length);
        assertInteger(parts[4]);  // inv
        assertInteger(parts[5]);  // dsc
        assertInteger(parts[6]);  // dis
        assertInteger(parts[7]);  // inact
        assertInteger(parts[8]);  // scan
        assertInteger(parts[9]);  // auth
        assertInteger(parts[10]); // ascing
        assertInteger(parts[11]); // asced
        assertInteger(parts[12]); // 4-way
        assertInteger(parts[13]); // group
        assertInteger(parts[14]); // compl
        assertInteger(parts[15]); // dorm
        assertInteger(parts[16]); // uninit
    }

    private void checkWifiSignalStrength(String[] parts) {
        assertEquals(9, parts.length);
        assertInteger(parts[4]); // none
        assertInteger(parts[5]); // poor
        assertInteger(parts[6]); // moderate
        assertInteger(parts[7]); // good
        assertInteger(parts[8]); // great
    }

    private void checkBluetoothState(String[] parts) {
        assertEquals(8, parts.length);
        assertInteger(parts[4]); // inactive
        assertInteger(parts[5]); // low
        assertInteger(parts[6]); // med
        assertInteger(parts[7]); // high
    }

    private void checkPowerUseSummary(String[] parts) {
        assertEquals(8, parts.length);
        assertDouble(parts[4]); // batteryCapacity
        assertDouble(parts[5]); // computedPower
        assertDouble(parts[6]); // minDrainedPower
        assertDouble(parts[7]); // maxDrainedPower
    }

    private void checkPowerUseItem(String[] parts) {
        assertEquals(9, parts.length);
        assertNotNull(parts[4]); // label
        final double totalPowerMah = assertDouble(parts[5]);  // totalPowerMah
        final long shouldHide = assertInteger(parts[6]);  // shouldHide (0 or 1)
        final double screenPowerMah = assertDouble(parts[7]);  // screenPowerMah
        final double proportionalSmearMah = assertDouble(parts[8]);  // proportionalSmearMah

        assertTrue("powerUseItem totalPowerMah must be >= 0", totalPowerMah >= 0);
        assertTrue("powerUseItem screenPowerMah must be >= 0", screenPowerMah >= 0);
        assertTrue("powerUseItem proportionalSmearMah must be >= 0", proportionalSmearMah >= 0);
        assertTrue("powerUseItem shouldHide must be 0 or 1", shouldHide == 0 || shouldHide == 1);

        // Largest current Android battery is ~5K. 100K shouldn't get made for a while.
        assertTrue("powerUseItem totalPowerMah is expected to be <= 100000", totalPowerMah <= 100000);
    }

    private void checkChargeDischargeStep(String[] parts) {
        assertEquals(9, parts.length);
        assertInteger(parts[4]); // duration
        if (!parts[5].equals("?")) {
            assertInteger(parts[5]); // level
        }
        assertNotNull(parts[6]); // screen
        assertNotNull(parts[7]); // power-save
        assertNotNull(parts[8]); // device-idle
    }

    private void checkDischargeTimeRemain(String[] parts) {
        assertEquals(5, parts.length);
        assertInteger(parts[4]); // batteryTimeRemaining
    }

    private void checkChargeTimeRemain(String[] parts) {
        assertEquals(5, parts.length);
        assertInteger(parts[4]); // chargeTimeRemaining
    }

    private void checkUidCpuUsage(String[] parts) {
        assertTrue(parts.length >= 6);
        assertInteger(parts[4]); // user time
        assertInteger(parts[5]); // system time
    }

    private void checkBluetoothMisc(String[] parts) {
        assertEquals(15, parts.length);
        assertInteger(parts[4]); // totalTime
        assertInteger(parts[5]); // count
        assertInteger(parts[6]); // countBg
        assertInteger(parts[7]); // actualTime
        assertInteger(parts[8]); // actualTimeBg
        assertInteger(parts[9]); // resultsCount
        assertInteger(parts[10]); // resultsCountBg
        assertInteger(parts[11]); // unoptimizedScanTotalTime
        assertInteger(parts[12]); // unoptimizedScanTotalTimeBg
        assertInteger(parts[13]); // unoptimizedScanMaxTime
        assertInteger(parts[14]); // unoptimizedScanMaxTimeBg
    }

    /**
     * Tests the output of "dumpsys gfxinfo framestats".
     *
     * @throws Exception
     */
    public void testGfxinfoFramestats() throws Exception {
        final String MARKER = "---PROFILEDATA---";

        try {
            // cleanup test apps that might be installed from previous partial test run
            getDevice().uninstallPackage(TEST_PKG);

            // install the test app
            CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
            File testAppFile = buildHelper.getTestFile(TEST_APK);
            String installResult = getDevice().installPackage(testAppFile, false);
            assertNull(
                    String.format("failed to install atrace test app. Reason: %s", installResult),
                    installResult);

            getDevice().executeShellCommand("am start -W " + TEST_PKG);

            String frameinfo = mDevice.executeShellCommand("dumpsys gfxinfo " +
                    TEST_PKG + " framestats");
            assertNotNull(frameinfo);
            assertTrue(frameinfo.length() > 0);
            int profileStart = frameinfo.indexOf(MARKER);
            int profileEnd = frameinfo.indexOf(MARKER, profileStart + 1);
            assertTrue(profileStart >= 0);
            assertTrue(profileEnd > profileStart);
            String profileData = frameinfo.substring(profileStart + MARKER.length(), profileEnd);
            assertTrue(profileData.length() > 0);
            validateProfileData(profileData);
        } finally {
            getDevice().uninstallPackage(TEST_PKG);
        }
    }

    private void validateProfileData(String profileData) throws IOException {
        final int TIMESTAMP_COUNT = 14;
        boolean foundAtLeastOneRow = false;
        try (BufferedReader reader = new BufferedReader(
                new StringReader(profileData))) {
            String line;
            // First line needs to be the headers
            while ((line = reader.readLine()) != null && line.isEmpty()) {}

            assertNotNull(line);
            assertTrue("First line was not the expected header",
                    line.startsWith("Flags,IntendedVsync,Vsync,OldestInputEvent" +
                            ",NewestInputEvent,HandleInputStart,AnimationStart" +
                            ",PerformTraversalsStart,DrawStart,SyncQueued,SyncStart" +
                            ",IssueDrawCommandsStart,SwapBuffers,FrameCompleted"));

            long[] numparts = new long[TIMESTAMP_COUNT];
            while ((line = reader.readLine()) != null && !line.isEmpty()) {

                String[] parts = line.split(",");
                assertTrue(parts.length >= TIMESTAMP_COUNT);
                for (int i = 0; i < TIMESTAMP_COUNT; i++) {
                    numparts[i] = assertInteger(parts[i]);
                }
                if (numparts[0] != 0) {
                    continue;
                }
                // assert VSYNC >= INTENDED_VSYNC
                assertTrue(numparts[2] >= numparts[1]);
                // assert time is flowing forwards, skipping index 3 & 4
                // as those are input timestamps that may or may not be present
                assertTrue(numparts[5] >= numparts[2]);
                for (int i = 6; i < TIMESTAMP_COUNT; i++) {
                    assertTrue("Index " + i + " did not flow forward, " +
                            numparts[i] + " not larger than " + numparts[i - 1],
                            numparts[i] >= numparts[i-1]);
                }
                long totalDuration = numparts[13] - numparts[1];
                assertTrue("Frame did not take a positive amount of time to process",
                        totalDuration > 0);
                assertTrue("Bogus frame duration, exceeds 100 seconds",
                        totalDuration < 100000000000L);
                foundAtLeastOneRow = true;
            }
        }
        assertTrue(foundAtLeastOneRow);
    }
}
