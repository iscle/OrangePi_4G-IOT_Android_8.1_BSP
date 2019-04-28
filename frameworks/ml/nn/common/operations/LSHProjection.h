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

#ifndef FRAMEWORKS_ML_NN_LSH_PROJECTION_H
#define FRAMEWORKS_ML_NN_LSH_PROJECTION_H

#include "Operations.h"

namespace android {
namespace hardware {
namespace neuralnetworks {
namespace V1_0 {
struct Operation;
}
}  // namespace neuralnetworks
}  // namespace hardware
}  // namespace android

namespace android {
namespace nn {

enum LSHProjectionType {
  LSHProjectionType_UNKNOWN = 0,
  LSHProjectionType_SPARSE = 1,
  LSHProjectionType_DENSE = 2,
};

struct RunTimeOperandInfo;
struct Shape;

class LSHProjection {
 public:
  LSHProjection(
      const android::hardware::neuralnetworks::V1_0::Operation &operation,
      std::vector<RunTimeOperandInfo> &operands);

  static bool Prepare(
      const android::hardware::neuralnetworks::V1_0::Operation &operation,
      std::vector<RunTimeOperandInfo>& operands,
      Shape *outputShape);
  bool Eval();

  static constexpr int kHashTensor = 0;
  static constexpr int kInputTensor = 1;
  static constexpr int kWeightTensor = 2;  // Optional

  static constexpr int kTypeParam = 3;

  static constexpr int kOutputTensor = 0;

 private:
  LSHProjectionType type_;

  const RunTimeOperandInfo *hash_;
  const RunTimeOperandInfo *input_;
  const RunTimeOperandInfo *weight_;

  RunTimeOperandInfo *output_;
};

}  // namespace nn
}  // namespace android

#endif  // FRAMEWORKS_ML_NN_LSH_PROJECTION_H
