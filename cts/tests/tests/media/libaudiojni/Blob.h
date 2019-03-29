/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef ANDROID_BLOB_H
#define ANDROID_BLOB_H

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

namespace android {

// read only byte buffer like object

class BlobReadOnly {
public:
    BlobReadOnly(const void *data, size_t size, bool byReference) :
        mMem(byReference ? NULL : malloc(size)),
        mData(byReference ? data : mMem),
        mSize(size) {
        if (!byReference) {
            memcpy(mMem, data, size);
        }
    }
    ~BlobReadOnly() {
        free(mMem);
    }

private:
          void * const mMem;

public:
    const void * const mData;
          const size_t mSize;
};

// read/write byte buffer like object

class Blob {
public:
    Blob(size_t size) :
        mData(malloc(size)),
        mOffset(0),
        mSize(size),
        mMem(mData) { }

    // by reference
    Blob(void *data, size_t size) :
        mData(data),
        mOffset(0),
        mSize(size),
        mMem(NULL) { }

    ~Blob() {
        free(mMem);
    }

    void * const mData;
          size_t mOffset;
    const size_t mSize;

private:
    void * const mMem;
};

} // namespace android

#endif // ANDROID_BLOB_H
