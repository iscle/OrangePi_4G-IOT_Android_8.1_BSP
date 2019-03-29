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

#include <nanohub/nanohub.h>
#include <nanohub/nanoapp.h>
#include <nanohub/sha2.h>
#include <nanohub/rsa.h>

static FILE* urandom = NULL;

#if defined(__APPLE__) || defined(_WIN32)
inline uint32_t bswap32 (uint32_t x) {
    uint32_t out = 0;
    for (int i=0; i < 4; ++i, x >>= 8)
        out = (out << 8) | (x & 0xFF);
    return out;
}

#define htobe32(x)  bswap32((x))
#define htole32(x)  ((uint32_t)(x))
#define be32toh(x)  bswap32((x))
#define le32toh(x)  ((uint32_t)(x))
#else
#include <endian.h>
#endif

//read exactly one hex-encoded byte from a file, skipping all the fluff
static int getHexEncodedByte(uint8_t *buf, uint32_t *ppos, uint32_t size)
{
    int c, i;
    uint32_t pos = *ppos;
    uint8_t val = 0;

    //for first byte
    for (i = 0; i < 2; i++) {
        val <<= 4;
        while(1) {
            if (pos == size)
                return -1;
            c = buf[pos++];
            *ppos = pos;

            if (c >= '0' && c <= '9')
                val += c - '0';
            else if (c >= 'a' && c <= 'f')
                val += c + 10 - 'a';
            else if (c >= 'A' && c <= 'F')
                val += c + 10 - 'A';
            else if (i)                         //disallow everything between first and second nibble
                return -1;
            else if (c > 'f' && c <= 'z')	//disallow nonalpha data
                return -1;
            else if (c > 'F' && c <= 'Z')	//disallow nonalpha data
                return -1;
            else
                continue;
            break;
        }
    }

    return val;
}

//provide a random number for which the following property is true ((ret & 0xFF000000) && (ret & 0xFF0000) && (ret & 0xFF00) && (ret & 0xFF))
static uint32_t rand32_no_zero_bytes(void)
{
    uint32_t i, v;
    uint8_t byte;

    if (!urandom) {
        urandom = fopen("/dev/urandom", "rb");
        if (!urandom) {
            fprintf(stderr, "Failed to open /dev/urandom. Cannot procceed!\n");
            exit(-2);
        }
    }

    for (v = 0, i = 0; i < 4; i++) {
        do {
            if (!fread(&byte, 1, 1, urandom)) {
                fprintf(stderr, "Failed to read /dev/urandom. Cannot procceed!\n");
                exit(-3);
            }
        } while (!byte);

        v = (v << 8) | byte;
    }

    return v;
}

static void cleanup(void)
{
    if (urandom)
        fclose(urandom);
}

struct RsaData {
    uint32_t num[RSA_LIMBS];
    uint32_t exponent[RSA_LIMBS];
    uint32_t modulus[RSA_LIMBS];
    struct RsaState state;
};

