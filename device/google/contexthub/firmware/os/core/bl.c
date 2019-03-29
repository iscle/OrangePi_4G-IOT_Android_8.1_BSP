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

#include <variant/variant.h>

#include <bl.h>

#include <plat/cmsis.h>
#include <plat/gpio.h>

#include <nanohub/sha2.h>
#include <nanohub/aes.h>
#include <nanohub/rsa.h>
#include <nanohub/nanohub.h>

#include <printf.h>
#include <string.h>

static uint32_t blVerifyOsImage(const uint8_t *addr, struct OsUpdateHdr **start, uint32_t *size);


//for comms protocol
#define BL_SYNC_IN                      0x5A
#define BL_ACK                          0x79
#define BL_NAK                          0x1F
#define BL_SYNC_OUT                     0xA5

#define BL_CMD_GET                      0x00
#define BL_CMD_READ_MEM                 0x11
#define BL_CMD_WRITE_MEM                0x31
#define BL_CMD_ERASE                    0x44
#define BL_CMD_GET_SIZES                0xEE /* our own command. reports: {u32 osSz, u32 sharedSz, u32 eeSz} all in big endian */
#define BL_CMD_UPDATE_FINISHED          0xEF /* our own command. attempts to verify the update -> ACK/NAK. MUST be called after upload to mark it as completed */

#define BL_ERROR                        0xDEADBEAF /* returned in place of command in case of exchange errors */


#define BL_SHARED_AREA_FAKE_ERASE_BLK   0xFFF0
#define BL_SHARED_AREA_FAKE_ADDR        0x50000000


//linker provides these
extern uint32_t __pubkeys_start[];
extern uint32_t __pubkeys_end[];
extern uint8_t __eedata_start[];
extern uint8_t __eedata_end[];
extern uint8_t __code_start[];
extern uint8_t __code_end[];
extern uint8_t __shared_start[];
extern uint8_t __shared_end[];

enum BlFlashType
{
    BL_FLASH_BL,
    BL_FLASH_EEDATA,
    BL_FLASH_KERNEL,
    BL_FLASH_SHARED
};

static const struct blFlashTable   // For erase code, we need to know which page a given memory address is in
{
    uint8_t *address;
    uint32_t length;
    uint32_t type;
} mBlFlashTable[] =
#ifndef BL_FLASH_TABLE
{
    { (uint8_t *)(&BL),                      0x04000, BL_FLASH_BL     },
    { (uint8_t *)(__eedata_start),           0x04000, BL_FLASH_EEDATA },
    { (uint8_t *)(__eedata_start + 0x04000), 0x04000, BL_FLASH_EEDATA },
    { (uint8_t *)(__code_start),             0x04000, BL_FLASH_KERNEL },
    { (uint8_t *)(__code_start + 0x04000),   0x10000, BL_FLASH_KERNEL },
    { (uint8_t *)(__code_start + 0x14000),   0x20000, BL_FLASH_KERNEL },
    { (uint8_t *)(__shared_start),           0x20000, BL_FLASH_SHARED },
    { (uint8_t *)(__shared_start + 0x20000), 0x20000, BL_FLASH_SHARED },
};
#else
BL_FLASH_TABLE;
#endif

static const char mOsUpdateMagic[] = OS_UPDT_MAGIC;

#ifdef DEBUG_UART_PIN

static bool blLogPutcharF(void *userData, char ch)
{
    if (ch == '\n')
        gpioBitbangedUartOut('\r');

    gpioBitbangedUartOut(ch);

    return true;
}

void blLog(const char *str, ...)
{
    va_list vl;

    va_start(vl, str);
    cvprintf(blLogPutcharF, 0, NULL, str, vl);
    va_end(vl);
}

#else

#define blLog(...)

#endif

static uint32_t blExtApiGetVersion(void)
{
    return BL_VERSION_CUR;
}

