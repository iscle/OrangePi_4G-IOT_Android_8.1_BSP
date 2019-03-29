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

#ifndef _MESSAGE_BUF_H_
#define _MESSAGE_BUF_H_

#include <endian.h>
#include <cstring>

namespace android {

namespace nanohub {

/*
 * Marshaling helper;
 * deals with alignment and endianness.
 * Assumption is:
 * read*()  parse buffer from device in LE format;
 *          return host endianness, aligned data
 * write*() primitives take host endinnness, aligned data,
 *          generate buffer to be passed to device in LE format
 *
 * Primitives do minimal error checking, only to ensure buffer read/write
 * safety. Caller is responsible for making sure correct amount of data
 * has been processed.
 */
class MessageBuf {
    char *data;
    size_t size;
    size_t pos;
    bool readOnly;
public:
    MessageBuf(char *buf, size_t bufSize) {
        size = bufSize;
        pos = 0;
        data = buf;
        readOnly = false;
    }
    MessageBuf(const char *buf, size_t bufSize) {
        size = bufSize;
        pos = 0;
        data = const_cast<char *>(buf);
        readOnly = true;
    }
    void reset() { pos = 0; }
    const char *getData() const { return data; }
    size_t getSize() const { return size; }
    size_t getPos() const { return pos; }
    size_t getRoom() const { return size - pos; }
    uint8_t readU8() {
        if (pos == size) {
            return 0;
        }
        return data[pos++];
    }
    void writeU8(uint8_t val) {
        if (pos == size || readOnly)
            return;
        data[pos++] = val;
    }
    uint16_t readU16() {
        if (pos > (size - sizeof(uint16_t))) {
            return 0;
        }
        uint16_t val;
        memcpy(&val, &data[pos], sizeof(val));
        pos += sizeof(val);
        return le16toh(val);
    }
    void writeU16(uint16_t val) {
        if (pos > (size - sizeof(uint16_t)) || readOnly) {
            return;
        }
        uint16_t tmp = htole16(val);
        memcpy(&data[pos], &tmp, sizeof(tmp));
        pos += sizeof(tmp);
    }
    uint32_t readU32() {
        if (pos > (size - sizeof(uint32_t))) {
            return 0;
        }
        uint32_t val;
        memcpy(&val, &data[pos], sizeof(val));
        pos += sizeof(val);
        return le32toh(val);
    }
    void writeU32(uint32_t val) {
        if (pos > (size - sizeof(uint32_t)) || readOnly) {
            return;
        }
        uint32_t tmp = htole32(val);
        memcpy(&data[pos], &tmp, sizeof(tmp));
        pos += sizeof(tmp);
    }
    uint64_t readU64() {
        if (pos > (size - sizeof(uint64_t))) {
            return 0;
        }
        uint64_t val;
        memcpy(&val, &data[pos], sizeof(val));
        pos += sizeof(val);
        return le32toh(val);
    }
    void writeU64(uint64_t val) {
        if (pos > (size - sizeof(uint64_t)) || readOnly) {
            return;
        }
        uint64_t tmp = htole64(val);
        memcpy(&data[pos], &tmp, sizeof(tmp));
        pos += sizeof(tmp);
    }
    const void *readRaw(size_t bufSize) {
        if (pos > (size - bufSize)) {
            return nullptr;
        }
        const void *buf = &data[pos];
        pos += bufSize;
        return buf;
    }
    void writeRaw(const void *buf, size_t bufSize) {
        if (pos > (size - bufSize) || readOnly) {
            return;
        }
        memcpy(&data[pos], buf, bufSize);
        pos += bufSize;
    }
};

}; // namespace nanohub

}; // namespace android

#endif

