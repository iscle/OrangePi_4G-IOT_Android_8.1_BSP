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

#define LOG_TAG "StreamInHAL"
//#define LOG_NDEBUG 0
#define ATRACE_TAG ATRACE_TAG_AUDIO

#include <android/log.h>
#include <hardware/audio.h>
#include <utils/Trace.h>
#include <memory>

#include "StreamIn.h"
#include "Util.h"

using ::android::hardware::audio::V2_0::MessageQueueFlagBits;

namespace android {
namespace hardware {
namespace audio {
namespace V2_0 {
namespace implementation {

using ::android::hardware::audio::common::V2_0::ThreadInfo;

namespace {

class ReadThread : public Thread {
   public:
    // ReadThread's lifespan never exceeds StreamIn's lifespan.
    ReadThread(std::atomic<bool>* stop, audio_stream_in_t* stream,
               StreamIn::CommandMQ* commandMQ, StreamIn::DataMQ* dataMQ,
               StreamIn::StatusMQ* statusMQ, EventFlag* efGroup)
        : Thread(false /*canCallJava*/),
          mStop(stop),
          mStream(stream),
          mCommandMQ(commandMQ),
          mDataMQ(dataMQ),
          mStatusMQ(statusMQ),
          mEfGroup(efGroup),
          mBuffer(nullptr) {}
    bool init() {
        mBuffer.reset(new (std::nothrow) uint8_t[mDataMQ->getQuantumCount()]);
        return mBuffer != nullptr;
    }
    virtual ~ReadThread() {}

   private:
    std::atomic<bool>* mStop;
    audio_stream_in_t* mStream;
    StreamIn::CommandMQ* mCommandMQ;
    StreamIn::DataMQ* mDataMQ;
    StreamIn::StatusMQ* mStatusMQ;
    EventFlag* mEfGroup;
    std::unique_ptr<uint8_t[]> mBuffer;
    IStreamIn::ReadParameters mParameters;
    IStreamIn::ReadStatus mStatus;

    bool threadLoop() override;

