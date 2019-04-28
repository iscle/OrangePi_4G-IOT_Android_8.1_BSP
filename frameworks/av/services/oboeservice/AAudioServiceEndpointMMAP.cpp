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

#define LOG_TAG "AAudioServiceEndpointMMAP"
//#define LOG_NDEBUG 0
#include <utils/Log.h>

#include <algorithm>
#include <assert.h>
#include <map>
#include <mutex>
#include <sstream>
#include <utils/Singleton.h>
#include <vector>


#include "AAudioEndpointManager.h"
#include "AAudioServiceEndpoint.h"

#include "core/AudioStreamBuilder.h"
#include "AAudioServiceEndpoint.h"
#include "AAudioServiceStreamShared.h"
#include "AAudioServiceEndpointPlay.h"
#include "AAudioServiceEndpointMMAP.h"


#define AAUDIO_BUFFER_CAPACITY_MIN    4 * 512
#define AAUDIO_SAMPLE_RATE_DEFAULT    48000

// This is an estimate of the time difference between the HW and the MMAP time.
// TODO Get presentation timestamps from the HAL instead of using these estimates.
#define OUTPUT_ESTIMATED_HARDWARE_OFFSET_NANOS  (3 * AAUDIO_NANOS_PER_MILLISECOND)
#define INPUT_ESTIMATED_HARDWARE_OFFSET_NANOS   (-1 * AAUDIO_NANOS_PER_MILLISECOND)

using namespace android;  // TODO just import names needed
using namespace aaudio;   // TODO just import names needed

AAudioServiceEndpointMMAP::AAudioServiceEndpointMMAP()
        :  mMmapStream(nullptr) {}

AAudioServiceEndpointMMAP::~AAudioServiceEndpointMMAP() {}

std::string AAudioServiceEndpointMMAP::dump() const {
    std::stringstream result;

    result << "  MMAP: framesTransferred = " << mFramesTransferred.get();
    result << ", HW nanos = " << mHardwareTimeOffsetNanos;
    result << ", port handle = " << mPortHandle;
    result << ", audio data FD = " << mAudioDataFileDescriptor;
    result << "\n";

    result << "    HW Offset Micros:     " <<
                                      (getHardwareTimeOffsetNanos()
                                       / AAUDIO_NANOS_PER_MICROSECOND) << "\n";

    result << AAudioServiceEndpoint::dump();
    return result.str();
}

