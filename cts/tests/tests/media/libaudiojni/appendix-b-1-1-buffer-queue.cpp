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

//#define LOG_NDEBUG 0
#define LOG_TAG "OpenSL-ES-Test-B-1-1-Buffer-Queue"

#include "sl-utils.h"

/*
 * See https://www.khronos.org/registry/sles/specs/OpenSL_ES_Specification_1.0.1.pdf
 * Appendix B.1.1 sample code.
 *
 * Minor edits made to conform to Android coding style.
 *
 * Correction to code: SL_IID_VOLUME is now made optional for the mixer.
 * It isn't supported on the standard Android mixer, but it is supported on the player.
 */

#define MAX_NUMBER_INTERFACES 3

/* Local storage for Audio data in 16 bit words */
#define AUDIO_DATA_STORAGE_SIZE 4096

#define AUDIO_DATA_SEGMENTS 8

/* Audio data buffer size in 16 bit words. 8 data segments are used in
   this simple example */
#define AUDIO_DATA_BUFFER_SIZE (AUDIO_DATA_STORAGE_SIZE / AUDIO_DATA_SEGMENTS)

/* Structure for passing information to callback function */
typedef struct  {
    SLPlayItf playItf;
    SLint16  *pDataBase; // Base address of local audio data storage
    SLint16  *pData;     // Current address of local audio data storage
    SLuint32  size;
} CallbackCntxt;

/* Local storage for Audio data */
static SLint16 pcmData[AUDIO_DATA_STORAGE_SIZE];

/* Callback for Buffer Queue events */
static void BufferQueueCallback(
        SLBufferQueueItf queueItf,
        void *pContext)
{
    SLresult res;
    CallbackCntxt *pCntxt = (CallbackCntxt*)pContext;
    if (pCntxt->pData < (pCntxt->pDataBase + pCntxt->size)) {
        res = (*queueItf)->Enqueue(queueItf, (void *)pCntxt->pData,
                sizeof(SLint16) * AUDIO_DATA_BUFFER_SIZE); /* Size given in bytes. */
        ALOGE_IF(res != SL_RESULT_SUCCESS, "error: %s", android::getSLErrStr(res));
        /* Increase data pointer by buffer size */
        pCntxt->pData += AUDIO_DATA_BUFFER_SIZE;
    }
}

