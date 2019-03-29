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
#define LOG_TAG "audio-record-native"

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

// Must be kept in sync with Java android.media.cts.AudioRecordNative.ReadFlags
enum {
    READ_FLAG_BLOCKING = (1 << 0),
};

// buffer queue buffers on the OpenSL ES side.
// The choice can be >= 1.  There is also internal buffering by AudioRecord.

static const size_t BUFFER_SIZE_MSEC = 20;

// TODO: Add a single buffer blocking read mode which does not require additional memory.
// TODO: Add internal buffer memory (e.g. use circular buffer, right now mallocs on heap).

class AudioRecordNative
#ifndef USE_SHARED_POINTER
        : public RefBase // android strong pointers require RefBase
#endif
{
public:
    AudioRecordNative() :
        mEngineObj(NULL),
        mEngine(NULL),
        mRecordObj(NULL),
        mRecord(NULL),
        mBufferQueue(NULL),
        mRecordState(SL_RECORDSTATE_STOPPED),
        mBufferSize(0),
        mNumBuffers(0)
    { }

    ~AudioRecordNative() {
        close();
    }

    typedef std::lock_guard<std::recursive_mutex> auto_lock;

    status_t open(uint32_t numChannels,
                  uint32_t channelMask,
                  uint32_t sampleRate,
                  bool useFloat,
                  uint32_t numBuffers) {
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

            SLDataLocator_IODevice locator_mic;
            /* Setup the data source structure */
            locator_mic.locatorType = SL_DATALOCATOR_IODEVICE;
            locator_mic.deviceType = SL_IODEVICE_AUDIOINPUT;
            locator_mic.deviceID = SL_DEFAULTDEVICEID_AUDIOINPUT;
            locator_mic.device= NULL;
            SLDataSource audioSource;
            audioSource.pLocator = (void *)&locator_mic;
            audioSource.pFormat = NULL;

            // FIXME: Android requires SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE
            // because the recorder makes the distinction from SL_DATALOCATOR_BUFFERQUEUE
            // which the player does not.
            SLDataLocator_AndroidSimpleBufferQueue loc_bq = {
                    SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, numBuffers
            };
#if 0
            SLDataFormat_PCM pcm = {
                    SL_DATAFORMAT_PCM, 1, SL_SAMPLINGRATE_16,
                    SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
                    SL_SPEAKER_FRONT_LEFT, SL_BYTEORDER_LITTLEENDIAN
            };
#else
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
#endif
            SLDataSink audioSink;
            audioSink = { &loc_bq, &pcm };

            SLboolean required[2];
            SLInterfaceID iidArray[2];
            /* Request the AndroidSimpleBufferQueue and AndroidConfiguration interfaces */
            required[0] = SL_BOOLEAN_TRUE;
            iidArray[0] = SL_IID_ANDROIDSIMPLEBUFFERQUEUE;
            required[1] = SL_BOOLEAN_TRUE;
            iidArray[1] = SL_IID_ANDROIDCONFIGURATION;

            ALOGV("creating recorder");
            /* Create audio recorder */
            res = (*mEngine)->CreateAudioRecorder(mEngine, &mRecordObj,
                    &audioSource, &audioSink, 2, iidArray, required);
            if (res != SL_RESULT_SUCCESS) break;

            ALOGV("realizing recorder");
            /* Realizing the recorder in synchronous mode. */
            res = (*mRecordObj)->Realize(mRecordObj, SL_BOOLEAN_FALSE /* async */);
            if (res != SL_RESULT_SUCCESS) break;

            ALOGV("geting record interface");
            /* Get the RECORD interface - it is an implicit interface */
            res = (*mRecordObj)->GetInterface(mRecordObj, SL_IID_RECORD, (void *)&mRecord);
            if (res != SL_RESULT_SUCCESS) break;

            ALOGV("geting buffer queue interface");
            /* Get the buffer queue interface which was explicitly requested */
            res = (*mRecordObj)->GetInterface(mRecordObj, SL_IID_ANDROIDSIMPLEBUFFERQUEUE,
                    (void *)&mBufferQueue);
            if (res != SL_RESULT_SUCCESS) break;

            ALOGV("registering buffer queue interface");
            /* Setup to receive buffer queue event callbacks */
            res = (*mBufferQueue)->RegisterCallback(mBufferQueue, BufferQueueCallback, this);
            if (res != SL_RESULT_SUCCESS) break;

            mBufferSize = (BUFFER_SIZE_MSEC * sampleRate / 1000)
                    * numChannels * (useFloat ? sizeof(float) : sizeof(int16_t));
            mNumBuffers = numBuffers;
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
        SLObjectItf recordObj;
        {
            auto_lock l(mLock);
            (void)stop();
            // once stopped, we can unregister the callback
            if (mBufferQueue != NULL) {
                (void)(*mBufferQueue)->RegisterCallback(
                        mBufferQueue, NULL /* callback */, NULL /* *pContext */);
            }
            (void)flush();
            engineObj = mEngineObj;
            recordObj = mRecordObj;
            // clear out interfaces and objects
            mRecord = NULL;
            mBufferQueue = NULL;
            mEngine = NULL;
            mRecordObj = NULL;
            mEngineObj = NULL;
            mRecordState = SL_RECORDSTATE_STOPPED;
            mBufferSize = 0;
            mNumBuffers = 0;
        }
        // destroy without lock
        if (recordObj != NULL) {
            (*recordObj)->Destroy(recordObj);
        }
        if (engineObj) {
            CloseSLEngine(engineObj);
        }
    }

    status_t setRecordState(SLuint32 recordState) {
        auto_lock l(mLock);
        if (mRecord == NULL) {
            return INVALID_OPERATION;
        }
        if (recordState == SL_RECORDSTATE_RECORDING) {
            queueBuffers();
        }
        SLresult res = (*mRecord)->SetRecordState(mRecord, recordState);
        if (res != SL_RESULT_SUCCESS) {
            ALOGW("setRecordState %d error %s", recordState, android::getSLErrStr(res));
            return INVALID_OPERATION;
        }
        mRecordState = recordState;
        return OK;
    }

    SLuint32 getRecordState() {
        auto_lock l(mLock);
        if (mRecord == NULL) {
            return SL_RECORDSTATE_STOPPED;
        }
        SLuint32 recordState;
        SLresult res = (*mRecord)->GetRecordState(mRecord, &recordState);
        if (res != SL_RESULT_SUCCESS) {
            ALOGW("getRecordState error %s", android::getSLErrStr(res));
            return SL_RECORDSTATE_STOPPED;
        }
        return recordState;
    }

    status_t getPositionInMsec(int64_t *position) {
        auto_lock l(mLock);
        if (mRecord == NULL) {
            return INVALID_OPERATION;
        }
        if (position == NULL) {
            return BAD_VALUE;
        }
        SLuint32 pos;
        SLresult res = (*mRecord)->GetPosition(mRecord, &pos);
        if (res != SL_RESULT_SUCCESS) {
            ALOGW("getPosition error %s", android::getSLErrStr(res));
            return INVALID_OPERATION;
        }
        // only lower 32 bits valid
        *position = pos;
        return OK;
    }

    status_t start() {
        return setRecordState(SL_RECORDSTATE_RECORDING);
    }

    status_t pause() {
        return setRecordState(SL_RECORDSTATE_PAUSED);
    }

    status_t stop() {
        return setRecordState(SL_RECORDSTATE_STOPPED);
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
        mReadyQueue.clear();
        // possible race if the engine is in the callback
        // safety is only achieved if the recorder is paused or stopped.
        mDeliveredQueue.clear();
        mReadBlob = NULL;
        mReadReady.terminate();
        return result;
    }

    ssize_t read(void *buffer, size_t size, bool blocking = false) {
        std::lock_guard<std::mutex> rl(mReadLock);
        // not needed if we assume that a single thread is doing the reading
        // or we always operate in non-blocking mode.

        ALOGV("reading:%p  %zu", buffer, size);
        size_t copied;
        std::shared_ptr<Blob> blob;
        {
            auto_lock l(mLock);
            if (mEngine == NULL) {
                return INVALID_OPERATION;
            }
            size_t osize = size;
            while (!mReadyQueue.empty() && size > 0) {
                auto b = mReadyQueue.front();
                size_t tocopy = min(size, b->mSize - b->mOffset);
                // ALOGD("buffer:%p  size:%zu  b->mSize:%zu  b->mOffset:%zu tocopy:%zu ",
                //        buffer, size, b->mSize, b->mOffset, tocopy);
                memcpy(buffer, (char *)b->mData + b->mOffset, tocopy);
                buffer = (char *)buffer + tocopy;
                size -= tocopy;
                b->mOffset += tocopy;
                if (b->mOffset == b->mSize) {
                    mReadyQueue.pop_front();
                }
            }
            copied = osize - size;
            if (!blocking || size == 0 || mReadBlob.get() != NULL) {
                return copied;
            }
            blob = std::make_shared<Blob>(buffer, size);
            mReadBlob = blob;
            mReadReady.closeGate(); // the callback will open gate when read is completed.
        }
        if (mReadReady.wait()) {
            // success then the blob is ours with valid data otherwise a flush has occurred
            // and we return a short count.
            copied += blob->mOffset;
        }
        return copied;
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
        return mReadyQueue.size();
    }

private:
    status_t queueBuffers() {
        if (mBufferQueue == NULL) {
            return INVALID_OPERATION;
        }
        if (mReadyQueue.size() + mDeliveredQueue.size() < mNumBuffers) {
            // add new empty buffer
            auto b = std::make_shared<Blob>(mBufferSize);
            mDeliveredQueue.emplace_back(b);
            (*mBufferQueue)->Enqueue(mBufferQueue, b->mData, b->mSize);
        }
        return OK;
    }

    void bufferQueueCallback(SLBufferQueueItf queueItf) {
        auto_lock l(mLock);
        if (queueItf != mBufferQueue) {
            ALOGW("invalid buffer queue interface, ignoring");
            return;
        }
        // logBufferState();

        // remove from delivered queue
        if (mDeliveredQueue.size()) {
            auto b = mDeliveredQueue.front();
            mDeliveredQueue.pop_front();
            if (mReadBlob.get() != NULL) {
                size_t tocopy = min(mReadBlob->mSize - mReadBlob->mOffset, b->mSize - b->mOffset);
                memcpy((char *)mReadBlob->mData + mReadBlob->mOffset,
                        (char *)b->mData + b->mOffset, tocopy);
                b->mOffset += tocopy;
                mReadBlob->mOffset += tocopy;
                if (mReadBlob->mOffset == mReadBlob->mSize) {
                    mReadBlob = NULL;      // we're done, clear our reference.
                    mReadReady.openGate(); // allow read to continue.
                }
                if (b->mOffset == b->mSize) {
                    b = NULL;
                }
            }
            if (b.get() != NULL) {
                if (mReadyQueue.size() + mDeliveredQueue.size() < mNumBuffers) {
                    mReadyQueue.emplace_back(b); // save onto ready queue for future reads
                } else {
                    ALOGW("dropping data");
                }
            }
        } else {
            ALOGW("no delivered data!");
        }
        queueBuffers();
    }

    static void BufferQueueCallback(SLBufferQueueItf queueItf, void *pContext) {
        // naked native record
        AudioRecordNative *record = (AudioRecordNative *)pContext;
        record->bufferQueueCallback(queueItf);
    }

    SLObjectItf           mEngineObj;
    SLEngineItf           mEngine;
    SLObjectItf           mRecordObj;
    SLRecordItf           mRecord;
    SLBufferQueueItf      mBufferQueue;
    SLuint32              mRecordState;
    size_t                mBufferSize;
    size_t                mNumBuffers;
    std::recursive_mutex  mLock;          // monitor lock - locks public API methods and callback.
                                          // recursive since it may call itself through API.
    std::mutex            mReadLock;      // read lock - for blocking mode, prevents multiple
                                          // reader threads from overlapping reads.  this is
                                          // generally unnecessary as reads occur from
                                          // one thread only.  acquire this before mLock.
    std::shared_ptr<Blob> mReadBlob;
    Gate                  mReadReady;
    std::deque<std::shared_ptr<Blob>> mReadyQueue;     // ready for read.
    std::deque<std::shared_ptr<Blob>> mDeliveredQueue; // delivered to BufferQueue
};

