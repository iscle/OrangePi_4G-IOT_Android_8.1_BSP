/*
 * Copyright (C) 2017 MediaTek Inc.
 *
 * Modification based on code covered by the mentioned copyright
 * and/or permission notice(s).
 */
/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.net.arp;

import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.PacketSocketAddress;
import android.system.StructTimeval;
import android.util.Log;

import static android.system.OsConstants.AF_PACKET;
import static android.system.OsConstants.ETH_P_ARP;
import static android.system.OsConstants.ETH_P_IP;
import static android.system.OsConstants.SOCK_RAW;
import static android.system.OsConstants.SOL_SOCKET;
import static android.system.OsConstants.SO_RCVTIMEO;

import com.android.internal.util.HexDump;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import libcore.io.IoBridge;


/**
 * This class allows simple ARP exchanges over an uninitialized network
 * interface.
 * rfc3927.
 *
 * @hide
 */
public class ArpPeer {
    private static final boolean DBG = false;
    private static final String TAG = "ArpPeer";
    private String mIfaceName;
    private byte[] mHwAddr;
    private final InetAddress mMyAddr;
    private final byte[] mMyMac = new byte[6];
    private final InetAddress mPeer;
    private FileDescriptor mSocket;
    private NetworkInterface mIface;
    private PacketSocketAddress mInterfaceBroadcastAddr;
    private static final boolean PKT_DBG = false;

