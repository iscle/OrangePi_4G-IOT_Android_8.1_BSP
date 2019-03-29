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

#include <inttypes.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include <nanohub/aes.h>
#include <nanohub/rsa.h>
#include <nanohub/sha2.h>

#include <appSec.h>
#include <bl.h>
#include <heap.h>
#include <seos.h>

#define APP_HDR_SIZE                (sizeof(struct ImageHeader))
#define APP_HDR_MAX_SIZE            (sizeof(struct ImageHeader) + sizeof(struct AppSecSignHdr) + sizeof(struct AppSecEncrHdr))
#define APP_DATA_CHUNK_SIZE         (AES_BLOCK_WORDS * sizeof(uint32_t))  //data blocks are this size
#define APP_SIG_SIZE                RSA_BYTES

// verify block is SHA placed in integral number of encryption blocks (for SHA256 and AES256 happens to be exactly 2 AES blocks)
#define APP_VERIFY_BLOCK_SIZE       ((SHA2_HASH_SIZE + AES_BLOCK_SIZE - 1) / AES_BLOCK_SIZE) * AES_BLOCK_SIZE

#define APP_SEC_SIG_ALIGN           APP_DATA_CHUNK_SIZE
#define APP_SEC_ENCR_ALIGN          APP_DATA_CHUNK_SIZE

#define STATE_INIT                  0 // nothing gotten yet
#define STATE_RXING_HEADERS         1 // variable size headers (min APP_HDR_SIZE, max APP_HDR_MAX_SIZE)
#define STATE_RXING_DATA            2 // each data block is AES_BLOCK_WORDS 32-bit words (for AES reasons)
#define STATE_RXING_SIG_HASH        3 // each is RSA_BYTES bytes
#define STATE_RXING_SIG_PUBKEY      4 // each is RSA_BYTES bytes
#define STATE_VERIFY                5 // decryption of ciphertext done; now decrypting and verifying the encrypted plaintext SHA2
#define STATE_DONE                  6 // all is finished and well
#define STATE_BAD                   7 // unrecoverable badness has happened. this will *NOT* fix itself. It is now ok to give up, start over, cry, or pray to your favourite deity for help
#define STATE_MAX                   8 // total number of states

//#define DEBUG_FSM

struct AppSecState {
    union { //we save some memory by reusing this space.
        struct {
            struct AesCbcContext cbc;
            struct Sha2state sha;
            struct Sha2state cbcSha;
        };
        struct {
            struct RsaState rsa;
            uint32_t rsaState1, rsaState2, rsaStep;
        };
    };
    uint32_t rsaTmp[RSA_WORDS];
    uint32_t lastHash[SHA2_HASH_WORDS];

    AppSecWriteCbk writeCbk;
    AppSecPubKeyFindCbk pubKeyFindCbk;
    AppSecGetAesKeyCbk aesKeyAccessCbk;

    union {
        union { //make the compiler work to make sure we have enough space
            uint8_t placeholderAppHdr[APP_HDR_MAX_SIZE];
            uint8_t placeholderDataChunk[APP_DATA_CHUNK_SIZE];
            uint8_t placeholderSigChunk[APP_SIG_SIZE];
            uint8_t placeholderAesKey[AES_KEY_WORDS * sizeof(uint32_t)];
        };
        uint8_t dataBytes[0]; //we actually use these two for access
        uint32_t dataWords[0];
    };

    uint32_t signedBytesIn;
    uint32_t encryptedBytesIn;
    uint32_t signedBytesOut;
    uint32_t encryptedBytesOut;

    uint16_t haveBytes;       //in dataBytes...
    uint16_t chunkSize;
    uint8_t curState;
    uint8_t needSig    :1;
    uint8_t haveSig    :1;
    uint8_t haveEncr   :1;
    uint8_t haveTrustedKey :1;
    uint8_t doingRsa   :1;
};

static void limitChunkSize(struct AppSecState *state)
{
    if (state->haveSig && state->chunkSize > state->signedBytesIn)
        state->chunkSize = state->signedBytesIn;
    if (state->haveEncr && state->chunkSize > state->encryptedBytesIn)
        state->chunkSize = state->signedBytesIn;
}

