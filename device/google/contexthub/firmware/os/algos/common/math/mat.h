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
/////////////////////////////////////////////////////////////////////////
/*
 * This module contains matrix math utilities for the following datatypes:
 * -) Mat33 structures for 3x3 dimensional matrices
 * -) Mat44 structures for 4x4 dimensional matrices
 * -) floating point arrays for NxM dimensional matrices.
 *
 * Note that the Mat33 and Mat44 utilities were ported from the Android
 * repository and maintain dependencies in that separate codebase. As a
 * result, the function signatures were left untouched for compatibility with
 * this legacy code, despite certain style violations. In particular, for this
 * module the function argument ordering is outputs before inputs. This style
 * violation will be addressed once the full set of dependencies in Android
 * have been brought into this repository.
 */
#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_COMMON_MATH_MAT_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_COMMON_MATH_MAT_H_

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "common/math/vec.h"

#ifdef __cplusplus
extern "C" {
#endif

struct Mat33 {
  float elem[3][3];
};

struct Size3 {
  uint32_t elem[3];
};

struct Mat44 {
  float elem[4][4];
};

struct Size4 {
  uint32_t elem[4];
};

// 3x3 MATRIX MATH /////////////////////////////////////////////////////////////
void initZeroMatrix(struct Mat33 *A);

// Updates A with the value x on the main diagonal and 0 on the off diagonals,
// i.e.:
// A = [x 0 0
//      0 x 0
//      0 0 x]
void initDiagonalMatrix(struct Mat33 *A, float x);

// Updates A such that the columns are given by the provided vectors, i.e.:
// A = [v1 v2 v3].
void initMatrixColumns(struct Mat33 *A, const struct Vec3 *v1,
                       const struct Vec3 *v2, const struct Vec3 *v3);

// Updates out with the multiplication of A with v, i.e.:
// out = A v.
void mat33Apply(struct Vec3 *out, const struct Mat33 *A, const struct Vec3 *v);

// Updates out with the multiplication of A with B, i.e.:
// out =  A B.
void mat33Multiply(struct Mat33 *out, const struct Mat33 *A,
                   const struct Mat33 *B);

// Updates A by scaling all entries by the provided scalar c, i.e.:
// A = A c.
void mat33ScalarMul(struct Mat33 *A, float c);

// Updates out by adding A to out, i.e.:
// out = out + A.
void mat33Add(struct Mat33 *out, const struct Mat33 *A);

// Updates out by subtracting A from out, i.e.:
// out = out - A.
void mat33Sub(struct Mat33 *out, const struct Mat33 *A);

// Returns 1 if the minimum eigenvalue of the matrix A is greater than the
// given tolerance. Note that the tolerance is assumed to be greater than 0.
// I.e., returns: 1[min(eig(A)) > tolerance].
// NOTE: this function currently only checks matrix symmetry and positivity
// of the diagonals which is insufficient for testing positive semidefinite.
int mat33IsPositiveSemidefinite(const struct Mat33 *A, float tolerance);

// Updates out with the inverse of the matrix A, i.e.:
// out = A^(-1)
void mat33Invert(struct Mat33 *out, const struct Mat33 *A);

// Updates out with the multiplication of A's transpose with B, i.e.:
// out = A^T B
void mat33MultiplyTransposed(struct Mat33 *out, const struct Mat33 *A,
                             const struct Mat33 *B);

// Updates out with the multiplication of A with B's transpose, i.e.:
// out = A B^T
void mat33MultiplyTransposed2(struct Mat33 *out, const struct Mat33 *A,
                              const struct Mat33 *B);

// Updates out with the transpose of A, i.e.:
// out = A^T
void mat33Transpose(struct Mat33 *out, const struct Mat33 *A);

// Returns the eigenvalues and corresponding eigenvectors of the symmetric
// matrix S.
// The i-th eigenvalue corresponds to the eigenvector in the i-th row of
// the matrix eigenvecs.
void mat33GetEigenbasis(struct Mat33 *S, struct Vec3 *eigenvals,
                        struct Mat33 *eigenvecs);


// 4x4 MATRIX MATH /////////////////////////////////////////////////////////////
// Updates out with the multiplication of A and v, i.e.:
// out = Av.
void mat44Apply(struct Vec4 *out, const struct Mat44 *A, const struct Vec4 *v);

// Decomposes the given matrix LU inplace, such that:
// LU = P' * L * U.
// where L is a lower-diagonal matrix, U is an upper-diagonal matrix, and P is a
// permutation matrix.
//
// L and U are stored compactly in the returned LU matrix such that:
// -) the superdiagonal elements make up "U" (with a diagonal of 1.0s),
// -) the subdiagonal and diagonal elements make up "L".
// e.g. if the returned LU matrix is:
//      LU = [A11 A12 A13 A14
//            A21 A22 A23 A24
//            A31 A32 A33 A34
//            A41 A42 A43 A44], then:
//       L = [A11  0   0   0      and   U = [ 1  A12 A13 A14
//            A21 A22  0   0                  0   1  A23 A24
//            A31 A32 A33  0                  0   0   1  A34
//            A41 A42 A43 A44]                0   0   0   1 ]
//
// The permutation matrix P can be reproduced from returned pivot vector as:
// matrix P(N);
// P.identity();
// for (size_t i = 0; i < N; ++i) {
//    P.swapRows(i, pivot[i]);
// }
void mat44DecomposeLup(struct Mat44 *LU, struct Size4 *pivot);

// Solves the linear system A x = b for x, where A is a compact LU decomposition
// (i.e. the LU matrix from mat44DecomposeLup) and pivot is the corresponding
// row pivots for the permutation matrix (also from mat44DecomposeLup).
void mat44Solve(const struct Mat44 *A, struct Vec4 *x, const struct Vec4 *b,
                const struct Size4 *pivot);

// MXN MATRIX MATH /////////////////////////////////////////////////////////////
/*
 * The following functions define basic math functionality for matrices of
 * arbitrary dimension.
 *
 * All matrices used in these functions are assumed to be row major, i.e. if:
 * A = [1 2 3
 *      4 5 6
 *      7 8 9]
 * then when A is passed into one of the functions below, the order of
 * elements is assumed to be [1 2 3 4 5 6 7 8 9].
 */

// Returns the maximum diagonal element of the given matrix.
// The matrix is assumed to be square, of size n x n.
float matMaxDiagonalElement(const float *square_mat, size_t n);

// Adds a constant value to the diagonal of the given square n x n matrix and
// returns the updated matrix in place:
// A = A + uI
void matAddConstantDiagonal(float *square_mat, float u, size_t n);

// Updates out with the result of A's transpose multiplied with A (i.e. A^T A).
// A is a matrix with dimensions nrows x ncols.
// out is a matrix with dimensions ncols x ncols.
void matTransposeMultiplyMat(float *out, const float *A,
                             size_t nrows, size_t ncols);

// Updates out with the result of A's transpose multiplied with v (i.e. A^T v).
// A is a matrix with dimensions nrows x ncols.
// v is a vector of dimension nrows.
// out is a vector of dimension ncols.
void matTransposeMultiplyVec(float* out, const float *A, const float *v,
                             size_t nrows, size_t ncols);

// Updates out with the result of A multiplied with v (i.e. out = Av).
// A is a matrix with dimensions nrows x ncols.
// v is a vector of dimension ncols.
// out is a vector of dimension nrows.
void matMultiplyVec(float *out, const float *A, const float *v,
                    size_t nrows, size_t ncols);

// Solves the linear system L L^T x = b for x, where L is a lower diagonal,
// symmetric matrix, i.e. the Cholesky factor of a matrix A = L L^T.
// L is a lower-diagonal matrix of dimension n x n.
// b is a vector of dimension n.
// x is a vector of dimension n.
// Returns true if the solver succeeds.
bool matLinearSolveCholesky(float *x, const float *L, const float *b,
                            size_t n);

// Performs the Cholesky decomposition on the given matrix A such that:
// A = L L^T, where L, the Cholesky factor, is a lower diagonal matrix.
// Updates the provided L matrix with the Cholesky factor.
// This decomposition is only successful for symmetric, positive definite
// matrices A.
// Returns true if the solver succeeds (will fail if the matrix is not
// symmetric, positive definite).
bool matCholeskyDecomposition(float *L, const float *A, size_t n);

#ifdef __cplusplus
}
#endif

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_COMMON_MATH_MAT_H_
