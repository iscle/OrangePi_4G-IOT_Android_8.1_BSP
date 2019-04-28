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

bool fullyConnectedFloat32(const float* inputData, const Shape& inputShape,
                           const float* weightsData, const Shape& weightsShape,
                           const float* biasData, const Shape& biasShape,
                           int32_t activation,
                           float* outputData, const Shape& outputShape) {

    #define ANDROID_NN_FULLY_CONNECTED(activation)                              \
        optimized_ops::FullyConnected<FusedActivationFunctionType::activation>( \
            inputData, convertShapeToDims(inputShape),                          \
            weightsData, convertShapeToDims(weightsShape),                      \
            biasData, convertShapeToDims(biasShape),                            \
            outputData, convertShapeToDims(outputShape))

    ANDROID_NN_MACRO_DISPATCH(ANDROID_NN_FULLY_CONNECTED)
    #undef ANDROID_NN_FULLY_CONNECTED
    return true;
}

bool fullyConnectedQuant8(const uint8_t* inputData, const Shape& inputShape,
                          const uint8_t* weightsData, const Shape& weightsShape,
                          const int32_t* biasData, const Shape& biasShape,
                          int32_t activation,
                          uint8_t* outputData, const Shape& outputShape) {
    int32_t inputOffset = -inputShape.offset;
    int32_t weightsOffset = -weightsShape.offset;
    int32_t outputOffset = outputShape.offset;

    float real_multiplier = 0.0;
    int32_t output_multiplier = 0;
    int32_t output_shift = 0;
    int32_t output_activation_min = 0;
    int32_t output_activation_max = 0;

    if (!GetQuantizedConvolutionMultipler(inputShape, weightsShape, biasShape,
                                          outputShape, &real_multiplier) ||
            !QuantizeMultiplierSmallerThanOne(real_multiplier, &output_multiplier,
                                              &output_shift)) {
        return false;
    }
    CalculateActivationRangeUint8(activation, outputShape,
                                  &output_activation_min,
                                  &output_activation_max);

    static gemmlowp::GemmContext gemm_context;
    // Alow gemmlowp automatcally decide how many threads to use.
    gemm_context.set_max_num_threads(0);

    #define ANDROID_NN_FULLY_CONNECTED(activation)                              \
        optimized_ops::FullyConnected<FusedActivationFunctionType::activation>( \
            inputData, convertShapeToDims(inputShape), inputOffset,             \
            weightsData, convertShapeToDims(weightsShape), weightsOffset,       \
            biasData, convertShapeToDims(biasShape),                            \
            outputOffset, output_multiplier, output_shift,                      \
            output_activation_min, output_activation_max,                       \
            outputData, convertShapeToDims(outputShape), &gemm_context)

    ANDROID_NN_MACRO_DISPATCH(ANDROID_NN_FULLY_CONNECTED)
    #undef ANDROID_NN_FULLY_CONNECTED
    return true;
}
}  // namespace nn
}  // namespace android
