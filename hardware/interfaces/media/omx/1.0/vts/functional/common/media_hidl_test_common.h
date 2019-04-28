/*
 * Copyright 2017, The Android Open Source Project
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

#ifndef MEDIA_HIDL_TEST_COMMON_H
#define MEDIA_HIDL_TEST_COMMON_H

#ifdef __LP64__
#define OMX_ANDROID_COMPILE_AS_32BIT_ON_64BIT_PLATFORMS
#endif

#include <media/stagefright/foundation/ALooper.h>
#include <utils/Condition.h>
#include <utils/List.h>
#include <utils/Mutex.h>

#include <media/openmax/OMX_Index.h>
#include <media/openmax/OMX_Core.h>
#include <media/openmax/OMX_Component.h>
#include <media/openmax/OMX_IndexExt.h>
#include <media/openmax/OMX_AudioExt.h>
#include <media/openmax/OMX_VideoExt.h>

/* TIME OUTS (Wait time in dequeueMessage()) */

/* As component is switching states (loaded<->idle<->execute), dequeueMessage()
 * expects the events to be received within this duration */
#define DEFAULT_TIMEOUT 100000
/* Time interval between successive Input/Output enqueues */
#define DEFAULT_TIMEOUT_Q 2000
/* While the component is amidst a process call, asynchronous commands like
 * flush, change states can get delayed (at max by process call time). Instead
 * of waiting on DEFAULT_TIMEOUT, we give an additional leeway. */
#define DEFAULT_TIMEOUT_PE 500000

/* Breakout Timeout :: 5 sec*/
#define TIMEOUT_COUNTER_Q (5000000 / DEFAULT_TIMEOUT_Q)
#define TIMEOUT_COUNTER_PE (5000000 / DEFAULT_TIMEOUT_PE)

/*
 * Random Index used for monkey testing while get/set parameters
 */
#define RANDOM_INDEX 1729

#define ALIGN_POWER_OF_TWO(value, n) \
    (((value) + ((1 << (n)) - 1)) & ~((1 << (n)) - 1))

enum bufferOwner {
    client,
    component,
    unknown,
};

/*
 * TODO: below definitions are borrowed from Conversion.h.
 * This is not the ideal way to do it. Loose these definitions once you
 * include Conversion.h
 */
inline uint32_t toRawIndexType(OMX_INDEXTYPE l) {
    return static_cast<uint32_t>(l);
}

inline android::hardware::media::omx::V1_0::Status toStatus(
    android::status_t l) {
    return static_cast<android::hardware::media::omx::V1_0::Status>(l);
}

inline hidl_vec<uint8_t> inHidlBytes(void const* l, size_t size) {
    hidl_vec<uint8_t> t;
    t.setToExternal(static_cast<uint8_t*>(const_cast<void*>(l)), size, false);
    return t;
}

inline uint32_t toRawCommandType(OMX_COMMANDTYPE l) {
    return static_cast<uint32_t>(l);
}

/*
 * struct definitions
 */
struct BufferInfo {
    uint32_t id;
    bufferOwner owner;
    android::hardware::media::omx::V1_0::CodecBuffer omxBuffer;
    ::android::sp<IMemory> mMemory;
    int32_t slot;
};

struct FrameData {
    int bytesCount;
    uint32_t flags;
    uint32_t timestamp;
};

/*
 * Handle Callback functions EmptythisBuffer(), FillthisBuffer(),
 * EventHandler()
 */
