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

#include <stdbool.h>
#include <string.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <inttypes.h>

#include <nanohub/aes.h>
#include <nanohub/sha2.h>
#include <nanohub/nanohub.h>
#include <nanohub/nanoapp.h>

static FILE* urandom = NULL;

static void cleanup(void)
{
    if (urandom)
        fclose(urandom);
}

static void rand_bytes(void *dst, uint32_t len)
{
    if (!urandom) {
        urandom = fopen("/dev/urandom", "rb");
        if (!urandom) {
            fprintf(stderr, "Failed to open /dev/urandom. Cannot procceed!\n");
            exit(2);
        }

        //it might not matter, but we still like to try to cleanup after ourselves
        (void)atexit(cleanup);
    }

    if (len != fread(dst, 1, len, urandom)) {
        fprintf(stderr, "Failed to read /dev/urandom. Cannot procceed!\n");
        exit(2);
    }
}

static int handleEncrypt(uint8_t **pbuf, uint32_t bufUsed, FILE *out, uint64_t keyId, uint32_t *key)
{
    uint32_t i;
    struct AesCbcContext ctx;
    struct ImageHeader *image;
    uint32_t *data;
    struct Sha2state shaState;
    bool err = false;
    struct AppSecEncrHdr encr;
    uint32_t padLen = 0;
    uint8_t *buf = *pbuf;

    encr.keyID = keyId;

//FIXME: compatibility: all the devices has google secret key with id 1, so we
//       can't simply change and enforce new key naming policy;
//       first, key upload mechanism shall start working, and then we can have
//       all the policies we want; for now, disable enforcement

//        if (encr.keyID <= 0xFFFF)
//            encr.keyID = AES_KEY_ID(encr.keyID);

    fprintf(stderr, "Using Key ID: %016" PRIX64 "\n", encr.keyID);
    rand_bytes(encr.IV, sizeof(encr.IV));
    printHash(stderr, "Using IV", encr.IV, AES_BLOCK_WORDS);

    if (bufUsed <= sizeof(*image)) {
        fprintf(stderr, "Input file is too small\n");
        return 2;
    }

    encr.dataLen = bufUsed;

    if (((bufUsed - sizeof(*image)) % AES_BLOCK_SIZE) != 0)
        padLen = AES_BLOCK_SIZE - ((bufUsed - sizeof(*image)) % AES_BLOCK_SIZE);

    if (padLen) {
        reallocOrDie(buf, bufUsed + padLen);
        rand_bytes(buf + bufUsed, padLen);
        bufUsed += padLen;
        fprintf(stderr, "Padded to %" PRIu32 " bytes\n", bufUsed);
        *pbuf = buf;
    }

    image = (struct ImageHeader *)buf;

    if (bufUsed >= sizeof(*image) && image->aosp.magic == NANOAPP_AOSP_MAGIC &&
        image->aosp.header_version == 1 && image->layout.magic == GOOGLE_LAYOUT_MAGIC) {
        fprintf(stderr, "Found AOSP header\n");
    } else {
        fprintf(stderr, "Unknown binary format\n");
        return 2;
    }

    if ((image->aosp.flags & NANOAPP_SIGNED_FLAG) != 0) {
        fprintf(stderr, "data is marked as signed; encryption is not possible for signed data\n");
        return 2;
    }
    if ((image->aosp.flags & NANOAPP_ENCRYPTED_FLAG) != 0) {
        fprintf(stderr, "data is marked as encrypted; encryption is not possible for encrypted data\n");
        return 2;
    }

    image->aosp.flags |= NANOAPP_ENCRYPTED_FLAG;
    fwrite(image, sizeof(*image), 1, out);
    data = (uint32_t *)(image + 1);
    fprintf(stderr, "orig len: %" PRIu32 " bytes\n", encr.dataLen);
    bufUsed -= sizeof(*image);
    encr.dataLen -= sizeof(*image);
    fwrite(&encr, sizeof(encr), 1, out);
    sha2init(&shaState);

    //encrypt and emit data
    aesCbcInitForEncr(&ctx, key, encr.IV);
    uint32_t outBuf[AES_BLOCK_WORDS];
    for (i = 0; i < bufUsed/sizeof(uint32_t); i += AES_BLOCK_WORDS) {
        aesCbcEncr(&ctx, data + i, outBuf);
        int32_t sz = encr.dataLen - (i * sizeof(uint32_t));
        sz = sz > AES_BLOCK_SIZE ? AES_BLOCK_SIZE : sz;
        if (sz > 0) {
            sha2processBytes(&shaState, data + i, sz);
            fwrite(outBuf, AES_BLOCK_SIZE, 1, out);
        }
    }
    const uint32_t *hash = sha2finish(&shaState);

    printHash(stderr, "HASH", hash, SHA2_HASH_WORDS);

    // finally, encrypt and output SHA2 hash
    aesCbcEncr(&ctx, hash, outBuf);
    fwrite(outBuf, AES_BLOCK_SIZE, 1, out);
    aesCbcEncr(&ctx, hash + AES_BLOCK_WORDS, outBuf);
    err = fwrite(outBuf, AES_BLOCK_SIZE, 1, out) != 1;

    return err ? 2 : 0;
}

