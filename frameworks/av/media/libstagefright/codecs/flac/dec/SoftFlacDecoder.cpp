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

//#define LOG_NDEBUG 0
#define LOG_TAG "SoftFlacDecoder"
#include <utils/Log.h>

#include "SoftFlacDecoder.h"
#include <OMX_AudioExt.h>
#include <OMX_IndexExt.h>

#include <cutils/properties.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/MediaErrors.h>
#include <utils/misc.h>

namespace android {

template<class T>
static void InitOMXParams(T *params) {
    params->nSize = sizeof(T);
    params->nVersion.s.nVersionMajor = 1;
    params->nVersion.s.nVersionMinor = 0;
    params->nVersion.s.nRevision = 0;
    params->nVersion.s.nStep = 0;
}

SoftFlacDecoder::SoftFlacDecoder(
        const char *name,
        const OMX_CALLBACKTYPE *callbacks,
        OMX_PTR appData,
        OMX_COMPONENTTYPE **component)
    : SimpleSoftOMXComponent(name, callbacks, appData, component),
      mFLACDecoder(NULL),
      mHasStreamInfo(false),
      mInputBufferCount(0),
      mSignalledError(false),
      mOutputPortSettingsChange(NONE) {
    ALOGV("ctor:");
    memset(&mStreamInfo, 0, sizeof(mStreamInfo));
    initPorts();
    initDecoder();
}

SoftFlacDecoder::~SoftFlacDecoder() {
    ALOGV("dtor:");
}

void SoftFlacDecoder::initPorts() {
    ALOGV("initPorts:");
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);

    def.nPortIndex = 0;
    def.eDir = OMX_DirInput;
    def.nBufferCountMin = kNumInputBuffers;
    def.nBufferCountActual = def.nBufferCountMin;
    def.nBufferSize = 32768;
    def.bEnabled = OMX_TRUE;
    def.bPopulated = OMX_FALSE;
    def.eDomain = OMX_PortDomainAudio;
    def.bBuffersContiguous = OMX_FALSE;
    def.nBufferAlignment = 1;

    def.format.audio.cMIMEType = const_cast<char *>("audio/flac");
    def.format.audio.pNativeRender = NULL;
    def.format.audio.bFlagErrorConcealment = OMX_FALSE;
    def.format.audio.eEncoding = OMX_AUDIO_CodingFLAC;

    addPort(def);

    def.nPortIndex = 1;
    def.eDir = OMX_DirOutput;
    def.nBufferCountMin = kNumOutputBuffers;
    def.nBufferCountActual = def.nBufferCountMin;
    def.nBufferSize = 4096 * FLACDecoder::kMaxChannels;
    def.bEnabled = OMX_TRUE;
    def.bPopulated = OMX_FALSE;
    def.eDomain = OMX_PortDomainAudio;
    def.bBuffersContiguous = OMX_FALSE;
    def.nBufferAlignment = 2;

    def.format.audio.cMIMEType = const_cast<char *>("audio/raw");
    def.format.audio.pNativeRender = NULL;
    def.format.audio.bFlagErrorConcealment = OMX_FALSE;
    def.format.audio.eEncoding = OMX_AUDIO_CodingPCM;

    addPort(def);
}

void SoftFlacDecoder::initDecoder() {
    ALOGV("initDecoder:");
    mFLACDecoder = FLACDecoder::Create();
    if (mFLACDecoder == NULL) {
        ALOGE("initDecoder: failed to create FLACDecoder");
        mSignalledError = true;
    }
}

OMX_ERRORTYPE SoftFlacDecoder::initCheck() const {
    if (mSignalledError) {
        if (mFLACDecoder == NULL) {
            ALOGE("initCheck: failed due to NULL encoder");
            return OMX_ErrorDynamicResourcesUnavailable;
        }
        return OMX_ErrorUndefined;
    }

    return SimpleSoftOMXComponent::initCheck();
}