struct CodecObserver : public IOmxObserver {
   public:
    CodecObserver(std::function<void(Message, const BufferInfo*)> fn)
        : callBack(fn) {}
    Return<void> onMessages(const hidl_vec<Message>& messages) override {
        android::Mutex::Autolock autoLock(msgLock);
        for (hidl_vec<Message>::const_iterator it = messages.begin();
             it != messages.end(); ++it) {
            msgQueue.push_back(*it);
        }
        msgCondition.signal();
        return Void();
    }
    android::hardware::media::omx::V1_0::Status dequeueMessage(
        Message* msg, int64_t timeoutUs,
        android::Vector<BufferInfo>* iBuffers = nullptr,
        android::Vector<BufferInfo>* oBuffers = nullptr) {
        int64_t finishBy = android::ALooper::GetNowUs() + timeoutUs;
        for (;;) {
            android::Mutex::Autolock autoLock(msgLock);
            android::List<Message>::iterator it = msgQueue.begin();
            while (it != msgQueue.end()) {
                if (it->type ==
                    android::hardware::media::omx::V1_0::Message::Type::EVENT) {
                    *msg = *it;
                    if (callBack) callBack(*it, nullptr);
                    it = msgQueue.erase(it);
                    // OMX_EventBufferFlag event is sent when the component has
                    // processed a buffer with its EOS flag set. This event is
                    // not sent by soft omx components. Vendor components can
                    // send this. From IOMX point of view, we will ignore this
                    // event.
                    if (msg->data.eventData.event == OMX_EventBufferFlag)
                        continue;
                    return ::android::hardware::media::omx::V1_0::Status::OK;
                } else if (it->type == android::hardware::media::omx::V1_0::
                                           Message::Type::FILL_BUFFER_DONE) {
                    if (oBuffers) {
                        size_t i;
                        for (i = 0; i < oBuffers->size(); ++i) {
                            if ((*oBuffers)[i].id ==
                                it->data.bufferData.buffer) {
                                if (callBack) callBack(*it, &(*oBuffers)[i]);
                                oBuffers->editItemAt(i).owner = client;
                                it = msgQueue.erase(it);
                                break;
                            }
                        }
                        EXPECT_LE(i, oBuffers->size());
                    }
                } else if (it->type == android::hardware::media::omx::V1_0::
                                           Message::Type::EMPTY_BUFFER_DONE) {
                    if (iBuffers) {
                        size_t i;
                        for (i = 0; i < iBuffers->size(); ++i) {
                            if ((*iBuffers)[i].id ==
                                it->data.bufferData.buffer) {
                                if (callBack) callBack(*it, &(*iBuffers)[i]);
                                iBuffers->editItemAt(i).owner = client;
                                it = msgQueue.erase(it);
                                break;
                            }
                        }
                        EXPECT_LE(i, iBuffers->size());
                    }
                } else {
                    EXPECT_TRUE(false) << "Received unexpected message";
                    ++it;
                }
            }
            int64_t delayUs = finishBy - android::ALooper::GetNowUs();
            if (delayUs < 0) return toStatus(android::TIMED_OUT);
            (timeoutUs < 0)
                ? msgCondition.wait(msgLock)
                : msgCondition.waitRelative(msgLock, delayUs * 1000ll);
        }
    }

    android::List<Message> msgQueue;
    android::Mutex msgLock;
    android::Condition msgCondition;
    std::function<void(Message, const BufferInfo*)> callBack;
};

/*
 * Useful Wrapper utilities
 */
template <class T>
void InitOMXParams(T* params) {
    params->nSize = sizeof(T);
    params->nVersion.s.nVersionMajor = 1;
    params->nVersion.s.nVersionMinor = 0;
    params->nVersion.s.nRevision = 0;
    params->nVersion.s.nStep = 0;
}

template <class T>
Return<android::hardware::media::omx::V1_0::Status> getParam(
    sp<IOmxNode> omxNode, OMX_INDEXTYPE omxIdx, T* params) {
    android::hardware::media::omx::V1_0::Status status;
    InitOMXParams(params);
    omxNode->getParameter(
        toRawIndexType(omxIdx), inHidlBytes(params, sizeof(*params)),
        [&status, &params](android::hardware::media::omx::V1_0::Status _s,
                           hidl_vec<uint8_t> const& outParams) {
            status = _s;
            std::copy(outParams.data(), outParams.data() + outParams.size(),
                      static_cast<uint8_t*>(static_cast<void*>(params)));
        });
    return status;
}

template <class T>
Return<android::hardware::media::omx::V1_0::Status> setParam(
    sp<IOmxNode> omxNode, OMX_INDEXTYPE omxIdx, T* params) {
    InitOMXParams(params);
    return omxNode->setParameter(toRawIndexType(omxIdx),
                                 inHidlBytes(params, sizeof(*params)));
}

template <class T>
Return<android::hardware::media::omx::V1_0::Status> getPortParam(
    sp<IOmxNode> omxNode, OMX_INDEXTYPE omxIdx, OMX_U32 nPortIndex, T* params) {
    android::hardware::media::omx::V1_0::Status status;
    InitOMXParams(params);
    params->nPortIndex = nPortIndex;
    omxNode->getParameter(
        toRawIndexType(omxIdx), inHidlBytes(params, sizeof(*params)),
        [&status, &params](android::hardware::media::omx::V1_0::Status _s,
                           hidl_vec<uint8_t> const& outParams) {
            status = _s;
            std::copy(outParams.data(), outParams.data() + outParams.size(),
                      static_cast<uint8_t*>(static_cast<void*>(params)));
        });
    return status;
}

template <class T>
Return<android::hardware::media::omx::V1_0::Status> setPortParam(
    sp<IOmxNode> omxNode, OMX_INDEXTYPE omxIdx, OMX_U32 nPortIndex, T* params) {
    InitOMXParams(params);
    params->nPortIndex = nPortIndex;
    return omxNode->setParameter(toRawIndexType(omxIdx),
                                 inHidlBytes(params, sizeof(*params)));
}

template <class T>
Return<android::hardware::media::omx::V1_0::Status> getPortConfig(
    sp<IOmxNode> omxNode, OMX_INDEXTYPE omxIdx, OMX_U32 nPortIndex, T* params) {
    android::hardware::media::omx::V1_0::Status status;
    InitOMXParams(params);
    params->nPortIndex = nPortIndex;
    omxNode->getConfig(
        toRawIndexType(omxIdx), inHidlBytes(params, sizeof(*params)),
        [&status, &params](android::hardware::media::omx::V1_0::Status _s,
                           hidl_vec<uint8_t> const& outParams) {
            status = _s;
            std::copy(outParams.data(), outParams.data() + outParams.size(),
                      static_cast<uint8_t*>(static_cast<void*>(params)));
        });
    return status;
}