/* Play some music from a buffer queue */
static void TestPlayMusicBufferQueue(SLObjectItf sl)
{
    SLEngineItf EngineItf;

    SLresult res;

    SLDataSource audioSource;
    SLDataLocator_BufferQueue bufferQueue;
    SLDataFormat_PCM pcm;

    SLDataSink audioSink;
    SLDataLocator_OutputMix locator_outputmix;

    SLObjectItf player;
    SLPlayItf playItf;
    SLBufferQueueItf bufferQueueItf;
    SLBufferQueueState state;

    SLObjectItf OutputMix;
    SLVolumeItf volumeItf;

    int i;

    SLboolean required[MAX_NUMBER_INTERFACES];
    SLInterfaceID iidArray[MAX_NUMBER_INTERFACES];

    /* Callback context for the buffer queue callback function */
    CallbackCntxt cntxt;

    /* Get the SL Engine Interface which is implicit */
    res = (*sl)->GetInterface(sl, SL_IID_ENGINE, (void *)&EngineItf);
    CheckErr(res);

    /* Initialize arrays required[] and iidArray[] */
    for (i = 0; i < MAX_NUMBER_INTERFACES; i++) {
        required[i] = SL_BOOLEAN_FALSE;
        iidArray[i] = SL_IID_NULL;
    }

    // Set arrays required[] and iidArray[] for VOLUME interface
    required[0] = SL_BOOLEAN_FALSE; // ANDROID: we don't require this interface
    iidArray[0] = SL_IID_VOLUME;

#if 0
    const unsigned interfaces = 1;
#else

    /* FIXME: Android doesn't properly support optional interfaces (required == false).
    [3.1.6] When an application requests explicit interfaces during object creation,
    it can flag any interface as required. If an implementation is unable to satisfy
    the request for an interface that is not flagged as required (i.e. it is not required),
    this will not cause the object to fail creation. On the other hand, if the interface
    is flagged as required and the implementation is unable to satisfy the request
    for the interface, the object will not be created.
    */
    const unsigned interfaces = 0;
#endif
    // Create Output Mix object to be used by player
    res = (*EngineItf)->CreateOutputMix(EngineItf, &OutputMix, interfaces,
            iidArray, required);
    CheckErr(res);

    // Realizing the Output Mix object in synchronous mode.
    res = (*OutputMix)->Realize(OutputMix, SL_BOOLEAN_FALSE);
    CheckErr(res);

    volumeItf = NULL; // ANDROID: Volume interface on mix object may not be supported
    res = (*OutputMix)->GetInterface(OutputMix, SL_IID_VOLUME,
            (void *)&volumeItf);

    /* Setup the data source structure for the buffer queue */
    bufferQueue.locatorType = SL_DATALOCATOR_BUFFERQUEUE;
    bufferQueue.numBuffers = 4; /* Four buffers in our buffer queue */

    /* Setup the format of the content in the buffer queue */
    pcm.formatType = SL_DATAFORMAT_PCM;
    pcm.numChannels = 2;
    pcm.samplesPerSec = SL_SAMPLINGRATE_44_1;
    pcm.bitsPerSample = SL_PCMSAMPLEFORMAT_FIXED_16;
    pcm.containerSize = 16;
    pcm.channelMask = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
    pcm.endianness = SL_BYTEORDER_LITTLEENDIAN;
    audioSource.pFormat = (void *)&pcm;
    audioSource.pLocator = (void *)&bufferQueue;

    /* Setup the data sink structure */
    locator_outputmix.locatorType = SL_DATALOCATOR_OUTPUTMIX;
    locator_outputmix.outputMix = OutputMix;
    audioSink.pLocator = (void *)&locator_outputmix;
    audioSink.pFormat = NULL;

    /* Initialize the context for Buffer queue callbacks */
    cntxt.pDataBase = pcmData;
    cntxt.pData = cntxt.pDataBase;
    cntxt.size = sizeof(pcmData) / sizeof(pcmData[0]); // ANDROID: Bug

    /* Set arrays required[] and iidArray[] for SEEK interface
       (PlayItf is implicit) */
    required[0] = SL_BOOLEAN_TRUE;
    iidArray[0] = SL_IID_BUFFERQUEUE;

    /* Create the music player */

    res = (*EngineItf)->CreateAudioPlayer(EngineItf, &player,
            &audioSource, &audioSink, 1, iidArray, required);
    CheckErr(res);

    /* Realizing the player in synchronous mode. */
    res = (*player)->Realize(player, SL_BOOLEAN_FALSE);
    CheckErr(res);

    /* Get seek and play interfaces */
    res = (*player)->GetInterface(player, SL_IID_PLAY, (void *)&playItf);
    CheckErr(res);
    res = (*player)->GetInterface(player, SL_IID_BUFFERQUEUE,
            (void *)&bufferQueueItf);
    CheckErr(res);

    /* Setup to receive buffer queue event callbacks */
    res = (*bufferQueueItf)->RegisterCallback(bufferQueueItf,
            BufferQueueCallback, &cntxt /* BUG, was NULL */);
    CheckErr(res);

    /* Before we start set volume to -3dB (-300mB) */
    if (volumeItf != NULL) { // ANDROID: Volume interface may not be supported.
        res = (*volumeItf)->SetVolumeLevel(volumeItf, -300);
        CheckErr(res);
    }

    /* Enqueue a few buffers to get the ball rolling */
    res = (*bufferQueueItf)->Enqueue(bufferQueueItf, cntxt.pData,
            sizeof(SLint16) * AUDIO_DATA_BUFFER_SIZE); /* Size given in bytes. */
    CheckErr(res);
    cntxt.pData += AUDIO_DATA_BUFFER_SIZE;
    res = (*bufferQueueItf)->Enqueue(bufferQueueItf, cntxt.pData,
            sizeof(SLint16) * AUDIO_DATA_BUFFER_SIZE); /* Size given in bytes. */
    CheckErr(res);
    cntxt.pData += AUDIO_DATA_BUFFER_SIZE;
    res = (*bufferQueueItf)->Enqueue(bufferQueueItf, cntxt.pData,
            sizeof(SLint16) * AUDIO_DATA_BUFFER_SIZE); /* Size given in bytes. */
    CheckErr(res);
    cntxt.pData += AUDIO_DATA_BUFFER_SIZE;

    /* Play the PCM samples using a buffer queue */
    res = (*playItf)->SetPlayState(playItf, SL_PLAYSTATE_PLAYING);
    CheckErr(res);

    /* Wait until the PCM data is done playing, the buffer queue callback
       will continue to queue buffers until the entire PCM data has been
       played. This is indicated by waiting for the count member of the
       SLBufferQueueState to go to zero.
     */
    res = (*bufferQueueItf)->GetState(bufferQueueItf, &state);
    CheckErr(res);

    while (state.count) {
        usleep(5 * 1000 /* usec */); // ANDROID: avoid busy waiting
        (*bufferQueueItf)->GetState(bufferQueueItf, &state);
    }

    /* Make sure player is stopped */
    res = (*playItf)->SetPlayState(playItf, SL_PLAYSTATE_STOPPED);
    CheckErr(res);

    /* Destroy the player */
    (*player)->Destroy(player);

    /* Destroy Output Mix object */
    (*OutputMix)->Destroy(OutputMix);
}

extern "C" void Java_android_media_cts_AudioNativeTest_nativeAppendixBBufferQueue(
        JNIEnv * /* env */, jclass /* clazz */)
{
    SLObjectItf engineObject = android::OpenSLEngine();
    LOG_ALWAYS_FATAL_IF(engineObject == NULL, "cannot open OpenSL ES engine");

    TestPlayMusicBufferQueue(engineObject);
    android::CloseSLEngine(engineObject);
}
