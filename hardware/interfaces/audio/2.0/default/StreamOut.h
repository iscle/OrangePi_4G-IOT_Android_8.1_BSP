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

#ifndef ANDROID_HARDWARE_AUDIO_V2_0_STREAMOUT_H
#define ANDROID_HARDWARE_AUDIO_V2_0_STREAMOUT_H

#include <atomic>
#include <memory>

#include <android/hardware/audio/2.0/IStreamOut.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <fmq/EventFlag.h>
#include <fmq/MessageQueue.h>
#include <utils/Thread.h>

#include "Device.h"
#include "Stream.h"

namespace android {
namespace hardware {
namespace audio {
namespace V2_0 {
namespace implementation {

using ::android::hardware::audio::common::V2_0::AudioChannelMask;
using ::android::hardware::audio::common::V2_0::AudioDevice;
using ::android::hardware::audio::common::V2_0::AudioFormat;
using ::android::hardware::audio::V2_0::AudioDrain;
using ::android::hardware::audio::V2_0::DeviceAddress;
using ::android::hardware::audio::V2_0::IStream;
using ::android::hardware::audio::V2_0::IStreamOut;
using ::android::hardware::audio::V2_0::IStreamOutCallback;
using ::android::hardware::audio::V2_0::ParameterValue;
using ::android::hardware::audio::V2_0::Result;
using ::android::hardware::audio::V2_0::TimeSpec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

struct StreamOut : public IStreamOut {
    typedef MessageQueue<WriteCommand, kSynchronizedReadWrite> CommandMQ;
    typedef MessageQueue<uint8_t, kSynchronizedReadWrite> DataMQ;
    typedef MessageQueue<WriteStatus, kSynchronizedReadWrite> StatusMQ;

    StreamOut(const sp<Device>& device, audio_stream_out_t* stream);

    // Methods from ::android::hardware::audio::V2_0::IStream follow.
    Return<uint64_t> getFrameSize()  override;
    Return<uint64_t> getFrameCount()  override;
    Return<uint64_t> getBufferSize()  override;
    Return<uint32_t> getSampleRate()  override;
    Return<void> getSupportedSampleRates(getSupportedSampleRates_cb _hidl_cb)  override;
    Return<Result> setSampleRate(uint32_t sampleRateHz)  override;
    Return<AudioChannelMask> getChannelMask()  override;
    Return<void> getSupportedChannelMasks(getSupportedChannelMasks_cb _hidl_cb)  override;
    Return<Result> setChannelMask(AudioChannelMask mask)  override;
    Return<AudioFormat> getFormat()  override;
    Return<void> getSupportedFormats(getSupportedFormats_cb _hidl_cb)  override;
    Return<Result> setFormat(AudioFormat format)  override;
    Return<void> getAudioProperties(getAudioProperties_cb _hidl_cb)  override;
    Return<Result> addEffect(uint64_t effectId)  override;
    Return<Result> removeEffect(uint64_t effectId)  override;
    Return<Result> standby()  override;
    Return<AudioDevice> getDevice()  override;
    Return<Result> setDevice(const DeviceAddress& address)  override;
    Return<Result> setConnectedState(const DeviceAddress& address, bool connected)  override;
    Return<Result> setHwAvSync(uint32_t hwAvSync)  override;
    Return<void> getParameters(
            const hidl_vec<hidl_string>& keys, getParameters_cb _hidl_cb)  override;
    Return<Result> setParameters(const hidl_vec<ParameterValue>& parameters)  override;
    Return<void> debugDump(const hidl_handle& fd)  override;
    Return<Result> close()  override;

    // Methods from ::android::hardware::audio::V2_0::IStreamOut follow.
    Return<uint32_t> getLatency()  override;
    Return<Result> setVolume(float left, float right)  override;
    Return<void> prepareForWriting(
            uint32_t frameSize, uint32_t framesCount, prepareForWriting_cb _hidl_cb)  override;
    Return<void> getRenderPosition(getRenderPosition_cb _hidl_cb)  override;
    Return<void> getNextWriteTimestamp(getNextWriteTimestamp_cb _hidl_cb)  override;
    Return<Result> setCallback(const sp<IStreamOutCallback>& callback)  override;
    Return<Result> clearCallback()  override;
    Return<void> supportsPauseAndResume(supportsPauseAndResume_cb _hidl_cb)  override;
    Return<Result> pause()  override;
    Return<Result> resume()  override;
    Return<bool> supportsDrain()  override;
    Return<Result> drain(AudioDrain type)  override;
    Return<Result> flush()  override;
    Return<void> getPresentationPosition(getPresentationPosition_cb _hidl_cb)  override;
    Return<Result> start() override;
    Return<Result> stop() override;
    Return<void> createMmapBuffer(int32_t minSizeFrames, createMmapBuffer_cb _hidl_cb) override;
    Return<void> getMmapPosition(getMmapPosition_cb _hidl_cb) override;

    static Result getPresentationPositionImpl(
            audio_stream_out_t *stream, uint64_t *frames, TimeSpec *timeStamp);

  private:
    bool mIsClosed;
    const sp<Device> mDevice;
    audio_stream_out_t *mStream;
    const sp<Stream> mStreamCommon;
    const sp<StreamMmap<audio_stream_out_t>> mStreamMmap;
    sp<IStreamOutCallback> mCallback;
    std::unique_ptr<CommandMQ> mCommandMQ;
    std::unique_ptr<DataMQ> mDataMQ;
    std::unique_ptr<StatusMQ> mStatusMQ;
    EventFlag* mEfGroup;
    std::atomic<bool> mStopWriteThread;
    sp<Thread> mWriteThread;

    virtual ~StreamOut();

    static int asyncCallback(stream_callback_event_t event, void *param, void *cookie);
};

}  // namespace implementation
}  // namespace V2_0
}  // namespace audio
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_AUDIO_V2_0_STREAMOUT_H
