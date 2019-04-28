/*
 *
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef ANDROID_TYPED_LOGGER_H
#define ANDROID_TYPED_LOGGER_H

#include <media/nbaio/NBLog.h>
#include <algorithm>

/*
Fowler-Noll-Vo (FNV-1a) hash function for the file name.
Hashes at compile time. FNV-1a iterative function:

hash = offset_basis
for each byte to be hashed
        hash = hash xor byte
        hash = hash * FNV_prime
return hash

offset_basis and FNV_prime values depend on the size of the hash output
Following values are defined by FNV and should not be changed arbitrarily
*/

template<typename T>
constexpr T offset_basis();

template<typename T>
constexpr T FNV_prime();

template<>
constexpr uint32_t offset_basis<uint32_t>() {
    return 2166136261u;
}

template<>
constexpr uint32_t FNV_prime<uint32_t>() {
    return 16777619u;
}

template<>
constexpr uint64_t offset_basis<uint64_t>() {
    return 14695981039346656037ull;
}

template<>
constexpr uint64_t FNV_prime<uint64_t>() {
    return 1099511628211ull;
}

template <typename T, size_t n>
constexpr T fnv1a(const char (&file)[n], int i = n - 1) {
    return i == -1 ? offset_basis<T>() : (fnv1a<T>(file, i - 1) ^ file[i]) * FNV_prime<T>();
}

template <size_t n>
constexpr uint64_t hash(const char (&file)[n], uint32_t line) {
    // Line numbers over or equal to 2^16 are clamped to 2^16 - 1. This way increases collisions
    // compared to wrapping around, but is easy to identify because it doesn't produce aliasing.
    // It's a very unlikely case anyways.
    return ((fnv1a<uint64_t>(file) << 16) ^ ((fnv1a<uint64_t>(file) >> 32) & 0xFFFF0000)) |
           std::min(line, 0xFFFFu);
}

// TODO Permit disabling of logging at compile-time.

// TODO A non-nullptr dummy implementation that is a nop would be faster than checking for nullptr
//      in the case when logging is enabled at compile-time and enabled at runtime, but it might be
//      slower than nullptr check when logging is enabled at compile-time and disabled at runtime.

// Write formatted entry to log
#define LOGT(fmt, ...) do { NBLog::Writer *x = tlNBLogWriter; if (x != nullptr) \
                                x->logFormat((fmt), hash(__FILE__, __LINE__), ##__VA_ARGS__); } \
                                while (0)

// Write histogram timestamp entry
#define LOG_HIST_TS() do { NBLog::Writer *x = tlNBLogWriter; if (x != nullptr) \
        x->logEventHistTs(NBLog::EVENT_HISTOGRAM_ENTRY_TS, hash(__FILE__, __LINE__)); } while(0)

// Record that audio was turned on/off
#define LOG_AUDIO_STATE() do { NBLog::Writer *x = tlNBLogWriter; if (x != nullptr) \
        x->logEventHistTs(NBLog::EVENT_AUDIO_STATE, hash(__FILE__, __LINE__)); } while(0)

namespace android {
extern "C" {
extern thread_local NBLog::Writer *tlNBLogWriter;
}
} // namespace android

#endif // ANDROID_TYPED_LOGGER_H
