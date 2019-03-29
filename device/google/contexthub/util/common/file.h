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

#ifndef FILE_H_

#define FILE_H_

#include <media/stagefright/foundation/ABase.h>
#include <utils/Errors.h>

namespace android {

struct File {
    File();
    File(const char *path, const char *mode);

    ~File();

    status_t initCheck() const;
    status_t setTo(const char *path, const char *mode);
    void close();

    ssize_t read(void *data, size_t size);
    ssize_t write(const void *data, size_t size);

    off64_t seekTo(off64_t pos, int whence = SEEK_SET);

private:
    status_t mInitCheck;
    int mFd;

    DISALLOW_EVIL_CONSTRUCTORS(File);
};

}  // namespace android

#endif  // FILE_H_
