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

#ifndef _NATIVE_MEDIA_SOURCE_H_
#define _NATIVE_MEDIA_SOURCE_H_

#include <stdlib.h>
#include <stdint.h>
#include <memory>
#include <string>

#include <android/native_window_jni.h>

#include "media/NdkMediaFormat.h"
#include "media/NdkMediaExtractor.h"
#include "media/NdkMediaCodec.h"
#include "media/NdkMediaMuxer.h"

#include "native_media_utils.h"
using Utils::Thread;
using Utils::Status;

class Source {
public:
    Source(int32_t w, int32_t h, int32_t colorFormat, float fps, bool looping)
        : mWidth(w),
          mHeight(h),
          mColorFormat(colorFormat),
          mFps(fps),
          mLooping(looping),
          mBufListener(nullptr) {
    }
    virtual ~Source() = default;

    class Listener {
    public:
        virtual void onBufferAvailable(
            uint8_t *buffer, int32_t size, int64_t timeStampUs, uint32_t flags) = 0;
        virtual ~Listener() = default;
    };
    virtual Status prepare(std::shared_ptr<Listener> l, std::shared_ptr<ANativeWindow> n) = 0;
    virtual Status start() = 0;
    virtual Status stop() = 0;

protected:
    int32_t mWidth;
    int32_t mHeight;
    int32_t mColorFormat;
    float mFps;
    bool mLooping;
    std::shared_ptr<Listener> mBufListener;
    std::shared_ptr<ANativeWindow> mSurface;
};

std::shared_ptr<Source> createDecoderSource(
        int32_t w, int32_t h, int32_t colorFormat, float fps, bool looping,
        bool regulateFeedingRate, /* WA for dynamic settings */
        int sourceFileFd, off64_t sourceFileOffset, off64_t sourceFileSize);

#endif // _NATIVE_MEDIA_SOURCE_H_