static void appSecSetCurState(struct AppSecState *state, uint32_t curState)
{
    const static uint16_t chunkSize[STATE_MAX] = {
        [STATE_RXING_HEADERS] = APP_HDR_SIZE,
        [STATE_RXING_DATA] = APP_DATA_CHUNK_SIZE,
        [STATE_VERIFY] = APP_VERIFY_BLOCK_SIZE,
        [STATE_RXING_SIG_HASH] = APP_SIG_SIZE,
        [STATE_RXING_SIG_PUBKEY] = APP_SIG_SIZE,
    };
    if (curState >= STATE_MAX)
        curState = STATE_BAD;
    if (curState != state->curState || curState == STATE_INIT) {
#ifdef DEBUG_FSM
        osLog(LOG_INFO, "%s: oldState=%" PRIu8
                        "; new state=%" PRIu32
                        "; old chunk size=%" PRIu16
                        "; new chunk size=%" PRIu16
                        "; have bytes=%" PRIu16
                        "\n",
              __func__, state->curState, curState,
              state->chunkSize, chunkSize[curState],
              state->haveBytes);
#endif
        state->curState = curState;
        state->chunkSize = chunkSize[curState];
    }
}

static inline uint32_t appSecGetCurState(const struct AppSecState *state)
{
    return state->curState;
}

//init/deinit
struct AppSecState *appSecInit(AppSecWriteCbk writeCbk, AppSecPubKeyFindCbk pubKeyFindCbk, AppSecGetAesKeyCbk aesKeyAccessCbk, bool mandateSigning)
{
    struct AppSecState *state = heapAlloc(sizeof(struct AppSecState));

    if (!state)
        return NULL;

    memset(state, 0, sizeof(struct AppSecState));

    state->writeCbk = writeCbk;
    state->pubKeyFindCbk = pubKeyFindCbk;
    state->aesKeyAccessCbk = aesKeyAccessCbk;
    appSecSetCurState(state, STATE_INIT);
    if (mandateSigning)
        state->needSig = 1;

    return state;
}

void appSecDeinit(struct AppSecState *state)
{
    heapFree(state);
}

//if needed, decrypt and hash incoming data
static AppSecErr appSecBlockRx(struct AppSecState *state)
{
    //if signatures are on, hash it
    if (state->haveSig) {

        //make sure we do not get too much data & account for the data we got
        if (state->haveBytes > state->signedBytesIn)
            return APP_SEC_TOO_MUCH_DATA;
        state->signedBytesIn -= state->haveBytes;

        //make sure we do not produce too much data (discard padding) & make sure we account for it
        if (state->signedBytesOut < state->haveBytes)
            state->haveBytes = state->signedBytesOut;
        state->signedBytesOut -= state->haveBytes;

        //hash the data
        BL.blSha2processBytes(&state->sha, state->dataBytes, state->haveBytes);
    }

    // decrypt if encryption is on
    if (state->haveEncr) {

        uint32_t *dataP = state->dataWords;
        uint32_t i, numBlocks = state->haveBytes / APP_DATA_CHUNK_SIZE;

        //we should not be called with partial encr blocks
        if (state->haveBytes % APP_DATA_CHUNK_SIZE)
            return APP_SEC_TOO_LITTLE_DATA;

        // make sure we do not get too much data & account for the data we got
        if (state->haveBytes > state->encryptedBytesIn)
            return APP_SEC_TOO_MUCH_DATA;
        state->encryptedBytesIn -= state->haveBytes;

        // decrypt
        for (i = 0; i < numBlocks; i++, dataP += AES_BLOCK_WORDS)
            BL.blAesCbcDecr(&state->cbc, dataP, dataP);

        // make sure we do not produce too much data (discard padding) & make sure we account for it
        if (state->encryptedBytesOut < state->haveBytes)
            state->haveBytes = state->encryptedBytesOut;
        state->encryptedBytesOut -= state->haveBytes;

        if (state->haveBytes)
            BL.blSha2processBytes(&state->cbcSha, state->dataBytes, state->haveBytes);
    }

    limitChunkSize(state);

    return APP_SEC_NO_ERROR;
}

