/*
 * Copyright (C) 2017 The Android Open Source Project
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

#define LOG_TAG "NativeCursorWindow"

#include <jni.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <linux/ashmem.h>
#include <utils/Log.h>

struct Header {
    // Offset of the lowest unused byte in the window.
    uint32_t freeOffset;

    // Offset of the first row slot chunk.
    uint32_t firstChunkOffset;

    uint32_t numRows;
    uint32_t numColumns;
};

struct RowSlot {
    uint32_t offset;
};

#define ROW_SLOT_CHUNK_NUM_ROWS 100

struct RowSlotChunk {
    struct RowSlot slots[ROW_SLOT_CHUNK_NUM_ROWS];
    uint32_t nextChunkOffset;
};

/* Field types. */
enum {
    FIELD_TYPE_NULL = 0,
    FIELD_TYPE_INTEGER = 1,
    FIELD_TYPE_FLOAT = 2,
    FIELD_TYPE_STRING = 3,
    FIELD_TYPE_BLOB = 4,
};

/* Opaque type that describes a field slot. */
struct FieldSlot {
    int32_t type;
    union {
        double d;
        int64_t l;
        struct {
            uint32_t offset;
            uint32_t size;
        } buffer;
    } data;
} __attribute((packed));

JNIEXPORT jint JNICALL
Java_android_content_cts_CursorWindowContentProvider_makeNativeCursorWindowFd(JNIEnv *env, jclass clazz,
jint offset, jint size, jboolean isBlob) {
    int fd = open("/dev/ashmem", O_RDWR);
    ioctl(fd, ASHMEM_SET_NAME, "Fake CursorWindow");

    ioctl(fd, ASHMEM_SET_SIZE, 1024);

    void *data = mmap(NULL, 1024, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);

    struct Header *header = (struct Header *) data;
    unsigned rowSlotChunkOffset = sizeof(struct Header);
    struct RowSlotChunk *rowSlotChunk = (struct RowSlotChunk *)(data + rowSlotChunkOffset);
    unsigned fieldSlotOffset = rowSlotChunkOffset + sizeof(struct RowSlotChunk);
    struct FieldSlot *fieldSlot = (struct FieldSlot *) (data + fieldSlotOffset);

    header->numRows = 1;
    header->numColumns = 1;
    header->firstChunkOffset = rowSlotChunkOffset;

    rowSlotChunk->slots[0].offset = fieldSlotOffset;

    fieldSlot->type = isBlob ? FIELD_TYPE_BLOB : FIELD_TYPE_STRING;
    fieldSlot->data.buffer.offset = offset;
    fieldSlot->data.buffer.size = size;

    munmap(data, 1024);

    return fd;

}
