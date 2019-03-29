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

public class DspBufferMath {
    private static final String TAG = "DspBufferMath";
    public static final int OPERANDS_TYPE_UNKNOWN  = -1;
    public static final int OPERANDS_TYPE_REAL     = 0;
    public static final int OPERANDS_TYPE_COMPLEX  = 1;
    public static final int OPERANDS_TYPE_MIXED    = 2;

    public static final int MATH_RESULT_UNDEFINED  = -1;
    public static final int MATH_RESULT_SUCCESS    = 0;
    public static final int MATH_RESULT_ERROR      = 1;

    static private<T extends DspBufferBase> int estimateOperandsType(T a, T b) {
        if (a instanceof DspBufferComplex) {
            if (b instanceof DspBufferComplex) {
                return OPERANDS_TYPE_COMPLEX;
            } else if (b instanceof DspBufferDouble) {
                return OPERANDS_TYPE_MIXED;
            }
        } else if (a instanceof DspBufferDouble) {
            if (b instanceof DspBufferComplex) {
                return OPERANDS_TYPE_MIXED;
            } else if (b instanceof DspBufferDouble) {
                return OPERANDS_TYPE_REAL;
            }
        }
        return OPERANDS_TYPE_UNKNOWN;
    }

    /**
     * adds r = a + b; element by element
     *
     * If the result is double vector, the imaginary part of complex operations is ignored.
     */
    static public <T extends DspBufferBase> int add(T r, T a, T b) {
        int size = Math.min(a.getSize(), b.getSize());
        r.setSize(size);

        T x = a;
        T y = b;
        int opType = estimateOperandsType(a, b);

        if (opType == OPERANDS_TYPE_MIXED) {
            if (a instanceof  DspBufferDouble)  {
                x = b; //Complex first
                y = a;
            }
        }

        if (opType == OPERANDS_TYPE_UNKNOWN) {
            return MATH_RESULT_UNDEFINED;
        }

        if (r instanceof DspBufferComplex) {
            switch (opType) {
                case OPERANDS_TYPE_REAL:
                    for (int i = 0; i < size; i++) {
                        ((DspBufferComplex) r).mReal[i] =
                                ((DspBufferDouble) x).mData[i] + ((DspBufferDouble) y).mData[i];
                        ((DspBufferComplex) r).mImag[i] = 0;
                    }
                    return MATH_RESULT_SUCCESS;
                case OPERANDS_TYPE_COMPLEX:
                    for (int i = 0; i < size; i++) {
                        ((DspBufferComplex) r).mReal[i] =
                                ((DspBufferComplex) x).mReal[i] + ((DspBufferComplex) y).mReal[i];
                        ((DspBufferComplex) r).mImag[i] =
                                ((DspBufferComplex) x).mImag[i] + ((DspBufferComplex) y).mImag[i];
                    }
                    return MATH_RESULT_SUCCESS;
                case OPERANDS_TYPE_MIXED:
                    for (int i = 0; i < size; i++) {
                        ((DspBufferComplex) r).mReal[i] =
                                ((DspBufferComplex) x).mReal[i] + ((DspBufferDouble) y).mData[i];
                        ((DspBufferComplex) r).mImag[i] = ((DspBufferComplex) x).mImag[i];
                    }
                    return MATH_RESULT_SUCCESS;
            }
        } else if (r instanceof DspBufferDouble) {
            switch (opType) {
                case OPERANDS_TYPE_REAL:
                    for (int i = 0; i < size; i++) {
                        ((DspBufferDouble) r).mData[i] =
                                ((DspBufferDouble) x).mData[i] + ((DspBufferDouble) y).mData[i];
                    }
                    return MATH_RESULT_SUCCESS;
                case OPERANDS_TYPE_COMPLEX:
                    for (int i = 0; i < size; i++) {
                        ((DspBufferDouble) r).mData[i] =
                                ((DspBufferComplex) x).mReal[i] + ((DspBufferComplex) y).mReal[i];
                    }
                    return MATH_RESULT_SUCCESS;
                case OPERANDS_TYPE_MIXED:
                    for (int i = 0; i < size; i++) {
                        ((DspBufferDouble) r).mData[i] =
                                ((DspBufferComplex) x).mReal[i] + ((DspBufferDouble) y).mData[i];
                    }
                    return MATH_RESULT_SUCCESS;
            }
        }
        return MATH_RESULT_ERROR;
    }

