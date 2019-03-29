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

package android.util.proto.cts;

import android.util.proto.EncodedBuffer;

import junit.framework.TestCase;
import org.junit.Assert;

/**
 * Test the EncodedBuffer class.
 *
 * Most of the tests here operate on an EncodedBuffer with two different chunk sizes,
 * the default size, which checks that the code is operating as it usually does, and
 * with the chunk size set to 1, which forces the boundary condition at the write of
 * every byte.
 */
public class EncodedBufferTest extends TestCase {
    private static final String TAG = "EncodedBufferTest";

    public void assertEquals(byte[] expected, EncodedBuffer actual) {
        if (expected == null) {
            expected = new byte[0];
        }
        assertEquals("actual.getWritePos() == expected.length", expected.length,
                actual.getWritePos());
        Assert.assertArrayEquals(expected, actual.getBytes(expected.length));
    }

    /**
     * Tests that a variety of chunk sizes advance the chunks correctly.
     */
    public void testWriteRawByteWrapsChunks() throws Exception {
        EncodedBuffer buffer;

        buffer = new EncodedBuffer(1);
        for (int i=0; i<100; i++) {
            buffer.writeRawByte((byte)i);
        }
        assertEquals(100, buffer.getChunkCount());
        assertEquals(99, buffer.getWriteBufIndex());
        assertEquals(1, buffer.getWriteIndex());

        buffer = new EncodedBuffer(50);
        for (int i=0; i<100; i++) {
            buffer.writeRawByte((byte)i);
        }
        assertEquals(2, buffer.getChunkCount());
        assertEquals(1, buffer.getWriteBufIndex());
        assertEquals(50, buffer.getWriteIndex());

        buffer = new EncodedBuffer(50);
        for (int i=0; i<101; i++) {
            buffer.writeRawByte((byte)i);
        }
        assertEquals(3, buffer.getChunkCount());
        assertEquals(2, buffer.getWriteBufIndex());
        assertEquals(1, buffer.getWriteIndex());

        buffer = new EncodedBuffer();
        for (int i=0; i<100; i++) {
            buffer.writeRawByte((byte)i);
        }
        assertEquals(1, buffer.getChunkCount());
        assertEquals(0, buffer.getWriteBufIndex());
        assertEquals(100, buffer.getWriteIndex());
    }

    /**
     * Tests that writeRawBytes writes what is expected.
     */
    public void testWriteRawBuffer() throws Exception {
        testWriteRawBuffer(0);
        testWriteRawBuffer(1);
        testWriteRawBuffer(5);
    }

    public void testWriteRawBuffer(int chunkSize) throws Exception {
        testWriteRawBuffer(chunkSize, 0);
        testWriteRawBuffer(chunkSize, 1);
        testWriteRawBuffer(chunkSize, 3);
        testWriteRawBuffer(chunkSize, 5);
        testWriteRawBuffer(chunkSize, 7);
        testWriteRawBuffer(chunkSize, 1024);
        testWriteRawBuffer(chunkSize, 1024*1024);
    }

    public void testWriteRawBuffer(int chunkSize, int bufferSize) throws Exception {
        final EncodedBuffer buffer = new EncodedBuffer(chunkSize);

        final byte[] data = bufferSize > 0 ? new byte[bufferSize] : null;
        final byte[] expected = bufferSize > 0 ? new byte[bufferSize] : null;
        for (int i=0; i<bufferSize; i++) {
            data[i] = (byte)i;
            expected[i] = (byte)i;
        }

        buffer.writeRawBuffer(data);

        // Make sure it didn't touch the original array
        Assert.assertArrayEquals(expected, data);

        // Make sure it wrote the array correctly
        assertEquals(data, buffer);
    }

    /**
     * Tests that writeRawBytes writes what is expected.
     */
    public void testWriteRawByte() throws Exception {
        testWriteRawByte(0);
        testWriteRawByte(1);
    }

    public void testWriteRawByte(int chunkSize) throws Exception {
        final EncodedBuffer buffer = new EncodedBuffer(chunkSize);

        buffer.writeRawByte((byte)0);
        buffer.writeRawByte((byte)42);
        buffer.writeRawByte((byte)127);
        buffer.writeRawByte((byte)-128);

        assertEquals(new byte[] { 0, 42, 127, -128 }, buffer);
    }

    /**
     * Tests the boundary conditions of getRawVarint32Size.
     */
    public void testGetRawUnsigned32Size() throws Exception {
        assertEquals(1, EncodedBuffer.getRawVarint32Size(0));
        assertEquals(1, EncodedBuffer.getRawVarint32Size(0x0000007f));
        assertEquals(2, EncodedBuffer.getRawVarint32Size(0x00000080));
        assertEquals(2, EncodedBuffer.getRawVarint32Size(0x00003fff));
        assertEquals(3, EncodedBuffer.getRawVarint32Size(0x00004000));
        assertEquals(3, EncodedBuffer.getRawVarint32Size(0x001fffff));
        assertEquals(4, EncodedBuffer.getRawVarint32Size(0x00200000));
        assertEquals(4, EncodedBuffer.getRawVarint32Size(0x0fffffff));
        assertEquals(5, EncodedBuffer.getRawVarint32Size(0x10000000));
        assertEquals(5, EncodedBuffer.getRawVarint32Size(0xffffffff));
    }

