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

package com.android.cts.vpnfirewall;

import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Inet4Address;

public class PingReflector extends Thread {
    private static final String TAG = "PingReflector";

    private static final int PROTOCOL_ICMP = 0x01;

    private FileDescriptor mFd;
    private byte[] mBuf;

    public PingReflector(FileDescriptor fd, int mtu) {
        super("PingReflector");
        mFd = fd;
        mBuf = new byte[mtu];
    }

    public void run() {
        Log.i(TAG, "PingReflector starting fd=" + mFd + " valid=" + mFd.valid());
        while (!interrupted() && mFd.valid()) {
            int len = readPacket(mBuf);
            if (len > 0) {
                int version = mBuf[0] >> 4;
                if (version != 4) {
                    Log.e(TAG, "Received packet version: " + version + ". Ignoring.");
                    continue;
                }
                try {
                    processPacket(mBuf, version, len, 0);
                } catch (IOException e) {
                    Log.w(TAG, "Failed processing packet", e);
                }
            }
        }
        Log.i(TAG, "PingReflector exiting fd=" + mFd + " valid=" + mFd.valid());
    }

    private void processPacket(byte[] buf, int version, int len, int hdrLen) throws IOException {
        IcmpMessage echo = null;
        IcmpMessage response = null;

        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(buf));
        Ipv4Packet packet = new Ipv4Packet(stream);
        Log.i(TAG, "Packet contents:\n" + packet);

        if (packet.protocol != PROTOCOL_ICMP) {
            Log.i(TAG, "Protocol is " + packet.protocol + " not ICMP. Ignoring.");
            return;
        }

        echo = new IcmpMessage(
                new DataInputStream(new ByteArrayInputStream(packet.data)), packet.data.length);
        Log.i(TAG, "Ping packet:\n" + echo);

        // Swap src and dst IP addresses to route the packet back into the device.
        Inet4Address tmp = packet.sourceAddress;
        packet.sourceAddress = packet.destinationAddress;
        packet.destinationAddress = tmp;

        packet.setData(echo.getEncoded());
        writePacket(packet.getEncoded());
        Log.i(TAG, "Wrote packet back");
    }

    private void writePacket(byte[] buf) {
        try {
            Os.write(mFd, buf, 0, buf.length);
        } catch (ErrnoException | IOException e) {
            Log.e(TAG, "Error writing packet: " + e.getMessage(), e);
        }
    }

    private int readPacket(byte[] buf) {
        try {
            return Os.read(mFd, buf, 0, buf.length);
        } catch (ErrnoException | IOException e) {
            Log.e(TAG, "Error reading packet: " + e.getMessage(), e);
            return -1;
        }
    }
}
