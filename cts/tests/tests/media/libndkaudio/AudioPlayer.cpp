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

#include <assert.h>

#include <android/log.h>

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

#include "AudioPlayer.h"

#include "AudioSource.h"
#include "SystemParams.h"
#include "OpenSLESUtils.h"

#ifndef NULL
#define NULL 0
#endif

/*
 * OpenSL ES Stuff
 */
static const char* TAG = "AudioPlayer";

// engine interfaces
static SLObjectItf engineObject = 0;
static SLEngineItf engineItf;

// output mix interfaces
static SLObjectItf outputMixObject = 0;

// this callback handler is called every time a buffer finishes playing
static void bqPlayerCallback(SLAndroidSimpleBufferQueueItf /*bq*/, void *context)
{
    // __android_log_print(ANDROID_LOG_INFO, TAG, "bqPlayerCallback()");
    ((ndkaudio::AudioPlayer*)context)->enqueBuffer();
}

static void OpenSLEngine() {
    SLresult result;

    // create engine
    result = slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL);
    assert(SL_RESULT_SUCCESS == result);
    // __android_log_print(ANDROID_LOG_INFO, TAG, "OpenSLEngine() - engineObject:%p", engineObject);

    // realize the engine
    result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
    // __android_log_print(ANDROID_LOG_INFO, TAG, "Realize() engine result:%s", getSLErrStr(result));
   assert(SL_RESULT_SUCCESS == result);

    // get the engine interface, which is needed in order to create other objects
    result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineItf);
    // __android_log_print(ANDROID_LOG_INFO, TAG, "GetInterface() engine:%p result:%s", engineItf, getSLErrStr(result));
    assert(SL_RESULT_SUCCESS == result);

    // get the output mixer
    result = (*engineItf)->CreateOutputMix(engineItf, &outputMixObject, 0, 0, 0);
    // __android_log_print(ANDROID_LOG_INFO, TAG, "CreateOutputMix() mix:%p result:%s", outputMixObject, getSLErrStr(result));
    assert(SL_RESULT_SUCCESS == result);

    // realize the output mix
    result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
    // __android_log_print(ANDROID_LOG_INFO, TAG, "Realize() result:%s", getSLErrStr(result));
    assert(SL_RESULT_SUCCESS == result);
}

static void CloseSLEngine() {
    __android_log_print(ANDROID_LOG_INFO, TAG, "CloseSLEngine()");

    // destroy output mix object, and invalidate all associated interfaces
    if (outputMixObject != NULL) {
        (*outputMixObject)->Destroy(outputMixObject);
        outputMixObject = NULL;
    }

    if (engineObject != NULL) {
        (*engineObject)->Destroy(engineObject);
        engineObject = NULL;
        engineItf = NULL;
    }
}

/*
 * AudioPlayer
 */
namespace ndkaudio {

AudioPlayer::AudioPlayer() {
    source_ = NULL;

    sampleRate_ = SystemParams::getSampleRate();
    numChannels_ = 1;

    numPlayBuffFrames_ = SystemParams::getNumBufferFrames();

    playing_ = false;

    time_ = 0;

    bqPlayerObject_ = NULL;
    bq_ = NULL;
    bqPlayerPlay_ = NULL;
    configItf_ = NULL;

    OpenSLEngine();
}

AudioPlayer::~AudioPlayer() {
    CloseSLEngine();

    delete[] playBuff_;
    playBuff_ = 0;
}

SLresult AudioPlayer::Open(int numChannels, AudioSource* source) {
    source_ = source;

    SLresult result;

    numChannels_ = numChannels;

    int internalBuffFactor = 1;

    playBuff_ =
            new float[numPlayBuffFrames_ * numChannels_ * internalBuffFactor];
    playBuffSizeInBytes_ = numPlayBuffFrames_ * numChannels_ * sizeof(float)
            * internalBuffFactor;

    sampleRate_ = SystemParams::getSampleRate();

//    __android_log_print(ANDROID_LOG_INFO, TAG,
//                        "AudioPlayer::Open(chans:%d, rate:%d)", numChannels,
//                        sampleRate_);

    // configure audio source
    SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {
            SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,    // locatorType
            1}; // numBuffers

    // SLuint32 chanMask = SL_SPEAKER_FRONT_LEFT|SL_SPEAKER_FRONT_RIGHT;
    SLAndroidDataFormat_PCM_EX format_pcm = {SL_ANDROID_DATAFORMAT_PCM_EX,	// formatType
            (SLuint32) numChannels_,			// numChannels
            (SLuint32)(sampleRate_ * 1000),			// milliSamplesPerSec
            32,								// bitsPerSample
            32,								// containerSize;
            (SLuint32) chanCountToChanMask(numChannels_),  // channelMask
            SL_BYTEORDER_LITTLEENDIAN,  // endianness
            SL_ANDROID_PCM_REPRESENTATION_FLOAT};  // representation
    SLDataSource audioSrc = {&loc_bufq, &format_pcm};

    // configure audio sink
    SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX,
            outputMixObject};
    SLDataSink audioSnk = {&loc_outmix, NULL};