static bool validateSignature(uint8_t *sigPack, struct RsaData *rsa, bool verbose, uint32_t *refHash, bool preset)
{
    int i;
    const uint32_t *rsaResult;
    const uint32_t *le32SigPack = (const uint32_t*)sigPack;
    //convert to native uint32_t; ignore possible alignment issues
    for (i = 0; i < RSA_LIMBS; i++)
        rsa->num[i] = le32toh(le32SigPack[i]);
    //update the user
    if (verbose)
        printHashRev(stderr, "RSA cyphertext", rsa->num, RSA_LIMBS);
    if (!preset)
        memcpy(rsa->modulus, sigPack + RSA_BYTES, RSA_BYTES);

    //do rsa op
    rsaResult = rsaPubOp(&rsa->state, rsa->num, rsa->modulus);

    //update the user
    if (verbose)
        printHashRev(stderr, "RSA plaintext", rsaResult, RSA_LIMBS);

    //verify padding is appropriate and valid
    if ((rsaResult[RSA_LIMBS - 1] & 0xffff0000) != 0x00020000) {
        fprintf(stderr, "Padding header is invalid\n");
        return false;
    }

    //verify first two bytes of padding
    if (!(rsaResult[RSA_LIMBS - 1] & 0xff00) || !(rsaResult[RSA_LIMBS - 1] & 0xff)) {
        fprintf(stderr, "Padding bytes 0..1 are invalid\n");
        return false;
    }

    //verify last 3 bytes of padding and the zero terminator
    if (!(rsaResult[8] & 0xff000000) || !(rsaResult[8] & 0xff0000) || !(rsaResult[8] & 0xff00) || (rsaResult[8] & 0xff)) {
        fprintf(stderr, "Padding last bytes & terminator invalid\n");
        return false;
    }

    //verify middle padding bytes
    for (i = 9; i < RSA_LIMBS - 1; i++) {
        if (!(rsaResult[i] & 0xff000000) || !(rsaResult[i] & 0xff0000) || !(rsaResult[i] & 0xff00) || !(rsaResult[i] & 0xff)) {
            fprintf(stderr, "Padding word %d invalid\n", i);
            return false;
        }
    }
    if (verbose) {
        printHash(stderr, "Recovered hash ", rsaResult, SHA2_HASH_WORDS);
        printHash(stderr, "Calculated hash", refHash, SHA2_HASH_WORDS);
    }

    if (!preset) {
        // we're doing full verification, with key extracted from signature pack
        if (memcmp(rsaResult, refHash, SHA2_HASH_SIZE)) {
            fprintf(stderr, "hash mismatch\n");
            return false;
        }
    } else {
        // we just decode the signature with key passed as an argument
        // in this case we return recovered hash
        memcpy(refHash, rsaResult, SHA2_HASH_SIZE);
    }
    return true;
}

#define SIGNATURE_BLOCK_SIZE    (2 * RSA_BYTES)

static int handleConvertKey(uint8_t **pbuf, uint32_t bufUsed, FILE *out, struct RsaData *rsa)
{
    bool  haveNonzero = false;
    uint8_t *buf = *pbuf;
    int i, c;
    uint32_t pos = 0;
    int ret;

    for (i = 0; i < (int)RSA_BYTES; i++) {

        //get a byte, skipping all zeroes (openssl likes to prepend one at times)
        do {
            c = getHexEncodedByte(buf, &pos, bufUsed);
        } while (c == 0 && !haveNonzero);
        haveNonzero = true;
        if (c < 0) {
            fprintf(stderr, "Invalid text RSA input data\n");
            return 2;
        }

        buf[i] = c;
    }

    // change form BE to native; ignore alignment
    uint32_t *be32Buf = (uint32_t*)buf;
    for (i = 0; i < RSA_LIMBS; i++)
        rsa->num[RSA_LIMBS - i - 1] = be32toh(be32Buf[i]);

    //output in our binary format (little-endian)
    ret = fwrite(rsa->num, 1, RSA_BYTES, out) == RSA_BYTES ? 0 : 2;
    fprintf(stderr, "Conversion status: %d\n", ret);

    return ret;
}