static bool blProgramFlash(uint8_t *dst, const uint8_t *src, uint32_t length, uint32_t key1, uint32_t key2)
{
    const uint32_t sector_cnt = sizeof(mBlFlashTable) / sizeof(struct blFlashTable);
    uint32_t offset, i, j = 0;
    uint8_t *ptr;

    if (((length == 0)) ||
        ((0xFFFFFFFF - (uint32_t)dst) < (length - 1)) ||
        ((dst < mBlFlashTable[0].address)) ||
        ((dst + length) > (mBlFlashTable[sector_cnt-1].address +
                           mBlFlashTable[sector_cnt-1].length))) {
        return false;
    }

    // compute which flash block we are starting from
    for (i = 0; i < sector_cnt; i++) {
        if (dst >= mBlFlashTable[i].address &&
            dst < (mBlFlashTable[i].address + mBlFlashTable[i].length)) {
            break;
        }
    }

    // now loop through all the flash blocks and see if we have to do any
    // 0 -> 1 transitions of a bit. If so, return false
    // 1 -> 0 transitions of a bit do not require an erase
    offset = (uint32_t)(dst - mBlFlashTable[i].address);
    ptr = mBlFlashTable[i].address;
    while (j < length && i < sector_cnt) {
        if (offset == mBlFlashTable[i].length) {
            i++;
            offset = 0;
            ptr = mBlFlashTable[i].address;
        }

        if ((ptr[offset] & src[j]) != src[j]) {
            return false;
        } else {
            j++;
            offset++;
        }
    }

    if (!blPlatProgramFlash(dst, src, length, key1, key2))
        return false;

    return !memcmp(dst, src, length);
}

static void blExtApiGetSnum(uint32_t *snum, uint32_t length)
{
    blGetSnum(snum, length);
}

static bool blProgramTypedArea(uint8_t *dst, const uint8_t *src, uint32_t length, uint32_t type, uint32_t key1, uint32_t key2)
{
    const uint32_t sector_cnt = sizeof(mBlFlashTable) / sizeof(struct blFlashTable);
    uint32_t i;

    for (i = 0; i < sector_cnt; i++) {

        if ((dst >= mBlFlashTable[i].address &&
             dst < (mBlFlashTable[i].address + mBlFlashTable[i].length)) ||
            (dst < mBlFlashTable[i].address &&
             (dst + length > mBlFlashTable[i].address))) {
            if (mBlFlashTable[i].type != type)
                return false;
        }
    }

    return blProgramFlash(dst, src, length, key1, key2);
}

static bool blExtApiProgramSharedArea(uint8_t *dst, const uint8_t *src, uint32_t length, uint32_t key1, uint32_t key2)
{
    return blProgramTypedArea(dst, src, length, BL_FLASH_SHARED, key1, key2);
}

static bool blExtApiProgramEe(uint8_t *dst, const uint8_t *src, uint32_t length, uint32_t key1, uint32_t key2)
{
    return blProgramTypedArea(dst, src, length, BL_FLASH_EEDATA, key1, key2);
}

static bool blEraseTypedArea(uint32_t type, uint32_t key1, uint32_t key2)
{
    const uint32_t sector_cnt = sizeof(mBlFlashTable) / sizeof(struct blFlashTable);
    uint32_t i, erase_cnt = 0;
    uint8_t erase_mask[sector_cnt];

    for (i = 0; i < sector_cnt; i++) {
        if (mBlFlashTable[i].type == type) {
            erase_mask[i] = 1;
            erase_cnt++;
        } else {
            erase_mask[i] = 0;
        }
    }

    if (erase_cnt)
        blEraseSectors(sector_cnt, erase_mask, key1, key2);

    return true; //we assume erase worked
}

static bool blExtApiEraseSharedArea(uint32_t key1, uint32_t key2)
{
    return blEraseTypedArea(BL_FLASH_SHARED, key1, key2);
}

static uint32_t blVerifyOsUpdate(struct OsUpdateHdr **start, uint32_t *size)
{
    uint32_t ret;
    int i;

    for (i = 0; i < BL_SCAN_OFFSET; i += 4) {
        ret = blVerifyOsImage(__shared_start + i, start, size);
        if (ret != OS_UPDT_HDR_CHECK_FAILED)
            break;
    }

    return ret;
}

static uint32_t blExtApiVerifyOsUpdate(void)
{
    return blVerifyOsUpdate(NULL, NULL);
}

static void blExtApiReboot(void)
{
    blReboot();
}

static const uint32_t *blExtApiGetRsaKeyInfo(uint32_t *numKeys)
{
    uint32_t numWords = __pubkeys_end - __pubkeys_start;

    if (numWords % RSA_WORDS) // something is wrong
        return NULL;

    *numKeys = numWords / RSA_WORDS;
    return __pubkeys_start;
}

