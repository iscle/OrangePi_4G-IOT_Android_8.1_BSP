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

#define LOG_TAG "StreamOutHAL"
//#define LOG_NDEBUG 0
#define ATRACE_TAG ATRACE_TAG_AUDIO

#include <memory>

#include <android/log.h>
#include <hardware/audio.h>
#include <utils/Trace.h>

#include "StreamOut.h"
#include "Util.h"

namespace android {
namespace hardware {
namespace audio {
namespace V2_0 {
namespace implementation {

using ::android::hardware::audio::common::V2_0::ThreadInfo;

namespace {

class WriteThread : public Thread {
   public:
    // WriteThread's lifespan never exceeds StreamOut's lifespan.
    WriteThread(std::atomic<bool>* stop, audio_stream_out_t* stream,
                StreamOut::CommandMQ* commandMQ, StreamOut::DataMQ* dataMQ,
                StreamOut::StatusMQ* statusMQ, EventFlag* efGroup)
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
    virtual ~WriteThread() {}

   private:
    std::atomic<bool>* mStop;
    audio_stream_out_t* mStream;
    StreamOut::CommandMQ* mCommandMQ;
    StreamOut::DataMQ* mDataMQ;
    StreamOut::StatusMQ* mStatusMQ;
    EventFlag* mEfGroup;
    std::unique_ptr<uint8_t[]> mBuffer;
    IStreamOut::WriteStatus mStatus;

    bool threadLoop() override;

