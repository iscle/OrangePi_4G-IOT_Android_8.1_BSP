#
# Copyright (C) 2015 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# This Python script is used to generate all input and output
# reference data into BLASData.txt

#!/usr/bin/python

from numpy import *

# functions used for generating input matrices.

# Modify a regular matrix to a triangular matrix.
def triangularMatrixGen(a, uplo):
    if uplo == 'u': #upper = 1, lower = 2
        for i in range(1, a.shape[0]):
            for j in range(0, i):
                a[i, j] = 0
    elif uplo == 'l':
        for i in range(0, a.shape[0]-1):
            for j in range(i+1, a.shape[1]):
                a[i, j] = 0

# Modify a regular matrix to a symmetric matrix.
def symm(a):
    for i in range(1, a.shape[0]):
        for j in range(0, i):
            a[i, j] = a[j, i];

# Modify a regular matrix to a hermitian matrix.
def herm(a):
    for i in range(0, a.shape[0]):
        a[i,i] = complex(a[i,i].real, 0);
    for i in range(1, a.shape[0]):
        for j in range(0, i):
            a[i, j] = complex(a[j, i].real, -a[j, i].imag);

# Zero all elments in a matrix
def zero(a):
    for i in range(0, a.shape[0]):
        for j in range(0, a.shape[1]):
            a[i, j] = 0;

# Generate a random float matrix given a scale.
def sMatGen(m, n, scale):
    a = mat(random.randint(1, 10, size=(m, n)).astype('f4')/scale)
    return a;

# Generate a random double matrix given a scale.
def dMatGen(m, n, scale):
    a = mat(random.randint(1, 10, size=(m, n)).astype('f8')/scale)
    return a;

# Generate a random float complex matrix given a scale.
def cMatGen(m, n, scale):
    a_real = mat(random.randint(1, 10, size=(m, n)).astype('f4')/scale)
    a_img = mat(random.randint(1, 10, size=(m, n)).astype('f4')/scale)
    a = a_real + 1j * a_img
    return a;

# Generate a random double complex matrix given a scale.
def zMatGen(m, n, scale):
    a_real = mat(random.randint(1, 10, size=(m, n)).astype('f8')/scale)
    a_img = mat(random.randint(1, 10, size=(m, n)).astype('f8')/scale)
    a = a_real + 1j * a_img
    return a;

# A wrapper to generated random matrices given a scale
def matrixCreateScale(dt, m, n, scale):
    if dt == 's':
        return sMatGen(m, n, scale);
    elif dt == 'd':
        return dMatGen(m, n, scale);
    elif dt == 'c':
        return cMatGen(m, n, scale);
    else:
        return zMatGen(m, n, scale);

# A wrapper to generated random matrices
def matrixCreate(dt, m, n):
    return matrixCreateScale(dt, m, n, 10);

# Write a float matrix into a given file.
# For each element, can pad arbitrary number of 0s after it.
def writeFloatMatrix(a, name, skip, fo):
    fo.write(name + '\n');
    for i in range(0, a.shape[0]):
        for j in range(0, a.shape[1]):
            fo.write(str(a[i,j]) + ", ");
            for hh in range(0, skip):
                fo.write("0.0, ");
    fo.write("\n\n");

# Write a double matrix into a given file.
# For each element, can pad arbitrary number of 0s after it.
def writeDoubleMatrix(a, name, skip, fo):
    writeFloatMatrix(a, name, skip, fo);

# Write a float complex matrix into a given file.
# For each element, can pad arbitrary number of 0s after it.
def writeFloatComplexMatrix(a, name, skip, fo):
    fo.write(name + '\n');
    for i in range(0, a.shape[0]):
        for j in range(0, a.shape[1]):
            fo.write(str(real(a[i,j])) + ", ");
            fo.write(str(imag(a[i,j])) + ", ");
            for hh in range(0, skip):
                fo.write("0.0, ");
                fo.write("0.0, ");
    fo.write("\n\n");

# Write a double complex matrix into a given file.
# For each element, can pad arbitrary number of 0s after it.
def writeDoubleComplexMatrix(a, name, skip, fo):
    writeFloatComplexMatrix(a, name, skip, fo);

# Wrapper to write a matrix into a given file.
# For each element, can pad arbitrary number of 0s after it.
def writeMatrixWithIncrements(dt, a, name, skip, fo):
    if dt == 's':
        writeFloatMatrix(a, name, skip, fo);
    elif dt == 'd':
        writeDoubleMatrix(a, name, skip, fo);
    elif dt == 'c':
        writeFloatComplexMatrix(a, name, skip, fo);
    else:
        writeDoubleComplexMatrix(a, name, skip, fo);

# Wrapper to write a matrix into a given file.
def writeMatrix(dt, a, name, fo):
    writeMatrixWithIncrements(dt, a, name, 0, fo);

# Write a symmetric or hermitian float matrix into a given file, in a packed form.
def writeFloatPackedMatrix(a, name, fo):
    fo.write(name + '\n');
    for i in range(0, a.shape[0]):
        for j in range(i, a.shape[1]):
            fo.write(str(a[i,j]) + ", ");
    fo.write("\n\n");