static const uint32_t* blExtApiSigPaddingVerify(const uint32_t *rsaResult)
{
    uint32_t i;

    //all but first and last word of padding MUST have no zero bytes
    for (i = SHA2_HASH_WORDS + 1; i < RSA_WORDS - 1; i++) {
        if (!(uint8_t)(rsaResult[i] >>  0))
            return NULL;
        if (!(uint8_t)(rsaResult[i] >>  8))
            return NULL;
        if (!(uint8_t)(rsaResult[i] >> 16))
            return NULL;
        if (!(uint8_t)(rsaResult[i] >> 24))
            return NULL;
    }

    //first padding word must have all nonzero bytes except low byte
    if ((rsaResult[SHA2_HASH_WORDS] & 0xff) || !(rsaResult[SHA2_HASH_WORDS] & 0xff00) || !(rsaResult[SHA2_HASH_WORDS] & 0xff0000) || !(rsaResult[SHA2_HASH_WORDS] & 0xff000000))
        return NULL;

    //last padding word must have 0x0002 in top 16 bits and nonzero random bytes in lower bytes
    if ((rsaResult[RSA_WORDS - 1] >> 16) != 2)
        return NULL;
    if (!(rsaResult[RSA_WORDS - 1] & 0xff00) || !(rsaResult[RSA_WORDS - 1] & 0xff))
        return NULL;

    return rsaResult;
}

static void blApplyVerifiedUpdate(const struct OsUpdateHdr *os) //only called if an update has been found to exist and be valid, signed, etc!
{
    //copy shared to code, and if successful, erase shared area
    if (blEraseTypedArea(BL_FLASH_KERNEL, BL_FLASH_KEY1, BL_FLASH_KEY2))
        if (blProgramTypedArea(__code_start, (const uint8_t*)(os + 1), os->size, BL_FLASH_KERNEL, BL_FLASH_KEY1, BL_FLASH_KEY2))
            (void)blExtApiEraseSharedArea(BL_FLASH_KEY1, BL_FLASH_KEY2);
}

static void blWriteMark(struct OsUpdateHdr *hdr, uint32_t mark)
{
    uint8_t dstVal = mark;

    (void)blExtApiProgramSharedArea(&hdr->marker, &dstVal, sizeof(hdr->marker), BL_FLASH_KEY1, BL_FLASH_KEY2);
}

static void blUpdateMark(uint32_t old, uint32_t new)
{
    struct OsUpdateHdr *hdr = (struct OsUpdateHdr *)__shared_start;

    if (hdr->marker != old)
        return;

    blWriteMark(hdr, new);
}

