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
import java.util.Arrays;

public class IcmpMessage {
    int type;
    int code;
    long quench;
    byte[] data;

    IcmpMessage(DataInputStream stream, int length) throws IOException {
        type = stream.readUnsignedByte();
        code = stream.readUnsignedByte();
        int checksum = stream.readUnsignedShort();
        quench = stream.readInt() & 0xFFFFFFFFL;

        int dataLength = length - 8;
        data = new byte[dataLength];
        int len = stream.read(data, 0, dataLength);
        if (len != dataLength) {
            throw new IOException("Expected " + dataLength + " data bytes, received " + len);
        }

        byte[] original = new byte[length];
        stream.reset();
        stream.readFully(original);
        if (!Arrays.equals(original, getEncoded())) {
            throw new IOException("Corrupted message. Checksum: " + checksum);
        }
    }

    public byte[] getEncoded() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream stream = new DataOutputStream(output);

        stream.writeByte(type);
        stream.writeByte(code);
        int checksumPosition = stream.size();
        stream.writeShort(/* checksum */ 0);
        stream.writeInt((int) quench);
        stream.write(data, 0, data.length);

        byte[] result = output.toByteArray();
        int checksum = Rfc1071.checksum(result, result.length);
        result[checksumPosition + 0] = (byte) ((checksum & 0xFF00) >> 8);
        result[checksumPosition + 1] = (byte) ((checksum & 0x00FF));
        return result;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder(64);
        out.append("ICMP payload {");
        out.append("\n  Type:     "); out.append(type);
        out.append("\n  Code:     "); out.append(code);
        out.append("\n  Quench:   "); out.append(quench);
        out.append("\n  Data: [");
        for (int i = 0 ; i < data.length; i++) {
            if (i % 16 == 0) {
                out.append(String.format("\n%4s", ""));
            }
            if (Character.isLetter((char) data[i])) {
                out.append(String.format(" %2c", (char) data[i]));
            } else {
                out.append(String.format(" %02X", data[i] & 0xFF));
            }
        }
        out.append("\n  ]");
        out.append("\n}");
        return out.toString();
    }
}