# Write a symmetric or hermitian double matrix into a given file, in a packed form.
def writeDoublePackedMatrix(a, name, fo):
    writeFloatPackedMatrix(a, name, fo);

# Write a symmetric or hermitian float complex matrix into a given file, in a packed form.
def writeFloatComplexPackedMatrix(a, name, fo):
    fo.write(name + '\n');
    for i in range(0, a.shape[0]):
        for j in range(i, a.shape[1]):
            fo.write(str(real(a[i,j])) + ", ");
            fo.write(str(imag(a[i,j])) + ", ");
    fo.write("\n\n");

# Write a symmetric or hermitian double complex matrix into a given file, in a packed form.
def writeDoubleComplexPackedMatrix(a, name, fo):
    writeFloatComplexPackedMatrix(a, name, fo);

# Wrapper to write a symmetric or hermitian matrix into a given file, in a packed form.
def writePackedMatrix(dt, a, name, fo):
    if dt == 's':
        writeFloatPackedMatrix(a, name, fo);
    elif dt == 'd':
        writeDoublePackedMatrix(a, name, fo);
    elif dt == 'c':
        writeFloatComplexPackedMatrix(a, name, fo);
    else:
        writeDoubleComplexPackedMatrix(a, name, fo);

# Write a float band matrix into a given file, in a banded-storage form.
def writeGeneralFloatBandedMatrix(a, kl, ku, name, fo):
    m = a.shape[0];
    n = a.shape[1];
    b = sMatGen(m, kl + ku + 1, 1);
    zero(b);
    for i in range(0, m):
        for j in range(max(0, i-kl), min(i+ku+1, n)):
            b[i, j-i+kl] = a[i, j]
    writeFloatMatrix(b, name, 0, fo);
    zero(a);
    for i in range(0, m):
        for j in range(max(0, i-kl), min(i+ku+1, n)):
            a[i, j] = b[i, j-i+kl]

# Write a double band matrix into a given file, in a banded-storage form.
def writeGeneralDoubleBandedMatrix(a, kl, ku, name, fo):
    m = a.shape[0];
    n = a.shape[1];
    b = dMatGen(m, kl + ku + 1, 1);
    zero(b);
    for i in range(0, m):
        for j in range(max(0, i-kl), min(i+ku+1, n)):
            b[i, j-i+kl] = a[i, j]
    writeDoubleMatrix(b, name, 0, fo);
    zero(a);
    for i in range(0, m):
        for j in range(max(0, i-kl), min(i+ku+1, n)):
            a[i, j] = b[i, j-i+kl]

# Write a float complex band matrix into a given file, in a banded-storage form.
def writeGeneralFloatComplexBandedMatrix(a, kl, ku, name, fo):
    m = a.shape[0];
    n = a.shape[1];
    b = cMatGen(m, kl + ku + 1, 1);
    zero(b);
    for i in range(0, m):
        for j in range(max(0, i-kl), min(i+ku+1, n)):
            b[i, j-i+kl] = a[i, j]
    writeFloatComplexMatrix(b, name, 0, fo);
    zero(a);
    for i in range(0, m):
        for j in range(max(0, i-kl), min(i+ku+1, n)):
            a[i, j] = b[i, j-i+kl]

# Write a double complex band matrix into a given file, in a banded-storage form.
def writeGeneralDoubleComplexBandedMatrix(a, kl, ku, name, fo):
    m = a.shape[0];
    n = a.shape[1];
    b = zMatGen(m, kl + ku + 1, 1);
    zero(b);
    for i in range(0, m):
        for j in range(max(0, i-kl), min(i+ku+1, n)):
            b[i, j-i+kl] = a[i, j]
    writeDoubleComplexMatrix(b, name, 0, fo);
    zero(a);
    for i in range(0, m):
        for j in range(max(0, i-kl), min(i+ku+1, n)):
            a[i, j] = b[i, j-i+kl]

# Wrapper to write a band matrix into a given file, in a banded-storage form.
def writeGeneralBandedMatrix(dt, a, kl, ku, name, fo):
    if dt == 's':
        writeGeneralFloatBandedMatrix(a, kl, ku, name, fo);
    elif dt == 'd':
        writeGeneralDoubleBandedMatrix(a, kl, ku, name, fo);
    elif dt == 'c':
        writeGeneralFloatComplexBandedMatrix(a, kl, ku, name, fo);
    else:
        writeGeneralDoubleComplexBandedMatrix(a, kl, ku, name, fo);

# Write a float symmetric or hermitian band matrix into a given file, in a banded-storage form.
def writeFloatSymmBandedMatrix(a, k, name, fo):
    n = a.shape[1];
    b = sMatGen(n, k+1, 1);
    zero(b);
    for i in range(0, n):
        for j in range(i, min(i+k+1, n)):
            b[i, j-i] = a[i, j]
    writeFloatMatrix(b, name, 0, fo);
    zero(a);
    for i in range(0, n):
        for j in range(i, min(i+k+1, n)):
            a[i, j] = b[i, j-i]

# Write a double symmetric or hermitian band matrix into a given file, in a banded-storage form.
def writeDoubleSymmBandedMatrix(a, k, name, fo):
    n = a.shape[1];
    b = dMatGen(n, k+1, 1);
    zero(b);
    for i in range(0, n):
        for j in range(i, min(i+k+1, n)):
            b[i, j-i] = a[i, j]
    writeDoubleMatrix(b, name, 0, fo);
    zero(a);
    for i in range(0, n):
        for j in range(i, min(i+k+1, n)):
            a[i, j] = b[i, j-i]

