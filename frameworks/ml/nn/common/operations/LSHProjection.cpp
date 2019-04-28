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

#include "LSHProjection.h"

#include "CpuExecutor.h"
#include "HalInterfaces.h"
#include "util/hash/farmhash.h"

namespace android {
namespace nn {

LSHProjection::LSHProjection(const Operation& operation,
                             std::vector<RunTimeOperandInfo>& operands) {
  input_  = GetInput(operation, operands, kInputTensor);
  weight_ = GetInput(operation, operands, kWeightTensor);
  hash_   = GetInput(operation, operands, kHashTensor);

  type_ = static_cast<LSHProjectionType>(
      getScalarData<int32_t>(*GetInput(operation, operands, kTypeParam)));

  output_ = GetOutput(operation, operands, kOutputTensor);
}

bool LSHProjection::Prepare(const Operation &operation,
                            std::vector<RunTimeOperandInfo>& operands,
                            Shape *outputShape) {
  const int num_inputs = NumInputsWithValues(operation, operands);
  NN_CHECK(num_inputs == 3 || num_inputs == 4);
  NN_CHECK_EQ(NumOutputs(operation), 1);

  const RunTimeOperandInfo *hash = GetInput(operation, operands, kHashTensor);
  NN_CHECK_EQ(NumDimensions(hash), 2);
  // Support up to 32 bits.
  NN_CHECK(SizeOfDimension(hash, 1) <= 32);

  const RunTimeOperandInfo* input = GetInput(operation, operands, kInputTensor);
  NN_CHECK(NumDimensions(input) >= 1);

  auto type = static_cast<LSHProjectionType>(
      getScalarData<int32_t>(operands[operation.inputs[kTypeParam]]));
  switch (type) {
    case LSHProjectionType_SPARSE:
      NN_CHECK(NumInputsWithValues(operation, operands) == 3);
      outputShape->dimensions = { SizeOfDimension(hash, 0) };
      break;
    case LSHProjectionType_DENSE: {
      RunTimeOperandInfo *weight = GetInput(operation, operands, kWeightTensor);
      NN_CHECK_EQ(NumInputsWithValues(operation, operands), 4);
      NN_CHECK_EQ(NumDimensions(weight), 1);
      NN_CHECK_EQ(SizeOfDimension(weight, 0), SizeOfDimension(input, 0));
      outputShape->dimensions = { SizeOfDimension(hash, 0) * SizeOfDimension(hash, 1) };
      break;
    }
    default:
      return false;
  }

  outputShape->type = OperandType::TENSOR_INT32;
  outputShape->offset = 0;
  outputShape->scale = 0.f;

  return true;
}

// Compute sign bit of dot product of hash(seed, input) and weight.
// NOTE: use float as seed, and convert it to double as a temporary solution
//       to match the trained model. This is going to be changed once the new
//       model is trained in an optimized method.
//
int running_sign_bit(const RunTimeOperandInfo* input,
                     const RunTimeOperandInfo* weight, float seed) {
  double score = 0.0;
  int input_item_bytes = sizeOfData(input->type, input->dimensions) /
      SizeOfDimension(input, 0);
  char* input_ptr = (char*)(input->buffer);

  const size_t seed_size = sizeof(float);
  const size_t key_bytes = sizeof(float) + input_item_bytes;
  std::unique_ptr<char[]> key(new char[key_bytes]);

  for (uint32_t i = 0; i < SizeOfDimension(input, 0); ++i) {
    // Create running hash id and value for current dimension.
    memcpy(key.get(), &seed, seed_size);
    memcpy(key.get() + seed_size, input_ptr, input_item_bytes);

    int64_t hash_signature = farmhash::Fingerprint64(key.get(), key_bytes);
    double running_value = static_cast<double>(hash_signature);
    input_ptr += input_item_bytes;
    if (weight->lifetime == OperandLifeTime::NO_VALUE) {
      score += running_value;
    } else {
      score += reinterpret_cast<float*>(weight->buffer)[i] * running_value;
    }
  }

  return (score > 0) ? 1 : 0;
}

void SparseLshProjection(const RunTimeOperandInfo* hash,
                         const RunTimeOperandInfo* input,
                         const RunTimeOperandInfo* weight, int32_t* out_buf) {
  int num_hash = SizeOfDimension(hash, 0);
  int num_bits = SizeOfDimension(hash, 1);
  for (int i = 0; i < num_hash; i++) {
    int32_t hash_signature = 0;
    for (int j = 0; j < num_bits; j++) {
      float seed = reinterpret_cast<float*>(hash->buffer)[i * num_bits + j];
      int bit = running_sign_bit(input, weight, seed);
      hash_signature = (hash_signature << 1) | bit;
    }
    *out_buf++ = hash_signature;
  }
}

void DenseLshProjection(const RunTimeOperandInfo* hash,
                        const RunTimeOperandInfo* input,
                        const RunTimeOperandInfo* weight, int32_t* out_buf) {
  int num_hash = SizeOfDimension(hash, 0);
  int num_bits = SizeOfDimension(hash, 1);
  for (int i = 0; i < num_hash; i++) {
    for (int j = 0; j < num_bits; j++) {
      float seed = reinterpret_cast<float*>(hash->buffer)[i * num_bits + j];
      int bit = running_sign_bit(input, weight, seed);
      *out_buf++ = bit;
    }
  }
}

bool LSHProjection::Eval() {
  int32_t* out_buf = reinterpret_cast<int32_t*>(output_->buffer);

  switch (type_) {
    case LSHProjectionType_DENSE:
      DenseLshProjection(hash_, input_, weight_, out_buf);
      break;
    case LSHProjectionType_SPARSE:
      SparseLshProjection(hash_, input_, weight_, out_buf);
      break;
    default:
      return false;
  }
  return true;
}

}  // namespace nn
}  // namespace android