static AppSecErr appSecProcessIncomingHdr(struct AppSecState *state, uint32_t *needBytesOut)
{
    struct ImageHeader *image;
    struct nano_app_binary_t *aosp;
    uint32_t flags;
    uint32_t needBytes;
    struct AppSecSignHdr *signHdr = NULL;
    struct AppSecEncrHdr *encrHdr = NULL;
    uint8_t *hdr = state->dataBytes;
    AppSecErr ret;

    image = (struct ImageHeader *)hdr; hdr += sizeof(*image);
    aosp = &image->aosp;
    flags = aosp->flags;
    if (aosp->header_version != 1 ||
        aosp->magic != NANOAPP_AOSP_MAGIC ||
        image->layout.version != 1 ||
        image->layout.magic != GOOGLE_LAYOUT_MAGIC)
        return APP_SEC_HEADER_ERROR;

    needBytes = sizeof(*image);
    if ((flags & NANOAPP_SIGNED_FLAG) != 0)
        needBytes += sizeof(*signHdr);
    if ((flags & NANOAPP_ENCRYPTED_FLAG) != 0)
        needBytes += sizeof(*encrHdr);

    *needBytesOut = needBytes;

    if (needBytes > state->haveBytes)
        return APP_SEC_NO_ERROR;

    *needBytesOut = 0;

    if ((flags & NANOAPP_SIGNED_FLAG) != 0) {
        signHdr = (struct AppSecSignHdr *)hdr; hdr += sizeof(*signHdr);
        osLog(LOG_INFO, "%s: signed size=%" PRIu32 "\n",
                        __func__, signHdr->appDataLen);
        if (!signHdr->appDataLen) {
            //no data bytes
            return APP_SEC_INVALID_DATA;
        }
        state->signedBytesIn = state->signedBytesOut = signHdr->appDataLen;
        state->haveSig = 1;
        BL.blSha2init(&state->sha);
        BL.blSha2processBytes(&state->sha, state->dataBytes, needBytes);
    }

    if ((flags & NANOAPP_ENCRYPTED_FLAG) != 0) {
        uint32_t k[AES_KEY_WORDS];

        encrHdr = (struct AppSecEncrHdr *)hdr; hdr += sizeof(*encrHdr);
        osLog(LOG_INFO, "%s: encrypted data size=%" PRIu32
                        "; key ID=%016" PRIX64 "\n",
                        __func__, encrHdr->dataLen, encrHdr->keyID);

        if (!encrHdr->dataLen || !encrHdr->keyID)
            return APP_SEC_INVALID_DATA;
        ret = state->aesKeyAccessCbk(encrHdr->keyID, k);
        if (ret != APP_SEC_NO_ERROR) {
            osLog(LOG_ERROR, "%s: Secret key not found\n", __func__);
            return ret;
        }

        BL.blAesCbcInitForDecr(&state->cbc, k, encrHdr->IV);
        BL.blSha2init(&state->cbcSha);
        state->encryptedBytesOut = encrHdr->dataLen;
        state->encryptedBytesIn = ((state->encryptedBytesOut + APP_SEC_ENCR_ALIGN - 1) / APP_SEC_ENCR_ALIGN) * APP_SEC_ENCR_ALIGN;
        state->haveEncr = 1;
        osLog(LOG_INFO, "%s: encrypted aligned data size=%" PRIu32 "\n",
                        __func__, state->encryptedBytesIn);

        if (state->haveSig) {
            state->signedBytesIn = state->signedBytesOut = signHdr->appDataLen - sizeof(*encrHdr);
            // at this point, signedBytesOut must equal encryptedBytesIn
            if (state->signedBytesOut != (state->encryptedBytesIn + SHA2_HASH_SIZE)) {
                osLog(LOG_ERROR, "%s: sig data size does not match encrypted data\n", __func__);
                return APP_SEC_INVALID_DATA;
            }
        }
    }

    //if we are in must-sign mode and no signature was provided, fail
    if (!state->haveSig && state->needSig) {
        osLog(LOG_ERROR, "%s: only signed images can be uploaded\n", __func__);
        return APP_SEC_SIG_VERIFY_FAIL;
    }

    // now, transform AOSP header to FW common header
    struct FwCommonHdr common = {
        .magic   = APP_HDR_MAGIC,
        .appId   = aosp->app_id,
        .fwVer   = APP_HDR_VER_CUR,
        .fwFlags = image->layout.flags,
        .appVer  = aosp->app_version,
        .payInfoType = image->layout.payload,
        .rfu = { 0xFF, 0xFF },
    };

    // check to see if this is special system types of payload
    switch(image->layout.payload) {
    case LAYOUT_APP:
        common.fwFlags = (common.fwFlags | FL_APP_HDR_APPLICATION) & ~FL_APP_HDR_INTERNAL;
        common.payInfoSize = sizeof(struct AppInfo);
        osLog(LOG_INFO, "App container found\n");
        break;
    case LAYOUT_KEY:
        common.fwFlags |= FL_APP_HDR_SECURE;
        common.payInfoSize = sizeof(struct KeyInfo);
        osLog(LOG_INFO, "Key container found\n");
        break;
    case LAYOUT_OS:
        common.payInfoSize = sizeof(struct OsUpdateHdr);
        osLog(LOG_INFO, "OS update container found\n");
        break;
    default:
        break;
    }

    memcpy(state->dataBytes, &common, sizeof(common));
    state->haveBytes = sizeof(common);

    //we're now in data-accepting state
    appSecSetCurState(state, STATE_RXING_DATA);

    return APP_SEC_NO_ERROR;
}

