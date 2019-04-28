/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 * 
 * MediaTek Inc. (C) 2010. All rights reserved.
 * 
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

#define LOG_TAG "MtkACodec"
#include <utils/Log.h>
#include <ui/GraphicBuffer.h>
#include <ui/Fence.h>
#include <media/stagefright/omx/OMXUtils.h>
#include "include/MtkACodec.h"

namespace android {

MtkACodec :: MtkACodec() {
}

MtkACodec::~MtkACodec() {
}

status_t MtkACodec::setupAudioCodec(
            status_t err, const char *mime, bool encoder, const sp<AMessage> &msg) {
    ALOGD("setupAudioCodec: mime %s, encoder %d, msg.get() %p", mime, encoder, msg.get());
#ifdef MTK_WMA_PLAYBACK_SUPPORT
    if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_WMA) ||
            !strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_WMAPRO)) {
        int32_t numChannels;
        int32_t sampleRate;
        if (!msg->findInt32("channel-count", &numChannels)
                || !msg->findInt32("sample-rate", &sampleRate)) {
            err = INVALID_OPERATION;
        } else {
            err = setupWMACodec(encoder, numChannels, sampleRate);
        }
    }
#endif
    // other audio codec configure...
#ifdef MTK_MP2_PLAYBACK_SUPPORT
    if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_MPEG_LAYER_II)) {
        ALOGD("mp2 support");
        int32_t numChannels, sampleRate;
        if (!msg->findInt32("channel-count", &numChannels)
                || !msg->findInt32("sample-rate", &sampleRate)) {
            // Since we did not always check for these, leave them optional
            // and have the decoder figure it all out.
            err = OK;
        } else {
            err = setupRawAudioFormat(
                    encoder ? kPortIndexInput : kPortIndexOutput,
                    sampleRate,
                    numChannels);
        }
    }
#endif
#ifdef MTK_AUDIO_APE_SUPPORT
    if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_APE)) {
        OMX_AUDIO_PARAM_APETYPE profile;
        InitOMXParams(&profile);
        profile.nPortIndex = OMX_DirInput;

        status_t err = mOMXNode->getParameter(
                OMX_IndexParamAudioApe, &profile, sizeof(profile));

        CHECK(msg->findInt32("ape-chl", (int32_t *)&profile.channels));
        CHECK(msg->findInt32("ape-bit-rate", (int32_t *)&profile.Bitrate));
        CHECK(msg->findInt32("ape-buffer-size", (int32_t *)&profile.SourceBufferSize));
        CHECK(msg->findInt32("sample-rate", (int32_t *)&profile.SampleRate));

        if(profile.SampleRate >0)
            profile.bps = (unsigned short) (profile.Bitrate /(profile.channels*profile.SampleRate));
        else
            profile.bps = 0;
        CHECK(msg->findInt32("ape-file-type", (int32_t *)&profile.fileversion));
        CHECK(msg->findInt32("ape-compression-type", (int32_t *)&profile.compressiontype));
        CHECK(msg->findInt32("ape-sample-per-frame", (int32_t *)&profile.blocksperframe));
        CHECK(msg->findInt32("ape-total-frame", (int32_t *)&profile.totalframes));
        CHECK(msg->findInt32("ape-final-sample", (int32_t *)&profile.finalframeblocks));

        err = mOMXNode->setParameter(
                OMX_IndexParamAudioApe, &profile, sizeof(profile));
        ///LOGD("err= %d",err);

        OMX_PARAM_PORTDEFINITIONTYPE def;
        InitOMXParams(&def);
        def.nPortIndex = OMX_DirInput;

        err = mOMXNode->getParameter(
                OMX_IndexParamPortDefinition, &def, sizeof(def));

        if(profile.SourceBufferSize != 0)
        {
          def.nBufferSize = profile.SourceBufferSize;
        }
        err = mOMXNode->setParameter(
                OMX_IndexParamPortDefinition, &def, sizeof(def));

#ifdef MTK_HIGH_RESOLUTION_AUDIO_SUPPORT
        if (profile.bps == 24)
        {
            InitOMXParams(&def);
            def.nPortIndex = OMX_DirOutput;
            err = mOMXNode->getParameter(OMX_IndexParamPortDefinition, &def, sizeof(def));

            def.nBufferSize <<= 1;
            err = mOMXNode->setParameter(OMX_IndexParamPortDefinition, &def, sizeof(def));
        }
#endif
    }
