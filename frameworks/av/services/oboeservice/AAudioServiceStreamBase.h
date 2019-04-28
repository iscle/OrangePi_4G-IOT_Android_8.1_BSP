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

#ifndef AAUDIO_AAUDIO_SERVICE_STREAM_BASE_H
#define AAUDIO_AAUDIO_SERVICE_STREAM_BASE_H

#include <assert.h>
#include <mutex>

#include <media/AudioClient.h>
#include <utils/RefBase.h>

#include "fifo/FifoBuffer.h"
#include "binding/IAAudioService.h"
#include "binding/AudioEndpointParcelable.h"
#include "binding/AAudioServiceMessage.h"
#include "utility/AAudioUtilities.h"
#include "utility/AudioClock.h"

#include "SharedRingBuffer.h"
#include "AAudioThread.h"

namespace android {
    class AAudioService;
}

namespace aaudio {

class AAudioServiceEndpoint;

// We expect the queue to only have a few commands.
// This should be way more than we need.
#define QUEUE_UP_CAPACITY_COMMANDS (128)

/**
 * Each instance of AAudioServiceStreamBase corresponds to a client stream.
 * It uses a subclass of AAudioServiceEndpoint to communicate with the underlying device or port.
 */
class AAudioServiceStreamBase
    : public virtual android::RefBase
    , public AAudioStreamParameters
    , public Runnable  {

public:
    AAudioServiceStreamBase(android::AAudioService &aAudioService);

    virtual ~AAudioServiceStreamBase();

    enum {
        ILLEGAL_THREAD_ID = 0
    };

    static std::string dumpHeader();

    // does not include EOL
    virtual std::string dump() const;

    // -------------------------------------------------------------------
    /**
     * Open the device.
     */
    virtual aaudio_result_t open(const aaudio::AAudioStreamRequest &request) = 0;

    virtual aaudio_result_t close();

    /**
     * Start the flow of audio data.
     *
     * This is not guaranteed to be synchronous but it currently is.
     * An AAUDIO_SERVICE_EVENT_STARTED will be sent to the client when complete.
     */
    virtual aaudio_result_t start();

    /**
     * Stop the flow of data so that start() can resume without loss of data.
     *
     * This is not guaranteed to be synchronous but it currently is.
     * An AAUDIO_SERVICE_EVENT_PAUSED will be sent to the client when complete.
    */
    virtual aaudio_result_t pause();

    /**
     * Stop the flow of data after the currently queued data has finished playing.
     *
     * This is not guaranteed to be synchronous but it currently is.
     * An AAUDIO_SERVICE_EVENT_STOPPED will be sent to the client when complete.
     *
     */
    virtual aaudio_result_t stop();

    aaudio_result_t stopTimestampThread();

    /**
     * Discard any data held by the underlying HAL or Service.
     *
     * An AAUDIO_SERVICE_EVENT_FLUSHED will be sent to the client when complete.
     */
    virtual aaudio_result_t flush();


    virtual aaudio_result_t startClient(const android::AudioClient& client __unused,
                                        audio_port_handle_t *clientHandle __unused) {
        ALOGD("AAudioServiceStreamBase::startClient(%p, ...) AAUDIO_ERROR_UNAVAILABLE", &client);
        return AAUDIO_ERROR_UNAVAILABLE;
    }

    virtual aaudio_result_t stopClient(audio_port_handle_t clientHandle __unused) {
        ALOGD("AAudioServiceStreamBase::stopClient(%d) AAUDIO_ERROR_UNAVAILABLE", clientHandle);
        return AAUDIO_ERROR_UNAVAILABLE;
    }

    bool isRunning() const {
        return mState == AAUDIO_STREAM_STATE_STARTED;
    }

    // -------------------------------------------------------------------

    /**
     * Send a message to the client.
     */
    aaudio_result_t sendServiceEvent(aaudio_service_event_t event,
                                     double  dataDouble = 0.0,
                                     int64_t dataLong = 0);

    /**
     * Fill in a parcelable description of stream.
     */
    aaudio_result_t getDescription(AudioEndpointParcelable &parcelable);


    void setRegisteredThread(pid_t pid) {
        mRegisteredClientThread = pid;
    }

    pid_t getRegisteredThread() const {
        return mRegisteredClientThread;
    }

    int32_t getFramesPerBurst() const {
        return mFramesPerBurst;
    }

    void run() override; // to implement Runnable

    void disconnect();

    const android::AudioClient &getAudioClient() {
        return mMmapClient;
    }

    uid_t getOwnerUserId() const {
        return mMmapClient.clientUid;
    }

    pid_t getOwnerProcessId() const {
        return mMmapClient.clientPid;
    }

    aaudio_handle_t getHandle() const {
        return mHandle;
    }
    void setHandle(aaudio_handle_t handle) {
        mHandle = handle;
    }

    aaudio_stream_state_t getState() const {
        return mState;
    }

    void onVolumeChanged(float volume);

protected:

    /**
     * Open the device.
     */
    aaudio_result_t open(const aaudio::AAudioStreamRequest &request,
                         aaudio_sharing_mode_t sharingMode);

    void setState(aaudio_stream_state_t state) {
        mState = state;
    }

    /**
     * Device specific startup.
     * @return AAUDIO_OK or negative error.
     */
    virtual aaudio_result_t startDevice();

    aaudio_result_t writeUpMessageQueue(AAudioServiceMessage *command);

    aaudio_result_t sendCurrentTimestamp();

    /**
     * @param positionFrames
     * @param timeNanos
     * @return AAUDIO_OK or AAUDIO_ERROR_UNAVAILABLE or other negative error
     */
    virtual aaudio_result_t getFreeRunningPosition(int64_t *positionFrames, int64_t *timeNanos) = 0;

    virtual aaudio_result_t getHardwareTimestamp(int64_t *positionFrames, int64_t *timeNanos) = 0;

    virtual aaudio_result_t getAudioDataDescription(AudioEndpointParcelable &parcelable) = 0;

    aaudio_stream_state_t   mState = AAUDIO_STREAM_STATE_UNINITIALIZED;

    pid_t                   mRegisteredClientThread = ILLEGAL_THREAD_ID;

    SharedRingBuffer*       mUpMessageQueue;
    std::mutex              mUpMessageQueueLock;

    AAudioThread            mTimestampThread;
    // This is used by one thread to tell another thread to exit. So it must be atomic.
    std::atomic<bool>       mThreadEnabled{false};

    int32_t                 mFramesPerBurst = 0;
    android::AudioClient    mMmapClient; // set in open, used in MMAP start()
    audio_port_handle_t     mClientHandle = AUDIO_PORT_HANDLE_NONE;

    SimpleDoubleBuffer<Timestamp>  mAtomicTimestamp;

    android::AAudioService &mAudioService;
    android::sp<AAudioServiceEndpoint> mServiceEndpoint;

private:
    aaudio_handle_t         mHandle = -1;
};

} /* namespace aaudio */

#endif //AAUDIO_AAUDIO_SERVICE_STREAM_BASE_H
