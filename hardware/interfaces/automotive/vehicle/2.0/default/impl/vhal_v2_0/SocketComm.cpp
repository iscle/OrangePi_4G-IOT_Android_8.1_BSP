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

#define LOG_TAG "SocketComm"

#include <android/hardware/automotive/vehicle/2.0/IVehicle.h>
#include <android/log.h>
#include <log/log.h>
#include <netinet/in.h>
#include <sys/socket.h>

#include "SocketComm.h"

// Socket to use when communicating with Host PC
static constexpr int DEBUG_SOCKET = 33452;

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

namespace impl {

SocketComm::SocketComm() {
    // Initialize member vars
    mCurSockFd = -1;
    mExit      =  0;
    mSockFd    = -1;
}


SocketComm::~SocketComm() {
    stop();
}

int SocketComm::connect() {
    sockaddr_in cliAddr;
    socklen_t cliLen = sizeof(cliAddr);
    int cSockFd = accept(mSockFd, reinterpret_cast<struct sockaddr*>(&cliAddr), &cliLen);

    if (cSockFd >= 0) {
        {
            std::lock_guard<std::mutex> lock(mMutex);
            mCurSockFd = cSockFd;
        }
        ALOGD("%s: Incoming connection received on socket %d", __FUNCTION__, cSockFd);
    } else {
        cSockFd = -1;
    }

    return cSockFd;
}

int SocketComm::open() {
    int retVal;
    struct sockaddr_in servAddr;

    mSockFd = socket(AF_INET, SOCK_STREAM, 0);
    if (mSockFd < 0) {
        ALOGE("%s: socket() failed, mSockFd=%d, errno=%d", __FUNCTION__, mSockFd, errno);
        mSockFd = -1;
        return -errno;
    }

    memset(&servAddr, 0, sizeof(servAddr));
    servAddr.sin_family = AF_INET;
    servAddr.sin_addr.s_addr = INADDR_ANY;
    servAddr.sin_port = htons(DEBUG_SOCKET);

    retVal = bind(mSockFd, reinterpret_cast<struct sockaddr*>(&servAddr), sizeof(servAddr));
    if(retVal < 0) {
        ALOGE("%s: Error on binding: retVal=%d, errno=%d", __FUNCTION__, retVal, errno);
        close(mSockFd);
        mSockFd = -1;
        return -errno;
    }

    listen(mSockFd, 1);

    // Set the socket to be non-blocking so we can poll it continouously
    fcntl(mSockFd, F_SETFL, O_NONBLOCK);

    return 0;
}

std::vector<uint8_t> SocketComm::read() {
    int32_t msgSize;
    int numBytes = 0;

    // This is a variable length message.
    // Read the number of bytes to rx over the socket
    numBytes = ::read(mCurSockFd, &msgSize, sizeof(msgSize));
    msgSize = ntohl(msgSize);

    if (numBytes != sizeof(msgSize)) {
        // This happens when connection is closed
        ALOGD("%s: numBytes=%d, expected=4", __FUNCTION__, numBytes);
        ALOGD("%s: Connection terminated on socket %d", __FUNCTION__, mCurSockFd);
        {
            std::lock_guard<std::mutex> lock(mMutex);
            mCurSockFd = -1;
        }

        return std::vector<uint8_t>();
    }

    std::vector<uint8_t> msg = std::vector<uint8_t>(msgSize);

    numBytes = ::read(mCurSockFd, msg.data(), msgSize);

    if ((numBytes == msgSize) && (msgSize > 0)) {
        // Received a message.
        return msg;
    } else {
        // This happens when connection is closed
        ALOGD("%s: numBytes=%d, msgSize=%d", __FUNCTION__, numBytes, msgSize);
        ALOGD("%s: Connection terminated on socket %d", __FUNCTION__, mCurSockFd);
        {
            std::lock_guard<std::mutex> lock(mMutex);
            mCurSockFd = -1;
        }

        return std::vector<uint8_t>();
    }
}

void SocketComm::stop() {
    if (mExit == 0) {
        std::lock_guard<std::mutex> lock(mMutex);
        mExit = 1;

        // Close emulator socket if it is open
        if (mCurSockFd != -1) {
            close(mCurSockFd);
            mCurSockFd = -1;
        }

        if (mSockFd != -1) {
            close(mSockFd);
            mSockFd = -1;
        }
    }
}

int SocketComm::write(const std::vector<uint8_t>& data) {
    static constexpr int MSG_HEADER_LEN = 4;
    int retVal = 0;
    union {
        uint32_t msgLen;
        uint8_t msgLenBytes[MSG_HEADER_LEN];
    };

    // Prepare header for the message
    msgLen = static_cast<uint32_t>(data.size());
    msgLen = htonl(msgLen);

    std::lock_guard<std::mutex> lock(mMutex);
    if (mCurSockFd != -1) {
        retVal = ::write(mCurSockFd, msgLenBytes, MSG_HEADER_LEN);

        if (retVal == MSG_HEADER_LEN) {
            retVal = ::write(mCurSockFd, data.data(), data.size());
        }
    }

    return retVal;
}


}  // impl

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android