#endif
#ifdef MTK_AUDIO_ALAC_SUPPORT
    if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_ALAC)) {
        int32_t numChannels;
        int32_t sampleRate;
        if (!msg->findInt32("channel-count", &numChannels)
            || !msg->findInt32("sample-rate", &sampleRate)) {
            err = INVALID_OPERATION;
        } else {
            err = setupAlacCodec(mime, msg);
        }
    }
#endif
#ifdef MTK_AUDIO_ADPCM_SUPPORT
    if(!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_MS_ADPCM) ||
                    !strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_DVI_IMA_ADPCM)) {
        err = setupADPCMCodec(mime, msg);
    }
#endif
    if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_MSGSM)) {
        int32_t numChannels;
        int32_t sampleRate;
        if (!msg->findInt32("channel-count", &numChannels)
                || !msg->findInt32("sample-rate", &sampleRate)) {
            err = INVALID_OPERATION;
        } else {
            err = setupRawAudioFormat(kPortIndexInput, sampleRate, numChannels);
        }
    }

    return err;
}

status_t MtkACodec::getPortFormat(OMX_U32 portIndex, sp<AMessage> &notify) {
    status_t err = ACodec::getPortFormat(portIndex, notify);
    if (err != BAD_TYPE) {
        return err;
    }

    /* only Google's ACodec::getPortFormat() return BAD_VALUE(
     * Unsupported audio coding/Unsupported domain),
     * then try to get mtk port format.
     */
    const char *niceIndex = portIndex == kPortIndexInput ? "input" : "output";
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = portIndex;

    err = mOMXNode->getParameter(OMX_IndexParamPortDefinition, &def, sizeof(def));
    if (err != OK) {
        return err;
    }

    if (def.eDir != (portIndex == kPortIndexOutput ? OMX_DirOutput : OMX_DirInput)) {
        ALOGE("unexpected dir: %s(%d) on %s port", asString(def.eDir), def.eDir, niceIndex);
        return BAD_VALUE;
    }

    switch (def.eDomain) {
        case OMX_PortDomainAudio:
        {
            OMX_AUDIO_PORTDEFINITIONTYPE *audioDef = &def.format.audio;

            switch ((int)audioDef->eEncoding) {
#ifdef MTK_WMA_PLAYBACK_SUPPORT
                case OMX_AUDIO_CodingWMA:
                {
                    OMX_AUDIO_PARAM_WMATYPE params;
                    InitOMXParams(&params);
                    params.nPortIndex = portIndex;

                    CHECK_EQ((status_t)OK, mOMXNode->getParameter(
                        (OMX_INDEXTYPE)OMX_IndexParamAudioWma,
                        &params,
                        sizeof(params)));

                    notify->setInt32("channel-count", params.nChannels);
                    notify->setInt32("sample-rate", params.nSamplingRate);
                    break;
                }
#endif
#ifdef MTK_AUDIO_APE_SUPPORT
                case OMX_AUDIO_CodingAPE:
                {
                    OMX_AUDIO_PARAM_APETYPE params;
                    InitOMXParams(&params);
                    params.nPortIndex = portIndex;

                    CHECK_EQ((status_t)OK, mOMXNode->getParameter(
                        (OMX_INDEXTYPE)OMX_IndexParamAudioApe,
                        &params,
                        sizeof(params)));

                    notify->setString("mime", MEDIA_MIMETYPE_AUDIO_APE);
                    notify->setInt32("ape-chl", params.channels);
                    notify->setInt32("sample-rate", params.SampleRate);
                    break;
                }
#endif
#ifdef MTK_AUDIO_ALAC_SUPPORT
                case OMX_AUDIO_CodingALAC:
                {
                    OMX_AUDIO_PARAM_ALACTYPE params;
                    InitOMXParams(&params);
                    params.nPortIndex = portIndex;

                    CHECK_EQ((status_t)OK, mOMXNode->getParameter(
                        (OMX_INDEXTYPE)OMX_IndexParamAudioAlac,
                        &params,
                        sizeof(params)));

                    notify->setString("mime", MEDIA_MIMETYPE_AUDIO_ALAC);
                    notify->setInt32("channel-count", params.nChannels);
                    notify->setInt32("sample-rate", params.nSampleRate);
                    break;
                }
#endif
#ifdef MTK_AUDIO_ADPCM_SUPPORT
                case OMX_AUDIO_CodingADPCM:
                {
                    OMX_AUDIO_PARAM_ADPCMTYPE params;
                    InitOMXParams(&params);
                    params.nPortIndex = portIndex;

                    CHECK_EQ((status_t)OK, mOMXNode->getParameter(
                        (OMX_INDEXTYPE)OMX_IndexParamAudioAdpcm,
                        &params,
                        sizeof(params)));

                    notify->setString("mime", params.nFormatTag == WAVE_FORMAT_MS_ADPCM ?
                        MEDIA_MIMETYPE_AUDIO_MS_ADPCM : MEDIA_MIMETYPE_AUDIO_DVI_IMA_ADPCM);
                    notify->setInt32("channel-count", params.nChannelCount);
                    notify->setInt32("sample-rate", params.nSamplesPerSec);
                    notify->setInt32("block-align", params.nBlockAlign);
                    notify->setInt32("bit-per-sample", params.nBitsPerSample);
                    sp<ABuffer> buffer = new ABuffer(params.nExtendDataSize);
                    memcpy(buffer->data(), params.pExtendData, params.nExtendDataSize);
                    notify->setBuffer("extra-data-pointer", buffer);
                    break;
                }
#endif

                default:
                    ALOGE("Unsupported audio coding: %s(%d)\n",
                            asString(audioDef->eEncoding), audioDef->eEncoding);
                    return BAD_TYPE;
            }
            break;
        }

        default:
            ALOGE("Unsupported domain: %s(%d)", asString(def.eDomain), def.eDomain);
            return BAD_TYPE;
    }

    return getVendorParameters(portIndex, notify);
}

