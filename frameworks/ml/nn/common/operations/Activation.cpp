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

bool reluFloat32(const float* inputData, const Shape& inputShape,
                 float* outputData, const Shape& outputShape) {
    int numElements = getNumberOfElements(inputShape);
    for (int i=0; i<numElements; i++, inputData++, outputData++) {
        *outputData = std::max(0.f, *inputData);
    }
    return true;
}

bool relu1Float32(const float* inputData, const Shape& inputShape,
                  float* outputData, const Shape& outputShape) {
    int numElements = getNumberOfElements(inputShape);
    for (int i=0; i<numElements; i++, inputData++, outputData++) {
        *outputData = std::min(std::max(-1.f, *inputData), 1.f);
    }
    return true;
}

bool relu6Float32(const float* inputData, const Shape& inputShape,
                  float* outputData, const Shape& outputShape) {
    int numElements = getNumberOfElements(inputShape);
    for (int i=0; i<numElements; i++, inputData++, outputData++) {
        *outputData = std::min(std::max(0.f, *inputData), 6.f);
    }
    return true;
}

bool tanhFloat32(const float* inputData, const Shape& inputShape,
                 float* outputData, const Shape& outputShape) {
    int numElements = getNumberOfElements(inputShape);
    for (int i=0; i<numElements; i++, inputData++, outputData++) {
        *outputData = std::tanh(*inputData);
    }
    return true;
}

bool logisticFloat32(const float* inputData, const Shape& inputShape,
                     float* outputData, const Shape& outputShape) {
    int numElements = getNumberOfElements(inputShape);
    for (int i=0; i<numElements; i++, inputData++, outputData++) {
        *outputData = 1.f / (1.f + std::exp(-*inputData));
    }
    return true;
}

bool softmaxFloat32(const float* inputData, const Shape& inputShape,
                    const float beta,
                    float* outputData, const Shape& outputShape) {
    Dims<4> dim;
    if (getNumberOfDimensions(inputShape) == 2) {
        uint32_t batch_size = getSizeOfDimension(inputShape, 0);
        uint32_t input_size = getNumberOfElements(inputShape) / batch_size;

        Shape shapeIn4D;
        shapeIn4D.dimensions = {batch_size, 1, 1, input_size};
        dim = convertShapeToDims(shapeIn4D);
    } else if (getNumberOfDimensions(inputShape) == 4) {
        dim = convertShapeToDims(inputShape);
    } else {
        LOG(ERROR) << "only 2D and 4D tensors supported";
        return false;
    }

    optimized_ops::Softmax(inputData, dim, beta,
                           outputData, dim);
    return true;
}

#define ANDROID_NN_RELUX_QUANT8(activation)                             \
    int numElements = getNumberOfElements(inputShape);                  \
    int32_t output_activation_min = 0;                                  \
    int32_t output_activation_max = 0;                                  \
                                                                        \
    CalculateActivationRangeUint8(activation, inputShape,               \
                                  &output_activation_min,               \
                                  &output_activation_max);              \
                                                                        \
    for (int i=0; i<numElements; i++, inputData++, outputData++) {      \
        *outputData = std::min((uint8_t)output_activation_max,          \
                std::max((uint8_t)output_activation_min, *inputData));  \
    }


bool reluQuant8(const uint8_t* inputData, const Shape& inputShape,
                uint8_t* outputData, const Shape& outputShape) {
    ANDROID_NN_RELUX_QUANT8(kActivationRelu)
    return true;
}

bool relu1Quant8(const uint8_t* inputData, const Shape& inputShape,
                 uint8_t* outputData, const Shape& outputShape) {
    ANDROID_NN_RELUX_QUANT8(kActivationRelu1)
    return true;
}

bool relu6Quant8(const uint8_t* inputData, const Shape& inputShape,
                 uint8_t* outputData, const Shape& outputShape) {
    ANDROID_NN_RELUX_QUANT8(kActivationRelu6)
    return true;
}

#undef ANDROID_NN_RELUX_QUANT8

bool logisticQuant8(const uint8_t* inputData, const Shape& inputShape,
                    uint8_t* outputData, const Shape& outputShape) {
    if (outputShape.offset != 0 || outputShape.scale != 1.f / 256) {
        LOG(ERROR) << "incorrect scale / offset for output";
        return false;
    }

    int numElements = getNumberOfElements(inputShape);
    static constexpr int kInputIntegerBits = 4;

    const double input_real_multiplier =
            inputShape.scale *
            static_cast<double>(1 << (31 - kInputIntegerBits));

    int32_t input_multiplier = 0;
    int32_t input_left_shift = 0;
    if (!QuantizeMultiplierGreaterThanOne(input_real_multiplier,
                                          &input_multiplier,
                                          &input_left_shift)) {
        return false;
    }
    int32_t input_range_radius =
            CalculateInputRadius(kInputIntegerBits, input_left_shift);

    optimized_ops::Logistic(
            inputData, convertShapeToDims(inputShape),
            inputShape.offset, input_range_radius,
            input_multiplier, input_left_shift,
            outputData, convertShapeToDims(outputShape));

    return true;
}

bool softmaxQuant8(const uint8_t* inputData, const Shape& inputShape,
                   const float beta,
                   uint8_t* outputData, const Shape& outputShape) {
    Dims<4> dim;
    if (getNumberOfDimensions(inputShape) == 2) {
        uint32_t batch_size = getSizeOfDimension(inputShape, 0);
        uint32_t input_size = getNumberOfElements(inputShape) / batch_size;

        Shape shapeIn4D;
        shapeIn4D.dimensions = {batch_size, 1, 1, input_size};
        dim = convertShapeToDims(shapeIn4D);
    } else if (getNumberOfDimensions(inputShape) == 4) {
        dim = convertShapeToDims(inputShape);
    } else {
        LOG(ERROR) << "only 2D and 4D tensors supported";
        return false;
    }

    if (outputShape.offset != 0 || outputShape.scale != 1.f / 256) {
        LOG(ERROR) << "incorrect scale / offset for output";
        return false;
    }

    static const int32_t kScaledDiffIntegerBits = 5;
    const double input_beta_real_multiplier = std::min(
            1.0 * beta * inputShape.scale * (1 << (31 - kScaledDiffIntegerBits)),
            (1ll << 31) - 1.0);

    int32_t input_multiplier = 0;
    int32_t input_left_shift = 0;
    if (!QuantizeMultiplierGreaterThanOne(input_beta_real_multiplier,
                                          &input_multiplier,
                                          &input_left_shift)) {
        return false;
    }
    float diff_min = -1.0f * CalculateInputRadius(kScaledDiffIntegerBits,
                                                  input_left_shift);

    optimized_ops::Softmax(inputData, dim, input_multiplier,
                           input_left_shift, diff_min,
                           outputData, dim);
    return true;
}


}  // namespace nn
}  // namespace android