# Write a float complex symmetric or hermitian band matrix into a given file, in a banded-storage form.
def writeFloatComplexSymmBandedMatrix(a, k, name, fo):
    n = a.shape[1];
    b = cMatGen(n, k+1, 1);
    zero(b);
    for i in range(0, n):
        for j in range(i, min(i+k+1, n)):
            b[i, j-i] = a[i, j]
    writeFloatComplexMatrix(b, name, 0, fo);
    zero(a);
    for i in range(0, n):
        for j in range(i, min(i+k+1, n)):
            a[i, j] = b[i, j-i]

# Write a double complex symmetric or hermitian band matrix into a given file, in a banded-storage form.
def writeDoubleComplexSymmBandedMatrix(a, k, name, fo):
    n = a.shape[1];
    b = zMatGen(n, k+1, 1);
    zero(b);
    for i in range(0, n):
        for j in range(i, min(i+k+1, n)):
            b[i, j-i] = a[i, j]
    writeDoubleComplexMatrix(b, name, 0, fo);
    zero(a);
    for i in range(0, n):
        for j in range(i, min(i+k+1, n)):
            a[i, j] = b[i, j-i]

# Wrapper to write a symmetric or hermitian band matrix into a given file, in a banded-storage form.
def writeSymmBandedMatrix(dt, a, k, name, fo):
    if dt == 's':
        writeFloatSymmBandedMatrix(a, k, name, fo);
    elif dt == 'd':
        writeDoubleSymmBandedMatrix(a, k, name, fo);
    elif dt == 'c':
        writeFloatComplexSymmBandedMatrix(a, k, name, fo);
    else:
        writeDoubleComplexSymmBandedMatrix(a, k, name, fo);



#L3 Functions, generate input and output matrices to file.
def L3_xGEMM(fo, alpha, beta, m, n, k):
    dataType = ['s', 'd', 'c', 'z'];

    for dt in dataType:
        a = matrixCreate(dt, m, k);
        b = matrixCreate(dt, k, n);
        c = matrixCreate(dt, m, n);
        writeMatrix(dt, a, "L3_" + dt + "GEMM_A_mk", fo);
        writeMatrix(dt, b, "L3_" + dt + "GEMM_B_kn", fo);
        writeMatrix(dt, c, "L3_" + dt + "GEMM_C_mn", fo);

        d = alpha * a * b + beta * c;
        writeMatrix(dt, d, "L3_" + dt + "GEMM_o_NN", fo);

        a = matrixCreate(dt, k, m);
        b = matrixCreate(dt, n, k);
        writeMatrix(dt, a, "L3_" + dt + "GEMM_A_km", fo);
        writeMatrix(dt, b, "L3_" + dt + "GEMM_B_nk", fo);

        d = alpha * a.T * b.T + beta * c;
        writeMatrix(dt, d, "L3_" + dt + "GEMM_o_TT", fo);
        d = alpha * a.H * b.H + beta * c;
        writeMatrix(dt, d, "L3_" + dt + "GEMM_o_HH", fo);

def L3_xSYMM(fo, alpha, beta, m, n):
    dataType = ['s', 'd', 'c', 'z'];

    for dt in dataType:
        a = matrixCreate(dt, m, m);
        symm(a);
        writeMatrix(dt, a, "L3_" + dt + "SYMM_A_mm", fo);

        b = matrixCreate(dt, m, n);
        c = matrixCreate(dt, m, n);
        writeMatrix(dt, b, "L3_" + dt + "SYMM_B_mn", fo);
        writeMatrix(dt, c, "L3_" + dt + "SYMM_C_mn", fo);

        d = alpha * a * b + beta * c;
        writeMatrix(dt, d, "L3_" + dt + "SYMM_o_L", fo);

        a = matrixCreate(dt, n, n);
        symm(a);
        writeMatrix(dt, a, "L3_" + dt + "SYMM_A_nn", fo);
        d = alpha * b * a + beta * c;
        writeMatrix(dt, d, "L3_" + dt + "SYMM_o_R", fo);

def L3_xHEMM(fo, alpha, beta, m, n):
    dataType = ['c', 'z'];

    for dt in dataType:
        a = matrixCreate(dt, m, m);
        herm(a);
        writeMatrix(dt, a, "L3_" + dt + "HEMM_A_mm", fo);

        b = matrixCreate(dt, m, n);
        c = matrixCreate(dt, m, n);
        writeMatrix(dt, b, "L3_" + dt + "HEMM_B_mn", fo);
        writeMatrix(dt, c, "L3_" + dt + "HEMM_C_mn", fo);

        d = alpha * a * b + beta * c;
        writeMatrix(dt, d, "L3_" + dt + "HEMM_o_L", fo);

        a = matrixCreate(dt, n, n);
        herm(a);
        writeMatrix(dt, a, "L3_" + dt + "HEMM_A_nn", fo);
        d = alpha * b * a + beta * c;
        writeMatrix(dt, d, "L3_" + dt + "HEMM_o_R", fo);

