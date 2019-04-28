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

#include <android-base/logging.h>
#include <android-base/properties.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/usb/ch9.h>
#include <linux/usb/functionfs.h>
#include <mutex>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/endian.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <vector>

#include "AsyncIO.h"
#include "MtpFfsHandle.h"
#include "mtp.h"

#define cpu_to_le16(x)  htole16(x)
#define cpu_to_le32(x)  htole32(x)

#define FUNCTIONFS_ENDPOINT_ALLOC       _IOR('g', 231, __u32)

namespace {

constexpr char FFS_MTP_EP_IN[] = "/dev/usb-ffs/mtp/ep1";
constexpr char FFS_MTP_EP_OUT[] = "/dev/usb-ffs/mtp/ep2";
constexpr char FFS_MTP_EP_INTR[] = "/dev/usb-ffs/mtp/ep3";

constexpr int MAX_PACKET_SIZE_FS = 64;
constexpr int MAX_PACKET_SIZE_HS = 512;
constexpr int MAX_PACKET_SIZE_SS = 1024;

// Must be divisible by all max packet size values
constexpr int MAX_FILE_CHUNK_SIZE = 3145728;

// Safe values since some devices cannot handle large DMAs
// To get good performance, override these with
// higher values per device using the properties
// sys.usb.ffs.max_read and sys.usb.ffs.max_write
constexpr int USB_FFS_MAX_WRITE = MTP_BUFFER_SIZE;
constexpr int USB_FFS_MAX_READ = MTP_BUFFER_SIZE;

constexpr unsigned FFS_NUM_EVENTS = 5;

static_assert(USB_FFS_MAX_WRITE > 0, "Max r/w values must be > 0!");
static_assert(USB_FFS_MAX_READ > 0, "Max r/w values must be > 0!");

constexpr unsigned int MAX_MTP_FILE_SIZE = 0xFFFFFFFF;

constexpr size_t ENDPOINT_ALLOC_RETRIES = 10;

struct func_desc {
    struct usb_interface_descriptor intf;
    struct usb_endpoint_descriptor_no_audio sink;
    struct usb_endpoint_descriptor_no_audio source;
    struct usb_endpoint_descriptor_no_audio intr;
} __attribute__((packed));

struct ss_func_desc {
    struct usb_interface_descriptor intf;
    struct usb_endpoint_descriptor_no_audio sink;
    struct usb_ss_ep_comp_descriptor sink_comp;
    struct usb_endpoint_descriptor_no_audio source;
    struct usb_ss_ep_comp_descriptor source_comp;
    struct usb_endpoint_descriptor_no_audio intr;
    struct usb_ss_ep_comp_descriptor intr_comp;
} __attribute__((packed));

struct desc_v1 {
    struct usb_functionfs_descs_head_v1 {
        __le32 magic;
        __le32 length;
        __le32 fs_count;
        __le32 hs_count;
    } __attribute__((packed)) header;
    struct func_desc fs_descs, hs_descs;
} __attribute__((packed));

struct desc_v2 {
    struct usb_functionfs_descs_head_v2 header;
    // The rest of the structure depends on the flags in the header.
    __le32 fs_count;
    __le32 hs_count;
    __le32 ss_count;
    __le32 os_count;
    struct func_desc fs_descs, hs_descs;
    struct ss_func_desc ss_descs;
    struct usb_os_desc_header os_header;
    struct usb_ext_compat_desc os_desc;
} __attribute__((packed));

const struct usb_interface_descriptor mtp_interface_desc = {
    .bLength = USB_DT_INTERFACE_SIZE,
    .bDescriptorType = USB_DT_INTERFACE,
    .bInterfaceNumber = 0,
    .bNumEndpoints = 3,
    .bInterfaceClass = USB_CLASS_STILL_IMAGE,
    .bInterfaceSubClass = 1,
    .bInterfaceProtocol = 1,
    .iInterface = 1,
};

const struct usb_interface_descriptor ptp_interface_desc = {
    .bLength = USB_DT_INTERFACE_SIZE,
    .bDescriptorType = USB_DT_INTERFACE,
    .bInterfaceNumber = 0,
    .bNumEndpoints = 3,
    .bInterfaceClass = USB_CLASS_STILL_IMAGE,
    .bInterfaceSubClass = 1,
    .bInterfaceProtocol = 1,
};

const struct usb_endpoint_descriptor_no_audio fs_sink = {
    .bLength = USB_DT_ENDPOINT_SIZE,
    .bDescriptorType = USB_DT_ENDPOINT,
    .bEndpointAddress = 1 | USB_DIR_IN,
    .bmAttributes = USB_ENDPOINT_XFER_BULK,
    .wMaxPacketSize = MAX_PACKET_SIZE_FS,
};

const struct usb_endpoint_descriptor_no_audio fs_source = {
    .bLength = USB_DT_ENDPOINT_SIZE,
    .bDescriptorType = USB_DT_ENDPOINT,
    .bEndpointAddress = 2 | USB_DIR_OUT,
    .bmAttributes = USB_ENDPOINT_XFER_BULK,
    .wMaxPacketSize = MAX_PACKET_SIZE_FS,
};

const struct usb_endpoint_descriptor_no_audio fs_intr = {
    .bLength = USB_DT_ENDPOINT_SIZE,
    .bDescriptorType = USB_DT_ENDPOINT,
    .bEndpointAddress = 3 | USB_DIR_IN,
    .bmAttributes = USB_ENDPOINT_XFER_INT,
    .wMaxPacketSize = MAX_PACKET_SIZE_FS,
    .bInterval = 6,
};

const struct usb_endpoint_descriptor_no_audio hs_sink = {
    .bLength = USB_DT_ENDPOINT_SIZE,
    .bDescriptorType = USB_DT_ENDPOINT,
    .bEndpointAddress = 1 | USB_DIR_IN,
    .bmAttributes = USB_ENDPOINT_XFER_BULK,
    .wMaxPacketSize = MAX_PACKET_SIZE_HS,
};

const struct usb_endpoint_descriptor_no_audio hs_source = {
    .bLength = USB_DT_ENDPOINT_SIZE,
    .bDescriptorType = USB_DT_ENDPOINT,
    .bEndpointAddress = 2 | USB_DIR_OUT,
    .bmAttributes = USB_ENDPOINT_XFER_BULK,
    .wMaxPacketSize = MAX_PACKET_SIZE_HS,
};

const struct usb_endpoint_descriptor_no_audio hs_intr = {
    .bLength = USB_DT_ENDPOINT_SIZE,
    .bDescriptorType = USB_DT_ENDPOINT,
    .bEndpointAddress = 3 | USB_DIR_IN,
    .bmAttributes = USB_ENDPOINT_XFER_INT,
    .wMaxPacketSize = MAX_PACKET_SIZE_HS,
    .bInterval = 6,
};

const struct usb_endpoint_descriptor_no_audio ss_sink = {
    .bLength = USB_DT_ENDPOINT_SIZE,
    .bDescriptorType = USB_DT_ENDPOINT,
    .bEndpointAddress = 1 | USB_DIR_IN,
    .bmAttributes = USB_ENDPOINT_XFER_BULK,
    .wMaxPacketSize = MAX_PACKET_SIZE_SS,
};

const struct usb_endpoint_descriptor_no_audio ss_source = {
    .bLength = USB_DT_ENDPOINT_SIZE,
    .bDescriptorType = USB_DT_ENDPOINT,
    .bEndpointAddress = 2 | USB_DIR_OUT,
    .bmAttributes = USB_ENDPOINT_XFER_BULK,
    .wMaxPacketSize = MAX_PACKET_SIZE_SS,
};

const struct usb_endpoint_descriptor_no_audio ss_intr = {
    .bLength = USB_DT_ENDPOINT_SIZE,
    .bDescriptorType = USB_DT_ENDPOINT,
    .bEndpointAddress = 3 | USB_DIR_IN,
    .bmAttributes = USB_ENDPOINT_XFER_INT,
    .wMaxPacketSize = MAX_PACKET_SIZE_SS,
    .bInterval = 6,
};

const struct usb_ss_ep_comp_descriptor ss_sink_comp = {
    .bLength = sizeof(ss_sink_comp),
    .bDescriptorType = USB_DT_SS_ENDPOINT_COMP,
    .bMaxBurst = 6,
};

const struct usb_ss_ep_comp_descriptor ss_source_comp = {
    .bLength = sizeof(ss_source_comp),
    .bDescriptorType = USB_DT_SS_ENDPOINT_COMP,
    .bMaxBurst = 6,
};

const struct usb_ss_ep_comp_descriptor ss_intr_comp = {
    .bLength = sizeof(ss_intr_comp),
    .bDescriptorType = USB_DT_SS_ENDPOINT_COMP,
};

const struct func_desc mtp_fs_descriptors = {
    .intf = mtp_interface_desc,
    .sink = fs_sink,
    .source = fs_source,
    .intr = fs_intr,
};

const struct func_desc mtp_hs_descriptors = {
    .intf = mtp_interface_desc,
    .sink = hs_sink,
    .source = hs_source,
    .intr = hs_intr,
};

const struct ss_func_desc mtp_ss_descriptors = {
    .intf = mtp_interface_desc,
    .sink = ss_sink,
    .sink_comp = ss_sink_comp,
    .source = ss_source,
    .source_comp = ss_source_comp,
    .intr = ss_intr,
    .intr_comp = ss_intr_comp,
};

const struct func_desc ptp_fs_descriptors = {
    .intf = ptp_interface_desc,
    .sink = fs_sink,
    .source = fs_source,
    .intr = fs_intr,
};

const struct func_desc ptp_hs_descriptors = {
    .intf = ptp_interface_desc,
    .sink = hs_sink,
    .source = hs_source,
    .intr = hs_intr,
};

const struct ss_func_desc ptp_ss_descriptors = {
    .intf = ptp_interface_desc,
    .sink = ss_sink,
    .sink_comp = ss_sink_comp,
    .source = ss_source,
    .source_comp = ss_source_comp,
    .intr = ss_intr,
    .intr_comp = ss_intr_comp,
};

#define STR_INTERFACE "MTP"
const struct {
    struct usb_functionfs_strings_head header;
    struct {
        __le16 code;
        const char str1[sizeof(STR_INTERFACE)];
    } __attribute__((packed)) lang0;
} __attribute__((packed)) strings = {
    .header = {
        .magic = cpu_to_le32(FUNCTIONFS_STRINGS_MAGIC),
        .length = cpu_to_le32(sizeof(strings)),
        .str_count = cpu_to_le32(1),
        .lang_count = cpu_to_le32(1),
    },
    .lang0 = {
        .code = cpu_to_le16(0x0409),
        .str1 = STR_INTERFACE,
    },
};

struct usb_os_desc_header mtp_os_desc_header = {
    .interface = htole32(1),
    .dwLength = htole32(sizeof(usb_os_desc_header) + sizeof(usb_ext_compat_desc)),
    .bcdVersion = htole16(1),
    .wIndex = htole16(4),
    .bCount = htole16(1),
    .Reserved = htole16(0),
};

struct usb_ext_compat_desc mtp_os_desc_compat = {
    .bFirstInterfaceNumber = 0,
    .Reserved1 = htole32(1),
    .CompatibleID = { 'M', 'T', 'P' },
    .SubCompatibleID = {0},
    .Reserved2 = {0},
};

struct usb_ext_compat_desc ptp_os_desc_compat = {
    .bFirstInterfaceNumber = 0,
    .Reserved1 = htole32(1),
    .CompatibleID = { 'P', 'T', 'P' },
    .SubCompatibleID = {0},
    .Reserved2 = {0},
};

struct mtp_device_status {
    uint16_t  wLength;
    uint16_t  wCode;
};

} // anonymous namespace