    void doGetLatency();
    void doGetPresentationPosition();
    void doWrite();
};

void WriteThread::doWrite() {
    const size_t availToRead = mDataMQ->availableToRead();
    mStatus.retval = Result::OK;
    mStatus.reply.written = 0;
    if (mDataMQ->read(&mBuffer[0], availToRead)) {
        ssize_t writeResult = mStream->write(mStream, &mBuffer[0], availToRead);
        if (writeResult >= 0) {
            mStatus.reply.written = writeResult;
        } else {
            mStatus.retval = Stream::analyzeStatus("write", writeResult);
        }
    }
}

void WriteThread::doGetPresentationPosition() {
    mStatus.retval = StreamOut::getPresentationPositionImpl(
        mStream, &mStatus.reply.presentationPosition.frames,
        &mStatus.reply.presentationPosition.timeStamp);
}

void WriteThread::doGetLatency() {
    mStatus.retval = Result::OK;
    mStatus.reply.latencyMs = mStream->get_latency(mStream);
}

bool WriteThread::threadLoop() {
    // This implementation doesn't return control back to the Thread until it
    // decides to stop,
    // as the Thread uses mutexes, and this can lead to priority inversion.
    while (!std::atomic_load_explicit(mStop, std::memory_order_acquire)) {
        uint32_t efState = 0;
        mEfGroup->wait(static_cast<uint32_t>(MessageQueueFlagBits::NOT_EMPTY),
                       &efState);
        if (!(efState &
              static_cast<uint32_t>(MessageQueueFlagBits::NOT_EMPTY))) {
            continue;  // Nothing to do.
        }
        if (!mCommandMQ->read(&mStatus.replyTo)) {
            continue;  // Nothing to do.
        }
        switch (mStatus.replyTo) {
            case IStreamOut::WriteCommand::WRITE:
                doWrite();
                break;
            case IStreamOut::WriteCommand::GET_PRESENTATION_POSITION:
                doGetPresentationPosition();
                break;
            case IStreamOut::WriteCommand::GET_LATENCY:
                doGetLatency();
                break;
            default:
                ALOGE("Unknown write thread command code %d", mStatus.replyTo);
                mStatus.retval = Result::NOT_SUPPORTED;
                break;
        }
        if (!mStatusMQ->write(&mStatus)) {
            ALOGE("status message queue write failed");
        }
        mEfGroup->wake(static_cast<uint32_t>(MessageQueueFlagBits::NOT_FULL));
    }

    return false;
}

}  // namespace

StreamOut::StreamOut(const sp<Device>& device, audio_stream_out_t* stream)
    : mIsClosed(false),
      mDevice(device),
      mStream(stream),
      mStreamCommon(new Stream(&stream->common)),
      mStreamMmap(new StreamMmap<audio_stream_out_t>(stream)),
      mEfGroup(nullptr),
      mStopWriteThread(false) {}

StreamOut::~StreamOut() {
    ATRACE_CALL();
    close();
    if (mWriteThread.get()) {
        ATRACE_NAME("mWriteThread->join");
        status_t status = mWriteThread->join();
        ALOGE_IF(status, "write thread exit error: %s", strerror(-status));
    }
    if (mEfGroup) {
        status_t status = EventFlag::deleteEventFlag(&mEfGroup);
        ALOGE_IF(status, "write MQ event flag deletion error: %s",
                 strerror(-status));
    }
    mCallback.clear();
    mDevice->closeOutputStream(mStream);
    mStream = nullptr;
}

// Methods from ::android::hardware::audio::V2_0::IStream follow.
Return<uint64_t> StreamOut::getFrameSize() {
    return audio_stream_out_frame_size(mStream);
}

Return<uint64_t> StreamOut::getFrameCount() {
    return mStreamCommon->getFrameCount();
}

Return<uint64_t> StreamOut::getBufferSize() {
    return mStreamCommon->getBufferSize();
}

Return<uint32_t> StreamOut::getSampleRate() {
    return mStreamCommon->getSampleRate();
}

Return<void> StreamOut::getSupportedSampleRates(
    getSupportedSampleRates_cb _hidl_cb) {
    return mStreamCommon->getSupportedSampleRates(_hidl_cb);
}

Return<Result> StreamOut::setSampleRate(uint32_t sampleRateHz) {
    return mStreamCommon->setSampleRate(sampleRateHz);
}

Return<AudioChannelMask> StreamOut::getChannelMask() {
    return mStreamCommon->getChannelMask();
}

Return<void> StreamOut::getSupportedChannelMasks(
    getSupportedChannelMasks_cb _hidl_cb) {
    return mStreamCommon->getSupportedChannelMasks(_hidl_cb);
}

Return<Result> StreamOut::setChannelMask(AudioChannelMask mask) {
    return mStreamCommon->setChannelMask(mask);
}

Return<AudioFormat> StreamOut::getFormat() {
    return mStreamCommon->getFormat();
}

Return<void> StreamOut::getSupportedFormats(getSupportedFormats_cb _hidl_cb) {
    return mStreamCommon->getSupportedFormats(_hidl_cb);
}

Return<Result> StreamOut::setFormat(AudioFormat format) {
    return mStreamCommon->setFormat(format);
}

Return<void> StreamOut::getAudioProperties(getAudioProperties_cb _hidl_cb) {
    return mStreamCommon->getAudioProperties(_hidl_cb);
}

Return<Result> StreamOut::addEffect(uint64_t effectId) {
    return mStreamCommon->addEffect(effectId);
}

Return<Result> StreamOut::removeEffect(uint64_t effectId) {
    return mStreamCommon->removeEffect(effectId);
}

Return<Result> StreamOut::standby() {
    return mStreamCommon->standby();
}

Return<AudioDevice> StreamOut::getDevice() {
    return mStreamCommon->getDevice();
}

Return<Result> StreamOut::setDevice(const DeviceAddress& address) {
    return mStreamCommon->setDevice(address);
}

Return<Result> StreamOut::setConnectedState(const DeviceAddress& address,
                                            bool connected) {
    return mStreamCommon->setConnectedState(address, connected);
}

Return<Result> StreamOut::setHwAvSync(uint32_t hwAvSync) {
    return mStreamCommon->setHwAvSync(hwAvSync);
}

Return<void> StreamOut::getParameters(const hidl_vec<hidl_string>& keys,
                                      getParameters_cb _hidl_cb) {
    return mStreamCommon->getParameters(keys, _hidl_cb);
}

Return<Result> StreamOut::setParameters(
    const hidl_vec<ParameterValue>& parameters) {
    return mStreamCommon->setParameters(parameters);
}

Return<void> StreamOut::debugDump(const hidl_handle& fd) {
    return mStreamCommon->debugDump(fd);
}

Return<Result> StreamOut::close() {
    if (mIsClosed) return Result::INVALID_STATE;
    mIsClosed = true;
    if (mWriteThread.get()) {
        mStopWriteThread.store(true, std::memory_order_release);
    }
    if (mEfGroup) {
        mEfGroup->wake(static_cast<uint32_t>(MessageQueueFlagBits::NOT_EMPTY));
    }
    return Result::OK;
}

// Methods from ::android::hardware::audio::V2_0::IStreamOut follow.
Return<uint32_t> StreamOut::getLatency() {
    return mStream->get_latency(mStream);
}

Return<Result> StreamOut::setVolume(float left, float right) {
    if (mStream->set_volume == NULL) {
        return Result::NOT_SUPPORTED;
    }
    if (!isGainNormalized(left)) {
        ALOGW("Can not set a stream output volume {%f, %f} outside [0,1]", left,
              right);
        return Result::INVALID_ARGUMENTS;
    }
    return Stream::analyzeStatus("set_volume",
                                 mStream->set_volume(mStream, left, right));
}

Return<void> StreamOut::prepareForWriting(uint32_t frameSize,
                                          uint32_t framesCount,
                                          prepareForWriting_cb _hidl_cb) {
    status_t status;
    ThreadInfo threadInfo = {0, 0};

    // Wrap the _hidl_cb to return an error
    auto sendError = [this, &threadInfo, &_hidl_cb](Result result) {
        _hidl_cb(result, CommandMQ::Descriptor(), DataMQ::Descriptor(),
                 StatusMQ::Descriptor(), threadInfo);

    };

    // Create message queues.
    if (mDataMQ) {
        ALOGE("the client attempts to call prepareForWriting twice");
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
    auto tempWriteThread = std::make_unique<WriteThread>(
        &mStopWriteThread, mStream, tempCommandMQ.get(), tempDataMQ.get(),
        tempStatusMQ.get(), tempElfGroup.get());
    if (!tempWriteThread->init()) {
        ALOGW("failed to start writer thread: %s", strerror(-status));
        sendError(Result::INVALID_ARGUMENTS);
        return Void();
    }
    status = tempWriteThread->run("writer", PRIORITY_URGENT_AUDIO);
    if (status != OK) {
        ALOGW("failed to start writer thread: %s", strerror(-status));
        sendError(Result::INVALID_ARGUMENTS);
        return Void();
    }

    mCommandMQ = std::move(tempCommandMQ);
    mDataMQ = std::move(tempDataMQ);
    mStatusMQ = std::move(tempStatusMQ);
    mWriteThread = tempWriteThread.release();
    mEfGroup = tempElfGroup.release();
    threadInfo.pid = getpid();
    threadInfo.tid = mWriteThread->getTid();
    _hidl_cb(Result::OK, *mCommandMQ->getDesc(), *mDataMQ->getDesc(),
             *mStatusMQ->getDesc(), threadInfo);
    return Void();
}

Return<void> StreamOut::getRenderPosition(getRenderPosition_cb _hidl_cb) {
    uint32_t halDspFrames;
    Result retval = Stream::analyzeStatus(
        "get_render_position",
        mStream->get_render_position(mStream, &halDspFrames));
    _hidl_cb(retval, halDspFrames);
    return Void();
}

Return<void> StreamOut::getNextWriteTimestamp(
    getNextWriteTimestamp_cb _hidl_cb) {
    Result retval(Result::NOT_SUPPORTED);
    int64_t timestampUs = 0;
    if (mStream->get_next_write_timestamp != NULL) {
        retval = Stream::analyzeStatus(
            "get_next_write_timestamp",
            mStream->get_next_write_timestamp(mStream, &timestampUs));
    }
    _hidl_cb(retval, timestampUs);
    return Void();
}

Return<Result> StreamOut::setCallback(const sp<IStreamOutCallback>& callback) {
    if (mStream->set_callback == NULL) return Result::NOT_SUPPORTED;
    int result = mStream->set_callback(mStream, StreamOut::asyncCallback, this);
    if (result == 0) {
        mCallback = callback;
    }
    return Stream::analyzeStatus("set_callback", result);
}

Return<Result> StreamOut::clearCallback() {
    if (mStream->set_callback == NULL) return Result::NOT_SUPPORTED;
    mCallback.clear();
    return Result::OK;
}

// static
int StreamOut::asyncCallback(stream_callback_event_t event, void*,
                             void* cookie) {
    wp<StreamOut> weakSelf(reinterpret_cast<StreamOut*>(cookie));
    sp<StreamOut> self = weakSelf.promote();
    if (self == nullptr || self->mCallback == nullptr) return 0;
    ALOGV("asyncCallback() event %d", event);
    switch (event) {
        case STREAM_CBK_EVENT_WRITE_READY:
            self->mCallback->onWriteReady();
            break;
        case STREAM_CBK_EVENT_DRAIN_READY:
            self->mCallback->onDrainReady();
            break;
        case STREAM_CBK_EVENT_ERROR:
            self->mCallback->onError();
            break;
        default:
            ALOGW("asyncCallback() unknown event %d", event);
            break;
    }
    return 0;
}

Return<void> StreamOut::supportsPauseAndResume(
    supportsPauseAndResume_cb _hidl_cb) {
    _hidl_cb(mStream->pause != NULL, mStream->resume != NULL);
    return Void();
}

Return<Result> StreamOut::pause() {
    return mStream->pause != NULL
               ? Stream::analyzeStatus("pause", mStream->pause(mStream))
               : Result::NOT_SUPPORTED;
}

Return<Result> StreamOut::resume() {
    return mStream->resume != NULL
               ? Stream::analyzeStatus("resume", mStream->resume(mStream))
               : Result::NOT_SUPPORTED;
}

Return<bool> StreamOut::supportsDrain() {
    return mStream->drain != NULL;
}

Return<Result> StreamOut::drain(AudioDrain type) {
    return mStream->drain != NULL
               ? Stream::analyzeStatus(
                     "drain",
                     mStream->drain(mStream,
                                    static_cast<audio_drain_type_t>(type)))
               : Result::NOT_SUPPORTED;
}

Return<Result> StreamOut::flush() {
    return mStream->flush != NULL
               ? Stream::analyzeStatus("flush", mStream->flush(mStream))
               : Result::NOT_SUPPORTED;
}

// static
Result StreamOut::getPresentationPositionImpl(audio_stream_out_t* stream,
                                              uint64_t* frames,
                                              TimeSpec* timeStamp) {
    // Don't logspam on EINVAL--it's normal for get_presentation_position
    // to return it sometimes. EAGAIN may be returned by A2DP audio HAL
    // implementation. ENODATA can also be reported while the writer is
    // continuously querying it, but the stream has been stopped.
    static const std::vector<int> ignoredErrors{EINVAL, EAGAIN, ENODATA};
    Result retval(Result::NOT_SUPPORTED);
    if (stream->get_presentation_position == NULL) return retval;
    struct timespec halTimeStamp;
    retval = Stream::analyzeStatus("get_presentation_position",
                                   stream->get_presentation_position(stream, frames, &halTimeStamp),
                                   ignoredErrors);
    if (retval == Result::OK) {
        timeStamp->tvSec = halTimeStamp.tv_sec;
        timeStamp->tvNSec = halTimeStamp.tv_nsec;
    }
    return retval;
}

Return<void> StreamOut::getPresentationPosition(
    getPresentationPosition_cb _hidl_cb) {
    uint64_t frames = 0;
    TimeSpec timeStamp = {0, 0};
    Result retval = getPresentationPositionImpl(mStream, &frames, &timeStamp);
    _hidl_cb(retval, frames, timeStamp);
    return Void();
}

Return<Result> StreamOut::start() {
    return mStreamMmap->start();
}

Return<Result> StreamOut::stop() {
    return mStreamMmap->stop();
}

Return<void> StreamOut::createMmapBuffer(int32_t minSizeFrames,
                                         createMmapBuffer_cb _hidl_cb) {
    return mStreamMmap->createMmapBuffer(
        minSizeFrames, audio_stream_out_frame_size(mStream), _hidl_cb);
}

Return<void> StreamOut::getMmapPosition(getMmapPosition_cb _hidl_cb) {
    return mStreamMmap->getMmapPosition(_hidl_cb);
}

}  // namespace implementation
}  // namespace V2_0
}  // namespace audio
}  // namespace hardware
}  // namespace android