def L3_xSYRK(fo, alpha, beta, n, k):
    dataType = ['s', 'd', 'c', 'z'];

    for dt in dataType:
        a = matrixCreate(dt, n, k);
        writeMatrix(dt, a, "L3_" + dt + "SYRK_A_nk", fo);
        c = matrixCreate(dt, n, n);
        symm(c);
        writeMatrix(dt, c, "L3_" + dt + "SYRK_C_nn", fo);
        d = alpha * a * a.T + beta * c;
        writeMatrix(dt, d, "L3_" + dt + "SYRK_o_N", fo);

        a = matrixCreate(dt, k, n);
        writeMatrix(dt, a, "L3_" + dt + "SYRK_A_kn", fo);
        d = alpha * a.T * a + beta * c;
        writeMatrix(dt, d, "L3_" + dt + "SYRK_o_T", fo);

def L3_xHERK(fo, alpha, beta, n, k):
    dataType = ['c', 'z'];

    for dt in dataType:
        a = matrixCreate(dt, n, k);
        writeMatrix(dt, a, "L3_" + dt + "HERK_A_nk", fo);
        c = matrixCreate(dt, n, n);
        herm(c);
        writeMatrix(dt, c, "L3_" + dt + "HERK_C_nn", fo);
        d = alpha * a * a.H + beta * c;
        writeMatrix(dt, d, "L3_" + dt + "HERK_o_N", fo);

        a = matrixCreate(dt, k, n);
        writeMatrix(dt, a, "L3_" + dt + "HERK_A_kn", fo);
        d = alpha * a.H * a + beta * c;
        writeMatrix(dt, d, "L3_" + dt + "HERK_o_H", fo);

def L3_xSYR2K(fo, alpha, beta, n, k):
    dataType = ['s', 'd', 'c', 'z'];

    for dt in dataType:
        a = matrixCreate(dt, n, k);
        b = matrixCreate(dt, n, k);
        writeMatrix(dt, a, "L3_" + dt + "SYR2K_A_nk", fo);
        writeMatrix(dt, b, "L3_" + dt + "SYR2K_B_nk", fo);
        c = matrixCreate(dt, n, n);
        symm(c);
        writeMatrix(dt, c, "L3_" + dt + "SYR2K_C_nn", fo);
        d = alpha * (a * b.T + b * a.T) + beta * c;
        writeMatrix(dt, d, "L3_" + dt + "SYR2K_o_N", fo);

        a = matrixCreate(dt, k, n);
        b = matrixCreate(dt, k, n);
        writeMatrix(dt, a, "L3_" + dt + "SYR2K_A_kn", fo);
        writeMatrix(dt, b, "L3_" + dt + "SYR2K_B_kn", fo);
        d = alpha * (a.T * b + b.T * a) + beta * c;
        writeMatrix(dt, d, "L3_" + dt + "SYR2K_o_T", fo);

def L3_xHER2K(fo, alpha, beta, n, k):
    dataType = ['c', 'z'];

    for dt in dataType:
        a = matrixCreate(dt, n, k);
        b = matrixCreate(dt, n, k);
        writeMatrix(dt, a, "L3_" + dt + "HER2K_A_nk", fo);
        writeMatrix(dt, b, "L3_" + dt + "HER2K_B_nk", fo);
        c = matrixCreate(dt, n, n);
        herm(c);
        writeMatrix(dt, c, "L3_" + dt + "HER2K_C_nn", fo);
        d = alpha * (a * b.H + b * a.H) + beta * c;
        writeMatrix(dt, d, "L3_" + dt + "HER2K_o_N", fo);

        a = matrixCreate(dt, k, n);
        b = matrixCreate(dt, k, n);
        writeMatrix(dt, a, "L3_" + dt + "HER2K_A_kn", fo);
        writeMatrix(dt, b, "L3_" + dt + "HER2K_B_kn", fo);
        d = alpha * (a.H * b + b.H * a) + beta * c;
        writeMatrix(dt, d, "L3_" + dt + "HER2K_o_H", fo);


def L3_xTRMM(fo, alpha, m, n):
    dataType = ['s', 'd', 'c', 'z'];

    for dt in dataType:
        a = matrixCreate(dt, m, m);
        triangularMatrixGen(a, 'u');
        writeMatrix(dt, a, "L3_" + dt + "TRMM_A_mm", fo);
        b = matrixCreate(dt, m, n);
        writeMatrix(dt, b, "L3_" + dt + "TRMM_B_mn", fo);
        d = alpha * a * b;
        writeMatrix(dt, d, "L3_" + dt + "TRMM_o_LUN", fo);

        a = matrixCreate(dt, n, n);
        triangularMatrixGen(a, 'l');
        writeMatrix(dt, a, "L3_" + dt + "TRMM_A_nn", fo);
        d = alpha * b * a.T;
        writeMatrix(dt, d, "L3_" + dt + "TRMM_o_RLT", fo);

