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

package com.android.cts.deviceandprofileowner.vpn;

import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.IPPROTO_ICMP;
import static android.system.OsConstants.POLLIN;
import static android.system.OsConstants.SOCK_DGRAM;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import android.annotation.TargetApi;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build.VERSION_CODES;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructPollfd;

import com.android.cts.deviceandprofileowner.BaseDeviceAdminTest;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Helper class to test vpn status
 */
@TargetApi(VERSION_CODES.N)
public class VpnTestHelper {
    public static final String VPN_PACKAGE = "com.android.cts.vpnfirewall";

    // IP address reserved for documentation by rfc5737
    public static final String TEST_ADDRESS = "192.0.2.4";

    // HACK (TODO issue 31585407) to wait for the network to actually be usable
    private static final int NETWORK_SETTLE_GRACE_MS = 200;

    private static final int SOCKET_TIMEOUT_MS = 5000;
    private static final int ICMP_ECHO_REQUEST = 0x08;
    private static final int ICMP_ECHO_REPLY = 0x00;
    private static final int NETWORK_TIMEOUT_MS = 5000;
    private static final ComponentName ADMIN_RECEIVER_COMPONENT =
            BaseDeviceAdminTest.ADMIN_RECEIVER_COMPONENT;
    private static final NetworkRequest VPN_NETWORK_REQUEST = new NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build();

    /**
     * Wait for a VPN app to establish VPN.
     *
     * @param context Caller's context.
     * @param packageName {@code null} if waiting for the existing VPN to connect. Otherwise we set
     *         this package as the new always-on VPN app and wait for it to connect.
     * @param usable Whether the resulting VPN tunnel is expected to be usable.
     */
    public static void waitForVpn(Context context, String packageName, boolean usable) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        if (packageName == null) {
            assertNotNull(dpm.getAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT));
        }

        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        final CountDownLatch vpnLatch = new CountDownLatch(1);
        final ConnectivityManager.NetworkCallback callback =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network net) {
                        vpnLatch.countDown();
                    }
                };
        cm.registerNetworkCallback(VPN_NETWORK_REQUEST, callback);

        try {
            if (packageName != null) {
                dpm.setAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT, packageName, true);
                assertEquals(packageName, dpm.getAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT));
            }
            if (!vpnLatch.await(NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                if (!isNetworkVpn(context)) {
                    fail("Took too long waiting to establish a VPN-backed connection");
                }
            }
            Thread.sleep(NETWORK_SETTLE_GRACE_MS);
        } catch (InterruptedException | PackageManager.NameNotFoundException e) {
            fail("Failed while waiting for VPN: " + e);
        } finally {
            cm.unregisterNetworkCallback(callback);
        }

        // Do we have a network?
        NetworkInfo vpnInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_VPN);
        assertNotNull(vpnInfo);

        // Is it usable?
        assertEquals(usable, vpnInfo.isConnected());
    }

    public static boolean isNetworkVpn(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = connectivityManager.getActiveNetwork();
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }

    public static void checkPing(String host) throws ErrnoException, IOException {
        FileDescriptor socket = Os.socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP);

        // Create an ICMP message
        final int identifier = 0x7E57;
        final String message = "test packet";
        byte[] echo = createIcmpMessage(ICMP_ECHO_REQUEST, 0x00, identifier, 0, message.getBytes());

        // Send the echo packet.
        int port = new InetSocketAddress(0).getPort();
        Os.connect(socket, InetAddress.getByName(host), port);
        Os.write(socket, echo, 0, echo.length);

        // Expect a reply.
        StructPollfd pollfd = new StructPollfd();
        pollfd.events = (short) POLLIN;
        pollfd.fd = socket;
        int ret = Os.poll(new StructPollfd[] { pollfd }, SOCKET_TIMEOUT_MS);
        assertEquals("Expected reply after sending ping", 1, ret);

        byte[] reply = new byte[echo.length];
        int read = Os.read(socket, reply, 0, echo.length);
        assertEquals(echo.length, read);

        // Ignore control type differences since echo=8, reply=0.
        assertEquals(echo[0], ICMP_ECHO_REQUEST);
        assertEquals(reply[0], ICMP_ECHO_REPLY);
        echo[0] = 0;
        reply[0] = 0;

        // Fix ICMP ID which kernel will have changed on the way out.
        InetSocketAddress local = (InetSocketAddress) Os.getsockname(socket);
        port = local.getPort();
        echo[4] = (byte) ((port >> 8) & 0xFF);
        echo[5] = (byte) (port & 0xFF);

        // Ignore checksum differences since the types are not supposed to match.
        echo[2] = echo[3] = 0;
        reply[2] = reply[3] = 0;

        assertTrue("Packet contents do not match."
                + "\nEcho packet:  " + Arrays.toString(echo)
                + "\nReply packet: " + Arrays.toString(reply), Arrays.equals(echo, reply));

        // Close socket if the test pass. Otherwise, any error will kill the process.
        Os.close(socket);
    }

    public static void tryPosixConnect(String host) throws ErrnoException, IOException {
        FileDescriptor socket = null;
        try {
            socket = Os.socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP);
            int port = new InetSocketAddress(0).getPort();
            Os.connect(socket, InetAddress.getByName(host), port);
        } finally {
            if (socket != null) {
                Os.close(socket);
            }
        }
    }

    private static byte[] createIcmpMessage(int type, int code, int extra1, int extra2,
            byte[] data) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream stream = new DataOutputStream(output);
        stream.writeByte(type);
        stream.writeByte(code);
        stream.writeShort(/* checksum */ 0);
        stream.writeShort((short) extra1);
        stream.writeShort((short) extra2);
        stream.write(data, 0, data.length);
        return output.toByteArray();
    }
}
