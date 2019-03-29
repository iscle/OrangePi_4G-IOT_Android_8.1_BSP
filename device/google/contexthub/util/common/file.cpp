/*
 * Copyright (C) 2015 The Android Open Source Project
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

//#define LOG_NDEBUG    0
#define LOG_TAG "file"
#include <utils/Log.h>
#include "file.h"

#include <fcntl.h>
#include <string.h>
#include <unistd.h>

namespace android {

File::File()
    : mInitCheck(NO_INIT),
      mFd(-1) {
}

File::File(const char *path, const char *mode)
    : mInitCheck(NO_INIT),
      mFd(-1) {
    mInitCheck = setTo(path, mode);
}

File::~File() {
    close();
}

status_t File::initCheck() const {
    return mInitCheck;
}

status_t File::setTo(const char *path, const char *mode) {
    close();

    int modeval = 0;
    if (!strcmp("r", mode)) {
        modeval = O_RDONLY;
    } else if (!strcmp("w", mode)) {
        modeval = O_WRONLY | O_CREAT | O_TRUNC;
    } else if (!strcmp("rw", mode)) {
        modeval = O_RDWR | O_CREAT;
    }

    int filemode = 0;
    if (modeval & O_CREAT) {
        filemode = S_IRUSR|S_IWUSR|S_IRGRP|S_IWGRP|S_IROTH;
    }

    mFd = open(path, modeval, filemode);

    mInitCheck = (mFd >= 0) ? OK : -errno;

    return mInitCheck;
}

void File::close() {
    if (mFd >= 0) {
        ::close(mFd);
        mFd = -1;
    }

    mInitCheck = NO_INIT;
}

ssize_t File::read(void *data, size_t size) {
    return ::read(mFd, data, size);
}

ssize_t File::write(const void *data, size_t size) {
    return ::write(mFd, data, size);
}

off64_t File::seekTo(off64_t pos, int whence) {
    off64_t new_pos = lseek64(mFd, pos, whence);
    return new_pos < 0 ? -errno : new_pos;
}

}  // namespace android
