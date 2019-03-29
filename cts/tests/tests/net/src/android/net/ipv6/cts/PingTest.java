/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.net.ipv6.cts;

import android.test.AndroidTestCase;
import android.util.Log;

import android.system.ErrnoException;
import android.system.Os;
import android.system.StructTimeval;
import static android.system.OsConstants.*;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

/**
 * Checks that the device has kernel support for the IPv6 ping socket. This allows ping6 to work
 * without root privileges. The necessary kernel code is in Linux 3.11 or above, or the
 * <code>common/android-3.x</code> kernel trees. If you are not running one of these kernels, the
 * functionality can be obtained by cherry-picking the following patches from David Miller's
 * <code>net-next</code> tree:
 * <ul>
 * <li>6d0bfe2 net: ipv6: Add IPv6 support to the ping socket.
 * <li>c26d6b4 ping: always initialize ->sin6_scope_id and ->sin6_flowinfo
 * <li>fbfe80c net: ipv6: fix wrong ping_v6_sendmsg return value
 * <li>a1bdc45 net: ipv6: add missing lock in ping_v6_sendmsg
 * <li>cf970c0 ping: prevent NULL pointer dereference on write to msg_name
 * </ul>
 * or the equivalent backports to the <code>common/android-3.x</code> trees.
 */
public class PingTest extends AndroidTestCase {
    /** Maximum size of the packets we're using to test. */
    private static final int MAX_SIZE = 4096;

    /** Size of the ICMPv6 header. */
    private static final int ICMP_HEADER_SIZE = 8;

    /** Number of packets to test. */
    private static final int NUM_PACKETS = 10;

    /** The beginning of an ICMPv6 echo request: type, code, and uninitialized checksum. */
    private static final byte[] PING_HEADER = new byte[] {
        (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00
    };

    /**
     * Returns a byte array containing an ICMPv6 echo request with the specified payload length.
     */
    private byte[] pingPacket(int payloadLength) {
        byte[] packet = new byte[payloadLength + ICMP_HEADER_SIZE];
        new Random().nextBytes(packet);
        System.arraycopy(PING_HEADER, 0, packet, 0, PING_HEADER.length);
        return packet;
    }

    /**
     * Checks that the first length bytes of two byte arrays are equal.
     */
    private void assertArrayBytesEqual(byte[] expected, byte[] actual, int length) {
        for (int i = 0; i < length; i++) {
            assertEquals("Arrays differ at index " + i + ":", expected[i], actual[i]);
        }
    }

    /**
     * Creates an IPv6 ping socket and sets a receive timeout of 100ms.
     */
    private FileDescriptor createPingSocket() throws ErrnoException {
        FileDescriptor s = Os.socket(AF_INET6, SOCK_DGRAM, IPPROTO_ICMPV6);
        Os.setsockoptTimeval(s, SOL_SOCKET, SO_RCVTIMEO, StructTimeval.fromMillis(100));
        return s;
    }

    /**
     * Sends a ping packet to a random port on the specified address on the specified socket.
     */
    private void sendPing(FileDescriptor s,
            InetAddress address, byte[] packet) throws ErrnoException, IOException {
        // Pick a random port. Choose a range that gives a reasonable chance of picking a low port.
        int port = (int) (Math.random() * 2048);

        // Send the packet.
        int ret = Os.sendto(s, ByteBuffer.wrap(packet), 0, address, port);
        assertEquals(packet.length, ret);
    }

    /**
     * Checks that a socket has received a response appropriate to the specified packet.
     */
    private void checkResponse(FileDescriptor s, InetAddress dest,
            byte[] sent, boolean useRecvfrom) throws ErrnoException, IOException {
        ByteBuffer responseBuffer = ByteBuffer.allocate(MAX_SIZE);
        int bytesRead;

        // Receive the response.
        if (useRecvfrom) {
            InetSocketAddress from = new InetSocketAddress();
            bytesRead = Os.recvfrom(s, responseBuffer, 0, from);

            // Check the source address and scope ID.
            assertTrue(from.getAddress() instanceof Inet6Address);
            Inet6Address fromAddress = (Inet6Address) from.getAddress();
            assertEquals(0, fromAddress.getScopeId());
            assertNull(fromAddress.getScopedInterface());
            assertEquals(dest.getHostAddress(), fromAddress.getHostAddress());
        } else {
            bytesRead = Os.read(s, responseBuffer);
        }

        // Check the packet length.
        assertEquals(sent.length, bytesRead);

        // Check the response is an echo reply.
        byte[] response = new byte[bytesRead];
        responseBuffer.flip();
        responseBuffer.get(response, 0, bytesRead);
        assertEquals((byte) 0x81, response[0]);

        // Find out what ICMP ID was used in the packet that was sent.
        int id = ((InetSocketAddress) Os.getsockname(s)).getPort();
        sent[4] = (byte) (id / 256);
        sent[5] = (byte) (id % 256);

        // Ensure the response is the same as the packet, except for the type (which is 0x81)
        // and the ID and checksum,  which are set by the kernel.
        response[0] = (byte) 0x80;                 // Type.
        response[2] = response[3] = (byte) 0x00;   // Checksum.
        assertArrayBytesEqual(response, sent, bytesRead);
    }

    /**
     * Sends NUM_PACKETS random ping packets to ::1 and checks the replies.
     */
    public void testLoopbackPing() throws ErrnoException, IOException {
        // Generate a random ping packet and send it to localhost.
        InetAddress ipv6Loopback = InetAddress.getByName(null);
        assertEquals("::1", ipv6Loopback.getHostAddress());

        for (int i = 0; i < NUM_PACKETS; i++) {
            byte[] packet = pingPacket((int) (Math.random() * (MAX_SIZE - ICMP_HEADER_SIZE)));
            FileDescriptor s = createPingSocket();
            // Use both recvfrom and read().
            sendPing(s, ipv6Loopback, packet);
            checkResponse(s, ipv6Loopback, packet, true);
            sendPing(s, ipv6Loopback, packet);
            checkResponse(s, ipv6Loopback, packet, false);
            // Check closing the socket doesn't raise an exception.
            Os.close(s);
        }
    }
}