    const SLInterfaceID ids[] =
            {SL_IID_BUFFERQUEUE, SL_IID_ANDROIDCONFIGURATION};
    const SLboolean req[] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};

    // The Player
    result = (*engineItf)->CreateAudioPlayer(engineItf, &bqPlayerObject_,
                                             &audioSrc, &audioSnk,
                                             sizeof(ids) / sizeof(ids[0]), ids,
                                             req);
//    __android_log_print(ANDROID_LOG_INFO, TAG,
//                        "CreateAudioPlayer() result:%s, bqPlayerObject_:%p",
//                        getSLErrStr(result), bqPlayerObject_);
    assert(SL_RESULT_SUCCESS == result);

    return result;
}

void AudioPlayer::Close() {
    __android_log_write(ANDROID_LOG_INFO, TAG, "CloseSLPlayer()");

    if (bqPlayerObject_ != NULL) {
        (*bqPlayerObject_)->Destroy(bqPlayerObject_);
        bqPlayerObject_ = NULL;

        // invalidate any interfaces
        bqPlayerPlay_ = NULL;
        bq_ = NULL;
    }
}

SLresult AudioPlayer::RealizePlayer() {
    SLresult result;

//    __android_log_print(ANDROID_LOG_INFO, TAG, "AudioPlayer::RealizePlayer()");

    result = (*bqPlayerObject_)->Realize(bqPlayerObject_, SL_BOOLEAN_FALSE);
//    __android_log_print(ANDROID_LOG_INFO, TAG,
//                        "Realize player object result:%s", getSLErrStr(result));
    assert(SL_RESULT_SUCCESS == result);

    result = (*bqPlayerObject_)->GetInterface(bqPlayerObject_, SL_IID_PLAY,
                                              &bqPlayerPlay_);
//    __android_log_print(ANDROID_LOG_INFO, TAG,
//                        "get player interface result:%s, bqPlayerPlay_:%p",
//                        getSLErrStr(result), bqPlayerPlay_);
    assert(SL_RESULT_SUCCESS == result);

    // The BufferQueue
    result = (*bqPlayerObject_)->GetInterface(bqPlayerObject_,
                                              SL_IID_ANDROIDSIMPLEBUFFERQUEUE,
                                              &bq_);
//    __android_log_print(ANDROID_LOG_INFO, TAG,
//                        "get bufferqueue interface:%p result:%s", bq_,
//                        getSLErrStr(result));
    assert(SL_RESULT_SUCCESS == result);

    // The register BufferQueue callback
    result = (*bq_)->RegisterCallback(bq_, bqPlayerCallback, this);
//    __android_log_print(ANDROID_LOG_INFO, TAG, "register callback result:%s",
//                        getSLErrStr(result));
    assert(SL_RESULT_SUCCESS == result);

    return result;
}

SLresult AudioPlayer::RealizeRoutingProxy() {
    SLresult result;

    // The Config interface (for routing)
    result = (*bqPlayerObject_)->GetInterface(bqPlayerObject_,
                                              SL_IID_ANDROIDCONFIGURATION,
                                              (void*) &configItf_);
//    __android_log_print(ANDROID_LOG_INFO, TAG, "get Config result:%s",
//                        getSLErrStr(result));
    assert(SL_RESULT_SUCCESS == result);

    return result;
}

SLresult AudioPlayer::Start() {
    // __android_log_print(ANDROID_LOG_INFO, TAG, "Start()");
    playing_ = true;

    // set the player's state to playing
    SLresult result = (*bqPlayerPlay_)->SetPlayState(bqPlayerPlay_,
                                                     SL_PLAYSTATE_PLAYING);
    // __android_log_print(ANDROID_LOG_INFO, TAG, "SetPlayState() result:%s", getSLErrStr(result));
    assert(SL_RESULT_SUCCESS == result);

    enqueBuffer();

    return result;
}

void AudioPlayer::Stop() {
    // __android_log_print(ANDROID_LOG_INFO, TAG, "Stop()");
    playing_ = false;
}

SLresult AudioPlayer::enqueBuffer() {
    // __android_log_print(ANDROID_LOG_INFO, TAG, "AudioPlayer::enqueBuffer()");
    if (playing_) {
        //long dataSizeInSamples = source_->getData(time_++, playBuff_,
        //                                          numPlayBuffFrames_,
        //                                          source_->getNumChannels());
        return (*bq_)->Enqueue(bq_, playBuff_, playBuffSizeInBytes_);
    } else {
        (*bqPlayerPlay_)->SetPlayState(bqPlayerPlay_, SL_PLAYSTATE_STOPPED);
        return 0;
    }
}

} // namespace ndkaudio

