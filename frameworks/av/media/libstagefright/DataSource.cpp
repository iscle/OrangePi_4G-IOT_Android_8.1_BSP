/*
 * Copyright (C) 2009 The Android Open Source Project
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
//#define LOG_NDEBUG 0
#define LOG_TAG "DataSource"

#include "include/CallbackDataSource.h"
#include "include/HTTPBase.h"
#include "include/NuCachedSource2.h"

#include <media/IDataSource.h>
#include <media/IMediaHTTPConnection.h>
#include <media/IMediaHTTPService.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/DataURISource.h>
#include <media/stagefright/FileSource.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaHTTP.h>
#include <media/stagefright/RemoteDataSource.h>
#include <media/stagefright/Utils.h>
#include <utils/String8.h>

#include <cutils/properties.h>

#include <private/android_filesystem_config.h>

namespace android {

bool DataSource::getUInt16(off64_t offset, uint16_t *x) {
    *x = 0;

    uint8_t byte[2];
    if (readAt(offset, byte, 2) != 2) {
        return false;
    }

    *x = (byte[0] << 8) | byte[1];

    return true;
}

bool DataSource::getUInt24(off64_t offset, uint32_t *x) {
    *x = 0;

    uint8_t byte[3];
    if (readAt(offset, byte, 3) != 3) {
        return false;
    }

    *x = (byte[0] << 16) | (byte[1] << 8) | byte[2];

    return true;
}

bool DataSource::getUInt32(off64_t offset, uint32_t *x) {
    *x = 0;

    uint32_t tmp;
    if (readAt(offset, &tmp, 4) != 4) {
        return false;
    }

    *x = ntohl(tmp);

    return true;
}

bool DataSource::getUInt64(off64_t offset, uint64_t *x) {
    *x = 0;

    uint64_t tmp;
    if (readAt(offset, &tmp, 8) != 8) {
        return false;
    }

    *x = ntoh64(tmp);

    return true;
}

bool DataSource::getUInt16Var(off64_t offset, uint16_t *x, size_t size) {
    if (size == 2) {
        return getUInt16(offset, x);
    }
    if (size == 1) {
        uint8_t tmp;
        if (readAt(offset, &tmp, 1) == 1) {
            *x = tmp;
            return true;
        }
    }
    return false;
}

bool DataSource::getUInt32Var(off64_t offset, uint32_t *x, size_t size) {
    if (size == 4) {
        return getUInt32(offset, x);
    }
    if (size == 2) {
        uint16_t tmp;
        if (getUInt16(offset, &tmp)) {
            *x = tmp;
            return true;
        }
    }
    return false;
}

bool DataSource::getUInt64Var(off64_t offset, uint64_t *x, size_t size) {
    if (size == 8) {
        return getUInt64(offset, x);
    }
    if (size == 4) {
        uint32_t tmp;
        if (getUInt32(offset, &tmp)) {
            *x = tmp;
            return true;
        }
    }
    return false;
}

status_t DataSource::getSize(off64_t *size) {
    *size = 0;

    return ERROR_UNSUPPORTED;
}

sp<IDataSource> DataSource::getIDataSource() const {
    return nullptr;
}

////////////////////////////////////////////////////////////////////////////////

// static
sp<DataSource> DataSource::CreateFromURI(
        const sp<IMediaHTTPService> &httpService,
        const char *uri,
        const KeyedVector<String8, String8> *headers,
        String8 *contentType,
        HTTPBase *httpSource) {
    if (contentType != NULL) {
        *contentType = "";
    }

    sp<DataSource> source;
    if (!strncasecmp("file://", uri, 7)) {
        source = new FileSource(uri + 7);
    } else if (!strncasecmp("http://", uri, 7) || !strncasecmp("https://", uri, 8)) {
        if (httpService == NULL) {
            ALOGE("Invalid http service!");
            return NULL;
        }

        if (httpSource == NULL) {
            sp<IMediaHTTPConnection> conn = httpService->makeHTTPConnection();
            if (conn == NULL) {
                ALOGE("Failed to make http connection from http service!");
                return NULL;
            }
            httpSource = new MediaHTTP(conn);
        }

        String8 cacheConfig;
        bool disconnectAtHighwatermark = false;
        KeyedVector<String8, String8> nonCacheSpecificHeaders;
        if (headers != NULL) {
            nonCacheSpecificHeaders = *headers;
            NuCachedSource2::RemoveCacheSpecificHeaders(
                    &nonCacheSpecificHeaders,
                    &cacheConfig,
                    &disconnectAtHighwatermark);
        }

        if (httpSource->connect(uri, &nonCacheSpecificHeaders) != OK) {
            ALOGE("Failed to connect http source!");
            return NULL;
        }

        if (contentType != NULL) {
            *contentType = httpSource->getMIMEType();
        }

        source = NuCachedSource2::Create(
                httpSource,
                cacheConfig.isEmpty() ? NULL : cacheConfig.string(),
                disconnectAtHighwatermark);
    } else if (!strncasecmp("data:", uri, 5)) {
        source = DataURISource::Create(uri);
    } else {
        // Assume it's a filename.
        source = new FileSource(uri);
    }

    if (source == NULL || source->initCheck() != OK) {
        return NULL;
    }

    return source;
}

sp<DataSource> DataSource::CreateFromFd(int fd, int64_t offset, int64_t length) {
    sp<FileSource> source = new FileSource(fd, offset, length);
    return source->initCheck() != OK ? nullptr : source;
}

sp<DataSource> DataSource::CreateMediaHTTP(const sp<IMediaHTTPService> &httpService) {
    if (httpService == NULL) {
        return NULL;
    }

    sp<IMediaHTTPConnection> conn = httpService->makeHTTPConnection();
    if (conn == NULL) {
        return NULL;
    } else {
        return new MediaHTTP(conn);
    }
}

sp<DataSource> DataSource::CreateFromIDataSource(const sp<IDataSource> &source) {
    return new TinyCacheSource(new CallbackDataSource(source));
}

String8 DataSource::getMIMEType() const {
    return String8("application/octet-stream");
}

sp<IDataSource> DataSource::asIDataSource() {
    return RemoteDataSource::wrap(sp<DataSource>(this));
}

}  // namespace android