/* Java static methods.
 *
 * These are not directly exposed to the user, so we can assume a valid "jrecord" handle
 * to be passed in.
 */

extern "C" jint Java_android_media_cts_AudioRecordNative_nativeTest(
        JNIEnv * /* env */, jclass /* clazz */,
        jint numChannels, jint channelMask, jint sampleRate,
        jboolean useFloat, jint msecPerBuffer, jint numBuffers) {
    AudioRecordNative record;
    const size_t frameSize = numChannels * (useFloat ? sizeof(float) : sizeof(int16_t));
    const size_t framesPerBuffer = msecPerBuffer * sampleRate / 1000;

    status_t res;
    void *buffer = calloc(framesPerBuffer * numBuffers, frameSize);
    for (;;) {
        res = record.open(numChannels, channelMask, sampleRate, useFloat, numBuffers);
        if (res != OK) break;

        record.logBufferState();
        res = record.start();
        if (res != OK) break;

        size_t size = framesPerBuffer * numBuffers * frameSize;
        for (size_t offset = 0; size - offset > 0; ) {
            ssize_t amount = record.read((char *)buffer + offset, size -offset);
            // ALOGD("read amount: %zd", amount);
            if (amount < 0) break;
            offset += amount;
            usleep(5 * 1000 /* usec */);
        }

        res = record.stop();
        break;
    }
    record.close();
    free(buffer);
    return res;
}

