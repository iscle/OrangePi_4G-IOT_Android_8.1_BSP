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

// Classes used to plan how to execute a model across multiple devices.

#ifndef ANDROID_ML_NN_RUNTIME_EXECUTION_PLAN_H
#define ANDROID_ML_NN_RUNTIME_EXECUTION_PLAN_H

#include "HalInterfaces.h"
#include "Memory.h"
#include "NeuralNetworks.h"
#include "Utils.h"

#include <set>

namespace android {
namespace nn {

class CompilationBuilder;
class Device;
class ExecutionBuilder;
class ExecutionPlan;
class Memory;
class ModelBuilder;
class StepExecutor;

class ExecutionStep {
private:
    typedef std::vector<std::pair<uint32_t, uint32_t>> RemapVectorType;
    typedef std::set<std::pair<uint32_t, uint32_t>> SubModelOutputSetType;

public:
    enum OperandKind { INPUT, OUTPUT };

    ExecutionStep(ExecutionPlan* plan,
                  uint32_t stepIndex,
                  std::shared_ptr<ModelBuilder> model,
                  std::shared_ptr<Device> device);
    int addOperation(int operationIndex, const ModelBuilder& fromModel);
    int addOperand(uint32_t fromOperandIndex, uint32_t* toOperandIndex,
                   const ModelBuilder& fromModel, OperandKind kind);

    // Each container entry is of the form (fromModel index, subModel index)
    const RemapVectorType& getModelInputs() const {
        return mModelInputs;
    }
    const RemapVectorType& getModelOutputs() const {
        return mModelOutputs;
    }
    const RemapVectorType& getSubModelInputs() const {
        return mSubModelInputs;
    }
    const SubModelOutputSetType& getSubModelOutputs() const {
        return mSubModelOutputs;
    }

    void recordSubModelOutput(uint32_t fromModelIndex) {
        const auto it = mOperandMap.find(fromModelIndex);
        nnAssert(it != mOperandMap.end());
        mSubModelOutputs.insert(std::make_pair(fromModelIndex, it->second));
    }

    // If this step has a submodel output of unknown size, sets
    // *hasOutputOfUnknownSize to true; otherwise, leaves it
    // unchanged.
    int finishSubModel(const ModelBuilder* fromModel, bool* hasOutputOfUnknownSize);

    std::shared_ptr<ModelBuilder> getSubModel() const { return mSubModel; }
    std::shared_ptr<Device> getDevice() const { return mDevice; }

    // only available after calling finishSubModel()
    sp<IPreparedModel> getPreparedSubModel() const { return mPreparedSubModel; }

    // Map inputs and outputs from ExecutionBuilder to StepExecutor.
    void mapInputsAndOutputs(std::shared_ptr<StepExecutor> stepExecutor) const;

    void dump() const;
private:
    // TODO: Some of the data is working state information that
    // shouldn't be needed after we've constructed but not executed
    // the step.

    ExecutionPlan* mPlan;
    uint32_t mIndex;  // index of step within plan
    std::shared_ptr<ModelBuilder> mSubModel;
    std::shared_ptr<Device> mDevice;  // nullptr signifies CPU
    sp<IPreparedModel> mPreparedSubModel;  // not used for CPU

    // Inputs of original model that are also inputs of this submodel:
    //     (fromModel index, subModel index)
    RemapVectorType mModelInputs;
    // Outputs of original model that are also outputs of this submodel:
    //     (fromModel index, subModel index)
    RemapVectorType mModelOutputs;
    // Temporaries of original model that are inputs of this submodel:
    //     (fromModel index, subModel index)
    RemapVectorType mSubModelInputs;
    // Temporaries of original model that are outputs of this submodel:
    //     (fromModel index, subModel index)
    SubModelOutputSetType mSubModelOutputs;
    // Converts operand indexes from the main model to the submodel.
    std::unordered_map<uint32_t, uint32_t> mOperandMap;
    // Converts input indexes from the submodel to the main model
    // (these are input indexes, not operand indexes).  This vector
    // only describes inputs of the submodel that are also inputs of
    // the main model -- that is, mModelInputs but not mSubModelInputs.
    std::vector<uint32_t> mInputIndexSubModelToFromModel;
    // Converts output indexes from the submodel to the main model
    // (these are output indexes, not operand indexes).  This vector
    // only describes outputs of the submodel that are also outputs of
    // the main model -- that is, mModelOutputs but not mSubModelOutputs.
    std::vector<uint32_t> mOutputIndexSubModelToFromModel;
};

class ExecutionPlan {
public:
    ExecutionPlan(const ExecutionPlan&) = delete;
    ExecutionPlan& operator=(const ExecutionPlan&) = delete;

    ExecutionPlan() { }
    ~ExecutionPlan() { delete mBody; }