static int handleDecrypt(uint8_t **pbuf, uint32_t bufUsed, FILE *out, uint32_t *key)
{
    struct AesCbcContext ctx;
    struct ImageHeader *image;
    struct Sha2state shaState;
    struct AppSecEncrHdr *encr;
    uint32_t *data;
    bool err = false;
    uint32_t fileHash[((SHA2_HASH_WORDS + AES_BLOCK_WORDS - 1) / AES_BLOCK_WORDS) * AES_BLOCK_WORDS], fileHashSz;
    uint32_t outBuf[AES_BLOCK_WORDS];
    uint32_t i;
    uint8_t *buf = *pbuf;

    //parse header
    image = (struct ImageHeader*)buf;
    if (bufUsed >= (sizeof(*image) + sizeof(*encr)) &&
        image->aosp.header_version == 1 && image->aosp.magic == NANOAPP_AOSP_MAGIC &&
        image->layout.magic == GOOGLE_LAYOUT_MAGIC) {
        fprintf(stderr, "Found AOSP header\n");
        if (!(image->aosp.flags & NANOAPP_ENCRYPTED_FLAG)) {
            fprintf(stderr, "data is not marked as encrypted; can't decrypt\n");
            return 2;
        }
        image->aosp.flags &= ~NANOAPP_ENCRYPTED_FLAG;
        data = (uint32_t *)(image + 1);
        encr = (struct AppSecEncrHdr *)data;
        data = (uint32_t *)(encr + 1);
        bufUsed -= sizeof(*image) + sizeof(*encr);
    } else {
        fprintf(stderr, "Unknown binary format\n");
        return 2;
    }

    if (encr->dataLen > bufUsed) {
        fprintf(stderr, "Claimed output size of %" PRIu32 "b invalid\n", encr->dataLen);
        return 2;
    }
    fprintf(stderr, "Original size %" PRIu32 "b (%" PRIu32 "b of padding present)\n",
            encr->dataLen, bufUsed - encr->dataLen);
    if (!encr->keyID)  {
        fprintf(stderr, "Input data has invalid key ID\n");
        return 2;
    }
    fprintf(stderr, "Using Key ID: %016" PRIX64 "\n", encr->keyID);
    printHash(stderr, "Using IV", encr->IV, AES_BLOCK_WORDS);

    fwrite(image, sizeof(*image), 1, out);
        //decrypt and emit data
    aesCbcInitForDecr(&ctx, key, encr->IV);
    fileHashSz = 0;
    sha2init(&shaState);
    for (i = 0; i < bufUsed / sizeof(uint32_t); i += AES_BLOCK_WORDS) {
        int32_t size = encr->dataLen - i * sizeof(uint32_t);
        aesCbcDecr(&ctx, data + i, outBuf);
        if (size > AES_BLOCK_SIZE)
            size = AES_BLOCK_SIZE;
        if (size > 0) {
            sha2processBytes(&shaState, outBuf, size);
            err = fwrite(outBuf, size, 1, out) != 1;
        } else if (fileHashSz < sizeof(fileHash)) {
            memcpy(((uint8_t*)fileHash) + fileHashSz, outBuf, AES_BLOCK_SIZE);
            fileHashSz += AES_BLOCK_SIZE;
        } else {
            fprintf(stderr, "Too much input data\n");
            return 2;
        }
    }
    const uint32_t *calcHash = sha2finish(&shaState);
    printHash(stderr, "HASH [calc]", calcHash, SHA2_HASH_WORDS);
    printHash(stderr, "HASH [file]", fileHash, SHA2_HASH_WORDS);

    bool verify = memcmp(fileHash, calcHash, SHA2_HASH_SIZE) == 0;
    fprintf(stderr, "hash verification: %s\n", verify ? "passed" : "failed");
    if (!verify)
        return 2;

    if (!err)
        fprintf(stderr, "Done\n");

    return err ? 2 : 0;
}

