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

#ifndef VTS_HAL_RENDERSCRIPT_V1_0_TARGET_TESTS_H
#define VTS_HAL_RENDERSCRIPT_V1_0_TARGET_TESTS_H

#define LOG_TAG "renderscript_hidl_hal_test"
#include <android-base/logging.h>

#include <android/hardware/renderscript/1.0/IContext.h>
#include <android/hardware/renderscript/1.0/IDevice.h>
#include <android/hardware/renderscript/1.0/types.h>

#include <VtsHalHidlTargetTestBase.h>
#include <gtest/gtest.h>

using ::android::hardware::renderscript::V1_0::Allocation;
using ::android::hardware::renderscript::V1_0::AllocationAdapter;
using ::android::hardware::renderscript::V1_0::AllocationCubemapFace;
using ::android::hardware::renderscript::V1_0::AllocationMipmapControl;
using ::android::hardware::renderscript::V1_0::AllocationUsageType;
using ::android::hardware::renderscript::V1_0::Closure;
using ::android::hardware::renderscript::V1_0::IContext;
using ::android::hardware::renderscript::V1_0::IDevice;
using ::android::hardware::renderscript::V1_0::ContextType;
using ::android::hardware::renderscript::V1_0::DataType;
using ::android::hardware::renderscript::V1_0::DataKind;
using ::android::hardware::renderscript::V1_0::Element;
using ::android::hardware::renderscript::V1_0::MessageToClientType;
using ::android::hardware::renderscript::V1_0::NativeWindow;
using ::android::hardware::renderscript::V1_0::ObjectBase;
using ::android::hardware::renderscript::V1_0::OpaqueHandle;
using ::android::hardware::renderscript::V1_0::Ptr;
using ::android::hardware::renderscript::V1_0::Sampler;
using ::android::hardware::renderscript::V1_0::SamplerValue;
using ::android::hardware::renderscript::V1_0::Script;
using ::android::hardware::renderscript::V1_0::ScriptFieldID;
using ::android::hardware::renderscript::V1_0::ScriptGroup;
using ::android::hardware::renderscript::V1_0::ScriptGroup2;
using ::android::hardware::renderscript::V1_0::ScriptIntrinsicID;
using ::android::hardware::renderscript::V1_0::ScriptInvokeID;
using ::android::hardware::renderscript::V1_0::ScriptKernelID;
using ::android::hardware::renderscript::V1_0::Size;
using ::android::hardware::renderscript::V1_0::ThreadPriorities;
using ::android::hardware::renderscript::V1_0::Type;
using ::android::hardware::renderscript::V1_0::YuvFormat;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

// bitcode slots
extern const int mExportVarIdx_var_int;
extern const int mExportVarIdx_var_long;
extern const int mExportVarIdx_var_float;
extern const int mExportVarIdx_var_double;
extern const int mExportVarIdx_var_allocation;
extern const int mExportVarIdx_var_uint32_t;
extern const int mExportVarIdx_var_point2;
extern const int mExportVarIdx_var_int_ptr;
// bitcode invoke slots
//extern const int mExportForEachIdx_root;
extern const int mExportForEachIdx_increment;
// bitcode reduce slots
extern const int mExportReduceIdx_summation;
// bitcode invoke slots
extern const int mExportFuncIdx_function;
extern const int mExportFuncIdx_functionV;
extern const int mExportFuncIdx_setBuffer;
extern const int mExportFuncIdx_setAllocation;
// bitcode
typedef signed char int8_t;
extern const int8_t bitCode[];
extern const int bitCodeLength;

// The main test class for RENDERSCRIPT HIDL HAL.
class RenderscriptHidlTest : public ::testing::VtsHalHidlTargetTestBase {
public:
    virtual void SetUp() override;
    virtual void TearDown() override;

    sp<IContext>   context;

private:
    sp<IDevice>    device;
};

#endif // VTS_HAL_RENDERSCRIPT_V1_0_TARGET_TESTS_H
