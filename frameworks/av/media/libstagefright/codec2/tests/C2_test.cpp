/*
 * Copyright 2014 The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "C2_test"

#include <gtest/gtest.h>

#include <C2.h>

namespace android {

/* ======================================= STATIC TESTS ======================================= */

template<int N>
struct c2_const_checker
{
    inline constexpr static int num() { return N; }
};

constexpr auto min_i32_i32 = c2_min(int32_t(1), int32_t(2));
static_assert(std::is_same<decltype(min_i32_i32), const int32_t>::value, "should be int32_t");
constexpr auto min_i32_i64 = c2_min(int32_t(3), int64_t(2));
static_assert(std::is_same<decltype(min_i32_i64), const int64_t>::value, "should be int64_t");
constexpr auto min_i8_i32 = c2_min(int8_t(0xff), int32_t(0xffffffff));
static_assert(std::is_same<decltype(min_i8_i32), const int32_t>::value, "should be int32_t");

static_assert(c2_const_checker<min_i32_i32>::num() == 1, "should be 1");
static_assert(c2_const_checker<min_i32_i64>::num() == 2, "should be 2");
static_assert(c2_const_checker<min_i8_i32>::num() == 0xffffffff, "should be 0xffffffff");

constexpr auto min_u32_u32 = c2_min(uint32_t(1), uint32_t(2));
static_assert(std::is_same<decltype(min_u32_u32), const uint32_t>::value, "should be uint32_t");
constexpr auto min_u32_u64 = c2_min(uint32_t(3), uint64_t(2));
static_assert(std::is_same<decltype(min_u32_u64), const uint32_t>::value, "should be uint32_t");
constexpr auto min_u32_u8 = c2_min(uint32_t(0xffffffff), uint8_t(0xff));
static_assert(std::is_same<decltype(min_u32_u8), const uint8_t>::value, "should be uint8_t");

static_assert(c2_const_checker<min_u32_u32>::num() == 1, "should be 1");
static_assert(c2_const_checker<min_u32_u64>::num() == 2, "should be 2");
static_assert(c2_const_checker<min_u32_u8>::num() == 0xff, "should be 0xff");

constexpr auto max_i32_i32 = c2_max(int32_t(1), int32_t(2));
static_assert(std::is_same<decltype(max_i32_i32), const int32_t>::value, "should be int32_t");
constexpr auto max_i32_i64 = c2_max(int32_t(3), int64_t(2));
static_assert(std::is_same<decltype(max_i32_i64), const int64_t>::value, "should be int64_t");
constexpr auto max_i8_i32 = c2_max(int8_t(0xff), int32_t(0xffffffff));
static_assert(std::is_same<decltype(max_i8_i32), const int32_t>::value, "should be int32_t");

static_assert(c2_const_checker<max_i32_i32>::num() == 2, "should be 2");
static_assert(c2_const_checker<max_i32_i64>::num() == 3, "should be 3");
static_assert(c2_const_checker<max_i8_i32>::num() == 0xffffffff, "should be 0xffffffff");

constexpr auto max_u32_u32 = c2_max(uint32_t(1), uint32_t(2));
static_assert(std::is_same<decltype(max_u32_u32), const uint32_t>::value, "should be uint32_t");
constexpr auto max_u32_u64 = c2_max(uint32_t(3), uint64_t(2));
static_assert(std::is_same<decltype(max_u32_u64), const uint64_t>::value, "should be uint64_t");
constexpr auto max_u32_u8 = c2_max(uint32_t(0x7fffffff), uint8_t(0xff));
static_assert(std::is_same<decltype(max_u32_u8), const uint32_t>::value, "should be uint32_t");

static_assert(c2_const_checker<max_u32_u32>::num() == 2, "should be 2");
static_assert(c2_const_checker<max_u32_u64>::num() == 3, "should be 3");
static_assert(c2_const_checker<max_u32_u8>::num() == 0x7fffffff, "should be 0x7fffffff");

} // namespace android
