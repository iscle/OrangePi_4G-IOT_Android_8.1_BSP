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
package com.android.server.cts;

import android.service.NetworkIdentityProto;
import android.service.NetworkInterfaceProto;
import android.service.NetworkStatsCollectionKeyProto;
import android.service.NetworkStatsCollectionStatsProto;
import android.service.NetworkStatsHistoryBucketProto;
import android.service.NetworkStatsHistoryProto;
import android.service.NetworkStatsRecorderProto;
import android.service.NetworkStatsServiceDumpProto;

import com.android.tradefed.log.LogUtil.CLog;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Test for "dumpsys netstats --proto"
 *
 * Note most of the logic here is just heuristics.
 *
 * Usage:

  cts-tradefed run cts --skip-device-info --skip-preconditions \
      --skip-system-status-check \
       com.android.compatibility.common.tradefed.targetprep.NetworkConnectivityChecker \
       -a armeabi-v7a -m CtsIncidentHostTestCases -t com.android.server.cts.NetstatsIncidentTest

 */
public class NetstatsIncidentTest extends ProtoDumpTestCase {
    private static final String DEVICE_SIDE_TEST_APK = "CtsNetStatsApp.apk";
    private static final String DEVICE_SIDE_TEST_PACKAGE = "com.android.server.cts.netstats";
    private static final String FEATURE_WIFI = "android.hardware.wifi";

    @Override
    protected void tearDown() throws Exception {
        getDevice().uninstallPackage(DEVICE_SIDE_TEST_PACKAGE);

        super.tearDown();
    }


    private void assertPositive(String name, long value) {
        if (value > 0) return;
        fail(name + " expected to be positive, but was: " + value);
    }

    private void assertNotNegative(String name, long value) {
        if (value >= 0) return;
        fail(name + " expected to be zero or positive, but was: " + value);
    }

    private void assertGreaterOrEqual(long greater, long lesser) {
        assertTrue("" + greater + " expected to be greater than or equal to " + lesser,
                greater >= lesser);
    }

    /**
     * Parse the output of "dumpsys netstats --proto" and make sure all the values are probable.
     */
    public void testSanityCheck() throws Exception {

        final long st = System.currentTimeMillis();

        installPackage(DEVICE_SIDE_TEST_APK, /* grantPermissions= */ true);

        // Find the package UID.
        final int uid = Integer.parseInt(execCommandAndGetFirstGroup(
                "dumpsys package " + DEVICE_SIDE_TEST_PACKAGE, "userId=(\\d+)"));

        CLog.i("Start time: " + st);
        CLog.i("App UID: " + uid);

        // Run the device side test which makes some network requests.
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, null, null);

        // Make some more activity.
        getDevice().executeShellCommand("ping -s 100 -c 10 -i 0  www.android.com");

        // Force refresh the output.
        getDevice().executeShellCommand("dumpsys netstats --poll");

        NetworkStatsServiceDumpProto dump = getDump(NetworkStatsServiceDumpProto.parser(),
                "dumpsys netstats --proto");

        CLog.d("First dump:\n" + dump.toString());

        // Basic sanity check.
        checkInterfaces(dump.getActiveInterfacesList());
        checkInterfaces(dump.getActiveUidInterfacesList());

        checkStats(dump.getDevStats(), /*withUid=*/ false, /*withTag=*/ false);
        checkStats(dump.getXtStats(), /*withUid=*/ false, /*withTag=*/ false);
        checkStats(dump.getUidStats(), /*withUid=*/ true, /*withTag=*/ false);
        checkStats(dump.getUidTagStats(), /*withUid=*/ true, /*withTag=*/ true);

        // Remember the original values.
        final Predicate<NetworkStatsCollectionKeyProto> uidFilt = key -> key.getUid() == uid;
        final Predicate<NetworkStatsCollectionKeyProto> tagFilt =
                key -> (key.getTag() == 123123123) && (key.getUid() == uid);

        final long devRxPackets = sum(dump.getDevStats(), st, b -> b.getRxPackets());
        final long devRxBytes = sum(dump.getDevStats(), st, b -> b.getRxBytes());
        final long devTxPackets = sum(dump.getDevStats(), st, b -> b.getTxPackets());
        final long devTxBytes = sum(dump.getDevStats(), st, b -> b.getTxBytes());

