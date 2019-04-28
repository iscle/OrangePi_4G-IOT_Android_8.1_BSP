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

#include "PipeRelay.h"

#include <sys/socket.h>
#include <utils/Thread.h>

namespace android {
namespace lshal {

struct PipeRelay::RelayThread : public Thread {
    explicit RelayThread(int fd, std::ostream &os);

    bool threadLoop() override;

private:
    int mFd;
    std::ostream &mOutStream;

    DISALLOW_COPY_AND_ASSIGN(RelayThread);
};

////////////////////////////////////////////////////////////////////////////////

PipeRelay::RelayThread::RelayThread(int fd, std::ostream &os)
    : mFd(fd),
      mOutStream(os) {
}

bool PipeRelay::RelayThread::threadLoop() {
    char buffer[1024];
    ssize_t n = read(mFd, buffer, sizeof(buffer));

    if (n <= 0) {
        return false;
    }

    mOutStream.write(buffer, n);

    return true;
}

////////////////////////////////////////////////////////////////////////////////

PipeRelay::PipeRelay(std::ostream &os)
    : mOutStream(os),
      mInitCheck(NO_INIT) {
    int res = socketpair(AF_UNIX, SOCK_STREAM, 0 /* protocol */, mFds);

    if (res < 0) {
        mInitCheck = -errno;
        return;
    }

    mThread = new RelayThread(mFds[0], os);
    mInitCheck = mThread->run("RelayThread");
}

void PipeRelay::CloseFd(int *fd) {
    if (*fd >= 0) {
        close(*fd);
        *fd = -1;
    }
}

PipeRelay::~PipeRelay() {
    if (mFds[1] >= 0) {
        shutdown(mFds[1], SHUT_WR);
    }

    if (mFds[0] >= 0) {
        shutdown(mFds[0], SHUT_RD);
    }

    if (mThread != NULL) {
        mThread->join();
        mThread.clear();
    }

    CloseFd(&mFds[1]);
    CloseFd(&mFds[0]);
}

status_t PipeRelay::initCheck() const {
    return mInitCheck;
}

int PipeRelay::fd() const {
    return mFds[1];
}

}  // namespace lshal
}  // namespace android