static int handleVerify(uint8_t **pbuf, uint32_t bufUsed, struct RsaData *rsa, bool verbose, bool bareData)
{
    struct Sha2state shaState;
    uint8_t *buf = *pbuf;
    uint32_t masterPubKey[RSA_LIMBS];

    memcpy(masterPubKey, rsa->modulus, RSA_BYTES);
    if (!bareData) {
        struct ImageHeader *image = (struct ImageHeader *)buf;
        struct AppSecSignHdr *secHdr = (struct AppSecSignHdr *)&image[1];
        int block = 0;
        uint8_t *sigPack;
        bool trusted = false;
        bool lastTrusted = false;
        int sigData;

        if (bufUsed < (sizeof(*image) + sizeof(*secHdr))) {
            fprintf(stderr, "Invalid signature header: file is too short\n");
            return 2;
        }

        if (verbose)
            fprintf(stderr, "Original Data len=%" PRIu32 " b; file size=%" PRIu32 " b; diff=%" PRIu32 " b\n",
                    secHdr->appDataLen, bufUsed, bufUsed - secHdr->appDataLen);

        if (!(image->aosp.flags & NANOAPP_SIGNED_FLAG)) {
            fprintf(stderr, "image is not marked as signed, can not verify\n");
            return 2;
        }
        sigData = bufUsed - (secHdr->appDataLen + sizeof(*image) + sizeof(*secHdr));
        if (sigData <= 0 || (sigData % SIGNATURE_BLOCK_SIZE) != 0) {
            fprintf(stderr, "Invalid signature header: data size mismatch\n");
            return 2;
        }

        sha2init(&shaState);
        sha2processBytes(&shaState, buf, bufUsed - sigData);
        int nSig = sigData / SIGNATURE_BLOCK_SIZE;
        sigPack = buf + bufUsed - sigData;
        for (block = 0; block < nSig; ++block) {
            if (!validateSignature(sigPack, rsa, verbose, (uint32_t*)sha2finish(&shaState), false)) {
                fprintf(stderr, "Signature verification failed: signature block #%d\n", block);
                return 2;
            }
            if (memcmp(masterPubKey, rsa->modulus, RSA_BYTES) == 0) {
                fprintf(stderr, "Key in block %d is trusted\n", block);
                trusted = true;
                lastTrusted = true;
            } else {
                lastTrusted = false;
            }
            sha2init(&shaState);
            sha2processBytes(&shaState, sigPack+RSA_BYTES, RSA_BYTES);
            sigPack += SIGNATURE_BLOCK_SIZE;
        }
        if (trusted && !lastTrusted) {
            fprintf(stderr, "Trusted key is not the last in key sequence\n");
        }
        return trusted ? 0 : 2;
    } else {
        uint8_t *sigPack = buf + bufUsed - SIGNATURE_BLOCK_SIZE;
        uint32_t *hash;
        // can not do signature chains in bare mode
        if (bufUsed > SIGNATURE_BLOCK_SIZE) {
            sha2init(&shaState);
            sha2processBytes(&shaState, buf, bufUsed - SIGNATURE_BLOCK_SIZE);
            hash = (uint32_t*)sha2finish(&shaState);
            printHash(stderr, "File hash", hash, SHA2_HASH_WORDS);
            if (verbose)
                printHashRev(stderr, "File PubKey", (uint32_t *)(sigPack + RSA_BYTES), RSA_LIMBS);
            if (!validateSignature(sigPack, rsa, verbose, hash, false)) {
                fprintf(stderr, "Signature verification failed on raw data\n");
                return 2;
            }
            if (memcmp(masterPubKey, sigPack + RSA_BYTES, RSA_BYTES) == 0) {
                fprintf(stderr, "Signature verification passed and the key is trusted\n");
                return 0;
            } else {
                fprintf(stderr, "Signature verification passed but the key is not trusted\n");
                return 2;
            }
        } else {
            fprintf(stderr, "Not enough raw data to extract signature from\n");
            return 2;
        }
    }

    return 0;
}

