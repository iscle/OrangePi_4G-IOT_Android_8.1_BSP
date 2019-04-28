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

/* Header-only library for various helpers of test harness
 * See frameworks/ml/nn/runtime/test/TestGenerated.cpp for how this is used.
 */
#ifndef ANDROID_ML_NN_TOOLS_TEST_GENERATOR_TEST_HARNESS_H
#define ANDROID_ML_NN_TOOLS_TEST_GENERATOR_TEST_HARNESS_H

#include <gtest/gtest.h>

#include <functional>
#include <map>
#include <tuple>
#include <vector>

namespace generated_tests {
typedef std::map<int, std::vector<float>> Float32Operands;
typedef std::map<int, std::vector<int32_t>> Int32Operands;
typedef std::map<int, std::vector<uint8_t>> Quant8Operands;
typedef std::tuple<Float32Operands,  // ANEURALNETWORKS_TENSOR_FLOAT32
                   Int32Operands,    // ANEURALNETWORKS_TENSOR_INT32
                   Quant8Operands    // ANEURALNETWORKS_TENSOR_QUANT8_ASYMM
                   >
        MixedTyped;
typedef std::pair<MixedTyped, MixedTyped> MixedTypedExampleType;

template <typename T>
struct MixedTypedIndex {};

template <>
struct MixedTypedIndex<float> {
    static constexpr size_t index = 0;
};
template <>
struct MixedTypedIndex<int32_t> {
    static constexpr size_t index = 1;
};
template <>
struct MixedTypedIndex<uint8_t> {
    static constexpr size_t index = 2;
};

// Go through all index-value pairs of a given input type
template <typename T>
inline void for_each(const MixedTyped& idx_and_data,
                     std::function<void(int, const std::vector<T>&)> execute) {
    for (auto& i : std::get<MixedTypedIndex<T>::index>(idx_and_data)) {
        execute(i.first, i.second);
    }
}

// non-const variant of for_each
template <typename T>
inline void for_each(MixedTyped& idx_and_data,
                     std::function<void(int, std::vector<T>&)> execute) {
    for (auto& i : std::get<MixedTypedIndex<T>::index>(idx_and_data)) {
        execute(i.first, i.second);
    }
}

// internal helper for for_all
template <typename T>
inline void for_all_internal(
        MixedTyped& idx_and_data,
        std::function<void(int, void*, size_t)> execute_this) {
    for_each<T>(idx_and_data, [&execute_this](int idx, std::vector<T>& m) {
        execute_this(idx, static_cast<void*>(m.data()), m.size() * sizeof(T));
    });
}

// Go through all index-value pairs of all input types
// expects a functor that takes (int index, void *raw data, size_t sz)
inline void for_all(MixedTyped& idx_and_data,
                    std::function<void(int, void*, size_t)> execute_this) {
    for_all_internal<float>(idx_and_data, execute_this);
    for_all_internal<int32_t>(idx_and_data, execute_this);
    for_all_internal<uint8_t>(idx_and_data, execute_this);
}

// Const variant of internal helper for for_all
template <typename T>
inline void for_all_internal(
        const MixedTyped& idx_and_data,
        std::function<void(int, const void*, size_t)> execute_this) {
    for_each<T>(idx_and_data, [&execute_this](int idx, const std::vector<T>& m) {
        execute_this(idx, static_cast<const void*>(m.data()), m.size() * sizeof(T));
    });
}

// Go through all index-value pairs (const variant)
// expects a functor that takes (int index, const void *raw data, size_t sz)
inline void for_all(
        const MixedTyped& idx_and_data,
        std::function<void(int, const void*, size_t)> execute_this) {
    for_all_internal<float>(idx_and_data, execute_this);
    for_all_internal<int32_t>(idx_and_data, execute_this);
    for_all_internal<uint8_t>(idx_and_data, execute_this);
}

// Helper template - resize test output per golden
template <typename ty, size_t tuple_index>
void resize_accordingly_(const MixedTyped& golden, MixedTyped& test) {
    std::function<void(int, const std::vector<ty>&)> execute =
            [&test](int index, const std::vector<ty>& m) {
                auto& t = std::get<tuple_index>(test);
                t[index].resize(m.size());
            };
    for_each<ty>(golden, execute);
}

inline void resize_accordingly(const MixedTyped& golden, MixedTyped& test) {
    resize_accordingly_<float, 0>(golden, test);
    resize_accordingly_<int32_t, 1>(golden, test);
    resize_accordingly_<uint8_t, 2>(golden, test);
}

template <typename ty, size_t tuple_index>
void filter_internal(const MixedTyped& golden, MixedTyped* filtered,
                     std::function<bool(int)> is_ignored) {
    for_each<ty>(golden,
                 [filtered, &is_ignored](int index, const std::vector<ty>& m) {
                     auto& g = std::get<tuple_index>(*filtered);
                     if (!is_ignored(index)) g[index] = m;
                 });
}

inline MixedTyped filter(const MixedTyped& golden,
                         std::function<bool(int)> is_ignored) {
    MixedTyped filtered;
    filter_internal<float, 0>(golden, &filtered, is_ignored);
    filter_internal<int32_t, 1>(golden, &filtered, is_ignored);
    filter_internal<uint8_t, 2>(golden, &filtered, is_ignored);
    return filtered;
}

// Compare results
#define VECTOR_TYPE(x) \
    typename std::tuple_element<x, MixedTyped>::type::mapped_type
#define VALUE_TYPE(x) VECTOR_TYPE(x)::value_type
template <size_t tuple_index>
void compare_(
        const MixedTyped& golden, const MixedTyped& test,
        std::function<void(VALUE_TYPE(tuple_index), VALUE_TYPE(tuple_index))>
                cmp) {
    for_each<VALUE_TYPE(tuple_index)>(
            golden,
            [&test, &cmp](int index, const VECTOR_TYPE(tuple_index) & m) {
                const auto& test_operands = std::get<tuple_index>(test);
                const auto& test_ty = test_operands.find(index);
                ASSERT_NE(test_ty, test_operands.end());
                for (unsigned int i = 0; i < m.size(); i++) {
                    SCOPED_TRACE(testing::Message()
                                 << "When comparing element " << i);
                    cmp(m[i], test_ty->second[i]);
                }
            });
}
#undef VALUE_TYPE
#undef VECTOR_TYPE
inline void compare(const MixedTyped& golden, const MixedTyped& test) {
    compare_<0>(golden, test,
                [](float g, float t) { EXPECT_NEAR(g, t, 1.e-5f); });
    compare_<1>(golden, test, [](int32_t g, int32_t t) { EXPECT_EQ(g, t); });
    compare_<2>(golden, test, [](uint8_t g, uint8_t t) { EXPECT_NEAR(g, t, 1); });
}

};  // namespace generated_tests

#endif  // ANDROID_ML_NN_TOOLS_TEST_GENERATOR_TEST_HARNESS_H
