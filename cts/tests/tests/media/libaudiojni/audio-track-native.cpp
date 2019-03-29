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
#define LOG_TAG "audio-track-native"

#include "Blob.h"
#include "Gate.h"
#include "sl-utils.h"

#include <deque>
#include <utils/Errors.h>

// Select whether to use STL shared pointer or to use Android strong pointer.
// We really don't promote any sharing of this object for its lifetime, but nevertheless could
// change the shared pointer value on the fly if desired.
#define USE_SHARED_POINTER

#ifdef USE_SHARED_POINTER
#include <memory>
template <typename T> using shared_pointer = std::shared_ptr<T>;
#else
#include <utils/RefBase.h>
template <typename T> using shared_pointer = android::sp<T>;
#endif

using namespace android;

// Must be kept in sync with Java android.media.cts.AudioTrackNative.WriteFlags
enum {
    WRITE_FLAG_BLOCKING = (1 << 0),
};

// TODO: Add a single buffer blocking write mode which does not require additional memory.
// TODO: Add internal buffer memory (e.g. use circular buffer, right now mallocs on heap).

class AudioTrackNative
#ifndef USE_SHARED_POINTER
        : public RefBase // android strong pointers require RefBase
#endif
{
public:
    AudioTrackNative() :
        mEngineObj(NULL),
        mEngine(NULL),
        mOutputMixObj(NULL),
        mPlayerObj(NULL),
        mPlay(NULL),
        mBufferQueue(NULL),
        mPlayState(SL_PLAYSTATE_STOPPED),
        mNumBuffers(0)
    { }

    ~AudioTrackNative() {
        close();
    }

    typedef std::lock_guard<std::recursive_mutex> auto_lock;

    status_t open(jint numChannels, jint channelMask,
                  jint sampleRate, jboolean useFloat, jint numBuffers) {
        close();
        auto_lock l(mLock);
        mEngineObj = OpenSLEngine();
        if (mEngineObj == NULL) {
            ALOGW("cannot create OpenSL ES engine");
            return INVALID_OPERATION;
        }

        SLresult res;
        for (;;) {
            /* Get the SL Engine Interface which is implicit */
            res = (*mEngineObj)->GetInterface(mEngineObj, SL_IID_ENGINE, (void *)&mEngine);
            if (res != SL_RESULT_SUCCESS) break;

            // Create Output Mix object to be used by player
            res = (*mEngine)->CreateOutputMix(
                    mEngine, &mOutputMixObj, 0 /* numInterfaces */,
                    NULL /* pInterfaceIds */, NULL /* pInterfaceRequired */);
            if (res != SL_RESULT_SUCCESS) break;

            // Realizing the Output Mix object in synchronous mode.
            res = (*mOutputMixObj)->Realize(mOutputMixObj, SL_BOOLEAN_FALSE /* async */);
            if (res != SL_RESULT_SUCCESS) break;

            /* Setup the data source structure for the buffer queue */
            SLDataLocator_BufferQueue bufferQueue;
            bufferQueue.locatorType = SL_DATALOCATOR_BUFFERQUEUE;
            bufferQueue.numBuffers = numBuffers;
            mNumBuffers = numBuffers;

            /* Setup the format of the content in the buffer queue */

            SLAndroidDataFormat_PCM_EX pcm;
            pcm.formatType = useFloat ? SL_ANDROID_DATAFORMAT_PCM_EX : SL_DATAFORMAT_PCM;
            pcm.numChannels = numChannels;
            pcm.sampleRate = sampleRate * 1000;
            pcm.bitsPerSample = useFloat ?
                    SL_PCMSAMPLEFORMAT_FIXED_32 : SL_PCMSAMPLEFORMAT_FIXED_16;
            pcm.containerSize = pcm.bitsPerSample;
            pcm.channelMask = channelMask;
            pcm.endianness = SL_BYTEORDER_LITTLEENDIAN;
            // additional
            pcm.representation = useFloat ? SL_ANDROID_PCM_REPRESENTATION_FLOAT
                                    : SL_ANDROID_PCM_REPRESENTATION_SIGNED_INT;
            SLDataSource audioSource;
            audioSource.pFormat = (void *)&pcm;
            audioSource.pLocator = (void *)&bufferQueue;

            /* Setup the data sink structure */
            SLDataLocator_OutputMix locator_outputmix;
            locator_outputmix.locatorType = SL_DATALOCATOR_OUTPUTMIX;
            locator_outputmix.outputMix = mOutputMixObj;

            SLDataSink audioSink;
            audioSink.pLocator = (void *)&locator_outputmix;
            audioSink.pFormat = NULL;

            SLboolean required[1];
            SLInterfaceID iidArray[1];
            required[0] = SL_BOOLEAN_TRUE;
            iidArray[0] = SL_IID_BUFFERQUEUE;

            res = (*mEngine)->CreateAudioPlayer(mEngine, &mPlayerObj,
                    &audioSource, &audioSink, 1 /* numInterfaces */, iidArray, required);
            if (res != SL_RESULT_SUCCESS) break;

            res = (*mPlayerObj)->Realize(mPlayerObj, SL_BOOLEAN_FALSE /* async */);
            if (res != SL_RESULT_SUCCESS) break;

            res = (*mPlayerObj)->GetInterface(mPlayerObj, SL_IID_PLAY, (void*)&mPlay);
            if (res != SL_RESULT_SUCCESS) break;

            res = (*mPlayerObj)->GetInterface(
                    mPlayerObj, SL_IID_BUFFERQUEUE, (void*)&mBufferQueue);
            if (res != SL_RESULT_SUCCESS) break;

            /* Setup to receive buffer queue event callbacks */
            res = (*mBufferQueue)->RegisterCallback(mBufferQueue, BufferQueueCallback, this);
            if (res != SL_RESULT_SUCCESS) break;

            // success
            break;
        }
        if (res != SL_RESULT_SUCCESS) {
            close(); // should be safe to close even with lock held
            ALOGW("open error %s", android::getSLErrStr(res));
            return INVALID_OPERATION;
        }
        return OK;
    }

    void close() {
        SLObjectItf engineObj;
        SLObjectItf outputMixObj;
        SLObjectItf playerObj;
        {
            auto_lock l(mLock);
            if (mPlay != NULL && mPlayState != SL_PLAYSTATE_STOPPED) {
                (void)stop();
            }
            // once stopped, we can unregister the callback
            if (mBufferQueue != NULL) {
                (void)(*mBufferQueue)->RegisterCallback(
                        mBufferQueue, NULL /* callback */, NULL /* *pContext */);
            }
            (void)flush();
            engineObj = mEngineObj;
            outputMixObj = mOutputMixObj;
            playerObj = mPlayerObj;
            // clear out interfaces and objects
            mPlay = NULL;
            mBufferQueue = NULL;
            mEngine = NULL;
            mPlayerObj = NULL;
            mOutputMixObj = NULL;
            mEngineObj = NULL;
            mPlayState = SL_PLAYSTATE_STOPPED;
        }
        // destroy without lock
        if (playerObj != NULL) {
            (*playerObj)->Destroy(playerObj);
        }
        if (outputMixObj != NULL) {
            (*outputMixObj)->Destroy(outputMixObj);
        }
        if (engineObj != NULL) {
            CloseSLEngine(engineObj);
        }
    }

    status_t setPlayState(SLuint32 playState) {
        auto_lock l(mLock);
        if (mPlay == NULL) {
            return INVALID_OPERATION;
        }
        SLresult res = (*mPlay)->SetPlayState(mPlay, playState);
        if (res != SL_RESULT_SUCCESS) {
            ALOGW("setPlayState %d error %s", playState, android::getSLErrStr(res));
            return INVALID_OPERATION;
        }
        mPlayState = playState;
        return OK;
    }

    SLuint32 getPlayState() {
        auto_lock l(mLock);
        if (mPlay == NULL) {
            return SL_PLAYSTATE_STOPPED;
        }
        SLuint32 playState;
        SLresult res = (*mPlay)->GetPlayState(mPlay, &playState);
        if (res != SL_RESULT_SUCCESS) {
            ALOGW("getPlayState error %s", android::getSLErrStr(res));
            return SL_PLAYSTATE_STOPPED;
        }
        return playState;
    }

    status_t getPositionInMsec(int64_t *position) {
        auto_lock l(mLock);
        if (mPlay == NULL) {
            return INVALID_OPERATION;
        }
        if (position == NULL) {
            return BAD_VALUE;
        }
        SLuint32 pos;
        SLresult res = (*mPlay)->GetPosition(mPlay, &pos);
        if (res != SL_RESULT_SUCCESS) {
            ALOGW("getPosition error %s", android::getSLErrStr(res));
            return INVALID_OPERATION;
        }
        // only lower 32 bits valid
        *position = pos;
        return OK;
    }

    status_t start() {
        return setPlayState(SL_PLAYSTATE_PLAYING);
    }

    status_t pause() {
        return setPlayState(SL_PLAYSTATE_PAUSED);
    }

    status_t stop() {
        return setPlayState(SL_PLAYSTATE_STOPPED);
    }

    status_t flush() {
        auto_lock l(mLock);
        status_t result = OK;
        if (mBufferQueue != NULL) {
            SLresult res = (*mBufferQueue)->Clear(mBufferQueue);
            if (res != SL_RESULT_SUCCESS) {
                return INVALID_OPERATION;
            }
        }

        // possible race if the engine is in the callback
        // safety is only achieved if the player is paused or stopped.
        mDeliveredQueue.clear();
        return result;
    }

    status_t write(const void *buffer, size_t size, bool isBlocking = false) {
        std::lock_guard<std::mutex> rl(mWriteLock);
        // not needed if we assume that a single thread is doing the reading
        // or we always operate in non-blocking mode.

        {
            auto_lock l(mLock);
            if (mBufferQueue == NULL) {
                return INVALID_OPERATION;
            }
            if (mDeliveredQueue.size() < mNumBuffers) {
                auto b = std::make_shared<BlobReadOnly>(buffer, size, false /* byReference */);
                mDeliveredQueue.emplace_back(b);
                (*mBufferQueue)->Enqueue(mBufferQueue, b->mData, b->mSize);
                return size;
            }
            if (!isBlocking) {
                return 0;
            }
            mWriteReady.closeGate(); // we're full.
        }
        if (mWriteReady.wait()) {
            auto_lock l(mLock);
            if (mDeliveredQueue.size() < mNumBuffers) {
                auto b = std::make_shared<BlobReadOnly>(buffer, size, false /* byReference */);
                mDeliveredQueue.emplace_back(b);
                (*mBufferQueue)->Enqueue(mBufferQueue, b->mData, b->mSize);
                return size;
            }
        }
        ALOGW("unable to deliver write");
        return 0;
    }

    void logBufferState() {
        auto_lock l(mLock);
        SLBufferQueueState state;
        SLresult res = (*mBufferQueue)->GetState(mBufferQueue, &state);
        CheckErr(res);
        ALOGD("logBufferState state.count:%d  state.playIndex:%d", state.count, state.playIndex);
    }

    size_t getBuffersPending() {
        auto_lock l(mLock);
        return mDeliveredQueue.size();
    }

private:
    void bufferQueueCallback(SLBufferQueueItf queueItf) {
        auto_lock l(mLock);
        if (queueItf != mBufferQueue) {
            ALOGW("invalid buffer queue interface, ignoring");
            return;
        }
        // logBufferState();

        // remove from delivered queue
        if (mDeliveredQueue.size()) {
            mDeliveredQueue.pop_front();
        } else {
            ALOGW("no delivered data!");
        }
        if (!mWriteReady.isOpen()) {
            mWriteReady.openGate();
        }
    }

    static void BufferQueueCallback(SLBufferQueueItf queueItf, void *pContext) {
        // naked native track
        AudioTrackNative *track = (AudioTrackNative *)pContext;
        track->bufferQueueCallback(queueItf);
    }

    SLObjectItf          mEngineObj;
    SLEngineItf          mEngine;
    SLObjectItf          mOutputMixObj;
    SLObjectItf          mPlayerObj;
    SLPlayItf            mPlay;
    SLBufferQueueItf     mBufferQueue;
    SLuint32             mPlayState;
    SLuint32             mNumBuffers;
    std::recursive_mutex mLock;           // monitor lock - locks public API methods and callback.
                                          // recursive since it may call itself through API.
    std::mutex           mWriteLock;      // write lock - for blocking mode, prevents multiple
                                          // writer threads from overlapping writes.  this is
                                          // generally unnecessary as writes occur from
                                          // one thread only.  acquire this before mLock.
    Gate                 mWriteReady;
    std::deque<std::shared_ptr<BlobReadOnly>> mDeliveredQueue; // delivered to mBufferQueue
};

