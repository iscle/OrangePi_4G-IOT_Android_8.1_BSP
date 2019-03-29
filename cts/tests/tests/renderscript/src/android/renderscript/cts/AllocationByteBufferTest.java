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

package android.renderscript.cts;

import java.nio.ByteBuffer;
import java.util.Random;

import android.renderscript.Allocation;
import android.renderscript.AllocationAdapter;
import android.renderscript.Element;
import android.renderscript.Element.DataType;
import android.renderscript.RSIllegalArgumentException;
import android.renderscript.Type;

import android.util.Log;

public class AllocationByteBufferTest extends RSBaseCompute {

    protected int MAX_DIM = 128;
    protected int RAND_SEED = 2016;

    Allocation createTypedAllocation(DataType dt, int size, int dimX, int dimY) {
        Element e = getElement(mRS, dt, size);
        Type t;
        if (dimY <= 1) {
            t = Type.createX(mRS, e, dimX);
        } else {
            t = Type.createXY(mRS, e, dimX, dimY);
        }

        return Allocation.createTyped(mRS, t);
    }

    void testByteBufferHelper(DataType dt, int byteSize, int dimX, int dimY) {
        Random r = new Random(RAND_SEED);

        for (int size = 1; size <= 4; size++) {
            int vecWidth = (size == 3) ? 4 : size;
            byte[] data = new byte[dimX * dimY * vecWidth * byteSize];
            RSUtils.genRandomBytes(RAND_SEED, data, true, 8);

            Allocation alloc = createTypedAllocation(dt, size, dimX, dimY);
            alloc.copyFromUnchecked(data);

            ByteBuffer bb = alloc.getByteBuffer();
            int stride = (int)alloc.getStride();
            for (int i=0; i < 10; i++) {
                int posX = r.nextInt(dimX);
                int posY = r.nextInt(dimY);
                byte byteInData = data[(posY * dimX + posX) * vecWidth * byteSize];
                byte byteInBuffer = bb.get(posY * stride + posX * vecWidth * byteSize);
                assertEquals(byteInData, byteInBuffer);
            }
            alloc.destroy();
        }
    }

    void testByteBufferHelper1D(DataType dt, int byteSize) {
        Random r = new Random(RAND_SEED);
        int dimX = r.nextInt(MAX_DIM) + 1;
        testByteBufferHelper(dt, byteSize, dimX, 1);
    }

    void testByteBufferHelper2D(DataType dt, int byteSize) {
        Random r = new Random(RAND_SEED);
        int dimX = r.nextInt(MAX_DIM) + 1;
        int dimY = r.nextInt(MAX_DIM) + 2; //Make sure dimY is larger than 1;
        testByteBufferHelper(dt, byteSize, dimX, dimY);
    }

    public void test1DWrite() {
        Random r = new Random(RAND_SEED);
        int vecWidth = 4;
        int dimX = r.nextInt(MAX_DIM) + 1;

        Type t = Type.createX(mRS, Element.U8_4(mRS), dimX);
        Allocation alloc = Allocation.createTyped(mRS, t);
        ByteBuffer bb = alloc.getByteBuffer();

        byte[] dataIn = new byte[dimX * vecWidth];
        byte[] dataOut = new byte[dimX * vecWidth];
        RSUtils.genRandomBytes(RAND_SEED, dataIn, true, 8);
        bb.put(dataIn);
        alloc.copyTo(dataOut);
        for (int i = 0; i < dimX * vecWidth; i++) {
            assertEquals(dataIn[i], dataOut[i]);
        }
    }

    public void test2DWrite() {
        Random r = new Random(RAND_SEED);
        int vecWidth = 4;
        int dimX = r.nextInt(MAX_DIM) + 1;
        int dimY = r.nextInt(MAX_DIM) + 2; //Make sure dimY is larger than 1;

        Type t = Type.createXY(mRS, Element.U8_4(mRS), dimX, dimY);
        Allocation alloc = Allocation.createTyped(mRS, t);
        ByteBuffer bb = alloc.getByteBuffer();

        int stride = (int)alloc.getStride();
        byte[] dataIn = new byte[stride * dimY];
        byte[] dataOut = new byte[dimX * dimY * vecWidth];
        RSUtils.genRandomBytes(RAND_SEED, dataIn, true, 8);
        bb.put(dataIn);
        alloc.copyTo(dataOut);
        for (int i = 0; i < dimX*vecWidth; i++) {
            for (int j = 0; j < dimY; j++) {
                assertEquals(dataIn[j*stride + i], dataOut[j*dimX*vecWidth + i]);
            }
        }
    }

    public void testByteBufferU8_1D() {
        testByteBufferHelper1D(DataType.UNSIGNED_8, 1);
    }

    public void testByteBufferU8_2D() {
        testByteBufferHelper2D(DataType.UNSIGNED_8, 1);
    }

    public void testByteBufferU16_1D() {
        testByteBufferHelper1D(DataType.UNSIGNED_16, 2);
    }

    public void testByteBufferU16_2D() {
        testByteBufferHelper2D(DataType.UNSIGNED_16, 2);
    }

    public void testByteBufferU32_1D() {
        testByteBufferHelper1D(DataType.UNSIGNED_32, 4);
    }

    public void testByteBufferU32_2D() {
        testByteBufferHelper2D(DataType.UNSIGNED_32, 4);
    }

    public void testByteBufferU64_1D() {
        testByteBufferHelper1D(DataType.UNSIGNED_64, 8);
    }

    public void testByteBufferU64_2D() {
        testByteBufferHelper2D(DataType.UNSIGNED_64, 8);
    }

    public void testByteBufferS8_1D() {
        testByteBufferHelper1D(DataType.SIGNED_8, 1);
    }

    public void testByteBufferS8_2D() {
        testByteBufferHelper2D(DataType.SIGNED_8, 1);
    }

    public void testByteBufferS16_1D() {
        testByteBufferHelper1D(DataType.SIGNED_16, 2);
    }

    public void testByteBufferS16_2D() {
        testByteBufferHelper2D(DataType.SIGNED_16, 2);
    }

    public void testByteBufferS32_1D() {
        testByteBufferHelper1D(DataType.SIGNED_32, 4);
    }

    public void testByteBufferS32_2D() {
        testByteBufferHelper2D(DataType.SIGNED_32, 4);
    }

    public void testByteBufferS64_1D() {
        testByteBufferHelper1D(DataType.SIGNED_64, 8);
    }

    public void testByteBufferS64_2D() {
        testByteBufferHelper2D(DataType.UNSIGNED_64, 8);
    }

    public void testByteBufferF32_1D() {
        testByteBufferHelper1D(DataType.FLOAT_32, 4);
    }

    public void testByteBufferF32_2D() {
        testByteBufferHelper2D(DataType.FLOAT_32, 4);
    }

    public void testByteBufferF64_1D() {
        testByteBufferHelper1D(DataType.FLOAT_64, 8);
    }

    public void testByteBufferF64_2D() {
        testByteBufferHelper2D(DataType.FLOAT_64, 8);
    }
}
