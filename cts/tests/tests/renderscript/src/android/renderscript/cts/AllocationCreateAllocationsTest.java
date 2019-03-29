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
import android.renderscript.Element;
import android.renderscript.Element.DataType;
import android.renderscript.RSIllegalArgumentException;
import android.renderscript.Type;

import android.util.Log;
import android.view.Surface;

public class AllocationCreateAllocationsTest extends RSBaseCompute {
    private int dimX = 1920;
    private int dimY = 1080;
    private final int MAX_NUM_IO_ALLOC = 16;

    Allocation[] createAllocationsHelper(int usage, int numAlloc) {
      Element e = Element.U8_4(mRS);
      Type t = Type.createXY(mRS, e, dimX, dimY);
      return Allocation.createAllocations(mRS, t, usage, numAlloc);
    }

    public void testCreateAllocations() {
        int usage = Allocation.USAGE_SCRIPT;

        int numAlloc = MAX_NUM_IO_ALLOC + 1;
        Allocation[] allocArray;
        allocArray = createAllocationsHelper(usage, numAlloc);
        assertTrue("failed to create AllocationQueue", allocArray != null);
        for (Allocation a : allocArray) {
            a.destroy();
        }
    }

    public void testCreateAllocations_USAGE_IO_INPUT() {
        int usage = Allocation.USAGE_IO_INPUT;

        int numAlloc = MAX_NUM_IO_ALLOC + 1;
        Allocation[] allocArray;
        try {
            allocArray = createAllocationsHelper(usage, MAX_NUM_IO_ALLOC + 1);
            fail("should throw RSIllegalArgumentException");
        } catch (RSIllegalArgumentException e) {
        }
        numAlloc = 10;
        allocArray = createAllocationsHelper(usage, numAlloc);
        assertTrue("failed to create AllocationQueue", allocArray != null);
        for (Allocation a : allocArray) {
            a.destroy();
        }
    }

    public void testGetProperties() {
        int numAlloc = 10;
        int usage = Allocation.USAGE_SCRIPT | Allocation.USAGE_IO_INPUT;
        Allocation[] allocArray = createAllocationsHelper(usage, numAlloc);

        Element eRef = allocArray[0].getElement();
        Type tRef = allocArray[0].getType();
        int uRef = allocArray[0].getUsage();
        Surface sRef = allocArray[0].getSurface();
        for (int i=1; i<numAlloc; i++) {
            Element e = allocArray[i].getElement();
            assertTrue("Element mismatch between AllocationQueue and Allocation",
                       e.equals(eRef));
            Type t = allocArray[i].getType();
            assertTrue("Type mismatch between AllocationQueue and Allocation",
                       t.equals(tRef));
            int u = allocArray[i].getUsage();
            assertTrue("Usage mismatch between AllocationQueue and Allocation",
                       u == uRef);
            Surface s = allocArray[i].getSurface();
            assertTrue("Surface mismatch between AllocationQueue and Allocation",
                       s.equals(sRef));
        }
        for (Allocation a : allocArray) {
            a.destroy();
        }
    }

    public void testMultipleIoReceive_USAGE_IO_INPUT() {
        int usage = Allocation.USAGE_SCRIPT | Allocation.USAGE_IO_INPUT;
        int dX = 64, dY = 64, numAlloc = 5;
        Type t = Type.createXY(mRS, Element.U8_4(mRS), dX, dY);

        Allocation[] allocArray = Allocation.createAllocations(mRS, t, usage, numAlloc);
        Allocation inputAlloc = Allocation.createTyped(mRS, t,
                                    Allocation.USAGE_SCRIPT | Allocation.USAGE_IO_OUTPUT);
        inputAlloc.setSurface(allocArray[0].getSurface());

        for (int i=0; i<numAlloc; i++) {
            Random r = new Random();
            byte[] dataIn = new byte[dX * dY * 4];
            byte[] dataOut = new byte[dX * dY * 4];

            r.nextBytes(dataIn);
            inputAlloc.copyFromUnchecked(dataIn);
            inputAlloc.ioSend();
            allocArray[i].ioReceive();
            allocArray[i].copyTo(dataOut);
            for (int j=0; j<dX*dY*4; j++) {
                assertTrue("IoReceive Failed, Frame: " + i, dataIn[j] == dataOut[j]);
            }
        }
        for (Allocation a : allocArray) {
            a.destroy();
        }
        inputAlloc.destroy();
    }
}
