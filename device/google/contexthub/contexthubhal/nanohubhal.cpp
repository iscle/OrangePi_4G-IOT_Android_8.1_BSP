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

#include <fcntl.h>
#include <poll.h>
#include <unistd.h>
#include <sys/inotify.h>
#include <sys/types.h>
#include <sys/stat.h>

#include <hardware/context_hub.h>
#include <hardware/hardware.h>

#include <cutils/properties.h>
#include <log/log.h>

#include <nanohub/nanohub.h>

#include <cinttypes>
#include <iomanip>
#include <sstream>

#include "nanohub_perdevice.h"
#include "system_comms.h"
#include "nanohubhal.h"

#define NANOHUB_LOCK_DIR        "/data/vendor/sensor/nanohub_lock"
#define NANOHUB_LOCK_FILE       NANOHUB_LOCK_DIR "/lock"
#define NANOHUB_LOCK_DIR_PERMS  (S_IRUSR | S_IWUSR | S_IXUSR)

namespace android {

namespace nanohub {

inline std::ostream &operator << (std::ostream &os, const hub_app_name_t &appId)
{
    char vendor[6];
    __be64 beAppId = htobe64(appId.id);
    uint32_t seqId = appId.id & NANOAPP_VENDOR_ALL_APPS;

    std::ios::fmtflags f(os.flags());
    memcpy(vendor, (void*)&beAppId, sizeof(vendor) - 1);
    vendor[sizeof(vendor) - 1] = 0;
    if (strlen(vendor) == 5)
        os << vendor << ", " << std::hex << std::setw(6)  << seqId;
    else
        os << "#" << std::hex << appId.id;
    os.flags(f);

    return os;
}

void dumpBuffer(const char *pfx, const hub_app_name_t &appId, uint32_t evtId, const void *data, size_t len, int status)
{
    std::ostringstream os;
    const uint8_t *p = static_cast<const uint8_t *>(data);
    os << pfx << ": [ID=" << appId << "; SZ=" << std::dec << len;
    if (evtId)
        os << "; EVT=" << std::hex << evtId;
    os << "]:" << std::hex;
    for (size_t i = 0; i < len; ++i) {
        os << " "  << std::setfill('0') << std::setw(2) << (unsigned int)p[i];
    }
    if (status) {
        os << "; status=" << status << " [" << std::setfill('0') << std::setw(8) << status << "]";
    }
    ALOGI("%s", os.str().c_str());
}

static int rwrite(int fd, const void *buf, int len)
{
    int ret;

    do {
        ret = write(fd, buf, len);
    } while (ret < 0 && errno == EINTR);

    if (ret != len) {
        return errno ? -errno : -EIO;
    }

    return 0;
}

static int rread(int fd, void *buf, int len)
{
    int ret;

    do {
        ret = read(fd, buf, len);
    } while (ret < 0 && errno == EINTR);

    return ret;
}

static bool init_inotify(pollfd *pfd) {
    bool success = false;

    mkdir(NANOHUB_LOCK_DIR, NANOHUB_LOCK_DIR_PERMS);
    pfd->fd = inotify_init1(IN_NONBLOCK);
    if (pfd->fd < 0) {
        ALOGE("Couldn't initialize inotify: %s", strerror(errno));
    } else if (inotify_add_watch(pfd->fd, NANOHUB_LOCK_DIR, IN_CREATE | IN_DELETE) < 0) {
        ALOGE("Couldn't add inotify watch: %s", strerror(errno));
        close(pfd->fd);
    } else {
        pfd->events = POLLIN;
        success = true;
    }

    return success;
}

static void discard_inotify_evt(pollfd &pfd) {
    if ((pfd.revents & POLLIN)) {
        char buf[sizeof(inotify_event) + NAME_MAX + 1];
        int ret = read(pfd.fd, buf, sizeof(buf));
        ALOGD("Discarded %d bytes of inotify data", ret);
    }
}

static void wait_on_dev_lock(pollfd &pfd) {
    // While the lock file exists, poll on the inotify fd (with timeout)
    discard_inotify_evt(pfd);
    while (access(NANOHUB_LOCK_FILE, F_OK) == 0) {
        ALOGW("Nanohub is locked; blocking read thread");
        int ret = TEMP_FAILURE_RETRY(poll(&pfd, 1, 5000));
        if (ret < 0) {
            ALOGE("poll returned with an error: %s", strerror(errno));
            break;
        } else if (ret > 0) {
            discard_inotify_evt(pfd);
        }
    }
}

NanoHub::NanoHub() {
    reset();
}

NanoHub::~NanoHub() {
    if (mMsgCbkFunc) {
        ALOGD("Shutting down");
        closeHub();
    }
}

int NanoHub::doSendToDevice(const hub_app_name_t name, const void *data, uint32_t len, uint32_t messageType)
{
    if (len > MAX_RX_PACKET) {
        return -EINVAL;
    }

    // transmit message to FW in CHRE format
    nano_message_chre msg = {
        .hdr = {
            .eventId = APP_FROM_HOST_CHRE_EVENT_ID,
            .appId = name.id,
            .len = static_cast<uint8_t>(len),
            .appEventId = messageType,
        },
    };

    memcpy(&msg.data[0], data, len);

    return rwrite(mFd, &msg, len + sizeof(msg.hdr));
}

void NanoHub::doSendToApp(HubMessage &&msg)
{
    std::unique_lock<std::mutex> lk(mAppTxLock);
    mAppTxQueue.push_back((HubMessage &&)msg);
    lk.unlock();
    mAppTxCond.notify_all();
}

void* NanoHub::runAppTx()
{
    std::unique_lock<std::mutex> lk(mAppTxLock);
    while(true) {
        mAppTxCond.wait(lk, [this] { return !mAppTxQueue.empty() || mAppQuit; });
        if (mAppQuit) {
            break;
        }
        HubMessage &m = mAppTxQueue.front();
        lk.unlock();
        mMsgCbkFunc(0, &m, mMsgCbkData);
        lk.lock();
        mAppTxQueue.pop_front();
    };
    return NULL;
}

void* NanoHub::runDeviceRx()
{
    enum {
        IDX_NANOHUB,
        IDX_CLOSE_PIPE,
        IDX_INOTIFY
    };
    pollfd myFds[3] = {
        [IDX_NANOHUB] = { .fd = mFd, .events = POLLIN, },
        [IDX_CLOSE_PIPE] = { .fd = mThreadClosingPipe[0], .events = POLLIN, },
    };
    pollfd &inotifyFd = myFds[IDX_INOTIFY];
    bool hasInotify = false;
    int numPollFds = 2;

    if (init_inotify(&inotifyFd)) {
        numPollFds++;
        hasInotify = true;
    }

    setDebugFlags(property_get_int32("persist.nanohub.debug", 0));

    while (1) {
        int ret = TEMP_FAILURE_RETRY(poll(myFds, numPollFds, -1));
        if (ret == 0)
            continue;
        else if (ret < 0) {
            ALOGE("poll returned with an error: %s", strerror(errno));
            break;
        }

        if (hasInotify) {
            wait_on_dev_lock(inotifyFd);
        }

        if (myFds[IDX_NANOHUB].revents & POLLIN) { // we have data

            nano_message msg;

            ret = rread(mFd, &msg, sizeof(msg));
            if (ret <= 0) {
                ALOGE("read failed with %d", ret);
                break;
            }
            if (ret < (int)sizeof(msg.hdr)) {
                ALOGE("Only read %d bytes", ret);
                break;
            }

            uint32_t len = msg.hdr.len;

            if (len > sizeof(msg.data)) {
                ALOGE("malformed packet with len %" PRIu32, len);
                break;
            }

            // receive message from FW in legacy format
            if (ret != (int)(sizeof(msg.hdr) + len)) {
                ALOGE("Expected %zu bytes, read %d bytes", sizeof(msg.hdr) + len, ret);
                break;
            }

            ret = SystemComm::handleRx(&msg);
            if (ret < 0) {
                ALOGE("SystemComm::handleRx() returned %d", ret);
            } else if (ret) {
                hub_app_name_t app_name = { .id = msg.hdr.appId };
                if (messageTracingEnabled()) {
                    dumpBuffer("DEV -> APP", app_name, msg.hdr.eventId, &msg.data[0], msg.hdr.len);
                }
                doSendToApp(HubMessage(&app_name, msg.hdr.eventId, &msg.data[0], msg.hdr.len));
            }
        }

        if (myFds[IDX_CLOSE_PIPE].revents & POLLIN) { // we have been asked to die
            ALOGD("thread exiting");
            break;
        }
    }

    close(mFd);
    return NULL;
}

int NanoHub::openHub()
{
    int ret = 0;

    mFd = open(get_devnode_path(), O_RDWR);
    if (mFd < 0) {
        ALOGE("cannot find hub devnode '%s'", get_devnode_path());
        ret = -errno;
        goto fail_open;
    }

    if (pipe(mThreadClosingPipe)) {
        ALOGE("failed to create signal pipe");
        ret = -errno;
        goto fail_pipe;
    }

    mPollThread = std::thread([this] { runDeviceRx(); });
    mAppThread = std::thread([this] { runAppTx(); });
    return 0;

fail_pipe:
    close(mFd);

fail_open:
    return ret;
}

int NanoHub::closeHub(void)
{
    char zero = 0;

    // stop mPollThread
    while(write(mThreadClosingPipe[1], &zero, 1) != 1) {
        continue;
    }

    // stop mAppThread
    {
        std::unique_lock<std::mutex> lk(mAppTxLock);
        mAppQuit = true;
        lk.unlock();
        mAppTxCond.notify_all();
    }

    //wait
    if (mPollThread.joinable()) {
        mPollThread.join();
    }

    //wait
    if (mAppThread.joinable()) {
        mAppThread.join();
    }
    //cleanup
    ::close(mThreadClosingPipe[0]);
    ::close(mThreadClosingPipe[1]);
    ::close(mFd);

    reset();

    return 0;
}

int NanoHub::doSubscribeMessages(uint32_t hub_id, context_hub_callback *cbk, void *cookie)
{
    if (hub_id) {
        return -ENODEV;
    }

    std::lock_guard<std::mutex> _l(mLock);
    int ret = 0;

    if (!mMsgCbkFunc && !cbk) { //we're off and staying off - do nothing

        ALOGD("staying off");
    } else if (cbk && mMsgCbkFunc) { //new callback but staying on

        ALOGD("staying on");
    } else if (mMsgCbkFunc) {     //we were on but turning off

        ALOGD("turning off");

        ret = closeHub();
    } else if (cbk) {             //we're turning on

        ALOGD("turning on");
        ret = openHub();
    }

    mMsgCbkFunc = cbk;
    mMsgCbkData = cookie;

    return ret;
}

int NanoHub::doSendToNanohub(uint32_t hub_id, const hub_message_t *msg)
{
    if (hub_id) {
        return -ENODEV;
    }

    int ret = 0;
    std::lock_guard<std::mutex> _l(mLock);

    if (!mMsgCbkFunc) {
        ALOGW("refusing to send a message when nobody around to get a reply!");
        ret = -EIO;
    } else {
        if (!msg || !msg->message) {
            ALOGW("not sending invalid message 1");
            ret = -EINVAL;
        } else if (get_hub_info()->os_app_name == msg->app_name) {
            //messages to the "system" app are special - hal handles them
            if (messageTracingEnabled()) {
                dumpBuffer("APP -> HAL", msg->app_name, msg->message_type, msg->message, msg->message_len);
            }
            ret = SystemComm::handleTx(msg);
        } else if (msg->message_len > MAX_RX_PACKET) {
            ALOGW("not sending invalid message 2");
            ret = -EINVAL;
        } else {
            if (messageTracingEnabled()) {
                dumpBuffer("APP -> DEV", msg->app_name, msg->message_type, msg->message, msg->message_len);
            }
            ret = doSendToDevice(msg->app_name, msg->message, msg->message_len, msg->message_type);
        }
    }

    return ret;
}

static int hal_get_hubs(context_hub_module_t*, const context_hub_t ** list)
{
    *list = get_hub_info();

    return 1; /* we have one hub */
}

}; // namespace nanohub

}; // namespace android

context_hub_module_t HAL_MODULE_INFO_SYM = {
    .common = {
        .tag = HARDWARE_MODULE_TAG,
        .module_api_version = CONTEXT_HUB_DEVICE_API_VERSION_1_0,
        .hal_api_version = HARDWARE_HAL_API_VERSION,
        .id = CONTEXT_HUB_MODULE_ID,
        .name = "Nanohub HAL",
        .author = "Google",
    },

    .get_hubs = android::nanohub::hal_get_hubs,
    .subscribe_messages = android::nanohub::NanoHub::subscribeMessages,
    .send_message = android::nanohub::NanoHub::sendToNanohub,
};
