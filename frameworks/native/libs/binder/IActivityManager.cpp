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

#include <unistd.h>
#include <fcntl.h>

#include <binder/IActivityManager.h>

#include <binder/Parcel.h>

namespace android {

// ------------------------------------------------------------------------------------

class BpActivityManager : public BpInterface<IActivityManager>
{
public:
    explicit BpActivityManager(const sp<IBinder>& impl)
        : BpInterface<IActivityManager>(impl)
    {
    }

    virtual int openContentUri(const String16& stringUri)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IActivityManager::getInterfaceDescriptor());
        data.writeString16(stringUri);
        status_t ret = remote()->transact(OPEN_CONTENT_URI_TRANSACTION, data, & reply);
        int fd = -1;
        if (ret == NO_ERROR) {
            int32_t exceptionCode = reply.readExceptionCode();
            if (!exceptionCode) {
                // Success is indicated here by a nonzero int followed by the fd;
                // failure by a zero int with no data following.
                if (reply.readInt32() != 0) {
                    fd = fcntl(reply.readParcelFileDescriptor(), F_DUPFD_CLOEXEC, 0);
                }
            } else {
                // An exception was thrown back; fall through to return failure
                ALOGD("openContentUri(%s) caught exception %d\n",
                        String8(stringUri).string(), exceptionCode);
            }
        }
        return fd;
    }
};

// ------------------------------------------------------------------------------------

IMPLEMENT_META_INTERFACE(ActivityManager, "android.app.IActivityManager");

}; // namespace android