    void doGetCapturePosition();
    void doRead();
};

void ReadThread::doRead() {
    size_t availableToWrite = mDataMQ->availableToWrite();
    size_t requestedToRead = mParameters.params.read;
    if (requestedToRead > availableToWrite) {
        ALOGW(
            "truncating read data from %d to %d due to insufficient data queue "
            "space",
            (int32_t)requestedToRead, (int32_t)availableToWrite);
        requestedToRead = availableToWrite;
    }
    ssize_t readResult = mStream->read(mStream, &mBuffer[0], requestedToRead);
    mStatus.retval = Result::OK;
    uint64_t read = 0;
    if (readResult >= 0) {
        mStatus.reply.read = readResult;
        if (!mDataMQ->write(&mBuffer[0], readResult)) {
            ALOGW("data message queue write failed");
        }
    } else {
        mStatus.retval = Stream::analyzeStatus("read", readResult);
    }
}

void ReadThread::doGetCapturePosition() {
    mStatus.retval = StreamIn::getCapturePositionImpl(
        mStream, &mStatus.reply.capturePosition.frames,
        &mStatus.reply.capturePosition.time);
}

bool ReadThread::threadLoop() {
    // This implementation doesn't return control back to the Thread until it
    // decides to stop,
    // as the Thread uses mutexes, and this can lead to priority inversion.
    while (!std::atomic_load_explicit(mStop, std::memory_order_acquire)) {
        uint32_t efState = 0;
        mEfGroup->wait(static_cast<uint32_t>(MessageQueueFlagBits::NOT_FULL),
                       &efState);
        if (!(efState &
              static_cast<uint32_t>(MessageQueueFlagBits::NOT_FULL))) {
            continue;  // Nothing to do.
        }
        if (!mCommandMQ->read(&mParameters)) {
            continue;  // Nothing to do.
        }
        mStatus.replyTo = mParameters.command;
        switch (mParameters.command) {
            case IStreamIn::ReadCommand::READ:
                doRead();
                break;
            case IStreamIn::ReadCommand::GET_CAPTURE_POSITION:
                doGetCapturePosition();
                break;
            default:
                ALOGE("Unknown read thread command code %d",
                      mParameters.command);
                mStatus.retval = Result::NOT_SUPPORTED;
                break;
        }
        if (!mStatusMQ->write(&mStatus)) {
            ALOGW("status message queue write failed");
        }
        mEfGroup->wake(static_cast<uint32_t>(MessageQueueFlagBits::NOT_EMPTY));
    }

    return false;
}

}  // namespace

StreamIn::StreamIn(const sp<Device>& device, audio_stream_in_t* stream)
    : mIsClosed(false),
      mDevice(device),
      mStream(stream),
      mStreamCommon(new Stream(&stream->common)),
      mStreamMmap(new StreamMmap<audio_stream_in_t>(stream)),
      mEfGroup(nullptr),
      mStopReadThread(false) {}

StreamIn::~StreamIn() {
    ATRACE_CALL();
    close();
    if (mReadThread.get()) {
        ATRACE_NAME("mReadThread->join");
        status_t status = mReadThread->join();
        ALOGE_IF(status, "read thread exit error: %s", strerror(-status));
    }
    if (mEfGroup) {
        status_t status = EventFlag::deleteEventFlag(&mEfGroup);
        ALOGE_IF(status, "read MQ event flag deletion error: %s",
                 strerror(-status));
    }
    mDevice->closeInputStream(mStream);
    mStream = nullptr;
}

// Methods from ::android::hardware::audio::V2_0::IStream follow.
Return<uint64_t> StreamIn::getFrameSize() {
    return audio_stream_in_frame_size(mStream);
}

Return<uint64_t> StreamIn::getFrameCount() {
    return mStreamCommon->getFrameCount();
}

Return<uint64_t> StreamIn::getBufferSize() {
    return mStreamCommon->getBufferSize();
}

Return<uint32_t> StreamIn::getSampleRate() {
    return mStreamCommon->getSampleRate();
}

Return<void> StreamIn::getSupportedSampleRates(
    getSupportedSampleRates_cb _hidl_cb) {
    return mStreamCommon->getSupportedSampleRates(_hidl_cb);
}

Return<Result> StreamIn::setSampleRate(uint32_t sampleRateHz) {
    return mStreamCommon->setSampleRate(sampleRateHz);
}

Return<AudioChannelMask> StreamIn::getChannelMask() {
    return mStreamCommon->getChannelMask();
}

Return<void> StreamIn::getSupportedChannelMasks(
    getSupportedChannelMasks_cb _hidl_cb) {
    return mStreamCommon->getSupportedChannelMasks(_hidl_cb);
}

Return<Result> StreamIn::setChannelMask(AudioChannelMask mask) {
    return mStreamCommon->setChannelMask(mask);
}

Return<AudioFormat> StreamIn::getFormat() {
    return mStreamCommon->getFormat();
}

Return<void> StreamIn::getSupportedFormats(getSupportedFormats_cb _hidl_cb) {
    return mStreamCommon->getSupportedFormats(_hidl_cb);
}

Return<Result> StreamIn::setFormat(AudioFormat format) {
    return mStreamCommon->setFormat(format);
}

Return<void> StreamIn::getAudioProperties(getAudioProperties_cb _hidl_cb) {
    return mStreamCommon->getAudioProperties(_hidl_cb);
}

Return<Result> StreamIn::addEffect(uint64_t effectId) {
    return mStreamCommon->addEffect(effectId);
}

Return<Result> StreamIn::removeEffect(uint64_t effectId) {
    return mStreamCommon->removeEffect(effectId);
}

Return<Result> StreamIn::standby() {
    return mStreamCommon->standby();
}

Return<AudioDevice> StreamIn::getDevice() {
    return mStreamCommon->getDevice();
}

Return<Result> StreamIn::setDevice(const DeviceAddress& address) {
    return mStreamCommon->setDevice(address);
}

Return<Result> StreamIn::setConnectedState(const DeviceAddress& address,
                                           bool connected) {
    return mStreamCommon->setConnectedState(address, connected);
}

Return<Result> StreamIn::setHwAvSync(uint32_t hwAvSync) {
    return mStreamCommon->setHwAvSync(hwAvSync);
}

Return<void> StreamIn::getParameters(const hidl_vec<hidl_string>& keys,
                                     getParameters_cb _hidl_cb) {
    return mStreamCommon->getParameters(keys, _hidl_cb);
}

Return<Result> StreamIn::setParameters(
    const hidl_vec<ParameterValue>& parameters) {
    return mStreamCommon->setParameters(parameters);
}

Return<void> StreamIn::debugDump(const hidl_handle& fd) {
    return mStreamCommon->debugDump(fd);
}

Return<Result> StreamIn::start() {
    return mStreamMmap->start();
}

Return<Result> StreamIn::stop() {
    return mStreamMmap->stop();
}

Return<void> StreamIn::createMmapBuffer(int32_t minSizeFrames,
                                        createMmapBuffer_cb _hidl_cb) {
    return mStreamMmap->createMmapBuffer(
        minSizeFrames, audio_stream_in_frame_size(mStream), _hidl_cb);
}

Return<void> StreamIn::getMmapPosition(getMmapPosition_cb _hidl_cb) {
    return mStreamMmap->getMmapPosition(_hidl_cb);
}

Return<Result> StreamIn::close() {
    if (mIsClosed) return Result::INVALID_STATE;
    mIsClosed = true;
    if (mReadThread.get()) {
        mStopReadThread.store(true, std::memory_order_release);
    }
    if (mEfGroup) {
        mEfGroup->wake(static_cast<uint32_t>(MessageQueueFlagBits::NOT_FULL));
    }
    return Result::OK;
}

// Methods from ::android::hardware::audio::V2_0::IStreamIn follow.
Return<void> StreamIn::getAudioSource(getAudioSource_cb _hidl_cb) {
    int halSource;
    Result retval =
        mStreamCommon->getParam(AudioParameter::keyInputSource, &halSource);
    AudioSource source(AudioSource::DEFAULT);
    if (retval == Result::OK) {
        source = AudioSource(halSource);
    }
    _hidl_cb(retval, source);
    return Void();
}

Return<Result> StreamIn::setGain(float gain) {
    if (!isGainNormalized(gain)) {
        ALOGW("Can not set a stream input gain (%f) outside [0,1]", gain);
        return Result::INVALID_ARGUMENTS;
    }
    return Stream::analyzeStatus("set_gain", mStream->set_gain(mStream, gain));
}

Return<void> StreamIn::prepareForReading(uint32_t frameSize,
                                         uint32_t framesCount,
                                         prepareForReading_cb _hidl_cb) {
    status_t status;
    ThreadInfo threadInfo = {0, 0};

    // Wrap the _hidl_cb to return an error
    auto sendError = [this, &threadInfo, &_hidl_cb](Result result) {
        _hidl_cb(result, CommandMQ::Descriptor(), DataMQ::Descriptor(),
                 StatusMQ::Descriptor(), threadInfo);

    };

    // Create message queues.
    if (mDataMQ) {
        ALOGE("the client attempts to call prepareForReading twice");
        sendError(Result::INVALID_STATE);
        return Void();
    }
    std::unique_ptr<CommandMQ> tempCommandMQ(new CommandMQ(1));

    // Check frameSize and framesCount
    if (frameSize == 0 || framesCount == 0) {
        ALOGE("Null frameSize (%u) or framesCount (%u)", frameSize,
              framesCount);
        sendError(Result::INVALID_ARGUMENTS);
        return Void();
    }

    if (frameSize > Stream::MAX_BUFFER_SIZE / framesCount) {
        ALOGE("Buffer too big: %u*%u bytes > MAX_BUFFER_SIZE (%u)", frameSize, framesCount,
              Stream::MAX_BUFFER_SIZE);
        sendError(Result::INVALID_ARGUMENTS);
        return Void();
    }
    std::unique_ptr<DataMQ> tempDataMQ(
        new DataMQ(frameSize * framesCount, true /* EventFlag */));

    std::unique_ptr<StatusMQ> tempStatusMQ(new StatusMQ(1));
    if (!tempCommandMQ->isValid() || !tempDataMQ->isValid() ||
        !tempStatusMQ->isValid()) {
        ALOGE_IF(!tempCommandMQ->isValid(), "command MQ is invalid");
        ALOGE_IF(!tempDataMQ->isValid(), "data MQ is invalid");
        ALOGE_IF(!tempStatusMQ->isValid(), "status MQ is invalid");
        sendError(Result::INVALID_ARGUMENTS);
        return Void();
    }
    EventFlag* tempRawEfGroup{};
    status = EventFlag::createEventFlag(tempDataMQ->getEventFlagWord(),
                                        &tempRawEfGroup);
    std::unique_ptr<EventFlag, void (*)(EventFlag*)> tempElfGroup(
        tempRawEfGroup, [](auto* ef) { EventFlag::deleteEventFlag(&ef); });
    if (status != OK || !tempElfGroup) {
        ALOGE("failed creating event flag for data MQ: %s", strerror(-status));
        sendError(Result::INVALID_ARGUMENTS);
        return Void();
    }

    // Create and launch the thread.
    auto tempReadThread = std::make_unique<ReadThread>(
        &mStopReadThread, mStream, tempCommandMQ.get(), tempDataMQ.get(),
        tempStatusMQ.get(), tempElfGroup.get());
    if (!tempReadThread->init()) {
        ALOGW("failed to start reader thread: %s", strerror(-status));
        sendError(Result::INVALID_ARGUMENTS);
        return Void();
    }
    status = tempReadThread->run("reader", PRIORITY_URGENT_AUDIO);
    if (status != OK) {
        ALOGW("failed to start reader thread: %s", strerror(-status));
        sendError(Result::INVALID_ARGUMENTS);
        return Void();
    }

    mCommandMQ = std::move(tempCommandMQ);
    mDataMQ = std::move(tempDataMQ);
    mStatusMQ = std::move(tempStatusMQ);
    mReadThread = tempReadThread.release();
    mEfGroup = tempElfGroup.release();
    threadInfo.pid = getpid();
    threadInfo.tid = mReadThread->getTid();
    _hidl_cb(Result::OK, *mCommandMQ->getDesc(), *mDataMQ->getDesc(),
             *mStatusMQ->getDesc(), threadInfo);
    return Void();
}

Return<uint32_t> StreamIn::getInputFramesLost() {
    return mStream->get_input_frames_lost(mStream);
}

// static
Result StreamIn::getCapturePositionImpl(audio_stream_in_t* stream,
                                        uint64_t* frames, uint64_t* time) {
    // HAL may have a stub function, always returning ENOSYS, don't
    // spam the log in this case.
    static const std::vector<int> ignoredErrors{ENOSYS};
    Result retval(Result::NOT_SUPPORTED);
    if (stream->get_capture_position != NULL) return retval;
    int64_t halFrames, halTime;
    retval = Stream::analyzeStatus("get_capture_position",
                                   stream->get_capture_position(stream, &halFrames, &halTime),
                                   ignoredErrors);
    if (retval == Result::OK) {
        *frames = halFrames;
        *time = halTime;
    }
    return retval;
};

Return<void> StreamIn::getCapturePosition(getCapturePosition_cb _hidl_cb) {
    uint64_t frames = 0, time = 0;
    Result retval = getCapturePositionImpl(mStream, &frames, &time);
    _hidl_cb(retval, frames, time);
    return Void();
}

}  // namespace implementation
}  // namespace V2_0
}  // namespace audio
}  // namespace hardware
}  // namespace android