def L3_xTRSM(fo, alpha, m, n):
    dataType = ['s', 'd', 'c', 'z'];

    for dt in dataType:
        a = matrixCreateScale(dt, m, m, 1);
        triangularMatrixGen(a, 'u');
        writeMatrix(dt, a, "L3_" + dt + "TRSM_A_mm", fo);
        b = matrixCreate(dt, m, n);
        writeMatrix(dt, b, "L3_" + dt + "TRSM_B_mn", fo);

        d = alpha * (a.I * b);
        writeMatrix(dt, d, "L3_" + dt + "TRSM_o_LUN", fo);

        a = matrixCreate(dt, n, n);
        triangularMatrixGen(a, 'l');
        writeMatrix(dt, a, "L3_" + dt + "TRSM_A_nn", fo);

        d = alpha * (b * a.I.T);
        writeMatrix(dt, d, "L3_" + dt + "TRSM_o_RLT", fo);

#L2 Functions, generate input and output matrices to file.
def L2_xGEMV(fo, alpha, beta, m, n):
    dataType = ['s', 'd', 'c', 'z'];
    for dt in dataType:
        a = matrixCreate(dt, m, n);
        writeMatrix(dt, a, "L2_" + dt + "GEMV_A_mn", fo);

        x = matrixCreate(dt, n, 1);
        writeMatrix(dt, x, "L2_" + dt + "GEMV_x_n1", fo);
        writeMatrixWithIncrements(dt, x, "L2_" + dt + "GEMV_x_n2", 1, fo);

        y = matrixCreate(dt, m, 1);
        writeMatrix(dt, y, "L2_" + dt + "GEMV_y_m1", fo);
        writeMatrixWithIncrements(dt, y, "L2_" + dt + "GEMV_y_m2", 2, fo);

        d = alpha * a * x + beta * y;
        writeMatrix(dt, d, "L2_" + dt + "GEMV_o_N", fo);
        writeMatrixWithIncrements(dt, d, "L2_" + dt + "GEMV_o_N2", 2, fo);

        d = alpha * a.T * y + beta * x;
        writeMatrix(dt, d, "L2_" + dt + "GEMV_o_T", fo);

        d = alpha * a.H * y + beta * x;
        writeMatrix(dt, d, "L2_" + dt + "GEMV_o_H", fo);

def L2_xGBMV(fo, alpha, beta, m, n, kl, ku):
    dataType = ['s', 'd', 'c', 'z'];
    for dt in dataType:
        a = matrixCreate(dt, m, n);
        writeGeneralBandedMatrix(dt, a, kl, ku, "L2_" + dt + "GBMV_A_mn", fo);

        x = matrixCreate(dt, n, 1);
        writeMatrix(dt, x, "L2_" + dt + "GBMV_x_n1", fo);
        writeMatrixWithIncrements(dt, x, "L2_" + dt + "GBMV_x_n2", 1, fo);

        y = matrixCreate(dt, m, 1);
        writeMatrix(dt, y, "L2_" + dt + "GBMV_y_m1", fo);
        writeMatrixWithIncrements(dt, y, "L2_" + dt + "GBMV_y_m2", 2, fo);

        d = alpha * a * x + beta * y;
        writeMatrix(dt, d, "L2_" + dt + "GBMV_o_N", fo);
        writeMatrixWithIncrements(dt, d, "L2_" + dt + "GBMV_o_N2", 2, fo);

        d = alpha * a.T * y + beta * x;
        writeMatrix(dt, d, "L2_" + dt + "GBMV_o_T", fo);

        d = alpha * a.H * y + beta * x;
        writeMatrix(dt, d, "L2_" + dt + "GBMV_o_H", fo);

def L2_xHEMV(fo, alpha, beta, n):
    dataType = ['c', 'z'];
    for dt in dataType:
        a = matrixCreate(dt, n, n);
        herm(a);
        writeMatrix(dt, a, "L2_" + dt + "HEMV_A_nn", fo);
        writePackedMatrix(dt, a, "L2_" + dt + "HEMV_A_nn_pu", fo);

        x = matrixCreate(dt, n, 1);
        writeMatrix(dt, x, "L2_" + dt + "HEMV_x_n1", fo);
        writeMatrixWithIncrements(dt, x, "L2_" + dt + "HEMV_x_n2", 1, fo);

        y = matrixCreate(dt, n, 1);
        writeMatrix(dt, y, "L2_" + dt + "HEMV_y_n1", fo);
        writeMatrixWithIncrements(dt, y, "L2_" + dt + "HEMV_y_n2", 2, fo);

        d = alpha * a * x + beta * y;
        writeMatrix(dt, d, "L2_" + dt + "HEMV_o_N", fo);
        writeMatrixWithIncrements(dt, d, "L2_" + dt + "HEMV_o_N2", 2, fo);

def L2_xHBMV(fo, alpha, beta, n, k):
    dataType = ['c', 'z'];
    for dt in dataType:
        a = matrixCreate(dt, n, n);
        herm(a);
        writeSymmBandedMatrix(dt, a, k, "L2_" + dt + "HBMV_A_nn", fo);
        herm(a);

        x = matrixCreate(dt, n, 1);
        writeMatrix(dt, x, "L2_" + dt + "HBMV_x_n1", fo);
        writeMatrixWithIncrements(dt, x, "L2_" + dt + "HBMV_x_n2", 1, fo);

        y = matrixCreate(dt, n, 1);
        writeMatrix(dt, y, "L2_" + dt + "HBMV_y_n1", fo);
        writeMatrixWithIncrements(dt, y, "L2_" + dt + "HBMV_y_n2", 2, fo);

        d = alpha * a * x + beta * y;
        writeMatrix(dt, d, "L2_" + dt + "HBMV_o_N", fo);
        writeMatrixWithIncrements(dt, d, "L2_" + dt + "HBMV_o_N2", 2, fo);


