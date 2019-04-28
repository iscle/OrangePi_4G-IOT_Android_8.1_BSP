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

#ifndef ANDROID_ML_NN_SAMPLE_DRIVER_SAMPLE_DRIVER_H
#define ANDROID_ML_NN_SAMPLE_DRIVER_SAMPLE_DRIVER_H

#include "CpuExecutor.h"
#include "HalInterfaces.h"
#include "NeuralNetworks.h"

#include <string>

namespace android {
namespace nn {
namespace sample_driver {

// Base class used to create sample drivers for the NN HAL.  This class
// provides some implementation of the more common functions.
//
// Since these drivers simulate hardware, they must run the computations
// on the CPU.  An actual driver would not do that.
class SampleDriver : public IDevice {
public:
    SampleDriver(const char* name) : mName(name) {}
    ~SampleDriver() override {}
    Return<ErrorStatus> prepareModel(const Model& model,
                                     const sp<IPreparedModelCallback>& callback) override;
    Return<DeviceStatus> getStatus() override;

    // Starts and runs the driver service.  Typically called from main().
    // This will return only once the service shuts down.
    int run();
protected:
    std::string mName;
};

class SamplePreparedModel : public IPreparedModel {
public:
    SamplePreparedModel(const Model& model)
          : // Make a copy of the model, as we need to preserve it.
            mModel(model) {}
    ~SamplePreparedModel() override {}
    bool initialize();
    Return<ErrorStatus> execute(const Request& request,
                                const sp<IExecutionCallback>& callback) override;

private:
    void asyncExecute(const Request& request, const sp<IExecutionCallback>& callback);

    Model mModel;
    std::vector<RunTimePoolInfo> mPoolInfos;
};

} // namespace sample_driver
} // namespace nn
} // namespace android

#endif // ANDROID_ML_NN_SAMPLE_DRIVER_SAMPLE_DRIVER_H
