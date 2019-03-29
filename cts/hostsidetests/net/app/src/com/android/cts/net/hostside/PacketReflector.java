/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.net.hostside;

import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;

public class PacketReflector extends Thread {

    private static int IPV4_HEADER_LENGTH = 20;
    private static int IPV6_HEADER_LENGTH = 40;

    private static int IPV4_ADDR_OFFSET = 12;
    private static int IPV6_ADDR_OFFSET = 8;
    private static int IPV4_ADDR_LENGTH = 4;
    private static int IPV6_ADDR_LENGTH = 16;

    private static int IPV4_PROTO_OFFSET = 9;
    private static int IPV6_PROTO_OFFSET = 6;

    private static final byte IPPROTO_ICMP = 1;
    private static final byte IPPROTO_TCP = 6;
    private static final byte IPPROTO_UDP = 17;
    private static final byte IPPROTO_ICMPV6 = 58;

    private static int ICMP_HEADER_LENGTH = 8;
    private static int TCP_HEADER_LENGTH = 20;
    private static int UDP_HEADER_LENGTH = 8;

    private static final byte ICMP_ECHO = 8;
    private static final byte ICMP_ECHOREPLY = 0;
    private static final byte ICMPV6_ECHO_REQUEST = (byte) 128;
    private static final byte ICMPV6_ECHO_REPLY = (byte) 129;

    private static String TAG = "PacketReflector";

    private FileDescriptor mFd;
    private byte[] mBuf;

    public PacketReflector(FileDescriptor fd, int mtu) {
        super("PacketReflector");
        mFd = fd;
        mBuf = new byte[mtu];
    }

    private static void swapBytes(byte[] buf, int pos1, int pos2, int len) {
        for (int i = 0; i < len; i++) {
            byte b = buf[pos1 + i];
            buf[pos1 + i] = buf[pos2 + i];
            buf[pos2 + i] = b;
        }
    }

    private static void swapAddresses(byte[] buf, int version) {
        int addrPos, addrLen;
        switch(version) {
            case 4:
                addrPos = IPV4_ADDR_OFFSET;
                addrLen = IPV4_ADDR_LENGTH;
                break;
            case 6:
                addrPos = IPV6_ADDR_OFFSET;
                addrLen = IPV6_ADDR_LENGTH;
                break;
            default:
                throw new IllegalArgumentException();
        }
        swapBytes(buf, addrPos, addrPos + addrLen, addrLen);
    }

    // Reflect TCP packets: swap the source and destination addresses, but don't change the ports.
    // This is used by the test to "connect to itself" through the VPN.
    private void processTcpPacket(byte[] buf, int version, int len, int hdrLen) {
        if (len < hdrLen + TCP_HEADER_LENGTH) {
            return;
        }

        // Swap src and dst IP addresses.
        swapAddresses(buf, version);

        // Send the packet back.
        writePacket(buf, len);
    }

    // Echo UDP packets: swap source and destination addresses, and source and destination ports.
    // This is used by the test to check that the bytes it sends are echoed back.
    private void processUdpPacket(byte[] buf, int version, int len, int hdrLen) {
        if (len < hdrLen + UDP_HEADER_LENGTH) {
            return;
        }

        // Swap src and dst IP addresses.
        swapAddresses(buf, version);

        // Swap dst and src ports.
        int portOffset = hdrLen;
        swapBytes(buf, portOffset, portOffset + 2, 2);

        // Send the packet back.
        writePacket(buf, len);
    }

    private void processIcmpPacket(byte[] buf, int version, int len, int hdrLen) {
        if (len < hdrLen + ICMP_HEADER_LENGTH) {
            return;
        }

        byte type = buf[hdrLen];
        if (!(version == 4 && type == ICMP_ECHO) &&
            !(version == 6 && type == ICMPV6_ECHO_REQUEST)) {
            return;
        }

        // Save the ping packet we received.
        byte[] request = buf.clone();

        // Swap src and dst IP addresses, and send the packet back.
        // This effectively pings the device to see if it replies.
        swapAddresses(buf, version);
        writePacket(buf, len);

        // The device should have replied, and buf should now contain a ping response.
        int received = readPacket(buf);
        if (received != len) {
            Log.i(TAG, "Reflecting ping did not result in ping response: " +
                       "read=" + received + " expected=" + len);
            return;
        }

        // Compare the response we got with the original packet.
        // The only thing that should have changed are addresses, type and checksum.
        // Overwrite them with the received bytes and see if the packet is otherwise identical.
        request[hdrLen] = buf[hdrLen];          // Type.
        request[hdrLen + 2] = buf[hdrLen + 2];  // Checksum byte 1.
        request[hdrLen + 3] = buf[hdrLen + 3];  // Checksum byte 2.

        // Since Linux kernel 4.2, net.ipv6.auto_flowlabels is set by default, and therefore
        // the request and reply may have different IPv6 flow label: ignore that as well.
        if (version == 6) {
            request[1] = (byte)(request[1] & 0xf0 | buf[1] & 0x0f);
            request[2] = buf[2];
            request[3] = buf[3];
        }

        for (int i = 0; i < len; i++) {
            if (buf[i] != request[i]) {
                Log.i(TAG, "Received non-matching packet when expecting ping response.");
                return;
            }
        }

        // Now swap the addresses again and reflect the packet. This sends a ping reply.
        swapAddresses(buf, version);
        writePacket(buf, len);
    }

    private void writePacket(byte[] buf, int len) {
        try {
            Os.write(mFd, buf, 0, len);
        } catch (ErrnoException|IOException e) {
            Log.e(TAG, "Error writing packet: " + e.getMessage());
        }
    }

    private int readPacket(byte[] buf) {
        int len;
        try {
            len = Os.read(mFd, buf, 0, buf.length);
        } catch (ErrnoException|IOException e) {
            Log.e(TAG, "Error reading packet: " + e.getMessage());
            len = -1;
        }
        return len;
    }

    // Reads one packet from our mFd, and possibly writes the packet back.
    private void processPacket() {
        int len = readPacket(mBuf);
        if (len < 1) {
            return;
        }

        int version = mBuf[0] >> 4;
        int addrPos, protoPos, hdrLen, addrLen;
        if (version == 4) {
            hdrLen = IPV4_HEADER_LENGTH;
            protoPos = IPV4_PROTO_OFFSET;
            addrPos = IPV4_ADDR_OFFSET;
            addrLen = IPV4_ADDR_LENGTH;
        } else if (version == 6) {
            hdrLen = IPV6_HEADER_LENGTH;
            protoPos = IPV6_PROTO_OFFSET;
            addrPos = IPV6_ADDR_OFFSET;
            addrLen = IPV6_ADDR_LENGTH;
        } else {
            return;
        }

        if (len < hdrLen) {
            return;
        }

        byte proto = mBuf[protoPos];
        switch (proto) {
            case IPPROTO_ICMP:
            case IPPROTO_ICMPV6:
                processIcmpPacket(mBuf, version, len, hdrLen);
                break;
            case IPPROTO_TCP:
                processTcpPacket(mBuf, version, len, hdrLen);
                break;
            case IPPROTO_UDP:
                processUdpPacket(mBuf, version, len, hdrLen);
                break;
        }
    }

    public void run() {
        Log.i(TAG, "PacketReflector starting fd=" + mFd + " valid=" + mFd.valid());
        while (!interrupted() && mFd.valid()) {
            processPacket();
        }
        Log.i(TAG, "PacketReflector exiting fd=" + mFd + " valid=" + mFd.valid());
    }
}