template <class T>
Return<android::hardware::media::omx::V1_0::Status> setPortConfig(
    sp<IOmxNode> omxNode, OMX_INDEXTYPE omxIdx, OMX_U32 nPortIndex, T* params) {
    InitOMXParams(params);
    params->nPortIndex = nPortIndex;
    return omxNode->setConfig(toRawIndexType(omxIdx),
                              inHidlBytes(params, sizeof(*params)));
}

/*
 * common functions declarations
 */
Return<android::hardware::media::omx::V1_0::Status> setRole(
    sp<IOmxNode> omxNode, const char* role);

Return<android::hardware::media::omx::V1_0::Status> setPortBufferSize(
    sp<IOmxNode> omxNode, OMX_U32 portIndex, OMX_U32 size);

Return<android::hardware::media::omx::V1_0::Status> setVideoPortFormat(
    sp<IOmxNode> omxNode, OMX_U32 portIndex,
    OMX_VIDEO_CODINGTYPE eCompressionFormat, OMX_COLOR_FORMATTYPE eColorFormat,
    OMX_U32 xFramerate);

Return<android::hardware::media::omx::V1_0::Status> setAudioPortFormat(
    sp<IOmxNode> omxNode, OMX_U32 portIndex, OMX_AUDIO_CODINGTYPE eEncoding);

void allocateBuffer(sp<IOmxNode> omxNode, BufferInfo* buffer, OMX_U32 portIndex,
                    OMX_U32 nBufferSize, PortMode portMode);

void allocatePortBuffers(sp<IOmxNode> omxNode,
                         android::Vector<BufferInfo>* buffArray,
                         OMX_U32 portIndex,
                         PortMode portMode = PortMode::PRESET_BYTE_BUFFER,
                         bool allocGrap = false);

void changeStateLoadedtoIdle(sp<IOmxNode> omxNode, sp<CodecObserver> observer,
                             android::Vector<BufferInfo>* iBuffer,
                             android::Vector<BufferInfo>* oBuffer,
                             OMX_U32 kPortIndexInput, OMX_U32 kPortIndexOutput,
                             PortMode* portMode = nullptr,
                             bool allocGrap = false);

void changeStateIdletoLoaded(sp<IOmxNode> omxNode, sp<CodecObserver> observer,
                             android::Vector<BufferInfo>* iBuffer,
                             android::Vector<BufferInfo>* oBuffer,
                             OMX_U32 kPortIndexInput, OMX_U32 kPortIndexOutput);

void changeStateIdletoExecute(sp<IOmxNode> omxNode, sp<CodecObserver> observer);

void changeStateExecutetoIdle(sp<IOmxNode> omxNode, sp<CodecObserver> observer,
                              android::Vector<BufferInfo>* iBuffer,
                              android::Vector<BufferInfo>* oBuffer);

size_t getEmptyBufferID(android::Vector<BufferInfo>* buffArray);

void dispatchOutputBuffer(sp<IOmxNode> omxNode,
                          android::Vector<BufferInfo>* buffArray,
                          size_t bufferIndex,
                          PortMode portMode = PortMode::PRESET_BYTE_BUFFER);

void dispatchInputBuffer(sp<IOmxNode> omxNode,
                         android::Vector<BufferInfo>* buffArray,
                         size_t bufferIndex, int bytesCount, uint32_t flags,
                         uint64_t timestamp,
                         PortMode portMode = PortMode::PRESET_BYTE_BUFFER);

void flushPorts(sp<IOmxNode> omxNode, sp<CodecObserver> observer,
                android::Vector<BufferInfo>* iBuffer,
                android::Vector<BufferInfo>* oBuffer, OMX_U32 kPortIndexInput,
                OMX_U32 kPortIndexOutput,
                int64_t timeoutUs = DEFAULT_TIMEOUT_PE);

typedef void (*portreconfig)(sp<IOmxNode> omxNode, sp<CodecObserver> observer,
                             android::Vector<BufferInfo>* iBuffer,
                             android::Vector<BufferInfo>* oBuffer,
                             OMX_U32 kPortIndexInput, OMX_U32 kPortIndexOutput,
                             Message msg, PortMode oPortMode, void* args);
void testEOS(sp<IOmxNode> omxNode, sp<CodecObserver> observer,
             android::Vector<BufferInfo>* iBuffer,
             android::Vector<BufferInfo>* oBuffer, bool signalEOS,
             bool& eosFlag, PortMode* portMode = nullptr,
             portreconfig fptr = nullptr, OMX_U32 kPortIndexInput = 0,
             OMX_U32 kPortIndexOutput = 1, void* args = nullptr);

#endif  // MEDIA_HIDL_TEST_COMMON_H
