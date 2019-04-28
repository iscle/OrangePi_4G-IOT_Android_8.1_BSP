/*
* Copyright (c) 2014 Intel Corporation.  All rights reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

#ifndef LOG_DUMP_HELPER_INCLUDED
#define LOG_DUMP_HELPER_INCLUDED

#ifndef LOG_TAG
#error "Before including this file, must #define LOG_TAG and #include <utils/Log.h>"
#endif

#if LOG_NDEBUG == 0

// We have dump routines for the structures defined in the following files:
#include "VideoFrameInfo.h"
#include "ProtectedDataBuffer.h"

// The following helps to use these dump routines from command line unit tests:
#ifdef ANDROID
#define DUMP_EOL    ""
#else
#define LOGV    printf
#define LOGE    printf
#define DUMP_EOL    "\n"
#endif // ANDROID

#ifndef log_helper_min
#define log_helper_min(a,b) ((a) < (b) ? (a) : (b))
#endif // log_helper_min

static inline void Copy4Bytes(void* dst, const void* src)
{
    // Don't check input pointers for NULL: this is internal function,
    // and if you pass NULL to it, your code deserves to crash.

    uint8_t* bdst = (uint8_t*) dst ;
    const uint8_t* bsrc = (const uint8_t*) src ;

    *bdst++ = *bsrc++ ;
    *bdst++ = *bsrc++ ;
    *bdst++ = *bsrc++ ;
    *bdst = *bsrc ;
}
// End of Copy4Bytes()

static void DumpBufferToString(char* str, uint32_t strSize, const uint8_t* start, uint32_t size)
{
    if (str == NULL || strSize == 0 || start == NULL || size == 0)
    {
        LOGV("Error: invalid parameters to %s", __FUNCTION__) ;
        return ;
    }

    char* s = str ;
    char* send = str + strSize ;

    const uint8_t* byte = start ;
    const uint8_t* end = start + size ;

    while (byte < end && s < send)
    {
        s += snprintf(s, strSize - (s - str), "%02x ", *byte) ;
        ++byte ;
    }
}
// End of DumpBufferToString()

static void DumpNaluDataBuffer(uint32_t nalu, const uint8_t* start, uint32_t size)
{
    if (start == NULL || size == 0)
    {
        LOGV("NALU-dump: error: invalid parameters to %s", __FUNCTION__) ;
        return ;
    }

    const uint32_t STR_SIZE = 1024 ;
    char str[STR_SIZE] = {0} ;

    DumpBufferToString(str, STR_SIZE, start, size) ;

    LOGV("NALU-dump(nalu %u): data: %s" DUMP_EOL, nalu, str) ;
}
// End of DumpNaluDataBuffer()

static void DumpBuffer(const char* prefix, const uint8_t* start, uint32_t size)
{
    if (start == NULL || size == 0)
    {
        LOGV("Error: invalid parameters to %s", __FUNCTION__) ;
        return ;
    }

    if (prefix == NULL)
    {
        prefix = "" ;
    }

    const uint32_t STR_SIZE = 1024 ;
    char str[STR_SIZE] = {0} ;

    DumpBufferToString(str, STR_SIZE, start, size) ;

    LOGV("%s: ptr=%p, size=%u, data=%s" DUMP_EOL, prefix, start, size, str) ;
}
// End of DumpBuffer()

static void DumpNaluHeaderBuffer(const uint8_t* const start, uint32_t size)
{
    if (start == NULL || size == 0)
    {
        LOGV("Error: invalid parameters to %s", __FUNCTION__) ;
        return ;
    }

    const uint8_t* current = start ;

    uint32_t numNALUs = 0 ;
    Copy4Bytes(&numNALUs, current) ;
    current += sizeof(numNALUs) ;

    LOGV("NALU-dump: num NALUs = %u\n", numNALUs) ;

    if (numNALUs > MAX_NALUS_IN_FRAME)
    {
        LOGE("NALU-dump: ERROR, num NALUs is too big (%u)" DUMP_EOL, numNALUs) ;
    }

    for (uint32_t nalu = 0; nalu < numNALUs; ++nalu)
    {
        uint32_t imr_offset = 0 ;
        Copy4Bytes(&imr_offset, current) ;
        current += sizeof(imr_offset) ;

        uint32_t nalu_size = 0 ;
        Copy4Bytes(&nalu_size, current) ;
        current += sizeof(nalu_size) ;

        uint32_t data_size = 0 ;
        Copy4Bytes(&data_size, current) ;
        current += sizeof(data_size) ;

        LOGV("NALU-dump(nalu %u): imr_offset = %u, nalu_size = %u, data_size = %u" DUMP_EOL,
            nalu, imr_offset, nalu_size, data_size) ;

        DumpNaluDataBuffer(nalu, current, data_size) ;

        // Skip past the data
        current += data_size ;
    }
    // End of for
}
// End of DumpNaluHeaderBuffer()

static const char* DrmSchemeToString(uint32_t drmScheme)
{
    switch(drmScheme)
    {
        case DRM_SCHEME_NONE:
            return "None" ;

        case DRM_SCHEME_WV_CLASSIC:
            return "WV Classic" ;

        case DRM_SCHEME_WV_MODULAR:
            return "WV Modular" ;

#ifdef DRM_SCHEME_MCAST_SINK
        case DRM_SCHEME_MCAST_SINK:
            return "MCast Sink" ;
#endif

#ifdef DRM_SCHEME_PLAYREADY_ASF
        case DRM_SCHEME_PLAYREADY_ASF:
            return "PlayReady/ASF" ;
#endif

        default:
            return "unknown" ;
    }
}
// End of DrmSchemeToString()

static void DumpBuffer2(const char* prefix, const uint8_t* start, uint32_t size)
{
    if (start == NULL || size == 0)
    {
        LOGV("Error: invalid parameters to %s", __FUNCTION__) ;
        return ;
    }

    if (prefix == NULL)
    {
        prefix = "" ;
    }

    const uint32_t STR_SIZE = 1024 ;
    char str[STR_SIZE] = {0} ;

    DumpBufferToString(str, STR_SIZE, start, size) ;

    LOGV("%s%s" DUMP_EOL, prefix, str) ;
}
// End of DumpBuffer2()

static void DumpProtectedDataBuffer(const char* prefix, ProtectedDataBuffer* buf)
{
    if (buf == NULL)
    {
        LOGV("Error: invalid parameters to %s", __FUNCTION__) ;
        return ;
    }

    if (prefix == NULL) { prefix = "" ; }

    const uint32_t MAX_BUFFER_DUMP_LENGTH = 32 ;

    if (buf->magic != PROTECTED_DATA_BUFFER_MAGIC)
    {
        const uint8_t* p = (uint8_t*) &buf->magic ;
        LOGV("%sWrong magic: %02x %02x %02x %02x" DUMP_EOL, prefix, p[0], p[1], p[2], p[3]) ;
        return ;
    }

    LOGV("%smagic: ok, drmScheme: %u (%s), clear: %u, size: %u, num PES: %u" DUMP_EOL, prefix,
        buf->drmScheme, DrmSchemeToString(buf->drmScheme), buf->clear, buf->size, buf->numPesBuffers) ;

    if (buf->numPesBuffers == 0)
    {
        uint32_t dumpLength = log_helper_min(buf->size, MAX_BUFFER_DUMP_LENGTH) ;
        DumpBuffer2("data: ", buf->data, dumpLength) ;
    }
    else
    {
        for (uint32_t i = 0; i < buf->numPesBuffers; ++i)
        {
            const uint32_t STR_SIZE = 1024 ;
            char str[STR_SIZE] = {0} ;

            uint32_t dumpLength = log_helper_min(buf->pesBuffers[i].pesSize, MAX_BUFFER_DUMP_LENGTH) ;

            DumpBufferToString(str, STR_SIZE,
                buf->data + buf->pesBuffers[i].pesDataOffset, dumpLength) ;

            LOGV("PES %u: streamCounter: %u, inputCounter: %llu, offset: %u, size: %u, PES data: %s" DUMP_EOL,
                i, buf->pesBuffers[i].streamCounter, buf->pesBuffers[i].inputCounter,
                buf->pesBuffers[i].pesDataOffset, buf->pesBuffers[i].pesSize, str) ;
        }
    }
}
// End of DumpProtectedDataBuffer

static void DumpVideoFrameInfo(frame_info_t* fInfo)
{
    if (fInfo == NULL)
    {
        LOGV("Error: invalid parameters to %s", __FUNCTION__) ;
        return ;
    }

    LOGV("frame_info_t: data = %p, size = %u, num_nalus = %u", fInfo->data, fInfo->size, fInfo->num_nalus) ;

    for (uint32_t i = 0; i < fInfo->num_nalus; ++i)
    {
        LOGV("nalu_info_t: type = %#x, offset = %u (%#x), data = %p, length = %u",
            fInfo->nalus[i].type, fInfo->nalus[i].offset, fInfo->nalus[i].offset,
            fInfo->nalus[i].data, fInfo->nalus[i].length) ;

        if (fInfo->nalus[i].data != NULL && fInfo->nalus[i].length > 0)
        {
            DumpBuffer2("nalu_info_t::data: ", fInfo->nalus[i].data, fInfo->nalus[i].length) ;
        }
    }
}
// End of DumpVideoFrameInfo()

#else

// Avoid #ifdef around the dump code

#define DumpBuffer(...)
#define DumpBuffer2(...)
#define DumpNaluHeaderBuffer(...)
#define DumpProtectedDataBuffer(...)
#define DumpVideoFrameInfo(...)

#define DUMP_EOL

#endif // LOG_NDEBUG == 0

#endif // LOG_DUMP_HELPER_INCLUDED