static uint32_t blVerifyOsImage(const uint8_t *addr, struct OsUpdateHdr **start, uint32_t *size)
{
    const uint32_t *rsaKey, *osSigHash, *osSigPubkey, *ourHash, *rsaResult, *expectedHash = NULL;
    struct OsUpdateHdr *hdr = (struct OsUpdateHdr*)addr;
    struct OsUpdateHdr cpy;
    uint32_t i, numRsaKeys = 0, rsaStateVar1, rsaStateVar2, rsaStep = 0;
    const uint8_t *updateBinaryData;
    bool isValid = false;
    struct Sha2state sha;
    struct RsaState rsa;
    uint32_t ret = OS_UPDT_HDR_CHECK_FAILED;
    const uint32_t overhead = sizeof(*hdr) + 2 * RSA_WORDS;

    // header does not fit or is not aligned
    if (addr < __shared_start || addr > (__shared_end - overhead) || ((uintptr_t)addr & 3))
        return OS_UPDT_HDR_CHECK_FAILED;

    // image does not fit
    if (hdr->size > (__shared_end - addr - overhead))
        return OS_UPDT_HDR_CHECK_FAILED;

    // OS magic does not match
    if (memcmp(hdr->magic, mOsUpdateMagic, sizeof(hdr->magic)) != 0)
        return OS_UPDT_HDR_CHECK_FAILED;

    // we don't allow shortcuts on success path, but we want to fail quickly
    if (hdr->marker == OS_UPDT_MARKER_INVALID)
        return OS_UPDT_HDR_MARKER_INVALID;

    // download did not finish
    if (hdr->marker == OS_UPDT_MARKER_INPROGRESS)
        return OS_UPDT_HDR_MARKER_INVALID;

    //get pointers
    updateBinaryData = (const uint8_t*)(hdr + 1);
    osSigHash = (const uint32_t*)(updateBinaryData + hdr->size);
    osSigPubkey = osSigHash + RSA_WORDS;

    //make sure the pub key is known
    for (i = 0, rsaKey = blExtApiGetRsaKeyInfo(&numRsaKeys); i < numRsaKeys; i++, rsaKey += RSA_WORDS) {
        if (memcmp(rsaKey, osSigPubkey, RSA_BYTES) == 0)
            break;
    }

    if (i == numRsaKeys) {
        ret = OS_UPDT_UNKNOWN_PUBKEY;
        //signed with an unknown key -> fail
        goto fail;
    }

    //decode sig using pubkey
    do {
        rsaResult = rsaPubOpIterative(&rsa, osSigHash, osSigPubkey, &rsaStateVar1, &rsaStateVar2, &rsaStep);
    } while (rsaStep);

    if (!rsaResult) {
        //decode fails -> invalid sig
        ret = OS_UPDT_INVALID_SIGNATURE;
        goto fail;
    }

    //verify padding
    expectedHash = blExtApiSigPaddingVerify(rsaResult);

    if (!expectedHash) {
        //padding check fails -> invalid sig
        ret = OS_UPDT_INVALID_SIGNATURE_HASH;
        goto fail;
    }

    //hash the update
    sha2init(&sha);

    memcpy(&cpy, hdr, sizeof(cpy));
    cpy.marker = OS_UPDT_MARKER_INPROGRESS;
    sha2processBytes(&sha, &cpy, sizeof(cpy));
    sha2processBytes(&sha, (uint8_t*)(hdr + 1), hdr->size);
    ourHash = sha2finish(&sha);

    //verify hash match
    if (memcmp(expectedHash, ourHash, SHA2_HASH_SIZE) != 0) {
        //hash does not match -> data tampered with
        ret = OS_UPDT_INVALID_SIGNATURE_HASH; // same error; do not disclose nature of hash problem
        goto fail;
    }

    //it is valid
    isValid = true;
    ret = OS_UPDT_SUCCESS;
    if (start)
        *start = hdr;
    if (size)
        *size = hdr->size;

fail:
    //mark it appropriately
    blWriteMark(hdr, isValid ? OS_UPDT_MARKER_VERIFIED : OS_UPDT_MARKER_INVALID);
    return ret;
}

static inline bool blUpdateVerify()
{
    return blVerifyOsImage(__shared_start, NULL, NULL) == OS_UPDT_SUCCESS;
}

static uint8_t blLoaderRxByte()
{
    return blSpiTxRxByte(0);
}

static void blLoaderTxByte(uint32_t val)
{
    blSpiTxRxByte(val);
}

static void blLoaderTxBytes(const void *data, uint32_t len)
{
    const uint8_t *buf = (const uint8_t*)data;

    blLoaderTxByte(len - 1);
    while (len--)
        blLoaderTxByte(*buf++);
}

static bool blLoaderSendSyncOut()
{
    return blSpiTxRxByte(BL_SYNC_OUT) == BL_SYNC_IN;
}

static bool blLoaderSendAck(bool ack)
{
    blLoaderRxByte();
    blLoaderTxByte(ack ? BL_ACK : BL_NAK);
    return blLoaderRxByte() == BL_ACK;
}

static uint32_t blLoaderRxCmd()
{
    uint8_t cmd = blLoaderRxByte();
    uint8_t cmdNot = blSpiTxRxByte(BL_ACK);
    return (cmd ^ cmdNot) == 0xFF ? cmd : BL_ERROR;
}