    /**
     * mult r = a * b; element by element
     */
    static public <T extends DspBufferBase> int mult(T r, T a, T b) {
        int size = Math.min(a.getSize(), b.getSize());
        r.setSize(size);

        T x = a;
        T y = b;
        int opType = estimateOperandsType(a, b);

        if (opType == OPERANDS_TYPE_MIXED) {
            if (a instanceof  DspBufferDouble)  {
                x = b; //Complex first
                y = a;
            }
        }

        if (opType == OPERANDS_TYPE_UNKNOWN) {
            return MATH_RESULT_UNDEFINED;
        }

        if (r instanceof DspBufferComplex) {
            switch (opType) {
                case OPERANDS_TYPE_REAL:
                    for (int i = 0; i < size; i++) {
                        ((DspBufferComplex) r).mReal[i] =
                                ((DspBufferDouble) x).mData[i] * ((DspBufferDouble) y).mData[i];
                        ((DspBufferComplex) r).mImag[i] = 0;
                    }
                    return MATH_RESULT_SUCCESS;
                case OPERANDS_TYPE_COMPLEX:
                    for (int i = 0; i < size; i++) {
                        double A = ((DspBufferComplex) x).mReal[i];
                        double B = ((DspBufferComplex) x).mImag[i];
                        double C = ((DspBufferComplex) y).mReal[i];
                        double D = ((DspBufferComplex) y).mImag[i];
                        ((DspBufferComplex) r).mReal[i] = (C * A) - (B * D);
                        ((DspBufferComplex) r).mImag[i] = (C * B) + (A * D);
                    }
                    return MATH_RESULT_SUCCESS;
                case OPERANDS_TYPE_MIXED:
                    for (int i = 0; i < size; i++) {
                        double A = ((DspBufferComplex) x).mReal[i];
                        double B = ((DspBufferComplex) x).mImag[i];
                        double C = ((DspBufferDouble) y).mData[i];
                        //double D = 0;
                        ((DspBufferComplex) r).mReal[i] = C * A;
                        ((DspBufferComplex) r).mImag[i] = C * B;
                    }
                    return MATH_RESULT_SUCCESS;
            }
        } else if (r instanceof DspBufferDouble) {
            switch (opType) {
                case OPERANDS_TYPE_REAL:
                    for (int i = 0; i < size; i++) {
                        ((DspBufferDouble) r).mData[i] =
                                ((DspBufferDouble) x).mData[i] * ((DspBufferDouble) y).mData[i];
                    }
                    return MATH_RESULT_SUCCESS;
                case OPERANDS_TYPE_COMPLEX:
                    for (int i = 0; i < size; i++) {
                        double A = ((DspBufferComplex) x).mReal[i];
                        double B = ((DspBufferComplex) x).mImag[i];
                        double C = ((DspBufferComplex) y).mReal[i];
                        double D = ((DspBufferComplex) y).mImag[i];
                        ((DspBufferDouble) r).mData[i] = (C * A) - (B * D);
                    }
                    return MATH_RESULT_SUCCESS;
                case OPERANDS_TYPE_MIXED:
                    for (int i = 0; i < size; i++) {
                        double A = ((DspBufferComplex) x).mReal[i];
                        double B = ((DspBufferComplex) x).mImag[i];
                        double C = ((DspBufferDouble) y).mData[i];
                        //double D = 0;
                        ((DspBufferDouble) r).mData[i] = C * A;
                    }
                    return MATH_RESULT_SUCCESS;
            }
        }
        return MATH_RESULT_ERROR;
    }

