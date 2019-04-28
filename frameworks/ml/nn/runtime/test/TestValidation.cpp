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

#include "NeuralNetworks.h"

//#include <android-base/logging.h>
#include <gtest/gtest.h>
#include <string>


// This file tests all the validations done by the Neural Networks API.

namespace {
class ValidationTest : public ::testing::Test {
protected:
    virtual void SetUp() {}
};

class ValidationTestModel : public ValidationTest {
protected:
    virtual void SetUp() {
        ValidationTest::SetUp();
        ASSERT_EQ(ANeuralNetworksModel_create(&mModel), ANEURALNETWORKS_NO_ERROR);
    }
    virtual void TearDown() {
        ANeuralNetworksModel_free(mModel);
        ValidationTest::TearDown();
    }
    ANeuralNetworksModel* mModel = nullptr;
};

class ValidationTestCompilation : public ValidationTestModel {
protected:
    virtual void SetUp() {
        ValidationTestModel::SetUp();

        uint32_t dimensions[]{1};
        ANeuralNetworksOperandType tensorType{.type = ANEURALNETWORKS_TENSOR_FLOAT32,
                                              .dimensionCount = 1,
                                              .dimensions = dimensions};
        ASSERT_EQ(ANeuralNetworksModel_addOperand(mModel, &tensorType), ANEURALNETWORKS_NO_ERROR);
        ASSERT_EQ(ANeuralNetworksModel_addOperand(mModel, &tensorType), ANEURALNETWORKS_NO_ERROR);
        ASSERT_EQ(ANeuralNetworksModel_addOperand(mModel, &tensorType), ANEURALNETWORKS_NO_ERROR);
        uint32_t inList[2]{0, 1};
        uint32_t outList[1]{2};
        ASSERT_EQ(ANeuralNetworksModel_addOperation(mModel, ANEURALNETWORKS_ADD, 2, inList, 1,
                                                    outList),
                  ANEURALNETWORKS_NO_ERROR);
        ASSERT_EQ(ANeuralNetworksModel_finish(mModel), ANEURALNETWORKS_NO_ERROR);

        ASSERT_EQ(ANeuralNetworksCompilation_create(mModel, &mCompilation),
                  ANEURALNETWORKS_NO_ERROR);
    }
    virtual void TearDown() {
        ANeuralNetworksCompilation_free(mCompilation);
        ValidationTestModel::TearDown();
    }
    ANeuralNetworksCompilation* mCompilation = nullptr;
};

class ValidationTestExecution : public ValidationTestCompilation {
protected:
    virtual void SetUp() {
        ValidationTestCompilation::SetUp();

        ASSERT_EQ(ANeuralNetworksCompilation_finish(mCompilation), ANEURALNETWORKS_NO_ERROR);

        ASSERT_EQ(ANeuralNetworksExecution_create(mCompilation, &mExecution),
                  ANEURALNETWORKS_NO_ERROR);
    }
    virtual void TearDown() {
        ANeuralNetworksExecution_free(mExecution);
        ValidationTestCompilation::TearDown();
    }
    ANeuralNetworksExecution* mExecution = nullptr;
};

TEST_F(ValidationTest, CreateModel) {
    EXPECT_EQ(ANeuralNetworksModel_create(nullptr), ANEURALNETWORKS_UNEXPECTED_NULL);
}

TEST_F(ValidationTestModel, AddOperand) {
    ANeuralNetworksOperandType floatType{
                .type = ANEURALNETWORKS_FLOAT32, .dimensionCount = 0, .dimensions = nullptr};
    EXPECT_EQ(ANeuralNetworksModel_addOperand(nullptr, &floatType),
              ANEURALNETWORKS_UNEXPECTED_NULL);
    EXPECT_EQ(ANeuralNetworksModel_addOperand(mModel, nullptr), ANEURALNETWORKS_UNEXPECTED_NULL);
    // TODO more types,
}

TEST_F(ValidationTestModel, SetOperandValue) {
    ANeuralNetworksOperandType floatType{
                .type = ANEURALNETWORKS_FLOAT32, .dimensionCount = 0, .dimensions = nullptr};
    EXPECT_EQ(ANeuralNetworksModel_addOperand(mModel, &floatType), ANEURALNETWORKS_NO_ERROR);

    char buffer[20];
    EXPECT_EQ(ANeuralNetworksModel_setOperandValue(nullptr, 0, buffer, sizeof(buffer)),
              ANEURALNETWORKS_UNEXPECTED_NULL);
    EXPECT_EQ(ANeuralNetworksModel_setOperandValue(mModel, 0, nullptr, sizeof(buffer)),
              ANEURALNETWORKS_UNEXPECTED_NULL);

    // This should fail, since buffer is not the size of a float32.
    EXPECT_EQ(ANeuralNetworksModel_setOperandValue(mModel, 0, buffer, sizeof(buffer)),
              ANEURALNETWORKS_BAD_DATA);

    // This should fail, as this operand does not exist.
    EXPECT_EQ(ANeuralNetworksModel_setOperandValue(mModel, 1, buffer, 4), ANEURALNETWORKS_BAD_DATA);

    // TODO lots of validation of type
    // EXPECT_EQ(ANeuralNetworksModel_setOperandValue(mModel, 0, buffer,
    // sizeof(buffer)), ANEURALNETWORKS_UNEXPECTED_NULL);
}

TEST_F(ValidationTestModel, AddOperation) {
    uint32_t input = 0;
    uint32_t output = 0;
    EXPECT_EQ(ANeuralNetworksModel_addOperation(nullptr, ANEURALNETWORKS_AVERAGE_POOL_2D, 1, &input,
                                                1, &output),
              ANEURALNETWORKS_UNEXPECTED_NULL);
    EXPECT_EQ(ANeuralNetworksModel_addOperation(mModel, ANEURALNETWORKS_AVERAGE_POOL_2D, 0, nullptr,
                                                1, &output),
              ANEURALNETWORKS_UNEXPECTED_NULL);
    EXPECT_EQ(ANeuralNetworksModel_addOperation(mModel, ANEURALNETWORKS_AVERAGE_POOL_2D, 1, &input,
                                                0, nullptr),
              ANEURALNETWORKS_UNEXPECTED_NULL);
    // EXPECT_EQ(ANeuralNetworksModel_addOperation(mModel,
    // ANEURALNETWORKS_AVERAGE_POOL_2D, &inputs,
    //                                            &outputs),
    //          ANEURALNETWORKS_UNEXPECTED_NULL);
}

TEST_F(ValidationTestModel, SetInputsAndOutputs) {
    uint32_t input = 0;
    uint32_t output = 0;
    EXPECT_EQ(ANeuralNetworksModel_identifyInputsAndOutputs(nullptr, 1, &input, 1, &output),
              ANEURALNETWORKS_UNEXPECTED_NULL);
    EXPECT_EQ(ANeuralNetworksModel_identifyInputsAndOutputs(mModel, 0, nullptr, 1, &output),
              ANEURALNETWORKS_UNEXPECTED_NULL);
    EXPECT_EQ(ANeuralNetworksModel_identifyInputsAndOutputs(mModel, 1, &input, 0, nullptr),
              ANEURALNETWORKS_UNEXPECTED_NULL);
}

TEST_F(ValidationTestModel, Finish) {
    EXPECT_EQ(ANeuralNetworksModel_finish(nullptr), ANEURALNETWORKS_UNEXPECTED_NULL);
    EXPECT_EQ(ANeuralNetworksModel_finish(mModel), ANEURALNETWORKS_NO_ERROR);
    EXPECT_EQ(ANeuralNetworksModel_finish(mModel), ANEURALNETWORKS_BAD_STATE);
}

TEST_F(ValidationTestModel, CreateCompilation) {
    ANeuralNetworksCompilation* compilation = nullptr;
    EXPECT_EQ(ANeuralNetworksCompilation_create(nullptr, &compilation),
              ANEURALNETWORKS_UNEXPECTED_NULL);
    EXPECT_EQ(ANeuralNetworksCompilation_create(mModel, nullptr), ANEURALNETWORKS_UNEXPECTED_NULL);
    EXPECT_EQ(ANeuralNetworksCompilation_create(mModel, &compilation), ANEURALNETWORKS_BAD_STATE);

    // EXPECT_EQ(ANeuralNetworksCompilation_create(mModel, ANeuralNetworksCompilation *
    // *compilation),
    //          ANEURALNETWORKS_UNEXPECTED_NULL);
}

TEST_F(ValidationTestCompilation, SetPreference) {
    EXPECT_EQ(ANeuralNetworksCompilation_setPreference(nullptr, ANEURALNETWORKS_PREFER_LOW_POWER),
              ANEURALNETWORKS_UNEXPECTED_NULL);

    EXPECT_EQ(ANeuralNetworksCompilation_setPreference(mCompilation, 40), ANEURALNETWORKS_BAD_DATA);
}

TEST_F(ValidationTestCompilation, CreateExecution) {
    ANeuralNetworksExecution* execution = nullptr;
    EXPECT_EQ(ANeuralNetworksExecution_create(nullptr, &execution),
              ANEURALNETWORKS_UNEXPECTED_NULL);
    EXPECT_EQ(ANeuralNetworksExecution_create(mCompilation, nullptr),
              ANEURALNETWORKS_UNEXPECTED_NULL);
    EXPECT_EQ(ANeuralNetworksExecution_create(mCompilation, &execution),
              ANEURALNETWORKS_BAD_STATE);
    // EXPECT_EQ(ANeuralNetworksExecution_create(mCompilation, ANeuralNetworksExecution *
    // *execution),
    //          ANEURALNETWORKS_UNEXPECTED_NULL);
}

TEST_F(ValidationTestCompilation, Finish) {
    EXPECT_EQ(ANeuralNetworksCompilation_finish(nullptr), ANEURALNETWORKS_UNEXPECTED_NULL);
    EXPECT_EQ(ANeuralNetworksCompilation_finish(mCompilation), ANEURALNETWORKS_NO_ERROR);
    EXPECT_EQ(ANeuralNetworksCompilation_setPreference(mCompilation,
                                                       ANEURALNETWORKS_PREFER_FAST_SINGLE_ANSWER),
              ANEURALNETWORKS_BAD_STATE);
    EXPECT_EQ(ANeuralNetworksCompilation_finish(mCompilation), ANEURALNETWORKS_BAD_STATE);
}

#if 0
// TODO do more..
TEST_F(ValidationTestExecution, SetInput) {
    EXPECT_EQ(ANeuralNetworksExecution_setInput(ANeuralNetworksExecution * execution, int32_t index,
                                                const ANeuralNetworksOperandType* type,
                                                const void* buffer, size_t length),
              ANEURALNETWORKS_UNEXPECTED_NULL);
}

TEST_F(ValidationTestExecution, SetInputFromMemory) {
    EXPECT_EQ(ANeuralNetworksExecution_setInputFromMemory(ANeuralNetworksExecution * execution,
                                                          int32_t index,
                                                          const ANeuralNetworksOperandType* type,
                                                          const ANeuralNetworksMemory* buffer,
                                                          uint32_t offset),
              ANEURALNETWORKS_UNEXPECTED_NULL);
}

TEST_F(ValidationTestExecution, SetOutput) {
    EXPECT_EQ(ANeuralNetworksExecution_setOutput(ANeuralNetworksExecution * execution,
                                                 int32_t index,
                                                 const ANeuralNetworksOperandType* type,
                                                 void* buffer, size_t length),
              ANEURALNETWORKS_UNEXPECTED_NULL);
}

TEST_F(ValidationTestExecution, SetOutputFromMemory) {
    EXPECT_EQ(ANeuralNetworksExecution_setOutputFromMemory(ANeuralNetworksExecution * execution,
                                                           int32_t index,
                                                           const ANeuralNetworksOperandType* type,
                                                           const ANeuralNetworksMemory* buffer,
                                                           uint32_t offset),
              ANEURALNETWORKS_UNEXPECTED_NULL);
}

TEST_F(ValidationTestExecution, StartCompute) {
    EXPECT_EQ(ANeuralNetworksExecution_startCompute(ANeuralNetworksExecution * execution,
                                                    ANeuralNetworksEvent * *event),
              ANEURALNETWORKS_UNEXPECTED_NULL);
}

TEST_F(ValidationTestEvent, Wait) {
    EXPECT_EQ(ANeuralNetworksEvent_wait(ANeuralNetworksEvent * event),
              ANEURALNETWORKS_UNEXPECTED_NULL);
}

TEST_F(ValidationTestEvent, Free) {
    EXPECT_EQ(d ANeuralNetworksEvent_free(ANeuralNetworksEvent * event),
              ANEURALNETWORKS_UNEXPECTED_NULL);
}
#endif

}  // namespace
