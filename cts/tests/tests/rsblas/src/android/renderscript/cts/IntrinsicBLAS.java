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

package android.cts.rsblas;

import android.renderscript.*;
import android.util.Log;
import java.util.ArrayList;

public class IntrinsicBLAS extends IntrinsicBase {
    private ScriptIntrinsicBLAS mBLAS;
    private BLASData mBLASData;
    private boolean mInitialized = false;

    private ArrayList<Allocation> mMatrixS;
    private final float alphaS = 1.0f;
    private final float betaS = 1.0f;

    private ArrayList<Allocation> mMatrixD;
    private final double alphaD = 1.0;
    private final double betaD = 1.0;

    private ArrayList<Allocation> mMatrixC;
    private final Float2 alphaC = new Float2(1.0f, 0.0f);
    private final Float2 betaC = new Float2(1.0f, 0.0f);

    private ArrayList<Allocation> mMatrixZ;
    private final Double2 alphaZ = new Double2(1.0, 0.0);
    private final Double2 betaZ = new Double2(1.0, 0.0);

    private int[] mTranspose = {ScriptIntrinsicBLAS.NO_TRANSPOSE,
                                ScriptIntrinsicBLAS.TRANSPOSE,
                                ScriptIntrinsicBLAS.CONJ_TRANSPOSE,
                                0};

    private int[] mUplo = {ScriptIntrinsicBLAS.UPPER,
                           ScriptIntrinsicBLAS.LOWER,
                           0};

    private int[] mDiag = {ScriptIntrinsicBLAS.NON_UNIT,
                           ScriptIntrinsicBLAS.UNIT,
                           0};

    private int[] mSide = {ScriptIntrinsicBLAS.LEFT,
                           ScriptIntrinsicBLAS.RIGHT,
                           0};