/* Java static methods.
 *
 * These are not directly exposed to the user, so we can assume a valid "jtrack" handle
 * to be passed in.
 */

extern "C" jint Java_android_media_cts_AudioTrackNative_nativeTest(
    JNIEnv * /* env */, jclass /* clazz */,
    jint numChannels, jint channelMask, jint sampleRate, jboolean useFloat,
    jint msecPerBuffer, jint numBuffers)
{
    AudioTrackNative track;
    const size_t frameSize = numChannels * (useFloat ? sizeof(float) : sizeof(int16_t));
    const size_t framesPerBuffer = msecPerBuffer * sampleRate / 1000;

    status_t res;
    void *buffer = calloc(framesPerBuffer * numBuffers, frameSize);
    for (;;) {
        res = track.open(numChannels, channelMask, sampleRate, useFloat, numBuffers);
        if (res != OK) break;

        for (int i = 0; i < numBuffers; ++i) {
            track.write((char *)buffer + i * (framesPerBuffer * frameSize),
                    framesPerBuffer * frameSize);
        }

        track.logBufferState();
        res = track.start();
        if (res != OK) break;

        size_t buffers;
        while ((buffers = track.getBuffersPending()) > 0) {
            // ALOGD("outstanding buffers: %zu", buffers);
            usleep(5 * 1000 /* usec */);
        }
        res = track.stop();
        break;
    }
    track.close();
    free(buffer);
    return res;
}

