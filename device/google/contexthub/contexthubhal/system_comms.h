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

#ifndef _NANOHUB_SYSTEM_COMMS_H_
#define _NANOHUB_SYSTEM_COMMS_H_

#include <utils/Condition.h>

#include <condition_variable>
#include <map>
#include <mutex>
#include <vector>

#include <hardware/context_hub.h>
#include <nanohub/nanohub.h>

#include "nanohubhal.h"
#include "message_buf.h"

//rx: return 0 if handled, > 0 if not handled, < 0 if error happened

#define MSG_HANDLED 0

//messages to the HostIf nanoapp & their replies (mesages and replies both begin with u8 message_type)
#define NANOHUB_EXT_APPS_ON        0 // () -> (char success)
#define NANOHUB_EXT_APPS_OFF       1 // () -> (char success)
#define NANOHUB_EXT_APP_DELETE     2 // (u64 name) -> (char success)    //idempotent
#define NANOHUB_QUERY_MEMINFO      3 // () -> (mem_info)
#define NANOHUB_QUERY_APPS         4 // (u32 idxStart) -> (app_info[idxStart] OR EMPTY IF NO MORE)
#define NANOHUB_QUERY_RSA_KEYS     5 // (u32 byteOffset) -> (u8 data[1 or more bytes] OR EMPTY IF NO MORE)
#define NANOHUB_START_UPLOAD       6 // (char isOs, u32 totalLenToTx) -> (char success)
#define NANOHUB_CONT_UPLOAD        7 // (u32 offset, u8 data[]) -> (char success)
#define NANOHUB_FINISH_UPLOAD      8 // () -> (char success)
#define NANOHUB_REBOOT             9 // () -> (char success)

#define NANOHUB_APP_NOT_LOADED  (-1)
#define NANOHUB_APP_LOADED      (0)

#define NANOHUB_UPLOAD_CHUNK_SZ_MAX 64
#define NANOHUB_MEM_SZ_UNKNOWN      0xFFFFFFFFUL

namespace android {

namespace nanohub {

int system_comms_handle_rx(const nano_message *msg);
int system_comms_handle_tx(const hub_message_t *outMsg);

struct NanohubAppInfo {
    hub_app_name_t name;
    uint32_t version, flashUse, ramUse;
} __attribute__((packed));

struct MgmtStatus {
    union {
        uint32_t value;
        struct {
            uint8_t app;
            uint8_t task;
            uint8_t op;
            uint8_t erase;
        } __attribute__((packed));
    };
} __attribute__((packed));

struct NanohubMemInfo {
    //sizes
    uint32_t flashSz, blSz, osSz, sharedSz, eeSz;
    uint32_t ramSz;

    //use
    uint32_t blUse, osUse, sharedUse, eeUse;
    uint32_t ramUse;
} __attribute__((packed));

struct NanohubRsp {
    uint32_t cmd;
    int32_t status;
    explicit NanohubRsp(MessageBuf &buf, bool no_status = false);
};

inline bool operator == (const hub_app_name_t &a, const hub_app_name_t &b) {
    return a.id == b.id;
}

inline bool operator != (const hub_app_name_t &a, const hub_app_name_t &b) {
    return !(a == b);
}

class SystemComm {
private:

    /*
     * Nanohub HAL sessions
     *
     * Session is an object that can group several message exchanges with FW,
     * maintain state, and be waited for completion by someone else.
     *
     * As of this moment, since all sessions are triggered by client thread,
     * and all the exchange is happening in local worker thread, it is only possible
     * for client thread to wait on session completion.
     * Allowing sessions to wait on each other will require a worker thread pool.
     * It is now unnecessary, and not implemented.
     */
    class ISession {
    public:
        virtual int setup(const hub_message_t *app_msg) = 0;
        virtual int handleRx(MessageBuf &buf) = 0;
        virtual int getState() const = 0; // FSM state
        virtual int getStatus() const = 0; // execution status (result code)
        virtual void abort(int32_t) = 0;
        virtual ~ISession() {}
    };

    class SessionManager;

    class Session : public ISession {
        friend class SessionManager;

        mutable std::mutex mDoneMutex; // controls condition and state transitions
        std::condition_variable mDoneCond;
        volatile int mState;

    protected:
        mutable std::mutex mLock; // serializes message handling
        int32_t mStatus;

        enum {
            SESSION_INIT = 0,
            SESSION_DONE = 1,
            SESSION_USER = 2,
        };

