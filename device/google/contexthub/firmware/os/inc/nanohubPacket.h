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

#ifndef __NANOHUBPACKET_H
#define __NANOHUBPACKET_H

/**
 * Formats and constants related to nanohub packets.  This header is intended
 * to be shared between the host Linux kernel and the nanohub implementation.
 */
#include "toolchain.h"

#ifdef __KERNEL__
#include <linux/types.h>
#else
#include <hostIntf.h>
#include <stdint.h>

typedef uint16_t __le16;
typedef uint16_t __be16;
typedef uint32_t __le32;
typedef uint32_t __be32;
typedef uint64_t __le64;
typedef uint64_t __be64;
#endif

SET_PACKED_STRUCT_MODE_ON
struct NanohubPacket {
    uint8_t sync;
    __le32 seq;
    __le32 reason;
    uint8_t len;
    uint8_t data[0];
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct NanohubPacketFooter {
    __le32 crc;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

static inline struct NanohubPacketFooter *nanohubGetPacketFooter(struct NanohubPacket *packet)
{
    return (struct NanohubPacketFooter *)(packet->data + packet->len);
}

#define NANOHUB_PACKET_SIZE(len) \
    (sizeof(struct NanohubPacket) + (len) + sizeof(struct NanohubPacketFooter))

#define NANOHUB_PACKET_PAYLOAD_MAX    255
#define NANOHUB_PACKET_SIZE_MAX       NANOHUB_PACKET_SIZE(NANOHUB_PACKET_PAYLOAD_MAX)
#define NANOHUB_PACKET_SIZE_MIN       NANOHUB_PACKET_SIZE(0)

#define NANOHUB_SYNC_BYTE             0x31

#define NANOHUB_PREAMBLE_BYTE         0xFF
#define NANOHUB_ACK_PREAMBLE_LEN      16
#define NANOHUB_PAYLOAD_PREAMBLE_LEN  512
#define NANOHUB_RSA_KEY_CHUNK_LEN     64

#define NANOHUB_INT_BOOT_COMPLETE     0
#define NANOHUB_INT_WAKE_COMPLETE     0
#define NANOHUB_INT_WAKEUP            1
#define NANOHUB_INT_NONWAKEUP         2
#define NANOHUB_INT_CMD_WAIT          3

#define NANOHUB_REASON_ACK                    0x00000000
#define NANOHUB_REASON_NAK                    0x00000001
#define NANOHUB_REASON_NAK_BUSY               0x00000002

/**
 * INFORMATIONAL
 */

#define NANOHUB_REASON_GET_OS_HW_VERSIONS     0x00001000
#if defined(__GNUC__)
SET_PACKED_STRUCT_MODE_ON
struct NanohubOsHwVersionsRequest {
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF
#endif

SET_PACKED_STRUCT_MODE_ON
struct NanohubOsHwVersionsResponse {
    __le16 hwType;
    __le16 hwVer;
    __le16 blVer;
    __le16 osVer;
    __le32 variantVer;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

#define NANOHUB_REASON_GET_APP_VERSIONS       0x00001001

SET_PACKED_STRUCT_MODE_ON
struct NanohubAppVersionsRequest {
    __le64 appId;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct NanohubAppVersionsResponse {
    __le32 appVer;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

#define NANOHUB_REASON_QUERY_APP_INFO         0x00001002

SET_PACKED_STRUCT_MODE_ON
struct NanohubAppInfoRequest {
    __le32 appIdx;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct NanohubAppInfoResponse {
    __le64 appId;
    __le32 appVer;
    __le32 appSize;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

#define NANOHUB_REASON_START_FIRMWARE_UPLOAD  0x00001040

SET_PACKED_STRUCT_MODE_ON
struct NanohubStartFirmwareUploadRequest {
    __le32 size;
    __le32 crc;
    uint8_t type;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct NanohubStartFirmwareUploadResponse {
    uint8_t accepted;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

#define NANOHUB_REASON_FIRMWARE_CHUNK         0x00001041

SET_PACKED_STRUCT_MODE_ON
struct NanohubFirmwareChunkRequest {
    __le32 offset;
    uint8_t data[NANOHUB_PACKET_PAYLOAD_MAX-sizeof(__le32)];
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

enum NanohubFirmwareChunkReply {
    NANOHUB_FIRMWARE_CHUNK_REPLY_ACCEPTED = 0,
    NANOHUB_FIRMWARE_CHUNK_REPLY_WAIT,
    NANOHUB_FIRMWARE_CHUNK_REPLY_RESEND,
    NANOHUB_FIRMWARE_CHUNK_REPLY_RESTART,
    NANOHUB_FIRMWARE_CHUNK_REPLY_CANCEL,
    NANOHUB_FIRMWARE_CHUNK_REPLY_CANCEL_NO_RETRY,
};

SET_PACKED_STRUCT_MODE_ON
struct NanohubFirmwareChunkResponse {
    uint8_t chunkReply;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

#define NANOHUB_REASON_FINISH_FIRMWARE_UPLOAD 0x00001042

#if defined(__GNUC__)
SET_PACKED_STRUCT_MODE_ON
struct NanohubFinishFirmwareUploadRequest {
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF
#endif

enum NanohubFirmwareUploadReply {
    NANOHUB_FIRMWARE_UPLOAD_SUCCESS = 0,
    NANOHUB_FIRMWARE_UPLOAD_PROCESSING,
    NANOHUB_FIRMWARE_UPLOAD_WAITING_FOR_DATA,
    NANOHUB_FIRMWARE_UPLOAD_APP_SEC_KEY_NOT_FOUND,
    NANOHUB_FIRMWARE_UPLOAD_APP_SEC_HEADER_ERROR,
    NANOHUB_FIRMWARE_UPLOAD_APP_SEC_TOO_MUCH_DATA,
    NANOHUB_FIRMWARE_UPLOAD_APP_SEC_TOO_LITTLE_DATA,
    NANOHUB_FIRMWARE_UPLOAD_APP_SEC_SIG_VERIFY_FAIL,
    NANOHUB_FIRMWARE_UPLOAD_APP_SEC_SIG_DECODE_FAIL,
    NANOHUB_FIRMWARE_UPLOAD_APP_SEC_SIG_ROOT_UNKNOWN,
    NANOHUB_FIRMWARE_UPLOAD_APP_SEC_MEMORY_ERROR,
    NANOHUB_FIRMWARE_UPLOAD_APP_SEC_INVALID_DATA,
    NANOHUB_FIRMWARE_UPLOAD_APP_SEC_VERIFY_FAILED,
    NANOHUB_FIRMWARE_UPLOAD_APP_SEC_BAD,
};

SET_PACKED_STRUCT_MODE_ON
struct NanohubFinishFirmwareUploadResponse {
   uint8_t uploadReply;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

#define NANOHUB_REASON_GET_INTERRUPT          0x00001080

SET_PACKED_STRUCT_MODE_ON
struct NanohubGetInterruptRequest {
    uint32_t clear[HOSTINTF_MAX_INTERRUPTS/(32*sizeof(uint8_t))];
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct NanohubGetInterruptResponse {
    uint32_t interrupts[HOSTINTF_MAX_INTERRUPTS/(32*sizeof(uint8_t))];
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

#define NANOHUB_REASON_MASK_INTERRUPT         0x00001081

SET_PACKED_STRUCT_MODE_ON
struct NanohubMaskInterruptRequest {
    uint8_t interrupt;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct NanohubMaskInterruptResponse {
    uint8_t accepted;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

#define NANOHUB_REASON_UNMASK_INTERRUPT       0x00001082

SET_PACKED_STRUCT_MODE_ON
struct NanohubUnmaskInterruptRequest {
    uint8_t interrupt;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct NanohubUnmaskInterruptResponse {
    uint8_t accepted;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

#define NANOHUB_REASON_READ_EVENT             0x00001090

SET_PACKED_STRUCT_MODE_ON
struct NanohubReadEventRequest {
    __le64 apBootTime;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct NanohubReadEventResponse {
    __le32 evtType;
    uint8_t evtData[NANOHUB_PACKET_PAYLOAD_MAX - sizeof(__le32)];
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

#define NANOHUB_REASON_WRITE_EVENT            0x00001091

SET_PACKED_STRUCT_MODE_ON
struct NanohubWriteEventRequest {
    __le32 evtType;
    uint8_t evtData[NANOHUB_PACKET_PAYLOAD_MAX - sizeof(__le32)];
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct NanohubWriteEventResponse {
    uint8_t accepted;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct NanohubHalHdr {
    uint64_t appId;
    uint8_t len;
    uint8_t msg;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

#define NANOHUB_HAL_EXT_APPS_ON     0
#define NANOHUB_HAL_EXT_APPS_OFF    1
#define NANOHUB_HAL_EXT_APP_DELETE  2

// this behaves more stable w.r.t. endianness than bit field
// this is setting byte fields in MgmtStatus response
// the high-order bit, if set, is indication of counter overflow
#define SET_COUNTER(counter, val) (counter = (val & 0x7F) | (val > 0x7F ? 0x80 : 0))

SET_PACKED_STRUCT_MODE_ON
struct MgmtStatus {
    union {
        __le32 value;
        // NOTE: union fields are accessed in CPU native mode
        struct {
            uint8_t app;
            uint8_t task;
            uint8_t op;
            uint8_t erase;
        } ATTRIBUTE_PACKED;
    };
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct NanohubHalMgmtRx {
    __le64 appId;
    struct MgmtStatus stat;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct NanohubHalMgmtTx {
    struct NanohubHalHdr hdr;
    __le32 status;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

#define NANOHUB_HAL_QUERY_MEMINFO   3
#define NANOHUB_HAL_QUERY_APPS      4

SET_PACKED_STRUCT_MODE_ON
struct NanohubHalQueryAppsRx {
    __le32 idx;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct NanohubHalQueryAppsTx {
    struct NanohubHalHdr hdr;
    __le64 appId;
    __le32 version;
    __le32 flashUse;
    __le32 ramUse;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

#define NANOHUB_HAL_QUERY_RSA_KEYS  5

SET_PACKED_STRUCT_MODE_ON
struct NanohubHalQueryRsaKeysRx {
    __le32 offset;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct NanohubHalQueryRsaKeysTx {
    struct NanohubHalHdr hdr;
    uint8_t data[];
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

#define NANOHUB_HAL_START_UPLOAD    6

SET_PACKED_STRUCT_MODE_ON
struct NanohubHalStartUploadRx {
    uint8_t isOs;
    __le32 length;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct NanohubHalStartUploadTx {
    struct NanohubHalHdr hdr;
    uint8_t success;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

#define NANOHUB_HAL_CONT_UPLOAD     7

SET_PACKED_STRUCT_MODE_ON
struct NanohubHalContUploadRx {
    __le32 offset;
    uint8_t data[];
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct NanohubHalContUploadTx {
    struct NanohubHalHdr hdr;
    uint8_t success;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

#define NANOHUB_HAL_FINISH_UPLOAD   8

SET_PACKED_STRUCT_MODE_ON
struct NanohubHalFinishUploadTx {
    struct NanohubHalHdr hdr;
    uint8_t success;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

#define NANOHUB_HAL_REBOOT          9

SET_PACKED_STRUCT_MODE_ON
struct NanohubHalRebootTx {
    struct NanohubHalHdr hdr;
    __le32 reason;
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

#endif /* __NANOHUBPACKET_H */
