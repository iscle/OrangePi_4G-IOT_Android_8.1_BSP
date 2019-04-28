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

#ifndef ANDROID_ML_NN_RUNTIME_EXECUTION_BUILDER_H
#define ANDROID_ML_NN_RUNTIME_EXECUTION_BUILDER_H

#include "Callbacks.h"
#include "HalInterfaces.h"
#include "Memory.h"
#include "ModelBuilder.h"
#include "NeuralNetworks.h"

#include <unordered_map>
#include <vector>

using ::android::hardware::neuralnetworks::V1_0::implementation::ExecutionCallback;
using ::android::hardware::neuralnetworks::V1_0::implementation::PreparedModelCallback;

namespace android {
namespace nn {

class CompilationBuilder;
class ExecutionPlan;
class Memory;
class ModelBuilder;
class StepExecutor;

// TODO move length out of DataLocation
struct ModelArgumentInfo {
    // Whether the argument was specified as being in a Memory, as a pointer,
    // has no value, or has not been specified.
    // If POINTER then:
    //   locationAndLength.length is valid.
    //   dimensions is valid.
    //   buffer is valid
    // If MEMORY then:
    //   locationAndLength.location.{poolIndex, offset, length} is valid.
    //   dimensions is valid.
    enum { POINTER, MEMORY, HAS_NO_VALUE, UNSPECIFIED } state = UNSPECIFIED;
    DataLocation locationAndLength;
    std::vector<uint32_t> dimensions;
    void* buffer;

    int setFromPointer(const Operand& operand, const ANeuralNetworksOperandType* type, void* buffer,
                       uint32_t length);
    int setFromMemory(const Operand& operand, const ANeuralNetworksOperandType* type,
                      uint32_t poolIndex, uint32_t offset, uint32_t length);
    int setFromTemporaryMemory(const Operand& operand, uint32_t poolIndex, uint32_t offset);
    int updateDimensionInfo(const Operand& operand, const ANeuralNetworksOperandType* newType);
};

class ExecutionBuilder {
    friend class StepExecutor;
public:
    ExecutionBuilder(const CompilationBuilder* compilation);

    int setInput(uint32_t index, const ANeuralNetworksOperandType* type, const void* buffer,
                 size_t length);
    int setInputFromMemory(uint32_t index, const ANeuralNetworksOperandType* type,
                           const Memory* memory, size_t offset, size_t length);
    int setOutput(uint32_t index, const ANeuralNetworksOperandType* type, void* buffer,
                  size_t length);
    int setOutputFromMemory(uint32_t index, const ANeuralNetworksOperandType* type,
                            const Memory* memory, size_t offset, size_t length);
    int startCompute(sp<ExecutionCallback>* synchronizationCallback);

    const ModelBuilder* getModel() const { return mModel; }

private:
    const ModelBuilder* mModel;
    const ExecutionPlan* mPlan;

    // The information we'll send to the driver about the inputs and outputs.
    // Note that we build this in two steps:
    // 1. As the arguments are specified, set the corresponding mInputs or mOutputs element.
    //    If set from a pointer, don't set the location in the RequestArgument but store it
    //    instead in mInputBuffers or mOutputBuffers.
    // 2. Once we have all the inputs and outputs, if needed, allocate shared memory for
    //    the m*Buffers entries.  Copy the input values into the shared memory.
    // We do this to avoid creating a lot of shared memory objects if we have a lot of
    // parameters specified via pointers.  We also avoid copying in the case where
    // some of the nodes will interpreted on the CPU anyway.
    std::vector<ModelArgumentInfo> mInputs;
    std::vector<ModelArgumentInfo> mOutputs;
    MemoryTracker mMemories;
};

// class StepExecutor is used to execute a single "step" in a
// potentially multiple step execution process.  The graph associated
// with that step is executed in its entirety on a single device (or
// on the CPU).
class StepExecutor {
public:
    // executionBuilder
    //     Describes the full (possibly multiple-"step") execution.
    // model
    //     The model to be executed by the executor.  Possibly a
    //     submodel of the model from executionBuilder.
    // driver, preparedModel
    //     The device on which to execute the "step", and the prepared
    //     model to execute on that device.  (Both are nullptr in the
    //     case of CPU.)
    StepExecutor(const ExecutionBuilder* executionBuilder,
                 const ModelBuilder* model,
                 sp<IDevice> driver, sp<IPreparedModel> preparedModel);