OMX_ERRORTYPE SoftFlacDecoder::internalGetParameter(
        OMX_INDEXTYPE index, OMX_PTR params) {
    ALOGV("internalGetParameter: index(%x)", index);
    switch ((OMX_U32)index) {
        case OMX_IndexParamAudioPortFormat:
        {
            OMX_AUDIO_PARAM_PORTFORMATTYPE *formatParams =
                (OMX_AUDIO_PARAM_PORTFORMATTYPE *)params;

            if (!isValidOMXParam(formatParams)) {
                return OMX_ErrorBadParameter;
            }

            if (formatParams->nPortIndex > 1) {
                return OMX_ErrorUndefined;
            }

            if (formatParams->nIndex > 0) {
                return OMX_ErrorNoMore;
            }

            formatParams->eEncoding =
                (formatParams->nPortIndex == 0)
                    ? OMX_AUDIO_CodingFLAC : OMX_AUDIO_CodingPCM;

            return OMX_ErrorNone;
        }
        case OMX_IndexParamAudioFlac:
        {
            OMX_AUDIO_PARAM_FLACTYPE *flacParams =
                (OMX_AUDIO_PARAM_FLACTYPE *)params;

            if (!isValidOMXParam(flacParams)) {
                ALOGE("internalGetParameter(OMX_IndexParamAudioFlac): invalid omx params");
                return OMX_ErrorBadParameter;
            }

            if (flacParams->nPortIndex != 0) {
                ALOGE("internalGetParameter(OMX_IndexParamAudioFlac): bad port index");
                return OMX_ErrorBadPortIndex;
            }

            flacParams->nCompressionLevel = 0;

            if (isConfigured()) {
                flacParams->nChannels = mStreamInfo.channels;
                flacParams->nSampleRate = mStreamInfo.sample_rate;
            } else {
                flacParams->nChannels = 1;
                flacParams->nSampleRate = 44100;
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamAudioPcm:
        {
            OMX_AUDIO_PARAM_PCMMODETYPE *pcmParams =
                (OMX_AUDIO_PARAM_PCMMODETYPE *)params;

            if (!isValidOMXParam(pcmParams)) {
                ALOGE("internalGetParameter(OMX_IndexParamAudioPcm): invalid omx params");
                return OMX_ErrorBadParameter;
            }

            if (pcmParams->nPortIndex != 1) {
                ALOGE("internalGetParameter(OMX_IndexParamAudioPcm): bad port index");
                return OMX_ErrorBadPortIndex;
            }

            pcmParams->eNumData = OMX_NumericalDataSigned;
            pcmParams->eEndian = OMX_EndianBig;
            pcmParams->bInterleaved = OMX_TRUE;
            pcmParams->nBitPerSample = 16;
            pcmParams->ePCMMode = OMX_AUDIO_PCMModeLinear;
            pcmParams->eChannelMapping[0] = OMX_AUDIO_ChannelLF;
            pcmParams->eChannelMapping[1] = OMX_AUDIO_ChannelRF;
            pcmParams->eChannelMapping[2] = OMX_AUDIO_ChannelCF;
            pcmParams->eChannelMapping[3] = OMX_AUDIO_ChannelLFE;
            pcmParams->eChannelMapping[4] = OMX_AUDIO_ChannelLS;
            pcmParams->eChannelMapping[5] = OMX_AUDIO_ChannelRS;

            if (isConfigured()) {
                pcmParams->nChannels = mStreamInfo.channels;
                pcmParams->nSamplingRate = mStreamInfo.sample_rate;
            } else {
                pcmParams->nChannels = 1;
                pcmParams->nSamplingRate = 44100;
            }

            return OMX_ErrorNone;
        }

        default:
            return SimpleSoftOMXComponent::internalGetParameter(index, params);
    }
}

OMX_ERRORTYPE SoftFlacDecoder::internalSetParameter(
        OMX_INDEXTYPE index, const OMX_PTR params) {
    ALOGV("internalSetParameter: index(%x)", (int)index);
    switch ((int)index) {
        case OMX_IndexParamStandardComponentRole:
        {
            const OMX_PARAM_COMPONENTROLETYPE *roleParams =
                (const OMX_PARAM_COMPONENTROLETYPE *)params;

            if (!isValidOMXParam(roleParams)) {
                return OMX_ErrorBadParameter;
            }

            if (strncmp((const char *)roleParams->cRole,
                        "audio_decoder.flac",
                        OMX_MAX_STRINGNAME_SIZE - 1) != 0) {
                return OMX_ErrorInvalidComponentName;
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamAudioPortFormat:
        {
            const OMX_AUDIO_PARAM_PORTFORMATTYPE *formatParams =
                (const OMX_AUDIO_PARAM_PORTFORMATTYPE *)params;

            if (!isValidOMXParam(formatParams)) {
                return OMX_ErrorBadParameter;
            }

            if (formatParams->nPortIndex > 1) {
                return OMX_ErrorUndefined;
            }

            if ((formatParams->nPortIndex == 0
                        && formatParams->eEncoding != OMX_AUDIO_CodingFLAC)
                || (formatParams->nPortIndex == 1
                        && formatParams->eEncoding != OMX_AUDIO_CodingPCM)) {
                return OMX_ErrorUndefined;
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamAudioPcm:
        {
            const OMX_AUDIO_PARAM_PCMMODETYPE *pcmParams =
                (OMX_AUDIO_PARAM_PCMMODETYPE *)params;

            if (!isValidOMXParam(pcmParams)) {
                return OMX_ErrorBadParameter;
            }

            if (pcmParams->nPortIndex != 1) {
                return OMX_ErrorBadPortIndex;
            }

            return OMX_ErrorNone;
        }

        default:
            return SimpleSoftOMXComponent::internalSetParameter(index, params);
    }
}

bool SoftFlacDecoder::isConfigured() const {
    return mHasStreamInfo;
}

void SoftFlacDecoder::onQueueFilled(OMX_U32 /* portIndex */) {
    ALOGV("onQueueFilled:");
    if (mSignalledError || mOutputPortSettingsChange != NONE) {
        return;
    }

    List<BufferInfo *> &inQueue = getPortQueue(0);
    List<BufferInfo *> &outQueue = getPortQueue(1);

    while (!inQueue.empty() && !outQueue.empty()) {
        BufferInfo *inInfo = *inQueue.begin();
        OMX_BUFFERHEADERTYPE *inHeader = inInfo->mHeader;
        BufferInfo *outInfo = *outQueue.begin();
        OMX_BUFFERHEADERTYPE *outHeader = outInfo->mHeader;
        uint8_t* inBuffer = inHeader->pBuffer + inHeader->nOffset;
        uint32_t inBufferLength = inHeader->nFilledLen;
        bool endOfInput = (inHeader->nFlags & OMX_BUFFERFLAG_EOS) != 0;

        if (inHeader->nFilledLen == 0) {
            if (endOfInput) {
                outHeader->nFilledLen = 0;
                outHeader->nFlags = OMX_BUFFERFLAG_EOS;
                outInfo->mOwnedByUs = false;
                outQueue.erase(outQueue.begin());
                notifyFillBufferDone(outHeader);
            } else {
                ALOGE("onQueueFilled: emptyInputBuffer received");
            }
            inInfo->mOwnedByUs = false;
            inQueue.erase(inQueue.begin());
            notifyEmptyBufferDone(inHeader);
            return;
        }
        if (mInputBufferCount == 0 && !(inHeader->nFlags & OMX_BUFFERFLAG_CODECCONFIG)) {
            ALOGE("onQueueFilled: first buffer should have OMX_BUFFERFLAG_CODECCONFIG set");
            inHeader->nFlags |= OMX_BUFFERFLAG_CODECCONFIG;
        }
        if ((inHeader->nFlags & OMX_BUFFERFLAG_CODECCONFIG) != 0) {
            status_t decoderErr = mFLACDecoder->parseMetadata(inBuffer, inBufferLength);
            mInputBufferCount++;

            if (decoderErr != OK && decoderErr != WOULD_BLOCK) {
                ALOGE("onQueueFilled: FLACDecoder parseMetaData returns error %d", decoderErr);
                mSignalledError = true;
                notify(OMX_EventError, OMX_ErrorStreamCorrupt, decoderErr, NULL);
                return;
            }

            inInfo->mOwnedByUs = false;
            inQueue.erase(inQueue.begin());
            notifyEmptyBufferDone(inHeader);

            if (decoderErr == WOULD_BLOCK) {
                continue;
            }
            mStreamInfo = mFLACDecoder->getStreamInfo();
            mHasStreamInfo = true;

            // Only send out port settings changed event if both sample rate
            // and numChannels are valid.
            if (mStreamInfo.sample_rate && mStreamInfo.channels) {
                ALOGD("onQueueFilled: initially configuring decoder: %d Hz, %d channels",
                    mStreamInfo.sample_rate, mStreamInfo.channels);

                notify(OMX_EventPortSettingsChanged, 1, 0, NULL);
                mOutputPortSettingsChange = AWAITING_DISABLED;
            }
            return;
        }

        short *outBuffer =
                reinterpret_cast<short *>(outHeader->pBuffer + outHeader->nOffset);
        size_t outBufferSize = outHeader->nAllocLen - outHeader->nOffset;

        status_t decoderErr = mFLACDecoder->decodeOneFrame(
                inBuffer, inBufferLength, outBuffer, &outBufferSize);
        if (decoderErr != OK) {
            ALOGE("onQueueFilled: FLACDecoder decodeOneFrame returns error %d", decoderErr);
            mSignalledError = true;
            notify(OMX_EventError, OMX_ErrorStreamCorrupt, decoderErr, NULL);
            return;
        }

        mInputBufferCount++;
        int64_t ts = inHeader->nTimeStamp;
        inInfo->mOwnedByUs = false;
        inQueue.erase(inQueue.begin());
        notifyEmptyBufferDone(inHeader);

        if (endOfInput) {
            outHeader->nFlags = OMX_BUFFERFLAG_EOS;
        } else if (outBufferSize == 0) {
            continue;
        } else {
            outHeader->nFlags = 0;
        }

        outHeader->nFilledLen = outBufferSize;
        outHeader->nTimeStamp = ts;

        outInfo->mOwnedByUs = false;
        outQueue.erase(outQueue.begin());
        notifyFillBufferDone(outHeader);
    }
}

void SoftFlacDecoder::onPortFlushCompleted(OMX_U32 portIndex) {
    ALOGV("onPortFlushCompleted: portIndex(%u)", portIndex);
    if (portIndex == 0) {
        drainDecoder();
    }
}

void SoftFlacDecoder::drainDecoder() {
    mFLACDecoder->flush();
}

void SoftFlacDecoder::onReset() {
    drainDecoder();

    memset(&mStreamInfo, 0, sizeof(mStreamInfo));
    mHasStreamInfo = false;
    mInputBufferCount = 0;
    mSignalledError = false;
    mOutputPortSettingsChange = NONE;
}

void SoftFlacDecoder::onPortEnableCompleted(OMX_U32 portIndex, bool enabled) {
    ALOGV("onPortEnableCompleted: portIndex(%u), enabled(%d)", portIndex, enabled);
    if (portIndex != 1) {
        return;
    }

    switch (mOutputPortSettingsChange) {
        case NONE:
            break;

        case AWAITING_DISABLED:
        {
            CHECK(!enabled);
            mOutputPortSettingsChange = AWAITING_ENABLED;
            PortInfo *info = editPortInfo(1 /* portIndex */);
            if (!info->mDef.bEnabled) {
                info->mDef.nBufferSize = mStreamInfo.max_blocksize * mStreamInfo.channels * 2;
            }
            break;
        }

        default:
        {
            CHECK_EQ((int)mOutputPortSettingsChange, (int)AWAITING_ENABLED);
            CHECK(enabled);
            mOutputPortSettingsChange = NONE;
            break;
        }
    }
}

}  // namespace android

android::SoftOMXComponent *createSoftOMXComponent(
        const char *name, const OMX_CALLBACKTYPE *callbacks,
        OMX_PTR appData, OMX_COMPONENTTYPE **component) {
    ALOGV("createSoftOMXComponent: flac decoder");
    return new android::SoftFlacDecoder(name, callbacks, appData, component);
}