// private function members

#ifdef MTK_WMA_PLAYBACK_SUPPORT
status_t MtkACodec::setupWMACodec(
        bool encoder, int32_t numChannels, int32_t sampleRate) {
    status_t err = setupRawAudioFormat(
            encoder ? kPortIndexInput : kPortIndexOutput, sampleRate, numChannels);

    if (err != OK) {
        return err;
    }

    if (encoder) {
        ALOGW("WMA encoding is not supported.");
        return INVALID_OPERATION;
    }
#ifdef MTK_SWIP_WMAPRO
    int32_t channelMask = 0;
    mOMXNode->getParameter(OMX_IndexParamAudioWmaProfile, &channelMask, sizeof(channelMask));
    mChannelMaskPresent = true;
    mChannelMask = channelMask;
    ALOGD("WMAPro channelMask is 0x%x", channelMask);
#endif

    OMX_AUDIO_PARAM_WMATYPE def;
    InitOMXParams(&def);
    def.nPortIndex = kPortIndexInput;

    err = mOMXNode->getParameter(
            (OMX_INDEXTYPE)OMX_IndexParamAudioWma,
            &def,
            sizeof(def));

    if (err != OK) {
        return err;
    }

    def.nChannels = numChannels;
    def.nSamplingRate = sampleRate;

    return mOMXNode->setParameter((OMX_INDEXTYPE)OMX_IndexParamAudioWma, &def, sizeof(def));
}
#endif