extern "C" jlong Java_android_media_cts_AudioTrackNative_nativeCreateTrack(
    JNIEnv * /* env */, jclass /* clazz */)
{
    return (jlong)(new shared_pointer<AudioTrackNative>(new AudioTrackNative()));
}

extern "C" void Java_android_media_cts_AudioTrackNative_nativeDestroyTrack(
    JNIEnv * /* env */, jclass /* clazz */, jlong jtrack)
{
    delete (shared_pointer<AudioTrackNative> *)jtrack;
}

extern "C" jint Java_android_media_cts_AudioTrackNative_nativeOpen(
    JNIEnv * /* env */, jclass /* clazz */, jlong jtrack,
    jint numChannels, jint channelMask, jint sampleRate,
    jboolean useFloat, jint numBuffers)
{
    auto track = *(shared_pointer<AudioTrackNative> *)jtrack;
    if (track.get() == NULL) {
        return (jint)INVALID_OPERATION;
    }
    return (jint) track->open(numChannels,
                              channelMask,
                              sampleRate,
                              useFloat == JNI_TRUE,
                              numBuffers);
}

extern "C" void Java_android_media_cts_AudioTrackNative_nativeClose(
    JNIEnv * /* env */, jclass /* clazz */, jlong jtrack)
{
    auto track = *(shared_pointer<AudioTrackNative> *)jtrack;
    if (track.get() != NULL) {
        track->close();
    }
}

