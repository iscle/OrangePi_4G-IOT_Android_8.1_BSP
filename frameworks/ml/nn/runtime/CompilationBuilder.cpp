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

#define LOG_TAG "CompilationBuilder"

#include "CompilationBuilder.h"

#include "ExecutionBuilder.h"
#include "ExecutionPlan.h"
#include "Manager.h"
#include "ModelBuilder.h"
#include "Utils.h"

namespace android {
namespace nn {

CompilationBuilder::CompilationBuilder(const ModelBuilder* model) :
    mModel(model) {
    VLOG(COMPILATION) << "CompilationBuilder::CompilationBuilder";
}

int CompilationBuilder::finish() {
    if (mFinished) {
        LOG(ERROR) << "ANeuralNetworksCompilation_finish called more than once";
        return ANEURALNETWORKS_BAD_STATE;
    }
    // TODO validate the rest

    mFinished = true;

    if (uint32_t p = DeviceManager::get()->getPartitioning()) {
        // Get the list of HAL devices.
        const std::vector<std::shared_ptr<Device>>& devices = DeviceManager::get()->getDrivers();

        int n = mModel->partitionTheWork(devices, mPreference, &mPlan);
        if (!DeviceManager::partitioningAllowsFallback(p) &&
            (n != ANEURALNETWORKS_NO_ERROR)) {
            return n;
        }
    }

    return ANEURALNETWORKS_NO_ERROR;
}

int CompilationBuilder::setPreference(int32_t preference) {
    if (mFinished) {
        LOG(ERROR) <<
                "ANeuralNetworksCompilation_setPreference can't modify after compilation finished";
        return ANEURALNETWORKS_BAD_STATE;
    }
    if (preference >= kNumberOfPreferences) {
        LOG(ERROR) << "ANeuralNetworksCompilation_setPreference invalid preference " << preference;
        return ANEURALNETWORKS_BAD_DATA;
    }

    mPreference = preference;
    return ANEURALNETWORKS_NO_ERROR;
}

int CompilationBuilder::createExecution(ExecutionBuilder **execution) {
    if (!mFinished) {
        LOG(ERROR) << "ANeuralNetworksExecution_create passed an unfinished compilation";
        *execution = nullptr;
        return ANEURALNETWORKS_BAD_STATE;
    }
    *execution = new ExecutionBuilder(this);
    return (*execution ? ANEURALNETWORKS_NO_ERROR : ANEURALNETWORKS_OUT_OF_MEMORY);
}

}  // namespace nn
}  // namespace android