    /**
     * mult r = a * v; element by element
     */
    static public <T extends DspBufferBase> int mult(T r, T a, double v) {
        int size = a.getSize();
        r.setSize(size);

        T x = a;
        int opType = estimateOperandsType(r, a);

        if (opType == OPERANDS_TYPE_UNKNOWN) {
            return MATH_RESULT_UNDEFINED;
        }

        if (r instanceof DspBufferComplex) {
            switch (opType) {
                case OPERANDS_TYPE_MIXED:
                    for (int i = 0; i < size; i++) {
                        ((DspBufferComplex) r).mReal[i] = ((DspBufferDouble) x).mData[i] * v;
                        ((DspBufferComplex) r).mImag[i] = 0;
                    }
                    return MATH_RESULT_SUCCESS;
                case OPERANDS_TYPE_COMPLEX:
                    for (int i = 0; i < size; i++) {
                        ((DspBufferComplex) r).mReal[i] = ((DspBufferComplex) x).mReal[i] * v;
                        ((DspBufferComplex) r).mImag[i] = ((DspBufferComplex) x).mImag[i] * v;
                    }
                    return MATH_RESULT_SUCCESS;
            }
        } else if (r instanceof DspBufferDouble) {
            switch (opType) {
                case OPERANDS_TYPE_REAL:
                    for (int i = 0; i < size; i++) {
                        ((DspBufferDouble) r).mData[i] = ((DspBufferDouble) x).mData[i] * v;
                    }
                    return MATH_RESULT_SUCCESS;
                case OPERANDS_TYPE_MIXED:
                    for (int i = 0; i < size; i++) {
                        ((DspBufferDouble) r).mData[i] = ((DspBufferComplex) x).mReal[i] * v;
                    }
                    return MATH_RESULT_SUCCESS;
            }
        }
        return MATH_RESULT_ERROR;
    }

    /**
     * set r = a ; element by element
     */
    static public <T extends DspBufferBase> int set(T r, T a) {
        int size = a.getSize();
        r.setSize(size);

        T x = a;
        int opType = estimateOperandsType(r, a);

        if (opType == OPERANDS_TYPE_UNKNOWN) {
            return MATH_RESULT_UNDEFINED;
        }

        if (r instanceof DspBufferComplex) {
            switch (opType) {
                case OPERANDS_TYPE_MIXED:
                    for (int i = 0; i < size; i++) {
                        ((DspBufferComplex) r).mReal[i] = ((DspBufferDouble) x).mData[i];
                        ((DspBufferComplex) r).mImag[i] = 0;
                    }
                    return MATH_RESULT_SUCCESS;
                case OPERANDS_TYPE_COMPLEX:
                    for (int i = 0; i < size; i++) {
                        ((DspBufferComplex) r).mReal[i] = ((DspBufferComplex) x).mReal[i];
                        ((DspBufferComplex) r).mImag[i] = ((DspBufferComplex) x).mImag[i];
                    }
                    return MATH_RESULT_SUCCESS;
            }
        } else if (r instanceof DspBufferDouble) {
            switch(opType) {
                case OPERANDS_TYPE_REAL:
                    for (int i = 0; i < size; i++) {
                        ((DspBufferDouble) r).mData[i] = ((DspBufferDouble) x).mData[i];
                    }
                    return MATH_RESULT_SUCCESS;
                case OPERANDS_TYPE_MIXED:
                    for (int i = 0; i < size; i++) {
                        ((DspBufferDouble) r).mData[i] = ((DspBufferComplex) x).mReal[i];
                    }
                    return MATH_RESULT_SUCCESS;
            }
        }
        return MATH_RESULT_ERROR;
    }


    /**
     * set r = v ; all elements the same
     * It keeps the size of the return vector
     */
    static public <T extends DspBufferBase> int set(T r, double ...values) {
        int size = r.getSize();

        double a = 0;
        double b = 0;
        if (values.length > 0) {
            a = values[0];
        }
        if (values.length > 1) {
            b = values[1];
        }

        if (r instanceof DspBufferComplex) {
            for (int i = 0; i < size; i++) {
                ((DspBufferComplex) r).mReal[i] = a;
                ((DspBufferComplex) r).mImag[i] = b;
            }
            return MATH_RESULT_SUCCESS;
        } else if (r instanceof DspBufferDouble) {
            for (int i = 0; i < size; i++) {
                ((DspBufferDouble) r).mData[i] = a;
            }
            return MATH_RESULT_SUCCESS;
        }
        return MATH_RESULT_ERROR;
    }
}