static AppSecErr appSecProcessIncomingData(struct AppSecState *state)
{
    //check for data-ending conditions
    if (state->haveSig && !state->signedBytesIn) {
        // we're all done with the signed portion of the data, now come the signatures
        appSecSetCurState(state, STATE_RXING_SIG_HASH);

        //collect the hash
        memcpy(state->lastHash, BL.blSha2finish(&state->sha), SHA2_HASH_SIZE);
    } else if (state->haveEncr && !state->encryptedBytesIn) {
        if (appSecGetCurState(state) == STATE_RXING_DATA) {
            //we're all done with encrypted plaintext
            state->encryptedBytesIn = sizeof(state->cbcSha);
            appSecSetCurState(state, STATE_VERIFY);
        }
    }

    //pass to caller
    return state->haveBytes ? state->writeCbk(state->dataBytes, state->haveBytes) : APP_SEC_NO_ERROR;
}

AppSecErr appSecDoSomeProcessing(struct AppSecState *state)
{
    const uint32_t *result;

    if (!state->doingRsa) {
        //shouldn't be calling us then...
        return APP_SEC_BAD;
    }

    result = BL.blRsaPubOpIterative(&state->rsa, state->rsaTmp, state->dataWords, &state->rsaState1, &state->rsaState2, &state->rsaStep);
    if (state->rsaStep)
        return APP_SEC_NEED_MORE_TIME;

    //we just finished the RSA-ing
    state->doingRsa = 0;

    //verify signature padding (and thus likely: correct decryption)
    result = BL.blSigPaddingVerify(result);
    if (!result)
        return APP_SEC_SIG_DECODE_FAIL;

    //check if hashes match
    if (memcmp(state->lastHash, result, SHA2_HASH_SIZE))
        return APP_SEC_SIG_VERIFY_FAIL;

    //hash the provided pubkey
    BL.blSha2init(&state->sha);
    BL.blSha2processBytes(&state->sha, state->dataBytes, APP_SIG_SIZE);
    memcpy(state->lastHash, BL.blSha2finish(&state->sha), SHA2_HASH_SIZE);
    appSecSetCurState(state, STATE_RXING_SIG_HASH);

    return APP_SEC_NO_ERROR;
}

static AppSecErr appSecProcessIncomingSigData(struct AppSecState *state)
{
    bool keyFound = false;

    //if we're RXing the hash, just stash it away and move on
    if (appSecGetCurState(state) == STATE_RXING_SIG_HASH) {
        state->haveTrustedKey = 0;
        memcpy(state->rsaTmp, state->dataWords, APP_SIG_SIZE);
        appSecSetCurState(state, STATE_RXING_SIG_PUBKEY);
        return APP_SEC_NO_ERROR;
    }

    // verify it is a known root
    state->pubKeyFindCbk(state->dataWords, &keyFound);
    state->haveTrustedKey = keyFound;

    //we now have the pubKey. decrypt over time
    state->doingRsa = 1;
    state->rsaStep = 0;
    return APP_SEC_NEED_MORE_TIME;
}

static AppSecErr appSecVerifyEncryptedData(struct AppSecState *state)
{
    const uint32_t *hash = BL.blSha2finish(&state->cbcSha);
    bool verified = memcmp(hash, state->dataBytes, SHA2_BLOCK_SIZE) == 0;

    osLog(LOG_INFO, "%s: decryption verification: %s\n", __func__, verified ? "passed" : "failed");

// TODO: fix verify logic
//    return verified ? APP_SEC_NO_ERROR : APP_SEC_VERIFY_FAILED;
    return APP_SEC_NO_ERROR;
}

