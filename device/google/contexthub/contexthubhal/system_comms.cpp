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

#define LOG_TAG "NanohubHAL"

#include <cassert>
#include <cerrno>
#include <cinttypes>

#include <endian.h>

#include <vector>

#include <log/log.h>

#include <endian.h>

#include <hardware/context_hub.h>
#include "nanohub_perdevice.h"
#include "system_comms.h"
#include "nanohubhal.h"

namespace android {

namespace nanohub {

static void readAppName(MessageBuf &buf, hub_app_name_t &name)
{
    name.id = buf.readU64();
}

static void writeAppName(MessageBuf &buf, const hub_app_name_t &name)
{
    buf.writeU64(name.id);
}

static void readNanohubAppInfo(MessageBuf &buf, NanohubAppInfo &info)
{
    size_t pos = buf.getPos();
    readAppName(buf, info.name);
    info.version = buf.readU32();
    info.flashUse = buf.readU32();
    info.ramUse = buf.readU32();
    if ((buf.getPos() - pos) != sizeof(info)) {
        ALOGE("%s: failed to read object", __func__);
    }
}

static void readNanohubMemInfo(MessageBuf &buf,  NanohubMemInfo &mi)
{
    size_t pos = buf.getPos();
    mi.flashSz = buf.readU32();
    mi.blSz = buf.readU32();
    mi.osSz = buf.readU32();
    mi.sharedSz = buf.readU32();
    mi.eeSz = buf.readU32();
    mi.ramSz = buf.readU32();

    mi.blUse = buf.readU32();
    mi.osUse = buf.readU32();
    mi.sharedUse = buf.readU32();
    mi.eeUse = buf.readU32();
    mi.ramUse = buf.readU32();
    if ((buf.getPos() - pos) != sizeof(mi)) {
        ALOGE("%s: failed to read object", __func__);
    }
}

NanohubRsp::NanohubRsp(MessageBuf &buf, bool no_status)
{
    // all responses start with command
    // most of them have 4-byte status (result code)
    buf.reset();
    cmd = buf.readU8();
    if (!buf.getSize()) {
        status = -EINVAL;
    } else if (no_status) {
        status = 0;
    } else {
        status = buf.readU32();
    }
}

int SystemComm::sendToSystem(const void *data, size_t len)
{
    if (NanoHub::messageTracingEnabled()) {
        dumpBuffer("HAL -> SYS", getSystem()->mHostIfAppName, 0, data, len);
    }
    return NanoHub::sendToDevice(&getSystem()->mHostIfAppName, data, len);
}

int SystemComm::AppInfoSession::setup(const hub_message_t *)
{
    std::lock_guard<std::mutex> _l(mLock);
    int suggestedSize = mAppInfo.size() ? mAppInfo.size() : 20;

    mAppInfo.clear();
    mAppInfo.reserve(suggestedSize);
    setState(SESSION_USER);

    return requestNext();
}

inline hub_app_name_t deviceAppNameToHost(const hub_app_name_t src)
{
    hub_app_name_t res = { .id = le64toh(src.id) };
    return res;
}

inline hub_app_name_t hostAppNameToDevice(const hub_app_name_t src)
{
    hub_app_name_t res = { .id = htole64(src.id) };
    return res;
}

int SystemComm::AppInfoSession::handleRx(MessageBuf &buf)
{
    std::lock_guard<std::mutex> _l(mLock);

    NanohubRsp rsp(buf, true);
    if (rsp.cmd != NANOHUB_QUERY_APPS) {
        return 1;
    }
    size_t len = buf.getRoom();
    if (len != sizeof(NanohubAppInfo) && len) {
        ALOGE("%s: Invalid data size; have %zu, need %zu", __func__,
              len, sizeof(NanohubAppInfo));
        return -EINVAL;
    }
    if (getState() != SESSION_USER) {
        ALOGE("%s: Invalid state; have %d, need %d", __func__, getState(), SESSION_USER);
        return -EINVAL;
    }
    if (len) {
        NanohubAppInfo info;
        readNanohubAppInfo(buf, info);
        hub_app_info appInfo;
        appInfo.num_mem_ranges = 0;
        if (info.flashUse != NANOHUB_MEM_SZ_UNKNOWN) {
            mem_range_t &range = appInfo.mem_usage[appInfo.num_mem_ranges++];
            range.type = HUB_MEM_TYPE_MAIN;
            range.total_bytes = info.flashUse;
        }
        if (info.ramUse != NANOHUB_MEM_SZ_UNKNOWN) {
            mem_range_t &range = appInfo.mem_usage[appInfo.num_mem_ranges++];
            range.type = HUB_MEM_TYPE_RAM;
            range.total_bytes = info.ramUse;
        }

        appInfo.app_name = info.name;
        appInfo.version = info.version;

        mAppInfo.push_back(appInfo);
        return requestNext();
    } else {
        sendToApp(CONTEXT_HUB_QUERY_APPS,
                        static_cast<const void *>(mAppInfo.data()),
                        mAppInfo.size() * sizeof(mAppInfo[0]));
        complete();
    }

    return 0;
}

int SystemComm::AppInfoSession::requestNext()
{
    char data[MAX_RX_PACKET];
    MessageBuf buf(data, sizeof(data));
    buf.writeU8(NANOHUB_QUERY_APPS);
    buf.writeU32(mAppInfo.size());
    return sendToSystem(buf.getData(), buf.getPos());
}

int SystemComm::MemInfoSession::setup(const hub_message_t *)
{
    std::lock_guard<std::mutex> _l(mLock);
    char data[MAX_RX_PACKET];
    MessageBuf buf(data, sizeof(data));
    buf.writeU8(NANOHUB_QUERY_MEMINFO);

    setState(SESSION_USER);
    return sendToSystem(buf.getData(), buf.getPos());
}

int SystemComm::MemInfoSession::handleRx(MessageBuf &buf)
{
    std::lock_guard<std::mutex> _l(mLock);
    NanohubRsp rsp(buf, true);

    if (rsp.cmd != NANOHUB_QUERY_MEMINFO)
        return 1;

    size_t len = buf.getRoom();

    if (len != sizeof(NanohubMemInfo)) {
        ALOGE("%s: Invalid data size: %zu", __func__, len);
        return -EINVAL;
    }
    if (getState() != SESSION_USER) {
        ALOGE("%s: Invalid state; have %d, need %d", __func__, getState(), SESSION_USER);
        return -EINVAL;
    }

    NanohubMemInfo mi;
    readNanohubMemInfo(buf, mi);
    std::vector<mem_range_t> ranges;
    ranges.reserve(4);

    //if each is valid, copy to output area
    if (mi.sharedSz != NANOHUB_MEM_SZ_UNKNOWN &&
        mi.sharedUse != NANOHUB_MEM_SZ_UNKNOWN)
        ranges.push_back({
            .type = HUB_MEM_TYPE_MAIN,
            .total_bytes = mi.sharedSz,
            .free_bytes = mi.sharedSz - mi.sharedUse,
        });

    if (mi.osSz != NANOHUB_MEM_SZ_UNKNOWN &&
        mi.osUse != NANOHUB_MEM_SZ_UNKNOWN)
        ranges.push_back({
            .type = HUB_MEM_TYPE_OS,
            .total_bytes = mi.osSz,
            .free_bytes = mi.osSz - mi.osUse,
        });

    if (mi.eeSz != NANOHUB_MEM_SZ_UNKNOWN &&
        mi.eeUse != NANOHUB_MEM_SZ_UNKNOWN)
        ranges.push_back({
            .type = HUB_MEM_TYPE_EEDATA,
            .total_bytes = mi.eeSz,
            .free_bytes = mi.eeSz - mi.eeUse,
        });

    if (mi.ramSz != NANOHUB_MEM_SZ_UNKNOWN &&
        mi.ramUse != NANOHUB_MEM_SZ_UNKNOWN)
        ranges.push_back({
            .type = HUB_MEM_TYPE_RAM,
            .total_bytes = mi.ramSz,
            .free_bytes = mi.ramSz - mi.ramUse,
        });

    //send it out
    sendToApp(CONTEXT_HUB_QUERY_MEMORY,
              static_cast<const void *>(ranges.data()),
              ranges.size() * sizeof(ranges[0]));

    complete();

    return 0;
}

int SystemComm::AppMgmtSession::setup(const hub_message_t *appMsg)
{
    std::lock_guard<std::mutex> _l(mLock);

    char data[MAX_RX_PACKET];
    MessageBuf buf(data, sizeof(data));
    const uint8_t *msgData = static_cast<const uint8_t*>(appMsg->message);

    mCmd = appMsg->message_type;
    mLen = appMsg->message_len;
    mPos = 0;

    switch (mCmd) {
    case  CONTEXT_HUB_APPS_ENABLE:
        return setupMgmt(appMsg, NANOHUB_EXT_APPS_ON);
    case  CONTEXT_HUB_APPS_DISABLE:
        return setupMgmt(appMsg, NANOHUB_EXT_APPS_OFF);
    case  CONTEXT_HUB_UNLOAD_APP:
        return setupMgmt(appMsg, NANOHUB_EXT_APP_DELETE);
    case  CONTEXT_HUB_LOAD_APP:
    {
        mData.clear();
        mData = std::vector<uint8_t>(msgData, msgData + mLen);
        const load_app_request_t *appReq = static_cast<const load_app_request_t*>(appMsg->message);
        if (appReq == nullptr || mLen <= sizeof(*appReq)) {
            ALOGE("%s: Invalid app header: too short\n", __func__);
            return -EINVAL;
        }
        mAppName = appReq->app_binary.app_id;
        setState(TRANSFER);

        buf.writeU8(NANOHUB_START_UPLOAD);
        buf.writeU8(0);
        buf.writeU32(mLen);
        return sendToSystem(buf.getData(), buf.getPos());
    }

    case  CONTEXT_HUB_OS_REBOOT:
        setState(REBOOT);
        buf.writeU8(NANOHUB_REBOOT);
        return sendToSystem(buf.getData(), buf.getPos());
    }

    return -EINVAL;
}

int SystemComm::AppMgmtSession::setupMgmt(const hub_message_t *appMsg, uint32_t cmd)
{
    const hub_app_name_t &appName = *static_cast<const hub_app_name_t*>(appMsg->message);
    if (appMsg->message_len != sizeof(appName)) {
        return -EINVAL;
    }

    char data[MAX_RX_PACKET];
    MessageBuf buf(data, sizeof(data));
    buf.writeU8(cmd);
    writeAppName(buf, appName);
    setState(MGMT);

    return sendToSystem(buf.getData(), buf.getPos());
}

int SystemComm::AppMgmtSession::handleRx(MessageBuf &buf)
{
    int ret = 0;
    std::lock_guard<std::mutex> _l(mLock);
    NanohubRsp rsp(buf);

    switch (getState()) {
    case TRANSFER:
        ret = handleTransfer(rsp);
        break;
    case FINISH:
        ret = handleFinish(rsp);
        break;
    case RUN:
        ret = handleRun(rsp);
        break;
    case RUN_FAILED:
        ret = handleRunFailed(rsp);
        break;
    case REBOOT:
        ret = handleReboot(rsp);
        break;
    case MGMT:
        ret = handleMgmt(rsp);
        break;
    }

    return ret;
}

int SystemComm::AppMgmtSession::handleTransfer(NanohubRsp &rsp)
{
    if (rsp.cmd != NANOHUB_CONT_UPLOAD && rsp.cmd != NANOHUB_START_UPLOAD)
        return 1;

    char data[MAX_RX_PACKET];
    MessageBuf buf(data, sizeof(data));

    static_assert(NANOHUB_UPLOAD_CHUNK_SZ_MAX <= (MAX_RX_PACKET-5),
                  "Invalid chunk size");

    if (mPos < mLen) {
        uint32_t chunkSize = mLen - mPos;

        if (chunkSize > NANOHUB_UPLOAD_CHUNK_SZ_MAX) {
            chunkSize = NANOHUB_UPLOAD_CHUNK_SZ_MAX;
        }

        buf.writeU8(NANOHUB_CONT_UPLOAD);
        buf.writeU32(mPos);
        buf.writeRaw(&mData[mPos], chunkSize);
        mPos += chunkSize;
    } else {
        buf.writeU8(NANOHUB_FINISH_UPLOAD);
        setState(FINISH);
    }

    return sendToSystem(buf.getData(), buf.getPos());
}

int SystemComm::AppMgmtSession::handleFinish(NanohubRsp &rsp)
{
    if (rsp.cmd != NANOHUB_FINISH_UPLOAD)
        return 1;

    int ret = 0;
    const bool success = rsp.status != 0;
    mData.clear();

    if (success) {
        char data[MAX_RX_PACKET];
        MessageBuf buf(data, sizeof(data));
        buf.writeU8(NANOHUB_EXT_APPS_ON);
        writeAppName(buf, mAppName);
        setState(RUN);
        ret = sendToSystem(buf.getData(), buf.getPos());
    } else {
        int32_t result = NANOHUB_APP_NOT_LOADED;

        sendToApp(mCmd, &result, sizeof(result));
        complete();
    }

    return ret;
}

int SystemComm::AppMgmtSession::handleRun(NanohubRsp &rsp)
{
    if (rsp.cmd != NANOHUB_EXT_APPS_ON)
        return 1;

    MgmtStatus sts = { .value = (uint32_t)rsp.status };

    // op counter returns number of nanoapps that were started as result of the command
    // for successful start command it must be > 0
    int32_t result = sts.value > 0 && sts.op > 0 && sts.op <= 0x7F ? 0 : -1;

    ALOGI("Nanohub NEW APP START: %08" PRIX32 "\n", rsp.status);
    if (result != 0) {
        // if nanoapp failed to start we have to unload it
        char data[MAX_RX_PACKET];
        MessageBuf buf(data, sizeof(data));
        buf.writeU8(NANOHUB_EXT_APP_DELETE);
        writeAppName(buf, mAppName);
        if (sendToSystem(buf.getData(), buf.getPos()) == 0) {
            setState(RUN_FAILED);
            return 0;
        }
        ALOGE("%s: failed to send DELETE for failed app\n", __func__);
    }

    // it is either success, and we report it, or
    // it is a failure to load, and also failure to send erase command
    sendToApp(mCmd, &result, sizeof(result));
    complete();
    return 0;
}

int SystemComm::AppMgmtSession::handleRunFailed(NanohubRsp &rsp)
{
    if (rsp.cmd != NANOHUB_EXT_APP_DELETE)
        return 1;

    int32_t result = -1;

    ALOGI("%s: APP DELETE [because it failed]: %08" PRIX32 "\n", __func__, rsp.status);

    sendToApp(mCmd, &result, sizeof(result));
    complete();

    return 0;
}

/* reboot notification, when triggered by App request */
int SystemComm::AppMgmtSession::handleReboot(NanohubRsp &rsp)
{
    if (rsp.cmd != NANOHUB_REBOOT)
        return 1;
    ALOGI("Nanohub reboot status [USER REQ]: %08" PRIX32 "\n", rsp.status);

    // reboot notification is sent by SessionManager
    complete();

    return 0;
}

int SystemComm::AppMgmtSession::handleMgmt(NanohubRsp &rsp)
{
    bool valid = false;

    int32_t result = rsp.status;

    // TODO: remove this when context hub service can handle non-zero success status
    if (result > 0) {
        // something happened; assume it worked
        result = 0;
    } else if (result == 0) {
        // nothing happened; this is provably an error
        result = -1;
    }

    switch (rsp.cmd) {
    case NANOHUB_EXT_APPS_OFF:
        valid = mCmd == CONTEXT_HUB_APPS_DISABLE;
        break;
    case NANOHUB_EXT_APPS_ON:
        valid = mCmd == CONTEXT_HUB_APPS_ENABLE;
        break;
    case NANOHUB_EXT_APP_DELETE:
        valid = mCmd == CONTEXT_HUB_UNLOAD_APP;
        break;
    default:
        return 1;
    }

    ALOGI("Nanohub MGMT response: CMD=%02X; STATUS=%08" PRIX32, rsp.cmd, rsp.status);
    if (!valid) {
        ALOGE("Invalid response for this state: APP CMD=%02X", mCmd);
        return -EINVAL;
    }

    sendToApp(mCmd, &result, sizeof(result));
    complete();

    return 0;
}

int SystemComm::KeyInfoSession::setup(const hub_message_t *) {
    std::lock_guard<std::mutex> _l(mLock);
    mRsaKeyData.clear();
    setState(SESSION_USER);
    mStatus = -EBUSY;
    return requestRsaKeys();
}

int SystemComm::KeyInfoSession::handleRx(MessageBuf &buf)
{
    std::lock_guard<std::mutex> _l(mLock);
    NanohubRsp rsp(buf, true);

    if (getState() != SESSION_USER) {
        // invalid state
        mStatus = -EFAULT;
        return mStatus;
    }

    if (buf.getRoom()) {
        mRsaKeyData.insert(mRsaKeyData.end(),
                           buf.getData() + buf.getPos(),
                           buf.getData() + buf.getSize());
        return requestRsaKeys();
    } else {
        mStatus = 0;
        complete();
        return 0;
    }
}

int SystemComm::KeyInfoSession::requestRsaKeys(void)
{
    char data[MAX_RX_PACKET];
    MessageBuf buf(data, sizeof(data));

    buf.writeU8(NANOHUB_QUERY_APPS);
    buf.writeU32(mRsaKeyData.size());

    return sendToSystem(buf.getData(), buf.getPos());
}

int SystemComm::doHandleRx(const nano_message *msg)
{
    //we only care for messages from HostIF
    if (msg->hdr.appId != mHostIfAppName.id)
        return 1;

    //they must all be at least 1 byte long
    if (!msg->hdr.len) {
        return -EINVAL;
    }
    MessageBuf buf(reinterpret_cast<const char*>(msg->data), msg->hdr.len);
    if (NanoHub::messageTracingEnabled()) {
        dumpBuffer("SYS -> HAL", mHostIfAppName, 0, buf.getData(), buf.getSize());
    }
    int status = mSessions.handleRx(buf);
    if (status) {
        // provide default handler for any system message, that is not properly handled
        dumpBuffer(status > 0 ? "HAL (not handled)" : "HAL (error)",
                   mHostIfAppName, 0, buf.getData(), buf.getSize(), status);
        status = status > 0 ? 0 : status;
    }

    return status;
}

int SystemComm::SessionManager::handleRx(MessageBuf &buf)
{
    int status = 1;
    std::unique_lock<std::mutex> lk(lock);

    // pass message to all active sessions, in arbitrary order
    // 1st session that handles the message terminates the loop
    for (auto pos = sessions_.begin(); pos != sessions_.end() && status > 0; next(pos)) {
        if (!isActive(pos)) {
            continue;
        }
        Session *session = pos->second;
        status = session->handleRx(buf);
        if (status < 0) {
            session->complete();
        }
    }

    NanohubRsp rsp(buf);
    if (rsp.cmd == NANOHUB_REBOOT) {
        // if this is reboot notification, kill all sessions
        for (auto pos = sessions_.begin(); pos != sessions_.end(); next(pos)) {
            if (!isActive(pos)) {
                continue;
            }
            Session *session = pos->second;
            session->abort(-EINTR);
        }
        lk.unlock();
        // log the reboot event, if not handled
        if (status > 0) {
            ALOGW("Nanohub reboot status [UNSOLICITED]: %08" PRIX32, rsp.status);
            status = 0;
        }
        // report to java apps
        sendToApp(CONTEXT_HUB_OS_REBOOT, &rsp.status, sizeof(rsp.status));
    }

    return status;
}

int SystemComm::SessionManager::setup_and_add(int id, Session *session, const hub_message_t *appMsg)
{
    std::lock_guard<std::mutex> _l(lock);

    // scan sessions to release those that are already done
    for (auto pos = sessions_.begin(); pos != sessions_.end(); next(pos)) {
        continue;
    }

    if (sessions_.count(id) == 0 && !session->isRunning()) {
        sessions_[id] = session;
        int ret = session->setup(appMsg);
        if (ret < 0) {
            session->complete();
        }
        return ret;
    }
    return -EBUSY;
}

int SystemComm::doHandleTx(const hub_message_t *appMsg)
{
    int status = 0;

    switch (appMsg->message_type) {
    case CONTEXT_HUB_LOAD_APP:
        if (!mKeySession.haveKeys()) {
            status = mSessions.setup_and_add(CONTEXT_HUB_LOAD_APP, &mKeySession, appMsg);
            if (status < 0) {
                break;
            }
            mKeySession.wait();
            status = mKeySession.getStatus();
            if (status < 0) {
                break;
            }
        }
        status = mSessions.setup_and_add(CONTEXT_HUB_LOAD_APP, &mAppMgmtSession, appMsg);
        break;
    case CONTEXT_HUB_APPS_ENABLE:
    case CONTEXT_HUB_APPS_DISABLE:
    case CONTEXT_HUB_UNLOAD_APP:
        // all APP-modifying commands share session key, to ensure they can't happen at the same time
        status = mSessions.setup_and_add(CONTEXT_HUB_LOAD_APP, &mAppMgmtSession, appMsg);
        break;

    case CONTEXT_HUB_QUERY_APPS:
        status = mSessions.setup_and_add(CONTEXT_HUB_QUERY_APPS, &mAppInfoSession, appMsg);
        break;

    case CONTEXT_HUB_QUERY_MEMORY:
        status = mSessions.setup_and_add(CONTEXT_HUB_QUERY_MEMORY, &mMemInfoSession, appMsg);
        break;

    default:
        ALOGW("Unknown os message type %u\n", appMsg->message_type);
        return -EINVAL;
    }

   return status;
}

}; // namespace nanohub

}; // namespace android