def L2_xSYMV(fo, alpha, beta, n):
    dataType = ['s', 'd'];
    for dt in dataType:
        a = matrixCreate(dt, n, n);
        symm(a);
        writeMatrix(dt, a, "L2_" + dt + "SYMV_A_nn", fo);
        writePackedMatrix(dt, a, "L2_" + dt + "SYMV_A_nn_pu", fo);

        x = matrixCreate(dt, n, 1);
        writeMatrix(dt, x, "L2_" + dt + "SYMV_x_n1", fo);
        writeMatrixWithIncrements(dt, x, "L2_" + dt + "SYMV_x_n2", 1, fo);

        y = matrixCreate(dt, n, 1);
        writeMatrix(dt, y, "L2_" + dt + "SYMV_y_n1", fo);
        writeMatrixWithIncrements(dt, y, "L2_" + dt + "SYMV_y_n2", 2, fo);

        d = alpha * a * x + beta * y;
        writeMatrix(dt, d, "L2_" + dt + "SYMV_o_N", fo);
        writeMatrixWithIncrements(dt, d, "L2_" + dt + "SYMV_o_N2", 2, fo);

def L2_xSBMV(fo, alpha, beta, n, k):
    dataType = ['s', 'd'];
    for dt in dataType:
        a = matrixCreate(dt, n, n);
        symm(a);
        writeSymmBandedMatrix(dt, a, k, "L2_" + dt + "SBMV_A_nn", fo);
        symm(a);

        x = matrixCreate(dt, n, 1);
        writeMatrix(dt, x, "L2_" + dt + "SBMV_x_n1", fo);
        writeMatrixWithIncrements(dt, x, "L2_" + dt + "SBMV_x_n2", 1, fo);

        y = matrixCreate(dt, n, 1);
        writeMatrix(dt, y, "L2_" + dt + "SBMV_y_n1", fo);
        writeMatrixWithIncrements(dt, y, "L2_" + dt + "SBMV_y_n2", 2, fo);

        d = alpha * a * x + beta * y;
        writeMatrix(dt, d, "L2_" + dt + "SBMV_o_N", fo);
        writeMatrixWithIncrements(dt, d, "L2_" + dt + "SBMV_o_N2", 2, fo);


def L2_xTRMV(fo, n):
    dataType = ['s', 'd', 'c', 'z'];
    for dt in dataType:
        a = matrixCreate(dt, n, n);
        triangularMatrixGen(a, 'u');
        writeMatrix(dt, a, "L2_" + dt + "TRMV_A_nn", fo);
        writePackedMatrix(dt, a, "L2_" + dt + "TRMV_A_nn_pu", fo);

        x = matrixCreate(dt, n, 1);
        writeMatrix(dt, x, "L2_" + dt + "TRMV_x_n1", fo);
        writeMatrixWithIncrements(dt, x, "L2_" + dt + "TRMV_x_n2", 1, fo);

        d = a * x;
        writeMatrix(dt, d, "L2_" + dt + "TRMV_o_UN", fo);
        writeMatrixWithIncrements(dt, d, "L2_" + dt + "TRMV_o_UN2", 1, fo);

        d = a.T * x;
        writeMatrix(dt, d, "L2_" + dt + "TRMV_o_UT", fo);

        d = a.H * x;
        writeMatrix(dt, d, "L2_" + dt + "TRMV_o_UH", fo);

def L2_xTBMV(fo, n, k):
    dataType = ['s', 'd', 'c', 'z'];
    for dt in dataType:
        a = matrixCreate(dt, n, n);
        writeSymmBandedMatrix(dt, a, k, "L2_" + dt + "TBMV_A_nn", fo);

        x = matrixCreate(dt, n, 1);
        writeMatrix(dt, x, "L2_" + dt + "TBMV_x_n1", fo);
        writeMatrixWithIncrements(dt, x, "L2_" + dt + "TBMV_x_n2", 1, fo);

        d = a * x;
        writeMatrix(dt, d, "L2_" + dt + "TBMV_o_UN", fo);
        writeMatrixWithIncrements(dt, d, "L2_" + dt + "TBMV_o_UN2", 1, fo);

        d = a.T * x;
        writeMatrix(dt, d, "L2_" + dt + "TBMV_o_UT", fo);

        d = a.H * x;
        writeMatrix(dt, d, "L2_" + dt + "TBMV_o_UH", fo);


