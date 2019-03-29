/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef AMEDIAOBJECTS_H_
#define AMEDIAOBJECTS_H_

#include <utils/Log.h>

#include "media/NdkMediaCrypto.h"
#include "media/NdkMediaDrm.h"
#include "media/NdkMediaExtractor.h"

namespace {

// Simple class to manage deletion of AMedia objects
class AMediaObjects {
 public:
    AMediaObjects();
    virtual ~AMediaObjects();

    void setCrypto(AMediaCrypto* const theCrypto) {
        mCrypto = theCrypto;
    }
    void setDrm(AMediaDrm* const theDrm) {
        mDrm = theDrm;
    }
    void setVideoExtractor(AMediaExtractor* const theExtractor) {
        mVideoExtractor = theExtractor;
    }
    void setAudioExtractor(AMediaExtractor* const theExtractor) {
        mAudioExtractor = theExtractor;
    }

    AMediaCrypto* getCrypto() const { return mCrypto; }
    AMediaDrm* getDrm() const { return mDrm; }
    AMediaExtractor* getAudioExtractor() const { return mAudioExtractor; }
    AMediaExtractor* getVideoExtractor() const { return mVideoExtractor; }

 private:
    AMediaCrypto *mCrypto;
    AMediaDrm* mDrm;
    AMediaExtractor* mAudioExtractor;
    AMediaExtractor* mVideoExtractor;

    // Disallow copy and assignment
    AMediaObjects(const AMediaObjects&);
    void operator=(const AMediaObjects&);
};

AMediaObjects::AMediaObjects(void) : mCrypto(NULL), mDrm(NULL),
        mAudioExtractor(NULL), mVideoExtractor(NULL) {
}

AMediaObjects::~AMediaObjects() {
    if (mCrypto) {
        AMediaCrypto_delete(mCrypto);
    }
    if (mAudioExtractor) {
        AMediaExtractor_delete(mAudioExtractor);
    }
    if (mVideoExtractor) {
        AMediaExtractor_delete(mVideoExtractor);
    }
    if (mDrm) {
        AMediaDrm_release(mDrm);
    }
}

}  // anonymous namespace
#endif  // AMEDIAOBJECTS_H_