static int handleSign(uint8_t **pbuf, uint32_t bufUsed, FILE *out, struct RsaData *rsa, bool verbose, bool bareData)
{
    struct Sha2state shaState;
    uint8_t *buf = *pbuf;
    uint32_t i;
    const uint32_t *hash;
    const uint32_t *rsaResult;
    int ret;

    if (!bareData) {
        struct ImageHeader *image = (struct ImageHeader *)buf;
        struct AppSecSignHdr *secHdr = (struct AppSecSignHdr *)&image[1];
        uint32_t grow = sizeof(*secHdr);
        if (!(image->aosp.flags & NANOAPP_SIGNED_FLAG)) {
            // this is the 1st signature in the chain; inject header, set flag
            buf = reallocOrDie(buf, bufUsed + grow);
            *pbuf = buf;
            image = (struct ImageHeader *)buf;
            secHdr = (struct AppSecSignHdr *)&image[1];

            fprintf(stderr, "Generating signature header\n");
            image->aosp.flags |= NANOAPP_SIGNED_FLAG;
            memmove((uint8_t*)&image[1] + grow, &image[1], bufUsed - sizeof(*image));
            secHdr->appDataLen = bufUsed - sizeof(*image);
            bufUsed += grow;
            fprintf(stderr, "Rehashing file\n");
            sha2init(&shaState);
            sha2processBytes(&shaState, buf, bufUsed);
        } else {
            int sigSz = bufUsed - sizeof(*image) - sizeof(*secHdr) - secHdr->appDataLen;
            int numSigs = sigSz / SIGNATURE_BLOCK_SIZE;
            if ((numSigs * (int)SIGNATURE_BLOCK_SIZE) != sigSz) {
                fprintf(stderr, "Invalid signature block(s) detected\n");
                return 2;
            } else {
                fprintf(stderr, "Found %d appended signature(s)\n", numSigs);
                // generating SHA256 of the last PubKey in chain
                fprintf(stderr, "Hashing last signature's PubKey\n");
                sha2init(&shaState);
                sha2processBytes(&shaState, buf + bufUsed- RSA_BYTES, RSA_BYTES);
            }
        }
    } else {
        fprintf(stderr, "Signing raw data\n");
        sha2init(&shaState);
        sha2processBytes(&shaState, buf, bufUsed);
    }

    //update the user on the progress
    hash = sha2finish(&shaState);
    if (verbose)
        printHash(stderr, "SHA2 hash", hash, SHA2_HASH_WORDS);

    memcpy(rsa->num, hash, SHA2_HASH_SIZE);

    i = SHA2_HASH_WORDS;
    //write padding
    rsa->num[i++] = rand32_no_zero_bytes() << 8; //low byte here must be zero as per padding spec
    for (;i < RSA_LIMBS - 1; i++)
        rsa->num[i] = rand32_no_zero_bytes();
    rsa->num[i] = (rand32_no_zero_bytes() >> 16) | 0x00020000; //as per padding spec

    //update the user
    if (verbose)
        printHashRev(stderr, "RSA plaintext", rsa->num, RSA_LIMBS);

    //do the RSA thing
    fprintf(stderr, "Retriculating splines...");
    rsaResult = rsaPrivOp(&rsa->state, rsa->num, rsa->exponent, rsa->modulus);
    fprintf(stderr, "DONE\n");

    //update the user
    if (verbose)
        printHashRev(stderr, "RSA cyphertext", rsaResult, RSA_LIMBS);

    // output in a format that our microcontroller will be able to digest easily & directly
    // (an array of bytes representing little-endian 32-bit words)
    fwrite(buf, 1, bufUsed, out);
    fwrite(rsaResult, 1, sizeof(uint32_t[RSA_LIMBS]), out);
    ret = (fwrite(rsa->modulus, 1, RSA_BYTES, out) == RSA_BYTES) ? 0 : 2;

    fprintf(stderr, "Status: %s (%d)\n", ret == 0 ? "success" : "failed", ret);
    return ret;

}

static void fatalUsage(const char *name, const char *msg, const char *arg)
{
    if (msg && arg)
        fprintf(stderr, "Error: %s: %s\n\n", msg, arg);
    else if (msg)
        fprintf(stderr, "Error: %s\n\n", msg);

    fprintf(stderr, "USAGE: %s [-v] [-e <pvt key>] [-m <pub key>] [-t] [-s] [-b] <input file> [<output file>]\n"
                    "       -v : be verbose\n"
                    "       -b : generate binary key from text file created by OpenSSL\n"
                    "       -s : sign post-processed file\n"
                    "       -t : verify signature of signed post-processed file\n"
                    "       -e : RSA binary private key\n"
                    "       -m : RSA binary public key\n"
                    "       -r : do not parse headers, do not generate headers (with -t, -s)\n"
                    , name);
    exit(1);
}