    // Controller is part of the interface to a mechanism for
    // performing an execution in N steps.
    //
    // Usage pattern:
    // - Instantiate Controller with ExecutionPlan::makeController().
    // - Call ExecutionPlan::next() on Controller N+1 times.  The first N times,
    //   *executor is set to point to a new StepExecutor corresponding
    //   to that step.  The N+1st time, *executor is set to nullptr,
    //   signifying there are no more steps.
    // - If ExecutionPlan::next() returns anything other than ANEURALNETWORKS_NO_ERROR,
    //   a problem has occurred.
    class Controller {
        friend class ExecutionPlan;
    private:
        Controller(const Controller&) = delete;
        Controller& operator=(const Controller&) = delete;

        // Map from the operand index of a TEMPORARY in the original
        // model to an offset into mTemporaries used to represent that
        // TEMPORARY as an inter-partition input or output.
        typedef std::map<uint32_t, uint32_t> SubModelInputsAndOutputsType;

        static const size_t kBadStepIndex = ~size_t(0);

        Controller(const ExecutionPlan* plan, const ExecutionBuilder* executionBuilder,
                   std::shared_ptr<const SubModelInputsAndOutputsType> subModelInputsAndOutputs,
                   uint32_t totalSizeOfTemporaries);

        const ExecutionPlan* mPlan;
        const ExecutionBuilder* mExecutionBuilder;
        std::shared_ptr<const SubModelInputsAndOutputsType> mSubModelInputsAndOutputs;  // may be nullptr
        Memory mTemporaries;
        size_t mNextStepIndex;
    };

    std::shared_ptr<Controller> makeController(const ExecutionBuilder* executionBuilder) const;

    int next(std::shared_ptr<Controller> controller, std::shared_ptr<StepExecutor>* executor) const;

    // Create the same executor as the last one created by next().
    int fallback(std::shared_ptr<Controller> controller, std::shared_ptr<StepExecutor>* executor) const;

    std::shared_ptr<ExecutionStep> createNewStep(const std::shared_ptr<Device> device);

    void becomeSingleStep(const std::shared_ptr<Device> device,
                          const ModelBuilder* model);

    int finish(const ModelBuilder* fromModel);

    void recordTemporaryDef(uint32_t fromModelIndex, uint32_t stepIndex) {
        auto& temporaryToDefiningStep = compound()->mTemporaryToDefiningStep;
        nnAssert(temporaryToDefiningStep.count(fromModelIndex) == 0);
        temporaryToDefiningStep.insert(std::make_pair(fromModelIndex, stepIndex));
    }

    void dump() const;

    // These functions are solely intended for use by unit tests of
    // the partitioning algorithm.
    enum class Kind { ERROR, EMPTY, SIMPLE, COMPOUND };
    Kind forTest_getKind() const;
    std::shared_ptr<const Device> forTest_simpleGetDevice() const;
    const std::vector<std::shared_ptr<ExecutionStep>>& forTest_compoundGetSteps() const;

private:
    void findSubModelOutputs();

    struct Body {
        virtual ~Body() {}
        virtual void dump() const = 0;
        virtual int finish(const ModelBuilder* fromModel) = 0;
        bool mSuccessfulFinish = false;
    };

    struct SimpleBody : Body {
        SimpleBody(std::shared_ptr<Device> device, const ModelBuilder* model) :
                mDevice(device), mModel(model) {}

        void dump() const override;
        int finish(const ModelBuilder* fromModel) override;

        std::shared_ptr<Device> mDevice;  // nullptr signifies CPU
        const ModelBuilder* mModel;
        sp<IPreparedModel> mPreparedModel;  // not used for CPU
    };

    struct CompoundBody : Body {
        void dump() const override;
        int finish(const ModelBuilder* fromModel) override;

        // TODO: Some of the data is working state information that
        // shouldn't be needed after we've constructed but not
        // executed the plan.

        std::vector<std::shared_ptr<ExecutionStep>> mSteps;

        // Map from original operand index to defining step index.
        // Used for all (and only) TEMPORARY_VARIABLEs.
        std::unordered_map<uint32_t, uint32_t> mTemporaryToDefiningStep;

        bool mHasSubModelOutputOfUnknownSize = false;
    private:
        void findSubModelOutputs();
    };

    enum { EMPTY, SIMPLE, COMPOUND } mState = EMPTY;
    Body* mBody = nullptr;
    CompoundBody* compound() {
        nnAssert(mState == COMPOUND);
        return static_cast<CompoundBody*>(mBody);
    }
    const CompoundBody* compound() const {
        nnAssert(mState == COMPOUND);
        return static_cast<const CompoundBody*>(mBody);
    }
};

}  // namespace nn
}  // namespace android

#endif  // ANDROID_ML_NN_RUNTIME_EXECUTION_PLAN_H