static void blLoader(bool force)
{
    bool seenErase = false;
    uint32_t nextAddr = 0;
    uint32_t expectedSize = 0;

    blSetup();

    //if int pin is not low, do not bother any further
    if (blHostActive() || force) {

        blConfigIo();

        //if we saw a sync, do the bootloader thing
        if (blSyncWait(BL_SYNC_IN)) {
            static const uint8_t supportedCmds[] = {BL_CMD_GET, BL_CMD_READ_MEM, BL_CMD_WRITE_MEM, BL_CMD_ERASE, BL_CMD_GET_SIZES, BL_CMD_UPDATE_FINISHED};
            uint32_t allSizes[] = {__builtin_bswap32(__code_end - __code_start), __builtin_bswap32(__shared_end - __shared_start), __builtin_bswap32(__eedata_end - __eedata_start)};
            bool ack = true;  //we ack the sync

            ack = blLoaderSendSyncOut();

            //loop forever listening to commands
            while (1) {
                uint32_t sync, cmd, addr = 0, len, checksum = 0, i;
                uint8_t data[256];

                //send ack or NAK for last thing
                if (!blLoaderSendAck(ack))
                    goto out;

                while ((sync = blLoaderRxByte()) != BL_SYNC_IN);
                cmd = blLoaderRxCmd();

                ack = false;
                if (sync == BL_SYNC_IN && cmd != BL_ERROR)
                switch (cmd) {
                case BL_CMD_GET:

                    //ACK the command
                    (void)blLoaderSendAck(true);

                    blLoaderTxBytes(supportedCmds, sizeof(supportedCmds));
                    ack = true;
                    break;

                case BL_CMD_READ_MEM:
                    if (!seenErase)  //no reading till we erase the shared area (this way we do not leak encrypted apps' plaintexts)
                        break;

                    //ACK the command
                    (void)blLoaderSendAck(true);

                    //get address
                    for (i = 0; i < 4; i++) {
                        uint32_t byte = blLoaderRxByte();
                        checksum ^= byte;
                        addr = (addr << 8) + byte;
                    }

                    //reject addresses outside of our fake area or on invalid checksum
                    if (blLoaderRxByte() != checksum || addr < BL_SHARED_AREA_FAKE_ADDR || addr - BL_SHARED_AREA_FAKE_ADDR > __shared_end - __shared_start)
                       break;

                    //ack the address
                    (void)blLoaderSendAck(true);

                    //get the length
                    len = blLoaderRxByte();

                    //reject invalid checksum
                    if (blLoaderRxByte() != (uint8_t)~len || addr + len - BL_SHARED_AREA_FAKE_ADDR > __shared_end - __shared_start)
                       break;

                    len++;

                    //reject reads past the end of the shared area
                    if (addr + len - BL_SHARED_AREA_FAKE_ADDR > __shared_end - __shared_start)
                       break;

                    //ack the length
                    (void)blLoaderSendAck(true);

                    //read the data & send it
                    blLoaderTxBytes(__shared_start + addr - BL_SHARED_AREA_FAKE_ADDR, len);
                    ack = true;
                    break;

                case BL_CMD_WRITE_MEM:
                    if (!seenErase)  //no writing till we erase the shared area (this way we do not purposefully modify encrypted apps' plaintexts in a nefarious fashion)
                        break;

                    //ACK the command
                    (void)blLoaderSendAck(true);

                    //get address
                    for (i = 0; i < 4; i++) {
                        uint32_t byte = blLoaderRxByte();
                        checksum ^= byte;
                        addr = (addr << 8) + byte;
                    }

                    //reject addresses outside of our fake area or on invalid checksum
                    if (blLoaderRxByte() != checksum ||
                        addr < BL_SHARED_AREA_FAKE_ADDR ||
                        addr - BL_SHARED_AREA_FAKE_ADDR > __shared_end - __shared_start)
                        break;

                    addr -= BL_SHARED_AREA_FAKE_ADDR;
                    if (addr != nextAddr)
                        break;

                    //ack the address
                    (void)blLoaderSendAck(true);

                    //get the length
                    checksum = len = blLoaderRxByte();
                    len++;

                    //get bytes
                    for (i = 0; i < len; i++) {
                        uint32_t byte = blLoaderRxByte();
                        checksum ^= byte;
                        data[i] = byte;
                    }

                    //reject writes that takes out outside fo shared area or invalid checksums
                    if (blLoaderRxByte() != checksum || addr + len > __shared_end - __shared_start)
                       break;

                    // OBSOLETE: superseded by sequential contiguous write requirement
                    //if (addr && addr < sizeof(struct OsUpdateHdr))
                    //    break;

                    //a write starting at zero must be big enough to contain a full OS update header
                    if (!addr) {
                        const struct OsUpdateHdr *hdr = (const struct OsUpdateHdr*)data;

                        //verify it is at least as big as the header
                        if (len < sizeof(struct OsUpdateHdr))
                            break;

                        //check for magic
                        for (i = 0; i < sizeof(hdr->magic) && hdr->magic[i] == mOsUpdateMagic[i]; i++);

                        //verify magic check passed & marker is properly set to inprogress
                        if (i != sizeof(hdr->magic) || hdr->marker != OS_UPDT_MARKER_INPROGRESS)
                            break;
                        expectedSize = sizeof(*hdr) + hdr->size + 2 * RSA_BYTES;
                    }
                    if (addr + len > expectedSize)
                        break;

                    //do it
                    ack = blExtApiProgramSharedArea(__shared_start + addr, data, len, BL_FLASH_KEY1, BL_FLASH_KEY2);
                    blResetRxData();
                    nextAddr += len;
                    break;

                case BL_CMD_ERASE:

                    //ACK the command
                    (void)blLoaderSendAck(true);

                    //get address
                    for (i = 0; i < 2; i++) {
                        uint32_t byte = blLoaderRxByte();
                        checksum ^= byte;
                        addr = (addr << 8) + byte;
                    }

                    //reject addresses that are not our magic address or on invalid checksum
                    if (blLoaderRxByte() != checksum || addr != BL_SHARED_AREA_FAKE_ERASE_BLK)
                        break;

                    //do it
                    ack = blExtApiEraseSharedArea(BL_FLASH_KEY1, BL_FLASH_KEY2);
                    if (ack) {
                        seenErase = true;
                        nextAddr = 0;
                        expectedSize = 0;
                    }
                    blResetRxData();
                    break;

                case BL_CMD_GET_SIZES:

                    //ACK the command
                    (void)blLoaderSendAck(true);

                    blLoaderTxBytes(allSizes, sizeof(allSizes));
                    break;

                case BL_CMD_UPDATE_FINISHED:
                    blUpdateMark(OS_UPDT_MARKER_INPROGRESS, OS_UPDT_MARKER_DOWNLOADED);
                    ack = blUpdateVerify();
                    break;
                }
            }
        }
    }

out:
    blCleanup();
}