extern "C" jint Java_android_media_cts_AudioTrackNative_nativeStart(
    JNIEnv * /* env */, jclass /* clazz */, jlong jtrack)
{
    auto track = *(shared_pointer<AudioTrackNative> *)jtrack;
    if (track.get() == NULL) {
        return (jint)INVALID_OPERATION;
    }
    return (jint)track->start();
}

extern "C" jint Java_android_media_cts_AudioTrackNative_nativeStop(
    JNIEnv * /* env */, jclass /* clazz */, jlong jtrack)
{
    auto track = *(shared_pointer<AudioTrackNative> *)jtrack;
    if (track.get() == NULL) {
        return (jint)INVALID_OPERATION;
    }
    return (jint)track->stop();
}

extern "C" jint Java_android_media_cts_AudioTrackNative_nativePause(
    JNIEnv * /* env */, jclass /* clazz */, jlong jtrack)
{
    auto track = *(shared_pointer<AudioTrackNative> *)jtrack;
    if (track.get() == NULL) {
        return (jint)INVALID_OPERATION;
    }
    return (jint)track->pause();
}

extern "C" jint Java_android_media_cts_AudioTrackNative_nativeFlush(
    JNIEnv * /* env */, jclass /* clazz */, jlong jtrack)
{
    auto track = *(shared_pointer<AudioTrackNative> *)jtrack;
    if (track.get() == NULL) {
        return (jint)INVALID_OPERATION;
    }
    return (jint)track->flush();
}

