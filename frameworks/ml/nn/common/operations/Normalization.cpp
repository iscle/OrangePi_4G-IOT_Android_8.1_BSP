/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "Operations.h"
#include "OperationsUtils.h"

#include "internal/optimized/optimized_ops.h"

namespace android {
namespace nn {

bool l2normFloat32(const float* inputData, const Shape& inputShape,
                   float* outputData, const Shape& outputShape) {
    optimized_ops::L2Normalization<FusedActivationFunctionType::kNone>(
            inputData, convertShapeToDims(inputShape),
            outputData, convertShapeToDims(outputShape));

    return true;
}

bool l2normQuant8(const uint8_t* inputData, const Shape& inputShape,
                  uint8_t* outputData, const Shape& outputShape) {
    optimized_ops::L2Normalization(
            inputData, convertShapeToDims(inputShape),
            inputShape.offset,
            outputData, convertShapeToDims(outputShape));

    return true;
}

bool localResponseNormFloat32(const float* inputData, const Shape& inputShape,
                              int32_t radius, float bias, float alpha, float beta,
                              float* outputData, const Shape& outputShape) {
    optimized_ops::LocalResponseNormalization(
            inputData, convertShapeToDims(inputShape),
            radius, bias, alpha, beta,
            outputData, convertShapeToDims(outputShape));

    return true;
}
}  // namespace nn
}  // namespace android