        final long xtRxPackets = sum(dump.getXtStats(), st, b -> b.getRxPackets());
        final long xtRxBytes = sum(dump.getXtStats(), st, b -> b.getRxBytes());
        final long xtTxPackets = sum(dump.getXtStats(), st, b -> b.getTxPackets());
        final long xtTxBytes = sum(dump.getXtStats(), st, b -> b.getTxBytes());

        final long uidRxPackets = sum(dump.getUidStats(), st, uidFilt, b -> b.getRxPackets());
        final long uidRxBytes = sum(dump.getUidStats(), st, uidFilt, b -> b.getRxBytes());
        final long uidTxPackets = sum(dump.getUidStats(), st, uidFilt, b -> b.getTxPackets());
        final long uidTxBytes = sum(dump.getUidStats(), st, uidFilt, b -> b.getTxBytes());

        final long tagRxPackets = sum(dump.getUidTagStats(), st, tagFilt, b -> b.getRxPackets());
        final long tagRxBytes = sum(dump.getUidTagStats(), st, tagFilt, b -> b.getRxBytes());
        final long tagTxPackets = sum(dump.getUidTagStats(), st, tagFilt, b -> b.getTxPackets());
        final long tagTxBytes = sum(dump.getUidTagStats(), st, tagFilt, b -> b.getTxBytes());

        // Run again to make some more activity.
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE,
                "com.android.server.cts.netstats.NetstatsDeviceTest",
                "testDoNetworkWithoutTagging");

        getDevice().executeShellCommand("dumpsys netstats --poll");
        dump = getDump(NetworkStatsServiceDumpProto.parser(), "dumpsys netstats --proto");

        CLog.d("Second dump:\n" + dump.toString());

        final long devRxPackets2 = sum(dump.getDevStats(), st, b -> b.getRxPackets());
        final long devRxBytes2 = sum(dump.getDevStats(), st, b -> b.getRxBytes());
        final long devTxPackets2 = sum(dump.getDevStats(), st, b -> b.getTxPackets());
        final long devTxBytes2 = sum(dump.getDevStats(), st, b -> b.getTxBytes());

        final long xtRxPackets2 = sum(dump.getXtStats(), st, b -> b.getRxPackets());
        final long xtRxBytes2 = sum(dump.getXtStats(), st, b -> b.getRxBytes());
        final long xtTxPackets2 = sum(dump.getXtStats(), st, b -> b.getTxPackets());
        final long xtTxBytes2 = sum(dump.getXtStats(), st, b -> b.getTxBytes());

        final long uidRxPackets2 = sum(dump.getUidStats(), st, uidFilt, b -> b.getRxPackets());
        final long uidRxBytes2 = sum(dump.getUidStats(), st, uidFilt, b -> b.getRxBytes());
        final long uidTxPackets2 = sum(dump.getUidStats(), st, uidFilt, b -> b.getTxPackets());
        final long uidTxBytes2 = sum(dump.getUidStats(), st, uidFilt, b -> b.getTxBytes());

        final long tagRxPackets2 = sum(dump.getUidTagStats(), st, tagFilt, b -> b.getRxPackets());
        final long tagRxBytes2 = sum(dump.getUidTagStats(), st, tagFilt, b -> b.getRxBytes());
        final long tagTxPackets2 = sum(dump.getUidTagStats(), st, tagFilt, b -> b.getTxPackets());
        final long tagTxBytes2 = sum(dump.getUidTagStats(), st, tagFilt, b -> b.getTxBytes());

        // At least 1 packet, 100 bytes sent.
        assertGreaterOrEqual(uidTxPackets2, uidTxPackets + 1);
        assertGreaterOrEqual(uidTxBytes2, uidTxBytes + 100);

//        assertGreaterOrEqual(tagTxPackets2, tagTxPackets + 1);
//        assertGreaterOrEqual(tagTxBytes2, tagTxBytes + 100);

        // At least 2 packets, 100 bytes sent.
        assertGreaterOrEqual(uidRxPackets2, uidRxPackets + 2);
        assertGreaterOrEqual(uidRxBytes2, uidRxBytes + 100);