static void fatalUsage(const char *name, const char *msg, const char *arg)
{
    if (msg && arg)
        fprintf(stderr, "Error: %s: %s\n\n", msg, arg);
    else if (msg)
        fprintf(stderr, "Error: %s\n\n", msg);

    fprintf(stderr, "USAGE: %s [-e] [-d] [-i <key id>] [-k <key file>] <input file> [<output file>]\n"
                    "       -i : 64-bit hex number != 0\n"
                    "       -e : encrypt post-processed file\n"
                    "       -d : decrypt encrypted post-processed file\n"
                    "       -k : binary file (32 byte size) containing AES-256 secret key\n"
                    , name);
    exit(1);
}

int main(int argc, char **argv)
{
    uint32_t bufUsed = 0;
    uint8_t *buf = NULL;
    uint64_t keyId = 0;
    int ret = -1;
    uint32_t *u32Arg = NULL;
    uint64_t *u64Arg = NULL;
    const char **strArg = NULL;
    const char *appName = argv[0];
    const char *posArg[2] = { NULL };
    uint32_t posArgCnt = 0;
    FILE *out = NULL;
    const char *prev = NULL;
    bool decrypt = false;
    bool encrypt = false;
    const char *keyFile = NULL;
    int multi = 0;
    uint32_t key[AES_KEY_WORDS];

    for (int i = 1; i < argc; i++) {
        char *end = NULL;
        if (argv[i][0] == '-') {
            prev = argv[i];
            if (!strcmp(argv[i], "-d"))
                decrypt = true;
            else if (!strcmp(argv[i], "-e"))
                encrypt = true;
            else if (!strcmp(argv[i], "-k"))
                strArg = &keyFile;
            else if (!strcmp(argv[i], "-i"))
                u64Arg = &keyId;
            else
                fatalUsage(appName, "unknown argument", argv[i]);
        } else {
            if (u64Arg) {
                uint64_t tmp = strtoull(argv[i], &end, 16);
                if (*end == '\0')
                    *u64Arg = tmp;
                u64Arg = NULL;
            } else if (u32Arg) {
                uint32_t tmp = strtoul(argv[i], &end, 16);
                if (*end == '\0')
                    *u32Arg = tmp;
                u32Arg = NULL;
            } else if (strArg) {
                    *strArg = argv[i];
                strArg = NULL;
            } else {
                if (posArgCnt < 2)
                    posArg[posArgCnt++] = argv[i];
                else
                    fatalUsage(appName, "too many positional arguments", argv[i]);
            }
            prev = 0;
        }
    }
    if (prev)
        fatalUsage(appName, "missing argument after", prev);

    if (!posArgCnt)
        fatalUsage(appName, "missing input file name", NULL);

    if (encrypt)
        multi++;
    if (decrypt)
        multi++;

    if (multi != 1)
        fatalUsage(appName, "select either -d or -e", NULL);

    if (!keyFile)
        fatalUsage(appName, "no key file given", NULL);

    if (encrypt && !keyId)
        fatalUsage(appName, "Non-zero Key ID must be given to encrypt data", NULL);

    //read key
    if (!readFile(key, sizeof(key), keyFile))
        fatalUsage(appName, "Key file does not exist or has incorrect size", keyFile);

    buf = loadFile(posArg[0], &bufUsed);
    fprintf(stderr, "Read %" PRIu32 " bytes\n", bufUsed);

    if (!posArg[1])
        out = stdout;
    else
        out = fopen(posArg[1], "w");
    if (!out)
        fatalUsage(appName, "failed to create/open output file", posArg[1]);

    if (encrypt)
        ret = handleEncrypt(&buf, bufUsed, out, keyId, key);
    else if (decrypt)
        ret = handleDecrypt(&buf, bufUsed, out, key);

    free(buf);
    fclose(out);
    return ret;
}