def L2_xTRSV(fo, n):
    dataType = ['s', 'd', 'c', 'z'];
    for dt in dataType:
        a = matrixCreateScale(dt, n, n, 0.25);
        triangularMatrixGen(a, 'u');
        writeMatrix(dt, a, "L2_" + dt + "TRSV_A_nn", fo);
        writePackedMatrix(dt, a, "L2_" + dt + "TRSV_A_nn_pu", fo);

        x = matrixCreate(dt, n, 1);
        writeMatrix(dt, x, "L2_" + dt + "TRSV_x_n1", fo);
        writeMatrixWithIncrements(dt, x, "L2_" + dt + "TRSV_x_n2", 1, fo);

        d = a.I * x;
        writeMatrix(dt, d, "L2_" + dt + "TRSV_o_UN", fo);
        writeMatrixWithIncrements(dt, d, "L2_" + dt + "TRSV_o_UN2", 1, fo);

        d = a.I.T * x;
        writeMatrix(dt, d, "L2_" + dt + "TRSV_o_UT", fo);

        d = a.I.H * x;
        writeMatrix(dt, d, "L2_" + dt + "TRSV_o_UH", fo);

def L2_xTBSV(fo, n, k):
    dataType = ['s', 'd', 'c', 'z'];
    for dt in dataType:
        a = matrixCreateScale(dt, n, n, 0.25);
        writeSymmBandedMatrix(dt, a, k, "L2_" + dt + "TBSV_A_nn", fo);

        x = matrixCreate(dt, n, 1);
        writeMatrix(dt, x, "L2_" + dt + "TBSV_x_n1", fo);
        writeMatrixWithIncrements(dt, x, "L2_" + dt + "TBSV_x_n2", 1, fo);

        d = a.I * x;
        writeMatrix(dt, d, "L2_" + dt + "TBSV_o_UN", fo);
        writeMatrixWithIncrements(dt, d, "L2_" + dt + "TBSV_o_UN2", 1, fo);

        d = a.I.T * x;
        writeMatrix(dt, d, "L2_" + dt + "TBSV_o_UT", fo);

        d = a.I.H * x;
        writeMatrix(dt, d, "L2_" + dt + "TBSV_o_UH", fo);


def L2_xGER(fo, alpha, m, n):
    dataType = ['s', 'd'];
    for dt in dataType:
        a = matrixCreate(dt, m, n);
        writeMatrix(dt, a, "L2_" + dt + "GER_A_mn", fo);

        x = matrixCreate(dt, m, 1);
        writeMatrix(dt, x, "L2_" + dt + "GER_x_m1", fo);
        writeMatrixWithIncrements(dt, x, "L2_" + dt + "GER_x_m2", 1, fo);

        y = matrixCreate(dt, n, 1);
        writeMatrix(dt, y, "L2_" + dt + "GER_y_n1", fo);
        writeMatrixWithIncrements(dt, y, "L2_" + dt + "GER_y_n2", 2, fo);

        d = alpha * x * y.T + a;
        writeMatrix(dt, d, "L2_" + dt + "GER_o_N", fo);

def L2_xGERU(fo, alpha, m, n):
    dataType = ['c', 'z'];
    for dt in dataType:
        a = matrixCreate(dt, m, n);
        writeMatrix(dt, a, "L2_" + dt + "GERU_A_mn", fo);

        x = matrixCreate(dt, m, 1);
        writeMatrix(dt, x, "L2_" + dt + "GERU_x_m1", fo);
        writeMatrixWithIncrements(dt, x, "L2_" + dt + "GERU_x_m2", 1, fo);

        y = matrixCreate(dt, n, 1);
        writeMatrix(dt, y, "L2_" + dt + "GERU_y_n1", fo);
        writeMatrixWithIncrements(dt, y, "L2_" + dt + "GERU_y_n2", 2, fo);

        d = alpha * x * y.T + a;
        writeMatrix(dt, d, "L2_" + dt + "GERU_o_N", fo);

def L2_xGERC(fo, alpha, m, n):
    dataType = ['c', 'z'];
    for dt in dataType:
        a = matrixCreate(dt, m, n);
        writeMatrix(dt, a, "L2_" + dt + "GERC_A_mn", fo);

        x = matrixCreate(dt, m, 1);
        writeMatrix(dt, x, "L2_" + dt + "GERC_x_m1", fo);
        writeMatrixWithIncrements(dt, x, "L2_" + dt + "GERC_x_m2", 1, fo);

        y = matrixCreate(dt, n, 1);
        writeMatrix(dt, y, "L2_" + dt + "GERC_y_n1", fo);
        writeMatrixWithIncrements(dt, y, "L2_" + dt + "GERC_y_n2", 2, fo);

        d = alpha * x * y.H + a;
        writeMatrix(dt, d, "L2_" + dt + "GERC_o_N", fo);

def L2_xHER(fo, alpha, n):
    dataType = ['c', 'z'];
    for dt in dataType:
        a = matrixCreate(dt, n, n);
        herm(a);
        writeMatrix(dt, a, "L2_" + dt + "HER_A_nn", fo);
        writePackedMatrix(dt, a, "L2_" + dt + "HER_A_nn_pu", fo);

        x = matrixCreate(dt, n, 1);
        writeMatrix(dt, x, "L2_" + dt + "HER_x_n1", fo);
        writeMatrixWithIncrements(dt, x, "L2_" + dt + "HER_x_n2", 1, fo);

        d = alpha * x * x.H + a;
        writeMatrix(dt, d, "L2_" + dt + "HER_o_N", fo);
        writePackedMatrix(dt, d, "L2_" + dt + "HER_o_N_pu", fo);


