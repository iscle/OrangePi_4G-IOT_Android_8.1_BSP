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

#ifndef _NANOHUB_NANOHUB_H_
#define _NANOHUB_NANOHUB_H_

#include <inttypes.h>
#include <nanohub/aes.h>

/* this file is collection of nanohub-related definitions shared between multiple parties,
 * including but not limited to: HAL, Kernel, utilities, nanohub FW
 * it provides minimum details on nanohub implementation, necessary to reliably identify it, and
 * generate/parse compatible images
 */

#define NANOHUB_OS_PATCH_LEVEL  0x0000

#define NANOHUB_VENDOR_GOOGLE      UINT64_C(0x476F6F676C) // "Googl"
#define NANOHUB_VENDOR_STMICRO     UINT64_C(0x53544d6963) // "STMic"

#define NANOAPP_SIGNED_FLAG    0x1  // contents is signed with one or more signature block(s)
#define NANOAPP_ENCRYPTED_FLAG 0x2  // contents is encrypted with exactly one encryption key

#define NANOAPP_AOSP_MAGIC (((uint32_t)'N' <<  0) | ((uint32_t)'A' <<  8) | ((uint32_t)'N' << 16) | ((uint32_t)'O' << 24))
#define NANOAPP_FW_MAGIC (((uint32_t)'N' <<  0) | ((uint32_t)'B' <<  8) | ((uint32_t)'I' << 16) | ((uint32_t)'N' << 24))
#define GOOGLE_LAYOUT_MAGIC (((uint32_t)'G' <<  0) | ((uint32_t)'o' <<  8) | ((uint32_t)'o' << 16) | ((uint32_t)'g' << 24))

#define APP_ID_ANY                 UINT64_C(0xFFFFFFFFFFFFFFFF)
#define APP_VENDOR_ANY             UINT64_C(0xFFFFFFFFFF)
#define APP_VENDOR_SHF             (24)
#define APP_SEQ_ID_ANY             UINT32_C(0xFFFFFF)
#define APP_ID_GET_VENDOR(appid)   ((appid) >> APP_VENDOR_SHF)
#define APP_ID_GET_SEQ_ID(appid)   ((appid) & APP_SEQ_ID_ANY)
#define APP_ID_MAKE(vendor, app)   ((((uint64_t)(vendor)) << APP_VENDOR_SHF) | ((app) & APP_SEQ_ID_ANY))

#ifndef CONTEXT_HUB_H
// The binary format below is in little endian format; borrowed from CONTEXT_HUB_H
struct nano_app_binary_t {
    uint32_t header_version;       // 0x1 for this version
    uint32_t magic;                // "NANO"
    uint64_t app_id;               // App Id contains vendor id
    uint32_t app_version;          // Version of the app
    uint32_t flags;                // Signed, encrypted
    uint64_t hw_hub_type;          // which hub type is this compiled for
    uint32_t reserved[2];          // Should be all zeroes
    uint8_t  custom_binary[0];     // start of custom binary data
};

#endif

struct HostMsgHdr {
    uint32_t eventId;
    uint64_t appId;
    uint8_t len;
} __attribute__((packed));

struct HostMsgHdrChre {
    uint32_t eventId;
    uint64_t appId;
    uint8_t len;
    uint32_t appEventId;
} __attribute__((packed));

// we translate AOSP header into FW header: this header is in LE format
// please maintain natural alignment for every field (matters to Intel; otherwise is has to be declared as packed)
struct FwCommonHdr {
    uint32_t magic;         // external & internal: NANOAPP_FW_MAGIC
    uint16_t fwVer;         // external & internal: set to 1; header version
    uint16_t fwFlags;       // external & internal: class : EXTERNAL/INTERNAL, EXEC/NOEXEC, APP/KERNEL/EEDATA/...
    uint64_t appId;         // external: copy from AOSP header; internal: defined locally
    uint32_t appVer;        // external: copy from AOSP header; internal: defined locally
    uint8_t  payInfoType;   // external: copy ImageLayout::payload; internal: LAYOUT_APP
    uint8_t  payInfoSize;   // sizeof(PayloadInfo) for this payload type
    uint8_t  rfu[2];        // filled with 0xFF
};

struct SectInfo {
    uint32_t data_start;
    uint32_t data_end;
    uint32_t data_data;

    uint32_t bss_start;
    uint32_t bss_end;

