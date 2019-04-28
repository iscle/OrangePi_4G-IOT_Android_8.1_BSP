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

#ifndef _MTP_FFS_HANDLE_H
#define _MTP_FFS_HANDLE_H

#include <android-base/unique_fd.h>
#include <IMtpHandle.h>

namespace android {

class MtpFfsHandleTest;

class MtpFfsHandle : public IMtpHandle {
    friend class android::MtpFfsHandleTest;
private:
    int writeHandle(int fd, const void *data, int len);
    int readHandle(int fd, void *data, int len);
    int spliceReadHandle(int fd, int fd_out, int len);
    bool initFunctionfs();
    void closeConfig();
    void closeEndpoints();
    void doSendEvent(mtp_event me);

    void controlLoop();
    int handleEvent();
    int handleControlRequest(const struct usb_ctrlrequest *setup);

    bool mPtp;

    std::timed_mutex mLock;

    android::base::unique_fd mControl;
    // "in" from the host's perspective => sink for mtp server
    android::base::unique_fd mBulkIn;
    // "out" from the host's perspective => source for mtp server
    android::base::unique_fd mBulkOut;
    android::base::unique_fd mIntr;

    int mMaxWrite;
    int mMaxRead;

    std::vector<char> mBuffer1;
    std::vector<char> mBuffer2;

public:
    int read(void *data, int len);
    int write(const void *data, int len);

    int receiveFile(mtp_file_range mfr, bool zero_packet);
    int sendFile(mtp_file_range mfr);
    int sendEvent(mtp_event me);

    /**
     * Open ffs endpoints and allocate necessary kernel and user memory.
     * Will sleep until endpoints are enabled, for up to 1 second.
     */
    int start();
    void close();

    int configure(bool ptp);

    MtpFfsHandle();
    ~MtpFfsHandle();
};

struct mtp_data_header {
    /* length of packet, including this header */
    __le32 length;
    /* container type (2 for data packet) */
    __le16 type;
    /* MTP command code */
    __le16 command;
    /* MTP transaction ID */
    __le32 transaction_id;
};

} // namespace android

#endif // _MTP_FF_HANDLE_H

