/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.renderscript.RenderScript;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RSRuntimeException;
import android.util.Log;

/**
 * Base RenderScript test class. This class provides a message handler and a
 * convenient way to wait for compute scripts to complete their execution.
 */
public class RSBaseCompute extends RSBase {
    RenderScript mRS;
    protected int INPUTSIZE = 512;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mRS = RenderScript.create(mCtx);
        mRS.setMessageHandler(mRsMessage);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void checkArray(float[] ref, float[] out, int height, int refStride,
             int outStride, float ulpCount) {
        int minStride = refStride > outStride ? outStride : refStride;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < minStride; j++) {
                int refIdx = i * refStride + j;
                int outIdx = i * outStride + j;
                float ulp = Math.ulp(ref[refIdx]) * ulpCount;
                assertEquals("Incorrect value @ idx = " + i + " |",
                        ref[refIdx],
                        out[outIdx],
                        ulp);
            }
        }
    }

    public void checkArray(int[] ref, int[] out, int height, int refStride,
             int outStride) {
        int minStride = refStride > outStride ? outStride : refStride;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < minStride; j++) {
                int refIdx = i * refStride + j;
                int outIdx = i * outStride + j;
                assertEquals("Incorrect value @ idx = " + i + " |",
                        ref[refIdx],
                        out[outIdx]);
            }
        }
    }

    // TODO Is there a better way to do this
    protected Element getElement(RenderScript rs, Element.DataType dataType, int size) {
        Element element = null;
        if (size == 1) {
            if (dataType == Element.DataType.FLOAT_64) {
                element = Element.F64(rs);
            } else if (dataType == Element.DataType.FLOAT_32) {
                element = Element.F32(rs);
            } else if (dataType == Element.DataType.FLOAT_16) {
                element = Element.F16(rs);
            } else if (dataType == Element.DataType.SIGNED_64) {
                element = Element.I64(rs);
            } else if (dataType == Element.DataType.UNSIGNED_64) {
                element = Element.U64(rs);
            } else if (dataType == Element.DataType.SIGNED_32) {
                element = Element.I32(rs);
            } else if (dataType == Element.DataType.UNSIGNED_32) {
                element = Element.U32(rs);
            } else if (dataType == Element.DataType.SIGNED_16) {
                element = Element.I16(rs);
            } else if (dataType == Element.DataType.UNSIGNED_16) {
                element = Element.U16(rs);
            } else if (dataType == Element.DataType.SIGNED_8) {
                element = Element.I8(rs);
            } else if (dataType == Element.DataType.UNSIGNED_8) {
                element = Element.U8(rs);
            } else {
                android.util.Log.e("RenderscriptCTS", "Don't know how to create allocation of type" +
                        dataType.toString());
            }
        } else {
            element = Element.createVector(rs, dataType, size);
        }
        return element;
    }

    protected Allocation createRandomAllocation(RenderScript rs, Element.DataType dataType,
            int size, long seed, boolean includeExtremes) {
        Element element = getElement(rs, dataType, size);
        Allocation alloc = Allocation.createSized(rs, element, INPUTSIZE);
        int width = (size == 3) ? 4 : size;
        if (dataType == Element.DataType.FLOAT_64) {
            double[] inArray = new double[INPUTSIZE * width];
            // TODO The ranges for float is too small.  We need to accept a wider range of values.
            double min = -4.0 * Math.PI;
            double max = 4.0 * Math.PI;
            RSUtils.genRandomDoubles(seed, min, max, inArray, includeExtremes);
            alloc.copy1DRangeFrom(0, INPUTSIZE, inArray);
        } else if (dataType == Element.DataType.FLOAT_32) {
            float[] inArray = new float[INPUTSIZE * width];
            // TODO The ranges for float is too small.  We need to accept a wider range of values.
            float min = -4.0f * (float) Math.PI;
            float max = 4.0f * (float) Math.PI;
            RSUtils.genRandomFloats(seed, min, max, inArray, includeExtremes);
            alloc.copy1DRangeFrom(0, INPUTSIZE, inArray);
        } else if (dataType == Element.DataType.FLOAT_16) {
            short[] inArray = new short[INPUTSIZE * width];
            double min = -4.0 * Math.PI;
            double max = 4.0 * Math.PI;
            RSUtils.genRandomFloat16s(seed, min, max, inArray, includeExtremes);
            alloc.copyFrom(inArray);
        } else if (dataType == Element.DataType.SIGNED_64) {
            long[] inArray = new long[INPUTSIZE * width];
            RSUtils.genRandomLongs(seed, inArray, true, 63);
            alloc.copy1DRangeFrom(0, INPUTSIZE, inArray);
        } else if (dataType == Element.DataType.UNSIGNED_64) {
            long[] inArray = new long[INPUTSIZE * width];
            RSUtils.genRandomLongs(seed, inArray, false, 64);
            alloc.copy1DRangeFrom(0, INPUTSIZE, inArray);
        } else if (dataType == Element.DataType.SIGNED_32) {
            int[] inArray = new int[INPUTSIZE * width];
            RSUtils.genRandomInts(seed, inArray, true, 31);
            alloc.copy1DRangeFrom(0, INPUTSIZE, inArray);
        } else if (dataType == Element.DataType.UNSIGNED_32) {
            int[] inArray = new int[INPUTSIZE * width];
            RSUtils.genRandomInts(seed, inArray, false, 32);
            alloc.copy1DRangeFrom(0, INPUTSIZE, inArray);
        } else if (dataType == Element.DataType.SIGNED_16) {
            short[] inArray = new short[INPUTSIZE * width];
            RSUtils.genRandomShorts(seed, inArray, true, 15);
            alloc.copy1DRangeFrom(0, INPUTSIZE, inArray);
        } else if (dataType == Element.DataType.UNSIGNED_16) {
            short[] inArray = new short[INPUTSIZE * width];
            RSUtils.genRandomShorts(seed, inArray, false, 16);
            alloc.copy1DRangeFrom(0, INPUTSIZE, inArray);
        } else if (dataType == Element.DataType.SIGNED_8) {
            byte[] inArray = new byte[INPUTSIZE * width];
            RSUtils.genRandomBytes(seed, inArray, true, 7);
            alloc.copy1DRangeFrom(0, INPUTSIZE, inArray);
        } else if (dataType == Element.DataType.UNSIGNED_8) {
            byte[] inArray = new byte[INPUTSIZE * width];
            RSUtils.genRandomBytes(seed, inArray, true, 8);
            alloc.copy1DRangeFrom(0, INPUTSIZE, inArray);
        } else {
            android.util.Log.e("RenderscriptCTS", "Don't know how to create allocation of type" +
                    dataType.toString());
        }
        return alloc;
    }

    protected Allocation createRandomFloatAllocation(RenderScript rs, Element.DataType dataType,
            int size, long seed, double minValue, double maxValue) {
        Element element = getElement(rs, dataType, size);
        Allocation alloc = Allocation.createSized(rs, element, INPUTSIZE);
        int width = (size == 3) ? 4 : size;
        if (dataType == Element.DataType.FLOAT_64) {
            double[] inArray = new double[INPUTSIZE * width];
            RSUtils.genRandomDoubles(seed, minValue, maxValue, inArray, false);
            alloc.copy1DRangeFrom(0, INPUTSIZE, inArray);
        } else if (dataType == Element.DataType.FLOAT_32) {
            float[] inArray = new float[INPUTSIZE * width];
            RSUtils.genRandomFloats(seed, (float) minValue, (float) maxValue, inArray, false);
            alloc.copy1DRangeFrom(0, INPUTSIZE, inArray);
        } else if (dataType == Element.DataType.FLOAT_16) {
            short[] inArray = new short[INPUTSIZE * width];
            RSUtils.genRandomFloat16s(seed, minValue, maxValue, inArray, false);
            alloc.copy1DRangeFrom(0, INPUTSIZE, inArray);
        } else {
            android.util.Log.e("RenderscriptCTS",
                               "Don't know how to create a random float allocation for " +
                                           dataType.toString());
        }
        return alloc;
    }

    protected Allocation createRandomIntegerAllocation(RenderScript rs, Element.DataType dataType,
            int size, long seed, boolean signed, int numberOfBits) {
        Element element = getElement(rs, dataType, size);
        Allocation alloc = Allocation.createSized(rs, element, INPUTSIZE);
        int width = (size == 3) ? 4 : size;
        if (dataType == Element.DataType.SIGNED_64 ||
                dataType == Element.DataType.UNSIGNED_64) {
            long[] inArray = new long[INPUTSIZE * width];
            RSUtils.genRandomLongs(seed, inArray, signed, numberOfBits);
            alloc.copy1DRangeFrom(0, INPUTSIZE, inArray);
        } else
        if (dataType == Element.DataType.SIGNED_32 ||
                dataType == Element.DataType.UNSIGNED_32) {
            int[] inArray = new int[INPUTSIZE * width];
            RSUtils.genRandomInts(seed, inArray, signed, numberOfBits);
            alloc.copy1DRangeFrom(0, INPUTSIZE, inArray);
        } else if (dataType == Element.DataType.SIGNED_16 ||
                dataType == Element.DataType.UNSIGNED_16) {
            short[] inArray = new short[INPUTSIZE * width];
            RSUtils.genRandomShorts(seed, inArray, signed, numberOfBits);
            alloc.copy1DRangeFrom(0, INPUTSIZE, inArray);
        } else if (dataType == Element.DataType.SIGNED_8 ||
                dataType == Element.DataType.UNSIGNED_8) {
            byte[] inArray = new byte[INPUTSIZE * width];
            RSUtils.genRandomBytes(seed, inArray, signed, numberOfBits);
            alloc.copy1DRangeFrom(0, INPUTSIZE, inArray);
        } else {
            android.util.Log.e("RenderscriptCTS",
                               "Don't know how to create an integer allocation of type" +
                                           dataType.toString());
        }
        return alloc;
    }

    protected <T> void enforceOrdering(/*RenderScript rs,*/ Allocation minAlloc, Allocation maxAlloc) {
        Element element = minAlloc.getElement();
        int stride = element.getVectorSize();
        if (stride == 3) {
            stride = 4;
        }
        int size = INPUTSIZE * stride;
        Element.DataType dataType = element.getDataType();
        if (dataType == Element.DataType.FLOAT_64) {
            double[] minArray = new double[size];
            double[] maxArray = new double[size];
            minAlloc.copyTo(minArray);
            maxAlloc.copyTo(maxArray);
            for (int i = 0; i < size; i++) {
                if (minArray[i] > maxArray[i]) {
                    double temp = minArray[i];
                    minArray[i] = maxArray[i];
                    maxArray[i] = temp;
                }
            }
            minAlloc.copyFrom(minArray);
            maxAlloc.copyFrom(maxArray);
        } else
        if (dataType == Element.DataType.FLOAT_32) {
            float[] minArray = new float[size];
            float[] maxArray = new float[size];
            minAlloc.copyTo(minArray);
            maxAlloc.copyTo(maxArray);
            for (int i = 0; i < size; i++) {
                if (minArray[i] > maxArray[i]) {
                    float temp = minArray[i];
                    minArray[i] = maxArray[i];
                    maxArray[i] = temp;
                }
            }
            minAlloc.copyFrom(minArray);
            maxAlloc.copyFrom(maxArray);
        } else if (dataType == Element.DataType.FLOAT_16) {
            short[] minArray = new short[size];
            short[] maxArray = new short[size];
            minAlloc.copyTo(minArray);
            maxAlloc.copyTo(maxArray);
            for (int i = 0; i < size; i++) {
                double minValue = Float16Utils.convertFloat16ToDouble(minArray[i]);
                double maxValue = Float16Utils.convertFloat16ToDouble(maxArray[i]);
                if (minValue > maxValue) {
                    short temp = minArray[i];
                    minArray[i] = maxArray[i];
                    maxArray[i] = temp;
                }
            }
            minAlloc.copyFrom(minArray);
            maxAlloc.copyFrom(maxArray);
        } else if (dataType == Element.DataType.SIGNED_64) {
            long[] minArray = new long[size];
            long[] maxArray = new long[size];
            minAlloc.copyTo(minArray);
            maxAlloc.copyTo(maxArray);
            for (int i = 0; i < size; i++) {
                if (minArray[i] > maxArray[i]) {
                    long temp = minArray[i];
                    minArray[i] = maxArray[i];
                    maxArray[i] = temp;
                }
            }
            minAlloc.copyFrom(minArray);
            maxAlloc.copyFrom(maxArray);
        } else if (dataType == Element.DataType.UNSIGNED_64) {
            long[] minArray = new long[size];
            long[] maxArray = new long[size];
            minAlloc.copyTo(minArray);
            maxAlloc.copyTo(maxArray);
            for (int i = 0; i < size; i++) {
                if (RSUtils.compareUnsignedLong(minArray[i], maxArray[i]) > 0) {
                    long temp = minArray[i];
                    minArray[i] = maxArray[i];
                    maxArray[i] = temp;
                }
            }
            minAlloc.copyFrom(minArray);
            maxAlloc.copyFrom(maxArray);
        } else if (dataType == Element.DataType.SIGNED_32) {
            int[] minArray = new int[size];
            int[] maxArray = new int[size];
            minAlloc.copyTo(minArray);
            maxAlloc.copyTo(maxArray);
            for (int i = 0; i < size; i++) {
                if (minArray[i] > maxArray[i]) {
                    int temp = minArray[i];
                    minArray[i] = maxArray[i];
                    maxArray[i] = temp;
                }
            }
            minAlloc.copyFrom(minArray);
            maxAlloc.copyFrom(maxArray);
        } else if (dataType == Element.DataType.UNSIGNED_32) {
            int[] minArray = new int[size];
            int[] maxArray = new int[size];
            minAlloc.copyTo(minArray);
            maxAlloc.copyTo(maxArray);
            for (int i = 0; i < size; i++) {
                long min = minArray[i] &0xffffffffl;
                long max = maxArray[i] &0xffffffffl;
                if (min > max) {
                    minArray[i] = (int) max;
                    maxArray[i] = (int) min;
                }
            }
            minAlloc.copyFrom(minArray);
            maxAlloc.copyFrom(maxArray);
        } else if (dataType == Element.DataType.SIGNED_16) {
            short[] minArray = new short[size];
            short[] maxArray = new short[size];
            minAlloc.copyTo(minArray);
            maxAlloc.copyTo(maxArray);
            for (int i = 0; i < size; i++) {
                if (minArray[i] > maxArray[i]) {
                    short temp = minArray[i];
                    minArray[i] = maxArray[i];
                    maxArray[i] = temp;
                }
            }
            minAlloc.copyFrom(minArray);
            maxAlloc.copyFrom(maxArray);
        } else if (dataType == Element.DataType.UNSIGNED_16) {
            short[] minArray = new short[size];
            short[] maxArray = new short[size];
            minAlloc.copyTo(minArray);
            maxAlloc.copyTo(maxArray);
            for (int i = 0; i < size; i++) {
                int min = minArray[i] &0xffff;
                int max = maxArray[i] &0xffff;
                if (min > max) {
                    minArray[i] = (short) max;
                    maxArray[i] = (short) min;
                }
            }
            minAlloc.copyFrom(minArray);
            maxAlloc.copyFrom(maxArray);
        } else if (dataType == Element.DataType.SIGNED_8) {
            byte[] minArray = new byte[size];
            byte[] maxArray = new byte[size];
            minAlloc.copyTo(minArray);
            maxAlloc.copyTo(maxArray);
            for (int i = 0; i < size; i++) {
                if (minArray[i] > maxArray[i]) {
                    byte temp = minArray[i];
                    minArray[i] = maxArray[i];
                    maxArray[i] = temp;
                }
            }
            minAlloc.copyFrom(minArray);
            maxAlloc.copyFrom(maxArray);
        } else if (dataType == Element.DataType.UNSIGNED_8) {
            byte[] minArray = new byte[size];
            byte[] maxArray = new byte[size];
            minAlloc.copyTo(minArray);
            maxAlloc.copyTo(maxArray);
            for (int i = 0; i < size; i++) {
                int min = minArray[i] &0xff;
                int max = maxArray[i] &0xff;
                if (min > max) {
                    minArray[i] = (byte) max;
                    maxArray[i] = (byte) min;
                }
            }
            minAlloc.copyFrom(minArray);
            maxAlloc.copyFrom(maxArray);
        } else {
            android.util.Log.e("RenderscriptCTS", "Ordering not supported for " +
                    dataType.toString());
        }
    }

    public void forEach(int testId, Allocation mIn, Allocation mOut) throws RSRuntimeException {
        // Intentionally empty... subclass will likely define only one, but not both
    }

    public void forEach(int testId, Allocation mIn) throws RSRuntimeException {
        // Intentionally empty... subclass will likely define only one, but not both
    }

    protected void appendVariableToMessage(StringBuilder message, int value) {
        message.append(String.format("%d {%x}", value, value));
    }

    protected void appendVariableToMessage(StringBuilder message, float value) {
        message.append(String.format("%14.8g {%8x} %15a", value,
                        Float.floatToRawIntBits(value), value));
    }

    protected void appendVariableToMessage(StringBuilder message, double value) {
        message.append(String.format("%24.8g {%16x} %31a", value,
                        Double.doubleToRawLongBits(value), value));
    }

    protected void appendVariableToMessage(StringBuilder message, Target.Floaty value) {
        message.append(value.toString());
    }
}