aaudio_result_t AAudioServiceEndpointMMAP::open(const aaudio::AAudioStreamRequest &request) {
    aaudio_result_t result = AAUDIO_OK;
    const audio_attributes_t attributes = {
            .content_type = AUDIO_CONTENT_TYPE_MUSIC,
            .usage = AUDIO_USAGE_MEDIA,
            .source = AUDIO_SOURCE_VOICE_RECOGNITION,
            .flags = AUDIO_FLAG_LOW_LATENCY,
            .tags = ""
    };
    audio_config_base_t config;
    audio_port_handle_t deviceId;

    int32_t burstMinMicros = AAudioProperty_getHardwareBurstMinMicros();
    int32_t burstMicros = 0;

    copyFrom(request.getConstantConfiguration());

    mMmapClient.clientUid = request.getUserId();
    mMmapClient.clientPid = request.getProcessId();
    mMmapClient.packageName.setTo(String16(""));

    mRequestedDeviceId = deviceId = getDeviceId();

    // Fill in config
    aaudio_format_t aaudioFormat = getFormat();
    if (aaudioFormat == AAUDIO_UNSPECIFIED || aaudioFormat == AAUDIO_FORMAT_PCM_FLOAT) {
        aaudioFormat = AAUDIO_FORMAT_PCM_I16;
    }
    config.format = AAudioConvert_aaudioToAndroidDataFormat(aaudioFormat);

    int32_t aaudioSampleRate = getSampleRate();
    if (aaudioSampleRate == AAUDIO_UNSPECIFIED) {
        aaudioSampleRate = AAUDIO_SAMPLE_RATE_DEFAULT;
    }
    config.sample_rate = aaudioSampleRate;

    int32_t aaudioSamplesPerFrame = getSamplesPerFrame();

    aaudio_direction_t direction = getDirection();
    if (direction == AAUDIO_DIRECTION_OUTPUT) {
        config.channel_mask = (aaudioSamplesPerFrame == AAUDIO_UNSPECIFIED)
                              ? AUDIO_CHANNEL_OUT_STEREO
                              : audio_channel_out_mask_from_count(aaudioSamplesPerFrame);
        mHardwareTimeOffsetNanos = OUTPUT_ESTIMATED_HARDWARE_OFFSET_NANOS; // frames at DAC later

    } else if (direction == AAUDIO_DIRECTION_INPUT) {
        config.channel_mask =  (aaudioSamplesPerFrame == AAUDIO_UNSPECIFIED)
                               ? AUDIO_CHANNEL_IN_STEREO
                               : audio_channel_in_mask_from_count(aaudioSamplesPerFrame);
        mHardwareTimeOffsetNanos = INPUT_ESTIMATED_HARDWARE_OFFSET_NANOS; // frames at ADC earlier

    } else {
        ALOGE("openMmapStream - invalid direction = %d", direction);
        return AAUDIO_ERROR_ILLEGAL_ARGUMENT;
    }

    MmapStreamInterface::stream_direction_t streamDirection =
            (direction == AAUDIO_DIRECTION_OUTPUT)
            ? MmapStreamInterface::DIRECTION_OUTPUT
            : MmapStreamInterface::DIRECTION_INPUT;

    // Open HAL stream. Set mMmapStream
    status_t status = MmapStreamInterface::openMmapStream(streamDirection,
                                                          &attributes,
                                                          &config,
                                                          mMmapClient,
                                                          &deviceId,
                                                          this, // callback
                                                          mMmapStream,
                                                          &mPortHandle);
    ALOGD("AAudioServiceEndpointMMAP::open() mMapClient.uid = %d, pid = %d => portHandle = %d\n",
          mMmapClient.clientUid,  mMmapClient.clientPid, mPortHandle);
    if (status != OK) {
        ALOGE("openMmapStream returned status %d", status);
        return AAUDIO_ERROR_UNAVAILABLE;
    }

    if (deviceId == AAUDIO_UNSPECIFIED) {
        ALOGW("AAudioServiceEndpointMMAP::open() - openMmapStream() failed to set deviceId");
    }
    setDeviceId(deviceId);

    // Create MMAP/NOIRQ buffer.
    int32_t minSizeFrames = getBufferCapacity();
    if (minSizeFrames <= 0) { // zero will get rejected
        minSizeFrames = AAUDIO_BUFFER_CAPACITY_MIN;
    }
    status = mMmapStream->createMmapBuffer(minSizeFrames, &mMmapBufferinfo);
    if (status != OK) {
        ALOGE("AAudioServiceEndpointMMAP::open() - createMmapBuffer() failed with status %d %s",
              status, strerror(-status));
        result = AAUDIO_ERROR_UNAVAILABLE;
        goto error;
    } else {
        ALOGD("createMmapBuffer status = %d, buffer_size = %d, burst_size %d"
                      ", Sharable FD: %s",
              status,
              abs(mMmapBufferinfo.buffer_size_frames),
              mMmapBufferinfo.burst_size_frames,
              mMmapBufferinfo.buffer_size_frames < 0 ? "Yes" : "No");
    }

    setBufferCapacity(mMmapBufferinfo.buffer_size_frames);
    // The audio HAL indicates if the shared memory fd can be shared outside of audioserver
    // by returning a negative buffer size
    if (getBufferCapacity() < 0) {
        // Exclusive mode can be used by client or service.
        setBufferCapacity(-getBufferCapacity());
    } else {
        // Exclusive mode can only be used by the service because the FD cannot be shared.
        uid_t audioServiceUid = getuid();
        if ((mMmapClient.clientUid != audioServiceUid) &&
            getSharingMode() == AAUDIO_SHARING_MODE_EXCLUSIVE) {
            // Fallback is handled by caller but indicate what is possible in case
            // this is used in the future
            setSharingMode(AAUDIO_SHARING_MODE_SHARED);
            ALOGW("AAudioServiceEndpointMMAP::open() - exclusive FD cannot be used by client");
            result = AAUDIO_ERROR_UNAVAILABLE;
            goto error;
        }
    }

    // Get information about the stream and pass it back to the caller.
    setSamplesPerFrame((direction == AAUDIO_DIRECTION_OUTPUT)
                       ? audio_channel_count_from_out_mask(config.channel_mask)
                       : audio_channel_count_from_in_mask(config.channel_mask));

    // AAudio creates a copy of this FD and retains ownership of the copy.
    // Assume that AudioFlinger will close the original shared_memory_fd.
    mAudioDataFileDescriptor.reset(dup(mMmapBufferinfo.shared_memory_fd));
    if (mAudioDataFileDescriptor.get() == -1) {
        ALOGE("AAudioServiceEndpointMMAP::open() - could not dup shared_memory_fd");
        result = AAUDIO_ERROR_INTERNAL;
        goto error;
    }
    mFramesPerBurst = mMmapBufferinfo.burst_size_frames;
    setFormat(AAudioConvert_androidToAAudioDataFormat(config.format));
    setSampleRate(config.sample_rate);

    // Scale up the burst size to meet the minimum equivalent in microseconds.
    // This is to avoid waking the CPU too often when the HW burst is very small
    // or at high sample rates.
    do {
        if (burstMicros > 0) {  // skip first loop
            mFramesPerBurst *= 2;
        }
        burstMicros = mFramesPerBurst * static_cast<int64_t>(1000000) / getSampleRate();
    } while (burstMicros < burstMinMicros);

    ALOGD("AAudioServiceEndpointMMAP::open() original burst = %d, minMicros = %d, to burst = %d\n",
          mMmapBufferinfo.burst_size_frames, burstMinMicros, mFramesPerBurst);

    ALOGD("AAudioServiceEndpointMMAP::open() actual rate = %d, channels = %d"
          ", deviceId = %d, capacity = %d\n",
          getSampleRate(), getSamplesPerFrame(), deviceId, getBufferCapacity());

    return result;

error:
    close();
    return result;
}