//        assertGreaterOrEqual(tagRxPackets2, tagRxPackets + 2);
//        assertGreaterOrEqual(tagRxBytes2, tagRxBytes + 100);

        // Run again to make some more activity.
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE,
                "com.android.server.cts.netstats.NetstatsDeviceTest",
                "testDoNetworkWithTagging");

        getDevice().executeShellCommand("dumpsys netstats --poll");
        dump = getDump(NetworkStatsServiceDumpProto.parser(), "dumpsys netstats --proto");

        CLog.d("Second dump:\n" + dump.toString());

        final long devRxPackets3 = sum(dump.getDevStats(), st, b -> b.getRxPackets());
        final long devRxBytes3 = sum(dump.getDevStats(), st, b -> b.getRxBytes());
        final long devTxPackets3 = sum(dump.getDevStats(), st, b -> b.getTxPackets());
        final long devTxBytes3 = sum(dump.getDevStats(), st, b -> b.getTxBytes());

        final long xtRxPackets3 = sum(dump.getXtStats(), st, b -> b.getRxPackets());
        final long xtRxBytes3 = sum(dump.getXtStats(), st, b -> b.getRxBytes());
        final long xtTxPackets3 = sum(dump.getXtStats(), st, b -> b.getTxPackets());
        final long xtTxBytes3 = sum(dump.getXtStats(), st, b -> b.getTxBytes());

        final long uidRxPackets3 = sum(dump.getUidStats(), st, uidFilt, b -> b.getRxPackets());
        final long uidRxBytes3 = sum(dump.getUidStats(), st, uidFilt, b -> b.getRxBytes());
        final long uidTxPackets3 = sum(dump.getUidStats(), st, uidFilt, b -> b.getTxPackets());
        final long uidTxBytes3 = sum(dump.getUidStats(), st, uidFilt, b -> b.getTxBytes());

        final long tagRxPackets3 = sum(dump.getUidTagStats(), st, tagFilt, b -> b.getRxPackets());
        final long tagRxBytes3 = sum(dump.getUidTagStats(), st, tagFilt, b -> b.getRxBytes());
        final long tagTxPackets3 = sum(dump.getUidTagStats(), st, tagFilt, b -> b.getTxPackets());
        final long tagTxBytes3 = sum(dump.getUidTagStats(), st, tagFilt, b -> b.getTxBytes());

        // At least 1 packet, 100 bytes sent.
        assertGreaterOrEqual(uidTxPackets3, uidTxPackets2 + 1);
        assertGreaterOrEqual(uidTxBytes3, uidTxBytes2 + 100);

        assertGreaterOrEqual(tagTxPackets3, tagTxPackets2 + 1);
        assertGreaterOrEqual(tagTxBytes3, tagTxBytes2 + 100);

        // At least 2 packets, 100 bytes sent.
        assertGreaterOrEqual(uidRxPackets3, uidRxPackets2 + 2);
        assertGreaterOrEqual(uidRxBytes3, uidRxBytes2 + 100);

        assertGreaterOrEqual(tagRxPackets3, tagRxPackets2 + 2);
        assertGreaterOrEqual(tagRxBytes3, tagRxBytes2 + 100);
    }

    private long sum(NetworkStatsRecorderProto recorder,
            long startTime,
            Function<NetworkStatsHistoryBucketProto, Long> func) {
        return sum(recorder, startTime, key -> true, func);
    }

    private long sum(NetworkStatsRecorderProto recorder,
            long startTime,
            Predicate<NetworkStatsCollectionKeyProto> filter,
            Function<NetworkStatsHistoryBucketProto, Long> func) {

        long total = 0;
        for (NetworkStatsCollectionStatsProto stats
                : recorder.getCompleteHistory().getStatsList()) {
            if (!filter.test(stats.getKey())) {
                continue;
            }
            for (NetworkStatsHistoryBucketProto bucket : stats.getHistory().getBucketsList()) {
                if (startTime < bucket.getBucketStartMs()) {
                    continue;
                }
                total += func.apply(bucket);
            }
        }
        return total;
    }

    private void checkInterfaces(List<NetworkInterfaceProto> interfaces) throws Exception{
        /* Example:
    active_interfaces=[
      NetworkInterfaceProto {
        interface=wlan0
        identities=NetworkIdentitySetProto {
          identities=[
            NetworkIdentityProto {
              type=1
              subscriber_id=
              network_id="wifiap"
              roaming=false
              metered=false
            }
          ]
        }
      }
    ]
         */
        assertTrue("There must be at least one network device",
                interfaces.size() > 0);

        boolean allRoaming = true;
        boolean allMetered = true;

        for (NetworkInterfaceProto iface : interfaces) {
            assertTrue("Missing interface name", !iface.getInterface().isEmpty());

            assertPositive("# identities", iface.getIdentities().getIdentitiesList().size());

            for (NetworkIdentityProto iden : iface.getIdentities().getIdentitiesList()) {
                allRoaming &= iden.getRoaming();
                allMetered &= iden.getMetered();

                // TODO Can we check the other fields too?  type, subscriber_id, and network_id.
            }
        }
        assertFalse("There must be at least one non-roaming interface during CTS", allRoaming);
        if (hasWiFiFeature()) {
            assertFalse("There must be at least one non-metered interface during CTS", allMetered);
        }
    }

    private void checkStats(NetworkStatsRecorderProto recorder, boolean withUid, boolean withTag) {
        /*
         * Example:
    dev_stats=NetworkStatsRecorderProto {
      pending_total_bytes=136
      complete_history=NetworkStatsCollectionProto {
        stats=[
          NetworkStatsCollectionStatsProto {
            key=NetworkStatsCollectionKeyProto {
              identity=NetworkIdentitySetProto {
                identities=[
                  NetworkIdentityProto {
                    type=1
                    subscriber_id=
                    network_id="wifiap"
                    roaming=false
                    metered=false
                  }
                ]
              }
              uid=-1
              set=-1
              tag=0
            }
            history=NetworkStatsHistoryProto {
              bucket_duration_ms=3600000
              buckets=[
                NetworkStatsHistoryBucketProto {
                  bucket_start_ms=2273694336
                  rx_bytes=2142
                  rx_packets=10
                  tx_bytes=1568
                  tx_packets=12
                  operations=0
                }
                NetworkStatsHistoryBucketProto {
                  bucket_start_ms=3196682880
                  rx_bytes=2092039
                  rx_packets=1987
                  tx_bytes=236735
                  tx_packets=1750
                  operations=0
                }
         */

        assertNotNegative("Pending bytes", recorder.getPendingTotalBytes());

        for (NetworkStatsCollectionStatsProto stats : recorder.getCompleteHistory().getStatsList()) {

            final NetworkStatsCollectionKeyProto key = stats.getKey();

            // TODO Check the key.

            final NetworkStatsHistoryProto hist = stats.getHistory();

            assertPositive("duration", hist.getBucketDurationMs());

            // Subtract one hour from duration to compensate for possible DTS.
            final long minInterval = hist.getBucketDurationMs() - (60 * 60 * 1000);

            NetworkStatsHistoryBucketProto prev = null;
            for (NetworkStatsHistoryBucketProto bucket : hist.getBucketsList()) {

                // Make sure the start time is increasing by at least the "duration",
                // except we subtract duration from one our to compensate possible DTS.

                if (prev != null) {
                    assertTrue(
                            String.format("Last start=%d, current start=%d, diff=%d, duration=%d",
                                    prev.getBucketStartMs(), bucket.getBucketStartMs(),
                                    (bucket.getBucketStartMs() - prev.getBucketStartMs()),
                                    minInterval),
                            (bucket.getBucketStartMs() - prev.getBucketStartMs()) >=
                                    minInterval);
                }
                assertNotNegative("RX bytes", bucket.getRxBytes());
                assertNotNegative("RX packets", bucket.getRxPackets());
                assertNotNegative("TX bytes", bucket.getTxBytes());
                assertNotNegative("TX packets", bucket.getTxPackets());

// 10 was still too big?                // It should be safe to say # of bytes >= 10 * 10 of packets, due to headers, etc...
                final long FACTOR = 4;
                assertTrue(
                        String.format("# of bytes %d too small for # of packets %d",
                                bucket.getRxBytes(), bucket.getRxPackets()),
                        bucket.getRxBytes() >= bucket.getRxPackets() * FACTOR);
                assertTrue(
                        String.format("# of bytes %d too small for # of packets %d",
                                bucket.getTxBytes(), bucket.getTxPackets()),
                        bucket.getTxBytes() >= bucket.getTxPackets() * FACTOR);
            }
        }

        // TODO Make sure test app's UID actually shows up.
    }

    private boolean hasWiFiFeature() throws Exception {
        final String commandOutput = getDevice().executeShellCommand("pm list features");
        return commandOutput.contains(FEATURE_WIFI);
    }
}