def L2_xHER2(fo, alpha, n):
    dataType = ['c', 'z'];
    for dt in dataType:
        a = matrixCreate(dt, n, n);
        herm(a);
        writeMatrix(dt, a, "L2_" + dt + "HER2_A_nn", fo);
        writePackedMatrix(dt, a, "L2_" + dt + "HER2_A_nn_pu", fo);

        x = matrixCreate(dt, n, 1);
        writeMatrix(dt, x, "L2_" + dt + "HER2_x_n1", fo);
        writeMatrixWithIncrements(dt, x, "L2_" + dt + "HER2_x_n2", 1, fo);

        y = matrixCreate(dt, n, 1);
        writeMatrix(dt, y, "L2_" + dt + "HER2_y_n1", fo);
        writeMatrixWithIncrements(dt, y, "L2_" + dt + "HER2_y_n2", 2, fo);

        d = alpha * x * y.H + y * (alpha * x.H) + a;
        writeMatrix(dt, d, "L2_" + dt + "HER2_o_N", fo);
        writePackedMatrix(dt, d, "L2_" + dt + "HER2_o_N_pu", fo);

def L2_xSYR(fo, alpha, n):
    dataType = ['s', 'd'];
    for dt in dataType:
        a = matrixCreate(dt, n, n);
        writeMatrix(dt, a, "L2_" + dt + "SYR_A_nn", fo);
        writePackedMatrix(dt, a, "L2_" + dt + "SYR_A_nn_pu", fo);

        x = matrixCreate(dt, n, 1);
        writeMatrix(dt, x, "L2_" + dt + "SYR_x_n1", fo);
        writeMatrixWithIncrements(dt, x, "L2_" + dt + "SYR_x_n2", 1, fo);

        d = alpha * x * x.T + a;
        writeMatrix(dt, d, "L2_" + dt + "SYR_o_N", fo);
        writePackedMatrix(dt, d, "L2_" + dt + "SYR_o_N_pu", fo);

def L2_xSYR2(fo, alpha, n):
    dataType = ['s', 'd'];
    for dt in dataType:
        a = matrixCreate(dt, n, n);
        writeMatrix(dt, a, "L2_" + dt + "SYR2_A_nn", fo);
        writePackedMatrix(dt, a, "L2_" + dt + "SYR2_A_nn_pu", fo);

        x = matrixCreate(dt, n, 1);
        writeMatrix(dt, x, "L2_" + dt + "SYR2_x_n1", fo);
        writeMatrixWithIncrements(dt, x, "L2_" + dt + "SYR2_x_n2", 1, fo);

        y = matrixCreate(dt, n, 1);
        writeMatrix(dt, y, "L2_" + dt + "SYR2_y_n1", fo);
        writeMatrixWithIncrements(dt, y, "L2_" + dt + "SYR2_y_n2", 2, fo);

        d = alpha * x * y.T + y * (alpha * x.T) + a;
        writeMatrix(dt, d, "L2_" + dt + "SYR2_o_N", fo);
        writePackedMatrix(dt, d, "L2_" + dt + "SYR2_o_N_pu", fo);


def testBLASL2L3(fo):
    m = random.randint(10, 20);
    n = random.randint(10, 20);
    k = random.randint(10, 20);
    kl = random.randint(1, 5);
    ku = random.randint(1, 5);

    alpha = 1.0;
    beta = 1.0;

    fo.write("M, N, K, KL, KU" + ';\n');
    fo.write(str(m) + " " + str(n) + " " + str(k) + " " + str(kl) + " " + str(ku) + '\n');
    fo.write('\n');

    L2_xGEMV(fo, alpha, beta, m, n);
    L2_xGBMV(fo, alpha, beta, m, n, kl, ku);
    L2_xHEMV(fo, alpha, beta, n);
    L2_xHBMV(fo, alpha, beta, n, kl);
    L2_xSYMV(fo, alpha, beta, n);
    L2_xSBMV(fo, alpha, beta, n, kl);
    L2_xTRMV(fo, n);
    L2_xTBMV(fo, n, kl);
    L2_xTRSV(fo, n);
    L2_xTBSV(fo, n, kl);
    L2_xGER(fo, alpha, m, n);
    L2_xGERU(fo, alpha, m, n);
    L2_xGERC(fo, alpha, m, n);
    L2_xHER(fo, alpha, n);
    L2_xHER2(fo, alpha, n);
    L2_xSYR(fo, alpha, n);
    L2_xSYR2(fo, alpha, n);

    L3_xGEMM(fo, alpha, beta, m, n, k);
    L3_xSYMM(fo, alpha, beta, m, n);
    L3_xHEMM(fo, alpha, beta, m, n);
    L3_xSYRK(fo, alpha, beta, n, k);
    L3_xHERK(fo, alpha, beta, n, k);
    L3_xSYR2K(fo, alpha, beta, n, k);
    L3_xHER2K(fo, alpha, beta, n, k);
    L3_xTRMM(fo, alpha, m, n);
    L3_xTRSM(fo, alpha, m, n);

    return;

def javaDataGen():
    fo = open("BLASData.txt", "w+")
    fo.write("/* Don't edit this file!  It is auto-generated by blas_gen.py. */\n");
    fo.write("\n");

    #data body
    testBLASL2L3(fo);
    fo.close()
    return;

javaDataGen();