    private int[] mInc = {0, 1, 2};
    private int[] mK = {-1, 0, 1};
    private int[] mDim = {1, 2, 3, 256};

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Now populate the test Matrixes and Vectors.
        if (!mInitialized) {
            mBLASData = new BLASData();
            mBLASData.loadData(mCtx);
            mBLAS = ScriptIntrinsicBLAS.create(mRS);
            mMatrixS = new ArrayList<Allocation>();
            mMatrixD = new ArrayList<Allocation>();
            mMatrixC = new ArrayList<Allocation>();
            mMatrixZ = new ArrayList<Allocation>();
            for (int x : mDim) {
                for (int y : mDim) {
                    mMatrixS.add(Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), x, y)));
                    mMatrixD.add(Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), x, y)));
                    mMatrixC.add(Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), x, y)));
                    mMatrixZ.add(Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), x, y)));
                }
            }
            // Also need Allocation with mismatch Element.
            Allocation misAlloc = Allocation.createTyped(mRS, Type.createXY(mRS, Element.U8(mRS), 1, 1));
            mMatrixS.add(misAlloc);
            mMatrixD.add(misAlloc);
            mMatrixC.add(misAlloc);
            mMatrixZ.add(misAlloc);
            mInitialized = true;
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    // Calculate the square of the L2 norm of a matrix.
    private double calcL2Norm(float[] input) {
        double l2Norm = 0;
        for (int i = 0; i < input.length; ++i) {
            l2Norm += input[i] * input[i];
        }
        return l2Norm;
    }

    private double calcL2Norm(double[] input) {
        double l2Norm = 0;
        for (int i = 0; i < input.length; ++i) {
            l2Norm += input[i] * input[i];
        }
        return l2Norm;
    }

    // Routine to verify if matrix are equivalent.
    private void verifyMatrix(Allocation ref, Allocation out) {
        verifyMatrix(ref, out, false);
    }

    // Use L2 norm of a matrix as the scale to determine whether two matrices are equivalent:
    // if the absolute square error of any elements is smaller than the average L2 Norm
    // per element times an allowed error range (1e-6), then the two matrices are considered equivalent.
    // Criterion: (a[i,j] - a'[i,j])^2 < epsilon * ||A||/(M*N)
    // M, N: the dimensions of the matrix; epsilon: allowed relative error.
    private void verifyMatrix(Allocation ref, Allocation out, boolean isUpperMatrix) {
        double l2Norm;
        int size;
        Element e = ref.getType().getElement();
        if (e.isCompatible(Element.F32(mRS)) || e.isCompatible(Element.F32_2(mRS))) {
            size = out.getBytesSize() / 4;
            float[] outArr = new float[size];
            float[] refArr = new float[size];
            out.copyTo(outArr);
            ref.copyTo(refArr);

            double l2NormOut = calcL2Norm(outArr);
            double l2NormRef = calcL2Norm(refArr);
            l2Norm = (l2NormOut < l2NormRef ? l2NormOut : l2NormRef) / size;
        } else {
            size = out.getBytesSize() / 8;
            double[] outArr = new double[size];
            double[] refArr = new double[size];
            out.copyTo(outArr);
            ref.copyTo(refArr);

            double l2NormOut = calcL2Norm(outArr);
            double l2NormRef = calcL2Norm(refArr);
            l2Norm = (l2NormOut < l2NormRef ? l2NormOut : l2NormRef) / size;
        }
        mVerify.invoke_verifyMatrix(ref, out, l2Norm, isUpperMatrix);
    }


    private boolean validateSide(int Side) {
        if (Side != ScriptIntrinsicBLAS.LEFT && Side != ScriptIntrinsicBLAS.RIGHT) {
            return false;
        }
        return true;
    }

    private boolean validateTranspose(int Trans) {
        if (Trans != ScriptIntrinsicBLAS.NO_TRANSPOSE &&
            Trans != ScriptIntrinsicBLAS.TRANSPOSE &&
            Trans != ScriptIntrinsicBLAS.CONJ_TRANSPOSE) {
            return false;
        }
        return true;
    }

    private boolean validateConjTranspose(int Trans) {
        if (Trans != ScriptIntrinsicBLAS.NO_TRANSPOSE &&
            Trans != ScriptIntrinsicBLAS.CONJ_TRANSPOSE) {
            return false;
        }
        return true;
    }

    private boolean validateDiag(int Diag) {
        if (Diag != ScriptIntrinsicBLAS.NON_UNIT &&
            Diag != ScriptIntrinsicBLAS.UNIT) {
            return false;
        }
        return true;
    }

    private boolean validateUplo(int Uplo) {
        if (Uplo != ScriptIntrinsicBLAS.UPPER &&
            Uplo != ScriptIntrinsicBLAS.LOWER) {
            return false;
        }
        return true;
    }

    private boolean validateVecInput(Allocation X) {
        if (X.getType().getY() > 2) {
            // For testing vector, need a mismatch Y for complete test coverage.
            return false;
        }
        return true;
    }

    private boolean validateGEMV(Element e, int TransA, Allocation A, Allocation X, int incX, Allocation Y, int incY) {
        if (!validateTranspose(TransA)) {
            return false;
        }
        int M = A.getType().getY();
        int N = A.getType().getX();
        if (!A.getType().getElement().isCompatible(e) ||
            !X.getType().getElement().isCompatible(e) ||
            !Y.getType().getElement().isCompatible(e)) {
            return false;
        }
        if (X.getType().getY() > 1 || Y.getType().getY() > 1) {
            return false;
        }

        if (incX <= 0 || incY <= 0) {
            return false;
        }
        int expectedXDim = -1, expectedYDim = -1;
        if (TransA == ScriptIntrinsicBLAS.NO_TRANSPOSE) {
            expectedXDim = 1 + (N - 1) * incX;
            expectedYDim = 1 + (M - 1) * incY;
        } else {
            expectedXDim = 1 + (M - 1) * incX;
            expectedYDim = 1 + (N - 1) * incY;
        }
        if (X.getType().getX() != expectedXDim ||
            Y.getType().getX() != expectedYDim) {
            return false;
        }
        return true;
    }

    private void xGEMV_API_test(int trans, int incX, int incY, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                for (Allocation vecY : mMatrix) {
                    if (!validateVecInput(vecY)) {
                        continue;
                    }
                    Element elemA = matA.getType().getElement();
                    if (validateGEMV(elemA, trans, matA, vecX, incX, vecY, incY)) {
                        try {
                            if (elemA.isCompatible(Element.F32(mRS))) {
                                mBLAS.SGEMV(trans, alphaS, matA, vecX, incX, betaS, vecY, incY);
                            } else if (elemA.isCompatible(Element.F64(mRS))) {
                                mBLAS.DGEMV(trans, alphaD, matA, vecX, incX, betaD, vecY, incY);
                            } else if (elemA.isCompatible(Element.F32_2(mRS))) {
                                mBLAS.CGEMV(trans, alphaC, matA, vecX, incX, betaC, vecY, incY);
                            } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                                mBLAS.ZGEMV(trans, alphaZ, matA, vecX, incX, betaZ, vecY, incY);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.SGEMV(trans, alphaS, matA, vecX, incX, betaS, vecY, incY);
                            fail("should throw RSRuntimeException for SGEMV");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.DGEMV(trans, alphaD, matA, vecX, incX, betaD, vecY, incY);
                            fail("should throw RSRuntimeException for DGEMV");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.CGEMV(trans, alphaC, matA, vecX, incX, betaC, vecY, incY);
                            fail("should throw RSRuntimeException for CGEMV");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.ZGEMV(trans, alphaZ, matA, vecX, incX, betaZ, vecY, incY);
                            fail("should throw RSRuntimeException for ZGEMV");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    public void L2_xGEMV_API(ArrayList<Allocation> mMatrix) {
        for (int trans : mTranspose) {
            for (int incX : mInc) {
                xGEMV_API_test(trans, incX, incX, mMatrix);
            }
        }
    }

    public void test_L2_SGEMV_API() {
        L2_xGEMV_API(mMatrixS);
    }

    public void test_L2_DGEMV_API() {
        L2_xGEMV_API(mMatrixD);
    }

    public void test_L2_CGEMV_API() {
        L2_xGEMV_API(mMatrixC);
    }

    public void test_L2_ZGEMV_API() {
        L2_xGEMV_API(mMatrixZ);
    }

    public void test_L2_SGEMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dM));
        Allocation vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        Allocation vectorYS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dM, 1));
        matrixAS.copyFrom(mBLASData.L2_sGEMV_A_mn);
        vectorXS.copyFrom(mBLASData.L2_sGEMV_x_n1);
        vectorYS.copyFrom(mBLASData.L2_sGEMV_y_m1);

        // Test for the default case: NO_TRANS
        mBLAS.SGEMV(trans, alphaS, matrixAS, vectorXS, incX, betaS, vectorYS, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dM, 1));
        vectorYRef.copyFrom(mBLASData.L2_sGEMV_o_N);
        verifyMatrix(vectorYRef, vectorYS);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector Y, since it was overwritten by BLAS.
        vectorYS.copyFrom(mBLASData.L2_sGEMV_y_m1);
        // After Transpose matrixA, vectorX and vectorY are exchanged to match the dim of A.T
        mBLAS.SGEMV(trans, alphaS, matrixAS, vectorYS, incY, betaS, vectorXS, incX);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        vectorYRef.copyFrom(mBLASData.L2_sGEMV_o_T);
        verifyMatrix(vectorYRef, vectorXS);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXS.copyFrom(mBLASData.L2_sGEMV_x_n1);
        mBLAS.SGEMV(trans, alphaS, matrixAS, vectorYS, incY, betaS, vectorXS, incX);
        vectorYRef.copyFrom(mBLASData.L2_sGEMV_o_H);
        verifyMatrix(vectorYRef, vectorXS);

        // Test for incX = 2 & incY = 3;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dM - 1) * incY;
        vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorYS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimY, 1));
        vectorXS.copyFrom(mBLASData.L2_sGEMV_x_n2);
        vectorYS.copyFrom(mBLASData.L2_sGEMV_y_m2);

        mBLAS.SGEMV(trans, alphaS, matrixAS, vectorXS, incX, betaS, vectorYS, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_sGEMV_o_N2);
        verifyMatrix(vectorYRef, vectorYS);

        mRS.finish();
        checkError();
    }

    public void test_L2_DGEMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dM));
        Allocation vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        Allocation vectorYD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dM, 1));
        matrixAD.copyFrom(mBLASData.L2_dGEMV_A_mn);
        vectorXD.copyFrom(mBLASData.L2_dGEMV_x_n1);
        vectorYD.copyFrom(mBLASData.L2_dGEMV_y_m1);

        // Test for the default case: NO_TRANS
        mBLAS.DGEMV(trans, alphaD, matrixAD, vectorXD, incX, betaD, vectorYD, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dM, 1));
        vectorYRef.copyFrom(mBLASData.L2_dGEMV_o_N);
        verifyMatrix(vectorYRef, vectorYD);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector Y, since it was overwritten by BLAS.
        vectorYD.copyFrom(mBLASData.L2_dGEMV_y_m1);
        // After Transpose matrixA, vectorX and vectorY are exchanged to match the dim of A.T
        mBLAS.DGEMV(trans, alphaD, matrixAD, vectorYD, incY, betaD, vectorXD, incX);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        vectorYRef.copyFrom(mBLASData.L2_dGEMV_o_T);
        verifyMatrix(vectorYRef, vectorXD);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXD.copyFrom(mBLASData.L2_dGEMV_x_n1);
        mBLAS.DGEMV(trans, alphaD, matrixAD, vectorYD, incY, betaD, vectorXD, incX);
        vectorYRef.copyFrom(mBLASData.L2_dGEMV_o_H);
        verifyMatrix(vectorYRef, vectorXD);

        // Test for incX = 2 & incY = 3;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dM - 1) * incY;
        vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorYD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimY, 1));
        vectorXD.copyFrom(mBLASData.L2_dGEMV_x_n2);
        vectorYD.copyFrom(mBLASData.L2_dGEMV_y_m2);

        mBLAS.DGEMV(trans, alphaD, matrixAD, vectorXD, incX, betaD, vectorYD, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_dGEMV_o_N2);
        verifyMatrix(vectorYRef, vectorYD);

        mRS.finish();
        checkError();
    }

    public void test_L2_CGEMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dM));
        Allocation vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        Allocation vectorYC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dM, 1));
        matrixAC.copyFrom(mBLASData.L2_cGEMV_A_mn);
        vectorXC.copyFrom(mBLASData.L2_cGEMV_x_n1);
        vectorYC.copyFrom(mBLASData.L2_cGEMV_y_m1);

        // Test for the default case: NO_TRANS
        mBLAS.CGEMV(trans, alphaC, matrixAC, vectorXC, incX, betaC, vectorYC, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dM, 1));
        vectorYRef.copyFrom(mBLASData.L2_cGEMV_o_N);
        verifyMatrix(vectorYRef, vectorYC);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector Y, since it was overwritten by BLAS.
        vectorYC.copyFrom(mBLASData.L2_cGEMV_y_m1);
        // After Transpose matrixA, vectorX and vectorY are exchanged to match the dim of A.T
        mBLAS.CGEMV(trans, alphaC, matrixAC, vectorYC, incY, betaC, vectorXC, incX);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        vectorYRef.copyFrom(mBLASData.L2_cGEMV_o_T);
        verifyMatrix(vectorYRef, vectorXC);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXC.copyFrom(mBLASData.L2_cGEMV_x_n1);
        mBLAS.CGEMV(trans, alphaC, matrixAC, vectorYC, incY, betaC, vectorXC, incX);
        vectorYRef.copyFrom(mBLASData.L2_cGEMV_o_H);
        verifyMatrix(vectorYRef, vectorXC);

        // Test for incX = 2 & incY = 3;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dM - 1) * incY;
        vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorYC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimY, 1));
        vectorXC.copyFrom(mBLASData.L2_cGEMV_x_n2);
        vectorYC.copyFrom(mBLASData.L2_cGEMV_y_m2);

        mBLAS.CGEMV(trans, alphaC, matrixAC, vectorXC, incX, betaC, vectorYC, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_cGEMV_o_N2);
        verifyMatrix(vectorYRef, vectorYC);

        mRS.finish();
        checkError();
    }

    public void test_L2_ZGEMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dM));
        Allocation vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        Allocation vectorYZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dM, 1));
        matrixAZ.copyFrom(mBLASData.L2_zGEMV_A_mn);
        vectorXZ.copyFrom(mBLASData.L2_zGEMV_x_n1);
        vectorYZ.copyFrom(mBLASData.L2_zGEMV_y_m1);

        // Test for the default case: NO_TRANS
        mBLAS.ZGEMV(trans, alphaZ, matrixAZ, vectorXZ, incX, betaZ, vectorYZ, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dM, 1));
        vectorYRef.copyFrom(mBLASData.L2_zGEMV_o_N);
        verifyMatrix(vectorYRef, vectorYZ);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector Y, since it was overwritten by BLAS.
        vectorYZ.copyFrom(mBLASData.L2_zGEMV_y_m1);
        // After Transpose matrixA, vectorX and vectorY are exchanged to match the dim of A.T
        mBLAS.ZGEMV(trans, alphaZ, matrixAZ, vectorYZ, incY, betaZ, vectorXZ, incX);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        vectorYRef.copyFrom(mBLASData.L2_zGEMV_o_T);
        verifyMatrix(vectorYRef, vectorXZ);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXZ.copyFrom(mBLASData.L2_zGEMV_x_n1);
        mBLAS.ZGEMV(trans, alphaZ, matrixAZ, vectorYZ, incY, betaZ, vectorXZ, incX);
        vectorYRef.copyFrom(mBLASData.L2_zGEMV_o_H);
        verifyMatrix(vectorYRef, vectorXZ);

        // Test for incX = 2 & incY = 3;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dM - 1) * incY;
        vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorYZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimY, 1));
        vectorXZ.copyFrom(mBLASData.L2_zGEMV_x_n2);
        vectorYZ.copyFrom(mBLASData.L2_zGEMV_y_m2);

        mBLAS.ZGEMV(trans, alphaZ, matrixAZ, vectorXZ, incX, betaZ, vectorYZ, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_zGEMV_o_N2);
        verifyMatrix(vectorYRef, vectorYZ);

        mRS.finish();
        checkError();
    }



    private void xGBMV_API_test(int trans, int KL, int KU, int incX, int incY, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                for (Allocation vecY : mMatrix) {
                    if (!validateVecInput(vecY)) {
                        continue;
                    }
                    Element elemA = matA.getType().getElement();
                    if (validateGEMV(elemA, trans, matA, vecX, incX, vecY, incY) && KU >= 0 && KL >= 0) {
                        try {
                            if (elemA.isCompatible(Element.F32(mRS))) {
                                mBLAS.SGBMV(trans, KL, KU, alphaS, matA, vecX, incX, betaS, vecY, incY);
                            } else if (elemA.isCompatible(Element.F64(mRS))) {
                                mBLAS.DGBMV(trans, KL, KU, alphaD, matA, vecX, incX, betaD, vecY, incY);
                            } else if (elemA.isCompatible(Element.F32_2(mRS))) {
                                mBLAS.CGBMV(trans, KL, KU, alphaC, matA, vecX, incX, betaC, vecY, incY);
                            } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                                mBLAS.ZGBMV(trans, KL, KU, alphaZ, matA, vecX, incX, betaZ, vecY, incY);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.SGBMV(trans, KL, KU, alphaS, matA, vecX, incX, betaS, vecY, incY);
                            fail("should throw RSRuntimeException for SGBMV");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.DGBMV(trans, KL, KU, alphaD, matA, vecX, incX, betaD, vecY, incY);
                            fail("should throw RSRuntimeException for DGBMV");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.CGBMV(trans, KL, KU, alphaC, matA, vecX, incX, betaC, vecY, incY);
                            fail("should throw RSRuntimeException for CGBMV");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.ZGBMV(trans, KL, KU, alphaZ, matA, vecX, incX, betaZ, vecY, incY);
                            fail("should throw RSRuntimeException for ZGBMV");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    public void L2_xGBMV_API(ArrayList<Allocation> mMatrix) {
        for (int trans : mTranspose) {
            for (int incX : mInc) {
                for (int K : mK) {
                    xGBMV_API_test(trans, K, K, incX, incX, mMatrix);
                }
            }
        }
    }

    public void test_L2_SGBMV_API() {
        L2_xGBMV_API(mMatrixS);
    }

    public void test_L2_DGBMV_API() {
        L2_xGBMV_API(mMatrixD);
    }

    public void test_L2_CGBMV_API() {
        L2_xGBMV_API(mMatrixC);
    }

    public void test_L2_ZGBMV_API() {
        L2_xGBMV_API(mMatrixZ);
    }

    public void test_L2_SGBMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dM));
        Allocation vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        Allocation vectorYS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dM, 1));
        matrixAS.copy2DRangeFrom(0, 0, mBLASData.KL + mBLASData.KU + 1, mBLASData.dM, mBLASData.L2_sGBMV_A_mn);
        vectorXS.copyFrom(mBLASData.L2_sGBMV_x_n1);
        vectorYS.copyFrom(mBLASData.L2_sGBMV_y_m1);

        // Test for the default case: NO_TRANS
        mBLAS.SGBMV(trans, mBLASData.KL, mBLASData.KU, alphaS, matrixAS, vectorXS, incX, betaS, vectorYS, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dM, 1));
        vectorYRef.copyFrom(mBLASData.L2_sGBMV_o_N);
        verifyMatrix(vectorYRef, vectorYS);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector Y, since it was overwritten by BLAS.
        vectorYS.copyFrom(mBLASData.L2_sGBMV_y_m1);
        // After Transpose matrixA, vectorX and vectorY are exchanged to match the dim of A.T
        mBLAS.SGBMV(trans, mBLASData.KL, mBLASData.KU, alphaS, matrixAS, vectorYS, incY, betaS, vectorXS, incX);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        vectorYRef.copyFrom(mBLASData.L2_sGBMV_o_T);
        verifyMatrix(vectorYRef, vectorXS);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXS.copyFrom(mBLASData.L2_sGBMV_x_n1);
        mBLAS.SGBMV(trans, mBLASData.KL, mBLASData.KU, alphaS, matrixAS, vectorYS, incY, betaS, vectorXS, incX);
        vectorYRef.copyFrom(mBLASData.L2_sGBMV_o_H);
        verifyMatrix(vectorYRef, vectorXS);

        // Test for incX = 2 & incY = 3;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dM - 1) * incY;
        vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorYS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimY, 1));
        vectorXS.copyFrom(mBLASData.L2_sGBMV_x_n2);
        vectorYS.copyFrom(mBLASData.L2_sGBMV_y_m2);

        mBLAS.SGBMV(trans, mBLASData.KL, mBLASData.KU, alphaS, matrixAS, vectorXS, incX, betaS, vectorYS, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_sGBMV_o_N2);
        verifyMatrix(vectorYRef, vectorYS);

        mRS.finish();
        checkError();
    }

    public void test_L2_DGBMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dM));
        Allocation vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        Allocation vectorYD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dM, 1));
        matrixAD.copy2DRangeFrom(0, 0, mBLASData.KL + mBLASData.KU + 1, mBLASData.dM, mBLASData.L2_dGBMV_A_mn);
        vectorXD.copyFrom(mBLASData.L2_dGBMV_x_n1);
        vectorYD.copyFrom(mBLASData.L2_dGBMV_y_m1);

        // Test for the default case: NO_TRANS
        mBLAS.DGBMV(trans, mBLASData.KL, mBLASData.KU, alphaD, matrixAD, vectorXD, incX, betaD, vectorYD, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dM, 1));
        vectorYRef.copyFrom(mBLASData.L2_dGBMV_o_N);
        verifyMatrix(vectorYRef, vectorYD);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector Y, since it was overwritten by BLAS.
        vectorYD.copyFrom(mBLASData.L2_dGBMV_y_m1);
        // After Transpose matrixA, vectorX and vectorY are exchanged to match the dim of A.T
        mBLAS.DGBMV(trans, mBLASData.KL, mBLASData.KU, alphaD, matrixAD, vectorYD, incY, betaD, vectorXD, incX);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        vectorYRef.copyFrom(mBLASData.L2_dGBMV_o_T);
        verifyMatrix(vectorYRef, vectorXD);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXD.copyFrom(mBLASData.L2_dGBMV_x_n1);
        mBLAS.DGBMV(trans, mBLASData.KL, mBLASData.KU, alphaD, matrixAD, vectorYD, incY, betaD, vectorXD, incX);
        vectorYRef.copyFrom(mBLASData.L2_dGBMV_o_H);
        verifyMatrix(vectorYRef, vectorXD);

        // Test for incX = 2 & incY = 3;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dM - 1) * incY;
        vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorYD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimY, 1));
        vectorXD.copyFrom(mBLASData.L2_dGBMV_x_n2);
        vectorYD.copyFrom(mBLASData.L2_dGBMV_y_m2);

        mBLAS.DGBMV(trans, mBLASData.KL, mBLASData.KU, alphaD, matrixAD, vectorXD, incX, betaD, vectorYD, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_dGBMV_o_N2);
        verifyMatrix(vectorYRef, vectorYD);

        mRS.finish();
        checkError();
    }

    public void test_L2_CGBMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dM));
        Allocation vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        Allocation vectorYC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dM, 1));
        matrixAC.copy2DRangeFrom(0, 0, mBLASData.KL + mBLASData.KU + 1, mBLASData.dM, mBLASData.L2_cGBMV_A_mn);
        vectorXC.copyFrom(mBLASData.L2_cGBMV_x_n1);
        vectorYC.copyFrom(mBLASData.L2_cGBMV_y_m1);

        // Test for the default case: NO_TRANS
        mBLAS.CGBMV(trans, mBLASData.KL, mBLASData.KU, alphaC, matrixAC, vectorXC, incX, betaC, vectorYC, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dM, 1));
        vectorYRef.copyFrom(mBLASData.L2_cGBMV_o_N);
        verifyMatrix(vectorYRef, vectorYC);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector Y, since it was overwritten by BLAS.
        vectorYC.copyFrom(mBLASData.L2_cGBMV_y_m1);
        // After Transpose matrixA, vectorX and vectorY are exchanged to match the dim of A.T
        mBLAS.CGBMV(trans, mBLASData.KL, mBLASData.KU, alphaC, matrixAC, vectorYC, incY, betaC, vectorXC, incX);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        vectorYRef.copyFrom(mBLASData.L2_cGBMV_o_T);
        verifyMatrix(vectorYRef, vectorXC);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXC.copyFrom(mBLASData.L2_cGBMV_x_n1);
        mBLAS.CGBMV(trans, mBLASData.KL, mBLASData.KU, alphaC, matrixAC, vectorYC, incY, betaC, vectorXC, incX);
        vectorYRef.copyFrom(mBLASData.L2_cGBMV_o_H);
        verifyMatrix(vectorYRef, vectorXC);

        // Test for incX = 2 & incY = 3;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dM - 1) * incY;
        vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorYC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimY, 1));
        vectorXC.copyFrom(mBLASData.L2_cGBMV_x_n2);
        vectorYC.copyFrom(mBLASData.L2_cGBMV_y_m2);

        mBLAS.CGBMV(trans, mBLASData.KL, mBLASData.KU, alphaC, matrixAC, vectorXC, incX, betaC, vectorYC, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_cGBMV_o_N2);
        verifyMatrix(vectorYRef, vectorYC);

        mRS.finish();
        checkError();
    }

    public void test_L2_ZGBMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dM));
        Allocation vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        Allocation vectorYZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dM, 1));
        matrixAZ.copy2DRangeFrom(0, 0, mBLASData.KL + mBLASData.KU + 1, mBLASData.dM, mBLASData.L2_zGBMV_A_mn);
        vectorXZ.copyFrom(mBLASData.L2_zGBMV_x_n1);
        vectorYZ.copyFrom(mBLASData.L2_zGBMV_y_m1);

        // Test for the default case: NO_TRANS
        mBLAS.ZGBMV(trans, mBLASData.KL, mBLASData.KU, alphaZ, matrixAZ, vectorXZ, incX, betaZ, vectorYZ, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dM, 1));
        vectorYRef.copyFrom(mBLASData.L2_zGBMV_o_N);
        verifyMatrix(vectorYRef, vectorYZ);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector Y, since it was overwritten by BLAS.
        vectorYZ.copyFrom(mBLASData.L2_zGBMV_y_m1);
        // After Transpose matrixA, vectorX and vectorY are exchanged to match the dim of A.T
        mBLAS.ZGBMV(trans, mBLASData.KL, mBLASData.KU, alphaZ, matrixAZ, vectorYZ, incY, betaZ, vectorXZ, incX);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        vectorYRef.copyFrom(mBLASData.L2_zGBMV_o_T);
        verifyMatrix(vectorYRef, vectorXZ);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXZ.copyFrom(mBLASData.L2_zGBMV_x_n1);
        mBLAS.ZGBMV(trans, mBLASData.KL, mBLASData.KU, alphaZ, matrixAZ, vectorYZ, incX, betaZ, vectorXZ, incY);
        vectorYRef.copyFrom(mBLASData.L2_zGBMV_o_H);
        verifyMatrix(vectorYRef, vectorXZ);

        // Test for incX = 2 & incY = 3;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dM - 1) * incY;
        vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorYZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimY, 1));
        vectorXZ.copyFrom(mBLASData.L2_zGBMV_x_n2);
        vectorYZ.copyFrom(mBLASData.L2_zGBMV_y_m2);

        mBLAS.ZGBMV(trans, mBLASData.KL, mBLASData.KU, alphaZ, matrixAZ, vectorXZ, incX, betaZ, vectorYZ, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_zGBMV_o_N2);
        verifyMatrix(vectorYRef, vectorYZ);

        mRS.finish();
        checkError();
    }


    private void xHEMV_API_test(int Uplo, int incX, int incY, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                for (Allocation vecY : mMatrix) {
                    if (!validateVecInput(vecY)) {
                        continue;
                    }
                    Element elemA = matA.getType().getElement();
                    if (validateSYR2(elemA, Uplo, vecX, incX, vecY, incY, matA)) {
                        try {
                            if (elemA.isCompatible(Element.F32_2(mRS))) {
                                mBLAS.CHEMV(Uplo, alphaC, matA, vecX, incX, betaC, vecY, incY);
                            } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                                mBLAS.ZHEMV(Uplo, alphaZ, matA, vecX, incX, betaZ, vecY, incY);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.CHEMV(Uplo, alphaC, matA, vecX, incX, betaC, vecY, incY);
                            fail("should throw RSRuntimeException for CHEMV");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.ZHEMV(Uplo, alphaZ, matA, vecX, incX, betaZ, vecY, incY);
                            fail("should throw RSRuntimeException for ZHEMV");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    public void L2_xHEMV_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int incX : mInc) {
                xHEMV_API_test(Uplo, incX, incX, mMatrix);
            }
        }
    }

    public void test_L2_CHEMV_API() {
        L2_xHEMV_API(mMatrixC);
    }

    public void test_L2_ZHEMV_API() {
        L2_xHEMV_API(mMatrixZ);
    }

    public void test_L2_CHEMV_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        Allocation vectorYC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        matrixAC.copyFrom(mBLASData.L2_cHEMV_A_nn);
        vectorXC.copyFrom(mBLASData.L2_cHEMV_x_n1);
        vectorYC.copyFrom(mBLASData.L2_cHEMV_y_n1);

        // Test for the default case:
        mBLAS.CHEMV(uplo, alphaC, matrixAC, vectorXC, incX, betaC, vectorYC, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        vectorYRef.copyFrom(mBLASData.L2_cHEMV_o_N);
        verifyMatrix(vectorYRef, vectorYC);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dN - 1) * incY;
        vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorYC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimY, 1));
        vectorXC.copyFrom(mBLASData.L2_cHEMV_x_n2);
        vectorYC.copyFrom(mBLASData.L2_cHEMV_y_n2);

        mBLAS.CHEMV(uplo, alphaC, matrixAC, vectorXC, incX, betaC, vectorYC, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_cHEMV_o_N2);
        verifyMatrix(vectorYRef, vectorYC);

        mRS.finish();
        checkError();
    }

    public void test_L2_ZHEMV_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        Allocation vectorYZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        matrixAZ.copyFrom(mBLASData.L2_zHEMV_A_nn);
        vectorXZ.copyFrom(mBLASData.L2_zHEMV_x_n1);
        vectorYZ.copyFrom(mBLASData.L2_zHEMV_y_n1);

        // Test for the default case: NO_TRANS
        mBLAS.ZHEMV(uplo, alphaZ, matrixAZ, vectorXZ, incX, betaZ, vectorYZ, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        vectorYRef.copyFrom(mBLASData.L2_zHEMV_o_N);
        verifyMatrix(vectorYRef, vectorYZ);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dN - 1) * incY;
        vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorYZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimY, 1));
        vectorXZ.copyFrom(mBLASData.L2_zHEMV_x_n2);
        vectorYZ.copyFrom(mBLASData.L2_zHEMV_y_n2);

        mBLAS.ZHEMV(uplo, alphaZ, matrixAZ, vectorXZ, incX, betaZ, vectorYZ, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_zHEMV_o_N2);
        verifyMatrix(vectorYRef, vectorYZ);

        mRS.finish();
        checkError();
    }



    private void xHBMV_API_test(int Uplo, int K, int incX, int incY, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                for (Allocation vecY : mMatrix) {
                    if (!validateVecInput(vecY)) {
                        continue;
                    }
                    Element elemA = matA.getType().getElement();
                    if (validateSYR2(elemA, Uplo, vecX, incX, vecY, incY, matA) && K >= 0) {
                        try {
                            if (elemA.isCompatible(Element.F32_2(mRS))) {
                                mBLAS.CHBMV(Uplo, K, alphaC, matA, vecX, incX, betaC, vecY, incY);
                            } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                                mBLAS.ZHBMV(Uplo, K, alphaZ, matA, vecX, incX, betaZ, vecY, incY);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.CHBMV(Uplo, K, alphaC, matA, vecX, incX, betaC, vecY, incY);
                            fail("should throw RSRuntimeException for CHBMV");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.ZHBMV(Uplo, K, alphaZ, matA, vecX, incX, betaZ, vecY, incY);
                            fail("should throw RSRuntimeException for ZHBMV");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    public void L2_xHBMV_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int K : mK) {
                for (int incX : mInc) {
                        xHBMV_API_test(Uplo, K, incX, incX, mMatrix);
                }
            }
        }
    }

    public void test_L2_CHBMV_API() {
        L2_xHBMV_API(mMatrixC);
    }

    public void test_L2_ZHBMV_API() {
        L2_xHBMV_API(mMatrixZ);
    }

    public void test_L2_CHBMV_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        Allocation vectorYC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        matrixAC.copy2DRangeFrom(0, 0, mBLASData.KL + 1, mBLASData.dN, mBLASData.L2_cHBMV_A_nn);
        vectorXC.copyFrom(mBLASData.L2_cHBMV_x_n1);
        vectorYC.copyFrom(mBLASData.L2_cHBMV_y_n1);

        // Test for the default case:
        mBLAS.CHBMV(uplo, mBLASData.KL, alphaC, matrixAC, vectorXC, incX, betaC, vectorYC, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        vectorYRef.copyFrom(mBLASData.L2_cHBMV_o_N);
        verifyMatrix(vectorYRef, vectorYC);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dN - 1) * incY;
        vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorYC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimY, 1));
        vectorXC.copyFrom(mBLASData.L2_cHBMV_x_n2);
        vectorYC.copyFrom(mBLASData.L2_cHBMV_y_n2);

        mBLAS.CHBMV(uplo, mBLASData.KL, alphaC, matrixAC, vectorXC, incX, betaC, vectorYC, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_cHBMV_o_N2);
        verifyMatrix(vectorYRef, vectorYC);

        mRS.finish();
        checkError();
    }

    public void test_L2_ZHBMV_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        Allocation vectorYZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        matrixAZ.copy2DRangeFrom(0, 0, mBLASData.KL + 1, mBLASData.dN, mBLASData.L2_zHBMV_A_nn);
        vectorXZ.copyFrom(mBLASData.L2_zHBMV_x_n1);
        vectorYZ.copyFrom(mBLASData.L2_zHBMV_y_n1);

        // Test for the default case: NO_TRANS
        mBLAS.ZHBMV(uplo, mBLASData.KL, alphaZ, matrixAZ, vectorXZ, incX, betaZ, vectorYZ, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        vectorYRef.copyFrom(mBLASData.L2_zHBMV_o_N);
        verifyMatrix(vectorYRef, vectorYZ);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dN - 1) * incY;
        vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorYZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimY, 1));
        vectorXZ.copyFrom(mBLASData.L2_zHBMV_x_n2);
        vectorYZ.copyFrom(mBLASData.L2_zHBMV_y_n2);

        mBLAS.ZHBMV(uplo, mBLASData.KL, alphaZ, matrixAZ, vectorXZ, incX, betaZ, vectorYZ, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_zHBMV_o_N2);
        verifyMatrix(vectorYRef, vectorYZ);

        mRS.finish();
        checkError();
    }


    private void xHPMV_API_test(int Uplo, int incX, int incY, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                for (Allocation vecY : mMatrix) {
                    if (!validateVecInput(vecY)) {
                        continue;
                    }
                    Element elemA = matA.getType().getElement();
                    if (validateSPR2(elemA, Uplo, vecX, incX, vecY, incY, matA)) {
                        try {
                            if (elemA.isCompatible(Element.F32_2(mRS))) {
                                mBLAS.CHPMV(Uplo, alphaC, matA, vecX, incX, betaC, vecY, incY);
                            } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                                mBLAS.ZHPMV(Uplo, alphaZ, matA, vecX, incX, betaZ, vecY, incY);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.CHPMV(Uplo, alphaC, matA, vecX, incX, betaC, vecY, incY);
                            fail("should throw RSRuntimeException for CHPMV");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.ZHPMV(Uplo, alphaZ, matA, vecX, incX, betaZ, vecY, incY);
                            fail("should throw RSRuntimeException for ZHPMV");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    public void L2_xHPMV_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int incX : mInc) {
                xHPMV_API_test(Uplo, incX, incX, mMatrix);
            }
        }
    }

    public void test_L2_CHPMV_API() {
        L2_xHPMV_API(mMatrixC);
    }

    public void test_L2_ZHPMV_API() {
        L2_xHPMV_API(mMatrixZ);
    }

    public void test_L2_CHPMV_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), N * (N+1) / 2, 1));
        Allocation vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), N, 1));
        Allocation vectorYC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), N, 1));
        matrixAC.copyFrom(mBLASData.L2_cHEMV_A_nn_pu);
        vectorXC.copyFrom(mBLASData.L2_cHEMV_x_n1);
        vectorYC.copyFrom(mBLASData.L2_cHEMV_y_n1);

        // Test for the default case:
        mBLAS.CHPMV(uplo, alphaC, matrixAC, vectorXC, incX, betaC, vectorYC, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), N, 1));
        vectorYRef.copyFrom(mBLASData.L2_cHEMV_o_N);
        verifyMatrix(vectorYRef, vectorYC);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (N - 1) * incX;
        int dimY = 1 + (N - 1) * incY;
        vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorYC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimY, 1));
        vectorXC.copyFrom(mBLASData.L2_cHEMV_x_n2);
        vectorYC.copyFrom(mBLASData.L2_cHEMV_y_n2);

        mBLAS.CHPMV(uplo, alphaC, matrixAC, vectorXC, incX, betaC, vectorYC, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_cHEMV_o_N2);
        verifyMatrix(vectorYRef, vectorYC);

        mRS.finish();
        checkError();
    }

    public void test_L2_ZHPMV_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), N * (N+1) / 2, 1));
        Allocation vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), N, 1));
        Allocation vectorYZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), N, 1));
        matrixAZ.copyFrom(mBLASData.L2_zHEMV_A_nn_pu);
        vectorXZ.copyFrom(mBLASData.L2_zHEMV_x_n1);
        vectorYZ.copyFrom(mBLASData.L2_zHEMV_y_n1);

        // Test for the default case: NO_TRANS
        mBLAS.ZHPMV(uplo, alphaZ, matrixAZ, vectorXZ, incX, betaZ, vectorYZ, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), N, 1));
        vectorYRef.copyFrom(mBLASData.L2_zHEMV_o_N);
        verifyMatrix(vectorYRef, vectorYZ);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (N - 1) * incX;
        int dimY = 1 + (N - 1) * incY;
        vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorYZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimY, 1));
        vectorXZ.copyFrom(mBLASData.L2_zHEMV_x_n2);
        vectorYZ.copyFrom(mBLASData.L2_zHEMV_y_n2);

        mBLAS.ZHPMV(uplo, alphaZ, matrixAZ, vectorXZ, incX, betaZ, vectorYZ, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_zHEMV_o_N2);
        verifyMatrix(vectorYRef, vectorYZ);

        mRS.finish();
        checkError();
    }


    private boolean validateSYMV(Element e, int Uplo, Allocation A, Allocation X, int incX, Allocation Y, int incY) {
        if (!validateUplo(Uplo)) {
            return false;
        }
        int N = A.getType().getY();
        if (A.getType().getX() != N) {
            return false;
        }
        if (!A.getType().getElement().isCompatible(e) ||
            !X.getType().getElement().isCompatible(e) ||
            !Y.getType().getElement().isCompatible(e) ) {
            return false;
        }
        if (X.getType().getY() > 1 || Y.getType().getY() > 1) {
            return false;
        }

        if (incX <= 0 || incY <= 0) {
            return false;
        }
        int expectedXDim = 1 + (N - 1) * incX;
        if (X.getType().getX() != expectedXDim) {
            return false;
        }
        int expectedYDim = 1 + (N - 1) * incY;
        if (Y.getType().getX() != expectedYDim) {
            return false;
        }
        return true;
    }

    private void xSYMV_API_test(int Uplo, int incX, int incY, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                for (Allocation vecY : mMatrix) {
                    if (!validateVecInput(vecY)) {
                        continue;
                    }
                    Element elemA = matA.getType().getElement();
                    if (validateSYMV(elemA, Uplo, matA, vecX, incX, vecY, incY)) {
                        try {
                            if (elemA.isCompatible(Element.F32(mRS))) {
                                mBLAS.SSYMV(Uplo, alphaS, matA, vecX, incX, betaS, vecY, incY);
                            } else if (elemA.isCompatible(Element.F64(mRS))) {
                                mBLAS.DSYMV(Uplo, alphaD, matA, vecX, incX, betaD, vecY, incY);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.SSYMV(Uplo, alphaS, matA, vecX, incX, betaS, vecY, incY);
                            fail("should throw RSRuntimeException for SSYMV");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.DSYMV(Uplo, alphaD, matA, vecX, incX, betaD, vecY, incY);
                            fail("should throw RSRuntimeException for DSYMV");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    public void L2_xSYMV_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int incX : mInc) {
                xSYMV_API_test(Uplo, incX, incX, mMatrix);
            }
        }
    }

    public void test_L2_SSYMV_API() {
        L2_xSYMV_API(mMatrixS);
    }

    public void test_L2_DSYMV_API() {
        L2_xSYMV_API(mMatrixD);
    }

    public void test_L2_SSYMV_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        Allocation vectorYS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        matrixAS.copyFrom(mBLASData.L2_sSYMV_A_nn);
        vectorXS.copyFrom(mBLASData.L2_sSYMV_x_n1);
        vectorYS.copyFrom(mBLASData.L2_sSYMV_y_n1);

        // Test for the default case:
        mBLAS.SSYMV(uplo, alphaS, matrixAS, vectorXS, incX, betaS, vectorYS, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        vectorYRef.copyFrom(mBLASData.L2_sSYMV_o_N);
        verifyMatrix(vectorYRef, vectorYS);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dN - 1) * incY;
        vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorYS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimY, 1));
        vectorXS.copyFrom(mBLASData.L2_sSYMV_x_n2);
        vectorYS.copyFrom(mBLASData.L2_sSYMV_y_n2);

        mBLAS.SSYMV(uplo, alphaS, matrixAS, vectorXS, incX, betaS, vectorYS, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_sSYMV_o_N2);
        verifyMatrix(vectorYRef, vectorYS);

        mRS.finish();
        checkError();
    }

    public void test_L2_DSYMV_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        Allocation vectorYD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        matrixAD.copyFrom(mBLASData.L2_dSYMV_A_nn);
        vectorXD.copyFrom(mBLASData.L2_dSYMV_x_n1);
        vectorYD.copyFrom(mBLASData.L2_dSYMV_y_n1);

        // Test for the default case:
        mBLAS.DSYMV(uplo, alphaD, matrixAD, vectorXD, incX, betaD, vectorYD, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        vectorYRef.copyFrom(mBLASData.L2_dSYMV_o_N);
        verifyMatrix(vectorYRef, vectorYD);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dN - 1) * incY;
        vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorYD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimY, 1));
        vectorXD.copyFrom(mBLASData.L2_dSYMV_x_n2);
        vectorYD.copyFrom(mBLASData.L2_dSYMV_y_n2);

        mBLAS.DSYMV(uplo, alphaD, matrixAD, vectorXD, incX, betaD, vectorYD, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_dSYMV_o_N2);
        verifyMatrix(vectorYRef, vectorYD);

        mRS.finish();
        checkError();
    }



    private void xSBMV_API_test(int Uplo, int K, int incX, int incY, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                for (Allocation vecY : mMatrix) {
                    if (!validateVecInput(vecY)) {
                        continue;
                    }
                    Element elemA = matA.getType().getElement();
                    if (validateSYMV(elemA, Uplo, matA, vecX, incX, vecY, incY) && K >= 0) {
                        try {
                            if (elemA.isCompatible(Element.F32(mRS))) {
                                mBLAS.SSBMV(Uplo, K, alphaS, matA, vecX, incX, betaS, vecY, incY);
                            } else if (elemA.isCompatible(Element.F64(mRS))) {
                                mBLAS.DSBMV(Uplo, K, alphaD, matA, vecX, incX, betaD, vecY, incY);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.SSBMV(Uplo, K, alphaS, matA, vecX, incX, betaS, vecY, incY);
                            fail("should throw RSRuntimeException for SSBMV");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.DSBMV(Uplo, K, alphaD, matA, vecX, incX, betaD, vecY, incY);
                            fail("should throw RSRuntimeException for DSBMV");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    public void L2_xSBMV_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int K : mK) {
                for (int incX : mInc) {
                    xSBMV_API_test(Uplo, K, incX, incX, mMatrix);
                }
            }
        }
    }

    public void test_L2_SSBMV_API() {
        L2_xSBMV_API(mMatrixS);
    }

    public void test_L2_DSBMV_API() {
        L2_xSBMV_API(mMatrixD);
    }

    public void test_L2_SSBMV_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        Allocation vectorYS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        matrixAS.copy2DRangeFrom(0, 0, mBLASData.KL + 1, mBLASData.dN, mBLASData.L2_sSBMV_A_nn);
        vectorXS.copyFrom(mBLASData.L2_sSBMV_x_n1);
        vectorYS.copyFrom(mBLASData.L2_sSBMV_y_n1);

        // Test for the default case:
        mBLAS.SSBMV(uplo, mBLASData.KL, alphaS, matrixAS, vectorXS, incX, betaS, vectorYS, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        vectorYRef.copyFrom(mBLASData.L2_sSBMV_o_N);
        verifyMatrix(vectorYRef, vectorYS);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dN - 1) * incY;
        vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorYS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimY, 1));
        vectorXS.copyFrom(mBLASData.L2_sSBMV_x_n2);
        vectorYS.copyFrom(mBLASData.L2_sSBMV_y_n2);

        mBLAS.SSBMV(uplo, mBLASData.KL, alphaS, matrixAS, vectorXS, incX, betaS, vectorYS, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_sSBMV_o_N2);
        verifyMatrix(vectorYRef, vectorYS);

        mRS.finish();
        checkError();
    }

    public void test_L2_DSBMV_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        Allocation vectorYD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        matrixAD.copy2DRangeFrom(0, 0, mBLASData.KL + 1, mBLASData.dN, mBLASData.L2_dSBMV_A_nn);
        vectorXD.copyFrom(mBLASData.L2_dSBMV_x_n1);
        vectorYD.copyFrom(mBLASData.L2_dSBMV_y_n1);

        // Test for the default case:
        mBLAS.DSBMV(uplo, mBLASData.KL, alphaD, matrixAD, vectorXD, incX, betaD, vectorYD, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        vectorYRef.copyFrom(mBLASData.L2_dSBMV_o_N);
        verifyMatrix(vectorYRef, vectorYD);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dN - 1) * incY;
        vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorYD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimY, 1));
        vectorXD.copyFrom(mBLASData.L2_dSBMV_x_n2);
        vectorYD.copyFrom(mBLASData.L2_dSBMV_y_n2);

        mBLAS.DSBMV(uplo, mBLASData.KL, alphaD, matrixAD, vectorXD, incX, betaD, vectorYD, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_dSBMV_o_N2);
        verifyMatrix(vectorYRef, vectorYD);

        mRS.finish();
        checkError();
    }


    private boolean validateSPMV(Element e, int Uplo, Allocation Ap, Allocation X, int incX, Allocation Y, int incY) {
        if (!validateUplo(Uplo)) {
            return false;
        }
        if (!Ap.getType().getElement().isCompatible(e) ||
            !X.getType().getElement().isCompatible(e) ||
            !Y.getType().getElement().isCompatible(e)) {
            return false;
        }
        if (X.getType().getY() > 1 || Y.getType().getY() > 1) {
            return false;
        }

        if (Ap.getType().getY() > 1) {
            return false;
        }

        int N = (int)Math.sqrt((double)Ap.getType().getX() * 2);
        if (Ap.getType().getX() != ((N * (N+1)) / 2)) {
            return false;
        }
        if (incX <= 0 || incY <= 0) {
            return false;
        }
        int expectedXDim = 1 + (N - 1) * incX;
        if (X.getType().getX() != expectedXDim) {
            return false;
        }
        int expectedYDim = 1 + (N - 1) * incY;
        if (Y.getType().getX() != expectedYDim) {
            return false;
        }

        return true;
    }

    private void xSPMV_API_test(int Uplo, int incX, int incY, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                for (Allocation vecY : mMatrix) {
                    if (!validateVecInput(vecY)) {
                        continue;
                    }
                    Element elemA = matA.getType().getElement();
                    if (validateSPMV(elemA, Uplo, matA, vecX, incX, vecY, incY)) {
                        try {
                            if (elemA.isCompatible(Element.F32(mRS))) {
                                mBLAS.SSPMV(Uplo, alphaS, matA, vecX, incX, betaS, vecY, incY);
                            } else if (elemA.isCompatible(Element.F64(mRS))) {
                                mBLAS.DSPMV(Uplo, alphaD, matA, vecX, incX, betaD, vecY, incY);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.SSPMV(Uplo, alphaS, matA, vecX, incX, betaS, vecY, incY);
                            fail("should throw RSRuntimeException for SSPMV");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.DSPMV(Uplo, alphaD, matA, vecX, incX, betaD, vecY, incY);
                            fail("should throw RSRuntimeException for DSPMV");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    public void L2_xSPMV_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int incX : mInc) {
                xSPMV_API_test(Uplo, incX, incX, mMatrix);
            }
        }
    }

    public void test_L2_SSPMV_API() {
        L2_xSPMV_API(mMatrixS);
    }

    public void test_L2_DSPMV_API() {
        L2_xSPMV_API(mMatrixD);
    }

    public void test_L2_SSPMV_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), N * (N+1) / 2, 1));
        Allocation vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), N, 1));
        Allocation vectorYS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), N, 1));
        matrixAS.copyFrom(mBLASData.L2_sSYMV_A_nn_pu);
        vectorXS.copyFrom(mBLASData.L2_sSYMV_x_n1);
        vectorYS.copyFrom(mBLASData.L2_sSYMV_y_n1);

        // Test for the default case:
        mBLAS.SSPMV(uplo, alphaS, matrixAS, vectorXS, incX, betaS, vectorYS, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), N, 1));
        vectorYRef.copyFrom(mBLASData.L2_sSYMV_o_N);
        verifyMatrix(vectorYRef, vectorYS);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (N - 1) * incX;
        int dimY = 1 + (N - 1) * incY;
        vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorYS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimY, 1));
        vectorXS.copyFrom(mBLASData.L2_sSYMV_x_n2);
        vectorYS.copyFrom(mBLASData.L2_sSYMV_y_n2);

        mBLAS.SSPMV(uplo, alphaS, matrixAS, vectorXS, incX, betaS, vectorYS, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_sSYMV_o_N2);
        verifyMatrix(vectorYRef, vectorYS);

        mRS.finish();
        checkError();
    }

    public void test_L2_DSPMV_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), N * (N+1) / 2, 1));
        Allocation vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), N, 1));
        Allocation vectorYD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), N, 1));
        matrixAD.copyFrom(mBLASData.L2_dSYMV_A_nn_pu);
        vectorXD.copyFrom(mBLASData.L2_dSYMV_x_n1);
        vectorYD.copyFrom(mBLASData.L2_dSYMV_y_n1);

        // Test for the default case:
        mBLAS.DSPMV(uplo, alphaD, matrixAD, vectorXD, incX, betaD, vectorYD, incY);
        Allocation vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), N, 1));
        vectorYRef.copyFrom(mBLASData.L2_dSYMV_o_N);
        verifyMatrix(vectorYRef, vectorYD);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (N - 1) * incX;
        int dimY = 1 + (N - 1) * incY;
        vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorYD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimY, 1));
        vectorXD.copyFrom(mBLASData.L2_dSYMV_x_n2);
        vectorYD.copyFrom(mBLASData.L2_dSYMV_y_n2);

        mBLAS.DSPMV(uplo, alphaD, matrixAD, vectorXD, incX, betaD, vectorYD, incY);
        vectorYRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimY, 1));
        vectorYRef.copyFrom(mBLASData.L2_dSYMV_o_N2);
        verifyMatrix(vectorYRef, vectorYD);

        mRS.finish();
        checkError();
    }



    private boolean validateTRMV(Element e, int Uplo, int TransA, int Diag, Allocation A, Allocation X, int incX) {
        if (!validateUplo(Uplo)) {
            return false;
        }
        if (!validateTranspose(TransA)) {
            return false;
        }
        if (!validateDiag(Diag)) {
            return false;
        }
        int N = A.getType().getY();
        if (A.getType().getX() != N) {
            return false;
        }
        if (!A.getType().getElement().isCompatible(e) ||
            !X.getType().getElement().isCompatible(e)) {
            return false;
        }
        if (X.getType().getY() > 1) {
            return false;
        }

        if (incX <= 0) {
            return false;
        }
        int expectedXDim = 1 + (N - 1) * incX;
        if (X.getType().getX() != expectedXDim) {
            return false;
        }
        return true;
    }

    private void xTRMV_API_test(int Uplo, int TransA, int Diag, int incX, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                Element elemA = matA.getType().getElement();
                if (validateTRMV(elemA, Uplo, TransA, Diag, matA, vecX, incX)) {
                    try {
                        if (elemA.isCompatible(Element.F32(mRS))) {
                            mBLAS.STRMV(Uplo, TransA, Diag, matA, vecX, incX);
                        } else if (elemA.isCompatible(Element.F64(mRS))) {
                            mBLAS.DTRMV(Uplo, TransA, Diag, matA, vecX, incX);
                        } else if (elemA.isCompatible(Element.F32_2(mRS))) {
                            mBLAS.CTRMV(Uplo, TransA, Diag, matA, vecX, incX);
                        } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                            mBLAS.ZTRMV(Uplo, TransA, Diag, matA, vecX, incX);
                        }
                    } catch (RSRuntimeException e) {
                        fail("should NOT throw RSRuntimeException");
                    }
                } else {
                    try {
                        mBLAS.STRMV(Uplo, TransA, Diag, matA, vecX, incX);
                        fail("should throw RSRuntimeException for STRMV");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.DTRMV(Uplo, TransA, Diag, matA, vecX, incX);
                        fail("should throw RSRuntimeException for DTRMV");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.CTRMV(Uplo, TransA, Diag, matA, vecX, incX);
                        fail("should throw RSRuntimeException for CTRMV");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.ZTRMV(Uplo, TransA, Diag, matA, vecX, incX);
                        fail("should throw RSRuntimeException for ZTRMV");
                    } catch (RSRuntimeException e) {
                    }
                }
            }
        }
    }

    public void L2_xTRMV_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int TransA : mTranspose) {
                for (int Diag : mDiag) {
                    for (int incX : mInc) {
                        xTRMV_API_test(Uplo, TransA, Diag, incX, mMatrix);
                    }
                }
            }
        }
    }

    public void test_L2_STRMV_API() {
        L2_xTRMV_API(mMatrixS);
    }

    public void test_L2_DTRMV_API() {
        L2_xTRMV_API(mMatrixD);
    }

    public void test_L2_CTRMV_API() {
        L2_xTRMV_API(mMatrixC);
    }

    public void test_L2_ZTRMV_API() {
        L2_xTRMV_API(mMatrixZ);
    }

    public void test_L2_STRMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        matrixAS.copyFrom(mBLASData.L2_sTRMV_A_nn);
        vectorXS.copyFrom(mBLASData.L2_sTRMV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.STRMV(uplo, trans, diag, matrixAS, vectorXS, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        vectorXRef.copyFrom(mBLASData.L2_sTRMV_o_UN);
        verifyMatrix(vectorXRef, vectorXS);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXS.copyFrom(mBLASData.L2_sTRMV_x_n1);
        mBLAS.STRMV(uplo, trans, diag, matrixAS, vectorXS, incX);
        vectorXRef.copyFrom(mBLASData.L2_sTRMV_o_UT);
        verifyMatrix(vectorXRef, vectorXS);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXS.copyFrom(mBLASData.L2_sTRMV_x_n1);
        mBLAS.STRMV(uplo, trans, diag, matrixAS, vectorXS, incX);
        vectorXRef.copyFrom(mBLASData.L2_sTRMV_o_UH);
        verifyMatrix(vectorXRef, vectorXS);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorXS.copyFrom(mBLASData.L2_sTRMV_x_n2);

        mBLAS.STRMV(uplo, trans, diag, matrixAS, vectorXS, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_sTRMV_o_UN2);
        verifyMatrix(vectorXRef, vectorXS);

        mRS.finish();
        checkError();
    }

    public void test_L2_DTRMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        matrixAD.copyFrom(mBLASData.L2_dTRMV_A_nn);
        vectorXD.copyFrom(mBLASData.L2_dTRMV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.DTRMV(uplo, trans, diag, matrixAD, vectorXD, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        vectorXRef.copyFrom(mBLASData.L2_dTRMV_o_UN);
        verifyMatrix(vectorXRef, vectorXD);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXD.copyFrom(mBLASData.L2_dTRMV_x_n1);
        mBLAS.DTRMV(uplo, trans, diag, matrixAD, vectorXD, incX);
        vectorXRef.copyFrom(mBLASData.L2_dTRMV_o_UT);
        verifyMatrix(vectorXRef, vectorXD);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXD.copyFrom(mBLASData.L2_dTRMV_x_n1);
        mBLAS.DTRMV(uplo, trans, diag, matrixAD, vectorXD, incX);
        vectorXRef.copyFrom(mBLASData.L2_dTRMV_o_UH);
        verifyMatrix(vectorXRef, vectorXD);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorXD.copyFrom(mBLASData.L2_dTRMV_x_n2);

        mBLAS.DTRMV(uplo, trans, diag, matrixAD, vectorXD, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_dTRMV_o_UN2);
        verifyMatrix(vectorXRef, vectorXD);

        mRS.finish();
        checkError();
    }

    public void test_L2_CTRMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        matrixAC.copyFrom(mBLASData.L2_cTRMV_A_nn);
        vectorXC.copyFrom(mBLASData.L2_cTRMV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.CTRMV(uplo, trans, diag, matrixAC, vectorXC, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        vectorXRef.copyFrom(mBLASData.L2_cTRMV_o_UN);
        verifyMatrix(vectorXRef, vectorXC);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXC.copyFrom(mBLASData.L2_cTRMV_x_n1);
        mBLAS.CTRMV(uplo, trans, diag, matrixAC, vectorXC, incX);
        vectorXRef.copyFrom(mBLASData.L2_cTRMV_o_UT);
        verifyMatrix(vectorXRef, vectorXC);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXC.copyFrom(mBLASData.L2_cTRMV_x_n1);
        mBLAS.CTRMV(uplo, trans, diag, matrixAC, vectorXC, incX);
        vectorXRef.copyFrom(mBLASData.L2_cTRMV_o_UH);
        verifyMatrix(vectorXRef, vectorXC);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorXC.copyFrom(mBLASData.L2_cTRMV_x_n2);

        mBLAS.CTRMV(uplo, trans, diag, matrixAC, vectorXC, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_cTRMV_o_UN2);
        verifyMatrix(vectorXRef, vectorXC);

        mRS.finish();
        checkError();
    }

    public void test_L2_ZTRMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        matrixAZ.copyFrom(mBLASData.L2_zTRMV_A_nn);
        vectorXZ.copyFrom(mBLASData.L2_zTRMV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.ZTRMV(uplo, trans, diag, matrixAZ, vectorXZ, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        vectorXRef.copyFrom(mBLASData.L2_zTRMV_o_UN);
        verifyMatrix(vectorXRef, vectorXZ);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXZ.copyFrom(mBLASData.L2_zTRMV_x_n1);
        mBLAS.ZTRMV(uplo, trans, diag, matrixAZ, vectorXZ, incX);
        vectorXRef.copyFrom(mBLASData.L2_zTRMV_o_UT);
        verifyMatrix(vectorXRef, vectorXZ);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXZ.copyFrom(mBLASData.L2_zTRMV_x_n1);
        mBLAS.ZTRMV(uplo, trans, diag, matrixAZ, vectorXZ, incX);
        vectorXRef.copyFrom(mBLASData.L2_zTRMV_o_UH);
        verifyMatrix(vectorXRef, vectorXZ);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorXZ.copyFrom(mBLASData.L2_zTRMV_x_n2);

        mBLAS.ZTRMV(uplo, trans, diag, matrixAZ, vectorXZ, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_zTRMV_o_UN2);
        verifyMatrix(vectorXRef, vectorXZ);

        mRS.finish();
        checkError();
    }



    private void xTBMV_API_test(int Uplo, int TransA, int Diag, int K, int incX, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                Element elemA = matA.getType().getElement();
                if (validateTRMV(elemA, Uplo, TransA, Diag, matA, vecX, incX) && K >= 0) {
                    try {
                        if (elemA.isCompatible(Element.F32(mRS))) {
                            mBLAS.STBMV(Uplo, TransA, Diag, K, matA, vecX, incX);
                        } else if (elemA.isCompatible(Element.F64(mRS))) {
                            mBLAS.DTBMV(Uplo, TransA, Diag, K, matA, vecX, incX);
                        } else if (elemA.isCompatible(Element.F32_2(mRS))) {
                            mBLAS.CTBMV(Uplo, TransA, Diag, K, matA, vecX, incX);
                        } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                            mBLAS.ZTBMV(Uplo, TransA, Diag, K, matA, vecX, incX);
                        }
                    } catch (RSRuntimeException e) {
                        fail("should NOT throw RSRuntimeException");
                    }
                } else {
                    try {
                        mBLAS.STBMV(Uplo, TransA, Diag, K, matA, vecX, incX);
                        fail("should throw RSRuntimeException for STBMV");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.DTBMV(Uplo, TransA, Diag, K, matA, vecX, incX);
                        fail("should throw RSRuntimeException for DTBMV");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.CTBMV(Uplo, TransA, Diag, K, matA, vecX, incX);
                        fail("should throw RSRuntimeException for CTBMV");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.ZTBMV(Uplo, TransA, Diag, K, matA, vecX, incX);
                        fail("should throw RSRuntimeException for ZTBMV");
                    } catch (RSRuntimeException e) {
                    }
                }
            }
        }
    }

    public void L2_xTBMV_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int TransA : mTranspose) {
                for (int Diag : mDiag) {
                    for (int K : mK) {
                        for (int incX : mInc) {
                            xTBMV_API_test(Uplo, TransA, Diag, K, incX, mMatrix);
                        }
                    }
                }
            }
        }
    }

    public void test_L2_STBMV_API() {
        L2_xTBMV_API(mMatrixS);
    }

    public void test_L2_DTBMV_API() {
        L2_xTBMV_API(mMatrixD);
    }

    public void test_L2_CTBMV_API() {
        L2_xTBMV_API(mMatrixC);
    }

    public void test_L2_ZTBMV_API() {
        L2_xTBMV_API(mMatrixZ);
    }

    public void test_L2_STBMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        matrixAS.copy2DRangeFrom(0, 0, mBLASData.KL + 1, mBLASData.dN, mBLASData.L2_sTBMV_A_nn);
        vectorXS.copyFrom(mBLASData.L2_sTBMV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.STBMV(uplo, trans, diag, mBLASData.KL, matrixAS, vectorXS, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        vectorXRef.copyFrom(mBLASData.L2_sTBMV_o_UN);
        verifyMatrix(vectorXRef, vectorXS);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXS.copyFrom(mBLASData.L2_sTBMV_x_n1);
        mBLAS.STBMV(uplo, trans, diag, mBLASData.KL, matrixAS, vectorXS, incX);
        vectorXRef.copyFrom(mBLASData.L2_sTBMV_o_UT);
        verifyMatrix(vectorXRef, vectorXS);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXS.copyFrom(mBLASData.L2_sTBMV_x_n1);
        mBLAS.STBMV(uplo, trans, diag, mBLASData.KL, matrixAS, vectorXS, incX);
        vectorXRef.copyFrom(mBLASData.L2_sTBMV_o_UH);
        verifyMatrix(vectorXRef, vectorXS);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorXS.copyFrom(mBLASData.L2_sTBMV_x_n2);

        mBLAS.STBMV(uplo, trans, diag, mBLASData.KL, matrixAS, vectorXS, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_sTBMV_o_UN2);
        verifyMatrix(vectorXRef, vectorXS);

        mRS.finish();
        checkError();
    }

    public void test_L2_DTBMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        matrixAD.copy2DRangeFrom(0, 0, mBLASData.KL + 1, mBLASData.dN, mBLASData.L2_dTBMV_A_nn);
        vectorXD.copyFrom(mBLASData.L2_dTBMV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.DTBMV(uplo, trans, diag, mBLASData.KL, matrixAD, vectorXD, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        vectorXRef.copyFrom(mBLASData.L2_dTBMV_o_UN);
        verifyMatrix(vectorXRef, vectorXD);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXD.copyFrom(mBLASData.L2_dTBMV_x_n1);
        mBLAS.DTBMV(uplo, trans, diag, mBLASData.KL, matrixAD, vectorXD, incX);
        vectorXRef.copyFrom(mBLASData.L2_dTBMV_o_UT);
        verifyMatrix(vectorXRef, vectorXD);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXD.copyFrom(mBLASData.L2_dTBMV_x_n1);
        mBLAS.DTBMV(uplo, trans, diag, mBLASData.KL, matrixAD, vectorXD, incX);
        vectorXRef.copyFrom(mBLASData.L2_dTBMV_o_UH);
        verifyMatrix(vectorXRef, vectorXD);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorXD.copyFrom(mBLASData.L2_dTBMV_x_n2);

        mBLAS.DTBMV(uplo, trans, diag, mBLASData.KL, matrixAD, vectorXD, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_dTBMV_o_UN2);
        verifyMatrix(vectorXRef, vectorXD);

        mRS.finish();
        checkError();
    }

    public void test_L2_CTBMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        matrixAC.copy2DRangeFrom(0, 0, mBLASData.KL + 1, mBLASData.dN, mBLASData.L2_cTBMV_A_nn);
        vectorXC.copyFrom(mBLASData.L2_cTBMV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.CTBMV(uplo, trans, diag, mBLASData.KL, matrixAC, vectorXC, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        vectorXRef.copyFrom(mBLASData.L2_cTBMV_o_UN);
        verifyMatrix(vectorXRef, vectorXC);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXC.copyFrom(mBLASData.L2_cTBMV_x_n1);
        mBLAS.CTBMV(uplo, trans, diag, mBLASData.KL, matrixAC, vectorXC, incX);
        vectorXRef.copyFrom(mBLASData.L2_cTBMV_o_UT);
        verifyMatrix(vectorXRef, vectorXC);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXC.copyFrom(mBLASData.L2_cTBMV_x_n1);
        mBLAS.CTBMV(uplo, trans, diag, mBLASData.KL, matrixAC, vectorXC, incX);
        vectorXRef.copyFrom(mBLASData.L2_cTBMV_o_UH);
        verifyMatrix(vectorXRef, vectorXC);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorXC.copyFrom(mBLASData.L2_cTBMV_x_n2);

        mBLAS.CTBMV(uplo, trans, diag, mBLASData.KL, matrixAC, vectorXC, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_cTBMV_o_UN2);
        verifyMatrix(vectorXRef, vectorXC);

        mRS.finish();
        checkError();
    }

    public void test_L2_ZTBMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        matrixAZ.copy2DRangeFrom(0, 0, mBLASData.KL + 1, mBLASData.dN, mBLASData.L2_zTBMV_A_nn);
        vectorXZ.copyFrom(mBLASData.L2_zTBMV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.ZTBMV(uplo, trans, diag, mBLASData.KL, matrixAZ, vectorXZ, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        vectorXRef.copyFrom(mBLASData.L2_zTBMV_o_UN);
        verifyMatrix(vectorXRef, vectorXZ);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXZ.copyFrom(mBLASData.L2_zTBMV_x_n1);
        mBLAS.ZTBMV(uplo, trans, diag, mBLASData.KL, matrixAZ, vectorXZ, incX);
        vectorXRef.copyFrom(mBLASData.L2_zTBMV_o_UT);
        verifyMatrix(vectorXRef, vectorXZ);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXZ.copyFrom(mBLASData.L2_zTBMV_x_n1);
        mBLAS.ZTBMV(uplo, trans, diag, mBLASData.KL, matrixAZ, vectorXZ, incX);
        vectorXRef.copyFrom(mBLASData.L2_zTBMV_o_UH);
        verifyMatrix(vectorXRef, vectorXZ);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorXZ.copyFrom(mBLASData.L2_zTBMV_x_n2);

        mBLAS.ZTBMV(uplo, trans, diag, mBLASData.KL, matrixAZ, vectorXZ, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_zTBMV_o_UN2);
        verifyMatrix(vectorXRef, vectorXZ);

        mRS.finish();
        checkError();
    }


    private boolean validateTPMV(Element e, int Uplo, int TransA, int Diag, Allocation Ap, Allocation X, int incX) {
        if (!validateUplo(Uplo)) {
            return false;
        }
        if (!validateTranspose(TransA)) {
            return false;
        }
        if (!validateDiag(Diag)) {
            return false;
        }
        if (!Ap.getType().getElement().isCompatible(e) ||
            !X.getType().getElement().isCompatible(e)) {
            return false;
        }
        if (X.getType().getY() > 1) {
            return false;
        }

        if (Ap.getType().getY() > 1) {
            return false;
        }

        int N = (int)Math.sqrt((double)Ap.getType().getX() * 2);
        if (Ap.getType().getX() != ((N * (N+1)) / 2)) {
            return false;
        }
        if (incX <= 0) {
            return false;
        }
        int expectedXDim = 1 + (N - 1) * incX;
        if (X.getType().getX() != expectedXDim) {
            return false;
        }

        return true;
    }

    private void xTPMV_API_test(int Uplo, int TransA, int Diag, int incX, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                Element elemA = matA.getType().getElement();
                if (validateTPMV(elemA, Uplo, TransA, Diag, matA, vecX, incX)) {
                    try {
                        if (elemA.isCompatible(Element.F32(mRS))) {
                            mBLAS.STPMV(Uplo, TransA, Diag, matA, vecX, incX);
                        } else if (elemA.isCompatible(Element.F64(mRS))) {
                            mBLAS.DTPMV(Uplo, TransA, Diag, matA, vecX, incX);
                        } else if (elemA.isCompatible(Element.F32_2(mRS))) {
                            mBLAS.CTPMV(Uplo, TransA, Diag, matA, vecX, incX);
                        } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                            mBLAS.ZTPMV(Uplo, TransA, Diag, matA, vecX, incX);
                        }
                    } catch (RSRuntimeException e) {
                        fail("should NOT throw RSRuntimeException");
                    }
                } else {
                    try {
                        mBLAS.STPMV(Uplo, TransA, Diag, matA, vecX, incX);
                        fail("should throw RSRuntimeException for STPMV");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.DTPMV(Uplo, TransA, Diag, matA, vecX, incX);
                        fail("should throw RSRuntimeException for DTPMV");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.CTPMV(Uplo, TransA, Diag, matA, vecX, incX);
                        fail("should throw RSRuntimeException for CTPMV");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.ZTPMV(Uplo, TransA, Diag, matA, vecX, incX);
                        fail("should throw RSRuntimeException for ZTPMV");
                    } catch (RSRuntimeException e) {
                    }
                }
            }
        }
    }

    public void L2_xTPMV_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int TransA : mTranspose) {
                for (int Diag : mDiag) {
                    for (int incX : mInc) {
                        xTPMV_API_test(Uplo, TransA, Diag, incX, mMatrix);
                    }
                }
            }
        }
    }

    public void test_L2_STPMV_API() {
        L2_xTPMV_API(mMatrixS);
    }

    public void test_L2_DTPMV_API() {
        L2_xTPMV_API(mMatrixD);
    }

    public void test_L2_CTPMV_API() {
        L2_xTPMV_API(mMatrixC);
    }

    public void test_L2_ZTPMV_API() {
        L2_xTPMV_API(mMatrixZ);
    }

    public void test_L2_STPMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), N * (N+1) / 2, 1));
        Allocation vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), N, 1));
        matrixAS.copyFrom(mBLASData.L2_sTRMV_A_nn_pu);
        vectorXS.copyFrom(mBLASData.L2_sTRMV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.STPMV(uplo, trans, diag, matrixAS, vectorXS, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), N, 1));
        vectorXRef.copyFrom(mBLASData.L2_sTRMV_o_UN);
        verifyMatrix(vectorXRef, vectorXS);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXS.copyFrom(mBLASData.L2_sTRMV_x_n1);
        mBLAS.STPMV(uplo, trans, diag, matrixAS, vectorXS, incX);
        vectorXRef.copyFrom(mBLASData.L2_sTRMV_o_UT);
        verifyMatrix(vectorXRef, vectorXS);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXS.copyFrom(mBLASData.L2_sTRMV_x_n1);
        mBLAS.STPMV(uplo, trans, diag, matrixAS, vectorXS, incX);
        vectorXRef.copyFrom(mBLASData.L2_sTRMV_o_UH);
        verifyMatrix(vectorXRef, vectorXS);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (N - 1) * incX;
        vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorXS.copyFrom(mBLASData.L2_sTRMV_x_n2);

        mBLAS.STPMV(uplo, trans, diag, matrixAS, vectorXS, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_sTRMV_o_UN2);
        verifyMatrix(vectorXRef, vectorXS);

        mRS.finish();
        checkError();
    }

    public void test_L2_DTPMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), N * (N+1) / 2, 1));
        Allocation vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), N, 1));
        matrixAD.copyFrom(mBLASData.L2_dTRMV_A_nn_pu);
        vectorXD.copyFrom(mBLASData.L2_dTRMV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.DTPMV(uplo, trans, diag, matrixAD, vectorXD, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), N, 1));
        vectorXRef.copyFrom(mBLASData.L2_dTRMV_o_UN);
        verifyMatrix(vectorXRef, vectorXD);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXD.copyFrom(mBLASData.L2_dTRMV_x_n1);
        mBLAS.DTPMV(uplo, trans, diag, matrixAD, vectorXD, incX);
        vectorXRef.copyFrom(mBLASData.L2_dTRMV_o_UT);
        verifyMatrix(vectorXRef, vectorXD);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXD.copyFrom(mBLASData.L2_dTRMV_x_n1);
        mBLAS.DTPMV(uplo, trans, diag, matrixAD, vectorXD, incX);
        vectorXRef.copyFrom(mBLASData.L2_dTRMV_o_UH);
        verifyMatrix(vectorXRef, vectorXD);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (N - 1) * incX;
        vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorXD.copyFrom(mBLASData.L2_dTRMV_x_n2);

        mBLAS.DTPMV(uplo, trans, diag, matrixAD, vectorXD, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_dTRMV_o_UN2);
        verifyMatrix(vectorXRef, vectorXD);

        mRS.finish();
        checkError();
    }

    public void test_L2_CTPMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), N * (N+1) / 2, 1));
        Allocation vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), N, 1));
        matrixAC.copyFrom(mBLASData.L2_cTRMV_A_nn_pu);
        vectorXC.copyFrom(mBLASData.L2_cTRMV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.CTPMV(uplo, trans, diag, matrixAC, vectorXC, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), N, 1));
        vectorXRef.copyFrom(mBLASData.L2_cTRMV_o_UN);
        verifyMatrix(vectorXRef, vectorXC);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXC.copyFrom(mBLASData.L2_cTRMV_x_n1);
        mBLAS.CTPMV(uplo, trans, diag, matrixAC, vectorXC, incX);
        vectorXRef.copyFrom(mBLASData.L2_cTRMV_o_UT);
        verifyMatrix(vectorXRef, vectorXC);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXC.copyFrom(mBLASData.L2_cTRMV_x_n1);
        mBLAS.CTPMV(uplo, trans, diag, matrixAC, vectorXC, incX);
        vectorXRef.copyFrom(mBLASData.L2_cTRMV_o_UH);
        verifyMatrix(vectorXRef, vectorXC);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (N - 1) * incX;
        vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorXC.copyFrom(mBLASData.L2_cTRMV_x_n2);

        mBLAS.CTPMV(uplo, trans, diag, matrixAC, vectorXC, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_cTRMV_o_UN2);
        verifyMatrix(vectorXRef, vectorXC);

        mRS.finish();
        checkError();
    }

    public void test_L2_ZTPMV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), N * (N+1) / 2, 1));
        Allocation vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), N, 1));
        matrixAZ.copyFrom(mBLASData.L2_zTRMV_A_nn_pu);
        vectorXZ.copyFrom(mBLASData.L2_zTRMV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.ZTPMV(uplo, trans, diag, matrixAZ, vectorXZ, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), N, 1));
        vectorXRef.copyFrom(mBLASData.L2_zTRMV_o_UN);
        verifyMatrix(vectorXRef, vectorXZ);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXZ.copyFrom(mBLASData.L2_zTRMV_x_n1);
        mBLAS.ZTPMV(uplo, trans, diag, matrixAZ, vectorXZ, incX);
        vectorXRef.copyFrom(mBLASData.L2_zTRMV_o_UT);
        verifyMatrix(vectorXRef, vectorXZ);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXZ.copyFrom(mBLASData.L2_zTRMV_x_n1);
        mBLAS.ZTPMV(uplo, trans, diag, matrixAZ, vectorXZ, incX);
        vectorXRef.copyFrom(mBLASData.L2_zTRMV_o_UH);
        verifyMatrix(vectorXRef, vectorXZ);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (N - 1) * incX;
        vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorXZ.copyFrom(mBLASData.L2_zTRMV_x_n2);

        mBLAS.ZTPMV(uplo, trans, diag, matrixAZ, vectorXZ, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_zTRMV_o_UN2);
        verifyMatrix(vectorXRef, vectorXZ);

        mRS.finish();
        checkError();
    }


    private void xTRSV_API_test(int Uplo, int TransA, int Diag, int incX, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                Element elemA = matA.getType().getElement();
                if (validateTRMV(elemA, Uplo, TransA, Diag, matA, vecX, incX)) {
                    try {
                        if (elemA.isCompatible(Element.F32(mRS))) {
                            mBLAS.STRSV(Uplo, TransA, Diag, matA, vecX, incX);
                        } else if (elemA.isCompatible(Element.F64(mRS))) {
                            mBLAS.DTRSV(Uplo, TransA, Diag, matA, vecX, incX);
                        } else if (elemA.isCompatible(Element.F32_2(mRS))) {
                            mBLAS.CTRSV(Uplo, TransA, Diag, matA, vecX, incX);
                        } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                            mBLAS.ZTRSV(Uplo, TransA, Diag, matA, vecX, incX);
                        }
                    } catch (RSRuntimeException e) {
                        fail("should NOT throw RSRuntimeException");
                    }
                } else {
                    try {
                        mBLAS.STRSV(Uplo, TransA, Diag, matA, vecX, incX);
                        fail("should throw RSRuntimeException for STRSV");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.DTRSV(Uplo, TransA, Diag, matA, vecX, incX);
                        fail("should throw RSRuntimeException for DTRSV");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.CTRSV(Uplo, TransA, Diag, matA, vecX, incX);
                        fail("should throw RSRuntimeException for CTRSV");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.ZTRSV(Uplo, TransA, Diag, matA, vecX, incX);
                        fail("should throw RSRuntimeException for ZTRSV");
                    } catch (RSRuntimeException e) {
                    }
                }
            }
        }
    }

    public void L2_xTRSV_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int TransA : mTranspose) {
                for (int Diag : mDiag) {
                    for (int incX : mInc) {
                        xTRSV_API_test(Uplo, TransA, Diag, incX, mMatrix);
                    }
                }
            }
        }
    }

    public void test_L2_STRSV_API() {
        L2_xTRSV_API(mMatrixS);
    }

    public void test_L2_DTRSV_API() {
        L2_xTRSV_API(mMatrixD);
    }

    public void test_L2_CTRSV_API() {
        L2_xTRSV_API(mMatrixC);
    }

    public void test_L2_ZTRSV_API() {
        L2_xTRSV_API(mMatrixZ);
    }

    public void test_L2_STRSV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        matrixAS.copyFrom(mBLASData.L2_sTRSV_A_nn);
        vectorXS.copyFrom(mBLASData.L2_sTRSV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.STRSV(uplo, trans, diag, matrixAS, vectorXS, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        vectorXRef.copyFrom(mBLASData.L2_sTRSV_o_UN);
        verifyMatrix(vectorXRef, vectorXS);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXS.copyFrom(mBLASData.L2_sTRSV_x_n1);
        mBLAS.STRSV(uplo, trans, diag, matrixAS, vectorXS, incX);
        vectorXRef.copyFrom(mBLASData.L2_sTRSV_o_UT);
        verifyMatrix(vectorXRef, vectorXS);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXS.copyFrom(mBLASData.L2_sTRSV_x_n1);
        mBLAS.STRSV(uplo, trans, diag, matrixAS, vectorXS, incX);
        vectorXRef.copyFrom(mBLASData.L2_sTRSV_o_UH);
        verifyMatrix(vectorXRef, vectorXS);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorXS.copyFrom(mBLASData.L2_sTRSV_x_n2);

        mBLAS.STRSV(uplo, trans, diag, matrixAS, vectorXS, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_sTRSV_o_UN2);
        verifyMatrix(vectorXRef, vectorXS);

        mRS.finish();
        checkError();
    }

    public void test_L2_DTRSV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        matrixAD.copyFrom(mBLASData.L2_dTRSV_A_nn);
        vectorXD.copyFrom(mBLASData.L2_dTRSV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.DTRSV(uplo, trans, diag, matrixAD, vectorXD, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        vectorXRef.copyFrom(mBLASData.L2_dTRSV_o_UN);
        verifyMatrix(vectorXRef, vectorXD);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXD.copyFrom(mBLASData.L2_dTRSV_x_n1);
        mBLAS.DTRSV(uplo, trans, diag, matrixAD, vectorXD, incX);
        vectorXRef.copyFrom(mBLASData.L2_dTRSV_o_UT);
        verifyMatrix(vectorXRef, vectorXD);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXD.copyFrom(mBLASData.L2_dTRSV_x_n1);
        mBLAS.DTRSV(uplo, trans, diag, matrixAD, vectorXD, incX);
        vectorXRef.copyFrom(mBLASData.L2_dTRSV_o_UH);
        verifyMatrix(vectorXRef, vectorXD);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorXD.copyFrom(mBLASData.L2_dTRSV_x_n2);

        mBLAS.DTRSV(uplo, trans, diag, matrixAD, vectorXD, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_dTRSV_o_UN2);
        verifyMatrix(vectorXRef, vectorXD);

        mRS.finish();
        checkError();
    }

    public void test_L2_CTRSV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        matrixAC.copyFrom(mBLASData.L2_cTRSV_A_nn);
        vectorXC.copyFrom(mBLASData.L2_cTRSV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.CTRSV(uplo, trans, diag, matrixAC, vectorXC, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        vectorXRef.copyFrom(mBLASData.L2_cTRSV_o_UN);
        verifyMatrix(vectorXRef, vectorXC);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXC.copyFrom(mBLASData.L2_cTRSV_x_n1);
        mBLAS.CTRSV(uplo, trans, diag, matrixAC, vectorXC, incX);
        vectorXRef.copyFrom(mBLASData.L2_cTRSV_o_UT);
        verifyMatrix(vectorXRef, vectorXC);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXC.copyFrom(mBLASData.L2_cTRSV_x_n1);
        mBLAS.CTRSV(uplo, trans, diag, matrixAC, vectorXC, incX);
        vectorXRef.copyFrom(mBLASData.L2_cTRSV_o_UH);
        verifyMatrix(vectorXRef, vectorXC);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorXC.copyFrom(mBLASData.L2_cTRSV_x_n2);

        mBLAS.CTRSV(uplo, trans, diag, matrixAC, vectorXC, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_cTRSV_o_UN2);
        verifyMatrix(vectorXRef, vectorXC);

        mRS.finish();
        checkError();
    }

    public void test_L2_ZTRSV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        matrixAZ.copyFrom(mBLASData.L2_zTRSV_A_nn);
        vectorXZ.copyFrom(mBLASData.L2_zTRSV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.ZTRSV(uplo, trans, diag, matrixAZ, vectorXZ, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        vectorXRef.copyFrom(mBLASData.L2_zTRSV_o_UN);
        verifyMatrix(vectorXRef, vectorXZ);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXZ.copyFrom(mBLASData.L2_zTRSV_x_n1);
        mBLAS.ZTRSV(uplo, trans, diag, matrixAZ, vectorXZ, incX);
        vectorXRef.copyFrom(mBLASData.L2_zTRSV_o_UT);
        verifyMatrix(vectorXRef, vectorXZ);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXZ.copyFrom(mBLASData.L2_zTRSV_x_n1);
        mBLAS.ZTRSV(uplo, trans, diag, matrixAZ, vectorXZ, incX);
        vectorXRef.copyFrom(mBLASData.L2_zTRSV_o_UH);
        verifyMatrix(vectorXRef, vectorXZ);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorXZ.copyFrom(mBLASData.L2_zTRSV_x_n2);

        mBLAS.ZTRSV(uplo, trans, diag, matrixAZ, vectorXZ, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_zTRSV_o_UN2);
        verifyMatrix(vectorXRef, vectorXZ);

        mRS.finish();
        checkError();
    }


    private void xTBSV_API_test(int Uplo, int TransA, int Diag, int K, int incX, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                Element elemA = matA.getType().getElement();
                if (validateTRMV(elemA, Uplo, TransA, Diag, matA, vecX, incX) && K >= 0) {
                    try {
                        if (elemA.isCompatible(Element.F32(mRS))) {
                            mBLAS.STBSV(Uplo, TransA, Diag, K, matA, vecX, incX);
                        } else if (elemA.isCompatible(Element.F64(mRS))) {
                            mBLAS.DTBSV(Uplo, TransA, Diag, K, matA, vecX, incX);
                        } else if (elemA.isCompatible(Element.F32_2(mRS))) {
                            mBLAS.CTBSV(Uplo, TransA, Diag, K, matA, vecX, incX);
                        } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                            mBLAS.ZTBSV(Uplo, TransA, Diag, K, matA, vecX, incX);
                        }
                    } catch (RSRuntimeException e) {
                        fail("should NOT throw RSRuntimeException");
                    }
                } else {
                    try {
                        mBLAS.STBSV(Uplo, TransA, Diag, K, matA, vecX, incX);
                        fail("should throw RSRuntimeException for STBSV");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.DTBSV(Uplo, TransA, Diag, K, matA, vecX, incX);
                        fail("should throw RSRuntimeException for DTBSV");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.CTBSV(Uplo, TransA, Diag, K, matA, vecX, incX);
                        fail("should throw RSRuntimeException for CTBSV");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.ZTBSV(Uplo, TransA, Diag, K, matA, vecX, incX);
                        fail("should throw RSRuntimeException for ZTBSV");
                    } catch (RSRuntimeException e) {
                    }
                }
            }
        }
    }

    public void L2_xTBSV_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int TransA : mTranspose) {
                for (int Diag : mDiag) {
                    for (int K : mK) {
                        for (int incX : mInc) {
                            xTBSV_API_test(Uplo, TransA, Diag, K, incX, mMatrix);
                        }
                    }
                }
            }
        }
    }

    public void test_L2_STBSV_API() {
        L2_xTBSV_API(mMatrixS);
    }

    public void test_L2_DTBSV_API() {
        L2_xTBSV_API(mMatrixD);
    }

    public void test_L2_CTBSV_API() {
        L2_xTBSV_API(mMatrixC);
    }

    public void test_L2_ZTBSV_API() {
        L2_xTBSV_API(mMatrixZ);
    }

    public void test_L2_STBSV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        matrixAS.copy2DRangeFrom(0, 0, mBLASData.KL + 1, mBLASData.dN, mBLASData.L2_sTBSV_A_nn);
        vectorXS.copyFrom(mBLASData.L2_sTBSV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.STBSV(uplo, trans, diag, mBLASData.KL, matrixAS, vectorXS, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        vectorXRef.copyFrom(mBLASData.L2_sTBSV_o_UN);
        verifyMatrix(vectorXRef, vectorXS);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXS.copyFrom(mBLASData.L2_sTBSV_x_n1);
        mBLAS.STBSV(uplo, trans, diag, mBLASData.KL, matrixAS, vectorXS, incX);
        vectorXRef.copyFrom(mBLASData.L2_sTBSV_o_UT);
        verifyMatrix(vectorXRef, vectorXS);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXS.copyFrom(mBLASData.L2_sTBSV_x_n1);
        mBLAS.STBSV(uplo, trans, diag, mBLASData.KL, matrixAS, vectorXS, incX);
        vectorXRef.copyFrom(mBLASData.L2_sTBSV_o_UH);
        verifyMatrix(vectorXRef, vectorXS);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorXS.copyFrom(mBLASData.L2_sTBSV_x_n2);

        mBLAS.STBSV(uplo, trans, diag, mBLASData.KL, matrixAS, vectorXS, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_sTBSV_o_UN2);
        verifyMatrix(vectorXRef, vectorXS);

        mRS.finish();
        checkError();
    }

    public void test_L2_DTBSV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        matrixAD.copy2DRangeFrom(0, 0, mBLASData.KL + 1, mBLASData.dN, mBLASData.L2_dTBSV_A_nn);
        vectorXD.copyFrom(mBLASData.L2_dTBSV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.DTBSV(uplo, trans, diag, mBLASData.KL, matrixAD, vectorXD, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        vectorXRef.copyFrom(mBLASData.L2_dTBSV_o_UN);
        verifyMatrix(vectorXRef, vectorXD);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXD.copyFrom(mBLASData.L2_dTBSV_x_n1);
        mBLAS.DTBSV(uplo, trans, diag, mBLASData.KL, matrixAD, vectorXD, incX);
        vectorXRef.copyFrom(mBLASData.L2_dTBSV_o_UT);
        verifyMatrix(vectorXRef, vectorXD);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXD.copyFrom(mBLASData.L2_dTBSV_x_n1);
        mBLAS.DTBSV(uplo, trans, diag, mBLASData.KL, matrixAD, vectorXD, incX);
        vectorXRef.copyFrom(mBLASData.L2_dTBSV_o_UH);
        verifyMatrix(vectorXRef, vectorXD);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorXD.copyFrom(mBLASData.L2_dTBSV_x_n2);

        mBLAS.DTBSV(uplo, trans, diag, mBLASData.KL, matrixAD, vectorXD, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_dTBSV_o_UN2);
        verifyMatrix(vectorXRef, vectorXD);

        mRS.finish();
        checkError();
    }

    public void test_L2_CTBSV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        matrixAC.copy2DRangeFrom(0, 0, mBLASData.KL + 1, mBLASData.dN, mBLASData.L2_cTBSV_A_nn);
        vectorXC.copyFrom(mBLASData.L2_cTBSV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.CTBSV(uplo, trans, diag, mBLASData.KL, matrixAC, vectorXC, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        vectorXRef.copyFrom(mBLASData.L2_cTBSV_o_UN);
        verifyMatrix(vectorXRef, vectorXC);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXC.copyFrom(mBLASData.L2_cTBSV_x_n1);
        mBLAS.CTBSV(uplo, trans, diag, mBLASData.KL, matrixAC, vectorXC, incX);
        vectorXRef.copyFrom(mBLASData.L2_cTBSV_o_UT);
        verifyMatrix(vectorXRef, vectorXC);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXC.copyFrom(mBLASData.L2_cTBSV_x_n1);
        mBLAS.CTBSV(uplo, trans, diag, mBLASData.KL, matrixAC, vectorXC, incX);
        vectorXRef.copyFrom(mBLASData.L2_cTBSV_o_UH);
        verifyMatrix(vectorXRef, vectorXC);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorXC.copyFrom(mBLASData.L2_cTBSV_x_n2);

        mBLAS.CTBSV(uplo, trans, diag, mBLASData.KL, matrixAC, vectorXC, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_cTBSV_o_UN2);
        verifyMatrix(vectorXRef, vectorXC);

        mRS.finish();
        checkError();
    }

    public void test_L2_ZTBSV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        matrixAZ.copy2DRangeFrom(0, 0, mBLASData.KL + 1, mBLASData.dN, mBLASData.L2_zTBSV_A_nn);
        vectorXZ.copyFrom(mBLASData.L2_zTBSV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.ZTBSV(uplo, trans, diag, mBLASData.KL, matrixAZ, vectorXZ, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        vectorXRef.copyFrom(mBLASData.L2_zTBSV_o_UN);
        verifyMatrix(vectorXRef, vectorXZ);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXZ.copyFrom(mBLASData.L2_zTBSV_x_n1);
        mBLAS.ZTBSV(uplo, trans, diag, mBLASData.KL, matrixAZ, vectorXZ, incX);
        vectorXRef.copyFrom(mBLASData.L2_zTBSV_o_UT);
        verifyMatrix(vectorXRef, vectorXZ);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXZ.copyFrom(mBLASData.L2_zTBSV_x_n1);
        mBLAS.ZTBSV(uplo, trans, diag, mBLASData.KL, matrixAZ, vectorXZ, incX);
        vectorXRef.copyFrom(mBLASData.L2_zTBSV_o_UH);
        verifyMatrix(vectorXRef, vectorXZ);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorXZ.copyFrom(mBLASData.L2_zTBSV_x_n2);

        mBLAS.ZTBSV(uplo, trans, diag, mBLASData.KL, matrixAZ, vectorXZ, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_zTBSV_o_UN2);
        verifyMatrix(vectorXRef, vectorXZ);

        mRS.finish();
        checkError();
    }


    private void xTPSV_API_test(int Uplo, int TransA, int Diag, int incX, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                Element elemA = matA.getType().getElement();
                if (validateTPMV(elemA, Uplo, TransA, Diag, matA, vecX, incX)) {
                    try {
                        if (elemA.isCompatible(Element.F32(mRS))) {
                            mBLAS.STPSV(Uplo, TransA, Diag, matA, vecX, incX);
                        } else if (elemA.isCompatible(Element.F64(mRS))) {
                            mBLAS.DTPSV(Uplo, TransA, Diag, matA, vecX, incX);
                        } else if (elemA.isCompatible(Element.F32_2(mRS))) {
                            mBLAS.CTPSV(Uplo, TransA, Diag, matA, vecX, incX);
                        } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                            mBLAS.ZTPSV(Uplo, TransA, Diag, matA, vecX, incX);
                        }
                    } catch (RSRuntimeException e) {
                        fail("should NOT throw RSRuntimeException");
                    }
                } else {
                    try {
                        mBLAS.STPSV(Uplo, TransA, Diag, matA, vecX, incX);
                        fail("should throw RSRuntimeException for STPSV");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.DTPSV(Uplo, TransA, Diag, matA, vecX, incX);
                        fail("should throw RSRuntimeException for DTPSV");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.CTPSV(Uplo, TransA, Diag, matA, vecX, incX);
                        fail("should throw RSRuntimeException for CTPSV");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.ZTPSV(Uplo, TransA, Diag, matA, vecX, incX);
                        fail("should throw RSRuntimeException for ZTPSV");
                    } catch (RSRuntimeException e) {
                    }
                }
            }
        }
    }

    public void L2_xTPSV_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int TransA : mTranspose) {
                for (int Diag : mDiag) {
                    for (int incX : mInc) {
                        xTPSV_API_test(Uplo, TransA, Diag, incX, mMatrix);
                    }
                }
            }
        }
    }

    public void test_L2_STPSV_API() {
        L2_xTPSV_API(mMatrixS);
    }

    public void test_L2_DTPSV_API() {
        L2_xTPSV_API(mMatrixD);
    }

    public void test_L2_CTPSV_API() {
        L2_xTPSV_API(mMatrixC);
    }

    public void test_L2_ZTPSV_API() {
        L2_xTPSV_API(mMatrixZ);
    }

    public void test_L2_STPSV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), N * (N+1) / 2, 1));
        Allocation vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), N, 1));
        matrixAS.copyFrom(mBLASData.L2_sTRSV_A_nn_pu);
        vectorXS.copyFrom(mBLASData.L2_sTRSV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.STPSV(uplo, trans, diag, matrixAS, vectorXS, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), N, 1));
        vectorXRef.copyFrom(mBLASData.L2_sTRSV_o_UN);
        verifyMatrix(vectorXRef, vectorXS);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXS.copyFrom(mBLASData.L2_sTRSV_x_n1);
        mBLAS.STPSV(uplo, trans, diag, matrixAS, vectorXS, incX);
        vectorXRef.copyFrom(mBLASData.L2_sTRSV_o_UT);
        verifyMatrix(vectorXRef, vectorXS);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXS.copyFrom(mBLASData.L2_sTRSV_x_n1);
        mBLAS.STPSV(uplo, trans, diag, matrixAS, vectorXS, incX);
        vectorXRef.copyFrom(mBLASData.L2_sTRSV_o_UH);
        verifyMatrix(vectorXRef, vectorXS);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (N - 1) * incX;
        vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorXS.copyFrom(mBLASData.L2_sTRSV_x_n2);

        mBLAS.STPSV(uplo, trans, diag, matrixAS, vectorXS, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_sTRSV_o_UN2);
        verifyMatrix(vectorXRef, vectorXS);

        mRS.finish();
        checkError();
    }

    public void test_L2_DTPSV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), N * (N+1) / 2, 1));
        Allocation vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), N, 1));
        matrixAD.copyFrom(mBLASData.L2_dTRSV_A_nn_pu);
        vectorXD.copyFrom(mBLASData.L2_dTRSV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.DTPSV(uplo, trans, diag, matrixAD, vectorXD, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), N, 1));
        vectorXRef.copyFrom(mBLASData.L2_dTRSV_o_UN);
        verifyMatrix(vectorXRef, vectorXD);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXD.copyFrom(mBLASData.L2_dTRSV_x_n1);
        mBLAS.DTPSV(uplo, trans, diag, matrixAD, vectorXD, incX);
        vectorXRef.copyFrom(mBLASData.L2_dTRSV_o_UT);
        verifyMatrix(vectorXRef, vectorXD);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXD.copyFrom(mBLASData.L2_dTRSV_x_n1);
        mBLAS.DTPSV(uplo, trans, diag, matrixAD, vectorXD, incX);
        vectorXRef.copyFrom(mBLASData.L2_dTRSV_o_UH);
        verifyMatrix(vectorXRef, vectorXD);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (N - 1) * incX;
        vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorXD.copyFrom(mBLASData.L2_dTRSV_x_n2);

        mBLAS.DTPSV(uplo, trans, diag, matrixAD, vectorXD, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_dTRSV_o_UN2);
        verifyMatrix(vectorXRef, vectorXD);

        mRS.finish();
        checkError();
    }

    public void test_L2_CTPSV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), N * (N+1) / 2, 1));
        Allocation vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), N, 1));
        matrixAC.copyFrom(mBLASData.L2_cTRSV_A_nn_pu);
        vectorXC.copyFrom(mBLASData.L2_cTRSV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.CTPSV(uplo, trans, diag, matrixAC, vectorXC, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), N, 1));
        vectorXRef.copyFrom(mBLASData.L2_cTRSV_o_UN);
        verifyMatrix(vectorXRef, vectorXC);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXC.copyFrom(mBLASData.L2_cTRSV_x_n1);
        mBLAS.CTPSV(uplo, trans, diag, matrixAC, vectorXC, incX);
        vectorXRef.copyFrom(mBLASData.L2_cTRSV_o_UT);
        verifyMatrix(vectorXRef, vectorXC);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXC.copyFrom(mBLASData.L2_cTRSV_x_n1);
        mBLAS.CTPSV(uplo, trans, diag, matrixAC, vectorXC, incX);
        vectorXRef.copyFrom(mBLASData.L2_cTRSV_o_UH);
        verifyMatrix(vectorXRef, vectorXC);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (N - 1) * incX;
        vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorXC.copyFrom(mBLASData.L2_cTRSV_x_n2);

        mBLAS.CTPSV(uplo, trans, diag, matrixAC, vectorXC, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_cTRSV_o_UN2);
        verifyMatrix(vectorXRef, vectorXC);

        mRS.finish();
        checkError();
    }

    public void test_L2_ZTPSV_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;
        int incX = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), N * (N+1) / 2, 1));
        Allocation vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), N, 1));
        matrixAZ.copyFrom(mBLASData.L2_zTRSV_A_nn_pu);
        vectorXZ.copyFrom(mBLASData.L2_zTRSV_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.ZTPSV(uplo, trans, diag, matrixAZ, vectorXZ, incX);
        Allocation vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), N, 1));
        vectorXRef.copyFrom(mBLASData.L2_zTRSV_o_UN);
        verifyMatrix(vectorXRef, vectorXZ);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload vector X, since it was overwritten by BLAS.
        vectorXZ.copyFrom(mBLASData.L2_zTRSV_x_n1);
        mBLAS.ZTPSV(uplo, trans, diag, matrixAZ, vectorXZ, incX);
        vectorXRef.copyFrom(mBLASData.L2_zTRSV_o_UT);
        verifyMatrix(vectorXRef, vectorXZ);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        vectorXZ.copyFrom(mBLASData.L2_zTRSV_x_n1);
        mBLAS.ZTPSV(uplo, trans, diag, matrixAZ, vectorXZ, incX);
        vectorXRef.copyFrom(mBLASData.L2_zTRSV_o_UH);
        verifyMatrix(vectorXRef, vectorXZ);

        // Test for incX = 2;
        trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        incX = 2;
        int dimX = 1 + (N - 1) * incX;
        vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorXZ.copyFrom(mBLASData.L2_zTRSV_x_n2);

        mBLAS.ZTPSV(uplo, trans, diag, matrixAZ, vectorXZ, incX);
        vectorXRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorXRef.copyFrom(mBLASData.L2_zTRSV_o_UN2);
        verifyMatrix(vectorXRef, vectorXZ);

        mRS.finish();
        checkError();
    }


    private boolean validateGER(Element e, Allocation X, int incX, Allocation Y, int incY, Allocation A) {
        if (!A.getType().getElement().isCompatible(e) ||
            !X.getType().getElement().isCompatible(e) ||
            !Y.getType().getElement().isCompatible(e) ) {
            return false;
        }

        if (X.getType().getY() > 1 || Y.getType().getY() > 1) {
            return false;
        }

        int M = A.getType().getY();
        int N = A.getType().getX();

        if (N < 1 || M < 1) {
            return false;
        }
        if (incX <= 0 || incY <= 0) {
            return false;
        }
        int expectedXDim = 1 + (M - 1) * incX;
        if (X.getType().getX() != expectedXDim) {
            return false;
        }
        int expectedYDim = 1 + (N - 1) * incY;
        if (Y.getType().getX() != expectedYDim) {
            return false;
        }
        return true;
    }


    private void xGER_API_test(int incX, int incY, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                for (Allocation vecY : mMatrix) {
                    if (!validateVecInput(vecY)) {
                        continue;
                    }
                    Element elemA = matA.getType().getElement();
                    if (validateGER(elemA, vecX, incX, vecY, incY, matA)) {
                        try {
                            if (elemA.isCompatible(Element.F32(mRS))) {
                                mBLAS.SGER(alphaS, vecX, incX, vecY, incY, matA);
                            } else if (elemA.isCompatible(Element.F64(mRS))) {
                                mBLAS.DGER(alphaD, vecX, incX, vecY, incY, matA);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.SGER(alphaS, vecX, incX, vecY, incY, matA);
                            fail("should throw RSRuntimeException for SGER");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.DGER(alphaD, vecX, incX, vecY, incY, matA);
                            fail("should throw RSRuntimeException for DGER");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    private void L2_xGER_API(ArrayList<Allocation> mMatrix) {
        for (int incX : mInc) {
            for (int incY : mInc) {
                xGERU_API_test(incX, incY, mMatrix);
            }
        }
    }

    public void test_L2_SGER_API() {
        L2_xGER_API(mMatrixS);
    }

    public void test_L2_DGER_API() {
        L2_xGER_API(mMatrixD);
    }

    public void test_L2_SGER_Correctness() {
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dM));
        Allocation vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dM, 1));
        Allocation vectorYS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        matrixAS.copyFrom(mBLASData.L2_sGER_A_mn);
        vectorXS.copyFrom(mBLASData.L2_sGER_x_m1);
        vectorYS.copyFrom(mBLASData.L2_sGER_y_n1);

        // Test for the default case: NO_TRANS
        mBLAS.SGER(alphaS, vectorXS, incX, vectorYS, incY, matrixAS);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dM));
        matrixARef.copyFrom(mBLASData.L2_sGER_o_N);
        verifyMatrix(matrixARef, matrixAS);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dM - 1) * incX;
        int dimY = 1 + (mBLASData.dN - 1) * incY;
        vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorYS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimY, 1));
        vectorXS.copyFrom(mBLASData.L2_sGER_x_m2);
        vectorYS.copyFrom(mBLASData.L2_sGER_y_n2);
        matrixAS.copyFrom(mBLASData.L2_sGER_A_mn);

        mBLAS.SGER(alphaS, vectorXS, incX, vectorYS, incY, matrixAS);
        verifyMatrix(matrixARef, matrixAS);

        mRS.finish();
        checkError();
    }

    public void test_L2_DGER_Correctness() {
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dM));
        Allocation vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dM, 1));
        Allocation vectorYD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        matrixAD.copyFrom(mBLASData.L2_dGER_A_mn);
        vectorXD.copyFrom(mBLASData.L2_dGER_x_m1);
        vectorYD.copyFrom(mBLASData.L2_dGER_y_n1);

        // Test for the default case: NO_TRANS
        mBLAS.DGER(alphaD, vectorXD, incX, vectorYD, incY, matrixAD);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dM));
        matrixARef.copyFrom(mBLASData.L2_dGER_o_N);
        verifyMatrix(matrixARef, matrixAD);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dM - 1) * incX;
        int dimY = 1 + (mBLASData.dN - 1) * incY;
        vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorYD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimY, 1));
        vectorXD.copyFrom(mBLASData.L2_dGER_x_m2);
        vectorYD.copyFrom(mBLASData.L2_dGER_y_n2);
        matrixAD.copyFrom(mBLASData.L2_dGER_A_mn);

        mBLAS.DGER(alphaD, vectorXD, incX, vectorYD, incY, matrixAD);
        verifyMatrix(matrixARef, matrixAD);

        mRS.finish();
        checkError();
    }


    private boolean validateGERU(Element e, Allocation X, int incX, Allocation Y, int incY, Allocation A) {
        if (!A.getType().getElement().isCompatible(e) ||
            !X.getType().getElement().isCompatible(e) ||
            !Y.getType().getElement().isCompatible(e)) {
            return false;
        }
        if (X.getType().getY() > 1 || Y.getType().getY() > 1) {
            return false;
        }

        int M = A.getType().getY();
        int N = A.getType().getX();
        if (incX <= 0 || incY <= 0) {
            return false;
        }
        int expectedXDim = 1 + (M - 1) * incX;
        if (X.getType().getX() != expectedXDim) {
            return false;
        }
        int expectedYDim = 1 + (N - 1) * incY;
        if (Y.getType().getX() != expectedYDim) {
            return false;
        }
        return true;
    }

    private void xGERU_API_test(int incX, int incY, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                for (Allocation vecY : mMatrix) {
                    if (!validateVecInput(vecY)) {
                        continue;
                    }
                    Element elemA = matA.getType().getElement();
                    if (validateGERU(elemA, vecX, incX, vecY, incY, matA)) {
                        try {
                            if (elemA.isCompatible(Element.F32_2(mRS))) {
                                mBLAS.CGERU(alphaC, vecX, incX, vecY, incY, matA);
                            } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                                mBLAS.ZGERU(alphaZ, vecX, incX, vecY, incY, matA);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.CGERU(alphaC, vecX, incX, vecY, incY, matA);
                            fail("should throw RSRuntimeException for CGERU");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.ZGERU(alphaZ, vecX, incX, vecY, incY, matA);
                            fail("should throw RSRuntimeException for ZGERU");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    private void L2_xGERU_API(ArrayList<Allocation> mMatrix) {
        for (int incX : mInc) {
            for (int incY : mInc) {
                xGERU_API_test(incX, incY, mMatrix);
            }
        }
    }

    public void test_L2_CGERU_API() {
        L2_xGERU_API(mMatrixC);
    }

    public void test_L2_ZGERU_API() {
        L2_xGERU_API(mMatrixZ);
    }

    public void test_L2_CGERU_Correctness() {
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dM));
        Allocation vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dM, 1));
        Allocation vectorYC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        matrixAC.copyFrom(mBLASData.L2_cGERU_A_mn);
        vectorXC.copyFrom(mBLASData.L2_cGERU_x_m1);
        vectorYC.copyFrom(mBLASData.L2_cGERU_y_n1);

        // Test for the default case: NO_TRANS
        mBLAS.CGERU(alphaC, vectorXC, incX, vectorYC, incY, matrixAC);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixARef.copyFrom(mBLASData.L2_cGERU_o_N);
        verifyMatrix(matrixARef, matrixAC);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dM - 1) * incX;
        int dimY = 1 + (mBLASData.dN - 1) * incY;
        vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorYC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimY, 1));
        vectorXC.copyFrom(mBLASData.L2_cGERU_x_m2);
        vectorYC.copyFrom(mBLASData.L2_cGERU_y_n2);
        matrixAC.copyFrom(mBLASData.L2_cGERU_A_mn);

        mBLAS.CGERU(alphaC, vectorXC, incX, vectorYC, incY, matrixAC);
        verifyMatrix(matrixARef, matrixAC);

        mRS.finish();
        checkError();
    }

    public void test_L2_ZGERU_Correctness() {
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dM));
        Allocation vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dM, 1));
        Allocation vectorYZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        matrixAZ.copyFrom(mBLASData.L2_zGERU_A_mn);
        vectorXZ.copyFrom(mBLASData.L2_zGERU_x_m1);
        vectorYZ.copyFrom(mBLASData.L2_zGERU_y_n1);

        // Test for the default case: NO_TRANS
        mBLAS.ZGERU(alphaZ, vectorXZ, incX, vectorYZ, incY, matrixAZ);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixARef.copyFrom(mBLASData.L2_zGERU_o_N);
        verifyMatrix(matrixARef, matrixAZ);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dM - 1) * incX;
        int dimY = 1 + (mBLASData.dN - 1) * incY;
        vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorYZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimY, 1));
        vectorXZ.copyFrom(mBLASData.L2_zGERU_x_m2);
        vectorYZ.copyFrom(mBLASData.L2_zGERU_y_n2);
        matrixAZ.copyFrom(mBLASData.L2_zGERU_A_mn);

        mBLAS.ZGERU(alphaZ, vectorXZ, incX, vectorYZ, incY, matrixAZ);
        verifyMatrix(matrixARef, matrixAZ);

        mRS.finish();
        checkError();
    }



    private void xGERC_API_test(int incX, int incY, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                for (Allocation vecY : mMatrix) {
                    if (!validateVecInput(vecY)) {
                        continue;
                    }
                    Element elemA = matA.getType().getElement();
                    if (validateGERU(elemA, vecX, incX, vecY, incY, matA)) {
                        try {
                            if (elemA.isCompatible(Element.F32_2(mRS))) {
                                mBLAS.CGERC(alphaC, vecX, incX, vecY, incY, matA);
                            } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                                mBLAS.ZGERC(alphaZ, vecX, incX, vecY, incY, matA);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.CGERC(alphaC, vecX, incX, vecY, incY, matA);
                            fail("should throw RSRuntimeException for CGERC");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.ZGERC(alphaZ, vecX, incX, vecY, incY, matA);
                            fail("should throw RSRuntimeException for ZGERC");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    private void L2_xGERC_API(ArrayList<Allocation> mMatrix) {
        for (int incX : mInc) {
            for (int incY : mInc) {
                xGERC_API_test(incX, incY, mMatrix);
            }
        }
    }

    public void test_L2_CGERC_API() {
        L2_xGERC_API(mMatrixC);
    }

    public void test_L2_ZGERC_API() {
        L2_xGERC_API(mMatrixZ);
    }

    public void test_L2_CGERC_Correctness() {
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dM));
        Allocation vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dM, 1));
        Allocation vectorYC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        matrixAC.copyFrom(mBLASData.L2_cGERC_A_mn);
        vectorXC.copyFrom(mBLASData.L2_cGERC_x_m1);
        vectorYC.copyFrom(mBLASData.L2_cGERC_y_n1);

        // Test for the default case: NO_TRANS
        mBLAS.CGERC(alphaC, vectorXC, incX, vectorYC, incY, matrixAC);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixARef.copyFrom(mBLASData.L2_cGERC_o_N);
        verifyMatrix(matrixARef, matrixAC);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dM - 1) * incX;
        int dimY = 1 + (mBLASData.dN - 1) * incY;
        vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorYC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimY, 1));
        vectorXC.copyFrom(mBLASData.L2_cGERC_x_m2);
        vectorYC.copyFrom(mBLASData.L2_cGERC_y_n2);
        matrixAC.copyFrom(mBLASData.L2_cGERC_A_mn);

        mBLAS.CGERC(alphaC, vectorXC, incX, vectorYC, incY, matrixAC);
        verifyMatrix(matrixARef, matrixAC);

        mRS.finish();
        checkError();
    }

    public void test_L2_ZGERC_Correctness() {
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dM));
        Allocation vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dM, 1));
        Allocation vectorYZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        matrixAZ.copyFrom(mBLASData.L2_zGERC_A_mn);
        vectorXZ.copyFrom(mBLASData.L2_zGERC_x_m1);
        vectorYZ.copyFrom(mBLASData.L2_zGERC_y_n1);

        // Test for the default case: NO_TRANS
        mBLAS.ZGERC(alphaZ, vectorXZ, incX, vectorYZ, incY, matrixAZ);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixARef.copyFrom(mBLASData.L2_zGERC_o_N);
        verifyMatrix(matrixARef, matrixAZ);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dM - 1) * incX;
        int dimY = 1 + (mBLASData.dN - 1) * incY;
        vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorYZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimY, 1));
        vectorXZ.copyFrom(mBLASData.L2_zGERC_x_m2);
        vectorYZ.copyFrom(mBLASData.L2_zGERC_y_n2);
        matrixAZ.copyFrom(mBLASData.L2_zGERC_A_mn);

        mBLAS.ZGERC(alphaZ, vectorXZ, incX, vectorYZ, incY, matrixAZ);
        verifyMatrix(matrixARef, matrixAZ);

        mRS.finish();
        checkError();
    }


    private void xHER_API_test(int Uplo, int incX, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                Element elemA = matA.getType().getElement();
                if (validateSYR(elemA, Uplo, vecX, incX, matA)) {
                    try {
                        if (elemA.isCompatible(Element.F32_2(mRS))) {
                            mBLAS.CHER(Uplo, alphaS, vecX, incX, matA);
                        } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                            mBLAS.ZHER(Uplo, alphaD, vecX, incX, matA);
                        }
                    } catch (RSRuntimeException e) {
                        fail("should NOT throw RSRuntimeException");
                    }
                } else {
                    try {
                        mBLAS.CHER(Uplo, alphaS, vecX, incX, matA);
                        fail("should throw RSRuntimeException for CHER");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.ZHER(Uplo, alphaD, vecX, incX, matA);
                        fail("should throw RSRuntimeException for ZHER");
                    } catch (RSRuntimeException e) {
                    }
                }
            }
        }
    }

    public void L2_xHER_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int incX : mInc) {
                xHER_API_test(Uplo, incX, mMatrix);
            }
        }
    }

    public void test_L2_CHER_API() {
        L2_xHER_API(mMatrixC);
    }

    public void test_L2_ZHER_API() {
        L2_xHER_API(mMatrixZ);
    }

    public void test_L2_CHER_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        matrixAC.copyFrom(mBLASData.L2_cHER_A_nn);
        vectorXC.copyFrom(mBLASData.L2_cHER_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.CHER(uplo, alphaS, vectorXC, incX, matrixAC);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixARef.copyFrom(mBLASData.L2_cHER_o_N);
        verifyMatrix(matrixARef, matrixAC, true);

        // Test for incX = 2;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorXC.copyFrom(mBLASData.L2_cHER_x_n2);
        matrixAC.copyFrom(mBLASData.L2_cHER_A_nn);

        mBLAS.CHER(uplo, alphaS, vectorXC, incX, matrixAC);
        verifyMatrix(matrixARef, matrixAC, true);

        mRS.finish();
        checkError();
    }

    public void test_L2_ZHER_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        matrixAZ.copyFrom(mBLASData.L2_zHER_A_nn);
        vectorXZ.copyFrom(mBLASData.L2_zHER_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.ZHER(uplo, alphaD, vectorXZ, incX, matrixAZ);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixARef.copyFrom(mBLASData.L2_zHER_o_N);
        verifyMatrix(matrixARef, matrixAZ, true);

        // Test for incX = 2;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorXZ.copyFrom(mBLASData.L2_zHER_x_n2);
        matrixAZ.copyFrom(mBLASData.L2_zHER_A_nn);

        mBLAS.ZHER(uplo, alphaD, vectorXZ, incX, matrixAZ);
        verifyMatrix(matrixARef, matrixAZ, true);

        mRS.finish();
        checkError();
    }


    private void xHPR_API_test(int Uplo, int incX, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                Element elemA = matA.getType().getElement();
                if (validateSPR(elemA, Uplo, vecX, incX, matA)) {
                    try {
                        if (elemA.isCompatible(Element.F32_2(mRS))) {
                            mBLAS.CHPR(Uplo, alphaS, vecX, incX, matA);
                        } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                            mBLAS.ZHPR(Uplo, alphaD, vecX, incX, matA);
                        }
                    } catch (RSRuntimeException e) {
                        fail("should NOT throw RSRuntimeException");
                    }
                } else {
                    try {
                        mBLAS.CHPR(Uplo, alphaS, vecX, incX, matA);
                        fail("should throw RSRuntimeException for CHPR");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.ZHPR(Uplo, alphaD, vecX, incX, matA);
                        fail("should throw RSRuntimeException for ZHPR");
                    } catch (RSRuntimeException e) {
                    }
                }
            }
        }
    }

    public void L2_xHPR_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int incX : mInc) {
                xHPR_API_test(Uplo, incX, mMatrix);
            }
        }
    }

    public void test_L2_CHPR_API() {
        L2_xHPR_API(mMatrixC);
    }

    public void test_L2_ZHPR_API() {
        L2_xHPR_API(mMatrixZ);
    }

    public void test_L2_CHPR_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), N * (N+1) / 2, 1));
        Allocation vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), N, 1));
        matrixAC.copyFrom(mBLASData.L2_cHER_A_nn_pu);
        vectorXC.copyFrom(mBLASData.L2_cHER_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.CHPR(uplo, alphaS, vectorXC, incX, matrixAC);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), N * (N+1) / 2, 1));
        matrixARef.copyFrom(mBLASData.L2_cHER_o_N_pu);
        verifyMatrix(matrixARef, matrixAC, true);

        // Test for incX = 2;
        incX = 2;
        int dimX = 1 + (N - 1) * incX;
        vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorXC.copyFrom(mBLASData.L2_cHER_x_n2);
        matrixAC.copyFrom(mBLASData.L2_cHER_A_nn_pu);

        mBLAS.CHPR(uplo, alphaS, vectorXC, incX, matrixAC);
        verifyMatrix(matrixARef, matrixAC, true);

        mRS.finish();
        checkError();
    }

    public void test_L2_ZHPR_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), N * (N+1) / 2, 1));
        Allocation vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), N, 1));
        matrixAZ.copyFrom(mBLASData.L2_zHER_A_nn_pu);
        vectorXZ.copyFrom(mBLASData.L2_zHER_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.ZHPR(uplo, alphaD, vectorXZ, incX, matrixAZ);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), N * (N+1) / 2, 1));
        matrixARef.copyFrom(mBLASData.L2_zHER_o_N_pu);
        verifyMatrix(matrixARef, matrixAZ, true);

        // Test for incX = 2;
        incX = 2;
        int dimX = 1 + (N - 1) * incX;
        vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorXZ.copyFrom(mBLASData.L2_zHER_x_n2);
        matrixAZ.copyFrom(mBLASData.L2_zHER_A_nn_pu);

        mBLAS.ZHPR(uplo, alphaD, vectorXZ, incX, matrixAZ);
        verifyMatrix(matrixARef, matrixAZ, true);

        mRS.finish();
        checkError();
    }


    private void xHER2_API_test(int Uplo, int incX, int incY, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                for (Allocation vecY : mMatrix) {
                    if (!validateVecInput(vecY)) {
                        continue;
                    }
                    Element elemA = matA.getType().getElement();
                    if (validateSYR2(elemA, Uplo, vecX, incX, vecY, incY, matA)) {
                        try {
                            if (elemA.isCompatible(Element.F32_2(mRS))) {
                                mBLAS.CHER2(Uplo, alphaC, vecX, incX, vecY, incY, matA);
                            } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                                mBLAS.ZHER2(Uplo, alphaZ, vecX, incX, vecY, incY, matA);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.CHER2(Uplo, alphaC, vecX, incX, vecY, incY, matA);
                            fail("should throw RSRuntimeException for CHER2");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.ZHER2(Uplo, alphaZ, vecX, incX, vecY, incY, matA);
                            fail("should throw RSRuntimeException for ZHER2");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    public void L2_xHER2_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int incX : mInc) {
                xHER2_API_test(Uplo, incX, incX, mMatrix);
            }
        }
    }

    public void test_L2_CHER2_API() {
        L2_xHER2_API(mMatrixC);
    }

    public void test_L2_ZHER2_API() {
        L2_xHER2_API(mMatrixZ);
    }

    public void test_L2_CHER2_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        Allocation vectorYC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, 1));
        matrixAC.copyFrom(mBLASData.L2_cHER2_A_nn);
        vectorXC.copyFrom(mBLASData.L2_cHER2_x_n1);
        vectorYC.copyFrom(mBLASData.L2_cHER2_y_n1);

        // Test for the default case: NO_TRANS
        mBLAS.CHER2(uplo, alphaC, vectorXC, incX, vectorYC, incY, matrixAC);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixARef.copyFrom(mBLASData.L2_cHER2_o_N);
        verifyMatrix(matrixARef, matrixAC, true);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dN - 1) * incY;
        vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorYC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimY, 1));
        vectorXC.copyFrom(mBLASData.L2_cHER2_x_n2);
        vectorYC.copyFrom(mBLASData.L2_cHER2_y_n2);
        matrixAC.copyFrom(mBLASData.L2_cHER2_A_nn);

        mBLAS.CHER2(uplo, alphaC, vectorXC, incX, vectorYC, incY, matrixAC);
        verifyMatrix(matrixARef, matrixAC, true);

        mRS.finish();
        checkError();
    }

    public void test_L2_ZHER2_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        Allocation vectorYZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, 1));
        matrixAZ.copyFrom(mBLASData.L2_zHER2_A_nn);
        vectorXZ.copyFrom(mBLASData.L2_zHER2_x_n1);
        vectorYZ.copyFrom(mBLASData.L2_zHER2_y_n1);

        // Test for the default case: NO_TRANS
        mBLAS.ZHER2(uplo, alphaZ, vectorXZ, incX, vectorYZ, incY, matrixAZ);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixARef.copyFrom(mBLASData.L2_zHER2_o_N);
        verifyMatrix(matrixARef, matrixAZ, true);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dN - 1) * incY;
        vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorYZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimY, 1));
        vectorXZ.copyFrom(mBLASData.L2_zHER2_x_n2);
        vectorYZ.copyFrom(mBLASData.L2_zHER2_y_n2);
        matrixAZ.copyFrom(mBLASData.L2_zHER2_A_nn);

        mBLAS.ZHER2(uplo, alphaZ, vectorXZ, incX, vectorYZ, incY, matrixAZ);
        verifyMatrix(matrixARef, matrixAZ, true);

        mRS.finish();
        checkError();
    }



    private void xHPR2_API_test(int Uplo, int incX, int incY, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                for (Allocation vecY : mMatrix) {
                    if (!validateVecInput(vecY)) {
                        continue;
                    }
                    Element elemA = matA.getType().getElement();
                    if (validateSPR2(elemA, Uplo, vecX, incX, vecY, incY, matA)) {
                        try {
                            if (elemA.isCompatible(Element.F32_2(mRS))) {
                                mBLAS.CHPR2(Uplo, alphaC, vecX, incX, vecY, incY, matA);
                            } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                                mBLAS.ZHPR2(Uplo, alphaZ, vecX, incX, vecY, incY, matA);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.CHPR2(Uplo, alphaC, vecX, incX, vecY, incY, matA);
                            fail("should throw RSRuntimeException for CHPR2");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.ZHPR2(Uplo, alphaZ, vecX, incX, vecY, incY, matA);
                            fail("should throw RSRuntimeException for ZHPR2");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    public void L2_xHPR2_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int incX : mInc) {
                xHPR2_API_test(Uplo, incX, incX, mMatrix);
            }
        }
    }

    public void test_L2_CHPR2_API() {
        L2_xHPR2_API(mMatrixC);
    }

    public void test_L2_ZHPR2_API() {
        L2_xHPR2_API(mMatrixZ);
    }

    public void test_L2_CHPR2_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), N * (N+1) / 2, 1));
        Allocation vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), N, 1));
        Allocation vectorYC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), N, 1));
        matrixAC.copyFrom(mBLASData.L2_cHER2_A_nn_pu);
        vectorXC.copyFrom(mBLASData.L2_cHER2_x_n1);
        vectorYC.copyFrom(mBLASData.L2_cHER2_y_n1);

        // Test for the default case: NO_TRANS
        mBLAS.CHPR2(uplo, alphaC, vectorXC, incX, vectorYC, incY, matrixAC);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), N * (N+1) / 2, 1));
        matrixARef.copyFrom(mBLASData.L2_cHER2_o_N_pu);
        verifyMatrix(matrixARef, matrixAC, true);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (N - 1) * incX;
        int dimY = 1 + (N - 1) * incY;
        vectorXC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimX, 1));
        vectorYC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), dimY, 1));
        vectorXC.copyFrom(mBLASData.L2_cHER2_x_n2);
        vectorYC.copyFrom(mBLASData.L2_cHER2_y_n2);
        matrixAC.copyFrom(mBLASData.L2_cHER2_A_nn_pu);

        mBLAS.CHPR2(uplo, alphaC, vectorXC, incX, vectorYC, incY, matrixAC);
        verifyMatrix(matrixARef, matrixAC, true);

        mRS.finish();
        checkError();
    }

    public void test_L2_ZHPR2_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), N * (N+1) / 2, 1));
        Allocation vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), N, 1));
        Allocation vectorYZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), N, 1));
        matrixAZ.copyFrom(mBLASData.L2_zHER2_A_nn_pu);
        vectorXZ.copyFrom(mBLASData.L2_zHER2_x_n1);
        vectorYZ.copyFrom(mBLASData.L2_zHER2_y_n1);

        // Test for the default case: NO_TRANS
        mBLAS.ZHPR2(uplo, alphaZ, vectorXZ, incX, vectorYZ, incY, matrixAZ);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), N * (N+1) / 2, 1));
        matrixARef.copyFrom(mBLASData.L2_zHER2_o_N_pu);
        verifyMatrix(matrixARef, matrixAZ, true);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (N - 1) * incX;
        int dimY = 1 + (N - 1) * incY;
        vectorXZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimX, 1));
        vectorYZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), dimY, 1));
        vectorXZ.copyFrom(mBLASData.L2_zHER2_x_n2);
        vectorYZ.copyFrom(mBLASData.L2_zHER2_y_n2);
        matrixAZ.copyFrom(mBLASData.L2_zHER2_A_nn_pu);

        mBLAS.ZHPR2(uplo, alphaZ, vectorXZ, incX, vectorYZ, incY, matrixAZ);
        verifyMatrix(matrixARef, matrixAZ, true);

        mRS.finish();
        checkError();
    }



    private boolean validateSYR(Element e, int Uplo, Allocation X, int incX, Allocation A) {
        if (!validateUplo(Uplo)) {
            return false;
        }
        if (!A.getType().getElement().isCompatible(e) ||
            !X.getType().getElement().isCompatible(e)) {
            return false;
        }

        int N = A.getType().getX();

        if (X.getType().getY() > 1) {
            return false;
        }
        if (N != A.getType().getY()) {
            return false;
        }
        if (incX <= 0) {
            return false;
        }
        int expectedXDim = 1 + (N - 1) * incX;
        if (X.getType().getX() != expectedXDim) {
            return false;
        }
        return true;
    }

    private void xSYR_API_test(int Uplo, int incX, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                Element elemA = matA.getType().getElement();
                if (validateSYR(elemA, Uplo, vecX, incX, matA)) {
                    try {
                        if (elemA.isCompatible(Element.F32(mRS))) {
                            mBLAS.SSYR(Uplo, alphaS, vecX, incX, matA);
                        } else if (elemA.isCompatible(Element.F64(mRS))) {
                            mBLAS.DSYR(Uplo, alphaD, vecX, incX, matA);
                        }
                    } catch (RSRuntimeException e) {
                        fail("should NOT throw RSRuntimeException");
                    }
                } else {
                    try {
                        mBLAS.SSYR(Uplo, alphaS, vecX, incX, matA);
                        fail("should throw RSRuntimeException for SSYR");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.DSYR(Uplo, alphaD, vecX, incX, matA);
                        fail("should throw RSRuntimeException for DSYR");
                    } catch (RSRuntimeException e) {
                    }
                }
            }
        }
    }

    public void L2_xSYR_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int incX : mInc) {
                xSYR_API_test(Uplo, incX, mMatrix);
            }
        }
    }

    public void test_L2_SSYR_API() {
        L2_xSYR_API(mMatrixS);
    }

    public void test_L2_DSYR_API() {
        L2_xSYR_API(mMatrixD);
    }

    public void test_L2_SSYR_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        matrixAS.copyFrom(mBLASData.L2_sSYR_A_nn);
        vectorXS.copyFrom(mBLASData.L2_sSYR_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.SSYR(uplo, alphaS, vectorXS, incX, matrixAS);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dN));
        matrixARef.copyFrom(mBLASData.L2_sSYR_o_N);
        verifyMatrix(matrixARef, matrixAS, true);

        // Test for incX = 2;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorXS.copyFrom(mBLASData.L2_sSYR_x_n2);
        matrixAS.copyFrom(mBLASData.L2_sSYR_A_nn);

        mBLAS.SSYR(uplo, alphaS, vectorXS, incX, matrixAS);
        verifyMatrix(matrixARef, matrixAS, true);

        mRS.finish();
        checkError();
    }

    public void test_L2_DSYR_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;

        // Populate input allocations
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        matrixAD.copyFrom(mBLASData.L2_dSYR_A_nn);
        vectorXD.copyFrom(mBLASData.L2_dSYR_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.DSYR(uplo, alphaD, vectorXD, incX, matrixAD);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dN));
        matrixARef.copyFrom(mBLASData.L2_dSYR_o_N);
        verifyMatrix(matrixARef, matrixAD, true);

        // Test for incX = 2;
        incX = 2;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorXD.copyFrom(mBLASData.L2_dSYR_x_n2);
        matrixAD.copyFrom(mBLASData.L2_dSYR_A_nn);

        mBLAS.DSYR(uplo, alphaD, vectorXD, incX, matrixAD);
        verifyMatrix(matrixARef, matrixAD, true);

        mRS.finish();
        checkError();
    }


    private boolean validateSPR(Element e, int Uplo, Allocation X, int incX, Allocation Ap) {
        if (!validateUplo(Uplo)) {
            return false;
        }
        if (!Ap.getType().getElement().isCompatible(e) ||
            !X.getType().getElement().isCompatible(e)) {
            return false;
        }
        if (X.getType().getY() > 1) {
            return false;
        }

        if (Ap.getType().getY() > 1) {
            return false;
        }

        int N = (int)Math.sqrt((double)Ap.getType().getX() * 2);
        if (Ap.getType().getX() != ((N * (N+1)) / 2)) {
            return false;
        }
        if (incX <= 0) {
            return false;
        }
        int expectedXDim = 1 + (N - 1) * incX;
        if (X.getType().getX() != expectedXDim) {
            return false;
        }

        return true;
    }

    private void xSPR_API_test(int Uplo, int incX, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                Element elemA = matA.getType().getElement();
                if (validateSPR(elemA, Uplo, vecX, incX, matA)) {
                    try {
                        if (elemA.isCompatible(Element.F32(mRS))) {
                            mBLAS.SSPR(Uplo, alphaS, vecX, incX, matA);
                        } else if (elemA.isCompatible(Element.F64(mRS))) {
                            mBLAS.DSPR(Uplo, alphaD, vecX, incX, matA);
                        }
                    } catch (RSRuntimeException e) {
                        fail("should NOT throw RSRuntimeException");
                    }
                } else {
                    try {
                        mBLAS.SSPR(Uplo, alphaS, vecX, incX, matA);
                        fail("should throw RSRuntimeException for SSPR");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.DSPR(Uplo, alphaD, vecX, incX, matA);
                        fail("should throw RSRuntimeException for DSPR");
                    } catch (RSRuntimeException e) {
                    }
                }
            }
        }
    }

    public void L2_xSPR_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int incX : mInc) {
                xSPR_API_test(Uplo, incX, mMatrix);
            }
        }
    }

    public void test_L2_SSPR_API() {
        L2_xSPR_API(mMatrixS);
    }

    public void test_L2_DSPR_API() {
        L2_xSPR_API(mMatrixD);
    }

    public void test_L2_SSPR_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), N * (N+1) / 2, 1));
        Allocation vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), N, 1));
        matrixAS.copyFrom(mBLASData.L2_sSYR_A_nn_pu);
        vectorXS.copyFrom(mBLASData.L2_sSYR_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.SSPR(uplo, alphaS, vectorXS, incX, matrixAS);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), N * (N+1) / 2, 1));
        matrixARef.copyFrom(mBLASData.L2_sSYR_o_N_pu);
        verifyMatrix(matrixARef, matrixAS, true);

        // Test for incX = 2;
        incX = 2;
        int dimX = 1 + (N - 1) * incX;
        vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorXS.copyFrom(mBLASData.L2_sSYR_x_n2);
        matrixAS.copyFrom(mBLASData.L2_sSYR_A_nn_pu);

        mBLAS.SSPR(uplo, alphaS, vectorXS, incX, matrixAS);
        verifyMatrix(matrixARef, matrixAS, true);

        mRS.finish();
        checkError();
    }

    public void test_L2_DSPR_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), N * (N+1) / 2, 1));
        Allocation vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), N, 1));
        matrixAD.copyFrom(mBLASData.L2_dSYR_A_nn_pu);
        vectorXD.copyFrom(mBLASData.L2_dSYR_x_n1);

        // Test for the default case: NO_TRANS
        mBLAS.DSPR(uplo, alphaD, vectorXD, incX, matrixAD);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), N * (N+1) / 2, 1));
        matrixARef.copyFrom(mBLASData.L2_dSYR_o_N_pu);
        verifyMatrix(matrixARef, matrixAD, true);

        // Test for incX = 2;
        incX = 2;
        int dimX = 1 + (N - 1) * incX;
        vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorXD.copyFrom(mBLASData.L2_dSYR_x_n2);
        matrixAD.copyFrom(mBLASData.L2_dSYR_A_nn_pu);

        mBLAS.DSPR(uplo, alphaD, vectorXD, incX, matrixAD);
        verifyMatrix(matrixARef, matrixAD, true);

        mRS.finish();
        checkError();
    }


    private boolean validateSYR2(Element e, int Uplo, Allocation X, int incX, Allocation Y, int incY, Allocation A) {
        if (!validateUplo(Uplo)) {
            return false;
        }
        if (!A.getType().getElement().isCompatible(e) ||
            !X.getType().getElement().isCompatible(e) ||
            !Y.getType().getElement().isCompatible(e)) {
            return false;
        }

        if (X.getType().getY() > 1 || Y.getType().getY() > 1) {
            return false;
        }

        int N = A.getType().getX();

        if (N != A.getType().getY()) {
            return false;
        }
        if (incX <= 0 || incY <= 0) {
            return false;
        }
        int expectedXDim = 1 + (N - 1) * incX;
        int expectedYDim = 1 + (N - 1) * incY;
        if (X.getType().getX() != expectedXDim || Y.getType().getX() != expectedYDim) {
            return false;
        }
        return true;
    }

    private void xSYR2_API_test(int Uplo, int incX, int incY, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                for (Allocation vecY : mMatrix) {
                    if (!validateVecInput(vecY)) {
                        continue;
                    }
                    Element elemA = matA.getType().getElement();
                    if (validateSYR2(elemA, Uplo, vecX, incX, vecY, incY, matA)) {
                        try {
                            if (elemA.isCompatible(Element.F32(mRS))) {
                                mBLAS.SSYR2(Uplo, alphaS, vecX, incX, vecY, incY, matA);
                            } else if (elemA.isCompatible(Element.F64(mRS))) {
                                mBLAS.DSYR2(Uplo, alphaD, vecX, incX, vecY, incY, matA);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.SSYR2(Uplo, alphaS, vecX, incX, vecY, incY, matA);
                            fail("should throw RSRuntimeException for SSYR2");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.DSYR2(Uplo, alphaD, vecX, incX, vecY, incY, matA);
                            fail("should throw RSRuntimeException for DSYR2");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    public void L2_xSYR2_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int incX : mInc) {
                xSYR2_API_test(Uplo, incX, incX, mMatrix);
            }
        }
    }

    public void test_L2_SSYR2_API() {
        L2_xSYR2_API(mMatrixS);
    }

    public void test_L2_DSYR2_API() {
        L2_xSYR2_API(mMatrixD);
    }

    public void test_L2_SSYR2_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        Allocation vectorYS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, 1));
        matrixAS.copyFrom(mBLASData.L2_sSYR2_A_nn);
        vectorXS.copyFrom(mBLASData.L2_sSYR2_x_n1);
        vectorYS.copyFrom(mBLASData.L2_sSYR2_y_n1);

        // Test for the default case: NO_TRANS
        mBLAS.SSYR2(uplo, alphaS, vectorXS, incX, vectorYS, incY, matrixAS);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dN));
        matrixARef.copyFrom(mBLASData.L2_sSYR2_o_N);
        verifyMatrix(matrixARef, matrixAS, true);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dN - 1) * incY;
        vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorYS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimY, 1));
        vectorXS.copyFrom(mBLASData.L2_sSYR2_x_n2);
        vectorYS.copyFrom(mBLASData.L2_sSYR2_y_n2);
        matrixAS.copyFrom(mBLASData.L2_sSYR2_A_nn);

        mBLAS.SSYR2(uplo, alphaS, vectorXS, incX, vectorYS, incY, matrixAS);
        verifyMatrix(matrixARef, matrixAS, true);

        mRS.finish();
        checkError();
    }

    public void test_L2_DSYR2_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dN));
        Allocation vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        Allocation vectorYD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, 1));
        matrixAD.copyFrom(mBLASData.L2_dSYR2_A_nn);
        vectorXD.copyFrom(mBLASData.L2_dSYR2_x_n1);
        vectorYD.copyFrom(mBLASData.L2_dSYR2_y_n1);

        // Test for the default case: NO_TRANS
        mBLAS.DSYR2(uplo, alphaD, vectorXD, incX, vectorYD, incY, matrixAD);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dN));
        matrixARef.copyFrom(mBLASData.L2_dSYR2_o_N);
        verifyMatrix(matrixARef, matrixAD, true);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (mBLASData.dN - 1) * incX;
        int dimY = 1 + (mBLASData.dN - 1) * incY;
        vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorYD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimY, 1));
        vectorXD.copyFrom(mBLASData.L2_dSYR2_x_n2);
        vectorYD.copyFrom(mBLASData.L2_dSYR2_y_n2);
        matrixAD.copyFrom(mBLASData.L2_dSYR2_A_nn);

        mBLAS.DSYR2(uplo, alphaD, vectorXD, incX, vectorYD, incY, matrixAD);
        verifyMatrix(matrixARef, matrixAD, true);

        mRS.finish();
        checkError();
    }


    private boolean validateSPR2(Element e, int Uplo, Allocation X, int incX, Allocation Y, int incY, Allocation Ap) {
        if (!validateUplo(Uplo)) {
            return false;
        }
        if (!Ap.getType().getElement().isCompatible(e) ||
            !X.getType().getElement().isCompatible(e) ||
            !Y.getType().getElement().isCompatible(e)) {
            return false;
        }
        if (X.getType().getY() > 1 || Y.getType().getY() > 1) {
            return false;
        }

        if (Ap.getType().getY() > 1) {
            return false;
        }

        int N = (int)Math.sqrt((double)Ap.getType().getX() * 2);
        if (Ap.getType().getX() != ((N * (N+1)) / 2)) {
            return false;
        }
        if (incX <= 0 || incY <= 0) {
            return false;
        }
        int expectedXDim = 1 + (N - 1) * incX;
        int expectedYDim = 1 + (N - 1) * incY;
        if (X.getType().getX() != expectedXDim || Y.getType().getX() != expectedYDim) {
            return false;
        }

        return true;
    }

    private void xSPR2_API_test(int Uplo, int incX, int incY, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation vecX : mMatrix) {
                if (!validateVecInput(vecX)) {
                    continue;
                }
                for (Allocation vecY : mMatrix) {
                    if (!validateVecInput(vecY)) {
                        continue;
                    }
                    Element elemA = matA.getType().getElement();
                    if (validateSPR2(elemA, Uplo, vecX, incX, vecY, incY, matA)) {
                        try {
                            if (elemA.isCompatible(Element.F32(mRS))) {
                                mBLAS.SSPR2(Uplo, alphaS, vecX, incX, vecY, incY, matA);
                            } else if (elemA.isCompatible(Element.F64(mRS))) {
                                mBLAS.DSPR2(Uplo, alphaD, vecX, incX, vecY, incY, matA);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.SSPR2(Uplo, alphaS, vecX, incX, vecY, incY, matA);
                            fail("should throw RSRuntimeException for SSPR2");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.DSPR2(Uplo, alphaD, vecX, incX, vecY, incY, matA);
                            fail("should throw RSRuntimeException for DSPR2");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    public void L2_xSPR2_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int incX : mInc) {
                xSPR2_API_test(Uplo, incX, incX, mMatrix);
            }
        }
    }

    public void test_L2_SSPR2_API() {
        L2_xSPR2_API(mMatrixS);
    }

    public void test_L2_DSPR2_API() {
        L2_xSPR2_API(mMatrixD);
    }

    public void test_L2_SSPR2_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), N * (N+1) / 2, 1));
        Allocation vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), N, 1));
        Allocation vectorYS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), N, 1));
        matrixAS.copyFrom(mBLASData.L2_sSYR2_A_nn_pu);
        vectorXS.copyFrom(mBLASData.L2_sSYR2_x_n1);
        vectorYS.copyFrom(mBLASData.L2_sSYR2_y_n1);

        // Test for the default case: NO_TRANS
        mBLAS.SSPR2(uplo, alphaS, vectorXS, incX, vectorYS, incY, matrixAS);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), N * (N+1) / 2, 1));
        matrixARef.copyFrom(mBLASData.L2_sSYR2_o_N_pu);
        verifyMatrix(matrixARef, matrixAS, true);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (N - 1) * incX;
        int dimY = 1 + (N - 1) * incY;
        vectorXS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimX, 1));
        vectorYS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), dimY, 1));
        vectorXS.copyFrom(mBLASData.L2_sSYR2_x_n2);
        vectorYS.copyFrom(mBLASData.L2_sSYR2_y_n2);
        matrixAS.copyFrom(mBLASData.L2_sSYR2_A_nn_pu);

        mBLAS.SSPR2(uplo, alphaS, vectorXS, incX, vectorYS, incY, matrixAS);
        verifyMatrix(matrixARef, matrixAS, true);

        mRS.finish();
        checkError();
    }

    public void test_L2_DSPR2_Correctness() {
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int incX = 1;
        int incY = 1;

        // Populate input allocations
        int N = mBLASData.dN;
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), N * (N+1) / 2, 1));
        Allocation vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), N, 1));
        Allocation vectorYD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), N, 1));
        matrixAD.copyFrom(mBLASData.L2_dSYR2_A_nn_pu);
        vectorXD.copyFrom(mBLASData.L2_dSYR2_x_n1);
        vectorYD.copyFrom(mBLASData.L2_dSYR2_y_n1);

        // Test for the default case: NO_TRANS
        mBLAS.DSPR2(uplo, alphaD, vectorXD, incX, vectorYD, incY, matrixAD);
        Allocation matrixARef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), N * (N+1) / 2, 1));
        matrixARef.copyFrom(mBLASData.L2_dSYR2_o_N_pu);
        verifyMatrix(matrixARef, matrixAD, true);

        // Test for incX = 2 & incY = 3;
        incX = 2;
        incY = 3;
        int dimX = 1 + (N - 1) * incX;
        int dimY = 1 + (N - 1) * incY;
        vectorXD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimX, 1));
        vectorYD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), dimY, 1));
        vectorXD.copyFrom(mBLASData.L2_dSYR2_x_n2);
        vectorYD.copyFrom(mBLASData.L2_dSYR2_y_n2);
        matrixAD.copyFrom(mBLASData.L2_dSYR2_A_nn_pu);

        mBLAS.DSPR2(uplo, alphaD, vectorXD, incX, vectorYD, incY, matrixAD);
        verifyMatrix(matrixARef, matrixAD, true);

        mRS.finish();
        checkError();
    }



    private boolean validateL3(Element e, int TransA, int TransB, int Side, Allocation A, Allocation B, Allocation C) {
        int aM = -1, aN = -1, bM = -1, bN = -1, cM = -1, cN = -1;
        if ((A != null && !A.getType().getElement().isCompatible(e)) ||
            (B != null && !B.getType().getElement().isCompatible(e)) ||
            (C != null && !C.getType().getElement().isCompatible(e))) {
            return false;
        }
        if (C == null) {
            //since matrix C is used to store the result, it cannot be null.
            return false;
        }
        cM = C.getType().getY();
        cN = C.getType().getX();

        if (Side == ScriptIntrinsicBLAS.RIGHT) {
            if ((A == null && B != null) || (A != null && B == null)) {
                return false;
            }
            if (B != null) {
                bM = A.getType().getY();
                bN = A.getType().getX();
            }
            if (A != null) {
                aM = B.getType().getY();
                aN = B.getType().getX();
            }
        } else {
            if (A != null) {
                if (TransA == ScriptIntrinsicBLAS.TRANSPOSE ||
                    TransA == ScriptIntrinsicBLAS.CONJ_TRANSPOSE ) {
                    aN = A.getType().getY();
                    aM = A.getType().getX();
                } else {
                    aM = A.getType().getY();
                    aN = A.getType().getX();
                }
            }
            if (B != null) {
                if (TransB == ScriptIntrinsicBLAS.TRANSPOSE ||
                    TransB == ScriptIntrinsicBLAS.CONJ_TRANSPOSE ) {
                    bN = B.getType().getY();
                    bM = B.getType().getX();
                } else {
                    bM = B.getType().getY();
                    bN = B.getType().getX();
                }
            }
        }
        if (A != null && B != null && C != null) {
            if (aN != bM || aM != cM || bN != cN) {
                return false;
            }
        } else if (A != null && C != null) {
            // A and C only, for SYRK
            if (cM != cN) {
                return false;
            }
            if (aM != cM) {
                return false;
            }
        } else if (A != null && B != null) {
            // A and B only
            if (aN != bM) {
                return false;
            }
        }

        return true;
    }

    private boolean validateL3_xGEMM(Element e, int TransA, int TransB, Allocation A, Allocation B, Allocation C) {
        boolean result = true;
        result &= validateTranspose(TransA);
        result &= validateTranspose(TransB);
        result &= validateL3(e, TransA, TransB, 0, A, B, C);

        return result;
    }

    private void xGEMM_API_test(int transA, int transB, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation matB : mMatrix) {
                for (Allocation matC : mMatrix) {
                    Element elemA = matA.getType().getElement();
                    if (validateL3_xGEMM(elemA, transA, transB, matA, matB, matC)) {
                        try {
                            if (elemA.isCompatible(Element.F32(mRS))) {
                                mBLAS.SGEMM(transA, transB, alphaS, matA, matB, betaS, matC);
                            } else if (elemA.isCompatible(Element.F64(mRS))) {
                                mBLAS.DGEMM(transA, transB, alphaD, matA, matB, betaD, matC);
                            } else if (elemA.isCompatible(Element.F32_2(mRS))) {
                                mBLAS.CGEMM(transA, transB, alphaC, matA, matB, betaC, matC);
                            } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                                mBLAS.ZGEMM(transA, transB, alphaZ, matA, matB, betaZ, matC);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.SGEMM(transA, transB, alphaS, matA, matB, betaS, matC);
                            fail("should throw RSRuntimeException for SGEMM");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.DGEMM(transA, transB, alphaD, matA, matB, betaD, matC);
                            fail("should throw RSRuntimeException for DGEMM");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.CGEMM(transA, transB, alphaC, matA, matB, betaC, matC);
                            fail("should throw RSRuntimeException for CGEMM");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.ZGEMM(transA, transB, alphaZ, matA, matB, betaZ, matC);
                            fail("should throw RSRuntimeException for ZGEMM");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    private void L3_xGEMM_API(ArrayList<Allocation> mMatrix) {
        for (int transA : mTranspose) {
            for (int transB : mTranspose) {
                xGEMM_API_test(transA, transB, mMatrix);
            }
        }
    }

    public void test_L3_SGEMM_API() {
        L3_xGEMM_API(mMatrixS);
    }

    public void test_L3_DGEMM_API() {
        L3_xGEMM_API(mMatrixD);
    }

    public void test_L3_CGEMM_API() {
        L3_xGEMM_API(mMatrixC);
    }

    public void test_L3_ZGEMM_API() {
        L3_xGEMM_API(mMatrixZ);
    }


    public void test_L3_SGEMM_Correctness() {
        int transA = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int transB = ScriptIntrinsicBLAS.NO_TRANSPOSE;

        // Populate input allocations
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dK, mBLASData.dM));
        Allocation matrixBS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dK));
        Allocation matrixCS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dM));
        matrixAS.copyFrom(mBLASData.L3_sGEMM_A_mk);
        matrixBS.copyFrom(mBLASData.L3_sGEMM_B_kn);
        matrixCS.copyFrom(mBLASData.L3_sGEMM_C_mn);

        // Test for the default case: NO_TRANS
        mBLAS.SGEMM(transA, transB, alphaS, matrixAS, matrixBS, betaS, matrixCS);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dM));
        matrixCRef.copyFrom(mBLASData.L3_sGEMM_o_NN);
        verifyMatrix(matrixCRef, matrixCS);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dM, mBLASData.dK));
        matrixBS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dK, mBLASData.dN));
        matrixAS.copyFrom(mBLASData.L3_sGEMM_A_km);
        matrixBS.copyFrom(mBLASData.L3_sGEMM_B_nk);

        transA = ScriptIntrinsicBLAS.TRANSPOSE;
        transB = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCS.copyFrom(mBLASData.L3_sGEMM_C_mn);
        mBLAS.SGEMM(transA, transB, alphaS, matrixAS, matrixBS, betaS, matrixCS);
        matrixCRef.copyFrom(mBLASData.L3_sGEMM_o_TT);
        verifyMatrix(matrixCRef, matrixCS);

        transA = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        transB = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        matrixCS.copyFrom(mBLASData.L3_sGEMM_C_mn);
        mBLAS.SGEMM(transA, transB, alphaS, matrixAS, matrixBS, betaS, matrixCS);
        matrixCRef.copyFrom(mBLASData.L3_sGEMM_o_HH);
        verifyMatrix(matrixCRef, matrixCS);

        mRS.finish();
        checkError();
    }

    public void test_L3_DGEMM_Correctness() {
        int transA = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int transB = ScriptIntrinsicBLAS.NO_TRANSPOSE;

        // Populate input allocations
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dK, mBLASData.dM));
        Allocation matrixBD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dK));
        Allocation matrixCD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dM));
        matrixAD.copyFrom(mBLASData.L3_dGEMM_A_mk);
        matrixBD.copyFrom(mBLASData.L3_dGEMM_B_kn);
        matrixCD.copyFrom(mBLASData.L3_dGEMM_C_mn);
        // Test for the default case: NO_TRANS
        mBLAS.DGEMM(transA, transB, alphaD, matrixAD, matrixBD, betaD, matrixCD);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dM));
        matrixCRef.copyFrom(mBLASData.L3_dGEMM_o_NN);
        verifyMatrix(matrixCRef, matrixCD);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dM, mBLASData.dK));
        matrixBD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dK, mBLASData.dN));
        matrixAD.copyFrom(mBLASData.L3_dGEMM_A_km);
        matrixBD.copyFrom(mBLASData.L3_dGEMM_B_nk);

        transA = ScriptIntrinsicBLAS.TRANSPOSE;
        transB = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCD.copyFrom(mBLASData.L3_dGEMM_C_mn);
        mBLAS.DGEMM(transA, transB, alphaD, matrixAD, matrixBD, betaD, matrixCD);
        matrixCRef.copyFrom(mBLASData.L3_dGEMM_o_TT);
        verifyMatrix(matrixCRef, matrixCD);

        transA = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        transB = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        matrixCD.copyFrom(mBLASData.L3_dGEMM_C_mn);
        mBLAS.DGEMM(transA, transB, alphaD, matrixAD, matrixBD, betaD, matrixCD);
        matrixCRef.copyFrom(mBLASData.L3_dGEMM_o_HH);
        verifyMatrix(matrixCRef, matrixCD);

        mRS.finish();
        checkError();
    }

    public void test_L3_CGEMM_Correctness() {
        int transA = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int transB = ScriptIntrinsicBLAS.NO_TRANSPOSE;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dK, mBLASData.dM));
        Allocation matrixBC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dK));
        Allocation matrixCC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixAC.copyFrom(mBLASData.L3_cGEMM_A_mk);
        matrixBC.copyFrom(mBLASData.L3_cGEMM_B_kn);
        matrixCC.copyFrom(mBLASData.L3_cGEMM_C_mn);

        // Test for the default case: NO_TRANS
        mBLAS.CGEMM(transA, transB, alphaC, matrixAC, matrixBC, betaC, matrixCC);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixCRef.copyFrom(mBLASData.L3_cGEMM_o_NN);
        verifyMatrix(matrixCRef, matrixCC);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dM, mBLASData.dK));
        matrixBC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dK, mBLASData.dN));
        matrixAC.copyFrom(mBLASData.L3_cGEMM_A_km);
        matrixBC.copyFrom(mBLASData.L3_cGEMM_B_nk);

        transA = ScriptIntrinsicBLAS.TRANSPOSE;
        transB = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCC.copyFrom(mBLASData.L3_cGEMM_C_mn);
        mBLAS.CGEMM(transA, transB, alphaC, matrixAC, matrixBC, betaC, matrixCC);
        matrixCRef.copyFrom(mBLASData.L3_cGEMM_o_TT);
        verifyMatrix(matrixCRef, matrixCC);

        transA = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        transB = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        matrixCC.copyFrom(mBLASData.L3_cGEMM_C_mn);
        mBLAS.CGEMM(transA, transB, alphaC, matrixAC, matrixBC, betaC, matrixCC);
        matrixCRef.copyFrom(mBLASData.L3_cGEMM_o_HH);
        verifyMatrix(matrixCRef, matrixCC);

        mRS.finish();
        checkError();
    }

    public void test_L3_ZGEMM_Correctness() {
        int transA = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int transB = ScriptIntrinsicBLAS.NO_TRANSPOSE;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dK, mBLASData.dM));
        Allocation matrixBZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dK));
        Allocation matrixCZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixAZ.copyFrom(mBLASData.L3_zGEMM_A_mk);
        matrixBZ.copyFrom(mBLASData.L3_zGEMM_B_kn);
        matrixCZ.copyFrom(mBLASData.L3_zGEMM_C_mn);

        // Test for the default case: NO_TRANS
        mBLAS.ZGEMM(transA, transB, alphaZ, matrixAZ, matrixBZ, betaZ, matrixCZ);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixCRef.copyFrom(mBLASData.L3_zGEMM_o_NN);
        verifyMatrix(matrixCRef, matrixCZ);

        // Test for trans cases: TRANSPOSE, CONJ_TRANSPOSE
        matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dM, mBLASData.dK));
        matrixBZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dK, mBLASData.dN));
        matrixAZ.copyFrom(mBLASData.L3_zGEMM_A_km);
        matrixBZ.copyFrom(mBLASData.L3_zGEMM_B_nk);

        transA = ScriptIntrinsicBLAS.TRANSPOSE;
        transB = ScriptIntrinsicBLAS.TRANSPOSE;
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCZ.copyFrom(mBLASData.L3_zGEMM_C_mn);
        mBLAS.ZGEMM(transA, transB, alphaZ, matrixAZ, matrixBZ, betaZ, matrixCZ);
        matrixCRef.copyFrom(mBLASData.L3_zGEMM_o_TT);
        verifyMatrix(matrixCRef, matrixCZ);

        transA = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        transB = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        matrixCZ.copyFrom(mBLASData.L3_zGEMM_C_mn);
        mBLAS.ZGEMM(transA, transB, alphaZ, matrixAZ, matrixBZ, betaZ, matrixCZ);
        matrixCRef.copyFrom(mBLASData.L3_zGEMM_o_HH);
        verifyMatrix(matrixCRef, matrixCZ);

        mRS.finish();
        checkError();
    }



    private boolean validateL3_xSYMM(Element e, int Side, int Uplo, Allocation A, Allocation B, Allocation C) {
        boolean result = true;
        result &= validateSide(Side);
        result &= validateUplo(Uplo);
        result &= validateL3(e, 0, 0, Side, A, B, C);
        result &= (A.getType().getX() == A.getType().getY());
        return result;
    }

    private void xSYMM_API_test(int Side, int Uplo, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation matB : mMatrix) {
                for (Allocation matC : mMatrix) {
                    Element elemA = matA.getType().getElement();
                    if (validateL3_xSYMM(elemA, Side, Uplo, matA, matB, matC)) {
                        try {
                            if (elemA.isCompatible(Element.F32(mRS))) {
                                mBLAS.SSYMM(Side, Uplo, alphaS, matA, matB, betaS, matC);
                            } else if (elemA.isCompatible(Element.F64(mRS))) {
                                mBLAS.DSYMM(Side, Uplo, alphaD, matA, matB, betaD, matC);
                            } else if (elemA.isCompatible(Element.F32_2(mRS))) {
                                mBLAS.CSYMM(Side, Uplo, alphaC, matA, matB, betaC, matC);
                            } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                                mBLAS.ZSYMM(Side, Uplo, alphaZ, matA, matB, betaZ, matC);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.SSYMM(Side, Uplo, alphaS, matA, matB, betaS, matC);
                            fail("should throw RSRuntimeException for SSYMM");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.DSYMM(Side, Uplo, alphaD, matA, matB, betaD, matC);
                            fail("should throw RSRuntimeException for DSYMM");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.CSYMM(Side, Uplo, alphaC, matA, matB, betaC, matC);
                            fail("should throw RSRuntimeException for CSYMM");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.ZSYMM(Side, Uplo, alphaZ, matA, matB, betaZ, matC);
                            fail("should throw RSRuntimeException for ZSYMM");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    private void L3_xSYMM_API(ArrayList<Allocation> mMatrix) {
        for (int Side : mSide) {
            for (int Uplo : mUplo) {
                xSYMM_API_test(Side, Uplo, mMatrix);
            }
        }
    }

    public void test_L3_SSYMM_API() {
        L3_xSYMM_API(mMatrixS);
    }

    public void test_L3_DSYMM_API() {
        L3_xSYMM_API(mMatrixD);
    }

    public void test_L3_CSYMM_API() {
        L3_xSYMM_API(mMatrixC);
    }

    public void test_L3_ZSYMM_API() {
        L3_xSYMM_API(mMatrixZ);
    }


    public void test_L3_SSYMM_Correctness() {
        int side = ScriptIntrinsicBLAS.LEFT;
        int uplo = ScriptIntrinsicBLAS.UPPER;

        // Populate input allocations
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dM, mBLASData.dM));
        Allocation matrixBS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dM));
        Allocation matrixCS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dM));
        matrixAS.copyFrom(mBLASData.L3_sSYMM_A_mm);
        matrixBS.copyFrom(mBLASData.L3_sSYMM_B_mn);
        matrixCS.copyFrom(mBLASData.L3_sSYMM_C_mn);

        // Default case: SIDE = LEFT
        mBLAS.SSYMM(side, uplo, alphaS, matrixAS, matrixBS, betaS, matrixCS);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dM));
        matrixCRef.copyFrom(mBLASData.L3_sSYMM_o_L);
        verifyMatrix(matrixCRef, matrixCS);

        // SIDE = RIGHT
        side = ScriptIntrinsicBLAS.RIGHT;
        matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dN));
        matrixAS.copyFrom(mBLASData.L3_sSYMM_A_nn);
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCS.copyFrom(mBLASData.L3_sSYMM_C_mn);
        mBLAS.SSYMM(side, uplo, alphaS, matrixAS, matrixBS, betaS, matrixCS);
        matrixCRef.copyFrom(mBLASData.L3_sSYMM_o_R);
        verifyMatrix(matrixCRef, matrixCS);

        mRS.finish();
        checkError();
    }

    public void test_L3_DSYMM_Correctness() {
        int side = ScriptIntrinsicBLAS.LEFT;
        int uplo = ScriptIntrinsicBLAS.UPPER;

        // Populate input allocations
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dM, mBLASData.dM));
        Allocation matrixBD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dM));
        Allocation matrixCD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dM));
        matrixAD.copyFrom(mBLASData.L3_dSYMM_A_mm);
        matrixBD.copyFrom(mBLASData.L3_dSYMM_B_mn);
        matrixCD.copyFrom(mBLASData.L3_dSYMM_C_mn);

        // Default case: SIDE = LEFT
        mBLAS.DSYMM(side, uplo, alphaD, matrixAD, matrixBD, betaD, matrixCD);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dM));
        matrixCRef.copyFrom(mBLASData.L3_dSYMM_o_L);
        verifyMatrix(matrixCRef, matrixCD);

        // SIDE = RIGHT
        side = ScriptIntrinsicBLAS.RIGHT;
        matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dN));
        matrixAD.copyFrom(mBLASData.L3_dSYMM_A_nn);
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCD.copyFrom(mBLASData.L3_dSYMM_C_mn);
        mBLAS.DSYMM(side, uplo, alphaD, matrixAD, matrixBD, betaD, matrixCD);
        matrixCRef.copyFrom(mBLASData.L3_dSYMM_o_R);
        verifyMatrix(matrixCRef, matrixCD);

        mRS.finish();
        checkError();
    }

    public void test_L3_CSYMM_Correctness() {
        int side = ScriptIntrinsicBLAS.LEFT;
        int uplo = ScriptIntrinsicBLAS.UPPER;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dM, mBLASData.dM));
        Allocation matrixBC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dM));
        Allocation matrixCC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixAC.copyFrom(mBLASData.L3_cSYMM_A_mm);
        matrixBC.copyFrom(mBLASData.L3_cSYMM_B_mn);
        matrixCC.copyFrom(mBLASData.L3_cSYMM_C_mn);

        // Default case: SIDE = LEFT
        mBLAS.CSYMM(side, uplo, alphaC, matrixAC, matrixBC, betaC, matrixCC);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixCRef.copyFrom(mBLASData.L3_cSYMM_o_L);
        verifyMatrix(matrixCRef, matrixCC);

        // SIDE = RIGHT
        side = ScriptIntrinsicBLAS.RIGHT;
        matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixAC.copyFrom(mBLASData.L3_cSYMM_A_nn);
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCC.copyFrom(mBLASData.L3_cSYMM_C_mn);
        mBLAS.CSYMM(side, uplo, alphaC, matrixAC, matrixBC, betaC, matrixCC);
        matrixCRef.copyFrom(mBLASData.L3_cSYMM_o_R);
        verifyMatrix(matrixCRef, matrixCC);

        mRS.finish();
        checkError();
    }

    public void test_L3_ZSYMM_Correctness() {
        int side = ScriptIntrinsicBLAS.LEFT;
        int uplo = ScriptIntrinsicBLAS.UPPER;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dM, mBLASData.dM));
        Allocation matrixBZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dM));
        Allocation matrixCZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixAZ.copyFrom(mBLASData.L3_zSYMM_A_mm);
        matrixBZ.copyFrom(mBLASData.L3_zSYMM_B_mn);
        matrixCZ.copyFrom(mBLASData.L3_zSYMM_C_mn);

        // Default case: SIDE = LEFT
        mBLAS.ZSYMM(side, uplo, alphaZ, matrixAZ, matrixBZ, betaZ, matrixCZ);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixCRef.copyFrom(mBLASData.L3_zSYMM_o_L);
        verifyMatrix(matrixCRef, matrixCZ);

        // SIDE = RIGHT
        side = ScriptIntrinsicBLAS.RIGHT;
        matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixAZ.copyFrom(mBLASData.L3_zSYMM_A_nn);
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCZ.copyFrom(mBLASData.L3_zSYMM_C_mn);
        mBLAS.ZSYMM(side, uplo, alphaZ, matrixAZ, matrixBZ, betaZ, matrixCZ);
        matrixCRef.copyFrom(mBLASData.L3_zSYMM_o_R);
        verifyMatrix(matrixCRef, matrixCZ);

        mRS.finish();
        checkError();
    }


    private boolean validateHEMM(Element e, int Side, int Uplo, Allocation A, Allocation B, Allocation C) {
        if (!validateSide(Side)) {
            return false;
        }

        if (!validateUplo(Uplo)) {
            return false;
        }

        if (!A.getType().getElement().isCompatible(e) ||
            !B.getType().getElement().isCompatible(e) ||
            !C.getType().getElement().isCompatible(e)) {
            return false;
        }

        // A must be square; can potentially be relaxed similar to TRSM
        int adim = A.getType().getX();
        if (adim != A.getType().getY()) {
            return false;
        }
        if ((Side == ScriptIntrinsicBLAS.LEFT && adim != B.getType().getY()) ||
            (Side == ScriptIntrinsicBLAS.RIGHT && adim != B.getType().getX())) {
            return false;
        }
        if (B.getType().getX() != C.getType().getX() ||
            B.getType().getY() != C.getType().getY()) {
            return false;
        }

        return true;
    }

    private void xHEMM_API_test(int Side, int Uplo, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation matB : mMatrix) {
                for (Allocation matC : mMatrix) {
                    Element elemA = matA.getType().getElement();
                    if (validateHEMM(elemA, Side, Uplo, matA, matB, matC)) {
                        try {
                            if (elemA.isCompatible(Element.F32_2(mRS))) {
                                mBLAS.CHEMM(Side, Uplo, alphaC, matA, matB, betaC, matC);
                            } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                                mBLAS.ZHEMM(Side, Uplo, alphaZ, matA, matB, betaZ, matC);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.CHEMM(Side, Uplo, alphaC, matA, matB, betaC, matC);
                            fail("should throw RSRuntimeException for CHEMM");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.ZHEMM(Side, Uplo, alphaZ, matA, matB, betaZ, matC);
                            fail("should throw RSRuntimeException for ZHEMM");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    public void L3_xHEMM_API(ArrayList<Allocation> mMatrix) {
        for (int Side : mSide) {
            for (int Uplo : mUplo) {
                xHEMM_API_test(Side, Uplo, mMatrix);
            }
        }
    }

    public void test_L3_CHEMM_API() {
        L3_xHEMM_API(mMatrixC);
    }

    public void test_L3_ZHEMM_API() {
        L3_xHEMM_API(mMatrixZ);
    }

    public void test_L3_CHEMM_Correctness() {
        int side = ScriptIntrinsicBLAS.LEFT;
        int uplo = ScriptIntrinsicBLAS.UPPER;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dM, mBLASData.dM));
        Allocation matrixBC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dM));
        Allocation matrixCC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixAC.copyFrom(mBLASData.L3_cHEMM_A_mm);
        matrixBC.copyFrom(mBLASData.L3_cHEMM_B_mn);
        matrixCC.copyFrom(mBLASData.L3_cHEMM_C_mn);

        // Default case: SIDE = LEFT
        mBLAS.CHEMM(side, uplo, alphaC, matrixAC, matrixBC, betaC, matrixCC);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixCRef.copyFrom(mBLASData.L3_cHEMM_o_L);
        verifyMatrix(matrixCRef, matrixCC);

        // SIDE = RIGHT
        side = ScriptIntrinsicBLAS.RIGHT;
        matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixAC.copyFrom(mBLASData.L3_cHEMM_A_nn);
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCC.copyFrom(mBLASData.L3_cHEMM_C_mn);
        mBLAS.CHEMM(side, uplo, alphaC, matrixAC, matrixBC, betaC, matrixCC);
        matrixCRef.copyFrom(mBLASData.L3_cHEMM_o_R);
        verifyMatrix(matrixCRef, matrixCC);

        mRS.finish();
        checkError();
    }

    public void test_L3_ZHEMM_Correctness() {
        int side = ScriptIntrinsicBLAS.LEFT;
        int uplo = ScriptIntrinsicBLAS.UPPER;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dM, mBLASData.dM));
        Allocation matrixBZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dM));
        Allocation matrixCZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixAZ.copyFrom(mBLASData.L3_zHEMM_A_mm);
        matrixBZ.copyFrom(mBLASData.L3_zHEMM_B_mn);
        matrixCZ.copyFrom(mBLASData.L3_zHEMM_C_mn);

        // Default case: SIDE = LEFT
        mBLAS.ZHEMM(side, uplo, alphaZ, matrixAZ, matrixBZ, betaZ, matrixCZ);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixCRef.copyFrom(mBLASData.L3_zHEMM_o_L);
        verifyMatrix(matrixCRef, matrixCZ);

        // SIDE = RIGHT
        side = ScriptIntrinsicBLAS.RIGHT;
        matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixAZ.copyFrom(mBLASData.L3_zHEMM_A_nn);
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCZ.copyFrom(mBLASData.L3_zHEMM_C_mn);
        mBLAS.ZHEMM(side, uplo, alphaZ, matrixAZ, matrixBZ, betaZ, matrixCZ);
        matrixCRef.copyFrom(mBLASData.L3_zHEMM_o_R);
        verifyMatrix(matrixCRef, matrixCZ);

        mRS.finish();
        checkError();
    }



    private boolean validateL3_xSYRK(Element e, int Uplo, int Trans, Allocation A, Allocation C) {
        boolean result = true;
        result &= validateTranspose(Trans);
        result &= validateUplo(Uplo);
        result &= validateL3(e, Trans, 0, 0, A, null, C);

        return result;
    }

    private void xSYRK_API_test(int Uplo, int Trans, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation matC : mMatrix) {
                Element elemA = matA.getType().getElement();
                if (validateL3_xSYRK(elemA, Uplo, Trans, matA, matC)) {
                    try {
                        if (elemA.isCompatible(Element.F32(mRS))) {
                            mBLAS.SSYRK(Uplo, Trans, alphaS, matA, betaS, matC);
                        } else if (elemA.isCompatible(Element.F64(mRS))) {
                            mBLAS.DSYRK(Uplo, Trans, alphaD, matA, betaD, matC);
                        } else if (elemA.isCompatible(Element.F32_2(mRS))) {
                            mBLAS.CSYRK(Uplo, Trans, alphaC, matA, betaC, matC);
                        } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                            mBLAS.ZSYRK(Uplo, Trans, alphaZ, matA, betaZ, matC);
                        }
                    } catch (RSRuntimeException e) {
                        fail("should NOT throw RSRuntimeException");
                    }
                } else {
                    try {
                        mBLAS.SSYRK(Uplo, Trans, alphaS, matA, betaS, matC);
                        fail("should throw RSRuntimeException for SSYRK");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.DSYRK(Uplo, Trans, alphaD, matA, betaD, matC);
                        fail("should throw RSRuntimeException for DSYRK");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.CSYRK(Uplo, Trans, alphaC, matA, betaC, matC);
                        fail("should throw RSRuntimeException for CSYRK");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.ZSYRK(Uplo, Trans, alphaZ, matA, betaZ, matC);
                        fail("should throw RSRuntimeException for ZSYRK");
                    } catch (RSRuntimeException e) {
                    }
                }
            }
        }
    }

    public void L3_xSYRK_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int Trans : mTranspose) {
                xSYRK_API_test(Uplo, Trans, mMatrix);
            }
        }
    }

    public void test_L3_SSYRK_API() {
        L3_xSYRK_API(mMatrixS);
    }

    public void test_L3_DSYRK_API() {
        L3_xSYRK_API(mMatrixD);
    }

    public void test_L3_CSYRK_API() {
        L3_xSYRK_API(mMatrixC);
    }

    public void test_L3_ZSYRK_API() {
        L3_xSYRK_API(mMatrixZ);
    }


    public void test_L3_SSYRK_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;

        // Populate input allocations
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dK, mBLASData.dN));
        Allocation matrixCS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dN));
        matrixAS.copyFrom(mBLASData.L3_sSYRK_A_nk);
        matrixCS.copyFrom(mBLASData.L3_sSYRK_C_nn);

        // Default case: NO_TRANSPOSE
        mBLAS.SSYRK(uplo, trans, alphaS, matrixAS, betaS, matrixCS);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dN));
        matrixCRef.copyFrom(mBLASData.L3_sSYRK_o_N);
        verifyMatrix(matrixCRef, matrixCS, true);

        // Case: TRANSPOSE
        matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dK));
        matrixAS.copyFrom(mBLASData.L3_sSYRK_A_kn);
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCS.copyFrom(mBLASData.L3_sSYRK_C_nn);

        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        mBLAS.SSYRK(uplo, trans, alphaS, matrixAS, betaS, matrixCS);
        matrixCRef.copyFrom(mBLASData.L3_sSYRK_o_T);
        verifyMatrix(matrixCRef, matrixCS, true);

        mRS.finish();
        checkError();
    }

    public void test_L3_DSYRK_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;

        // Populate input allocations
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dK, mBLASData.dN));
        Allocation matrixCD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dN));
        matrixAD.copyFrom(mBLASData.L3_dSYRK_A_nk);
        matrixCD.copyFrom(mBLASData.L3_dSYRK_C_nn);

        // Default case: NO_TRANSPOSE
        mBLAS.DSYRK(uplo, trans, alphaD, matrixAD, betaD, matrixCD);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dN));
        matrixCRef.copyFrom(mBLASData.L3_dSYRK_o_N);
        verifyMatrix(matrixCRef, matrixCD, true);

        // Case: TRANSPOSE
        matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dK));
        matrixAD.copyFrom(mBLASData.L3_dSYRK_A_kn);
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCD.copyFrom(mBLASData.L3_dSYRK_C_nn);

        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        mBLAS.DSYRK(uplo, trans, alphaD, matrixAD, betaD, matrixCD);
        matrixCRef.copyFrom(mBLASData.L3_dSYRK_o_T);
        verifyMatrix(matrixCRef, matrixCD, true);

        mRS.finish();
        checkError();
    }

    public void test_L3_CSYRK_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dK, mBLASData.dN));
        Allocation matrixCC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixAC.copyFrom(mBLASData.L3_cSYRK_A_nk);
        matrixCC.copyFrom(mBLASData.L3_cSYRK_C_nn);

        // Default case: NO_TRANSPOSE
        mBLAS.CSYRK(uplo, trans, alphaC, matrixAC, betaC, matrixCC);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixCRef.copyFrom(mBLASData.L3_cSYRK_o_N);
        verifyMatrix(matrixCRef, matrixCC, true);

        // Case: TRANSPOSE
        matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dK));
        matrixAC.copyFrom(mBLASData.L3_cSYRK_A_kn);
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCC.copyFrom(mBLASData.L3_cSYRK_C_nn);

        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        mBLAS.CSYRK(uplo, trans, alphaC, matrixAC, betaC, matrixCC);
        matrixCRef.copyFrom(mBLASData.L3_cSYRK_o_T);
        verifyMatrix(matrixCRef, matrixCC, true);

        mRS.finish();
        checkError();
    }

    public void test_L3_ZSYRK_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dK, mBLASData.dN));
        Allocation matrixCZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixAZ.copyFrom(mBLASData.L3_zSYRK_A_nk);
        matrixCZ.copyFrom(mBLASData.L3_zSYRK_C_nn);

        // Default case: NO_TRANSPOSE
        mBLAS.ZSYRK(uplo, trans, alphaZ, matrixAZ, betaZ, matrixCZ);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixCRef.copyFrom(mBLASData.L3_zSYRK_o_N);
        verifyMatrix(matrixCRef, matrixCZ, true);

        // Case: TRANSPOSE
        matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dK));
        matrixAZ.copyFrom(mBLASData.L3_zSYRK_A_kn);
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCZ.copyFrom(mBLASData.L3_zSYRK_C_nn);

        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        mBLAS.ZSYRK(uplo, trans, alphaZ, matrixAZ, betaZ, matrixCZ);
        matrixCRef.copyFrom(mBLASData.L3_zSYRK_o_T);
        verifyMatrix(matrixCRef, matrixCZ, true);

        mRS.finish();
        checkError();
    }


    private boolean validateHERK(Element e, int Uplo, int Trans, Allocation A, Allocation C) {
        if (!validateUplo(Uplo)) {
            return false;
        }
        if (!A.getType().getElement().isCompatible(e) ||
            !C.getType().getElement().isCompatible(e)) {
            return false;
        }
        if (!validateConjTranspose(Trans)) {
            return false;
        }
        int cdim = C.getType().getX();
        if (cdim != C.getType().getY()) {
            return false;
        }
        if (Trans == ScriptIntrinsicBLAS.NO_TRANSPOSE) {
            if (cdim != A.getType().getY()) {
                return false;
            }
        } else {
            if (cdim != A.getType().getX()) {
                return false;
            }
        }
        return true;
    }

    private void xHERK_API_test(int Uplo, int Trans, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation matC : mMatrix) {
                Element elemA = matA.getType().getElement();
                if (validateHERK(elemA, Uplo, Trans, matA, matC)) {
                    try {
                        if (elemA.isCompatible(Element.F32_2(mRS))) {
                            mBLAS.CHERK(Uplo, Trans, alphaS, matA, betaS, matC);
                        } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                            mBLAS.ZHERK(Uplo, Trans, alphaD, matA, betaD, matC);
                        }
                    } catch (RSRuntimeException e) {
                        fail("should NOT throw RSRuntimeException");
                    }
                } else {
                    try {
                        mBLAS.CHERK(Uplo, Trans, alphaS, matA, betaS, matC);
                        fail("should throw RSRuntimeException for CHERK");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.ZHERK(Uplo, Trans, alphaD, matA, betaD, matC);
                        fail("should throw RSRuntimeException for ZHERK");
                    } catch (RSRuntimeException e) {
                    }
                }
            }
        }
    }

    public void L3_xHERK_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int Trans : mTranspose) {
                xHERK_API_test(Uplo, Trans, mMatrix);
            }
        }
    }

    public void test_L3_CHERK_API() {
        L3_xHERK_API(mMatrixC);
    }

    public void test_L3_ZHERK_API() {
        L3_xHERK_API(mMatrixZ);
    }

    public void test_L3_CHERK_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dK, mBLASData.dN));
        Allocation matrixCC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixAC.copyFrom(mBLASData.L3_cHERK_A_nk);
        matrixCC.copyFrom(mBLASData.L3_cHERK_C_nn);

        // Default case: NO_TRANSPOSE
        mBLAS.CHERK(uplo, trans, alphaS, matrixAC, betaS, matrixCC);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixCRef.copyFrom(mBLASData.L3_cHERK_o_N);
        verifyMatrix(matrixCRef, matrixCC, true);

        // Case: TRANSPOSE
        matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dK));
        matrixAC.copyFrom(mBLASData.L3_cHERK_A_kn);
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCC.copyFrom(mBLASData.L3_cHERK_C_nn);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        mBLAS.CHERK(uplo, trans, alphaS, matrixAC, betaS, matrixCC);
        matrixCRef.copyFrom(mBLASData.L3_cHERK_o_H);
        verifyMatrix(matrixCRef, matrixCC, true);

        mRS.finish();
        checkError();
    }

    public void test_L3_ZHERK_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dK, mBLASData.dN));
        Allocation matrixCZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixAZ.copyFrom(mBLASData.L3_zHERK_A_nk);
        matrixCZ.copyFrom(mBLASData.L3_zHERK_C_nn);

        // Default case: NO_TRANSPOSE
        mBLAS.ZHERK(uplo, trans, alphaD, matrixAZ, betaD, matrixCZ);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixCRef.copyFrom(mBLASData.L3_zHERK_o_N);
        verifyMatrix(matrixCRef, matrixCZ, true);

        // Case: TRANSPOSE
        matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dK));
        matrixAZ.copyFrom(mBLASData.L3_zHERK_A_kn);
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCZ.copyFrom(mBLASData.L3_zHERK_C_nn);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        mBLAS.ZHERK(uplo, trans, alphaD, matrixAZ, betaD, matrixCZ);
        matrixCRef.copyFrom(mBLASData.L3_zHERK_o_H);
        verifyMatrix(matrixCRef, matrixCZ, true);

        mRS.finish();
        checkError();
    }


    private boolean validateSYR2K(Element e, int Uplo, int Trans, Allocation A, Allocation B, Allocation C) {
        if (!validateTranspose(Trans)) {
            return false;
        }
        if (!validateUplo(Uplo)) {
            return false;
        }

        if (!A.getType().getElement().isCompatible(e) ||
            !B.getType().getElement().isCompatible(e) ||
            !C.getType().getElement().isCompatible(e)) {
            return false;
        }
        int Cdim = -1;
        // A is n x k if no transpose, k x n if transpose
        // C is n x n
        if (Trans == ScriptIntrinsicBLAS.TRANSPOSE) {
            // check columns versus C
            Cdim = A.getType().getX();
        } else {
            // check rows versus C
            Cdim = A.getType().getY();
        }
        if (C.getType().getX() != Cdim || C.getType().getY() != Cdim) {
            return false;
        }
        // A dims == B dims
        if (A.getType().getX() != B.getType().getX() || A.getType().getY() != B.getType().getY()) {
            return false;
        }
        return true;
    }

    private void xSYR2K_API_test(int Uplo, int Trans, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation matB : mMatrix) {
                for (Allocation matC : mMatrix) {
                    Element elemA = matA.getType().getElement();
                    if (validateSYR2K(elemA, Uplo, Trans, matA, matB, matC)) {
                        try {
                            if (elemA.isCompatible(Element.F32(mRS))) {
                                mBLAS.SSYR2K(Uplo, Trans, alphaS, matA, matB, betaS, matC);
                            } else if (elemA.isCompatible(Element.F64(mRS))) {
                                mBLAS.DSYR2K(Uplo, Trans, alphaD, matA, matB, betaD, matC);
                            } else if (elemA.isCompatible(Element.F32_2(mRS))) {
                                mBLAS.CSYR2K(Uplo, Trans, alphaC, matA, matB, betaC, matC);
                            } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                                mBLAS.ZSYR2K(Uplo, Trans, alphaZ, matA, matB, betaZ, matC);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.SSYR2K(Uplo, Trans, alphaS, matA, matB, betaS, matC);
                            fail("should throw RSRuntimeException for SSYR2K");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.DSYR2K(Uplo, Trans, alphaD, matA, matB, betaD, matC);
                            fail("should throw RSRuntimeException for DSYR2K");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.CSYR2K(Uplo, Trans, alphaC, matA, matB, betaC, matC);
                            fail("should throw RSRuntimeException for CSYR2K");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.ZSYR2K(Uplo, Trans, alphaZ, matA, matB, betaZ, matC);
                            fail("should throw RSRuntimeException for ZSYR2K");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    public void L3_xSYR2K_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int Trans : mTranspose) {
                xSYR2K_API_test(Uplo, Trans, mMatrix);
            }
        }
    }

    public void test_L3_SSYR2K_API() {
        L3_xSYR2K_API(mMatrixS);
    }

    public void test_L3_DSYR2K_API() {
        L3_xSYR2K_API(mMatrixD);
    }

    public void test_L3_CSYR2K_API() {
        L3_xSYR2K_API(mMatrixC);
    }

    public void test_L3_ZSYR2K_API() {
        L3_xSYR2K_API(mMatrixZ);
    }


    public void test_L3_SSYR2K_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;

        // Populate input allocations
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dK, mBLASData.dN));
        Allocation matrixBS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dK, mBLASData.dN));
        Allocation matrixCS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dN));
        matrixAS.copyFrom(mBLASData.L3_sSYR2K_A_nk);
        matrixBS.copyFrom(mBLASData.L3_sSYR2K_B_nk);
        matrixCS.copyFrom(mBLASData.L3_sSYR2K_C_nn);

        // Default case: NO_TRANSPOSE
        mBLAS.SSYR2K(uplo, trans, alphaS, matrixAS, matrixBS, betaS, matrixCS);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dN));
        matrixCRef.copyFrom(mBLASData.L3_sSYR2K_o_N);
        verifyMatrix(matrixCRef, matrixCS, true);

        // Case: TRANSPOSE
        matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dK));
        matrixBS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dK));
        matrixAS.copyFrom(mBLASData.L3_sSYR2K_A_kn);
        matrixBS.copyFrom(mBLASData.L3_sSYR2K_B_kn);
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCS.copyFrom(mBLASData.L3_sSYR2K_C_nn);

        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        mBLAS.SSYR2K(uplo, trans, alphaS, matrixAS, matrixBS, betaS, matrixCS);
        matrixCRef.copyFrom(mBLASData.L3_sSYR2K_o_T);
        verifyMatrix(matrixCRef, matrixCS, true);

        mRS.finish();
        checkError();
    }

    public void test_L3_DSYR2K_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;

        // Populate input allocations
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dK, mBLASData.dN));
        Allocation matrixBD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dK, mBLASData.dN));
        Allocation matrixCD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dN));
        matrixAD.copyFrom(mBLASData.L3_dSYR2K_A_nk);
        matrixBD.copyFrom(mBLASData.L3_dSYR2K_B_nk);
        matrixCD.copyFrom(mBLASData.L3_dSYR2K_C_nn);

        // Default case: NO_TRANSPOSE
        mBLAS.DSYR2K(uplo, trans, alphaD, matrixAD, matrixBD, betaD, matrixCD);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dN));
        matrixCRef.copyFrom(mBLASData.L3_dSYR2K_o_N);
        verifyMatrix(matrixCRef, matrixCD, true);

        // Case: TRANSPOSE
        matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dK));
        matrixBD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dK));
        matrixAD.copyFrom(mBLASData.L3_dSYR2K_A_kn);
        matrixBD.copyFrom(mBLASData.L3_dSYR2K_B_kn);
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCD.copyFrom(mBLASData.L3_dSYR2K_C_nn);

        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        mBLAS.DSYR2K(uplo, trans, alphaD, matrixAD, matrixBD, betaD, matrixCD);
        matrixCRef.copyFrom(mBLASData.L3_dSYR2K_o_T);
        verifyMatrix(matrixCRef, matrixCD, true);

        mRS.finish();
        checkError();
    }

    public void test_L3_CSYR2K_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dK, mBLASData.dN));
        Allocation matrixBC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dK, mBLASData.dN));
        Allocation matrixCC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixAC.copyFrom(mBLASData.L3_cSYR2K_A_nk);
        matrixBC.copyFrom(mBLASData.L3_cSYR2K_B_nk);
        matrixCC.copyFrom(mBLASData.L3_cSYR2K_C_nn);

        // Default case: NO_TRANSPOSE
        mBLAS.CSYR2K(uplo, trans, alphaC, matrixAC, matrixBC, betaC, matrixCC);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixCRef.copyFrom(mBLASData.L3_cSYR2K_o_N);
        verifyMatrix(matrixCRef, matrixCC, true);

        // Case: TRANSPOSE
        matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dK));
        matrixBC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dK));
        matrixAC.copyFrom(mBLASData.L3_cSYR2K_A_kn);
        matrixBC.copyFrom(mBLASData.L3_cSYR2K_B_kn);
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCC.copyFrom(mBLASData.L3_cSYR2K_C_nn);

        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        mBLAS.CSYR2K(uplo, trans, alphaC, matrixAC, matrixBC, betaC, matrixCC);
        matrixCRef.copyFrom(mBLASData.L3_cSYR2K_o_T);
        verifyMatrix(matrixCRef, matrixCC, true);

        mRS.finish();
        checkError();
    }

    public void test_L3_ZSYR2K_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dK, mBLASData.dN));
        Allocation matrixBZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dK, mBLASData.dN));
        Allocation matrixCZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixAZ.copyFrom(mBLASData.L3_zSYR2K_A_nk);
        matrixBZ.copyFrom(mBLASData.L3_zSYR2K_B_nk);
        matrixCZ.copyFrom(mBLASData.L3_zSYR2K_C_nn);

        // Default case: NO_TRANSPOSE
        mBLAS.ZSYR2K(uplo, trans, alphaZ, matrixAZ, matrixBZ, betaZ, matrixCZ);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixCRef.copyFrom(mBLASData.L3_zSYR2K_o_N);
        verifyMatrix(matrixCRef, matrixCZ, true);

        // Case: TRANSPOSE
        matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dK));
        matrixBZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dK));
        matrixAZ.copyFrom(mBLASData.L3_zSYR2K_A_kn);
        matrixBZ.copyFrom(mBLASData.L3_zSYR2K_B_kn);
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCZ.copyFrom(mBLASData.L3_zSYR2K_C_nn);

        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        mBLAS.ZSYR2K(uplo, trans, alphaZ, matrixAZ, matrixBZ, betaZ, matrixCZ);
        matrixCRef.copyFrom(mBLASData.L3_zSYR2K_o_T);
        verifyMatrix(matrixCRef, matrixCZ, true);

        mRS.finish();
        checkError();
    }


    private boolean validateHER2K(Element e, int Uplo, int Trans, Allocation A, Allocation B, Allocation C) {
        if (!validateUplo(Uplo)) {
            return false;
        }
        if (!A.getType().getElement().isCompatible(e) ||
            !B.getType().getElement().isCompatible(e) ||
            !C.getType().getElement().isCompatible(e)) {
            return false;
        }
        if (!validateConjTranspose(Trans)) {
            return false;
        }
        int cdim = C.getType().getX();
        if (cdim != C.getType().getY()) {
            return false;
        }
        if (Trans == ScriptIntrinsicBLAS.NO_TRANSPOSE) {
            if (A.getType().getY() != cdim) {
                return false;
            }
        } else {
            if (A.getType().getX() != cdim) {
                return false;
            }
        }
        if (A.getType().getX() != B.getType().getX() || A.getType().getY() != B.getType().getY()) {
            return false;
        }
        return true;
    }

    private void xHER2K_API_test(int Uplo, int Trans, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation matB : mMatrix) {
                for (Allocation matC : mMatrix) {
                    Element elemA = matA.getType().getElement();
                    if (validateHER2K(elemA, Uplo, Trans, matA, matB, matC)) {
                        try {
                            if (elemA.isCompatible(Element.F32_2(mRS))) {
                                mBLAS.CHER2K(Uplo, Trans, alphaC, matA, matB, betaS, matC);
                            } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                                mBLAS.ZHER2K(Uplo, Trans, alphaZ, matA, matB, betaD, matC);
                            }
                        } catch (RSRuntimeException e) {
                            fail("should NOT throw RSRuntimeException");
                        }
                    } else {
                        try {
                            mBLAS.CHER2K(Uplo, Trans, alphaC, matA, matB, betaS, matC);
                            fail("should throw RSRuntimeException for CHER2K");
                        } catch (RSRuntimeException e) {
                        }
                        try {
                            mBLAS.ZHER2K(Uplo, Trans, alphaZ, matA, matB, betaD, matC);
                            fail("should throw RSRuntimeException for ZHER2K");
                        } catch (RSRuntimeException e) {
                        }
                    }
                }
            }
        }
    }

    public void L3_xHER2K_API(ArrayList<Allocation> mMatrix) {
        for (int Uplo : mUplo) {
            for (int Trans : mTranspose) {
                xHER2K_API_test(Uplo, Trans, mMatrix);
            }
        }
    }

    public void test_L3_CHER2K_API() {
        L3_xHER2K_API(mMatrixC);
    }

    public void test_L3_ZHER2K_API() {
        L3_xHER2K_API(mMatrixZ);
    }

    public void test_L3_CHER2K_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dK, mBLASData.dN));
        Allocation matrixBC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dK, mBLASData.dN));
        Allocation matrixCC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixAC.copyFrom(mBLASData.L3_cHER2K_A_nk);
        matrixBC.copyFrom(mBLASData.L3_cHER2K_B_nk);
        matrixCC.copyFrom(mBLASData.L3_cHER2K_C_nn);

        // Default case: NO_TRANSPOSE
        mBLAS.CHER2K(uplo, trans, alphaC, matrixAC, matrixBC, betaS, matrixCC);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixCRef.copyFrom(mBLASData.L3_cHER2K_o_N);
        verifyMatrix(matrixCRef, matrixCC, true);

        // Case: TRANSPOSE
        matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dK));
        matrixBC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dK));
        matrixAC.copyFrom(mBLASData.L3_cHER2K_A_kn);
        matrixBC.copyFrom(mBLASData.L3_cHER2K_B_kn);
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCC.copyFrom(mBLASData.L3_cHER2K_C_nn);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        mBLAS.CHER2K(uplo, trans, alphaC, matrixAC, matrixBC, betaS, matrixCC);
        matrixCRef.copyFrom(mBLASData.L3_cHER2K_o_H);
        verifyMatrix(matrixCRef, matrixCC, true);

        mRS.finish();
        checkError();
    }

    public void test_L3_ZHER2K_Correctness() {
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dK, mBLASData.dN));
        Allocation matrixBZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dK, mBLASData.dN));
        Allocation matrixCZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixAZ.copyFrom(mBLASData.L3_zHER2K_A_nk);
        matrixBZ.copyFrom(mBLASData.L3_zHER2K_B_nk);
        matrixCZ.copyFrom(mBLASData.L3_zHER2K_C_nn);

        // Default case: NO_TRANSPOSE
        mBLAS.ZHER2K(uplo, trans, alphaZ, matrixAZ, matrixBZ, betaD, matrixCZ);
        Allocation matrixCRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixCRef.copyFrom(mBLASData.L3_zHER2K_o_N);
        verifyMatrix(matrixCRef, matrixCZ, true);

        // Case: TRANSPOSE
        matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dK));
        matrixBZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dK));
        matrixAZ.copyFrom(mBLASData.L3_zHER2K_A_kn);
        matrixBZ.copyFrom(mBLASData.L3_zHER2K_B_kn);
        // Reload matrix C, since it was overwritten by BLAS.
        matrixCZ.copyFrom(mBLASData.L3_zHER2K_C_nn);

        trans = ScriptIntrinsicBLAS.CONJ_TRANSPOSE;
        mBLAS.ZHER2K(uplo, trans, alphaZ, matrixAZ, matrixBZ, betaD, matrixCZ);
        matrixCRef.copyFrom(mBLASData.L3_zHER2K_o_H);
        verifyMatrix(matrixCRef, matrixCZ, true);

        mRS.finish();
        checkError();
    }


    private boolean validateTRMM(Element e, int Side, int Uplo, int TransA, int Diag, Allocation A, Allocation B) {
        if (!validateSide(Side)) {
            return false;
        }
        if (!validateUplo(Uplo)) {
            return false;
        }
        if (!validateTranspose(TransA)) {
            return false;
        }
        if (!validateDiag(Diag)) {
            return false;
        }
        int aM = -1, aN = -1, bM = -1, bN = -1;
        if (!A.getType().getElement().isCompatible(e) ||
            !B.getType().getElement().isCompatible(e)) {
            return false;
        }

        aM = A.getType().getY();
        aN = A.getType().getX();
        if (aM != aN) {
            return false;
        }

        bM = B.getType().getY();
        bN = B.getType().getX();
        if (Side == ScriptIntrinsicBLAS.LEFT) {
            if (aN != bM) {
                return false;
            }
        } else {
            if (bN != aM) {
                return false;
            }
        }
        return true;
    }

    private void xTRMM_API_test(int Side, int Uplo, int TransA, int Diag, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation matB : mMatrix) {
                Element elemA = matA.getType().getElement();
                if (validateTRMM(elemA, Side, Uplo, TransA, Diag, matA, matB)) {
                    try {
                        if (elemA.isCompatible(Element.F32(mRS))) {
                            mBLAS.STRMM(Side, Uplo, TransA, Diag, alphaS, matA, matB);
                        } else if (elemA.isCompatible(Element.F64(mRS))) {
                            mBLAS.DTRMM(Side, Uplo, TransA, Diag, alphaD, matA, matB);
                        } else if (elemA.isCompatible(Element.F32_2(mRS))) {
                            mBLAS.CTRMM(Side, Uplo, TransA, Diag, alphaC, matA, matB);
                        } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                            mBLAS.ZTRMM(Side, Uplo, TransA, Diag, alphaZ, matA, matB);
                        }
                    } catch (RSRuntimeException e) {
                        fail("should NOT throw RSRuntimeException");
                    }
                } else {
                    try {
                        mBLAS.STRMM(Side, Uplo, TransA, Diag, alphaS, matA, matB);
                        fail("should throw RSRuntimeException for STRMM");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.DTRMM(Side, Uplo, TransA, Diag, alphaD, matA, matB);
                        fail("should throw RSRuntimeException for DTRMM");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.CTRMM(Side, Uplo, TransA, Diag, alphaC, matA, matB);
                        fail("should throw RSRuntimeException for CTRMM");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.ZTRMM(Side, Uplo, TransA, Diag, alphaZ, matA, matB);
                        fail("should throw RSRuntimeException for ZTRMM");
                    } catch (RSRuntimeException e) {
                    }
                }
            }
        }
    }

    public void L3_xTRMM_API(ArrayList<Allocation> mMatrix) {
        for (int Side : mSide) {
            for (int Uplo : mUplo) {
                for (int TransA : mTranspose) {
                    for (int Diag : mDiag) {
                        xTRMM_API_test(Side, Uplo, TransA, Diag, mMatrix);
                    }
                }
            }
        }
    }

    public void test_L3_STRMM_API() {
        L3_xTRMM_API(mMatrixS);
    }

    public void test_L3_DTRMM_API() {
        L3_xTRMM_API(mMatrixD);
    }

    public void test_L3_CTRMM_API() {
        L3_xTRMM_API(mMatrixC);
    }

    public void test_L3_ZTRMM_API() {
        L3_xTRMM_API(mMatrixZ);
    }


    public void test_L3_STRMM_Correctness() {
        int side = ScriptIntrinsicBLAS.LEFT;
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;

        // Populate input allocations
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dM, mBLASData.dM));
        Allocation matrixBS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dM));
        matrixAS.copyFrom(mBLASData.L3_sTRMM_A_mm);
        matrixBS.copyFrom(mBLASData.L3_sTRMM_B_mn);

        // Default case: LEFT, UPPER, NO_TRANSPOSE
        mBLAS.STRMM(side, uplo, trans, diag, alphaS, matrixAS, matrixBS);
        Allocation matrixBRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dM));
        matrixBRef.copyFrom(mBLASData.L3_sTRMM_o_LUN);
        verifyMatrix(matrixBRef, matrixBS);

        // Case: RIGHT, LOWER, TRANSPOSE
        matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dN));
        matrixAS.copyFrom(mBLASData.L3_sTRMM_A_nn);
        // Reload matrix B, since it was overwritten by BLAS.
        matrixBS.copyFrom(mBLASData.L3_sTRMM_B_mn);

        side = ScriptIntrinsicBLAS.RIGHT;
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        uplo = ScriptIntrinsicBLAS.LOWER;
        mBLAS.STRMM(side, uplo, trans, diag, alphaS, matrixAS, matrixBS);
        matrixBRef.copyFrom(mBLASData.L3_sTRMM_o_RLT);
        verifyMatrix(matrixBRef, matrixBS);

        mRS.finish();
        checkError();
    }

    public void test_L3_DTRMM_Correctness() {
        int side = ScriptIntrinsicBLAS.LEFT;
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;

        // Populate input allocations
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dM, mBLASData.dM));
        Allocation matrixBD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dM));
        matrixAD.copyFrom(mBLASData.L3_dTRMM_A_mm);
        matrixBD.copyFrom(mBLASData.L3_dTRMM_B_mn);

        // Default case: LEFT, UPPER, NO_TRANSPOSE
        mBLAS.DTRMM(side, uplo, trans, diag, alphaD, matrixAD, matrixBD);
        Allocation matrixBRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dM));
        matrixBRef.copyFrom(mBLASData.L3_dTRMM_o_LUN);
        verifyMatrix(matrixBRef, matrixBD);

        // Case: RIGHT, LOWER, TRANSPOSE
        matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dN));
        matrixAD.copyFrom(mBLASData.L3_dTRMM_A_nn);
        // Reload matrix B, since it was overwritten by BLAS.
        matrixBD.copyFrom(mBLASData.L3_dTRMM_B_mn);

        side = ScriptIntrinsicBLAS.RIGHT;
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        uplo = ScriptIntrinsicBLAS.LOWER;
        mBLAS.DTRMM(side, uplo, trans, diag, alphaD, matrixAD, matrixBD);
        matrixBRef.copyFrom(mBLASData.L3_dTRMM_o_RLT);
        verifyMatrix(matrixBRef, matrixBD);

        mRS.finish();
        checkError();
    }

    public void test_L3_CTRMM_Correctness() {
        int side = ScriptIntrinsicBLAS.LEFT;
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dM, mBLASData.dM));
        Allocation matrixBC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixAC.copyFrom(mBLASData.L3_cTRMM_A_mm);
        matrixBC.copyFrom(mBLASData.L3_cTRMM_B_mn);

        // Default case: LEFT, UPPER, NO_TRANSPOSE
        mBLAS.CTRMM(side, uplo, trans, diag, alphaC, matrixAC, matrixBC);
        Allocation matrixBRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixBRef.copyFrom(mBLASData.L3_cTRMM_o_LUN);
        verifyMatrix(matrixBRef, matrixBC);

        // Case: RIGHT, LOWER, TRANSPOSE
        matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixAC.copyFrom(mBLASData.L3_cTRMM_A_nn);
        // Reload matrix B, since it was overwritten by BLAS.
        matrixBC.copyFrom(mBLASData.L3_cTRMM_B_mn);

        side = ScriptIntrinsicBLAS.RIGHT;
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        uplo = ScriptIntrinsicBLAS.LOWER;
        mBLAS.CTRMM(side, uplo, trans, diag, alphaC, matrixAC, matrixBC);
        matrixBRef.copyFrom(mBLASData.L3_cTRMM_o_RLT);
        verifyMatrix(matrixBRef, matrixBC);

        mRS.finish();
        checkError();
    }

    public void test_L3_ZTRMM_Correctness() {
        int side = ScriptIntrinsicBLAS.LEFT;
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dM, mBLASData.dM));
        Allocation matrixBZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixAZ.copyFrom(mBLASData.L3_zTRMM_A_mm);
        matrixBZ.copyFrom(mBLASData.L3_zTRMM_B_mn);

        // Default case: LEFT, UPPER, NO_TRANSPOSE
        mBLAS.ZTRMM(side, uplo, trans, diag, alphaZ, matrixAZ, matrixBZ);
        Allocation matrixBRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixBRef.copyFrom(mBLASData.L3_zTRMM_o_LUN);
        verifyMatrix(matrixBRef, matrixBZ);

        // Case: RIGHT, LOWER, TRANSPOSE
        matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixAZ.copyFrom(mBLASData.L3_zTRMM_A_nn);
        // Reload matrix B, since it was overwritten by BLAS.
        matrixBZ.copyFrom(mBLASData.L3_zTRMM_B_mn);

        side = ScriptIntrinsicBLAS.RIGHT;
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        uplo = ScriptIntrinsicBLAS.LOWER;
        mBLAS.ZTRMM(side, uplo, trans, diag, alphaZ, matrixAZ, matrixBZ);
        matrixBRef.copyFrom(mBLASData.L3_zTRMM_o_RLT);
        verifyMatrix(matrixBRef, matrixBZ);

        mRS.finish();
        checkError();
    }


    private boolean validateTRSM(Element e, int Side, int Uplo, int TransA, int Diag, Allocation A, Allocation B) {
        int adim = -1, bM = -1, bN = -1;
        if (!validateSide(Side)) {
            return false;
        }
        if (!validateTranspose(TransA)) {
            return false;
        }
        if (!validateUplo(Uplo)) {
            return false;
        }
        if (!validateDiag(Diag)) {
            return false;
        }
        if (!A.getType().getElement().isCompatible(e) ||
            !B.getType().getElement().isCompatible(e)) {
            return false;
        }
        adim = A.getType().getX();
        if (adim != A.getType().getY()) {
            // this may be unnecessary, the restriction could potentially be relaxed
            // A needs to contain at least that symmetric matrix but could theoretically be larger
            // for now we assume adapters are sufficient, will reevaluate in the future
            return false;
        }
        bM = B.getType().getY();
        bN = B.getType().getX();
        if (Side == ScriptIntrinsicBLAS.LEFT) {
            // A is M*M
            if (adim != bM) {
                return false;
            }
        } else {
            // A is N*N
            if (adim != bN) {
                return false;
            }
        }
        return true;
    }

    private void xTRSM_API_test(int Side, int Uplo, int TransA, int Diag, ArrayList<Allocation> mMatrix) {
        for (Allocation matA : mMatrix) {
            for (Allocation matB : mMatrix) {
                Element elemA = matA.getType().getElement();
                if (validateTRSM(elemA, Side, Uplo, TransA, Diag, matA, matB)) {
                    try {
                        if (elemA.isCompatible(Element.F32(mRS))) {
                            mBLAS.STRSM(Side, Uplo, TransA, Diag, alphaS, matA, matB);
                        } else if (elemA.isCompatible(Element.F64(mRS))) {
                            mBLAS.DTRSM(Side, Uplo, TransA, Diag, alphaD, matA, matB);
                        } else if (elemA.isCompatible(Element.F32_2(mRS))) {
                            mBLAS.CTRSM(Side, Uplo, TransA, Diag, alphaC, matA, matB);
                        } else if (elemA.isCompatible(Element.F64_2(mRS))) {
                            mBLAS.ZTRSM(Side, Uplo, TransA, Diag, alphaZ, matA, matB);
                        }
                    } catch (RSRuntimeException e) {
                        fail("should NOT throw RSRuntimeException");
                    }
                } else {
                    try {
                        mBLAS.STRSM(Side, Uplo, TransA, Diag, alphaS, matA, matB);
                        fail("should throw RSRuntimeException for STRSM");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.DTRSM(Side, Uplo, TransA, Diag, alphaD, matA, matB);
                        fail("should throw RSRuntimeException for DTRSM");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.CTRSM(Side, Uplo, TransA, Diag, alphaC, matA, matB);
                        fail("should throw RSRuntimeException for CTRSM");
                    } catch (RSRuntimeException e) {
                    }
                    try {
                        mBLAS.ZTRSM(Side, Uplo, TransA, Diag, alphaZ, matA, matB);
                        fail("should throw RSRuntimeException for ZTRSM");
                    } catch (RSRuntimeException e) {
                    }
                }
            }
        }
    }

    public void L3_xTRSM_API(ArrayList<Allocation> mMatrix) {
        for (int Side : mSide) {
            for (int Uplo : mUplo) {
                for (int TransA : mTranspose) {
                    for (int Diag : mDiag) {
                        xTRSM_API_test(Side, Uplo, TransA, Diag, mMatrix);
                    }
                }
            }
        }
    }

    public void test_L3_STRSM_API() {
        L3_xTRSM_API(mMatrixS);
    }

    public void test_L3_DTRSM_API() {
        L3_xTRSM_API(mMatrixD);
    }

    public void test_L3_CTRSM_API() {
        L3_xTRSM_API(mMatrixC);
    }

    public void test_L3_ZTRSM_API() {
        L3_xTRSM_API(mMatrixZ);
    }

    public void test_L3_STRSM_Correctness() {
        int side = ScriptIntrinsicBLAS.LEFT;
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;

        // Populate input allocations
        Allocation matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dM, mBLASData.dM));
        Allocation matrixBS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dM));
        matrixAS.copyFrom(mBLASData.L3_sTRSM_A_mm);
        matrixBS.copyFrom(mBLASData.L3_sTRSM_B_mn);

        // Default case: LEFT, UPPER, NO_TRANSPOSE
        mBLAS.STRSM(side, uplo, trans, diag, alphaS, matrixAS, matrixBS);
        Allocation matrixBRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dM));
        matrixBRef.copyFrom(mBLASData.L3_sTRSM_o_LUN);
        verifyMatrix(matrixBRef, matrixBS);

        // Case: RIGHT, LOWER, TRANSPOSE
        matrixAS = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32(mRS), mBLASData.dN, mBLASData.dN));
        matrixAS.copyFrom(mBLASData.L3_sTRSM_A_nn);
        // Reload matrix B, since it was overwritten by BLAS.
        matrixBS.copyFrom(mBLASData.L3_sTRSM_B_mn);

        side = ScriptIntrinsicBLAS.RIGHT;
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        uplo = ScriptIntrinsicBLAS.LOWER;
        mBLAS.STRSM(side, uplo, trans, diag, alphaS, matrixAS, matrixBS);
        matrixBRef.copyFrom(mBLASData.L3_sTRSM_o_RLT);
        verifyMatrix(matrixBRef, matrixBS);

        mRS.finish();
        checkError();
    }

    public void test_L3_DTRSM_Correctness() {
        int side = ScriptIntrinsicBLAS.LEFT;
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;

        // Populate input allocations
        Allocation matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dM, mBLASData.dM));
        Allocation matrixBD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dM));
        matrixAD.copyFrom(mBLASData.L3_dTRSM_A_mm);
        matrixBD.copyFrom(mBLASData.L3_dTRSM_B_mn);

        // Default case: LEFT, UPPER, NO_TRANSPOSE
        mBLAS.DTRSM(side, uplo, trans, diag, alphaD, matrixAD, matrixBD);
        Allocation matrixBRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dM));
        matrixBRef.copyFrom(mBLASData.L3_dTRSM_o_LUN);
        verifyMatrix(matrixBRef, matrixBD);

        // Case: RIGHT, LOWER, TRANSPOSE
        matrixAD = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64(mRS), mBLASData.dN, mBLASData.dN));
        matrixAD.copyFrom(mBLASData.L3_dTRSM_A_nn);
        // Reload matrix B, since it was overwritten by BLAS.
        matrixBD.copyFrom(mBLASData.L3_dTRSM_B_mn);

        side = ScriptIntrinsicBLAS.RIGHT;
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        uplo = ScriptIntrinsicBLAS.LOWER;
        mBLAS.DTRSM(side, uplo, trans, diag, alphaD, matrixAD, matrixBD);
        matrixBRef.copyFrom(mBLASData.L3_dTRSM_o_RLT);
        verifyMatrix(matrixBRef, matrixBD);

        mRS.finish();
        checkError();
    }

    public void test_L3_CTRSM_Correctness() {
        int side = ScriptIntrinsicBLAS.LEFT;
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;

        // Populate input allocations
        Allocation matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dM, mBLASData.dM));
        Allocation matrixBC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixAC.copyFrom(mBLASData.L3_cTRSM_A_mm);
        matrixBC.copyFrom(mBLASData.L3_cTRSM_B_mn);

        // Default case: LEFT, UPPER, NO_TRANSPOSE
        mBLAS.CTRSM(side, uplo, trans, diag, alphaC, matrixAC, matrixBC);
        Allocation matrixBRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixBRef.copyFrom(mBLASData.L3_cTRSM_o_LUN);
        verifyMatrix(matrixBRef, matrixBC);

        // Case: RIGHT, LOWER, TRANSPOSE
        matrixAC = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F32_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixAC.copyFrom(mBLASData.L3_cTRSM_A_nn);
        // Reload matrix B, since it was overwritten by BLAS.
        matrixBC.copyFrom(mBLASData.L3_cTRSM_B_mn);

        side = ScriptIntrinsicBLAS.RIGHT;
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        uplo = ScriptIntrinsicBLAS.LOWER;
        mBLAS.CTRSM(side, uplo, trans, diag, alphaC, matrixAC, matrixBC);
        matrixBRef.copyFrom(mBLASData.L3_cTRSM_o_RLT);
        verifyMatrix(matrixBRef, matrixBC);

        mRS.finish();
        checkError();
    }

    public void test_L3_ZTRSM_Correctness() {
        int side = ScriptIntrinsicBLAS.LEFT;
        int trans = ScriptIntrinsicBLAS.NO_TRANSPOSE;
        int uplo = ScriptIntrinsicBLAS.UPPER;
        int diag = ScriptIntrinsicBLAS.NON_UNIT;

        // Populate input allocations
        Allocation matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dM, mBLASData.dM));
        Allocation matrixBZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixAZ.copyFrom(mBLASData.L3_zTRSM_A_mm);
        matrixBZ.copyFrom(mBLASData.L3_zTRSM_B_mn);

        // Default case: LEFT, UPPER, NO_TRANSPOSE
        mBLAS.ZTRSM(side, uplo, trans, diag, alphaZ, matrixAZ, matrixBZ);
        Allocation matrixBRef = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dM));
        matrixBRef.copyFrom(mBLASData.L3_zTRSM_o_LUN);
        verifyMatrix(matrixBRef, matrixBZ);

        // Case: RIGHT, LOWER, TRANSPOSE
        matrixAZ = Allocation.createTyped(mRS, Type.createXY(mRS, Element.F64_2(mRS), mBLASData.dN, mBLASData.dN));
        matrixAZ.copyFrom(mBLASData.L3_zTRSM_A_nn);
        // Reload matrix B, since it was overwritten by BLAS.
        matrixBZ.copyFrom(mBLASData.L3_zTRSM_B_mn);

        side = ScriptIntrinsicBLAS.RIGHT;
        trans = ScriptIntrinsicBLAS.TRANSPOSE;
        uplo = ScriptIntrinsicBLAS.LOWER;
        mBLAS.ZTRSM(side, uplo, trans, diag, alphaZ, matrixAZ, matrixBZ);
        matrixBRef.copyFrom(mBLASData.L3_zTRSM_o_RLT);
        verifyMatrix(matrixBRef, matrixBZ);

        mRS.finish();
        checkError();
    }
}