int main(int argc, char **argv)
{
    uint32_t bufUsed = 0;
    uint8_t *buf = NULL;
    int ret = -1;
    const char **strArg = NULL;
    const char *appName = argv[0];
    const char *posArg[2] = { NULL };
    uint32_t posArgCnt = 0;
    FILE *out = NULL;
    const char *prev = NULL;
    bool verbose = false;
    bool sign = false;
    bool verify = false;
    bool txt2bin = false;
    bool bareData = false;
    const char *keyPvtFile = NULL;
    const char *keyPubFile = NULL;
    int multi = 0;
    struct RsaData rsa;
    struct ImageHeader *image;

    //it might not matter, but we still like to try to cleanup after ourselves
    (void)atexit(cleanup);

    for (int i = 1; i < argc; i++) {
        if (argv[i][0] == '-') {
            prev = argv[i];
            if (!strcmp(argv[i], "-v"))
                verbose = true;
            else if (!strcmp(argv[i], "-s"))
                sign = true;
            else if (!strcmp(argv[i], "-t"))
                verify = true;
            else if (!strcmp(argv[i], "-b"))
                txt2bin = true;
            else if (!strcmp(argv[i], "-e"))
                strArg = &keyPvtFile;
            else if (!strcmp(argv[i], "-m"))
                strArg = &keyPubFile;
            else if (!strcmp(argv[i], "-r"))
                bareData = true;
            else
                fatalUsage(appName, "unknown argument", argv[i]);
        } else {
            if (strArg) {
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

    if (sign)
        multi++;
    if (verify)
        multi++;
    if (txt2bin)
        multi++;

    if (multi != 1)
        fatalUsage(appName, "select either -s, -t, or -b", NULL);

    memset(&rsa, 0, sizeof(rsa));

    if (sign && !(keyPvtFile && keyPubFile))
        fatalUsage(appName, "We need both PUB (-m) and PVT (-e) keys for signing", NULL);

    if (verify && (!keyPubFile || keyPvtFile))
        fatalUsage(appName, "We only need PUB (-m)  key for signature checking", NULL);

    if (keyPvtFile) {
        if (!readFile(rsa.exponent, sizeof(rsa.exponent), keyPvtFile))
            fatalUsage(appName, "Can't read PVT key from", keyPvtFile);
#ifdef DEBUG_KEYS
        else if (verbose)
            printHashRev(stderr, "RSA exponent", rsa.exponent, RSA_LIMBS);
#endif
    }

    if (keyPubFile) {
        if (!readFile(rsa.modulus, sizeof(rsa.modulus), keyPubFile))
            fatalUsage(appName, "Can't read PUB key from", keyPubFile);
        else if (verbose)
            printHashRev(stderr, "RSA modulus", rsa.modulus, RSA_LIMBS);
    }

    buf = loadFile(posArg[0], &bufUsed);
    fprintf(stderr, "Read %" PRIu32 " bytes\n", bufUsed);

    image = (struct ImageHeader *)buf;
    if (!bareData && !txt2bin) {
        if (bufUsed >= sizeof(*image) &&
            image->aosp.header_version == 1 &&
            image->aosp.magic == NANOAPP_AOSP_MAGIC &&
            image->layout.magic == GOOGLE_LAYOUT_MAGIC) {
            fprintf(stderr, "Found AOSP header\n");
        } else {
            fprintf(stderr, "Unknown binary format\n");
            return 2;
        }
    }

    if (!posArg[1])
        out = stdout;
    else
        out = fopen(posArg[1], "w");
    if (!out)
        fatalUsage(appName, "failed to create/open output file", posArg[1]);

    if (sign)
        ret = handleSign(&buf, bufUsed, out, &rsa, verbose, bareData);
    else if (verify)
        ret = handleVerify(&buf, bufUsed, &rsa, verbose, bareData);
    else if (txt2bin)
        ret = handleConvertKey(&buf, bufUsed, out, &rsa);

    free(buf);
    fclose(out);
    return ret;
}
