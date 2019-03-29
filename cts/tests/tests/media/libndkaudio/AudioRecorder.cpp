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

#include "AudioRecorder.h"

#include "AudioSink.h"
#include "SystemParams.h"
#include "OpenSLESUtils.h"

#ifndef NULL
#define NULL 0
#endif

#define ARRAYSIZE(a) sizeof((a))/sizeof((a)[0])

using namespace ndkaudio;

//static const char* const TAG = "AudioRecorder";

#define NB_BUFFERS_IN_QUEUE 1

static void RecCallback(SLRecordItf /*recorderItf_*/, void * /*context*/, SLuint32 event)
{
    if (SL_RECORDEVENT_HEADATNEWPOS & event) {
        // __android_log_print(ANDROID_LOG_INFO, TAG, "SL_RECORDEVENT_HEADATNEWPOS");
    }

    if (SL_RECORDEVENT_HEADATMARKER & event) {
        // __android_log_print(ANDROID_LOG_INFO, TAG, "SL_RECORDEVENT_HEADATMARKER");
    }

    if (SL_RECORDEVENT_BUFFER_FULL & event) {
        // __android_log_print(ANDROID_LOG_INFO, TAG, "SL_RECORDEVENT_BUFFER_FULL");
    }
}

#define BUFFER_SIZE_IN_FRAMES	8192

static float* recBuffer = NULL;

static void RecBufferQueueCallback(SLAndroidSimpleBufferQueueItf /*queueItf*/, void * context)
{
    AudioRecorder* recorder = (AudioRecorder*)context;
    // __android_log_print(ANDROID_LOG_INFO, TAG, "RecBufferQueueCallback()");
    recorder->enqueBuffer();
}

/*
 * The OpenSL ES code was derived from:
 *   frameworks/wilhelm/tests/examples/slesTestRecBuffQueue.cpp
 */
AudioRecorder::AudioRecorder()
 : sink_(NULL),
   recording_(false),
   sampleRate_(48000),
   numChannels_(0),
   numBufferSamples_(0),
   engineObj_(NULL),
   engineItf_(NULL),
   recorderObj_(NULL),
   recorderItf_(NULL),
   recBuffQueueItf_(NULL),
   configItf_(NULL)
{}

AudioRecorder::~AudioRecorder() {}

void AudioRecorder::Open(int numChannels, AudioSink* sink) {
    sink_ = sink;
    numChannels_ = numChannels;
    // __android_log_print(ANDROID_LOG_INFO, TAG, "AudioRecorder::Open() - numChannels:%d", numChannels);

    SLresult result;

    numBufferSamples_ = BUFFER_SIZE_IN_FRAMES * numChannels_;
    recBuffer = new float[numBufferSamples_];

    SLEngineOption EngineOption[] = {
            {(SLuint32) SL_ENGINEOPTION_THREADSAFE, (SLuint32) SL_BOOLEAN_TRUE}
    };

    /* Create the OpenSL ES Engine object */
    result = slCreateEngine(&engineObj_, 1, EngineOption, 0, NULL, NULL);
    assert(SL_RESULT_SUCCESS == result);
    // __android_log_print(ANDROID_LOG_INFO, TAG, "slCreateEngine() - engineObj_:%p", engineObj_);

    /* Realizing the SL Engine in synchronous mode. */
    result = (*engineObj_)->Realize(engineObj_, SL_BOOLEAN_FALSE);
    assert(SL_RESULT_SUCCESS == result);
    // __android_log_print(ANDROID_LOG_INFO, TAG, "(*engineObj_)->Realize()");

    result = (*engineObj_)->GetInterface(engineObj_, SL_IID_ENGINE, (void*)&engineItf_);
    assert(SL_RESULT_SUCCESS == result);
    // __android_log_print(ANDROID_LOG_INFO, TAG, "GetInterface() - engineItf_:%p", engineItf_);

    // Configuration of the recorder
    SLboolean required[] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
    SLInterfaceID iidArray[] = {SL_IID_ANDROIDSIMPLEBUFFERQUEUE, SL_IID_ANDROIDCONFIGURATION};

    SLDataLocator_IODevice ioDevice;
    ioDevice.locatorType = SL_DATALOCATOR_IODEVICE;
    ioDevice.deviceType = SL_IODEVICE_AUDIOINPUT;
    ioDevice.deviceID = SL_DEFAULTDEVICEID_AUDIOINPUT;
    ioDevice.device = NULL;

    SLDataSource recSource;
    recSource.pLocator = (void *) &ioDevice;
    recSource.pFormat = NULL;

    /* Setup the (OpenSL ES) data sink */
    SLDataLocator_AndroidSimpleBufferQueue recBuffQueue;
    recBuffQueue.locatorType = SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE;
    recBuffQueue.numBuffers = NB_BUFFERS_IN_QUEUE;

    SLAndroidDataFormat_PCM_EX pcm;
    pcm.formatType = SL_ANDROID_DATAFORMAT_PCM_EX;
    pcm.numChannels = numChannels_;
    pcm.sampleRate = sampleRate_ * 1000; // milliHz
    pcm.bitsPerSample = 32;
    pcm.containerSize = 32;
    pcm.channelMask = chanCountToChanMask(numChannels_);
    pcm.endianness = SL_BYTEORDER_LITTLEENDIAN;
    pcm.representation = SL_ANDROID_PCM_REPRESENTATION_FLOAT;

    SLDataSink recDest;
    recDest.pLocator = (void *) &recBuffQueue;
    recDest.pFormat = (void * ) &pcm;

    /* Create the audio recorder */
    result = (*engineItf_)->CreateAudioRecorder(engineItf_, &recorderObj_, &recSource, &recDest,
            ARRAYSIZE(iidArray), iidArray, required);
    assert(SL_RESULT_SUCCESS == result);
    // __android_log_print(ANDROID_LOG_INFO, TAG, "CreateAudioRecorder() - recorderObj_:%p", recorderObj_);
}