extern "C" jint Java_android_media_cts_AudioTrackNative_nativeGetPositionInMsec(
    JNIEnv *env, jclass /* clazz */, jlong jtrack, jlongArray jPosition)
{
    auto track = *(shared_pointer<AudioTrackNative> *)jtrack;
    if (track.get() == NULL) {
        return (jint)INVALID_OPERATION;
    }
    int64_t pos;
    status_t res = track->getPositionInMsec(&pos);
    if (res != OK) {
        return res;
    }
    jlong *nPostition = (jlong *) env->GetPrimitiveArrayCritical(jPosition, NULL /* isCopy */);
    if (nPostition == NULL) {
        ALOGE("Unable to get array for nativeGetPositionInMsec()");
        return BAD_VALUE;
    }
    nPostition[0] = (jlong)pos;
    env->ReleasePrimitiveArrayCritical(jPosition, nPostition, 0 /* mode */);
    return OK;
}

extern "C" jint Java_android_media_cts_AudioTrackNative_nativeGetBuffersPending(
    JNIEnv * /* env */, jclass /* clazz */, jlong jtrack)
{
    auto track = *(shared_pointer<AudioTrackNative> *)jtrack;
    if (track.get() == NULL) {
        return (jint)0;
    }
    return (jint)track->getBuffersPending();
}

template <typename T>
static inline jint writeToTrack(jlong jtrack, const T *data,
    jint offsetInSamples, jint sizeInSamples, jint writeFlags)
{
    auto track = *(shared_pointer<AudioTrackNative> *)jtrack;
    if (track.get() == NULL) {
        return (jint)INVALID_OPERATION;
    }

    const bool isBlocking = writeFlags & WRITE_FLAG_BLOCKING;
    const size_t sizeInBytes = sizeInSamples * sizeof(T);
    ssize_t ret = track->write(data + offsetInSamples, sizeInBytes, isBlocking);
    return (jint)(ret > 0 ? ret / sizeof(T) : ret);
}

template <typename T>
static inline jint writeArray(JNIEnv *env, jclass /* clazz */, jlong jtrack,
        T javaAudioData, jint offsetInSamples, jint sizeInSamples, jint writeFlags)
{
    if (javaAudioData == NULL) {
        return (jint)INVALID_OPERATION;
    }

    auto cAudioData = envGetArrayElements(env, javaAudioData, NULL /* isCopy */);
    if (cAudioData == NULL) {
        ALOGE("Error retrieving source of audio data to play");
        return (jint)BAD_VALUE;
    }

    jint ret = writeToTrack(jtrack, cAudioData, offsetInSamples, sizeInSamples, writeFlags);
    envReleaseArrayElements(env, javaAudioData, cAudioData, 0 /* mode */);
    return ret;
}

extern "C" jint Java_android_media_cts_AudioTrackNative_nativeWriteByteArray(
    JNIEnv *env, jclass clazz, jlong jtrack,
    jbyteArray byteArray, jint offsetInSamples, jint sizeInSamples, jint writeFlags)
{
    ALOGV("nativeWriteByteArray(%p, %d, %d, %d)",
            byteArray, offsetInSamples, sizeInSamples, writeFlags);
    return writeArray(env, clazz, jtrack, byteArray, offsetInSamples, sizeInSamples, writeFlags);
}

extern "C" jint Java_android_media_cts_AudioTrackNative_nativeWriteShortArray(
    JNIEnv *env, jclass clazz, jlong jtrack,
    jshortArray shortArray, jint offsetInSamples, jint sizeInSamples, jint writeFlags)
{
    ALOGV("nativeWriteShortArray(%p, %d, %d, %d)",
            shortArray, offsetInSamples, sizeInSamples, writeFlags);
    return writeArray(env, clazz, jtrack, shortArray, offsetInSamples, sizeInSamples, writeFlags);
}

extern "C" jint Java_android_media_cts_AudioTrackNative_nativeWriteFloatArray(
    JNIEnv *env, jclass clazz, jlong jtrack,
    jfloatArray floatArray, jint offsetInSamples, jint sizeInSamples, jint writeFlags)
{
    ALOGV("nativeWriteFloatArray(%p, %d, %d, %d)",
            floatArray, offsetInSamples, sizeInSamples, writeFlags);
    return writeArray(env, clazz, jtrack, floatArray, offsetInSamples, sizeInSamples, writeFlags);
}