    // Map inputs and outputs from ExecutionBuilder to StepExecutor,
    // in the case where we have a single-"step" execution (i.e., the executor
    // is executing the entire model from the ExecutionBuilder).
    void mapInputsAndOutputsTrivially();

    // Map inputs and outputs from ExecutionBuilder to StepExecutor,
    // one at a time.  Note that these are input/output indexes, not
    // operand indexes.
    void mapInput(uint32_t builderIndex, uint32_t executorIndex) {
        mapInputOrOutput(mExecutionBuilder->mInputs[builderIndex],
                         &mInputs[executorIndex]);
    }
    void mapOutput(uint32_t builderIndex, uint32_t executorIndex) {
        mapInputOrOutput(mExecutionBuilder->mOutputs[builderIndex],
                         &mOutputs[executorIndex]);
    }

    // The input or output is assumed to have the size of the
    // corresponding operand.
    int setInputFromTemporaryMemory(uint32_t inputIndex, const Memory* memory, uint32_t offset) {
        return setInputOrOutputFromTemporaryMemory(mModel->getInputOperand(inputIndex),
                                                   memory, offset,
                                                   &mInputs.at(inputIndex));
    }
    int setOutputFromTemporaryMemory(uint32_t outputIndex, const Memory* memory, uint32_t offset) {
        return setInputOrOutputFromTemporaryMemory(mModel->getOutputOperand(outputIndex),
                                                   memory, offset,
                                                   &mOutputs.at(outputIndex));
    }

    // Executes using the (driver, preparedModel) specified at construction time.
    int startCompute(sp<ExecutionCallback>* synchronizationCallback);

    // Executes using the CPU, regardless of the (driver,
    // preparedModel) specified at construction time.
    int startComputeOnCpu(sp<ExecutionCallback>* synchronizationCallback);

    bool isCpu() const { return mDriver == nullptr; }

private:
    int allocatePointerArgumentsToPool(std::vector<ModelArgumentInfo>* args, Memory* memory);
    int startComputeOnDevice(sp<ExecutionCallback>* synchronizationCallback);

    void mapInputOrOutput(const ModelArgumentInfo& builderInputOrOutput,
                          ModelArgumentInfo* executorInputOrOutput);

    int setInputOrOutputFromTemporaryMemory(const Operand& inputOrOutputOperand,
                                            const Memory* memory, uint32_t offset,
                                            ModelArgumentInfo* inputOrOutputInfo);

    // describes the full (possibly multiple-"step") execution
    const ExecutionBuilder* mExecutionBuilder;

    // model to be executed on the executor, in both original and
    // compiled forms; and device on which to execute it
    const ModelBuilder* mModel;
    sp<IDevice> mDriver;                // nullptr if CPU execution
    sp<IPreparedModel> mPreparedModel;  // nullptr if CPU execution or if bypassing ExecutionPlan

    // The information we'll send to the driver about the inputs and outputs.
    // Note that we build this in two steps:
    // 1. As the arguments are specified, set the corresponding mInputs or mOutputs element.
    //    If set from a pointer, don't set the location in the RequestArgument but store it
    //    instead in mInputBuffers or mOutputBuffers.
    // 2. Once we have all the inputs and outputs, if needed, allocate shared memory for
    //    the m*Buffers entries.  Copy the input values into the shared memory.
    // We do this to avoid creating a lot of shared memory objects if we have a lot of
    // parameters specified via pointers.  We also avoid copying in the case where
    // some of the nodes will interpreted on the CPU anyway.
    std::vector<ModelArgumentInfo> mInputs;
    std::vector<ModelArgumentInfo> mOutputs;
    MemoryTracker mMemories;
};

} // namespace nn
} // namespace android

#endif // ANDROID_ML_NN_RUNTIME_EXECUTION_BUILDER_H