aaudio_result_t AAudioServiceEndpointMMAP::close() {

    if (mMmapStream != 0) {
        ALOGD("AAudioServiceEndpointMMAP::close() clear() endpoint");
        // Needs to be explicitly cleared or CTS will fail but it is not clear why.
        mMmapStream.clear();
        // Apparently the above close is asynchronous. An attempt to open a new device
        // right after a close can fail. Also some callbacks may still be in flight!
        // FIXME Make closing synchronous.
        AudioClock::sleepForNanos(100 * AAUDIO_NANOS_PER_MILLISECOND);
    }

    return AAUDIO_OK;
}

aaudio_result_t AAudioServiceEndpointMMAP::startStream(sp<AAudioServiceStreamBase> stream,
                                                   audio_port_handle_t *clientHandle) {
    // Start the client on behalf of the AAudio service.
    // Use the port handle that was provided by openMmapStream().
    return startClient(mMmapClient, &mPortHandle);
}

aaudio_result_t AAudioServiceEndpointMMAP::stopStream(sp<AAudioServiceStreamBase> stream,
                                                  audio_port_handle_t clientHandle) {
    mFramesTransferred.reset32();

    // Round 64-bit counter up to a multiple of the buffer capacity.
    // This is required because the 64-bit counter is used as an index
    // into a circular buffer and the actual HW position is reset to zero
    // when the stream is stopped.
    mFramesTransferred.roundUp64(getBufferCapacity());

    return stopClient(mPortHandle);
}

aaudio_result_t AAudioServiceEndpointMMAP::startClient(const android::AudioClient& client,
                                                       audio_port_handle_t *clientHandle) {
    if (mMmapStream == nullptr) return AAUDIO_ERROR_NULL;
    ALOGD("AAudioServiceEndpointMMAP::startClient(%p(uid=%d, pid=%d))",
          &client, client.clientUid, client.clientPid);
    audio_port_handle_t originalHandle =  *clientHandle;
    status_t status = mMmapStream->start(client, clientHandle);
    aaudio_result_t result = AAudioConvert_androidToAAudioResult(status);
    ALOGD("AAudioServiceEndpointMMAP::startClient() , %d => %d returns %d",
          originalHandle, *clientHandle, result);
    return result;
}