namespace android {

MtpFfsHandle::MtpFfsHandle() :
    mMaxWrite(USB_FFS_MAX_WRITE),
    mMaxRead(USB_FFS_MAX_READ) {}

MtpFfsHandle::~MtpFfsHandle() {}

void MtpFfsHandle::closeEndpoints() {
    mIntr.reset();
    mBulkIn.reset();
    mBulkOut.reset();
}

bool MtpFfsHandle::initFunctionfs() {
    ssize_t ret;
    struct desc_v1 v1_descriptor;
    struct desc_v2 v2_descriptor;

    v2_descriptor.header.magic = cpu_to_le32(FUNCTIONFS_DESCRIPTORS_MAGIC_V2);
    v2_descriptor.header.length = cpu_to_le32(sizeof(v2_descriptor));
    v2_descriptor.header.flags = FUNCTIONFS_HAS_FS_DESC | FUNCTIONFS_HAS_HS_DESC |
                                 FUNCTIONFS_HAS_SS_DESC | FUNCTIONFS_HAS_MS_OS_DESC;
    v2_descriptor.fs_count = 4;
    v2_descriptor.hs_count = 4;
    v2_descriptor.ss_count = 7;
    v2_descriptor.os_count = 1;
    v2_descriptor.fs_descs = mPtp ? ptp_fs_descriptors : mtp_fs_descriptors;
    v2_descriptor.hs_descs = mPtp ? ptp_hs_descriptors : mtp_hs_descriptors;
    v2_descriptor.ss_descs = mPtp ? ptp_ss_descriptors : mtp_ss_descriptors;
    v2_descriptor.os_header = mtp_os_desc_header;
    v2_descriptor.os_desc = mPtp ? ptp_os_desc_compat : mtp_os_desc_compat;

    if (mControl < 0) { // might have already done this before
        mControl.reset(TEMP_FAILURE_RETRY(open(FFS_MTP_EP0, O_RDWR)));
        if (mControl < 0) {
            PLOG(ERROR) << FFS_MTP_EP0 << ": cannot open control endpoint";
            goto err;
        }

        ret = TEMP_FAILURE_RETRY(::write(mControl, &v2_descriptor, sizeof(v2_descriptor)));
        if (ret < 0) {
            v1_descriptor.header.magic = cpu_to_le32(FUNCTIONFS_DESCRIPTORS_MAGIC);
            v1_descriptor.header.length = cpu_to_le32(sizeof(v1_descriptor));
            v1_descriptor.header.fs_count = 4;
            v1_descriptor.header.hs_count = 4;
            v1_descriptor.fs_descs = mPtp ? ptp_fs_descriptors : mtp_fs_descriptors;
            v1_descriptor.hs_descs = mPtp ? ptp_hs_descriptors : mtp_hs_descriptors;
            PLOG(ERROR) << FFS_MTP_EP0 << "Switching to V1 descriptor format";
            ret = TEMP_FAILURE_RETRY(::write(mControl, &v1_descriptor, sizeof(v1_descriptor)));
            if (ret < 0) {
                PLOG(ERROR) << FFS_MTP_EP0 << "Writing descriptors failed";
                goto err;
            }
        }
        ret = TEMP_FAILURE_RETRY(::write(mControl, &strings, sizeof(strings)));
        if (ret < 0) {
            PLOG(ERROR) << FFS_MTP_EP0 << "Writing strings failed";
            goto err;
        }
    }
    if (mBulkIn > -1 || mBulkOut > -1 || mIntr > -1)
        LOG(WARNING) << "Endpoints were not closed before configure!";

    return true;

err:
    closeConfig();
    return false;
}

void MtpFfsHandle::closeConfig() {
    mControl.reset();
}

void MtpFfsHandle::controlLoop() {
    while (!handleEvent()) {}
    LOG(DEBUG) << "Mtp server shutting down";
}

int MtpFfsHandle::handleEvent() {
    std::vector<usb_functionfs_event> events(FFS_NUM_EVENTS);
    usb_functionfs_event *event = events.data();
    int nbytes = TEMP_FAILURE_RETRY(::read(mControl, event,
                events.size() * sizeof(usb_functionfs_event)));
    if (nbytes == -1) {
        return -1;
    }
    int ret = 0;
    for (size_t n = nbytes / sizeof *event; n; --n, ++event) {
        switch (event->type) {
        case FUNCTIONFS_BIND:
        case FUNCTIONFS_ENABLE:
        case FUNCTIONFS_RESUME:
            ret = 0;
            errno = 0;
            break;
        case FUNCTIONFS_SUSPEND:
        case FUNCTIONFS_UNBIND:
        case FUNCTIONFS_DISABLE:
            errno = ESHUTDOWN;
            ret = -1;
            break;
        case FUNCTIONFS_SETUP:
            if (handleControlRequest(&event->u.setup) == -1)
                ret = -1;
            break;
        default:
            LOG(DEBUG) << "Mtp Event " << event->type << " (unknown)";
        }
    }
    return ret;
}

int MtpFfsHandle::handleControlRequest(const struct usb_ctrlrequest *setup) {
    uint8_t type = setup->bRequestType;
    uint8_t code = setup->bRequest;
    uint16_t length = setup->wLength;
    uint16_t index = setup->wIndex;
    uint16_t value = setup->wValue;
    std::vector<char> buf;
    buf.resize(length);

    if (!(type & USB_DIR_IN)) {
        if (::read(mControl, buf.data(), length) != length) {
            PLOG(DEBUG) << "Mtp error ctrlreq read data";
        }
    }

    if ((type & USB_TYPE_MASK) == USB_TYPE_CLASS && index == 0 && value == 0) {
        switch(code) {
        case MTP_REQ_GET_DEVICE_STATUS:
        {
            if (length < sizeof(struct mtp_device_status)) {
                return -1;
            }
            struct mtp_device_status *st = reinterpret_cast<struct mtp_device_status*>(buf.data());
            st->wLength = htole16(sizeof(st));
            st->wCode = MTP_RESPONSE_OK;
            length = st->wLength;
            break;
        }
        default:
            LOG(DEBUG) << "Unrecognized Mtp class request! " << code;
        }
    } else {
        LOG(DEBUG) << "Unrecognized request type " << type;
    }

    if (type & USB_DIR_IN) {
        if (::write(mControl, buf.data(), length) != length) {
            PLOG(DEBUG) << "Mtp error ctrlreq write data";
        }
    }
    return 0;
}

int MtpFfsHandle::writeHandle(int fd, const void* data, int len) {
    LOG(VERBOSE) << "MTP about to write fd = " << fd << ", len=" << len;
    int ret = 0;
    const char* buf = static_cast<const char*>(data);
    while (len > 0) {
        int write_len = std::min(mMaxWrite, len);
        int n = TEMP_FAILURE_RETRY(::write(fd, buf, write_len));

        if (n < 0) {
            PLOG(ERROR) << "write ERROR: fd = " << fd << ", n = " << n;
            return -1;
        } else if (n < write_len) {
            errno = EIO;
            PLOG(ERROR) << "less written than expected";
            return -1;
        }
        buf += n;
        len -= n;
        ret += n;
    }
    return ret;
}

int MtpFfsHandle::readHandle(int fd, void* data, int len) {
    LOG(VERBOSE) << "MTP about to read fd = " << fd << ", len=" << len;
    int ret = 0;
    char* buf = static_cast<char*>(data);
    while (len > 0) {
        int read_len = std::min(mMaxRead, len);
        int n = TEMP_FAILURE_RETRY(::read(fd, buf, read_len));
        if (n < 0) {
            PLOG(ERROR) << "read ERROR: fd = " << fd << ", n = " << n;
            return -1;
        }
        ret += n;
        if (n < read_len) // done reading early
            break;
        buf += n;
        len -= n;
    }
    return ret;
}

int MtpFfsHandle::spliceReadHandle(int fd, int pipe_out, int len) {
    LOG(VERBOSE) << "MTP about to splice read fd = " << fd << ", len=" << len;
    int ret = 0;
    loff_t dummyoff;
    while (len > 0) {
        int read_len = std::min(mMaxRead, len);
        dummyoff = 0;
        int n = TEMP_FAILURE_RETRY(splice(fd, &dummyoff, pipe_out, nullptr, read_len, 0));
        if (n < 0) {
            PLOG(ERROR) << "splice read ERROR: fd = " << fd << ", n = " << n;
            return -1;
        }
        ret += n;
        if (n < read_len) // done reading early
            break;
        len -= n;
    }
    return ret;
}

int MtpFfsHandle::read(void* data, int len) {
    return readHandle(mBulkOut, data, len);
}

int MtpFfsHandle::write(const void* data, int len) {
    return writeHandle(mBulkIn, data, len);
}

int MtpFfsHandle::start() {
    mLock.lock();

    mBulkIn.reset(TEMP_FAILURE_RETRY(open(FFS_MTP_EP_IN, O_RDWR)));
    if (mBulkIn < 0) {
        PLOG(ERROR) << FFS_MTP_EP_IN << ": cannot open bulk in ep";
        return -1;
    }

    mBulkOut.reset(TEMP_FAILURE_RETRY(open(FFS_MTP_EP_OUT, O_RDWR)));
    if (mBulkOut < 0) {
        PLOG(ERROR) << FFS_MTP_EP_OUT << ": cannot open bulk out ep";
        return -1;
    }

    mIntr.reset(TEMP_FAILURE_RETRY(open(FFS_MTP_EP_INTR, O_RDWR)));
    if (mIntr < 0) {
        PLOG(ERROR) << FFS_MTP_EP0 << ": cannot open intr ep";
        return -1;
    }

    mBuffer1.resize(MAX_FILE_CHUNK_SIZE);
    mBuffer2.resize(MAX_FILE_CHUNK_SIZE);
    posix_madvise(mBuffer1.data(), MAX_FILE_CHUNK_SIZE,
            POSIX_MADV_SEQUENTIAL | POSIX_MADV_WILLNEED);
    posix_madvise(mBuffer2.data(), MAX_FILE_CHUNK_SIZE,
            POSIX_MADV_SEQUENTIAL | POSIX_MADV_WILLNEED);

    // Handle control requests.
    std::thread t([this]() { this->controlLoop(); });
    t.detach();

    // Get device specific r/w size
    mMaxWrite = android::base::GetIntProperty("sys.usb.ffs.max_write", USB_FFS_MAX_WRITE);
    mMaxRead = android::base::GetIntProperty("sys.usb.ffs.max_read", USB_FFS_MAX_READ);

    size_t attempts = 0;
    while (mMaxWrite >= USB_FFS_MAX_WRITE && mMaxRead >= USB_FFS_MAX_READ &&
            attempts < ENDPOINT_ALLOC_RETRIES) {
        // If larger contiguous chunks of memory aren't available, attempt to try
        // smaller allocations.
        if (ioctl(mBulkIn, FUNCTIONFS_ENDPOINT_ALLOC, static_cast<__u32>(mMaxWrite)) ||
            ioctl(mBulkOut, FUNCTIONFS_ENDPOINT_ALLOC, static_cast<__u32>(mMaxRead))) {
            if (errno == ENODEV) {
                // Driver hasn't enabled endpoints yet.
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
                attempts += 1;
                continue;
            }
            mMaxWrite /= 2;
            mMaxRead /=2;
        } else {
            return 0;
        }
    }
    // Try to start MtpServer anyway, with the smallest max r/w values
    PLOG(ERROR) << "Functionfs could not allocate any memory!";
    return 0;
}

int MtpFfsHandle::configure(bool usePtp) {
    // Wait till previous server invocation has closed
    if (!mLock.try_lock_for(std::chrono::milliseconds(1000))) {
        LOG(ERROR) << "MtpServer was unable to get configure lock";
        return -1;
    }
    int ret = 0;

    // If ptp is changed, the configuration must be rewritten
    if (mPtp != usePtp) {
        closeEndpoints();
        closeConfig();
    }
    mPtp = usePtp;

    if (!initFunctionfs()) {
        ret = -1;
    }
    mLock.unlock();
    return ret;
}

void MtpFfsHandle::close() {
    closeEndpoints();
    mLock.unlock();
}

/* Read from USB and write to a local file. */
int MtpFfsHandle::receiveFile(mtp_file_range mfr, bool zero_packet) {
    // When receiving files, the incoming length is given in 32 bits.
    // A >4G file is given as 0xFFFFFFFF
    uint32_t file_length = mfr.length;
    uint64_t offset = mfr.offset;
    struct usb_endpoint_descriptor mBulkOut_desc;
    int packet_size;

    if (ioctl(mBulkOut, FUNCTIONFS_ENDPOINT_DESC, reinterpret_cast<unsigned long>(&mBulkOut_desc))) {
        PLOG(ERROR) << "Could not get FFS bulk-out descriptor";
        packet_size = MAX_PACKET_SIZE_HS;
    } else {
        packet_size = mBulkOut_desc.wMaxPacketSize;
    }

    char *data = mBuffer1.data();
    char *data2 = mBuffer2.data();

    struct aiocb aio;
    aio.aio_fildes = mfr.fd;
    aio.aio_buf = nullptr;
    struct aiocb *aiol[] = {&aio};
    int ret = -1;
    size_t length;
    bool read = false;
    bool write = false;
    bool short_packet = false;

    posix_fadvise(mfr.fd, 0, 0, POSIX_FADV_SEQUENTIAL | POSIX_FADV_NOREUSE);

    // Break down the file into pieces that fit in buffers
    while (file_length > 0 || write) {
        if (file_length > 0) {
            length = std::min(static_cast<uint32_t>(MAX_FILE_CHUNK_SIZE), file_length);

            // Read data from USB, handle errors after waiting for write thread.
            ret = readHandle(mBulkOut, data, length);

            if (file_length != MAX_MTP_FILE_SIZE && ret < static_cast<int>(length)) {
                ret = -1;
                errno = EIO;
            }
            read = true;
        }

        if (write) {
            // get the return status of the last write request
            aio_suspend(aiol, 1, nullptr);

            int written = aio_return(&aio);
            if (written == -1) {
                errno = aio_error(&aio);
                return -1;
            }
            if (static_cast<size_t>(written) < aio.aio_nbytes) {
                errno = EIO;
                return -1;
            }
            write = false;
        }

        // If there was an error reading above
        if (ret == -1) {
            return -1;
        }

        if (read) {
            if (file_length == MAX_MTP_FILE_SIZE) {
                // For larger files, receive until a short packet is received.
                if (static_cast<size_t>(ret) < length) {
                    file_length = 0;
                    short_packet = true;
                }
            } else {
                // Receive an empty packet if size is a multiple of the endpoint size.
                file_length -= ret;
            }
            // Enqueue a new write request
            aio.aio_buf = data;
            aio.aio_sink = mfr.fd;
            aio.aio_offset = offset;
            aio.aio_nbytes = ret;
            aio_write(&aio);

            offset += ret;
            std::swap(data, data2);

            write = true;
            read = false;
        }
    }
    if ((ret % packet_size == 0 && !short_packet) || zero_packet) {
        if (TEMP_FAILURE_RETRY(::read(mBulkOut, data, packet_size)) != 0) {
            return -1;
        }
    }
    return 0;
}

/* Read from a local file and send over USB. */
int MtpFfsHandle::sendFile(mtp_file_range mfr) {
    uint64_t file_length = mfr.length;
    uint32_t given_length = std::min(static_cast<uint64_t>(MAX_MTP_FILE_SIZE),
            file_length + sizeof(mtp_data_header));
    uint64_t offset = mfr.offset;
    struct usb_endpoint_descriptor mBulkIn_desc;
    int packet_size;

    if (ioctl(mBulkIn, FUNCTIONFS_ENDPOINT_DESC, reinterpret_cast<unsigned long>(&mBulkIn_desc))) {
        PLOG(ERROR) << "Could not get FFS bulk-in descriptor";
        packet_size = MAX_PACKET_SIZE_HS;
    } else {
        packet_size = mBulkIn_desc.wMaxPacketSize;
    }

    // If file_length is larger than a size_t, truncating would produce the wrong comparison.
    // Instead, promote the left side to 64 bits, then truncate the small result.
    int init_read_len = std::min(
            static_cast<uint64_t>(packet_size - sizeof(mtp_data_header)), file_length);

    char *data = mBuffer1.data();
    char *data2 = mBuffer2.data();

    posix_fadvise(mfr.fd, 0, 0, POSIX_FADV_SEQUENTIAL | POSIX_FADV_NOREUSE);

    struct aiocb aio;
    aio.aio_fildes = mfr.fd;
    struct aiocb *aiol[] = {&aio};
    int ret, length;
    int error = 0;
    bool read = false;
    bool write = false;

    // Send the header data
    mtp_data_header *header = reinterpret_cast<mtp_data_header*>(data);
    header->length = __cpu_to_le32(given_length);
    header->type = __cpu_to_le16(2); /* data packet */
    header->command = __cpu_to_le16(mfr.command);
    header->transaction_id = __cpu_to_le32(mfr.transaction_id);

    // Some hosts don't support header/data separation even though MTP allows it
    // Handle by filling first packet with initial file data
    if (TEMP_FAILURE_RETRY(pread(mfr.fd, reinterpret_cast<char*>(data) +
                    sizeof(mtp_data_header), init_read_len, offset))
            != init_read_len) return -1;
    if (writeHandle(mBulkIn, data, sizeof(mtp_data_header) + init_read_len) == -1) return -1;
    file_length -= init_read_len;
    offset += init_read_len;
    ret = init_read_len + sizeof(mtp_data_header);

    // Break down the file into pieces that fit in buffers
    while(file_length > 0) {
        if (read) {
            // Wait for the previous read to finish
            aio_suspend(aiol, 1, nullptr);
            ret = aio_return(&aio);
            if (ret == -1) {
                errno = aio_error(&aio);
                return -1;
            }
            if (static_cast<size_t>(ret) < aio.aio_nbytes) {
                errno = EIO;
                return -1;
            }

            file_length -= ret;
            offset += ret;
            std::swap(data, data2);
            read = false;
            write = true;
        }

        if (error == -1) {
            return -1;
        }

        if (file_length > 0) {
            length = std::min(static_cast<uint64_t>(MAX_FILE_CHUNK_SIZE), file_length);
            // Queue up another read
            aio.aio_buf = data;
            aio.aio_offset = offset;
            aio.aio_nbytes = length;
            aio_read(&aio);
            read = true;
        }

        if (write) {
            if (writeHandle(mBulkIn, data2, ret) == -1) {
                error = -1;
            }
            write = false;
        }
    }

    if (ret % packet_size == 0) {
        // If the last packet wasn't short, send a final empty packet
        if (TEMP_FAILURE_RETRY(::write(mBulkIn, data, 0)) != 0) {
            return -1;
        }
    }

    return 0;
}

int MtpFfsHandle::sendEvent(mtp_event me) {
    // Mimic the behavior of f_mtp by sending the event async.
    // Events aren't critical to the connection, so we don't need to check the return value.
    char *temp = new char[me.length];
    memcpy(temp, me.data, me.length);
    me.data = temp;
    std::thread t([this, me]() { return this->doSendEvent(me); });
    t.detach();
    return 0;
}

void MtpFfsHandle::doSendEvent(mtp_event me) {
    unsigned length = me.length;
    int ret = ::write(mIntr, me.data, length);
    if (static_cast<unsigned>(ret) != length)
        PLOG(ERROR) << "Mtp error sending event thread!";
    delete[] reinterpret_cast<char*>(me.data);
}

} // namespace android

IMtpHandle *get_ffs_handle() {
    return new android::MtpFfsHandle();
}

