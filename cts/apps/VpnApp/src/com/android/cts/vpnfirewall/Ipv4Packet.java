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

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.util.Arrays;

public class Ipv4Packet {
    private static final int HEADER_MIN_LENGTH = 20;

    int version;
    int headerLength;
    int type;
    int totalLength;
    int identification;
    int flagsAndOffset;
    int timeToLive;
    int protocol;
    Inet4Address sourceAddress;
    Inet4Address destinationAddress;
    byte[] options;
    byte[] data;

    public Ipv4Packet(DataInputStream stream) throws IOException {
        int versionIhl = stream.readUnsignedByte();
        version = (versionIhl & 0xF0) >> 4;
        headerLength = (versionIhl & 0x0F) * 4;

        type = stream.readUnsignedByte();
        totalLength = stream.readUnsignedShort();
        identification = stream.readUnsignedShort();
        flagsAndOffset = stream.readUnsignedShort();
        timeToLive = stream.readUnsignedByte();
        protocol = stream.readUnsignedByte();
        int checksum = stream.readUnsignedShort();

        byte[] address = new byte[4];

        stream.read(address, 0, address.length);
        sourceAddress = (Inet4Address) Inet4Address.getByAddress(address);

        stream.read(address, 0, address.length);
        destinationAddress = (Inet4Address) Inet4Address.getByAddress(address);

        if (headerLength < HEADER_MIN_LENGTH) {
            throw new IllegalArgumentException("Header length = " + headerLength
                    + " is less than HEADER_MIN_LENGTH = " + HEADER_MIN_LENGTH);
        }
        options = new byte[headerLength - HEADER_MIN_LENGTH];
        stream.read(options, 0, options.length);

        if (totalLength < headerLength) {
            throw new IllegalArgumentException("Total length = " + totalLength
                    + " is less than header length = " + headerLength);
        }
        data = new byte[totalLength - headerLength];
        stream.read(data, 0, data.length);

        byte[] original = new byte[totalLength];
        stream.reset();
        stream.readFully(original);
        if (!Arrays.equals(original, getEncoded())) {
            throw new IOException("Corrupted message. Checksum: " + checksum);
        }
    }

    public void setOptions(byte[] newOptions) {
        options = newOptions;
        headerLength = HEADER_MIN_LENGTH + options.length;
    }

    public void setData(byte[] newData) {
        data = newData;
        totalLength = headerLength + data.length;
    }

    public byte[] getEncoded() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream stream = new DataOutputStream(output);

        stream.writeByte((version << 4) | (headerLength / 4));
        stream.writeByte(type);
        stream.writeShort(totalLength);
        stream.writeShort(identification);
        stream.writeShort(flagsAndOffset);
        stream.writeByte(timeToLive);
        stream.writeByte(protocol);

        int checksumPosition = stream.size();
        stream.writeShort(/* checksum */ 0);
        stream.write(sourceAddress.getAddress(), 0, 4);
        stream.write(destinationAddress.getAddress(), 0, 4);
        stream.write(options, 0, options.length);
        stream.write(data, 0, data.length);
        stream.close();

        byte[] result = output.toByteArray();
        int checksum = Rfc1071.checksum(result, headerLength);
        result[checksumPosition + 0] = (byte) ((checksum & 0xFF00) >> 8);
        result[checksumPosition + 1] = (byte) ((checksum & 0x00FF));
        return result;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder(256);

        out.append("IPv4 Packet {");
        out.append("\n  Version:        ").append(version);
        out.append("\n  Header length:  ").append(headerLength);
        out.append("\n  Type:           ").append(type);
        out.append("\n  Total length:   ").append(totalLength);
        out.append("\n  Identification: ").append(identification);
        out.append("\n  Flags + offset: ").append(flagsAndOffset);
        out.append("\n  Time to live:   ").append(timeToLive);
        out.append("\n  Protocol:       ").append(protocol);
        out.append("\n  Source:         ").append(sourceAddress.getHostAddress());
        out.append("\n  Destination:    ").append(destinationAddress.getHostAddress());
        out.append("\n  Options: [");
        for (int i = 0 ; i < options.length; i++) {
            if (i % 16 == 0) {
                out.append(String.format("\n%4s", ""));
            }
            out.append(String.format(" %02X", options[i] & 0xFF));
        }
        out.append("\n  ]");
        out.append("\n  Data: [");
        for (int i = 0 ; i < data.length; i++) {
            if (i % 16 == 0) {
                out.append(String.format("\n%4s", ""));
            }
            out.append(String.format(" %02X", data[i] & 0xFF));
        }
        out.append("\n  ]");
        out.append("\n}");
        return out.toString();
    }
}
