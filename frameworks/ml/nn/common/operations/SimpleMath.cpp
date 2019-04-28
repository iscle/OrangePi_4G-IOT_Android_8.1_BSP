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

// Contains the implementation of the operations.

#define LOG_TAG "Operations"

#include "Operations.h"
#include "OperationsUtils.h"

#include "internal/optimized/optimized_ops.h"

namespace android {
namespace nn {
bool addFloat32(const float* in1, const Shape& shape1,
                const float* in2, const Shape& shape2,
                int32_t activation,
                float* out, const Shape& shapeOut) {
    bool needBroadcast = !SameShape(shape1, shape2);

    #define ANDROID_NN_NORMAL_ADD(activation)                        \
        optimized_ops::Add<FusedActivationFunctionType::activation>( \
                in1, convertShapeToDims(shape1),                     \
                in2, convertShapeToDims(shape2),                     \
                out, convertShapeToDims(shapeOut))

    #define ANDROID_NN_BROADCAST_ADD(activation)                              \
        optimized_ops::BroadcastAdd<FusedActivationFunctionType::activation>( \
                in1, convertShapeToDims(shape1),                              \
                in2, convertShapeToDims(shape2),                              \
                out, convertShapeToDims(shapeOut))

    if (needBroadcast) {
        ANDROID_NN_MACRO_DISPATCH(ANDROID_NN_BROADCAST_ADD)
    } else {
        ANDROID_NN_MACRO_DISPATCH(ANDROID_NN_NORMAL_ADD)
    }

    #undef ANDROID_NN_NORMAL_ADD
    #undef ANDROID_NN_BROADCAST_ADD
    return true;
}

bool addQuant8(const uint8_t* in1, const Shape& shape1,
               const uint8_t* in2, const Shape& shape2,
               int32_t activation,
               uint8_t* out, const Shape& shapeOut) {
    bool needBroadcast = !SameShape(shape1, shape2);

    const int32_t input1_offset = -shape1.offset;
    const int32_t input2_offset = -shape2.offset;
    const int32_t output_offset = shapeOut.offset;
    const int left_shift = 20;
    const double twice_max_input_scale = 2 * std::max(shape1.scale, shape2.scale);
    const double real_input1_multiplier = shape1.scale / twice_max_input_scale;
    const double real_input2_multiplier = shape2.scale / twice_max_input_scale;
    const double real_output_multiplier =
            twice_max_input_scale /
            ((1 << left_shift) * shapeOut.scale);

    int32_t input1_multiplier;
    int32_t input1_shift;
    if (!QuantizeMultiplierSmallerThanOne(real_input1_multiplier,
                                          &input1_multiplier, &input1_shift)) {
        return false;
    }
    int32_t input2_multiplier;
    int32_t input2_shift;
    if (!QuantizeMultiplierSmallerThanOne(real_input2_multiplier,
                                          &input2_multiplier, &input2_shift)) {
        return false;
    }
    int32_t output_multiplier;
    int32_t output_shift;
    if (!QuantizeMultiplierSmallerThanOne(real_output_multiplier,
                                          &output_multiplier, &output_shift)) {
        return false;
    }
    int32_t output_activation_min;
    int32_t output_activation_max;
    CalculateActivationRangeUint8(activation, shapeOut,
                                  &output_activation_min,
                                  &output_activation_max);

    #define ANDROID_NN_NORMAL_ADD(activation)                           \
        optimized_ops::Add<FusedActivationFunctionType::activation>(    \
                left_shift,                                             \
                in1, convertShapeToDims(shape1),                        \
                input1_offset, input1_multiplier, input1_shift,         \
                in2, convertShapeToDims(shape2),                        \
                input2_offset, input2_multiplier, input2_shift,         \
                output_offset, output_multiplier, output_shift,         \
                output_activation_min, output_activation_max,           \
                out, convertShapeToDims(shapeOut))

    #define ANDROID_NN_BROADCAST_ADD(activation)                                 \
        optimized_ops::BroadcastAdd<FusedActivationFunctionType::activation>(    \
                left_shift,                                                      \
                in1, convertShapeToDims(shape1),                                 \
                input1_offset, input1_multiplier, input1_shift,                  \
                in2, convertShapeToDims(shape2),                                 \
                input2_offset, input2_multiplier, input2_shift,                  \
                output_offset, output_multiplier, output_shift,                  \
                output_activation_min, output_activation_max,                    \
                out, convertShapeToDims(shapeOut))

    if (needBroadcast) {
        ANDROID_NN_MACRO_DISPATCH(ANDROID_NN_BROADCAST_ADD)
    } else {
        ANDROID_NN_MACRO_DISPATCH(ANDROID_NN_NORMAL_ADD)
    }

    #undef ANDROID_NN_NORMAL_ADD
    #undef ANDROID_NN_BROADCAST_ADD
    return true;
}

bool mulFloat32(const float* in1, const Shape& shape1,
                const float* in2, const Shape& shape2,
                int32_t activation,
                float* out, const Shape& shapeOut) {
    bool needBroadcast = !SameShape(shape1, shape2);

    #define ANDROID_NN_NORMAL_MUL(activation)                        \
        optimized_ops::Mul<FusedActivationFunctionType::activation>( \
                in1, convertShapeToDims(shape1),                     \
                in2, convertShapeToDims(shape2),                     \
                out, convertShapeToDims(shapeOut))

    #define ANDROID_NN_BROADCAST_MUL(activation)                              \
        optimized_ops::BroadcastMul<FusedActivationFunctionType::activation>( \
                in1, convertShapeToDims(shape1),                              \
                in2, convertShapeToDims(shape2),                              \
                out, convertShapeToDims(shapeOut))

    if (needBroadcast) {
        ANDROID_NN_MACRO_DISPATCH(ANDROID_NN_BROADCAST_MUL)
    } else {
        ANDROID_NN_MACRO_DISPATCH(ANDROID_NN_NORMAL_MUL)
    }

    #undef ANDROID_NN_NORMAL_MUL
    #undef ANDROID_NN_BROADCAST_MUL
    return true;
}

bool mulQuant8(const uint8_t* in1, const Shape& shape1,
               const uint8_t* in2, const Shape& shape2,
               int32_t activation,
               uint8_t* out, const Shape& shapeOut) {
    const int32_t input1_offset = -shape1.offset;
    const int32_t input2_offset = -shape2.offset;
    const int32_t output_offset = shapeOut.offset;
    const double input_product_scale = shape1.scale * shape2.scale;
    const double real_multiplier = input_product_scale / shapeOut.scale;
    int32 output_multiplier;
    int output_shift;
    if (!QuantizeMultiplierSmallerThanOne(real_multiplier, &output_multiplier,
                                          &output_shift)) {
        return false;
    }
    int32_t output_activation_min;
    int32_t output_activation_max;
    CalculateActivationRangeUint8(activation, shapeOut,
                                  &output_activation_min,
                                  &output_activation_max);

    // Use BROADCAST version to handle the normal case until we have a optimized Mul.
    #define ANDROID_NN_BROADCAST_MUL(activation)                                 \
        optimized_ops::BroadcastMul<FusedActivationFunctionType::activation>(    \
                in1, convertShapeToDims(shape1), input1_offset,                  \
                in2, convertShapeToDims(shape2), input2_offset,                  \
                output_offset, output_multiplier, output_shift,                  \
                output_activation_min, output_activation_max,                    \
                out, convertShapeToDims(shapeOut))

    ANDROID_NN_MACRO_DISPATCH(ANDROID_NN_BROADCAST_MUL)

    #undef ANDROID_NN_NORMAL_MUL
    #undef ANDROID_NN_BROADCAST_MUL
    return true;
}

bool floorFloat32(const float* inputData,
                  float* outputData,
                  const Shape& shape) {
    Dims<4> dim = convertShapeToDims(shape);
    optimized_ops::Floor(inputData, dim, outputData, dim);
    return true;
}

bool dequantizeQuant8ToFloat32(const uint8_t* inputData,
                               float* outputData,
                               const Shape& shape) {
    Dims<4> dim = convertShapeToDims(shape);
    optimized_ops::Dequantize(inputData, dim,
                              shape.offset, shape.scale,
                              outputData, dim);
    return true;
}

} // namespace nn
} // namespace android
