/*
 * Copyright 2017 The Android Open Source Project
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

#ifndef FRAMEWORKS_ML_NN_COMMON_OPERATIONS_INTERNAL_OPTIMIZED_NEON_TENSOR_UTILS_H_
#define FRAMEWORKS_ML_NN_COMMON_OPERATIONS_INTERNAL_OPTIMIZED_NEON_TENSOR_UTILS_H_

#include "ActivationFunctor.h"
#include "cpu_check.h"
#include "tensor_utils_impl.h"

namespace android {
namespace nn {
namespace tensor_utils {

void MatrixBatchVectorMultiplyAccumulate(const float* matrix, int m_rows,
                                         int m_cols, const float* vector,
                                         int n_batch, float* result,
                                         int result_stride) {
  NEON_OR_PORTABLE(MatrixBatchVectorMultiplyAccumulate, matrix, m_rows, m_cols,
                   vector, n_batch, result, result_stride);
}

void VectorVectorCwiseProduct(const float* vector1, const float* vector2,
                              int v_size, float* result) {
  NEON_OR_PORTABLE(VectorVectorCwiseProduct, vector1, vector2, v_size, result);
}

void VectorVectorCwiseProductAccumulate(const float* vector1,
                                        const float* vector2, int v_size,
                                        float* result) {
  NEON_OR_PORTABLE(VectorVectorCwiseProductAccumulate, vector1, vector2, v_size,
                   result);
}

void VectorBatchVectorCwiseProductAccumulate(const float* vector, int v_size,
                                             const float* batch_vector,
                                             int n_batch, float* result) {
  NEON_OR_PORTABLE(VectorBatchVectorCwiseProductAccumulate, vector, v_size,
                   batch_vector, n_batch, result);
}

float VectorVectorDotProduct(const float* vector1, const float* vector2,
                             int v_size) {
  return PortableVectorVectorDotProduct(vector1, vector2, v_size);
}

void BatchVectorBatchVectorDotProduct(const float* vector1,
                                      const float* vector2, int v_size,
                                      int n_batch, float* result,
                                      int result_stride) {
  PortableBatchVectorBatchVectorDotProduct(vector1, vector2, v_size, n_batch,
                                           result, result_stride);
}

void VectorBatchVectorAssign(const float* vector, int v_size, int n_batch,
                             float* batch_vector) {
  PortableVectorBatchVectorAssign(vector, v_size, n_batch, batch_vector);
}

void ApplySigmoidToVector(const float* vector, int v_size, float* result) {
  PortableApplySigmoidToVector(vector, v_size, result);
}

void ApplyActivationToVector(const float* vector, int v_size,
                             ActivationFn activation, float* result) {
  PortableApplyActivationToVector(vector, v_size, activation, result);
}

void CopyVector(const float* vector, int v_size, float* result) {
  PortableCopyVector(vector, v_size, result);
}

void Sub1Vector(const float* vector, int v_size, float* result) {
  NEON_OR_PORTABLE(Sub1Vector, vector, v_size, result);
}

void ZeroVector(float* vector, int v_size) {
  PortableZeroVector(vector, v_size);
}

float Clip(float f, float abs_limit) { return PortableClip(f, abs_limit); }

void ClipVector(const float* vector, int v_size, float abs_limit,
                float* result) {
  NEON_OR_PORTABLE(ClipVector, vector, v_size, abs_limit, result);
}

// TODO(ghodrat): Implement Neon version.
void VectorShiftLeft(float* vector, int v_size, float shift_value) {
  PortableVectorShiftLeft(vector, v_size, shift_value);
}

// TODO(ghodrat): Implement Neon version.
void ReductionSumVector(const float* input_vector, int input_stride,
                        float* output_vector, int output_size,
                        int reduction_size) {
  PortableReductionSumVector(input_vector, input_stride, output_vector,
                             output_size, reduction_size);
}

}  // namespace tensor_utils
}  // namespace nn
}  // namespace android

#endif  // FRAMEWORKS_ML_NN_COMMON_OPERATIONS_INTERNAL_OPTIMIZED_NEON_TENSOR_UTILS_H_
