/*
**
** Copyright 2012, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#ifndef ANDROID_AUDIO_STREAM_OUT_H
#define ANDROID_AUDIO_STREAM_OUT_H

#include <stdint.h>
#include <sys/types.h>

#include <common_time/local_clock.h>
#include <hardware/audio.h>
#include <media/AudioParameter.h>

#include "AudioOutput.h"

namespace android {

class AudioHardwareOutput;

class AudioStreamOut {
  public:
    AudioStreamOut(AudioHardwareOutput& owner, bool mcOut, bool isIec958NonAudio);
    ~AudioStreamOut();

    uint32_t            latency() const;
    status_t            getRenderPosition(uint32_t *dspFrames);
    status_t            getPresentationPosition(uint64_t *frames, struct timespec *timestamp);
    status_t            getNextWriteTimestamp(int64_t *timestamp);
    status_t            standby();
    status_t            pause();
    status_t            resume();
    status_t            flush();
    status_t            dump(int fd);

    uint32_t            sampleRate()        const { return mInputSampleRate; }
    uint32_t            outputSampleRate()  const;

    size_t              bufferSize()        const { return mInputBufSize; }
    uint32_t            chanMask()          const { return mInputChanMask; }
    audio_format_t      format()            const { return mInputFormat; }
    uint32_t            framesPerChunk()    const { return mInputChunkFrames; }
    uint32_t            nomChunksInFlight() const { return mInputNominalChunksInFlight; }

    status_t            set(audio_format_t *pFormat,
                            uint32_t       *pChannels,
                            uint32_t       *pRate);
    void                setTgtDevices(uint32_t tgtDevices);

    status_t            setParameters(struct audio_stream *stream,
                                      const char *kvpairs);
    char*               getParameters(const char* keys);
    const char*         getName() const { return mIsMCOutput ? "Multi-channel"
                                                             : "Main"; }

    ssize_t             write(const void* buffer, size_t bytes);

    bool                isIec958NonAudio() const { return mIsIec958NonAudio; }

protected:
    // Lock in this order to avoid deadlock.
    //    mRoutingLock
    //    mPresentationLock

    // Track frame position for timestamps, etc.
    uint64_t        mRenderPosition;  // in frames, increased by write
    uint64_t        mFramesPresented; // increased by write

    // Cache of the last PresentationPosition.
    // This cache is used in case of retrograde timestamps or if the mRoutingLock is held.
    Mutex           mPresentationLock; // protects these mLastPresentation* variables
    uint64_t        mLastPresentationPosition; // frames
    struct timespec mLastPresentationTime;
    bool            mLastPresentationValid;

    // Our HAL, used as the middle-man to collect and trade AudioOutputs.
    AudioHardwareOutput&  mOwnerHAL;

    // Details about the format of the audio we have been configured to receive
    // from audio flinger.
    uint32_t        mInputSampleRate;
    uint32_t        mInputChanMask;
    audio_format_t  mInputFormat;
    uint32_t        mInputNominalChunksInFlight;

    // Handy values pre-computed from the audio configuration.
    uint32_t        mInputBufSize;
    uint32_t        mInputChanCount;
    uint32_t        mInputFrameSize;
    uint32_t        mInputChunkFrames;
    uint32_t        mInputNominalLatencyUSec;
    LinearTransform mLocalTimeToFrames;

    // Bookkeeping used to throttle audio flinger when this audio stream has no
    // actual physical outputs.
    LocalClock      mLocalClock;
    bool            mThrottleValid;
    int64_t         mWriteStartLT;
    int64_t         mFramesWritten; // application rate frames, not device rate frames
    LinearTransform mUSecToLocalTime;

    // State to track which actual outputs are assigned to this output stream.
    Mutex           mRoutingLock; // This protects mPhysOutputs and mTgtDevices
    AudioOutputList mPhysOutputs;
    uint32_t        mTgtDevices;
    bool            mTgtDevicesDirty;
    uint32_t        mAudioFlingerTgtDevices;

    // Flag to track if this StreamOut was created to sink a direct output
    // multichannel stream.
    bool            mIsMCOutput;
    // Is the stream on standby?
    bool            mInStandby;
    // Is the stream compressed audio in SPDIF data bursts?
    const bool      mIsIec958NonAudio;

    // reduce log spew
    bool            mReportedAvailFail;

    status_t        standbyHardware();
    void            releaseAllOutputs(); // locks mRoutingLock
    void            updateTargetOutputs();  // locks mRoutingLock
    void            updateInputNums();
    void            finishedWriteOp(size_t framesWritten, bool needThrottle);
    void            resetThrottle() { mThrottleValid = false; }
    status_t        getNextWriteTimestamp_internal(int64_t *timestamp);
    void            adjustOutputs(int64_t maxTime);
    ssize_t         writeInternal(const void* buffer, size_t bytes);

    // mRoutingLock should be held before calling this.
    status_t        getPresentationPosition_l(uint64_t *frames, struct timespec *timestamp);
};

}  // android
#endif  // ANDROID_AUDIO_STREAM_OUT_H