void AudioRecorder::Close() {
    /* Shutdown OpenSL ES */
    (*engineObj_)->Destroy(engineObj_);
    engineObj_ = 0;
}

void AudioRecorder::RealizeRecorder() {
    SLresult result;

    /* Realize the recorder in synchronous mode. */
    result = (*recorderObj_)->Realize(recorderObj_, SL_BOOLEAN_FALSE);
    assert(SL_RESULT_SUCCESS == result);
    // __android_log_print(ANDROID_LOG_INFO, TAG, "Recorder realized");

    /* Get the record interface which is implicit */
    result = (*recorderObj_)->GetInterface(recorderObj_, SL_IID_RECORD, (void*)&recorderItf_);
    assert(SL_RESULT_SUCCESS == result);
    // __android_log_print(ANDROID_LOG_INFO, TAG, "GetInterface() recorderItf_:%p", recorderItf_);

    /* Set up the recorder callback to get events during the recording */
    // __android_log_print(ANDROID_LOG_INFO, TAG, "SetMarkerPosition()");
    result = (*recorderItf_)->SetMarkerPosition(recorderItf_, 2000);
    assert(SL_RESULT_SUCCESS == result);

    // __android_log_print(ANDROID_LOG_INFO, TAG, "SetPositionUpdatePeriod()");
    result = (*recorderItf_)->SetPositionUpdatePeriod(recorderItf_, 500);
    assert(SL_RESULT_SUCCESS == result);

    // __android_log_print(ANDROID_LOG_INFO, TAG, "SetCallbackEventsMask()");
    result = (*recorderItf_)->SetCallbackEventsMask(recorderItf_, SL_RECORDEVENT_HEADATMARKER | SL_RECORDEVENT_HEADATNEWPOS);
    assert(SL_RESULT_SUCCESS == result);

    // __android_log_print(ANDROID_LOG_INFO, TAG, "RegisterCallback() - Events");
    result = (*recorderItf_)->RegisterCallback(recorderItf_, RecCallback, NULL);
    assert(SL_RESULT_SUCCESS == result);

    /* Get the buffer queue interface which was explicitly requested */
    result = (*recorderObj_)->GetInterface(recorderObj_, SL_IID_ANDROIDSIMPLEBUFFERQUEUE, (void*)&recBuffQueueItf_);
    assert(SL_RESULT_SUCCESS == result);
    // __android_log_print(ANDROID_LOG_INFO, TAG, "GetInterface() recBuffQueueItf_:%p", recBuffQueueItf_);

    result = (*recBuffQueueItf_)->RegisterCallback(recBuffQueueItf_, RecBufferQueueCallback, (void*)this);
    assert(SL_RESULT_SUCCESS == result);
    // __android_log_print(ANDROID_LOG_INFO, TAG, "RegisterCallback() - Buffers");
}

void AudioRecorder::RealizeRoutingProxy() {
    SLresult result;
    // The Config interface (for routing)
    result = (*recorderObj_)->GetInterface(recorderObj_, SL_IID_ANDROIDCONFIGURATION, (void*)&configItf_);
    // __android_log_print(ANDROID_LOG_INFO, TAG, "get Config result:%s", getSLErrStr(result));
    assert(SL_RESULT_SUCCESS == result);
}

void AudioRecorder::Start() {
    SLresult result;

    /* Enqueue buffers to map the region of memory allocated to store the recorded data */
    // __android_log_print(ANDROID_LOG_INFO, TAG, "Enqueueing buffer");
    //int bufferSizeInBytes = BUFFER_SIZE_IN_FRAMES * numChannels_ * sizeof(float);

    enqueBuffer();

    /* ------------------------------------------------------ */
    /* Start recording */
    // __android_log_print(ANDROID_LOG_INFO, TAG, "Start Recording");
    recording_ = true;
    result = (*recorderItf_)->SetRecordState(recorderItf_, SL_RECORDSTATE_RECORDING);
    assert(SL_RESULT_SUCCESS == result);
}

void AudioRecorder::Stop() {
    recording_ = false;

    SLresult result;
    result = (*recorderItf_)->SetRecordState(recorderItf_, SL_RECORDSTATE_STOPPED);
}

SLresult AudioRecorder::enqueBuffer() {
    SLresult result;
    int bufferSizeInBytes = numBufferSamples_ * sizeof(float);
    // __android_log_print(ANDROID_LOG_INFO, TAG, "Enque %d bytes", bufferSizeInBytes);
    result = (*recBuffQueueItf_)->Enqueue(recBuffQueueItf_, recBuffer, bufferSizeInBytes);
    assert(SL_RESULT_SUCCESS == result);

    return result;
}

float* AudioRecorder::GetRecordBuffer() {
    return recBuffer;
}