#ifdef MTK_AUDIO_ALAC_SUPPORT
status_t MtkACodec::setupAlacCodec(const char *mime, const sp<AMessage> &msg) {
    int32_t numChannels = 0, sampleRate = 0, bitWidth = 0, numSamples = 0;

    CHECK(msg->findInt32("channel-count", &numChannels));
    CHECK(msg->findInt32("sample-rate", &sampleRate));
    ALOGD("setupAlacCodec mime %s", mime);
    status_t err = setupRawAudioFormat(kPortIndexOutput, sampleRate, numChannels);
    if (err != OK) {
        return err;
    }

    OMX_AUDIO_PARAM_ALACTYPE profileAlac;
    InitOMXParams(&profileAlac);
    profileAlac.nPortIndex = kPortIndexInput;

    err = mOMXNode->getParameter(
        (OMX_INDEXTYPE)OMX_IndexParamAudioAlac, &profileAlac, sizeof(profileAlac));
    CHECK_EQ((status_t)OK, err);

    profileAlac.nChannels   = numChannels;
    profileAlac.nSampleRate = sampleRate;
    if (msg->findInt32("number-samples", &numSamples) && numSamples > 0)
    {
        profileAlac.nSamplesPerPakt = numSamples;
    }
    if (msg->findInt32("bit-width", &bitWidth) && bitWidth > 0)
    {
        profileAlac.nBitsWidth  = bitWidth;
    }
    err = mOMXNode->setParameter(
        (OMX_INDEXTYPE)OMX_IndexParamAudioAlac, &profileAlac, sizeof(profileAlac));
    CHECK_EQ((status_t)OK, err);

    OMX_PARAM_PORTDEFINITIONTYPE inputdef, outputdef;

    InitOMXParams(&inputdef);
    inputdef.nPortIndex = OMX_DirInput;

    err = mOMXNode->getParameter(
        (OMX_INDEXTYPE)OMX_IndexParamPortDefinition, &inputdef, sizeof(inputdef));
    CHECK_EQ((status_t)OK, err);

    inputdef.nBufferSize = profileAlac.nChannels * (profileAlac.nBitsWidth >> 3) * profileAlac.nSamplesPerPakt;
    err = mOMXNode->setParameter(
        (OMX_INDEXTYPE)OMX_IndexParamPortDefinition, &inputdef, sizeof(inputdef));
    CHECK_EQ((status_t)OK, err);

    InitOMXParams(&outputdef);
    outputdef.nPortIndex = OMX_DirOutput;

    err = mOMXNode->getParameter(
        (OMX_INDEXTYPE)OMX_IndexParamPortDefinition, &outputdef, sizeof(outputdef));
    CHECK_EQ((status_t)OK, err);
    outputdef.nBufferSize = profileAlac.nChannels * 2 * profileAlac.nSamplesPerPakt;

    if (profileAlac.nBitsWidth > 16)
    {
        outputdef.nBufferSize <<= 1;
    }

    err = mOMXNode->setParameter(
        (OMX_INDEXTYPE)OMX_IndexParamPortDefinition, &outputdef, sizeof(outputdef));
    CHECK_EQ((status_t)OK, err);
    return err;
}
#endif

