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

#ifndef ANDROID_ML_NN_COMMON_OPERATIONS_INTERNAL_COMMON_H_
#define ANDROID_ML_NN_COMMON_OPERATIONS_INTERNAL_COMMON_H_

#ifndef USE_NEON
#if defined(__ARM_NEON__) || defined(__ARM_NEON)
#define USE_NEON
#include <arm_neon.h>
#endif
#endif

#include "gemmlowp.h"
#include "types.h"

namespace android {
namespace nn {

template <FusedActivationFunctionType Ac>
struct ActivationFunctionImpl {};

template <>
struct ActivationFunctionImpl<FusedActivationFunctionType::kNone> {
  static float Eval(float x) { return x; }
};

template <>
struct ActivationFunctionImpl<FusedActivationFunctionType::kRelu> {
  static float Eval(float x) { return x < 0.f ? 0.f : x; }
};

template <>
struct ActivationFunctionImpl<FusedActivationFunctionType::kRelu1> {
  static float Eval(float x) { return x > 1.f ? 1.f : x < -1.f ? -1.f : x; }
};

template <>
struct ActivationFunctionImpl<FusedActivationFunctionType::kRelu6> {
  static float Eval(float x) { return x > 6.f ? 6.f : x < 0.f ? 0.f : x; }
};

template <FusedActivationFunctionType Ac>
float ActivationFunction(float x) {
  return ActivationFunctionImpl<Ac>::Eval(x);
}

inline int32 MultiplyByQuantizedMultiplierSmallerThanOne(
    int32 x, int32 quantized_multiplier, int right_shift) {
  using gemmlowp::RoundingDivideByPOT;
  using gemmlowp::SaturatingRoundingDoublingHighMul;
  return RoundingDivideByPOT(
      SaturatingRoundingDoublingHighMul(x, quantized_multiplier), right_shift);
}

inline int32 MultiplyByQuantizedMultiplierGreaterThanOne(
    int32 x, int32 quantized_multiplier, int left_shift) {
  using gemmlowp::SaturatingRoundingDoublingHighMul;
  return SaturatingRoundingDoublingHighMul(x * (1 << left_shift),
                                           quantized_multiplier);
}

}  // namespace nn
}  // namespace android

#endif  // ANDROID_ML_NN_COMMON_OPERATIONS_INTERNAL_COMMON_H_