void blMain(uint32_t appBase)
{
    bool forceLoad = false;

    blLog("NanohubOS bootloader up @ %p\n", &blMain);

    //enter SPI loader if requested
    do {
        uint32_t res;
        struct OsUpdateHdr *os;

        blLoader(forceLoad);
        res = blVerifyOsUpdate(&os, NULL);
        if (res == OS_UPDT_SUCCESS)
            blApplyVerifiedUpdate(os);
        else if (res != OS_UPDT_HDR_CHECK_FAILED)
            blExtApiEraseSharedArea(BL_FLASH_KEY1, BL_FLASH_KEY2);

        forceLoad = true;
    } while (*(volatile uint32_t*)appBase == 0xFFFFFFFF);
}

const struct BlApiTable __attribute__((section(".blapi"))) __BL_API =
{
    .blGetVersion = &blExtApiGetVersion,
    .blReboot = &blExtApiReboot,
    .blGetSnum = &blExtApiGetSnum,
    .blProgramShared = &blExtApiProgramSharedArea,
    .blEraseShared = &blExtApiEraseSharedArea,
    .blProgramEe = &blExtApiProgramEe,
    .blGetPubKeysInfo = &blExtApiGetRsaKeyInfo,
    .blRsaPubOpIterative = &rsaPubOpIterative,
    .blSha2init = &sha2init,
    .blSha2processBytes = &sha2processBytes,
    .blSha2finish = &sha2finish,
    .blAesInitForEncr = &aesInitForEncr,
    .blAesInitForDecr = &aesInitForDecr,
    .blAesEncr = &aesEncr,
    .blAesDecr = &aesDecr,
    .blAesCbcInitForEncr = &aesCbcInitForEncr,
    .blAesCbcInitForDecr = &aesCbcInitForDecr,
    .blAesCbcEncr = &aesCbcEncr,
    .blAesCbcDecr = &aesCbcDecr,
    .blSigPaddingVerify = &blExtApiSigPaddingVerify,
    .blVerifyOsUpdate = &blExtApiVerifyOsUpdate,
};
