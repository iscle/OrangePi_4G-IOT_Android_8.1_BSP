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

// If possible we will use this static buffer for the tensor.
static constexpr int kStaticBufferSize = 1605632;
static char static_scratch_buffer[kStaticBufferSize];

#define ANDROID_NN_CONV_PARAMETERS(Type)                                        \
    uint32_t height       = getSizeOfDimension(inputShape, 1);                  \
    uint32_t width        = getSizeOfDimension(inputShape, 2);                  \
    uint32_t filterHeight = getSizeOfDimension(filterShape, 1);                 \
    uint32_t filterWidth  = getSizeOfDimension(filterShape, 2);                 \
    uint32_t outHeight    = getSizeOfDimension(outputShape, 1);                 \
    uint32_t outWidth     = getSizeOfDimension(outputShape, 2);                 \
    uint32_t inDepth      = getSizeOfDimension(inputShape, 3);                  \
                                                                                \
    uint32_t paddingHeight = (uint32_t)padding_top;                             \
    uint32_t paddingWidth = (uint32_t)padding_left;                             \
                                                                                \
    Dims<4> im2colDim;                                                          \
    im2colDim.sizes[3] = (int)getSizeOfDimension(outputShape, 0);               \
    im2colDim.sizes[2] = (int)getSizeOfDimension(outputShape, 1);               \
    im2colDim.sizes[1] = (int)getSizeOfDimension(outputShape, 2);               \
    im2colDim.sizes[0] = (int)inDepth * filterHeight * filterWidth;             \
                                                                                \
    im2colDim.strides[0] = 1;                                                   \
    for (int i=1; i<4; i++) {                                                   \
        im2colDim.strides[i] = im2colDim.strides[i-1] * im2colDim.sizes[i-1];   \
    }                                                                           \
                                                                                \
    Type* im2colData = nullptr;                                                 \
    int im2colByteSize = sizeof(Type);                                          \
    for (int i=0; i<4; i++) {                                                   \
        im2colByteSize *= im2colDim.sizes[i];                                   \
    }                                                                           \
    if (im2colByteSize <= kStaticBufferSize) {                                  \
        im2colData = reinterpret_cast<Type *>(static_scratch_buffer);           \
    } else {                                                                    \
        im2colData = new (std::nothrow) Type[im2colByteSize / sizeof(Type)];    \
    }

bool convFloat32(const float* inputData, const Shape& inputShape,
                 const float* filterData, const Shape& filterShape,
                 const float* biasData, const Shape& biasShape,
                 int32_t padding_left, int32_t padding_right,
                 int32_t padding_top, int32_t padding_bottom,
                 int32_t stride_width, int32_t stride_height,
                 int32_t activation,
                 float* outputData, const Shape& outputShape) {

    ANDROID_NN_CONV_PARAMETERS(float)

    #define ANDROID_NN_CONV(activation)                                        \
        optimized_ops::Conv<FusedActivationFunctionType::activation>(          \
            inputData, convertShapeToDims(inputShape),                         \
            filterData, convertShapeToDims(filterShape),                       \
            biasData, convertShapeToDims(biasShape),                           \
            stride_width, stride_height, paddingWidth, paddingHeight,          \
            outputData, convertShapeToDims(outputShape),                       \
            im2colData, im2colDim)

    ANDROID_NN_MACRO_DISPATCH(ANDROID_NN_CONV)
    #undef ANDROID_NN_CONV

    if (im2colByteSize > kStaticBufferSize) {
        delete[] im2colData;
    }
    return true;
}

bool convQuant8(const uint8_t* inputData, const Shape& inputShape,
                const uint8_t* filterData, const Shape& filterShape,
                const int32_t* biasData, const Shape& biasShape,
                int32_t padding_left, int32_t padding_right,
                int32_t padding_top, int32_t padding_bottom,
                int32_t stride_width, int32_t stride_height,
                int32_t activation,
                uint8_t* outputData, const Shape& outputShape) {

    ANDROID_NN_CONV_PARAMETERS(uint8_t)

    int32_t inputOffset = -inputShape.offset;
    int32_t filterOffset = -filterShape.offset;
    int32_t outputOffset = outputShape.offset;

    float real_multiplier = 0.0;
    int32_t output_multiplier = 0;
    int32_t output_shift = 0;
    int32_t output_activation_min = 0;
    int32_t output_activation_max = 0;

    if (!GetQuantizedConvolutionMultipler(inputShape, filterShape, biasShape,
                                          outputShape, &real_multiplier) ||
            !QuantizeMultiplierSmallerThanOne(real_multiplier, &output_multiplier,
                                              &output_shift)){
        return false;
    }
    CalculateActivationRangeUint8(activation, outputShape,
                                  &output_activation_min,
                                  &output_activation_max);

    static gemmlowp::GemmContext gemm_context;
    // Alow gemmlowp automatcally decide how many threads to use.
    gemm_context.set_max_num_threads(0);

    #define ANDROID_NN_CONV(activation)                                        \
        optimized_ops::Conv<FusedActivationFunctionType::activation>(          \
            inputData, convertShapeToDims(inputShape), inputOffset,            \
            filterData, convertShapeToDims(filterShape), filterOffset,         \
            biasData, convertShapeToDims(biasShape),                           \
            stride_width, stride_height, paddingWidth, paddingHeight,          \
            outputOffset, output_multiplier, output_shift,                     \
            output_activation_min, output_activation_max,                      \
            outputData, convertShapeToDims(outputShape),                       \
            im2colData, im2colDim, &gemm_context)

    ANDROID_NN_MACRO_DISPATCH(ANDROID_NN_CONV)
    #undef ANDROID_NN_CONV

    if (im2colByteSize > kStaticBufferSize) {
        delete[] im2colData;
    }
    return true;
}

#undef ANDROID_NN_CONV_PARAMETERS
}  // namespace nn
}  // namespace android