    /**
     * Tests that writeRawVarint32 writes what is expected.
     */
    public void testWriteRawVarint32() throws Exception {
        testWriteRawVarint32(0);
        testWriteRawVarint32(1);
    }

    public void testWriteRawVarint32(int chunkSize) throws Exception {
        final EncodedBuffer buffer = new EncodedBuffer(chunkSize);

        buffer.writeRawVarint32(0);
        buffer.writeRawVarint32(0x0000007f << (7 * 0));
        buffer.writeRawVarint32(0x0000007f << (7 * 1));
        buffer.writeRawVarint32(0x0000007f << (7 * 2));
        buffer.writeRawVarint32(0x0000007f << (7 * 3));
        buffer.writeRawVarint32(0xf0000000);
        buffer.writeRawVarint32(-1);
        buffer.writeRawVarint32(Integer.MIN_VALUE);
        buffer.writeRawVarint32(Integer.MAX_VALUE);

        assertEquals(new byte[] { 
                (byte)0x00,                                                 // 0
                (byte)0x7f,                                                 // 0x7f << (7 * 0)
                (byte)0x80, (byte)0x7f,                                     // 0x7f << (7 * 1)
                (byte)0x80, (byte)0x80, (byte)0x7f,                         // 0x7f << (7 * 2)
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x7f,             // 0x7f << (7 * 3)
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x0f, // 0xf0000000
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x0f, // 0xffffffff
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x08, // 0x80000000
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x07, // 0x7fffffff
                }, buffer);
    }

    /**
     * Tests the boundary conditions of getRawVarint64Size.
     */
    public void testGetRawVarint64Size() throws Exception {
        assertEquals(1, EncodedBuffer.getRawVarint64Size(0));
        assertEquals(1, EncodedBuffer.getRawVarint64Size(0x00000000000007fL));
        assertEquals(2, EncodedBuffer.getRawVarint64Size(0x000000000000080L));
        assertEquals(2, EncodedBuffer.getRawVarint64Size(0x000000000003fffL));
        assertEquals(3, EncodedBuffer.getRawVarint64Size(0x000000000004000L));
        assertEquals(3, EncodedBuffer.getRawVarint64Size(0x0000000001fffffL));
        assertEquals(4, EncodedBuffer.getRawVarint64Size(0x000000000200000L));
        assertEquals(4, EncodedBuffer.getRawVarint64Size(0x00000000fffffffL));
        assertEquals(5, EncodedBuffer.getRawVarint64Size(0x000000010000000L));
        assertEquals(5, EncodedBuffer.getRawVarint64Size(0x0000007ffffffffL));
        assertEquals(6, EncodedBuffer.getRawVarint64Size(0x000000800000000L));
        assertEquals(6, EncodedBuffer.getRawVarint64Size(0x00003ffffffffffL));
        assertEquals(7, EncodedBuffer.getRawVarint64Size(0x000040000000000L));
        assertEquals(7, EncodedBuffer.getRawVarint64Size(0x001ffffffffffffL));
        assertEquals(8, EncodedBuffer.getRawVarint64Size(0x002000000000000L));
        assertEquals(8, EncodedBuffer.getRawVarint64Size(0x0ffffffffffffffL));
        assertEquals(9, EncodedBuffer.getRawVarint64Size(0x0100000000000000L));
        assertEquals(9, EncodedBuffer.getRawVarint64Size(0x7fffffffffffffffL));
        assertEquals(10, EncodedBuffer.getRawVarint64Size(0x8000000000000000L));
        assertEquals(10, EncodedBuffer.getRawVarint64Size(0xffffffffffffffffL));
    }

    /**
     * With chunk size 1: Tests that startEditing puts the EncodedBuffer into
     * a state where the read and write pointers are reset, the ranges are set,
     * and it is ready to read / rewrite.
     */
    public void testStartEditingChunkSize1() {
        final byte[] DATA = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };

        final EncodedBuffer buffer = new EncodedBuffer(1);

        buffer.writeRawBuffer(DATA);

        // All the pointers are at the end of what we just wrote
        assertEquals(DATA.length, buffer.getWritePos());
        assertEquals(DATA.length-1, buffer.getWriteBufIndex());
        assertEquals(1, buffer.getWriteIndex());
        assertEquals(DATA.length, buffer.getChunkCount());
        assertEquals(-1, buffer.getReadableSize());

        buffer.startEditing();

        // Should be reset
        assertEquals(0, buffer.getWritePos());
        assertEquals(0, buffer.getWriteBufIndex());
        assertEquals(0, buffer.getWriteIndex());

