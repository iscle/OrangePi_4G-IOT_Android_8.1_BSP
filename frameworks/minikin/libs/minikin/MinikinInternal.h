/*
 * Copyright (C) 2014 The Android Open Source Project
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

// Definitions internal to Minikin

#ifndef MINIKIN_INTERNAL_H
#define MINIKIN_INTERNAL_H

#include <hb.h>

#include <utils/Mutex.h>

#include <minikin/MinikinFont.h>

namespace minikin {

// All external Minikin interfaces are designed to be thread-safe.
// Presently, that's implemented by through a global lock, and having
// all external interfaces take that lock.

extern android::Mutex gMinikinLock;

// Aborts if gMinikinLock is not acquired. Do nothing on the release build.
void assertMinikinLocked();

hb_blob_t* getFontTable(const MinikinFont* minikinFont, uint32_t tag);

constexpr uint32_t MAX_UNICODE_CODE_POINT = 0x10FFFF;

constexpr uint32_t VS1 = 0xFE00;
constexpr uint32_t VS16 = 0xFE0F;
constexpr uint32_t VS17 = 0xE0100;
constexpr uint32_t VS256 = 0xE01EF;

// Returns variation selector index. This is one unit less than the variation selector number. For
// example, VARIATION SELECTOR-25 maps to 24.
// [0x00-0x0F] for U+FE00..U+FE0F
// [0x10-0xFF] for U+E0100..U+E01EF
// INVALID_VS_INDEX for other input.
constexpr uint16_t INVALID_VS_INDEX = 0xFFFF;
uint16_t getVsIndex(uint32_t codePoint);

// Returns true if the code point is a variation selector.
// Note that this function returns false for Mongolian free variation selectors.
bool isVariationSelector(uint32_t codePoint);

// An RAII wrapper for hb_blob_t
class HbBlob {
public:
    // Takes ownership of hb_blob_t object, caller is no longer
    // responsible for calling hb_blob_destroy().
    explicit HbBlob(hb_blob_t* blob) : mBlob(blob) {
    }

    ~HbBlob() {
        hb_blob_destroy(mBlob);
    }

    const uint8_t* get() const {
        const char* data = hb_blob_get_data(mBlob, nullptr);
        return reinterpret_cast<const uint8_t*>(data);
    }

    size_t size() const {
        return (size_t)hb_blob_get_length(mBlob);
    }

private:
    hb_blob_t* mBlob;
};

}  // namespace minikin

#endif  // MINIKIN_INTERNAL_H