extern "C" jlong Java_android_media_cts_AudioRecordNative_nativeCreateRecord(
    JNIEnv * /* env */, jclass /* clazz */)
{
    return (jlong)(new shared_pointer<AudioRecordNative>(new AudioRecordNative()));
}

extern "C" void Java_android_media_cts_AudioRecordNative_nativeDestroyRecord(
    JNIEnv * /* env */, jclass /* clazz */, jlong jrecord)
{
    delete (shared_pointer<AudioRecordNative> *)jrecord;
}

extern "C" jint Java_android_media_cts_AudioRecordNative_nativeOpen(
        JNIEnv * /* env */, jclass /* clazz */, jlong jrecord,
        jint numChannels, jint channelMask, jint sampleRate, jboolean useFloat, jint numBuffers)
{
    auto record = *(shared_pointer<AudioRecordNative> *)jrecord;
    if (record.get() == NULL) {
        return (jint)INVALID_OPERATION;
    }
    return (jint) record->open(numChannels, channelMask, sampleRate, useFloat == JNI_TRUE,
            numBuffers);
}

extern "C" void Java_android_media_cts_AudioRecordNative_nativeClose(
    JNIEnv * /* env */, jclass /* clazz */, jlong jrecord)
{
    auto record = *(shared_pointer<AudioRecordNative> *)jrecord;
    if (record.get() != NULL) {
        record->close();
    }
}

