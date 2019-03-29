/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.verifier.audio.wavelib;

import android.util.Log;

public class VectorAverage {
    private static final String LOGTAG = "VectorAverage";
    private static final int mVersion = 0;
    private double[] mData;
    private int mValueCount = 0;

    public static final int CAPTURE_TYPE_AVERAGE = 0;
    public static final int CAPTURE_TYPE_MAX     = 1;
    public static final int CAPTURE_TYPE_MIN     = 2;

    private int mCaptureType = CAPTURE_TYPE_AVERAGE;

    public void setData(double[] data, boolean replace) {
        int size = data.length;
        if (mData == null || mData.length != size) {
            mData = new double[size];
            mValueCount = 0;
        }
        if (replace || mValueCount == 0) {
            System.arraycopy(data, 0, mData, 0, size);
            mValueCount = 1;
        } else {
            switch(mCaptureType) {
                default:
                case CAPTURE_TYPE_AVERAGE: {
                    for (int i = 0; i < size; i++) {
                        mData[i] += data[i];
                    }
                    mValueCount++;
                }
                break;
                case CAPTURE_TYPE_MAX: {
                    for (int i = 0; i < size; i++) {
                        if (data[i] > mData[i]) {
                            mData[i] = data[i];
                        }
                    }
                    mValueCount = 1;
                }
                break;
                case CAPTURE_TYPE_MIN: {
                    for (int i = 0; i < size; i++) {
                        if (data[i] < mData[i]) {
                            mData[i] = data[i];
                        }
                    }
                    mValueCount = 1;
                }
                break;
            }
        }
    }

    public int getData(double[] data, boolean raw) {
        int nCount = 0;
        if (mData != null && mData.length <= data.length) {
            nCount = mData.length;
            if (mValueCount == 0) {
                for (int i = 0; i < nCount; i++) {
                    data[i] = 0;
                }
            } else if (!raw && mValueCount > 1) {
                for (int i = 0; i < nCount; i++) {
                    data[i] = mData[i] / mValueCount;
                }
            } else {
                for (int i = 0; i < nCount; i++) {
                    data[i] = mData[i];
                }
            }
        }
        return nCount;
    }

    public int getCount() {
        return mValueCount;
    }

    public int getSize() {
        if (mData != null) {
            return mData.length;
        }
        return 0;
    }

    public void reset() {
        mValueCount = 0;
    }

    public void setCaptureType(int type) {
        switch(type) {
            case CAPTURE_TYPE_AVERAGE:
            case CAPTURE_TYPE_MAX:
            case CAPTURE_TYPE_MIN:
                mCaptureType = type;
                break;
            default:
                mCaptureType = CAPTURE_TYPE_AVERAGE;
        }
    }

    public int getCaptureType() {
        return mCaptureType;
    }

    private final String SERIALIZED_VERSION = "VECTOR_AVERAGE_VERSION";
    private final String SERIALIZED_COUNT = "COUNT";

    public String toString() {
        StringBuffer sb = new StringBuffer();

        //version
        sb.append(SERIALIZED_VERSION +"="+ mVersion +"\n");

        double[] data = new double[getSize()];
        getData(data,false);

        //element count
        int nCount = data.length;
        sb.append(SERIALIZED_COUNT + "=" + nCount +"\n");

        for (int i = 0; i < nCount; i++) {
            sb.append(String.format("%f\n",data[i]));
        }

        return sb.toString();
    }

    public boolean initFromString(String string) {
        boolean success = false;

        String[] lines = string.split(System.getProperty("line.separator"));

        int lineCount = lines.length;
        if (lineCount > 3) {
            int nVersion = -1;
            int nCount = -1;
            int nIndex = 0;

            //search for version:
            while (nIndex < lineCount) {
                String[] separated = lines[nIndex].split("=");
                nIndex++;
                if (separated.length > 1 && separated[0].equalsIgnoreCase(SERIALIZED_VERSION)) {
                    nVersion = Integer.parseInt(separated[1]);
                    break;
                }
            }

            if (nVersion >= 0) {
                //get count

                while (nIndex < lineCount) {
                    String[] separated = lines[nIndex].split("=");
                    nIndex++;
                    if (separated.length > 1 && separated[0].equalsIgnoreCase(SERIALIZED_COUNT)) {
                        nCount = Integer.parseInt(separated[1]);
                        break;
                    }
                }

                if (nCount > 0 && nCount <= lineCount-2 && nCount < 20000) { //foolproof
                    //now add nCount to the vector.
                    double[] data = new double[nCount];
                    int dataIndex=0;

                    while (nIndex < lineCount) {
                        double value = Double.parseDouble(lines[nIndex]);
                        data[dataIndex++] = value;
                        nIndex++;
                    }
                    setData(data, true);
                    success = true;
                }
            }
        }

        return success;
    }

    private static void log(String msg) {
        Log.v(LOGTAG, msg);
    }
}