#ifdef MTK_AUDIO_ADPCM_SUPPORT
status_t MtkACodec::setupADPCMCodec(const char *mime, const sp<AMessage> &msg) {
    int32_t encoder;
    if (!msg->findInt32("encoder", &encoder)) {
        encoder = false;
    }

    int32_t numChannels;
    int32_t sampleRate;
    CHECK(msg->findInt32("channel-count", &numChannels));
    CHECK(msg->findInt32("sample-rate", &sampleRate));

    status_t err = setupRawAudioFormat(
        encoder ? kPortIndexInput : kPortIndexOutput, sampleRate, numChannels);

    if (err != OK) {
        return err;
    }

    OMX_AUDIO_PARAM_ADPCMTYPE def;

    if (encoder) {
        InitOMXParams(&def);
        def.nPortIndex = kPortIndexOutput;
        //uint32_t type;

        err = mOMXNode->getParameter((OMX_INDEXTYPE)OMX_IndexParamAudioAdpcm, &def, sizeof(def));
        if (err != OK) {
            return err;
        }

        def.nFormatTag = (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_MS_ADPCM)) ? WAVE_FORMAT_MS_ADPCM : WAVE_FORMAT_DVI_IMA_ADPCM;
        def.nChannelCount = numChannels;
        def.nSamplesPerSec = sampleRate;

        return mOMXNode->setParameter(
            (OMX_INDEXTYPE)OMX_IndexParamAudioAdpcm, &def, sizeof(def));
    } else {
        OMX_AUDIO_ADPCMPARAM def;
        InitOMXParams(&def);
        def.nPortIndex = kPortIndexInput;
        //uint32_t type;
        sp<ABuffer> buffer;

        err = mOMXNode->getParameter(
            (OMX_INDEXTYPE)OMX_IndexParamAudioAdpcm, &def, sizeof(def));
        if (err != OK) {
            return err;
        }

        def.nFormatTag = (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_MS_ADPCM)) ? WAVE_FORMAT_MS_ADPCM : WAVE_FORMAT_DVI_IMA_ADPCM;
        def.nChannelCount = numChannels;
        def.nSamplesPerSec = sampleRate;
        CHECK(msg->findInt32("block-align", (int32_t *)&def.nBlockAlign));
        CHECK(msg->findInt32("bit-per-sample", (int32_t *)&def.nBitsPerSample));
        if((msg->findBuffer("extra-data-pointer", &buffer)) && (buffer != NULL))
        {
           def.nExtendDataSize = buffer->size();
           memcpy(def.pExtendData,buffer->data(), def.nExtendDataSize);
        }
        return mOMXNode->setParameter(
            (OMX_INDEXTYPE)OMX_IndexParamAudioAdpcm, &def, sizeof(def));
    }
}
#endif

// Set up for MTK G711 Component.
status_t MtkACodec::setupMtkG711Codec(int32_t sampleRate, int32_t numChannels, const char *mime) {
    OMX_AUDIO_PCMMODETYPE PCMMode;
    status_t err = OK;

    PCMMode = !strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_G711_ALAW)
                                ? OMX_AUDIO_PCMModeALaw
                                :  OMX_AUDIO_PCMModeMULaw;

    OMX_AUDIO_PARAM_PCMMODETYPE def;
    InitOMXParams(&def);
    def.nPortIndex = kPortIndexInput;

    err = mOMXNode->getParameter((OMX_INDEXTYPE)OMX_IndexParamAudioPcm, &def, sizeof(def));
    if (err != OK) {
        return err;
    }

    def.nChannels = numChannels;
    def.nSamplingRate = sampleRate;
    def.ePCMMode = PCMMode;

    err = mOMXNode->setParameter((OMX_INDEXTYPE)OMX_IndexParamAudioPcm,  &def, sizeof(def));
    if (err != OK) {
        return err;
    }

    return setupRawAudioFormat(kPortIndexOutput, sampleRate, numChannels);
}

status_t MtkACodec::setupAudioBitWidth(const sp<AMessage> &msg) {
    status_t err = OK;
    int32_t bitWidth;

    OMX_AUDIO_PARAM_PCMMODETYPE params;
    InitOMXParams(&params);
    params.nPortIndex = kPortIndexOutput;

    if (msg->findInt32("bit-width", &bitWidth) &&
        !strncmp(mComponentName.c_str(), "OMX.MTK.AUDIO.", 14)) {
        err = mOMXNode->getParameter((OMX_INDEXTYPE)OMX_IndexParamAudioPcm, &params, sizeof(params));
        if (err != OK) {
            return err;
        }

        if (bitWidth > 16)
        {
#ifdef MTK_HIGH_RESOLUTION_AUDIO_SUPPORT
            ALOGD("Audio 24bit resolution: open.");
            params.nBitPerSample = 32;
#else
            params.nBitPerSample = 16;
#endif
        } else {
            params.nBitPerSample = 16;
        }
        err = mOMXNode->setParameter((OMX_INDEXTYPE)OMX_IndexParamAudioPcm, &params, sizeof(params));
        if (err != OK) {
            return err;
        }
    }
    return err;
}

}  // namespace android