aaudio_result_t AAudioServiceEndpointMMAP::stopClient(audio_port_handle_t clientHandle) {
    if (mMmapStream == nullptr) return AAUDIO_ERROR_NULL;
    aaudio_result_t result = AAudioConvert_androidToAAudioResult(mMmapStream->stop(clientHandle));
    ALOGD("AAudioServiceEndpointMMAP::stopClient(%d) returns %d", clientHandle, result);
    return result;
}

// Get free-running DSP or DMA hardware position from the HAL.
aaudio_result_t AAudioServiceEndpointMMAP::getFreeRunningPosition(int64_t *positionFrames,
                                                                int64_t *timeNanos) {
    struct audio_mmap_position position;
    if (mMmapStream == nullptr) {
        return AAUDIO_ERROR_NULL;
    }
    status_t status = mMmapStream->getMmapPosition(&position);
    ALOGV("AAudioServiceEndpointMMAP::getFreeRunningPosition() status= %d, pos = %d, nanos = %lld\n",
          status, position.position_frames, (long long) position.time_nanoseconds);
    aaudio_result_t result = AAudioConvert_androidToAAudioResult(status);
    if (result == AAUDIO_ERROR_UNAVAILABLE) {
        ALOGW("sendCurrentTimestamp(): getMmapPosition() has no position data available");
    } else if (result != AAUDIO_OK) {
        ALOGE("sendCurrentTimestamp(): getMmapPosition() returned status %d", status);
    } else {
        // Convert 32-bit position to 64-bit position.
        mFramesTransferred.update32(position.position_frames);
        *positionFrames = mFramesTransferred.get();
        *timeNanos = position.time_nanoseconds;
    }
    return result;
}

aaudio_result_t AAudioServiceEndpointMMAP::getTimestamp(int64_t *positionFrames,
                                                    int64_t *timeNanos) {
    return 0; // TODO
}


void AAudioServiceEndpointMMAP::onTearDown() {
    ALOGD("AAudioServiceEndpointMMAP::onTearDown() called");
    disconnectRegisteredStreams();
};

void AAudioServiceEndpointMMAP::onVolumeChanged(audio_channel_mask_t channels,
                                              android::Vector<float> values) {
    // TODO do we really need a different volume for each channel?
    float volume = values[0];
    ALOGD("AAudioServiceEndpointMMAP::onVolumeChanged() volume[0] = %f", volume);
    std::lock_guard<std::mutex> lock(mLockStreams);
    for(const auto stream : mRegisteredStreams) {
        stream->onVolumeChanged(volume);
    }
};

void AAudioServiceEndpointMMAP::onRoutingChanged(audio_port_handle_t deviceId) {
    ALOGD("AAudioServiceEndpointMMAP::onRoutingChanged() called with %d, old = %d",
          deviceId, getDeviceId());
    if (getDeviceId() != AUDIO_PORT_HANDLE_NONE  && getDeviceId() != deviceId) {
        disconnectRegisteredStreams();
    }
    setDeviceId(deviceId);
};

/**
 * Get an immutable description of the data queue from the HAL.
 */
aaudio_result_t AAudioServiceEndpointMMAP::getDownDataDescription(AudioEndpointParcelable &parcelable)
{
    // Gather information on the data queue based on HAL info.
    int32_t bytesPerFrame = calculateBytesPerFrame();
    int32_t capacityInBytes = getBufferCapacity() * bytesPerFrame;
    int fdIndex = parcelable.addFileDescriptor(mAudioDataFileDescriptor, capacityInBytes);
    parcelable.mDownDataQueueParcelable.setupMemory(fdIndex, 0, capacityInBytes);
    parcelable.mDownDataQueueParcelable.setBytesPerFrame(bytesPerFrame);
    parcelable.mDownDataQueueParcelable.setFramesPerBurst(mFramesPerBurst);
    parcelable.mDownDataQueueParcelable.setCapacityInFrames(getBufferCapacity());
    return AAUDIO_OK;
}
