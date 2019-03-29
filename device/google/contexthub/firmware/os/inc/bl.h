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
#ifndef _BL_H_
#define _BL_H_

#include <stdbool.h>
#include <stdarg.h>
#include <stdint.h>

#include <plat/bl.h>

#define OS_UPDT_SUCCESS                0
#define OS_UPDT_HDR_CHECK_FAILED       1
#define OS_UPDT_HDR_MARKER_INVALID     2
#define OS_UPDT_UNKNOWN_PUBKEY         3
#define OS_UPDT_INVALID_SIGNATURE      4
#define OS_UPDT_INVALID_SIGNATURE_HASH 5

#define BL_SCAN_OFFSET      0x00000100

#define BL_VERSION_1        1
#define BL_VERSION_CUR      BL_VERSION_1

#define BL _BL.api

struct Sha2state;
struct RsaState;
struct AesContext;
struct AesSetupTempWorksSpace;
struct AesCbcContext;

struct BlApiTable {
    //ver 1 bl supports:

    //basics
    uint32_t        (*blGetVersion)(void);
    void            (*blReboot)(void);
    void            (*blGetSnum)(uint32_t *snum, uint32_t length);

    //flash
    bool            (*blProgramShared)(uint8_t *dst, const uint8_t *src, uint32_t length, uint32_t key1, uint32_t key2);
    bool            (*blEraseShared)(uint32_t key1, uint32_t key2);
    bool            (*blProgramEe)(uint8_t *dst, const uint8_t *src, uint32_t length, uint32_t key1, uint32_t key2);

    //security data
    const uint32_t* (*blGetPubKeysInfo)(uint32_t *numKeys);

    //hashing, encryption, signature apis
    const uint32_t* (*blRsaPubOpIterative)(struct RsaState* state, const uint32_t *a, const uint32_t *c, uint32_t *state1, uint32_t *state2, uint32_t *stepP);
    void            (*blSha2init)(struct Sha2state *state);
    void            (*blSha2processBytes)(struct Sha2state *state, const void *bytes, uint32_t numBytes);
    const uint32_t* (*blSha2finish)(struct Sha2state *state);
    void            (*blAesInitForEncr)(struct AesContext *ctx, const uint32_t *k);
    void            (*blAesInitForDecr)(struct AesContext *ctx, struct AesSetupTempWorksSpace *tmpSpace, const uint32_t *k);
    void            (*blAesEncr)(struct AesContext *ctx, const uint32_t *src, uint32_t *dst);
    void            (*blAesDecr)(struct AesContext *ctx, const uint32_t *src, uint32_t *dst);
    void            (*blAesCbcInitForEncr)(struct AesCbcContext *ctx, const uint32_t *k, const uint32_t *iv);
    void            (*blAesCbcInitForDecr)(struct AesCbcContext *ctx, const uint32_t *k, const uint32_t *iv);
    void            (*blAesCbcEncr)(struct AesCbcContext *ctx, const uint32_t *src, uint32_t *dst);
    void            (*blAesCbcDecr)(struct AesCbcContext *ctx, const uint32_t *src, uint32_t *dst);
    const uint32_t* (*blSigPaddingVerify)(const uint32_t *rsaResult); //return pointer to hash inside the rsaResult or NULL on error

    // extension: for binary compatibility, placed here
    uint32_t        (*blVerifyOsUpdate)(void);
};

struct BlTable {
    struct BlVecTable vec;
    struct BlApiTable api;
};

//for using outside of bootloader
extern struct BlTable _BL;

//for code in bootloader to log things
void blLog(const char *str, ...);

bool blEraseSectors(uint32_t sector_cnt, uint8_t *erase_mask, uint32_t key1, uint32_t key2);
bool blPlatProgramFlash(uint8_t *dst, const uint8_t *src, uint32_t length, uint32_t key1, uint32_t key2);
uint32_t blDisableInts(void);
void blRestoreInts(uint32_t state);
void blReboot(void);

// return device serial number in array pointed to by snum
// return value is actual copied data size in bytes
uint32_t blGetSnum(uint32_t *snum, uint32_t length);
// early platform init; after return from this call blHostActive() and blConfigIo() may be called immediately
void blSetup();
// platform cleanup before leaving bootloader; restore configuration altered by blSetup
// on return from this call system state is similar enough to conditions seen after HW reset
// it is safe to re-run all the bootloader code sequence again
// it is also safe to start the custom code from flash
void blCleanup();
// returns true if host is requesting us to start bootloader
bool blHostActive();
// prepare data channel to exchange data with host
void blConfigIo();
// reads data stream from host until synccode is seen; returns true if received synccode, false otherwise
// must be called after blConfigIo()
bool blSyncWait(uint32_t syncCode);
// reset IO channel HW;
// makes controller ready for next data packet
// current packet is abandoned
void blResetRxData();

// exchange 1 byte in both directions (SPI only)
uint8_t blSpiTxRxByte(uint32_t val);

// this must be called from reset vector handler
// once data bss and stack are set properly
// this method executes boot protocol, and returns when it is done
// after calling method bootloader must jump to the OS boot address
void blMain(uint32_t bootAddr);

#endif /* _BL_H_ */