        void complete() {
            std::unique_lock<std::mutex> lk(mDoneMutex);
            if (mState != SESSION_DONE) {
                mState = SESSION_DONE;
                lk.unlock();
                mDoneCond.notify_all();
            }
        }
        void abort(int32_t status) {
            std::lock_guard<std::mutex> _l(mLock);
            mStatus = status;
            complete();
        }
        void setState(int state) {
            if (state == SESSION_DONE) {
                complete();
            } else {
                std::lock_guard<std::mutex> _l(mDoneMutex);
                mState = state;
            }
        }
    public:
        Session() { mState = SESSION_INIT; mStatus = -1; }
        int getStatus() const {
            std::lock_guard<std::mutex> _l(mLock);
            return mStatus;
        }
        int wait() {
            std::unique_lock<std::mutex> lk(mDoneMutex);
            mDoneCond.wait(lk, [this] { return mState == SESSION_DONE; });
            return 0;
        }
        virtual int getState() const override {
            std::lock_guard<std::mutex> _l(mDoneMutex);
            return mState;
        }
        virtual bool isDone() const {
            std::lock_guard<std::mutex> _l(mDoneMutex);
            return mState == SESSION_DONE;
        }
        virtual bool isRunning() const {
            std::lock_guard<std::mutex> _l(mDoneMutex);
            return mState > SESSION_DONE;
        }
    };

    class AppMgmtSession : public Session {
        enum {
            TRANSFER = SESSION_USER,
            FINISH,
            RUN,
            RUN_FAILED,
            REBOOT,
            MGMT,
        };
        uint32_t mCmd; // LOAD_APP, UNLOAD_APP, ENABLE_APP, DISABLE_APP
        uint32_t mResult;
        std::vector<uint8_t> mData;
        uint32_t mLen;
        uint32_t mPos;
        hub_app_name_t mAppName;

        int setupMgmt(const hub_message_t *appMsg, uint32_t cmd);
        int handleTransfer(NanohubRsp &rsp);
        int handleFinish(NanohubRsp &rsp);
        int handleRun(NanohubRsp &rsp);
        int handleRunFailed(NanohubRsp &rsp);
        int handleReboot(NanohubRsp &rsp);
        int handleMgmt(NanohubRsp &rsp);
    public:
        AppMgmtSession() {
            mCmd = 0;
            mResult = 0;
            mPos = 0;
            mLen = 0;
            memset(&mAppName, 0, sizeof(mAppName));
        }
        virtual int handleRx(MessageBuf &buf) override;
        virtual int setup(const hub_message_t *app_msg) override;
    };

    class MemInfoSession : public Session {
    public:
        virtual int setup(const hub_message_t *app_msg) override;
        virtual int handleRx(MessageBuf &buf) override;
    };

    class KeyInfoSession  : public Session {
        std::vector<uint8_t> mRsaKeyData;
        int requestRsaKeys(void);
    public:
        virtual int setup(const hub_message_t *) override;
        virtual int handleRx(MessageBuf &buf) override;
        bool haveKeys() const {
            std::lock_guard<std::mutex> _l(mLock);
            return mRsaKeyData.size() > 0 && !isRunning();
        }
    };

    class AppInfoSession : public Session {
        std::vector<hub_app_info> mAppInfo;
        int requestNext();
    public:
        virtual int setup(const hub_message_t *) override;
        virtual int handleRx(MessageBuf &buf) override;
    };

    class SessionManager {
        typedef std::map<int, Session* > SessionMap;

        std::mutex lock;
        SessionMap sessions_;

        bool isActive(const SessionMap::iterator &pos) const
        {
            return !pos->second->isDone();
        }
        void next(SessionMap::iterator &pos)
        {
            isActive(pos) ? pos++ : pos = sessions_.erase(pos);
        }

    public:
        int handleRx(MessageBuf &buf);
        int setup_and_add(int id, Session *session, const hub_message_t *appMsg);
    } mSessions;

    const hub_app_name_t mHostIfAppName = {
        .id = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0)
    };

    static SystemComm *getSystem() {
        // this is thread-safe in c++11
        static SystemComm theInstance;
        return &theInstance;
    }

    SystemComm () = default;
    ~SystemComm() = default;

    int doHandleTx(const hub_message_t *txMsg);
    int doHandleRx(const nano_message *rxMsg);

    static void sendToApp(uint32_t typ, const void *data, uint32_t len) {
        if (NanoHub::messageTracingEnabled()) {
            dumpBuffer("HAL -> APP", get_hub_info()->os_app_name, typ, data, len);
        }
        NanoHub::sendToApp(HubMessage(&get_hub_info()->os_app_name, typ, data, len));
    }
    static int sendToSystem(const void *data, size_t len);

    KeyInfoSession mKeySession;
    AppMgmtSession mAppMgmtSession;
    AppInfoSession mAppInfoSession;
    MemInfoSession mMemInfoSession;

public:
    static int handleTx(const hub_message_t *txMsg) {
        return getSystem()->doHandleTx(txMsg);
    }
    static int handleRx(const nano_message *rxMsg) {
        return getSystem()->doHandleRx(rxMsg);
    }
};

}; // namespace nanohub

}; // namespace android

#endif