AppSecErr appSecRxData(struct AppSecState *state, const void *dataP, uint32_t len, uint32_t *lenUnusedP)
{
    const uint8_t *data = (const uint8_t*)dataP;
    AppSecErr ret = APP_SEC_NO_ERROR;
    uint32_t needBytes;

    if (appSecGetCurState(state) == STATE_INIT)
        appSecSetCurState(state, STATE_RXING_HEADERS);

    while (len) {
        len--;
        state->dataBytes[state->haveBytes++] = *data++;
        if (state->haveBytes < state->chunkSize)
            continue;
        switch (appSecGetCurState(state)) {
        case STATE_RXING_HEADERS:
            // AOSP header is never encrypted; if it is signed, it will hash itself
            needBytes = 0;
            ret = appSecProcessIncomingHdr(state, &needBytes);
            if (ret != APP_SEC_NO_ERROR)
                goto out;
            if (needBytes > state->chunkSize) {
                state->chunkSize = needBytes;
                // get more data and try again
                continue;
            }
            // done with parsing header(s); we might have something to write to flash
            if (state->haveBytes) {
                osLog(LOG_INFO, "%s: save converted header [%" PRIu16 " bytes] to flash\n", __func__, state->haveBytes);
                ret = appSecProcessIncomingData(state);
                state->haveBytes = 0;
            }
            limitChunkSize(state);
            goto out;

        case STATE_RXING_DATA:
            ret = appSecBlockRx(state);
            if (ret != APP_SEC_NO_ERROR)
                goto out;

            ret = appSecProcessIncomingData(state);
            state->haveBytes = 0;
            if (ret != APP_SEC_NO_ERROR)
                goto out;
            break;

        case STATE_VERIFY:
            ret = appSecBlockRx(state);
            if (ret == APP_SEC_NO_ERROR)
                ret = appSecProcessIncomingData(state);
            if (ret == APP_SEC_NO_ERROR)
                ret = appSecVerifyEncryptedData(state);
            goto out;

        case STATE_RXING_SIG_HASH:
        case STATE_RXING_SIG_PUBKEY:
            //no need for calling appSecBlockRx() as sigs are not signed, and encryption cannot be done after signing
            ret = appSecProcessIncomingSigData(state);
            state->haveBytes = 0;
            goto out;

        default:
            appSecSetCurState(state, STATE_BAD);
            state->haveBytes = 0;
            len = 0;
            ret = APP_SEC_BAD;
            break;
        }
    }

out:
    *lenUnusedP = len;

    if (ret != APP_SEC_NO_ERROR && ret != APP_SEC_NEED_MORE_TIME) {
        osLog(LOG_ERROR, "%s: failed: state=%" PRIu32 "; err=%" PRIu32 "\n",
              __func__, appSecGetCurState(state), ret);
        appSecSetCurState(state,  STATE_BAD);
    }

    return ret;
}

AppSecErr appSecRxDataOver(struct AppSecState *state)
{
    AppSecErr ret;

    // Feed remaining data to data processor, if any
    if (state->haveBytes) {
        // if we are using encryption and/or signing, we are supposed to consume all data at this point.
        if (state->haveSig || state->haveEncr) {
            appSecSetCurState(state, STATE_BAD);
            return APP_SEC_TOO_LITTLE_DATA;
        }
        // Not in data rx stage when the incoming data ends? This is not good (if we had encr or sign we'd not be here)
        if (appSecGetCurState(state) != STATE_RXING_DATA) {
            appSecSetCurState(state, STATE_BAD);
            return APP_SEC_TOO_LITTLE_DATA;
        }
        // Feed the remaining data to the data processor
        ret = appSecProcessIncomingData(state);
        if (ret != APP_SEC_NO_ERROR) {
            appSecSetCurState(state, STATE_BAD);
            return ret;
        }
    } else {
        // we don't know in advance how many signature packs we shall receive,
        // so we evaluate every signature pack as if it is the last, but do not
        // return error if public key is not trusted; only here we make the final
        // determination
        if (state->haveSig) {
            // check the most recent key status
            if (!state->haveTrustedKey) {
                appSecSetCurState(state, STATE_BAD);
                return APP_SEC_SIG_ROOT_UNKNOWN;
            } else {
                appSecSetCurState(state, STATE_DONE);
            }
        }
    }

    //for unsigned/unencrypted case we have no way to judge length, so we assume it is over when we're told it is
    //this is potentially dangerous, but then again so is allowing unsigned uploads in general.
    if (!state->haveSig && !state->haveEncr && appSecGetCurState(state) == STATE_RXING_DATA)
        appSecSetCurState(state, STATE_DONE);

    //Check the state and return our verdict
    if(appSecGetCurState(state) == STATE_DONE)
        return APP_SEC_NO_ERROR;

    appSecSetCurState(state, STATE_BAD);
    return APP_SEC_TOO_LITTLE_DATA;
}
