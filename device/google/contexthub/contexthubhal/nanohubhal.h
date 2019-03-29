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

#ifndef _NANOHUB_HAL_H_
#define _NANOHUB_HAL_H_

#include <mutex>
#include <thread>
#include <list>

#include <hardware/context_hub.h>

#include <nanohub/nanohub.h>

//as per protocol
#define MAX_RX_PACKET               128
#define APP_FROM_HOST_EVENT_ID      0x000000F8
#define APP_FROM_HOST_CHRE_EVENT_ID 0x000000F9

namespace android {

namespace nanohub {

void dumpBuffer(const char *pfx, const hub_app_name_t &appId, uint32_t evtId, const void *data, size_t len, int status = 0);

struct nano_message_chre {
    HostMsgHdrChre hdr;
    uint8_t data[MAX_RX_PACKET];
} __attribute__((packed));

struct nano_message {
    HostMsgHdr hdr;
    uint8_t data[MAX_RX_PACKET];
} __attribute__((packed));

class HubMessage : public hub_message_t {
    std::unique_ptr<uint8_t> data_;
public:
    HubMessage(const HubMessage &other) = delete;
    HubMessage &operator = (const HubMessage &other) = delete;

    HubMessage(const hub_app_name_t *name, uint32_t typ, const void *data, uint32_t len) {
        app_name = *name;
        message_type = typ;
        message_len = len;
        message = data;
        if (len > 0 && data != nullptr) {
            data_ = std::unique_ptr<uint8_t>(new uint8_t[len]);
            memcpy(data_.get(), data, len);
            message = data_.get();
        }
    }

    HubMessage(HubMessage &&other) {
        *this = (HubMessage &&)other;
    }

    HubMessage &operator = (HubMessage &&other) {
        *static_cast<hub_message_t *>(this) = static_cast<hub_message_t>(other);
        data_ = std::move(other.data_);
        other.message = nullptr;
        other.message_len = 0;
        return *this;
    }
};

class NanoHub {
    std::mutex mLock;
    bool mAppQuit;
    std::mutex mAppTxLock;
    std::condition_variable mAppTxCond;
    std::list<HubMessage> mAppTxQueue;
    std::thread mPollThread;
    std::thread mAppThread;
    context_hub_callback *mMsgCbkFunc;
    int mThreadClosingPipe[2];
    int mFd; // [0] is read end
    void * mMsgCbkData;

    NanoHub();
    ~NanoHub();

    void reset() {
        mThreadClosingPipe[0] = -1;
        mThreadClosingPipe[1] = -1;
        mFd = -1;
        mMsgCbkData = nullptr;
        mMsgCbkFunc = nullptr;
        mAppQuit = false;
    }

    void* runAppTx();
    void* runDeviceRx();

    int openHub();
    int closeHub();

    static NanoHub *hubInstance() {
        static NanoHub theHub;
        return &theHub;
    }

    int doSubscribeMessages(uint32_t hub_id, context_hub_callback *cbk, void *cookie);
    int doSendToNanohub(uint32_t hub_id, const hub_message_t *msg);
    int doSendToDevice(const hub_app_name_t name, const void *data, uint32_t len, uint32_t messageType);
    void doSendToApp(HubMessage &&msg);

    static constexpr unsigned int FL_MESSAGE_TRACING = 1;

    unsigned int mFlags = 0;

public:

    // debugging interface

    static bool messageTracingEnabled() {
        return hubInstance()->mFlags & FL_MESSAGE_TRACING;
    }
    static unsigned int getDebugFlags() {
        return hubInstance()->mFlags;
    }
    static void setDebugFlags(unsigned int flags) {
        hubInstance()->mFlags = flags;
    }

    // messaging interface

    // define callback to invoke for APP messages
    static int subscribeMessages(uint32_t hub_id, context_hub_callback *cbk, void *cookie) {
        return hubInstance()->doSubscribeMessages(hub_id, cbk, cookie);
    }
    // all messages from APP go here
    static int sendToNanohub(uint32_t hub_id, const hub_message_t *msg) {
        return hubInstance()->doSendToNanohub(hub_id, msg);
    }
    // passes message to kernel driver directly
    static int sendToDevice(const hub_app_name_t *name, const void *data, uint32_t len) {
        return hubInstance()->doSendToDevice(*name, data, len, 0);
    }
    // passes message to APP via callback
    static void sendToApp(HubMessage &&msg) {
        hubInstance()->doSendToApp((HubMessage &&)msg);
    }
};

}; // namespace nanohub

}; // namespace android

#endif
