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

bool reshapeGeneric(const void* inputData, const Shape& inputShape,
                    void* outputData, const Shape& outputShape) {
    size_t count = sizeOfData(inputShape.type, inputShape.dimensions);
    memcpy(outputData, inputData, count);
    return true;
}

bool resizeBilinearFloat32(const float* inputData, const Shape& inputShape,
                           float* outputData, const Shape& outputShape) {
    int32_t height = (int32_t) getSizeOfDimension(outputShape, 1);
    int32_t width  = (int32_t) getSizeOfDimension(outputShape, 2);

    int32_t outDimData[2] = {height, width};
    // We have to fake a tensor here, to satisfy ResizeBilinear().
    Shape outDimShape;
    outDimShape.dimensions = {1, 1, 1, 2};

    optimized_ops::ResizeBilinear(
            inputData, convertShapeToDims(inputShape),
            outDimData, convertShapeToDims(outDimShape),
            outputData, convertShapeToDims(outputShape));
    return true;
}

bool depthToSpaceGeneric(const uint8_t* inputData, const Shape& inputShape,
                         int32_t blockSize,
                         uint8_t* outputData, const Shape& outputShape) {
    if (inputShape.type == OperandType::TENSOR_FLOAT32) {
        optimized_ops::DepthToSpace(
                reinterpret_cast<const float*>(inputData),
                convertShapeToDims(inputShape),
                blockSize,
                reinterpret_cast<float*>(outputData),
                convertShapeToDims(outputShape));
    } else if (inputShape.type == OperandType::TENSOR_QUANT8_ASYMM) {
        optimized_ops::DepthToSpace(
                reinterpret_cast<const uint8_t*>(inputData),
                convertShapeToDims(inputShape),
                blockSize,
                reinterpret_cast<uint8_t*>(outputData),
                convertShapeToDims(outputShape));
    } else {
        LOG(ERROR) << "Unsupported data type";
        return false;
    }
    return true;
}

bool spaceToDepthGeneric(const uint8_t* inputData, const Shape& inputShape,
                         int32_t blockSize,
                         uint8_t* outputData, const Shape& outputShape) {
    if (inputShape.type == OperandType::TENSOR_FLOAT32) {
        optimized_ops::SpaceToDepth(
                reinterpret_cast<const float*>(inputData),
                convertShapeToDims(inputShape),
                blockSize,
                reinterpret_cast<float*>(outputData),
                convertShapeToDims(outputShape));
    } else if (inputShape.type == OperandType::TENSOR_QUANT8_ASYMM) {
        optimized_ops::SpaceToDepth(
                reinterpret_cast<const uint8_t*>(inputData),
                convertShapeToDims(inputShape),
                blockSize,
                reinterpret_cast<uint8_t*>(outputData),
                convertShapeToDims(outputShape));
    } else {
        LOG(ERROR) << "Unsupported data type";
        return false;
    }
    return true;
}

} // namespace nn
} // namespace android