extern "C" jint Java_android_media_cts_AudioRecordNative_nativeStart(
    JNIEnv * /* env */, jclass /* clazz */, jlong jrecord)
{
    auto record = *(shared_pointer<AudioRecordNative> *)jrecord;
    if (record.get() == NULL) {
        return (jint)INVALID_OPERATION;
    }
    return (jint)record->start();
}

extern "C" jint Java_android_media_cts_AudioRecordNative_nativeStop(
    JNIEnv * /* env */, jclass /* clazz */, jlong jrecord)
{
    auto record = *(shared_pointer<AudioRecordNative> *)jrecord;
    if (record.get() == NULL) {
        return (jint)INVALID_OPERATION;
    }
    return (jint)record->stop();
}

extern "C" jint Java_android_media_cts_AudioRecordNative_nativePause(
    JNIEnv * /* env */, jclass /* clazz */, jlong jrecord)
{
    auto record = *(shared_pointer<AudioRecordNative> *)jrecord;
    if (record.get() == NULL) {
        return (jint)INVALID_OPERATION;
    }
    return (jint)record->pause();
}

extern "C" jint Java_android_media_cts_AudioRecordNative_nativeFlush(
    JNIEnv * /* env */, jclass /* clazz */, jlong jrecord)
{
    auto record = *(shared_pointer<AudioRecordNative> *)jrecord;
    if (record.get() == NULL) {
        return (jint)INVALID_OPERATION;
    }
    return (jint)record->flush();
}