    uint32_t got_start;
    uint32_t got_end;
    uint32_t rel_start;
    uint32_t rel_end;
};

// this is platform-invariant version of struct TaskFuncs (from seos.h)
struct AppVectors {
    uint32_t init;
    uint32_t end;
    uint32_t handle;
};

#define FLASH_RELOC_OFFSET offsetof(struct AppHdr, sect)        // used by appSupport.c at run time
#define BINARY_RELOC_OFFSET offsetof(struct BinHdr, sect)       // used by postprocess at build time

struct BinCommonHdr {
    uint32_t magic;
    uint32_t appVer;
};

// binary nanoapp image (.bin) produced by objcopy starts with this binary header (LE)
struct BinHdr {
    struct BinCommonHdr hdr;
    struct SectInfo     sect;
    struct AppVectors   vec;
};

// FW nanoapp image starts with this binary header (LE) in flash
struct AppHdr {
    struct FwCommonHdr hdr;
    struct SectInfo    sect;
    struct AppVectors  vec;
};

struct AppSecSignHdr {
    uint32_t appDataLen;
};

struct AppSecEncrHdr {
    uint64_t keyID;
    uint32_t dataLen;
    uint32_t IV[AES_BLOCK_WORDS];
};

#define LAYOUT_APP  1
#define LAYOUT_KEY  2
#define LAYOUT_OS   3
#define LAYOUT_DATA 4

struct ImageLayout {
    uint32_t magic;     // Layout ID: (GOOGLE_LAYOUT_MAGIC for this implementation)
    uint8_t  version;   // layout version
    uint8_t  payload;   // type of payload: APP, SECRET KEY, OS IMAGE, USER DATA, ...
    uint16_t flags;     // layout flags: extra options for certain payload types; payload-specific
};

// .napp image starts with this binary header (LE)
// it is optionally followed by AppSecSignHdr and/or AppSecEncrHdr
// all of the above are included in signing hash, but never encrypted
// encryption (if enabled) starts immediately after those
struct ImageHeader {
    struct nano_app_binary_t aosp;
    struct ImageLayout   layout;
};

#define CKK_RSA 0x00
#define CKK_AES 0x1F

#define CKO_PUBLIC_KEY  0x02
#define CKO_PRIVATE_KEY 0x03
#define CKO_SECRET_KEY  0x04

// flags
#define FL_KI_ENFORCE_ID 0x0001  // if set, size, key_type, obj_type must be valid

// payload header format: LAYOUT_KEY
struct KeyInfo {
    union {
        struct {
            uint16_t id;        // arbitrary number, != 0, equivalent of PKCS#11 name
            uint16_t flags;     // key flags (additional PKCS#11 attrs, unused for now; must be 0)
            uint16_t size;      // key size in bits
            uint8_t  key_type;  // 8 LSB of PKCS-11 CKK_<KEY TYPE>
            uint8_t  obj_type;  // 8 LSB of PKCS-11 CKO_<OBJ TYPE>
        };
        uint64_t data;          // complete 64-bit key-id, unique within this APP namespace (complete id is <APP_ID | KEY_INFO> 128 bits)
    };
};

#define AES_KEY_ID(_id) (((struct KeyInfo){ .key_type = CKK_AES, .obj_type = CKO_SECRET_KEY, .size = 256, .id = (_id) }).data)

// payload header format: LAYOUT_APP
struct AppInfo {
    struct SectInfo   sect;
    struct AppVectors vec;
};

#define OS_UPDT_MARKER_INPROGRESS     0xFF
#define OS_UPDT_MARKER_DOWNLOADED     0xFE
#define OS_UPDT_MARKER_VERIFIED       0xF0
#define OS_UPDT_MARKER_INVALID        0x00
#define OS_UPDT_MAGIC                 "Nanohub OS" //11 bytes incl terminator

// payload header format: LAYOUT_OS
struct OsUpdateHdr {
    char magic[11];
    uint8_t marker; //OS_UPDT_MARKER_INPROGRESS -> OS_UPDT_MARKER_DOWNLOADED -> OS_UPDT_MARKER_VERIFIED / OS_UPDT_INVALID
    uint32_t size;  //does not include the mandatory signature (using device key) that follows
};

// payload header format: LAYOUT_DATA
struct DataInfo {
    uint32_t id;
    uint32_t size;
};

#endif // _NANOHUB_NANOHUB_H_