    private static final byte[] L2_BROADCAST = new byte[] {
            (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff,
    };
    /// M: refactor from DhcpPacket.java  @{
    private static final int MAX_LENGTH = 1500;
    /// @}
    private static final int ETHERNET_TYPE = 1;
    private static final int ARP_TYPE = 0x0806;
    private static final int ETHERNET_LENGTH = 14;
    private static final int ARP_LENGTH = 28;
    private static final int MAC_ADDR_LENGTH = 6;
    private static final int IPV4_LENGTH = 4;

    /**
     *  Construction function for ArpPeer class.
     * @param interfaceName network interface name.
     * @param myAddr local IP address.
     * @param peer peer IP address.
     * @throws SocketException if socket error is occurred.
     *
     */
    public ArpPeer(String interfaceName, InetAddress myAddr, InetAddress peer)
                        throws SocketException {
        mIfaceName = interfaceName;
        mMyAddr = myAddr;

        if (myAddr instanceof Inet6Address || peer instanceof Inet6Address) {
            throw new IllegalArgumentException("IPv6 unsupported");
        }

        mPeer = peer;

        initInterface();
        initSocket();

        Log.i(TAG, "ArpPeer in " + interfaceName + ":" + myAddr + ":" + peer);
    }

    private boolean initInterface() {
        try {
            mIface = NetworkInterface.getByName(mIfaceName);
            mHwAddr = mIface.getHardwareAddress();
            Log.i(TAG, "mac addr:" + HexDump.dumpHexString(mHwAddr) + ":" + mIface.getIndex());
            mInterfaceBroadcastAddr = new PacketSocketAddress(mIface.getIndex(),
                    L2_BROADCAST);
            mInterfaceBroadcastAddr.sll_protocol = ARP_TYPE;
        } catch (SocketException e) {
            Log.e(TAG, "Can't determine ifindex or MAC address for " + mIfaceName);
            return false;
        }
        return true;
    }

    private boolean initSocket() {
        try {
            mSocket = Os.socket(AF_PACKET, SOCK_RAW, ETH_P_ARP);
            PacketSocketAddress addr = new PacketSocketAddress((short) ETH_P_ARP,
                                                        mIface.getIndex());
            Os.bind(mSocket, addr);
        } catch (SocketException|ErrnoException e) {
            Log.e(TAG, "Error creating packet socket", e);
            return false;
        }
        return true;
    }

    /**
     * Returns the MAC address (or null if timeout) for the peer side.
     * @param timeoutMillis timeout value for arp procedure.
     * @return the MAC address of peer side.
     * @throws ErrnoException if error is occurred.
     */
    public byte[] doArp(int timeoutMillis) throws ErrnoException {
        ByteBuffer buf = ByteBuffer.allocate(MAX_LENGTH);
        byte[] desiredIp = mPeer.getAddress();
        Log.i(TAG, "My MAC:" + HexDump.dumpHexString(mMyAddr.getAddress()));
        long timeout = SystemClock.elapsedRealtime() + timeoutMillis;

        Log.i(TAG, "doArp in " + timeoutMillis);

        // construct ARP request packet, using a ByteBuffer as a
        // convenient container
        buf.clear();
        buf.order(ByteOrder.BIG_ENDIAN);

        //Fill ethernet frame with 14 bytes
        buf.put(L2_BROADCAST);
        buf.put(mHwAddr);
        buf.putShort((short) ARP_TYPE);

        buf.putShort((short) ETHERNET_TYPE); // Ethernet type, 16 bits
        buf.putShort((short) ETH_P_IP); // Protocol type IP, 16 bits
        buf.put((byte) MAC_ADDR_LENGTH);  // MAC address length, 6 bytes
        buf.put((byte) IPV4_LENGTH);  // IPv4 protocol size
        buf.putShort((short) 1); // ARP opcode 1: 'request'
        buf.put(mHwAddr);        // six bytes: sender MAC
        buf.put(mMyAddr.getAddress());  // four bytes: sender IP address
        buf.put(new byte[MAC_ADDR_LENGTH]); // target MAC address: unknown
        buf.put(desiredIp); // target IP address, 4 bytes
        buf.flip();

        try {
            Os.sendto(mSocket, buf.array(), 0, buf.limit(), 0, mInterfaceBroadcastAddr);
        } catch (Exception se) {
            //Consider an Arp socket creation issue as a successful Arp
            //test to avoid any wifi connectivity issues
            Log.e(TAG, "ARP send failure: " + se);
            return null;
        }

        byte[] socketBuf = new byte[MAX_LENGTH];

        while (SystemClock.elapsedRealtime() < timeout) {
            long duration = (long) timeout - SystemClock.elapsedRealtime();
            StructTimeval t = StructTimeval.fromMillis(duration);
            Os.setsockoptTimeval(mSocket, SOL_SOCKET, SO_RCVTIMEO, t);
            Log.i(TAG, "Wait ARP reply in " + duration);

            int readLen = 0;
            try {
                readLen = Os.read(mSocket, socketBuf, 0, MAX_LENGTH);
                Log.i(TAG, "readLen: " + readLen);
            } catch (Exception se) {
                //Consider an Arp socket creation issue as a successful Arp
                //test to avoid any wifi connectivity issues
                Log.e(TAG, "ARP read failure: " + se);
                return null;
            }



            // Verify packet details. see RFC 826
            if (readLen >= ARP_LENGTH + ETHERNET_LENGTH) {
                byte[] recvBuf = new byte[ARP_LENGTH];
                //Skip 14 bytes for ethernet frame
                System.arraycopy(socketBuf, 14, recvBuf, 0, ARP_LENGTH);
                if (PKT_DBG) {
                    Log.i(TAG, "Recv Buffer:" + HexDump.dumpHexString(recvBuf));
                }

                if ((recvBuf[0] == 0) && (recvBuf[1] == ETHERNET_TYPE) // type Ethernet
                    && (recvBuf[2] == 8) && (recvBuf[3] == 0) // protocol IP
                    && (recvBuf[4] == MAC_ADDR_LENGTH) // mac length
                    && (recvBuf[5] == IPV4_LENGTH) // IPv4 protocol size
                    && (recvBuf[6] == 0) && (recvBuf[7] == 2) // ARP reply
                    // verify desired IP address
                    && (recvBuf[14] == desiredIp[0]) && (recvBuf[15] == desiredIp[1])
                    && (recvBuf[16] == desiredIp[2]) && (recvBuf[17] == desiredIp[3]))
                {
                    // looks good.  copy out the MAC
                    byte[] result = new byte[MAC_ADDR_LENGTH];
                    System.arraycopy(recvBuf, 8, result, 0, MAC_ADDR_LENGTH);
                    Log.i(TAG, "target mac addr:" + HexDump.dumpHexString(result));
                    return result;
                }
            }
        }

        return null;
    }

    /**
     * Run ARP procedure.
     * @param interfaceName network interface name.
     * @param myAddr local IP address.
     * @param peerAddr peer IP address.
     * @param timeoutMillis timeout value for arp procedure.
     * @return indicate the ARP procedure is succeed or not depended on arp reply.
     */
    public static boolean doArp(String interfaceName, InetAddress myAddr,
                                    InetAddress peerAddr, int timeoutMillis) {
        return doArp(interfaceName, myAddr, peerAddr, timeoutMillis, 2);
    }

    /**
     * Run ARP procedure.
     * @param interfaceName network interface name.
     * @param myAddr local IP address.
     * @param peerAddr peer IP address.
     * @param timeoutMillis timeout value for arp procedure.
     * @param totalTimes ARP procedure times.
     * @return indicate the ARP procedure is succeed or not depended on arp reply.
     */
    public static boolean doArp(String interfaceName, InetAddress myAddr,
                                    InetAddress peerAddr, int timeoutMillis,
                                    int totalTimes) {
        boolean success = false;
        ArpPeer peer = null;
        try {
            peer = new ArpPeer(interfaceName, myAddr, peerAddr);
            int responses = 0;
            for (int i = 0; i < totalTimes; i++) {
                if (peer.doArp(timeoutMillis) != null) {
                    responses++;
                }
            }
            Log.d(TAG, "ARP test result: " + responses);
            if (responses == totalTimes) {
                return true;
            }
        } catch (SocketException|ErrnoException se) {
            //Consider an Arp socket creation issue as a successful Arp
            //test to avoid any wifi connectivity issues
            Log.e(TAG, "ARP test initiation failure: " + se);
        } catch (Exception e) {
            Log.e(TAG, "exception:" + e);
        } finally {
            if (peer != null) {
                peer.close();
            }
        }
        return success;
    }

    /**
     * Close sockets which is used in ARP.
     */
    public void close() {
        Log.i(TAG, "Close arp");
        closeQuietly(mSocket);
        /// M: ALPS03066133: prevent arpPeer fd leak  @{
        mSocket = null;
        /// @}
    }

    private static void closeQuietly(FileDescriptor fd) {
        try {
            IoBridge.closeAndSignalBlockedThreads(fd);
        } catch (IOException ignored) { }
    }

    /// M: ALPS03066133: prevent arpPeer fd leak  @{
    @Override
    protected void finalize() throws Throwable {
        try {
            if (mSocket != null) {
                Log.e(TAG, "ArpPeer socket was finalized without closing");
                close();
            }
        } catch (Exception e) {
            Log.e(TAG, "finalize() exception: " + e);
        } finally {
            super.finalize();
        }
    }
    /// @}
}