extern "C" jint Java_android_media_cts_AudioRecordNative_nativeGetPositionInMsec(
    JNIEnv *env, jclass /* clazz */, jlong jrecord, jlongArray jPosition)
{
    auto record = *(shared_pointer<AudioRecordNative> *)jrecord;
    if (record.get() == NULL) {
        return (jint)INVALID_OPERATION;
    }
    int64_t pos;
    status_t res = record->getPositionInMsec(&pos);
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


extern "C" jint Java_android_media_cts_AudioRecordNative_nativeGetBuffersPending(
    JNIEnv * /* env */, jclass /* clazz */, jlong jrecord)
{
    auto record = *(shared_pointer<AudioRecordNative> *)jrecord;
    if (record.get() == NULL) {
        return (jint)0;
    }
    return (jint)record->getBuffersPending();
}

template <typename T>
static inline jint readFromRecord(jlong jrecord, T *data,
    jint offsetInSamples, jint sizeInSamples, jint readFlags)
{
    auto record = *(shared_pointer<AudioRecordNative> *)jrecord;
    if (record.get() == NULL) {
        return (jint)INVALID_OPERATION;
    }

    const bool isBlocking = readFlags & READ_FLAG_BLOCKING;
    const size_t sizeInBytes = sizeInSamples * sizeof(T);
    ssize_t ret = record->read(data + offsetInSamples, sizeInBytes, isBlocking == JNI_TRUE);
    return (jint)(ret > 0 ? ret / sizeof(T) : ret);
}

template <typename T>
static inline jint readArray(JNIEnv *env, jclass /* clazz */, jlong jrecord,
        T javaAudioData, jint offsetInSamples, jint sizeInSamples, jint readFlags)
{
    if (javaAudioData == NULL) {
        return (jint)BAD_VALUE;
    }

    auto cAudioData = envGetArrayElements(env, javaAudioData, NULL /* isCopy */);
    if (cAudioData == NULL) {
        ALOGE("Error retrieving destination of audio data to record");
        return (jint)BAD_VALUE;
    }

    jint ret = readFromRecord(jrecord, cAudioData, offsetInSamples, sizeInSamples, readFlags);
    envReleaseArrayElements(env, javaAudioData, cAudioData, 0 /* mode */);
    return ret;
}

extern "C" jint Java_android_media_cts_AudioRecordNative_nativeReadByteArray(
    JNIEnv *env, jclass clazz, jlong jrecord,
    jbyteArray byteArray, jint offsetInSamples, jint sizeInSamples, jint readFlags)
{
    return readArray(env, clazz, jrecord, byteArray, offsetInSamples, sizeInSamples, readFlags);
}

extern "C" jint Java_android_media_cts_AudioRecordNative_nativeReadShortArray(
    JNIEnv *env, jclass clazz, jlong jrecord,
    jshortArray shortArray, jint offsetInSamples, jint sizeInSamples, jint readFlags)
{
    return readArray(env, clazz, jrecord, shortArray, offsetInSamples, sizeInSamples, readFlags);
}

extern "C" jint Java_android_media_cts_AudioRecordNative_nativeReadFloatArray(
    JNIEnv *env, jclass clazz, jlong jrecord,
    jfloatArray floatArray, jint offsetInSamples, jint sizeInSamples, jint readFlags)
{
    return readArray(env, clazz, jrecord, floatArray, offsetInSamples, sizeInSamples, readFlags);
}
