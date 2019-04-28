/*
 * Copyright (C) 2009 The Android Open Source Project
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
#define LOG_TAG "AcodecMtkMp3"

#include <media/IOMX.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/OMXClient.h>
#include <media/stagefright/omx/OMXUtils.h>
#include <media/stagefright/MetaData.h>
#include <media/openmax/OMX_Component.h>
#include <media/openmax/OMX_Audio.h>

#include "mtkext/include/AcodecMtkMp3.h"

namespace android {

#define MP3_MULTI_FRAME_COUNT_IN_ONE_INPUTBUFFER_FOR_PURE_AUDIO 20
#define MP3_MULTI_FRAME_COUNT_IN_ONE_OUTPUTBUFFER_FOR_PURE_AUDIO 20

static const OMX_U32 kPortIndexInput = 0;
static const OMX_U32 kPortIndexOutput = 1;

////////////////////////////////////////////////////////////////////////////////

AcodecMtkMp3::AcodecMtkMp3()
{

}

AcodecMtkMp3::~AcodecMtkMp3()
{

}

status_t AcodecMtkMp3::setOmxReadMultiFrame(const sp<IOMXNode> &omxNode,
        const sp<AMessage> &msg)
{
    status_t err = BAD_VALUE;

    OMX_AUDIO_PARAM_MP3TYPE profileMp3;
    InitOMXParams(&profileMp3);
    profileMp3.nPortIndex = kPortIndexInput;
    int32_t ch = 0, saR = 0;
    if (msg->findInt32("channel-count", &ch) && msg->findInt32("sample-rate", &saR)) {
        profileMp3.nChannels=ch;
        profileMp3.nSampleRate=saR;
        err = omxNode->getParameter(
                OMX_IndexParamAudioMp3, &profileMp3, sizeof(profileMp3));

        if (err == OK) {
            err = omxNode->setParameter(
                    OMX_IndexParamAudioMp3, &profileMp3, sizeof(profileMp3));
        }
    }

    OMX_PARAM_U32TYPE defmp3;
    InitOMXParams(&defmp3);
    defmp3.nPortIndex = kPortIndexOutput;
    int32_t  isFromMP3Extractor = 0;
    if (msg->findInt32("from-mp3extractor", &isFromMP3Extractor)) {
        if(isFromMP3Extractor == 1) {
            err = omxNode->getParameter(
                    OMX_IndexVendorMtkMP3Decode, &defmp3, sizeof(defmp3));
            if (err == OK) {
                defmp3.nU32 = (OMX_U32)MP3_MULTI_FRAME_COUNT_IN_ONE_OUTPUTBUFFER_FOR_PURE_AUDIO;
                err = omxNode->setParameter(
                        OMX_IndexVendorMtkMP3Decode, &defmp3, sizeof(defmp3));
            }

            if (err == OK) {
                ALOGD("Turn on MP3-Enhance, set mp3FrameCountInBuffer %d", defmp3.nU32);
            }
        }
    } else {
        err = BAD_VALUE;
    }

    return err;
}


}  // namespace android