        // The data should still be there
        assertEquals(DATA.length, buffer.getChunkCount());
        assertEquals(DATA.length, buffer.getReadableSize());
    }

    /**
     * With chunk size 100 (big enough to fit everything): Tests that
     * startEditing puts the EncodedBuffer into a state where the read
     * and write pointers are reset, the ranges are set, and it is ready
     * to read / rewrite.
     */
    public void testStartEditingChunkSize100() {
        final byte[] DATA = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };

        final EncodedBuffer buffer = new EncodedBuffer(100);

        buffer.writeRawBuffer(DATA);

        // All the pointers are at the end of what we just wrote
        assertEquals(DATA.length, buffer.getWritePos());
        assertEquals(0, buffer.getWriteBufIndex());
        assertEquals(DATA.length, buffer.getWriteIndex());
        assertEquals(1, buffer.getChunkCount());

        // Not set yet
        assertEquals(-1, buffer.getReadableSize());

        buffer.startEditing();

        // Should be reset
        assertEquals(0, buffer.getWritePos());
        assertEquals(0, buffer.getWriteBufIndex());
        assertEquals(0, buffer.getWriteIndex());

        // The data should still be there
        assertEquals(1, buffer.getChunkCount());
        assertEquals(DATA.length, buffer.getReadableSize());
    }

    /**
     * Tests that writeFromThisBuffer writes what is expected.
     */
    public void testWriteFromThisBuffer() throws Exception {
        testWriteFromThisBuffer(0);
        testWriteFromThisBuffer(1);
        testWriteFromThisBuffer(23);
        testWriteFromThisBuffer(25);
        testWriteFromThisBuffer(50);
        testWriteFromThisBuffer(75);
        testWriteFromThisBuffer(100);
        testWriteFromThisBuffer(101);
        testWriteFromThisBuffer(1000);
    }

    public void testWriteFromThisBuffer(int chunkSize) throws Exception {
        testWriteFromThisBuffer(chunkSize, 0, 0, 0);
        testWriteFromThisBuffer(chunkSize, 0, 0, 10);
        testWriteFromThisBuffer(chunkSize, 0, 0, 100);
        testWriteFromThisBuffer(chunkSize, 0, 1, 99);
        testWriteFromThisBuffer(chunkSize, 0, 10, 10);
        testWriteFromThisBuffer(chunkSize, 0, 10, 90);
        testWriteFromThisBuffer(chunkSize, 0, 25, 25);
        testWriteFromThisBuffer(chunkSize, 0, 50, 25);
        testWriteFromThisBuffer(chunkSize, 0, 50, 50);

        testWriteFromThisBufferFails(chunkSize, 0, 0, -1);
        testWriteFromThisBufferFails(chunkSize, 0, 0, 101);
        testWriteFromThisBufferFails(chunkSize, 0, 10, 100);
        testWriteFromThisBufferFails(chunkSize, 10, 0, 0);
        testWriteFromThisBufferFails(chunkSize, 10, 0, 1);
        testWriteFromThisBufferFails(chunkSize, 10, 0, 89);
        testWriteFromThisBufferFails(chunkSize, 10, 0, 90);
    }

    private void testWriteFromThisBuffer(int chunkSize, int destOffset, int srcOffset, int size)
            throws Exception {
        // Setup
        final EncodedBuffer buffer = new EncodedBuffer(chunkSize);

        // Input data: { 0 .. 99 }
        final byte[] DATA = new byte[100];
        final byte[] expected = new byte[DATA.length];
        for (byte i=0; i<DATA.length; i++) {
            DATA[i] = i;
            expected[i] = i;
        }
        // This *should* be the same as System.arraycopy
        System.arraycopy(expected, srcOffset, expected, destOffset, size);

        buffer.writeRawBuffer(DATA);

        buffer.startEditing();
        Assert.assertArrayEquals(DATA, buffer.getBytes(buffer.getReadableSize()));


        // Skip destOffset bytes (also tests that writing from offset 0 to offset 0 works).
        if (destOffset != 0) {
            buffer.writeFromThisBuffer(0, destOffset);
            Assert.assertArrayEquals(DATA, buffer.getBytes(buffer.getReadableSize()));
        }

        // Test call
        buffer.writeFromThisBuffer(srcOffset, size);

        // Assert correctness
        Assert.assertArrayEquals(expected, buffer.getBytes(buffer.getReadableSize()));
        assertEquals(destOffset+size, buffer.getWritePos());
    }

    private void testWriteFromThisBufferFails(int chunkSize, int destOffset, int srcOffset,
            int size) throws Exception {
        try {
            testWriteFromThisBuffer(chunkSize, destOffset, srcOffset, size);
            throw new RuntimeException("Should have thorown an exception: "
                    + " chunkSize=" + chunkSize + " destOffset=" + destOffset
                    + " srcOffset=" + srcOffset + " size=" + size);
        } catch (Exception ex) {
            // good
        }
    }
}
