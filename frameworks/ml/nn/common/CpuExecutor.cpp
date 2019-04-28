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

#define LOG_TAG "CpuExecutor"

#include "CpuExecutor.h"

#include "NeuralNetworks.h"
#include "Operations.h"

#include <sys/mman.h>

namespace android {
namespace nn {

// TODO: short term, make share memory mapping and updating a utility function.
// TODO: long term, implement mmap_fd as a hidl IMemory service.
bool RunTimePoolInfo::set(const hidl_memory& hidlMemory) {
    this->hidlMemory = hidlMemory;
    auto memType = hidlMemory.name();
    if (memType == "ashmem") {
        memory = mapMemory(hidlMemory);
        if (memory == nullptr) {
            LOG(ERROR) << "Can't map shared memory.";
            return false;
        }
        memory->update();
        buffer = reinterpret_cast<uint8_t*>(static_cast<void*>(memory->getPointer()));
        if (buffer == nullptr) {
            LOG(ERROR) << "Can't access shared memory.";
            return false;
        }
        return true;
    } else if (memType == "mmap_fd") {
        size_t size = hidlMemory.size();
        int fd = hidlMemory.handle()->data[0];
        int prot = hidlMemory.handle()->data[1];
        size_t offset = getSizeFromInts(hidlMemory.handle()->data[2],
                                        hidlMemory.handle()->data[3]);
        buffer = static_cast<uint8_t*>(mmap(nullptr, size, prot, MAP_SHARED, fd, offset));
        if (buffer == MAP_FAILED) {
            LOG(ERROR) << "Can't mmap the file descriptor.";
            return false;
        }
        return true;
    } else {
        LOG(ERROR) << "unsupported hidl_memory type";
        return false;
    }
}

// Making sure the output data are correctly updated after execution.
bool RunTimePoolInfo::update() {
    auto memType = hidlMemory.name();
    if (memType == "ashmem") {
        memory->commit();
        return true;
    } else if (memType == "mmap_fd") {
        int prot = hidlMemory.handle()->data[1];
        if (prot & PROT_WRITE) {
            size_t size = hidlMemory.size();
            return msync(buffer, size, MS_SYNC) == 0;
        }
    }
    // No-op for other types of memory.
    return true;
}

bool setRunTimePoolInfosFromHidlMemories(std::vector<RunTimePoolInfo>* poolInfos,
                                         const hidl_vec<hidl_memory>& pools) {
    poolInfos->resize(pools.size());
    for (size_t i = 0; i < pools.size(); i++) {
        auto& poolInfo = (*poolInfos)[i];
        if (!poolInfo.set(pools[i])) {
            LOG(ERROR) << "Could not map pool";
            return false;
        }
    }
    return true;
}

// Updates the RunTimeOperandInfo with the newly calculated shape.
// Allocate the buffer if we need to.
static bool setInfoAndAllocateIfNeeded(RunTimeOperandInfo* info, const Shape& shape) {
    // For user-provided model output operands, the parameters must match the Shape
    // calculated from the preparation step.
    if (info->lifetime == OperandLifeTime::MODEL_OUTPUT) {
        if (info->type != shape.type ||
            info->dimensions != shape.dimensions) {
            LOG(ERROR) << "Invalid type or dimensions for model output";
            return false;
        }
        if (info->type == OperandType::TENSOR_QUANT8_ASYMM &&
            (info->scale != shape.scale || info->zeroPoint != shape.offset)) {
            LOG(ERROR) << "Invalid scale or zeroPoint for model output";
            return false;
        }
    }
    info->type = shape.type;
    info->dimensions = shape.dimensions;
    info->scale = shape.scale;
    info->zeroPoint = shape.offset;
    if (info->lifetime == OperandLifeTime::TEMPORARY_VARIABLE && info->buffer == nullptr) {
        uint32_t length = sizeOfData(info->type, info->dimensions);
        info->buffer = new uint8_t[length];
        if (info->buffer == nullptr) {
            return false;
        }
    }
    return true;
}

// Ignore the .pools entry in model and request.  This will have been taken care of
// by the caller.
int CpuExecutor::run(const Model& model, const Request& request,
                     const std::vector<RunTimePoolInfo>& modelPoolInfos,
                     const std::vector<RunTimePoolInfo>& requestPoolInfos) {
    VLOG(CPUEXE) << "CpuExecutor::run()";
    // VLOG(CPUEXE) << "model: " << toString(model);
    VLOG(CPUEXE) << "request: " << toString(request);

    mModel = &model;
    mRequest = &request; // TODO check if mRequest is needed
    initializeRunTimeInfo(modelPoolInfos, requestPoolInfos);
    // The model has serialized the operation in execution order.
    for (const auto& operation : model.operations) {
        int n = executeOperation(operation);
        if (n != ANEURALNETWORKS_NO_ERROR) {
            return n;
        }
    }
    for (auto runtimeInfo : modelPoolInfos) {
        runtimeInfo.update();
    }
    for (auto runtimeInfo : requestPoolInfos) {
        runtimeInfo.update();
    }
    mModel = nullptr;
    mRequest = nullptr;
    VLOG(CPUEXE) << "Completed run normally";
    return ANEURALNETWORKS_NO_ERROR;
}

bool CpuExecutor::initializeRunTimeInfo(const std::vector<RunTimePoolInfo>& modelPoolInfos,
                                        const std::vector<RunTimePoolInfo>& requestPoolInfos) {
    VLOG(CPUEXE) << "CpuExecutor::initializeRunTimeInfo";
    const size_t count = mModel->operands.size();
    mOperands.resize(count);

    // Start by setting the runtime info to what's in the model.
    for (size_t i = 0; i < count; i++) {
        const Operand& from = mModel->operands[i];
        RunTimeOperandInfo& to = mOperands[i];
        to.type = from.type;
        to.dimensions = from.dimensions;
        to.scale = from.scale;
        to.zeroPoint = from.zeroPoint;
        to.length = from.location.length;
        to.lifetime = from.lifetime;
        switch (from.lifetime) {
            case OperandLifeTime::TEMPORARY_VARIABLE:
                to.buffer = nullptr;
                to.numberOfUsesLeft = from.numberOfConsumers;
                break;
            case OperandLifeTime::CONSTANT_COPY:
                to.buffer = const_cast<uint8_t*>(&mModel->operandValues[from.location.offset]);
                to.numberOfUsesLeft = 0;
                break;
            case OperandLifeTime::CONSTANT_REFERENCE: {
                auto poolIndex = from.location.poolIndex;
                nnAssert(poolIndex < modelPoolInfos.size());
                auto& r = modelPoolInfos[poolIndex];
                to.buffer = r.buffer + from.location.offset;
                to.numberOfUsesLeft = 0;
                break;
            }
            case OperandLifeTime::MODEL_INPUT:
            case OperandLifeTime::MODEL_OUTPUT:
            case OperandLifeTime::NO_VALUE:
                to.buffer = nullptr;
                to.numberOfUsesLeft = 0;
                break;
            default:
                nnAssert(false);
                break;
        }
    }

    // Adjust the runtime info for the arguments passed to the model,
    // modifying the buffer location, and possibly the dimensions.
    auto updateForArguments = [this, &requestPoolInfos](const std::vector<uint32_t>& indexes,
                                  const hidl_vec<RequestArgument>& arguments) {
        nnAssert(indexes.size() == arguments.size());
        for (size_t i = 0; i < indexes.size(); i++) {
            const uint32_t operandIndex = indexes[i];
            const RequestArgument& from = arguments[i];
            RunTimeOperandInfo& to = mOperands[operandIndex];
            if (from.dimensions.size() > 0) {
                // It's the responsibility of the caller to validate that
                // from.dimensions only modifies the dimensions that were
                // unspecified in the model.  That's the case in SampleDriver.cpp
                // with the call to validateRequest().
                // TODO make sure that's the case for the default CPU path.
                to.dimensions = from.dimensions;
            }
            if (from.hasNoValue) {
                to.lifetime = OperandLifeTime::NO_VALUE;
                nnAssert(to.buffer == nullptr);
            } else {
                auto poolIndex = from.location.poolIndex;
                nnAssert(poolIndex < requestPoolInfos.size());
                auto& r = requestPoolInfos[poolIndex];
                to.buffer = r.buffer + from.location.offset;
            }
        }
    };
    updateForArguments(mModel->inputIndexes, mRequest->inputs);
    updateForArguments(mModel->outputIndexes, mRequest->outputs);

    return true;
}

void CpuExecutor::freeNoLongerUsedOperands(const std::vector<uint32_t>& inputs) {
    for (uint32_t i : inputs) {
        auto& info = mOperands[i];
        // Check if it's a static or model input/output.
        if (info.numberOfUsesLeft == 0) {
            continue;
        }
        info.numberOfUsesLeft--;
        if (info.numberOfUsesLeft == 0) {
            nnAssert(info.buffer != nullptr);
            delete[] info.buffer;
            info.buffer = nullptr;
        }
    }
}

int CpuExecutor::executeOperation(const Operation& operation) {
    // VLOG(CPUEXE) << "CpuExecutor::executeOperation(" << toString(operation) << ")";
    const hidl_vec<uint32_t>& ins = operation.inputs;
    const hidl_vec<uint32_t>& outs = operation.outputs;
    bool success = false;

    // Function to verify that the number of input and output parameters
    // matches what is expected.  Also checks that all the parameters have
    // values. This function is to be used only for operations that do not
    // accept optional arguments.
    // TODO Have a version that works for optional arguments.
    auto allParametersPresent = [&operation, &ins, &outs, this](size_t requiredIns,
                                                                size_t requiredOuts) -> bool {
        auto verify = [&operation, this](size_t requiredCount, const hidl_vec<uint32_t>& indexes,
                          const char* type) -> bool {
            size_t actualCount = indexes.size();
            if (actualCount != requiredCount) {
                LOG(ERROR) << getOperationName(operation.type)
                           << ": Invalid number of " << type << " operands. Got " << actualCount
                           << " of " << requiredCount;
                return false;
            }
            for (size_t i = 0; i < actualCount; i++) {
                if (mOperands[indexes[i]].lifetime == OperandLifeTime::NO_VALUE) {
                    LOG(ERROR) << getOperationName(operation.type) << " " << type
                               << " operand " << i << " is required but missing.";
                    return false;
                }
            }
            return true;
        };
        return verify(requiredIns, ins, "in") && verify(requiredOuts, outs, "out");
    };

    switch (operation.type) {
        case OperationType::OEM_OPERATION: {
            LOG(ERROR) << "OEM operation not supported for CPU execution";
            success = false;
        } break;
        case OperationType::ADD: {
            if (!allParametersPresent(3, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& in1 = mOperands[ins[0]];
            const RunTimeOperandInfo& in2 = mOperands[ins[1]];
            int32_t activation = getScalarData<int32_t>(mOperands[ins[2]]);

            RunTimeOperandInfo& out = mOperands[outs[0]];
            Shape outShape = out.shape();

            if (in1.type == OperandType::TENSOR_FLOAT32) {
                success = addMulPrepare(in1.shape(), in2.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&out, outShape) &&
                          addFloat32(reinterpret_cast<const float*>(in1.buffer),
                                     in1.shape(),
                                     reinterpret_cast<const float*>(in2.buffer),
                                     in2.shape(),
                                     activation,
                                     reinterpret_cast<float*>(out.buffer),
                                     outShape);
            } else if (in1.type == OperandType::TENSOR_QUANT8_ASYMM) {
                success = addMulPrepare(in1.shape(), in2.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&out, outShape) &&
                          addQuant8(reinterpret_cast<const uint8_t*>(in1.buffer),
                                    in1.shape(),
                                    reinterpret_cast<const uint8_t*>(in2.buffer),
                                    in2.shape(),
                                    activation,
                                    reinterpret_cast<uint8_t*>(out.buffer),
                                    outShape);
            }
        } break;
        case OperationType::MUL: {
            if (!allParametersPresent(3, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& in1 = mOperands[ins[0]];
            const RunTimeOperandInfo& in2 = mOperands[ins[1]];
            int32_t activation = getScalarData<int32_t>(mOperands[ins[2]]);

            RunTimeOperandInfo& out = mOperands[outs[0]];
            Shape outShape = out.shape();

            if (in1.type == OperandType::TENSOR_FLOAT32) {
                success = addMulPrepare(in1.shape(), in2.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&out, outShape) &&
                          mulFloat32(reinterpret_cast<const float*>(in1.buffer),
                                     in1.shape(),
                                     reinterpret_cast<const float*>(in2.buffer),
                                     in2.shape(),
                                     activation,
                                     reinterpret_cast<float*>(out.buffer),
                                     outShape);
            } else if (in1.type == OperandType::TENSOR_QUANT8_ASYMM) {
                success = addMulPrepare(in1.shape(), in2.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&out, outShape) &&
                          mulQuant8(reinterpret_cast<const uint8_t*>(in1.buffer),
                                    in1.shape(),
                                    reinterpret_cast<const uint8_t*>(in2.buffer),
                                    in2.shape(),
                                    activation,
                                    reinterpret_cast<uint8_t*>(out.buffer),
                                    outShape);
            }
        } break;
        case OperationType::FLOOR: {
            if (!allParametersPresent(1, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& input = mOperands[ins[0]];
            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            if (input.type == OperandType::TENSOR_FLOAT32) {
                success = floorPrepare(input.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          floorFloat32(reinterpret_cast<const float*>(input.buffer),
                                       reinterpret_cast<float*>(output.buffer),
                                       outShape);
            }
        } break;
        case OperationType::DEQUANTIZE: {
            if (!allParametersPresent(1, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& input = mOperands[ins[0]];
            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            if (input.type == OperandType::TENSOR_QUANT8_ASYMM) {
                success = dequantizePrepare(input.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          dequantizeQuant8ToFloat32(
                                  reinterpret_cast<const uint8_t*>(input.buffer),
                                  reinterpret_cast<float*>(output.buffer),
                                  input.shape());
            }
        } break;
        case OperationType::DEPTHWISE_CONV_2D: {
            const size_t inCount = ins.size();
            if ((inCount != 11 && inCount != 8) ||
                    !allParametersPresent(inCount, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& input  = mOperands[ins[0]];
            const RunTimeOperandInfo& filter = mOperands[ins[1]];
            const RunTimeOperandInfo& bias   = mOperands[ins[2]];

            int32_t padding_left, padding_right;
            int32_t padding_top, padding_bottom;
            int32_t stride_width, stride_height;
            int32_t depth_multiplier;
            int32_t activation;

            if (inCount == 11) {
                padding_left     = getScalarData<int32_t>(mOperands[ins[3]]);
                padding_right    = getScalarData<int32_t>(mOperands[ins[4]]);
                padding_top      = getScalarData<int32_t>(mOperands[ins[5]]);
                padding_bottom   = getScalarData<int32_t>(mOperands[ins[6]]);
                stride_width     = getScalarData<int32_t>(mOperands[ins[7]]);
                stride_height    = getScalarData<int32_t>(mOperands[ins[8]]);
                depth_multiplier = getScalarData<int32_t>(mOperands[ins[9]]);
                activation       = getScalarData<int32_t>(mOperands[ins[10]]);
            } else {
                int32_t padding_implicit = getScalarData<int32_t>(mOperands[ins[3]]);
                stride_width     = getScalarData<int32_t>(mOperands[ins[4]]);
                stride_height    = getScalarData<int32_t>(mOperands[ins[5]]);
                depth_multiplier = getScalarData<int32_t>(mOperands[ins[6]]);
                activation       = getScalarData<int32_t>(mOperands[ins[7]]);

                Shape inputShape = input.shape();
                Shape filterShape = filter.shape();
                int32_t input_width  = getSizeOfDimension(inputShape, 2);
                int32_t input_height = getSizeOfDimension(inputShape, 1);
                int32_t filter_width  = getSizeOfDimension(filterShape, 2);
                int32_t filter_height = getSizeOfDimension(filterShape, 1);
                calculateExplicitPadding(input_width, stride_width,
                                         filter_width, padding_implicit,
                                         &padding_left, &padding_right);
                calculateExplicitPadding(input_height, stride_height,
                                         filter_height, padding_implicit,
                                         &padding_top, &padding_bottom);
            }

            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            if (input.type == OperandType::TENSOR_FLOAT32) {
                success = depthwiseConvPrepare(input.shape(), filter.shape(), bias.shape(),
                                               padding_left, padding_right,
                                               padding_top, padding_bottom,
                                               stride_width, stride_height,
                                               &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          depthwiseConvFloat32(reinterpret_cast<const float*>(input.buffer),
                                               input.shape(),
                                               reinterpret_cast<const float*>(filter.buffer),
                                               filter.shape(),
                                               reinterpret_cast<const float*>(bias.buffer),
                                               bias.shape(),
                                               padding_left, padding_right,
                                               padding_top, padding_bottom,
                                               stride_width, stride_height,
                                               depth_multiplier, activation,
                                               reinterpret_cast<float*>(output.buffer),
                                               outShape);
            } else if (input.type == OperandType::TENSOR_QUANT8_ASYMM) {
                success = depthwiseConvPrepare(input.shape(), filter.shape(), bias.shape(),
                                               padding_left, padding_right,
                                               padding_top, padding_bottom,
                                               stride_width, stride_height,
                                               &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          depthwiseConvQuant8(reinterpret_cast<const uint8_t*>(input.buffer),
                                              input.shape(),
                                              reinterpret_cast<const uint8_t*>(filter.buffer),
                                              filter.shape(),
                                              reinterpret_cast<const int32_t*>(bias.buffer),
                                              bias.shape(),
                                              padding_left, padding_right,
                                              padding_top, padding_bottom,
                                              stride_width, stride_height,
                                              depth_multiplier, activation,
                                              reinterpret_cast<uint8_t*>(output.buffer),
                                              outShape);
            }

        } break;
        case OperationType::CONV_2D: {
            const size_t inCount = ins.size();
            if ((inCount != 10 && inCount != 7) ||
                    !allParametersPresent(inCount, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& input  = mOperands[ins[0]];
            const RunTimeOperandInfo& filter = mOperands[ins[1]];
            const RunTimeOperandInfo& bias   = mOperands[ins[2]];

            int32_t padding_left, padding_right;
            int32_t padding_top, padding_bottom;
            int32_t stride_width, stride_height;
            int32_t activation;

            if (inCount == 10) {
                padding_left     = getScalarData<int32_t>(mOperands[ins[3]]);
                padding_right    = getScalarData<int32_t>(mOperands[ins[4]]);
                padding_top      = getScalarData<int32_t>(mOperands[ins[5]]);
                padding_bottom   = getScalarData<int32_t>(mOperands[ins[6]]);
                stride_width     = getScalarData<int32_t>(mOperands[ins[7]]);
                stride_height    = getScalarData<int32_t>(mOperands[ins[8]]);
                activation       = getScalarData<int32_t>(mOperands[ins[9]]);
            } else {
                int32_t padding_implicit = getScalarData<int32_t>(mOperands[ins[3]]);
                stride_width     = getScalarData<int32_t>(mOperands[ins[4]]);
                stride_height    = getScalarData<int32_t>(mOperands[ins[5]]);
                activation       = getScalarData<int32_t>(mOperands[ins[6]]);

                Shape inputShape = input.shape();
                Shape filterShape = filter.shape();
                int32_t input_width  = getSizeOfDimension(inputShape, 2);
                int32_t input_height = getSizeOfDimension(inputShape, 1);
                int32_t filter_width  = getSizeOfDimension(filterShape, 2);
                int32_t filter_height = getSizeOfDimension(filterShape, 1);
                calculateExplicitPadding(input_width, stride_width,
                                         filter_width, padding_implicit,
                                         &padding_left, &padding_right);
                calculateExplicitPadding(input_height, stride_height,
                                         filter_height, padding_implicit,
                                         &padding_top, &padding_bottom);
            }

            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            if (input.type == OperandType::TENSOR_FLOAT32) {
                success = convPrepare(input.shape(), filter.shape(), bias.shape(),
                                      padding_left, padding_right,
                                      padding_top, padding_bottom,
                                      stride_width, stride_height,
                                      &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          convFloat32(reinterpret_cast<const float*>(input.buffer), input.shape(),
                                      reinterpret_cast<const float*>(filter.buffer), filter.shape(),
                                      reinterpret_cast<const float*>(bias.buffer), bias.shape(),
                                      padding_left, padding_right,
                                      padding_top, padding_bottom,
                                      stride_width, stride_height, activation,
                                      reinterpret_cast<float*>(output.buffer), outShape);
            } else if (input.type == OperandType::TENSOR_QUANT8_ASYMM) {
                success = convPrepare(input.shape(), filter.shape(), bias.shape(),
                                      padding_left, padding_right,
                                      padding_top, padding_bottom,
                                      stride_width, stride_height,
                                      &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          convQuant8(reinterpret_cast<const uint8_t*>(input.buffer),
                                     input.shape(),
                                     reinterpret_cast<const uint8_t*>(filter.buffer),
                                     filter.shape(),
                                     reinterpret_cast<const int32_t*>(bias.buffer),
                                     bias.shape(),
                                     padding_left, padding_right,
                                     padding_top, padding_bottom,
                                     stride_width, stride_height, activation,
                                     reinterpret_cast<uint8_t*>(output.buffer),
                                     outShape);
            }
        } break;
        case OperationType::AVERAGE_POOL_2D: {
            const size_t inCount = ins.size();
            if ((inCount != 10 && inCount != 7) ||
                    !allParametersPresent(inCount, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& input = mOperands[ins[0]];

            int32_t padding_left, padding_right;
            int32_t padding_top, padding_bottom;
            int32_t stride_width, stride_height;
            int32_t filter_width, filter_height;
            int32_t activation;

            if (inCount == 10) {
                padding_left     = getScalarData<int32_t>(mOperands[ins[1]]);
                padding_right    = getScalarData<int32_t>(mOperands[ins[2]]);
                padding_top      = getScalarData<int32_t>(mOperands[ins[3]]);
                padding_bottom   = getScalarData<int32_t>(mOperands[ins[4]]);
                stride_width     = getScalarData<int32_t>(mOperands[ins[5]]);
                stride_height    = getScalarData<int32_t>(mOperands[ins[6]]);
                filter_width     = getScalarData<int32_t>(mOperands[ins[7]]);
                filter_height    = getScalarData<int32_t>(mOperands[ins[8]]);
                activation       = getScalarData<int32_t>(mOperands[ins[9]]);
            } else {
                int32_t padding_implicit = getScalarData<int32_t>(mOperands[ins[1]]);
                stride_width     = getScalarData<int32_t>(mOperands[ins[2]]);
                stride_height    = getScalarData<int32_t>(mOperands[ins[3]]);
                filter_width     = getScalarData<int32_t>(mOperands[ins[4]]);
                filter_height    = getScalarData<int32_t>(mOperands[ins[5]]);
                activation       = getScalarData<int32_t>(mOperands[ins[6]]);

                Shape inputShape = input.shape();
                int32_t input_width  = getSizeOfDimension(inputShape, 2);
                int32_t input_height = getSizeOfDimension(inputShape, 1);
                calculateExplicitPadding(input_width, stride_width,
                                         filter_width, padding_implicit,
                                         &padding_left, &padding_right);
                calculateExplicitPadding(input_height, stride_height,
                                         filter_height, padding_implicit,
                                         &padding_top, &padding_bottom);
            }

            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            if (input.type == OperandType::TENSOR_FLOAT32) {
                success = genericPoolingPrepare(input.shape(),
                                                padding_left, padding_right,
                                                padding_top, padding_bottom,
                                                stride_width, stride_height,
                                                filter_width, filter_height,
                                                &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          averagePoolFloat32(reinterpret_cast<const float*>(input.buffer),
                                             input.shape(),
                                             padding_left, padding_right,
                                             padding_top, padding_bottom,
                                             stride_width, stride_height,
                                             filter_width, filter_height, activation,
                                             reinterpret_cast<float*>(output.buffer),
                                             outShape);
            } else if (input.type == OperandType::TENSOR_QUANT8_ASYMM) {
                success = genericPoolingPrepare(input.shape(),
                                                padding_left, padding_right,
                                                padding_top, padding_bottom,
                                                stride_width, stride_height,
                                                filter_width, filter_height,
                                                &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          averagePoolQuant8(reinterpret_cast<const uint8_t*>(input.buffer),
                                            input.shape(),
                                            padding_left, padding_right,
                                            padding_top, padding_bottom,
                                            stride_width, stride_height,
                                            filter_width, filter_height, activation,
                                            reinterpret_cast<uint8_t*>(output.buffer),
                                            outShape);
            }
        } break;
        case OperationType::L2_POOL_2D: {
            const size_t inCount = ins.size();
            if ((inCount != 10 && inCount != 7) ||
                    !allParametersPresent(inCount, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& input = mOperands[ins[0]];

            int32_t padding_left, padding_right;
            int32_t padding_top, padding_bottom;
            int32_t stride_width, stride_height;
            int32_t filter_width, filter_height;
            int32_t activation;

            if (inCount == 10) {
                padding_left     = getScalarData<int32_t>(mOperands[ins[1]]);
                padding_right    = getScalarData<int32_t>(mOperands[ins[2]]);
                padding_top      = getScalarData<int32_t>(mOperands[ins[3]]);
                padding_bottom   = getScalarData<int32_t>(mOperands[ins[4]]);
                stride_width     = getScalarData<int32_t>(mOperands[ins[5]]);
                stride_height    = getScalarData<int32_t>(mOperands[ins[6]]);
                filter_width     = getScalarData<int32_t>(mOperands[ins[7]]);
                filter_height    = getScalarData<int32_t>(mOperands[ins[8]]);
                activation       = getScalarData<int32_t>(mOperands[ins[9]]);
            } else {
                int32_t padding_implicit = getScalarData<int32_t>(mOperands[ins[1]]);
                stride_width     = getScalarData<int32_t>(mOperands[ins[2]]);
                stride_height    = getScalarData<int32_t>(mOperands[ins[3]]);
                filter_width     = getScalarData<int32_t>(mOperands[ins[4]]);
                filter_height    = getScalarData<int32_t>(mOperands[ins[5]]);
                activation       = getScalarData<int32_t>(mOperands[ins[6]]);

                Shape inputShape = input.shape();
                int32_t input_width  = getSizeOfDimension(inputShape, 2);
                int32_t input_height = getSizeOfDimension(inputShape, 1);
                calculateExplicitPadding(input_width, stride_width,
                                         filter_width, padding_implicit,
                                         &padding_left, &padding_right);
                calculateExplicitPadding(input_height, stride_height,
                                         filter_height, padding_implicit,
                                         &padding_top, &padding_bottom);
            }

            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            if (input.type == OperandType::TENSOR_FLOAT32) {
                success = genericPoolingPrepare(input.shape(),
                                                padding_left, padding_right,
                                                padding_top, padding_bottom,
                                                stride_width, stride_height,
                                                filter_width, filter_height,
                                                &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          l2PoolFloat32(reinterpret_cast<const float*>(input.buffer),
                                        input.shape(),
                                        padding_left, padding_right,
                                        padding_top, padding_bottom,
                                        stride_width, stride_height,
                                        filter_width, filter_height, activation,
                                        reinterpret_cast<float*>(output.buffer),
                                        outShape);
            }
        } break;
        case OperationType::MAX_POOL_2D: {
            const size_t inCount = ins.size();
            if ((inCount != 10 && inCount != 7) ||
                    !allParametersPresent(inCount, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& input = mOperands[ins[0]];

            int32_t padding_left, padding_right;
            int32_t padding_top, padding_bottom;
            int32_t stride_width, stride_height;
            int32_t filter_width, filter_height;
            int32_t activation;

            if (inCount == 10) {
                padding_left     = getScalarData<int32_t>(mOperands[ins[1]]);
                padding_right    = getScalarData<int32_t>(mOperands[ins[2]]);
                padding_top      = getScalarData<int32_t>(mOperands[ins[3]]);
                padding_bottom   = getScalarData<int32_t>(mOperands[ins[4]]);
                stride_width     = getScalarData<int32_t>(mOperands[ins[5]]);
                stride_height    = getScalarData<int32_t>(mOperands[ins[6]]);
                filter_width     = getScalarData<int32_t>(mOperands[ins[7]]);
                filter_height    = getScalarData<int32_t>(mOperands[ins[8]]);
                activation       = getScalarData<int32_t>(mOperands[ins[9]]);
            } else {
                int32_t padding_implicit = getScalarData<int32_t>(mOperands[ins[1]]);
                stride_width     = getScalarData<int32_t>(mOperands[ins[2]]);
                stride_height    = getScalarData<int32_t>(mOperands[ins[3]]);
                filter_width     = getScalarData<int32_t>(mOperands[ins[4]]);
                filter_height    = getScalarData<int32_t>(mOperands[ins[5]]);
                activation       = getScalarData<int32_t>(mOperands[ins[6]]);

                Shape inputShape = input.shape();
                int32_t input_width  = getSizeOfDimension(inputShape, 2);
                int32_t input_height = getSizeOfDimension(inputShape, 1);
                calculateExplicitPadding(input_width, stride_width,
                                         filter_width, padding_implicit,
                                         &padding_left, &padding_right);
                calculateExplicitPadding(input_height, stride_height,
                                         filter_height, padding_implicit,
                                         &padding_top, &padding_bottom);
            }

            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            if (input.type == OperandType::TENSOR_FLOAT32) {
                success = genericPoolingPrepare(input.shape(),
                                                padding_left, padding_right,
                                                padding_top, padding_bottom,
                                                stride_width, stride_height,
                                                filter_width, filter_height,
                                                &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          maxPoolFloat32(reinterpret_cast<const float*>(input.buffer),
                                         input.shape(),
                                         padding_left, padding_right,
                                         padding_top, padding_bottom,
                                         stride_width, stride_height,
                                         filter_width, filter_height, activation,
                                         reinterpret_cast<float*>(output.buffer),
                                         outShape);
            } else if (input.type == OperandType::TENSOR_QUANT8_ASYMM) {
                success = genericPoolingPrepare(input.shape(),
                                                padding_left, padding_right,
                                                padding_top, padding_bottom,
                                                stride_width, stride_height,
                                                filter_width, filter_height,
                                                &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          maxPoolQuant8(reinterpret_cast<const uint8_t*>(input.buffer),
                                        input.shape(),
                                        padding_left, padding_right,
                                        padding_top, padding_bottom,
                                        stride_width, stride_height,
                                        filter_width, filter_height, activation,
                                        reinterpret_cast<uint8_t*>(output.buffer),
                                        outShape);
            }

        } break;
        case OperationType::RELU: {
            if (!allParametersPresent(1, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& input = mOperands[ins[0]];
            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            if (input.type == OperandType::TENSOR_FLOAT32) {
                success = genericActivationPrepare(input.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          reluFloat32(reinterpret_cast<const float*>(input.buffer),
                                      input.shape(),
                                      reinterpret_cast<float*>(output.buffer),
                                      outShape);
            } else if (input.type == OperandType::TENSOR_QUANT8_ASYMM) {
                success = genericActivationPrepare(input.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          reluQuant8(reinterpret_cast<const uint8_t*>(input.buffer),
                                     input.shape(),
                                     reinterpret_cast<uint8_t*>(output.buffer),
                                     outShape);
            }
        } break;
        case OperationType::RELU1: {
            if (!allParametersPresent(1, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& input = mOperands[ins[0]];
            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            if (input.type == OperandType::TENSOR_FLOAT32) {
                success = genericActivationPrepare(input.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          relu1Float32(reinterpret_cast<const float*>(input.buffer),
                                       input.shape(),
                                       reinterpret_cast<float*>(output.buffer),
                                       outShape);
            } else if (input.type == OperandType::TENSOR_QUANT8_ASYMM) {
                success = genericActivationPrepare(input.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          relu1Quant8(reinterpret_cast<const uint8_t*>(input.buffer),
                                      input.shape(),
                                      reinterpret_cast<uint8_t*>(output.buffer),
                                      outShape);
            }
        } break;
        case OperationType::RELU6: {
            if (!allParametersPresent(1, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& input = mOperands[ins[0]];
            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            if (input.type == OperandType::TENSOR_FLOAT32) {
                success = genericActivationPrepare(input.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          relu6Float32(reinterpret_cast<const float*>(input.buffer),
                                       input.shape(),
                                       reinterpret_cast<float*>(output.buffer),
                                       outShape);
            } else if (input.type == OperandType::TENSOR_QUANT8_ASYMM) {
                success = genericActivationPrepare(input.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          relu6Quant8(reinterpret_cast<const uint8_t*>(input.buffer),
                                      input.shape(),
                                      reinterpret_cast<uint8_t*>(output.buffer),
                                      outShape);
            }
        } break;
        case OperationType::TANH: {
            if (!allParametersPresent(1, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& input = mOperands[ins[0]];
            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            if (input.type == OperandType::TENSOR_FLOAT32) {
                success = genericActivationPrepare(input.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          tanhFloat32(reinterpret_cast<const float*>(input.buffer),
                                      input.shape(),
                                      reinterpret_cast<float*>(output.buffer),
                                      outShape);
            }
        } break;
        case OperationType::LOGISTIC: {
            if (!allParametersPresent(1, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& input = mOperands[ins[0]];
            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            if (input.type == OperandType::TENSOR_FLOAT32) {
                success = genericActivationPrepare(input.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          logisticFloat32(reinterpret_cast<const float*>(input.buffer),
                                          input.shape(),
                                          reinterpret_cast<float*>(output.buffer),
                                          outShape);
            } else if (input.type == OperandType::TENSOR_QUANT8_ASYMM) {
                success = genericActivationPrepare(input.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          logisticQuant8(reinterpret_cast<const uint8_t*>(input.buffer),
                                         input.shape(),
                                         reinterpret_cast<uint8_t*>(output.buffer),
                                         outShape);
            }
        } break;
        case OperationType::SOFTMAX: {
            if (!allParametersPresent(2, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            RunTimeOperandInfo& input = mOperands[ins[0]];
            float beta = getScalarData<float>(mOperands[ins[1]]);
            if (beta <= 0.0f) {
                LOG(ERROR) << "beta must be positive for softmax";
                return ANEURALNETWORKS_BAD_DATA;
            }

            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            if (input.type == OperandType::TENSOR_FLOAT32) {
                success = genericActivationPrepare(input.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          softmaxFloat32(reinterpret_cast<const float*>(input.buffer),
                                         input.shape(),
                                         beta,
                                         reinterpret_cast<float*>(output.buffer),
                                         output.shape());
            } else if (input.type == OperandType::TENSOR_QUANT8_ASYMM) {
                success = genericActivationPrepare(input.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          softmaxQuant8(reinterpret_cast<const uint8_t*>(input.buffer),
                                        input.shape(),
                                        beta,
                                        reinterpret_cast<uint8_t*>(output.buffer),
                                        output.shape());
            }
        } break;
        case OperationType::FULLY_CONNECTED: {
            if (!allParametersPresent(4, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            RunTimeOperandInfo& input   = mOperands[ins[0]];
            RunTimeOperandInfo& weights = mOperands[ins[1]];
            RunTimeOperandInfo& bias    = mOperands[ins[2]];

            int32_t activation = getScalarData<int32_t>(mOperands[ins[3]]);

            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            if (input.type == OperandType::TENSOR_FLOAT32) {
                success = fullyConnectedPrepare(input.shape(), weights.shape(), bias.shape(),
                                                &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          fullyConnectedFloat32(reinterpret_cast<const float*>(input.buffer),
                                                input.shape(),
                                                reinterpret_cast<const float*>(weights.buffer),
                                                weights.shape(),
                                                reinterpret_cast<const float*>(bias.buffer),
                                                bias.shape(),
                                                activation,
                                                reinterpret_cast<float*>(output.buffer),
                                                outShape);
            } else if (input.type == OperandType::TENSOR_QUANT8_ASYMM) {
                success = fullyConnectedPrepare(input.shape(), weights.shape(), bias.shape(),
                                                &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          fullyConnectedQuant8(reinterpret_cast<const uint8_t*>(input.buffer),
                                               input.shape(),
                                               reinterpret_cast<const uint8_t*>(weights.buffer),
                                               weights.shape(),
                                               reinterpret_cast<const int32_t*>(bias.buffer),
                                               bias.shape(),
                                               activation,
                                               reinterpret_cast<uint8_t*>(output.buffer),
                                               outShape);
            }
        } break;
        case OperationType::CONCATENATION: {
            if (outs.size() != 1 || ins.size() < 2) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            int numInputTensors = ins.size() - 1;
            int32_t axis = getScalarData<int32_t>(mOperands[ins[numInputTensors]]);

            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            const RunTimeOperandInfo& firstInput = mOperands[ins[0]];
            if (firstInput.type == OperandType::TENSOR_FLOAT32) {
                std::vector<Shape> inputShapes(numInputTensors);
                std::vector<const float*> inputDataPtrs(numInputTensors);

                for (int i=0; i<numInputTensors; i++) {
                    RunTimeOperandInfo& input = mOperands[ins[i]];
                    inputShapes[i] = input.shape();
                    inputDataPtrs[i] = reinterpret_cast<const float*>(input.buffer);
                }
                success = concatenationPrepare(inputShapes, axis, &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          concatenationFloat32(inputDataPtrs, inputShapes, axis,
                                               reinterpret_cast<float*>(output.buffer), outShape);
            } else if (firstInput.type == OperandType::TENSOR_QUANT8_ASYMM) {
                std::vector<Shape> inputShapes(numInputTensors);
                std::vector<const uint8_t*> inputDataPtrs(numInputTensors);

                for (int i=0; i<numInputTensors; i++) {
                    RunTimeOperandInfo& input = mOperands[ins[i]];
                    inputShapes[i] = input.shape();
                    inputDataPtrs[i] = reinterpret_cast<const uint8_t*>(input.buffer);
                }
                success = concatenationPrepare(inputShapes, axis, &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          concatenationQuant8(inputDataPtrs, inputShapes, axis,
                                              reinterpret_cast<uint8_t*>(output.buffer),
                                              outShape);
            }
        } break;
        case OperationType::L2_NORMALIZATION: {
            if (!allParametersPresent(1, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& input = mOperands[ins[0]];
            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            if (input.type == OperandType::TENSOR_FLOAT32) {
                success = genericNormalizationPrepare(input.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          l2normFloat32(reinterpret_cast<const float*>(input.buffer),
                                        input.shape(),
                                        reinterpret_cast<float*>(output.buffer),
                                        outShape);
            } else if (input.type == OperandType::TENSOR_QUANT8_ASYMM) {
                success = genericNormalizationPrepare(input.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          l2normQuant8(reinterpret_cast<const uint8_t*>(input.buffer),
                                       input.shape(),
                                       reinterpret_cast<uint8_t*>(output.buffer),
                                       outShape);
            }
        } break;
        case OperationType::LOCAL_RESPONSE_NORMALIZATION: {
            if (!allParametersPresent(5, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& input = mOperands[ins[0]];
            int32_t radius = getScalarData<int32_t>(mOperands[ins[1]]);
            float bias = getScalarData<float>(mOperands[ins[2]]);
            float alpha = getScalarData<float>(mOperands[ins[3]]);
            float beta = getScalarData<float>(mOperands[ins[4]]);

            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            if (input.type == OperandType::TENSOR_FLOAT32) {
                success = genericNormalizationPrepare(input.shape(), &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          localResponseNormFloat32(reinterpret_cast<const float*>(input.buffer),
                                                   input.shape(),
                                                   radius, bias, alpha, beta,
                                                   reinterpret_cast<float*>(output.buffer),
                                                   outShape);
            }
        } break;
        case OperationType::RESHAPE: {
            if (!allParametersPresent(2, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& input = mOperands[ins[0]];
            const RunTimeOperandInfo& targetShape = mOperands[ins[1]];

            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            success = reshapePrepare(input.shape(),
                                     reinterpret_cast<const int32_t*>(targetShape.buffer),
                                     getNumberOfElements(targetShape.shape()),
                                     &outShape) &&
                      setInfoAndAllocateIfNeeded(&output, outShape) &&
                      reshapeGeneric(reinterpret_cast<const void*>(input.buffer),
                                     input.shape(),
                                     reinterpret_cast<void*>(output.buffer),
                                     outShape);
        } break;
        case OperationType::RESIZE_BILINEAR: {
            if (!allParametersPresent(3, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& input = mOperands[ins[0]];
            int32_t width = getScalarData<int32_t>(mOperands[ins[1]]);
            int32_t height = getScalarData<int32_t>(mOperands[ins[2]]);

            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            if (input.type == OperandType::TENSOR_FLOAT32) {
                success = resizeBilinearPrepare(input.shape(),
                                                width, height,
                                                &outShape) &&
                          setInfoAndAllocateIfNeeded(&output, outShape) &&
                          resizeBilinearFloat32(reinterpret_cast<const float*>(input.buffer),
                                                input.shape(),
                                                reinterpret_cast<float*>(output.buffer),
                                                outShape);
            }
        } break;
        case OperationType::DEPTH_TO_SPACE: {
            if (!allParametersPresent(2, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& input = mOperands[ins[0]];
            int32_t blockSize = getScalarData<int32_t>(mOperands[ins[1]]);

            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            success = depthToSpacePrepare(input.shape(),
                                          blockSize,
                                          &outShape) &&
                      setInfoAndAllocateIfNeeded(&output, outShape) &&
                      depthToSpaceGeneric(input.buffer,
                                          input.shape(),
                                          blockSize,
                                          output.buffer,
                                          outShape);
        } break;
        case OperationType::SPACE_TO_DEPTH: {
            if (!allParametersPresent(2, 1)) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const RunTimeOperandInfo& input = mOperands[ins[0]];
            int32_t blockSize = getScalarData<int32_t>(mOperands[ins[1]]);

            RunTimeOperandInfo& output = mOperands[outs[0]];
            Shape outShape = output.shape();

            success = spaceToDepthPrepare(input.shape(),
                                          blockSize,
                                          &outShape) &&
                      setInfoAndAllocateIfNeeded(&output, outShape) &&
                      spaceToDepthGeneric(input.buffer,
                                          input.shape(),
                                          blockSize,
                                          output.buffer,
                                          outShape);
        } break;
        case OperationType::EMBEDDING_LOOKUP: {
            const RunTimeOperandInfo &values =
                mOperands[ins[EmbeddingLookup::kValueTensor]];
            const RunTimeOperandInfo &lookups =
                mOperands[ins[EmbeddingLookup::kLookupTensor]];
            RunTimeOperandInfo &output =
                mOperands[outs[EmbeddingLookup::kOutputTensor]];

            Shape outputShape;
            EmbeddingLookup lookup(operation, mOperands);

            success = embeddingLookupPrepare(values.shape(), lookups.shape(), &outputShape) &&
                setInfoAndAllocateIfNeeded(&output, outputShape) &&
                lookup.Eval();
        } break;
        case OperationType::HASHTABLE_LOOKUP: {
            const RunTimeOperandInfo &lookups =
                mOperands[ins[HashtableLookup::kLookupTensor]];
            const RunTimeOperandInfo &keys =
                mOperands[ins[HashtableLookup::kKeyTensor]];
            const RunTimeOperandInfo &values =
                mOperands[ins[HashtableLookup::kValueTensor]];

            RunTimeOperandInfo &output =
                mOperands[outs[HashtableLookup::kOutputTensor]];
            RunTimeOperandInfo &hits =
                mOperands[outs[HashtableLookup::kHitsTensor]];

            Shape outputShape, hitShape;
            HashtableLookup lookup(operation, mOperands);

            success = hashtableLookupPrepare(lookups.shape(), keys.shape(), values.shape(),
                                             &outputShape, &hitShape) &&
                setInfoAndAllocateIfNeeded(&output, outputShape) &&
                setInfoAndAllocateIfNeeded(&hits, hitShape) &&
                lookup.Eval();
        } break;
        case OperationType::LSH_PROJECTION: {
            RunTimeOperandInfo &output =
                mOperands[outs[LSHProjection::kOutputTensor]];

            Shape outputShape;
            LSHProjection lsh(operation, mOperands);

            success = LSHProjection::Prepare(operation, mOperands,
                                             &outputShape) &&
                setInfoAndAllocateIfNeeded(&output, outputShape) &&
                lsh.Eval();
        } break;
        case OperationType::LSTM: {
            RunTimeOperandInfo &scratch =
                mOperands[outs[LSTMCell::kScratchBufferTensor]];
            RunTimeOperandInfo &outputStateOut =
                mOperands[outs[LSTMCell::kOutputStateOutTensor]];
            RunTimeOperandInfo &cellStateOut =
                mOperands[outs[LSTMCell::kCellStateOutTensor]];
            RunTimeOperandInfo &output =
                mOperands[outs[LSTMCell::kOutputTensor]];

            Shape scratchShape, outputStateShape, cellStateShape, outputShape;
            LSTMCell lstm_cell(operation, mOperands);

            success = LSTMCell::Prepare(operation, mOperands,
                                        &scratchShape, &outputStateShape,
                                        &cellStateShape, &outputShape) &&
                setInfoAndAllocateIfNeeded(&scratch, scratchShape) &&
                setInfoAndAllocateIfNeeded(&outputStateOut, outputStateShape) &&
                setInfoAndAllocateIfNeeded(&cellStateOut, cellStateShape) &&
                setInfoAndAllocateIfNeeded(&output, outputShape) &&
                lstm_cell.Eval();
        } break;
        case OperationType::RNN: {
            RunTimeOperandInfo &hiddenStateOut =
                mOperands[outs[RNN::kHiddenStateOutTensor]];
            RunTimeOperandInfo &output =
                mOperands[outs[RNN::kOutputTensor]];

            Shape hiddenStateShape, outputShape;
            RNN rnn_cell(operation, mOperands);

            success = RNN::Prepare(operation, mOperands,
                                   &hiddenStateShape, &outputShape) &&
                setInfoAndAllocateIfNeeded(&hiddenStateOut, hiddenStateShape) &&
                setInfoAndAllocateIfNeeded(&output, outputShape) &&
                rnn_cell.Eval();
        } break;
        case OperationType::SVDF: {
            RunTimeOperandInfo &stateOut =
                mOperands[outs[SVDF::kStateOutTensor]];
            RunTimeOperandInfo &output =
                mOperands[outs[SVDF::kOutputTensor]];

            Shape stateShape, outputShape;
            SVDF svdf(operation, mOperands);

            success = SVDF::Prepare(operation, mOperands,
                                    &stateShape, &outputShape) &&
                setInfoAndAllocateIfNeeded(&stateOut, stateShape) &&
                setInfoAndAllocateIfNeeded(&output, outputShape) &&
                svdf.Eval();
        } break;
        default:
            nnAssert(false);
            break;
    }
    if (!success) {
        LOG(ERROR) << getOperationName(operation.type) << " failed.";
        return ANEURALNETWORKS_OP_FAILED;
    }

    freeNoLongerUsedOperands(ins);
    return ANEURALNETWORKS_NO_ERROR;
}

} // namespace nn
} // namespace android
